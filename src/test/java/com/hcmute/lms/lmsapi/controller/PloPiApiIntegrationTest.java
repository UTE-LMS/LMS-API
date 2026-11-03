package com.hcmute.lms.lmsapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hcmute.lms.lmsapi.document.Pi;
import com.hcmute.lms.lmsapi.document.Plo;
import com.hcmute.lms.lmsapi.document.Program;
import com.hcmute.lms.lmsapi.document.ProgramVersion;
import com.hcmute.lms.lmsapi.document.UserScope;
import com.hcmute.lms.lmsapi.dto.PiRequest;
import com.hcmute.lms.lmsapi.dto.PloRequest;
import com.hcmute.lms.lmsapi.enums.PiContributionType;
import com.hcmute.lms.lmsapi.enums.ProgramVersionStatus;
import com.hcmute.lms.lmsapi.enums.Role;
import com.hcmute.lms.lmsapi.testsupport.ApiTestFixtures;
import com.hcmute.lms.lmsapi.testsupport.IntegrationTestBase;
import java.util.List;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.web.servlet.MvcResult;

/**
 * F02 / US-06: PLO và PI qua HTTP. Giá trị kỳ vọng lấy từ {@code doc/plan/F02-ctdt-plo-pi.md} mục
 * 3, 4, 5 (mẫu mã, miền 1-6, miền 0-1, sắp theo {@code displayOrder}).
 */
@DisplayName("F02 / US-06: API PLO và PI")
class PloPiApiIntegrationTest extends IntegrationTestBase {

  private static final String PLOS = "/api/v1/program-versions/{pv}/plos";
  private static final String PLO_BY_ID = "/api/v1/program-versions/{pv}/plos/{id}";
  private static final String PIS = "/api/v1/program-versions/{pv}/pis";
  private static final String PI_BY_ID = "/api/v1/program-versions/{pv}/pis/{id}";

  private String manager;
  private Program program;
  private ProgramVersion version;

  @BeforeEach
  void setUp() {
    program = fixtures.program();
    manager =
        fixtures.bearerFor(
            Role.PROGRAM_MANAGER, UserScope.builder().programIds(List.of(program.getId())).build());
    version = fixtures.programVersion(program, ProgramVersionStatus.DRAFT);
  }

  private static PloRequest ploRequest(String code, int displayOrder) {
    return PloRequest.builder().code(code).displayOrder(displayOrder).build();
  }

  private static PiRequest piRequest(String ploId, String code, int displayOrder) {
    return PiRequest.builder().ploId(ploId).code(code).displayOrder(displayOrder).build();
  }

  // ------------------------------------------------------------------ US-06/AC-1: mẫu mã

