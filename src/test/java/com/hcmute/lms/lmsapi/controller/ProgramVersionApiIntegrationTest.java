package com.hcmute.lms.lmsapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hcmute.lms.lmsapi.document.Program;
import com.hcmute.lms.lmsapi.document.ProgramVersion;
import com.hcmute.lms.lmsapi.document.UserScope;
import com.hcmute.lms.lmsapi.dto.ProgramVersionRequest;
import com.hcmute.lms.lmsapi.dto.ProgramVersionStatusUpdateRequest;
import com.hcmute.lms.lmsapi.dto.ProgramVersionUpdateRequest;
import com.hcmute.lms.lmsapi.enums.ProgramVersionStatus;
import com.hcmute.lms.lmsapi.enums.Role;
import com.hcmute.lms.lmsapi.testsupport.ApiTestFixtures;
import com.hcmute.lms.lmsapi.testsupport.IntegrationTestBase;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MvcResult;

/**
 * F02 / US-05: chương trình đào tạo và phiên bản CTĐT qua HTTP. Giá trị kỳ vọng lấy từ {@code
 * doc/plan/F02-ctdt-plo-pi.md} mục 4 và 5, không lấy từ mã sản phẩm.
 */
@DisplayName("F02 / US-05: API phiên bản CTĐT")
class ProgramVersionApiIntegrationTest extends IntegrationTestBase {

  private static final String VERSIONS = "/api/v1/program-versions";
  private static final String VERSION_BY_ID = "/api/v1/program-versions/{id}";
  private static final String VERSION_STATUS = "/api/v1/program-versions/{id}/status";

  private String manager;
  private Program program;

  @BeforeEach
  void setUp() {
    program = fixtures.program();
    manager =
        fixtures.bearerFor(
            Role.PROGRAM_MANAGER, UserScope.builder().programIds(List.of(program.getId())).build());
  }

  private ProgramVersionRequest request(String version) {
    return ProgramVersionRequest.builder().programId(program.getId()).version(version).build();
  }

  // ------------------------------------------------------------------ US-05/AC-1

  @Test
  @DisplayName(
      "US-05/AC-1: tạo phiên bản mới thành công, status = DRAFT, chưa xác nhận, không tự điền")
  void createVersion_defaultsToDraftAndUnverified() throws Exception {
    MvcResult result =
        mockMvc
            .perform(postAs(manager, VERSIONS, request("V-NEW")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.programId").value(program.getId()))
            .andExpect(jsonPath("$.version").value("V-NEW"))
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andExpect(jsonPath("$.verification.status").value("NEEDS_CLARIFICATION"))
            .andExpect(jsonPath("$.verification.verifiedBy").value(nullValue()))
            // Q01: khóa áp dụng và số tín chỉ để trống, không được đặt mặc định.
            .andExpect(jsonPath("$.applicableCohorts", empty()))
            .andExpect(jsonPath("$.totalCredits").value(nullValue()))
            .andExpect(jsonPath("$.durationYears").value(nullValue()))
            .andReturn();

    String id = read(result, "$.id");
    ProgramVersion stored = mongo().findById(id, ProgramVersion.class);
    assertThat(stored).isNotNull();
    assertThat(stored.getStatus()).isEqualTo(ProgramVersionStatus.DRAFT);
  }

  @Test
  @DisplayName(
      "US-05/AC-1: trường thừa (id, status, verification) trong body bị bỏ qua, không mass-assign")
  void createVersion_extraFieldsAreIgnored() throws Exception {
    String forgedId = ApiTestFixtures.missingId();
    String body =
        "{\"programId\":\""
            + program.getId()
            + "\",\"version\":\"V-FORGED\",\"id\":\""
            + forgedId
            + "\",\"status\":\"ACTIVE\","
            + "\"verification\":{\"status\":\"VERIFIED\",\"verifiedBy\":\"someone\"}}";

    mockMvc
        .perform(postAs(manager, VERSIONS, body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("DRAFT"))
        .andExpect(jsonPath("$.id").value(not(forgedId)))
        .andExpect(jsonPath("$.verification.status").value("NEEDS_CLARIFICATION"))
        .andExpect(jsonPath("$.verification.verifiedBy").value(nullValue()));
  }

  static Stream<Arguments> versionLengthBoundaries() {
    return Stream.of(Arguments.of(100, 201), Arguments.of(101, 400));
  }

  @ParameterizedTest(name = "version dài {0} ký tự -> HTTP {1}")
  @MethodSource("versionLengthBoundaries")
  @DisplayName("US-05/AC-1 (biên): độ dài version 100 hợp lệ, 101 bị 400")
  void createVersion_lengthBoundary(int length, int expectedStatus) throws Exception {
    mockMvc
        .perform(postAs(manager, VERSIONS, request("v".repeat(length))))
        .andExpect(status().is(expectedStatus));
  }

  @Test
  @DisplayName("US-05/AC-1 (biên): totalCredits = 0 hợp lệ, âm bị 400; durationYears âm bị 400")
  void createVersion_numericBoundaries() throws Exception {
    ProgramVersionRequest zero = request("V-ZERO");
    zero.setTotalCredits(0);
    mockMvc.perform(postAs(manager, VERSIONS, zero)).andExpect(status().isCreated());

    ProgramVersionRequest negativeCredits = request("V-NEG-CREDITS");
    negativeCredits.setTotalCredits(-1);
    mockMvc
        .perform(postAs(manager, VERSIONS, negativeCredits))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("totalCredits")));

    ProgramVersionRequest negativeDuration = request("V-NEG-DURATION");
    negativeDuration.setDurationYears(-0.5);
    mockMvc
        .perform(postAs(manager, VERSIONS, negativeDuration))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("durationYears")));
  }

  // ------------------------------------------------------------------ US-05/AC-2

  @Test
  @DisplayName(
      "US-05/AC-2: trùng (programId, version) -> 409 CONFLICT, kể cả khi khác khoảng trắng")
  void createVersion_duplicateIsConflict() throws Exception {
    mockMvc.perform(postAs(manager, VERSIONS, request("V-DUP"))).andExpect(status().isCreated());

    mockMvc
        .perform(postAs(manager, VERSIONS, request("V-DUP")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"))
        .andExpect(jsonPath("$.status").value(409));
    // version được cắt khoảng trắng trước khi so sánh.
    mockMvc
        .perform(postAs(manager, VERSIONS, request("  V-DUP  ")))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("US-05/AC-2 (biên): cùng mã version ở CTĐT khác không bị coi là trùng")
  void createVersion_sameVersionInOtherProgramIsAllowed() throws Exception {
    Program other = fixtures.program();
    String managerWithBoth =
        fixtures.bearerFor(
            Role.PROGRAM_MANAGER,
            UserScope.builder().programIds(List.of(program.getId(), other.getId())).build());
    mockMvc
        .perform(postAs(managerWithBoth, VERSIONS, request("V-SHARED")))
        .andExpect(status().isCreated());
    ProgramVersionRequest forOther =
        ProgramVersionRequest.builder().programId(other.getId()).version("V-SHARED").build();
    mockMvc.perform(postAs(managerWithBoth, VERSIONS, forOther)).andExpect(status().isCreated());
  }

  @Test
  @DisplayName("US-05/AC-2: đổi version sang mã đã có của phiên bản khác cùng CTĐT -> 409")
  void updateVersion_renameToExistingIsConflict() throws Exception {
    ProgramVersion first = fixtures.programVersion(program, ProgramVersionStatus.DRAFT);
    ProgramVersion second = fixtures.programVersion(program, ProgramVersionStatus.DRAFT);
    ProgramVersionUpdateRequest body =
        ProgramVersionUpdateRequest.builder().version(first.getVersion()).build();

    mockMvc
        .perform(putAs(manager, VERSION_BY_ID, body, second.getId()))
        .andExpect(status().isConflict());
  }

  // ------------------------------------------------------------------ US-05/AC-3

  @Test
  @DisplayName("US-05/AC-3: programId hợp lệ nhưng không tồn tại -> 422 BUSINESS_RULE_VIOLATION")
  void createVersion_missingProgramIsUnprocessable() throws Exception {
    String missingId = ApiTestFixtures.missingId();
    String managerWithMissingScope =
        fixtures.bearerFor(
            Role.PROGRAM_MANAGER, UserScope.builder().programIds(List.of(missingId)).build());
    ProgramVersionRequest body =
        ProgramVersionRequest.builder().programId(missingId).version("V-X").build();
    mockMvc
        .perform(postAs(managerWithMissingScope, VERSIONS, body))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
  }

  @ParameterizedTest(name = "programId = \"{0}\" -> 400")
  @ValueSource(
      strings = {
        "abc",
        "12345",
        "zzzzzzzzzzzzzzzzzzzzzzzz",
        "671dcba5f123456789abc77",
        "671dcba5f123456789abc7777"
      })
  @DisplayName("US-05/AC-3 (âm): programId sai định dạng ObjectId -> 400, không phải 422 hay 500")
  void createVersion_malformedProgramIdIsBadRequest(String programId) throws Exception {
    ProgramVersionRequest body =
        ProgramVersionRequest.builder().programId(programId).version("V-X").build();
    mockMvc
        .perform(postAs(manager, VERSIONS, body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("programId")));
  }

  // ------------------------------------------------------------------ US-05/AC-4

  static Stream<Arguments> statusTransitions() {
    var draft = ProgramVersionStatus.DRAFT;
    var active = ProgramVersionStatus.ACTIVE;
    var archived = ProgramVersionStatus.ARCHIVED;
    return Stream.of(
        Arguments.of(draft, active, 200),
        Arguments.of(active, archived, 200),
        Arguments.of(draft, archived, 422),
        Arguments.of(active, draft, 422),
        Arguments.of(archived, active, 422),
        Arguments.of(archived, draft, 422));
  }

  @ParameterizedTest(name = "{0} -> {1} => HTTP {2}")
  @MethodSource("statusTransitions")
  @DisplayName("US-05/AC-4: chỉ cho DRAFT -> ACTIVE -> ARCHIVED; đi ngược hoặc nhảy cóc -> 422")
  void updateVersion_statusTransition(
      ProgramVersionStatus from, ProgramVersionStatus to, int expectedHttp) throws Exception {
    ProgramVersion version = fixtures.programVersion(program, from);
    ProgramVersionStatusUpdateRequest body =
        ProgramVersionStatusUpdateRequest.builder().status(to).build();

    mockMvc
        .perform(putAs(manager, VERSION_STATUS, body, version.getId()))
        .andExpect(status().is(expectedHttp));

    ProgramVersionStatus expectedStored = expectedHttp == 200 ? to : from;
    assertThat(mongo().findById(version.getId(), ProgramVersion.class).getStatus())
        .isEqualTo(expectedStored);
  }

  @Test
  @DisplayName("US-05/AC-4 (biên): cập nhật thông tin phiên bản thì giữ nguyên trạng thái")
  void updateVersion_withoutStatusKeepsStatus() throws Exception {
    ProgramVersion version = fixtures.programVersion(program, ProgramVersionStatus.ACTIVE);
    ProgramVersionUpdateRequest body =
        ProgramVersionUpdateRequest.builder()
            .version(version.getVersion())
            .totalCredits(120)
            .build();

    mockMvc
        .perform(putAs(manager, VERSION_BY_ID, body, version.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.totalCredits").value(120));
  }

  @Test
  @DisplayName("US-05/AC-4 (âm): status ngoài enum, hoặc là số/chuỗi số (thứ tự enum) -> 400")
  void updateVersion_unknownStatusIsBadRequest() throws Exception {
    ProgramVersion version = fixtures.programVersion(program, ProgramVersionStatus.DRAFT);
    // "1" và 1 từng bị Jackson hiểu là thứ tự enum (ACTIVE); xem JacksonConfig.
    for (String status : new String[] {"\"PUBLISHED\"", "1", "\"1\"", "0", "\"\""}) {
      String body = "{\"status\":" + status + "}";

      mockMvc
          .perform(putAs(manager, VERSION_STATUS, body, version.getId()))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
    assertThat(mongo().findById(version.getId(), ProgramVersion.class).getStatus())
        .isEqualTo(ProgramVersionStatus.DRAFT);
  }

  @Test
  @DisplayName("US-05/AC-4 (âm): PUT status với body rỗng hoặc thiếu status -> 400")
  void updateVersionStatus_emptyOrMissingStatusIsBadRequest() throws Exception {
    ProgramVersion version = fixtures.programVersion(program, ProgramVersionStatus.DRAFT);
    mockMvc
        .perform(putAs(manager, VERSION_STATUS, "{}", version.getId()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  // ------------------------------------------------------------------ ca âm chung (US-40)

  @Test
  @DisplayName("US-40/AC-1: body rỗng, {} và JSON hỏng đều trả 400 VALIDATION_ERROR, không 500")
  void createVersion_emptyAndMalformedBodies() throws Exception {
    mockMvc
        .perform(postAs(manager, VERSIONS, null))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    mockMvc
        .perform(postAs(manager, VERSIONS, "{"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    mockMvc
        .perform(postAs(manager, VERSIONS, "{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("programId")))
        .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("version")));
  }

  @ParameterizedTest(name = "{0} -> 400")
  @ValueSource(strings = {"abc", "12345", "zzzzzzzzzzzzzzzzzzzzzzzz", "671dcba5f123456789abc7777"})
  @DisplayName("US-40 (biên): ObjectId sai định dạng ở path -> 400 VALIDATION_ERROR, không 500")
  void getVersion_malformedIdIsBadRequest(String id) throws Exception {
    mockMvc
        .perform(getAs(manager, VERSION_BY_ID, id))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  @Test
  @DisplayName("US-40: ObjectId hợp lệ nhưng không tồn tại -> 404 NOT_FOUND")
  void getVersion_unknownIdIsNotFound() throws Exception {
    mockMvc
        .perform(getAs(manager, VERSION_BY_ID, ApiTestFixtures.missingId()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("US-40 (âm): query programId sai định dạng -> 400")
  void listVersions_malformedProgramIdFilterIsBadRequest() throws Exception {
    mockMvc
        .perform(getAs(manager, VERSIONS + "?programId=not-an-object-id"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  // ------------------------------------------------------------------ quyền (401/403)

  @Test
  @DisplayName("US-03/AC-2: không đăng nhập -> 401 trên GET và POST program-versions")
  void anonymousIsUnauthorized() throws Exception {
    mockMvc.perform(getAs(null, VERSIONS)).andExpect(status().isUnauthorized());
    mockMvc.perform(postAs(null, VERSIONS, request("V-A"))).andExpect(status().isUnauthorized());
  }

  @ParameterizedTest(name = "{0} không được ghi program-versions")
  @EnumSource(
      value = Role.class,
      names = {"PROGRAM_MANAGER"},
      mode = EnumSource.Mode.EXCLUDE)
  @DisplayName(
      "F02 mục 6: chỉ PROGRAM_MANAGER được ghi; vai trò khác (kể cả ADMIN) -> 403, đọc vẫn được")
  void onlyProgramManagerMayWrite(Role role) throws Exception {
    String bearer = fixtures.bearerFor(role, null);
    mockMvc
        .perform(postAs(bearer, VERSIONS, request("V-" + role)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    mockMvc
        .perform(getAs(bearer, VERSIONS + "?programId=" + program.getId()))
        .andExpect(status().isOk());
  }

  // ------------------------------------------------------------------ phân trang

  @Test
  @DisplayName(
      "US-02/AC-6 (áp dụng cho F02): phân trang biên trên danh sách phiên bản của một CTĐT")
  void listVersions_paginationBoundaries() throws Exception {
    // Ba phiên bản, sắp theo version tăng dần: V-A < V-B < V-C.
    for (String v : new String[] {"V-C", "V-A", "V-B"}) {
      mockMvc.perform(postAs(manager, VERSIONS, request(v))).andExpect(status().isCreated());
    }
    String base = VERSIONS + "?programId=" + program.getId();

    // totalElements = 3, size = 2 nên totalPages = ceil(3 / 2) = 2.
    mockMvc
        .perform(getAs(manager, base + "&page=0&size=2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[*].version", contains("V-A", "V-B")))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(2))
        .andExpect(jsonPath("$.totalElements").value(3))
        .andExpect(jsonPath("$.totalPages").value(2))
        .andExpect(jsonPath("$.last").value(false));
    mockMvc
        .perform(getAs(manager, base + "&page=1&size=2"))
        .andExpect(jsonPath("$.content[*].version", contains("V-C")))
        .andExpect(jsonPath("$.last").value(true));
    // Trang vượt quá cuối: rỗng nhưng hợp lệ.
    mockMvc
        .perform(getAs(manager, base + "&page=9&size=2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(0)))
        .andExpect(jsonPath("$.totalElements").value(3))
        .andExpect(jsonPath("$.last").value(true));
    // size = 1 và size = 100 là hai biên hợp lệ: 3 trang / 1 trang.
    mockMvc.perform(getAs(manager, base + "&size=1")).andExpect(jsonPath("$.totalPages").value(3));
    mockMvc
        .perform(getAs(manager, base + "&size=100"))
        .andExpect(jsonPath("$.totalPages").value(1))
        .andExpect(jsonPath("$.last").value(true));
  }

  @ParameterizedTest(name = "{0} -> 400")
  @ValueSource(strings = {"page=-1", "size=0", "size=101", "size=-3", "page=abc", "size=abc"})
  @DisplayName("US-02/AC-6 (áp dụng cho F02): page/size ngoài biên -> 400 VALIDATION_ERROR")
  void listVersions_invalidPaginationIsBadRequest(String query) throws Exception {
    mockMvc
        .perform(getAs(manager, VERSIONS + "?" + query))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }
}