  @ParameterizedTest(name = "mã PLO hợp lệ {0} -> 201")
  @ValueSource(strings = {"PLO1", "PLO7", "PLO10", "PLO99"})
  @DisplayName("US-06/AC-1: mã PLO đúng mẫu ^PLO[1-9][0-9]*$ -> 201")
  void createPlo_validCodes(String code) throws Exception {
    mockMvc
        .perform(postAs(manager, PLOS, ploRequest(code, 1), version.getId()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.code").value(code));
  }

  @ParameterizedTest(name = "mã PLO sai {0} -> 400")
  @ValueSource(
      strings = {"PLO0", "plo1", "Plo1", "PLO01", "PLO", "PLO1 ", "PLO-1", "PLO1.1", "PLO1a"})
  @DisplayName("US-06/AC-1: PLO0, plo1, PLO01 và các mã sai mẫu khác -> 400 VALIDATION_ERROR")
  void createPlo_invalidCodes(String code) throws Exception {
    mockMvc
        .perform(postAs(manager, PLOS, ploRequest(code, 1), version.getId()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("code")));
  }

  @ParameterizedTest(name = "mã PI hợp lệ {0} -> 201")
  @ValueSource(strings = {"PI1.1", "PI7.2", "PI10.12", "PI2.10"})
  @DisplayName("US-06/AC-1: mã PI đúng mẫu ^PI[1-9][0-9]*\\.[1-9][0-9]*$ -> 201")
  void createPi_validCodes(String code) throws Exception {
    Plo plo = fixtures.plo(version, "PLO1", 1);
    mockMvc
        .perform(postAs(manager, PIS, piRequest(plo.getId(), code, 1), version.getId()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.code").value(code));
  }

  @ParameterizedTest(name = "mã PI sai {0} -> 400")
  @ValueSource(
      strings = {"PI1.0", "PI01.1", "PI0.1", "PI1", "PI1.01", "pi1.1", "PI1.1.1", "PI.1", "PI1."})
  @DisplayName("US-06/AC-1: PI1.0, PI01.1 và các mã PI sai mẫu khác -> 400 VALIDATION_ERROR")
  void createPi_invalidCodes(String code) throws Exception {
    Plo plo = fixtures.plo(version, "PLO1", 1);
    mockMvc
        .perform(postAs(manager, PIS, piRequest(plo.getId(), code, 1), version.getId()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("code")));
  }

  // ------------------------------------------------------------------ US-06/AC-2: PI ↔ PLO

  @Test
  @DisplayName("US-06/AC-2: PI có ploId thuộc phiên bản CTĐT khác -> 422, dù PLO đó có thật")
  void createPi_ploFromOtherVersionIsUnprocessable() throws Exception {
    ProgramVersion other = fixtures.programVersion(fixtures.program(), ProgramVersionStatus.DRAFT);
    Plo ploInOther = fixtures.plo(other, "PLO1", 1);

    mockMvc
        .perform(postAs(manager, PIS, piRequest(ploInOther.getId(), "PI1.1", 1), version.getId()))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
  }

  @Test
  @DisplayName("US-06/AC-2: PI có ploId hợp lệ nhưng không tồn tại -> 422; sai định dạng -> 400")
  void createPi_missingOrMalformedPlo() throws Exception {
    mockMvc
        .perform(
            postAs(
                manager, PIS, piRequest(ApiTestFixtures.missingId(), "PI1.1", 1), version.getId()))
        .andExpect(status().isUnprocessableEntity());
    mockMvc
        .perform(postAs(manager, PIS, piRequest("not-an-id", "PI1.1", 1), version.getId()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("ploId")));
  }

  @Test
  @DisplayName(
      "US-06/AC-2 (dương): PI cùng phiên bản với PLO -> 201, trả đúng ploId và programVersionId")
  void createPi_samePloVersionSucceeds() throws Exception {
    Plo plo = fixtures.plo(version, "PLO1", 1);
    mockMvc
        .perform(postAs(manager, PIS, piRequest(plo.getId(), "PI1.1", 1), version.getId()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.ploId").value(plo.getId()))
        .andExpect(jsonPath("$.programVersionId").value(version.getId()));
  }

  // ------------------------------------------------------------------ US-06/AC-3: miền 1-6

  @ParameterizedTest(name = "capabilityLevelRaw = {0} -> 400")
  @ValueSource(ints = {0, 7, -1, 100})
  @DisplayName("US-06/AC-3: capabilityLevelRaw ngoài 1-6 -> 400 (PLO và PI)")
  void capabilityLevel_outOfRange(int level) throws Exception {
    PloRequest plo = ploRequest("PLO1", 1);
    plo.setCapabilityLevelRaw(level);
    mockMvc
        .perform(postAs(manager, PLOS, plo, version.getId()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("capabilityLevelRaw")));

    Plo parent = fixtures.plo(version, "PLO2", 2);
    PiRequest pi = piRequest(parent.getId(), "PI2.1", 1);
    pi.setCapabilityLevelRaw(level);
    mockMvc
        .perform(postAs(manager, PIS, pi, version.getId()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("capabilityLevelRaw")));
  }

  @ParameterizedTest(name = "capabilityLevelRaw = {0} -> 201 và lưu nguyên giá trị")
  @ValueSource(ints = {1, 6})
  @DisplayName("US-06/AC-3 (biên): 1 và 6 hợp lệ, lưu nguyên không diễn giải (Q03)")
  void capabilityLevel_boundsAccepted(int level) throws Exception {
    PloRequest plo = ploRequest("PLO1", 1);
    plo.setCapabilityLevelRaw(level);
    mockMvc
        .perform(postAs(manager, PLOS, plo, version.getId()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.capabilityLevelRaw").value(level));
  }

  @ParameterizedTest(name = "targetPassRate = {0} -> 400")
  @ValueSource(doubles = {-0.01, 1.01, 2.0, -1.0})
  @DisplayName("F02 mục 3 (âm): targetPassRate ngoài [0,1] -> 400")
  void targetPassRate_outOfRange(double rate) throws Exception {
    Plo parent = fixtures.plo(version, "PLO1", 1);
    PiRequest pi = piRequest(parent.getId(), "PI1.1", 1);
    pi.setTargetPassRate(rate);
    mockMvc
        .perform(postAs(manager, PIS, pi, version.getId()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("targetPassRate")));
  }

  @ParameterizedTest(name = "targetPassRate = {0} -> 201")
  @ValueSource(doubles = {0.0, 1.0})
  @DisplayName("F02 mục 3 (biên): targetPassRate = 0 và 1 hợp lệ")
  void targetPassRate_boundsAccepted(double rate) throws Exception {
    Plo parent = fixtures.plo(version, "PLO1", 1);
    PiRequest pi = piRequest(parent.getId(), "PI1.1", 1);
    pi.setTargetPassRate(rate);
    mockMvc
        .perform(postAs(manager, PIS, pi, version.getId()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.targetPassRate").value(rate));
  }

  // ------------------------------------------------------------------ US-06/AC-4: thứ tự

  @Test
  @DisplayName(
      "US-06/AC-4: danh sách PLO/PI sắp theo displayOrder, không theo code hay thứ tự chèn")
  void list_sortedByDisplayOrder() throws Exception {
    // Thứ tự chèn: PLO3, PLO1, PLO2. displayOrder: 1, 3, 2. Thứ tự kỳ vọng theo displayOrder:
    // PLO3 (1), PLO2 (2), PLO1 (3) - khác cả thứ tự chèn lẫn thứ tự mã.
    mockMvc.perform(postAs(manager, PLOS, ploRequest("PLO3", 1), version.getId()));
    MvcResult plo1 =
        mockMvc.perform(postAs(manager, PLOS, ploRequest("PLO1", 3), version.getId())).andReturn();
    mockMvc.perform(postAs(manager, PLOS, ploRequest("PLO2", 2), version.getId()));

    mockMvc
        .perform(getAs(manager, PLOS, version.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].code", contains("PLO3", "PLO2", "PLO1")));

    String ploId = read(plo1, "$.id");
    mockMvc.perform(postAs(manager, PIS, piRequest(ploId, "PI1.3", 2), version.getId()));
    mockMvc.perform(postAs(manager, PIS, piRequest(ploId, "PI1.1", 3), version.getId()));
    mockMvc.perform(postAs(manager, PIS, piRequest(ploId, "PI1.2", 1), version.getId()));
    mockMvc
        .perform(getAs(manager, PIS, version.getId()))
        .andExpect(jsonPath("$[*].code", contains("PI1.2", "PI1.3", "PI1.1")));
  }

  // ------------------------------------------------------------------ US-06/AC-5: nội dung gốc

  @Test
  @DisplayName(
      "US-06/AC-5: PLO/PI không có contentOriginal vẫn hợp lệ, chưa VERIFIED, không tự điền")
  void create_withoutOriginalContentIsUnverified() throws Exception {
    MvcResult plo =
        mockMvc
            .perform(postAs(manager, PLOS, ploRequest("PLO1", 1), version.getId()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.contentOriginal").value(nullValue()))
            .andExpect(jsonPath("$.verification.status").value(not("VERIFIED")))
            .andExpect(jsonPath("$.verification.status").value("NEEDS_CLARIFICATION"))
            .andExpect(jsonPath("$.category").value("UNSPECIFIED"))
            .andExpect(jsonPath("$.capabilityLevelRaw").value(nullValue()))
            .andReturn();

    mockMvc
        .perform(postAs(manager, PIS, piRequest(read(plo, "$.id"), "PI1.1", 1), version.getId()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.contentOriginal").value(nullValue()))
        .andExpect(jsonPath("$.verification.status").value("NEEDS_CLARIFICATION"))
        // F02 mục 4.7: không đặt mặc định cho targetPassRate.
        .andExpect(jsonPath("$.targetPassRate").value(nullValue()))
        .andExpect(jsonPath("$.capabilityLevelRaw").value(nullValue()));
  }

  @Test
  @DisplayName(
      "US-06/AC-5: body cố tình gửi verification = VERIFIED bị bỏ qua (chỉ QA_OFFICER được xác nhận)")
  void create_forgedVerificationIsIgnored() throws Exception {
    String body =
        "{\"code\":\"PLO1\",\"displayOrder\":1,"
            + "\"verification\":{\"status\":\"VERIFIED\",\"verifiedBy\":\"x\"}}";
    mockMvc
        .perform(postAs(manager, PLOS, body, version.getId()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.verification.status").value("NEEDS_CLARIFICATION"))
        .andExpect(jsonPath("$.verification.verifiedBy").value(nullValue()));
  }

  // ------------------------------------------------------------------ trùng khóa, tham chiếu

  @Test
  @DisplayName(
      "US-40/AC-3: trùng (programVersionId, code) -> 409 CONFLICT; cùng mã ở phiên bản khác thì được")
  void duplicateCodes() throws Exception {
    mockMvc
        .perform(postAs(manager, PLOS, ploRequest("PLO1", 1), version.getId()))
        .andExpect(status().isCreated());
    mockMvc
        .perform(postAs(manager, PLOS, ploRequest("PLO1", 2), version.getId()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"));

    Plo ploForPi = fixtures.plo(version, "PLO2", 2);
    mockMvc
        .perform(postAs(manager, PIS, piRequest(ploForPi.getId(), "PI2.1", 1), version.getId()))
        .andExpect(status().isCreated());
    mockMvc
        .perform(postAs(manager, PIS, piRequest(ploForPi.getId(), "PI2.1", 2), version.getId()))
        .andExpect(status().isConflict());

    ProgramVersion other = fixtures.programVersion(program, ProgramVersionStatus.DRAFT);
    mockMvc
        .perform(postAs(manager, PLOS, ploRequest("PLO1", 1), other.getId()))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("US-40/AC-3: sửa PLO sang mã đã có -> 409; giữ nguyên mã của chính nó -> 200")
  void updatePlo_renameConflictAndSelfKeep() throws Exception {
    Plo first = fixtures.plo(version, "PLO1", 1);
    Plo second = fixtures.plo(version, "PLO2", 2);

    mockMvc
        .perform(putAs(manager, PLO_BY_ID, ploRequest("PLO1", 2), version.getId(), second.getId()))
        .andExpect(status().isConflict());

    PloRequest keep = ploRequest("PLO1", 5);
    keep.setContentSummary(ApiTestFixtures.LABEL + " tóm tắt đã sửa");
    mockMvc
        .perform(putAs(manager, PLO_BY_ID, keep, version.getId(), first.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.displayOrder").value(5))
        .andExpect(jsonPath("$.contentSummary").value(ApiTestFixtures.LABEL + " tóm tắt đã sửa"));
  }

  // ------------------------------------------------------------------ xóa

  @Test
  @DisplayName("F02 mục 7: xóa PLO còn PI -> 422; xóa PLO không còn PI -> 204 rồi 404")
  void deletePlo() throws Exception {
    Plo withPi = fixtures.plo(version, "PLO1", 1);
    fixtures.pi(version, withPi, "PI1.1", 1);
    Plo bare = fixtures.plo(version, "PLO2", 2);

    mockMvc
        .perform(deleteAs(manager, PLO_BY_ID, version.getId(), withPi.getId()))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    assertThat(mongo().findById(withPi.getId(), Plo.class)).isNotNull();

    mockMvc
        .perform(deleteAs(manager, PLO_BY_ID, version.getId(), bare.getId()))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(deleteAs(manager, PLO_BY_ID, version.getId(), bare.getId()))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("F02 mục 7: xóa PI còn dòng ma trận I/R/M tham chiếu -> 422; PI tự do -> 204")
  void deletePi() throws Exception {
    Plo plo = fixtures.plo(version, "PLO1", 1);
    Pi referenced = fixtures.pi(version, plo, "PI1.1", 1);
    Pi free = fixtures.pi(version, plo, "PI1.2", 2);
    fixtures.contribution(version, fixtures.course(), referenced, PiContributionType.I);

    mockMvc
        .perform(deleteAs(manager, PI_BY_ID, version.getId(), referenced.getId()))
        .andExpect(status().isUnprocessableEntity());
    assertThat(mongo().findById(referenced.getId(), Pi.class)).isNotNull();
    mockMvc
        .perform(deleteAs(manager, PI_BY_ID, version.getId(), free.getId()))
        .andExpect(status().isNoContent());
  }

  @Test
  @DisplayName(
      "F02 mục 7: xóa PI còn clo_pi_links tham chiếu -> 422, gỡ liên kết xong mới xóa được")
  void deletePi_referencedByCloPiLink() throws Exception {
    Plo plo = fixtures.plo(version, "PLO1", 1);
    Pi pi = fixtures.pi(version, plo, "PI1.1", 1);
    ObjectId piOid = new ObjectId(pi.getId());
    // clo_pi_links thuộc F03 (chưa có mã): chèn tài liệu giả đúng validator để thử nối với F02.
    Document link =
        new Document("programVersionId", new ObjectId(version.getId()))
            .append("syllabusId", new ObjectId())
            .append("cloId", new ObjectId())
            .append("piId", piOid)
            .append("weight", null)
            .append("verification", new Document("status", "NEEDS_CLARIFICATION"));
    mongo().insert(link, "clo_pi_links");
    try {
      mockMvc
          .perform(deleteAs(manager, PI_BY_ID, version.getId(), pi.getId()))
          .andExpect(status().isUnprocessableEntity());
      assertThat(mongo().findById(pi.getId(), Pi.class)).isNotNull();
    } finally {
      mongo().remove(Query.query(Criteria.where("piId").is(piOid)), "clo_pi_links");
    }
    mockMvc
        .perform(deleteAs(manager, PI_BY_ID, version.getId(), pi.getId()))
        .andExpect(status().isNoContent());
  }

  // ------------------------------------------------------------------ phiên bản không phải DRAFT

  @Test
  @DisplayName(
      "F02 mục 4.3: phiên bản ACTIVE không cho tạo/sửa/xóa PLO, PI (422), nhưng vẫn đọc được")
  void nonDraftVersionIsReadOnly() throws Exception {
    ProgramVersion active = fixtures.programVersion(program, ProgramVersionStatus.ACTIVE);
    Plo plo = fixtures.plo(active, "PLO1", 1);
    Pi pi = fixtures.pi(active, plo, "PI1.1", 1);

    mockMvc
        .perform(postAs(manager, PLOS, ploRequest("PLO2", 2), active.getId()))
        .andExpect(status().isUnprocessableEntity());
    mockMvc
        .perform(putAs(manager, PLO_BY_ID, ploRequest("PLO1", 9), active.getId(), plo.getId()))
        .andExpect(status().isUnprocessableEntity());
    mockMvc
        .perform(deleteAs(manager, PI_BY_ID, active.getId(), pi.getId()))
        .andExpect(status().isUnprocessableEntity());
    mockMvc
        .perform(postAs(manager, PIS, piRequest(plo.getId(), "PI1.2", 2), active.getId()))
        .andExpect(status().isUnprocessableEntity());

    mockMvc
        .perform(getAs(manager, PLOS, active.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].code", contains("PLO1")));
  }

  // ------------------------------------------------------------------ ca âm chung

  @ParameterizedTest(name = "{0} -> 400")
  @ValueSource(strings = {"abc", "12345", "zzzzzzzzzzzzzzzzzzzzzzzz", "671dcba5f123456789abc7777"})
  @DisplayName("US-40 (biên): ObjectId sai định dạng ở path (version hoặc id) -> 400, không 500")
  void malformedPathIds(String badId) throws Exception {
    mockMvc.perform(getAs(manager, PLOS, badId)).andExpect(status().isBadRequest());
    mockMvc.perform(getAs(manager, PIS, badId)).andExpect(status().isBadRequest());
    mockMvc
        .perform(getAs(manager, PLO_BY_ID, version.getId(), badId))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(getAs(manager, PI_BY_ID, version.getId(), badId))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(deleteAs(manager, PLO_BY_ID, version.getId(), badId))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(deleteAs(manager, PI_BY_ID, version.getId(), badId))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("US-40: phiên bản/PLO/PI hợp lệ nhưng không tồn tại -> 404 NOT_FOUND")
  void unknownIdsAreNotFound() throws Exception {
    String missing = ApiTestFixtures.missingId();
    mockMvc
        .perform(getAs(manager, PLOS, missing))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    mockMvc
        .perform(getAs(manager, PLO_BY_ID, version.getId(), missing))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(getAs(manager, PI_BY_ID, version.getId(), missing))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName(
      "US-40 (biên): PLO của phiên bản khác không truy cập được qua path của phiên bản này")
  void ploIsScopedToItsVersion() throws Exception {
    ProgramVersion other = fixtures.programVersion(program, ProgramVersionStatus.DRAFT);
    Plo foreign = fixtures.plo(other, "PLO1", 1);
    mockMvc
        .perform(getAs(manager, PLO_BY_ID, version.getId(), foreign.getId()))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(deleteAs(manager, PLO_BY_ID, version.getId(), foreign.getId()))
        .andExpect(status().isNotFound());
    assertThat(mongo().findById(foreign.getId(), Plo.class)).isNotNull();
  }

  @Test
  @DisplayName("US-40/AC-1: body rỗng, {} , JSON hỏng, thiếu displayOrder -> 400 VALIDATION_ERROR")
  void emptyAndMalformedBodies() throws Exception {
    mockMvc
        .perform(postAs(manager, PLOS, null, version.getId()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    mockMvc.perform(postAs(manager, PLOS, "[", version.getId())).andExpect(status().isBadRequest());
    mockMvc
        .perform(postAs(manager, PLOS, "{}", version.getId()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("code")))
        .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("displayOrder")));
    mockMvc
        .perform(postAs(manager, PLOS, "{\"code\":\"PLO1\",\"displayOrder\":-1}", version.getId()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("displayOrder")));
    mockMvc
        .perform(
            postAs(manager, PLOS, "{\"code\":\"PLO1\",\"displayOrder\":\"một\"}", version.getId()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("US-06 (biên): contentSummary 5000 ký tự hợp lệ, 5001 bị 400")
  void contentSummaryLengthBoundary() throws Exception {
    PloRequest atLimit = ploRequest("PLO1", 1);
    atLimit.setContentSummary("a".repeat(5000));
    mockMvc
        .perform(postAs(manager, PLOS, atLimit, version.getId()))
        .andExpect(status().isCreated());

    PloRequest over = ploRequest("PLO2", 2);
    over.setContentSummary("a".repeat(5001));
    mockMvc
        .perform(postAs(manager, PLOS, over, version.getId()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("contentSummary")));
  }

  // ------------------------------------------------------------------ quyền

  @Test
  @DisplayName("US-03/AC-2: không đăng nhập -> 401 trên mọi thao tác PLO/PI")
  void anonymousIsUnauthorized() throws Exception {
    Plo plo = fixtures.plo(version, "PLO1", 1);
    mockMvc.perform(getAs(null, PLOS, version.getId())).andExpect(status().isUnauthorized());
    mockMvc.perform(getAs(null, PIS, version.getId())).andExpect(status().isUnauthorized());
    mockMvc
        .perform(postAs(null, PLOS, ploRequest("PLO2", 2), version.getId()))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(putAs(null, PLO_BY_ID, ploRequest("PLO1", 2), version.getId(), plo.getId()))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(deleteAs(null, PLO_BY_ID, version.getId(), plo.getId()))
        .andExpect(status().isUnauthorized());
  }

  @ParameterizedTest(name = "{0} không được ghi PLO/PI")
  @EnumSource(
      value = Role.class,
      names = {"PROGRAM_MANAGER"},
      mode = EnumSource.Mode.EXCLUDE)
  @DisplayName(
      "F02 mục 6: chỉ PROGRAM_MANAGER ghi PLO/PI; vai trò khác -> 403 và dữ liệu không đổi")
  void onlyProgramManagerMayWrite(Role role) throws Exception {
    String bearer = fixtures.bearerFor(role, null);
    Plo plo = fixtures.plo(version, "PLO1", 1);

    mockMvc
        .perform(postAs(bearer, PLOS, ploRequest("PLO2", 2), version.getId()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    mockMvc
        .perform(putAs(bearer, PLO_BY_ID, ploRequest("PLO1", 9), version.getId(), plo.getId()))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(deleteAs(bearer, PLO_BY_ID, version.getId(), plo.getId()))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(postAs(bearer, PIS, piRequest(plo.getId(), "PI1.1", 1), version.getId()))
        .andExpect(status().isForbidden());

    assertThat(mongo().findById(plo.getId(), Plo.class).getDisplayOrder()).isEqualTo(1);
    mockMvc.perform(getAs(bearer, PLOS, version.getId())).andExpect(status().isOk());
  }
}
