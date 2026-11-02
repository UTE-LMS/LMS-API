package com.hcmute.lms.lmsapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hcmute.lms.lmsapi.document.Course;
import com.hcmute.lms.lmsapi.document.CoursePiContribution;
import com.hcmute.lms.lmsapi.document.Pi;
import com.hcmute.lms.lmsapi.document.Plo;
import com.hcmute.lms.lmsapi.document.ProgramVersion;
import com.hcmute.lms.lmsapi.dto.CoursePiContributionRequest;
import com.hcmute.lms.lmsapi.enums.PiContributionType;
import com.hcmute.lms.lmsapi.enums.ProgramVersionStatus;
import com.hcmute.lms.lmsapi.enums.Role;
import com.hcmute.lms.lmsapi.testsupport.ApiTestFixtures;
import com.hcmute.lms.lmsapi.testsupport.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MvcResult;

/**
 * F02 / US-07: ma trận học phần đóng góp PI (I/R/M) qua HTTP. I/R/M là vai trò sư phạm, không phải
 * trọng số; ô trống của ma trận là "không có bản ghi" (F02 mục 4.6).
 */
@DisplayName("F02 / US-07: API ma trận I/R/M")
class CoursePiContributionApiIntegrationTest extends IntegrationTestBase {

  private static final String ROWS = "/api/v1/program-versions/{pv}/pi-contributions";
  private static final String ROW_BY_ID = "/api/v1/program-versions/{pv}/pi-contributions/{id}";

  private String manager;
  private ProgramVersion version;
  private Pi pi;
  private Course course;

  @BeforeEach
  void setUp() {
    manager = fixtures.bearerFor(Role.PROGRAM_MANAGER, null);
    version = fixtures.programVersion(fixtures.program(), ProgramVersionStatus.DRAFT);
    Plo plo = fixtures.plo(version, "PLO1", 1);
    pi = fixtures.pi(version, plo, "PI1.1", 1);
    course = fixtures.course();
  }

  private CoursePiContributionRequest request(PiContributionType type) {
    return CoursePiContributionRequest.builder()
        .courseId(course.getId())
        .piId(pi.getId())
        .contribution(type)
        .build();
  }

  // ------------------------------------------------------------------ US-07/AC-1

  @ParameterizedTest(name = "contribution = {0} -> 201")
  @EnumSource(PiContributionType.class)
  @DisplayName("US-07/AC-1: thêm dòng ma trận với I, R, M -> 201, lưu nguyên mức, chưa xác nhận")
  void addRow_eachContributionType(PiContributionType type) throws Exception {
    MvcResult result =
        mockMvc
            .perform(postAs(manager, ROWS, request(type), version.getId()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.contribution").value(type.name()))
            .andExpect(jsonPath("$.programVersionId").value(version.getId()))
            .andExpect(jsonPath("$.courseId").value(course.getId()))
            .andExpect(jsonPath("$.piId").value(pi.getId()))
            .andExpect(jsonPath("$.verification.status").value("NEEDS_CLARIFICATION"))
            .andReturn();

    CoursePiContribution stored =
        mongo().findById(read(result, "$.id").toString(), CoursePiContribution.class);
    assertThat(stored).isNotNull();
    assertThat(stored.getContribution()).isEqualTo(type);
  }

  @Test
  @DisplayName("US-07/AC-1: ô trống = không có bản ghi; danh sách chỉ chứa các dòng đã thêm")
  void emptyCellHasNoRecord() throws Exception {
    mockMvc
        .perform(getAs(manager, ROWS, version.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(0)));

    mockMvc.perform(postAs(manager, ROWS, request(PiContributionType.R), version.getId()));
    mockMvc
        .perform(getAs(manager, ROWS, version.getId()))
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].contribution").value("R"));
  }

  // ------------------------------------------------------------------ US-07/AC-2

  @Test
  @DisplayName("US-07/AC-2: trùng (programVersionId, courseId, piId) -> 409, không ghi đè mức cũ")
  void duplicateRowIsConflictAndKeepsOriginal() throws Exception {
    mockMvc
        .perform(postAs(manager, ROWS, request(PiContributionType.I), version.getId()))
        .andExpect(status().isCreated());

    mockMvc
        .perform(postAs(manager, ROWS, request(PiContributionType.M), version.getId()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"));

    mockMvc
        .perform(getAs(manager, ROWS, version.getId()))
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].contribution").value("I"));
  }

  @Test
  @DisplayName("US-07/AC-2 (biên): cùng học phần và PI ở phiên bản CTĐT khác không bị coi là trùng")
  void sameCourseAndPiInOtherVersionIsAllowed() throws Exception {
    mockMvc.perform(postAs(manager, ROWS, request(PiContributionType.I), version.getId()));
    ProgramVersion other = fixtures.programVersion(fixtures.program(), ProgramVersionStatus.DRAFT);
    Pi otherPi = fixtures.pi(other, fixtures.plo(other, "PLO1", 1), "PI1.1", 1);
    CoursePiContributionRequest body =
        CoursePiContributionRequest.builder()
            .courseId(course.getId())
            .piId(otherPi.getId())
            .contribution(PiContributionType.I)
            .build();
    mockMvc.perform(postAs(manager, ROWS, body, other.getId())).andExpect(status().isCreated());
  }

  @ParameterizedTest(name = "contribution = {0} -> 400")
  @ValueSource(strings = {"X", "i", "r", "m", "", "IRM", "1", "2", "3", "WEIGHT"})
  @DisplayName("US-07/AC-2: contribution khác I/R/M (kể cả viết thường, số 1/2/3) -> 400")
  void invalidContributionIsBadRequest(String value) throws Exception {
    String body =
        "{\"courseId\":\""
            + course.getId()
            + "\",\"piId\":\""
            + pi.getId()
            + "\",\"contribution\":\""
            + value
            + "\"}";
    mockMvc
        .perform(postAs(manager, ROWS, body, version.getId()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  @Test
  @DisplayName("US-07/AC-2: contribution là số hoặc thiếu -> 400 (I/R/M không đổi thành 1/2/3)")
  void numericOrMissingContributionIsBadRequest() throws Exception {
    String prefix = "{\"courseId\":\"" + course.getId() + "\",\"piId\":\"" + pi.getId() + "\"";
    mockMvc
        .perform(postAs(manager, ROWS, prefix + ",\"contribution\":2}", version.getId()))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(postAs(manager, ROWS, prefix + "}", version.getId()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("contribution")));
  }

  // ------------------------------------------------------------------ US-07/AC-3

  @Test
  @DisplayName("US-07/AC-3: piId thuộc phiên bản CTĐT khác -> 422 dù PI có thật")
  void piFromOtherVersionIsUnprocessable() throws Exception {
    ProgramVersion other = fixtures.programVersion(fixtures.program(), ProgramVersionStatus.DRAFT);
    Pi foreignPi = fixtures.pi(other, fixtures.plo(other, "PLO1", 1), "PI1.1", 1);
    CoursePiContributionRequest body =
        CoursePiContributionRequest.builder()
            .courseId(course.getId())
            .piId(foreignPi.getId())
            .contribution(PiContributionType.I)
            .build();

    mockMvc
        .perform(postAs(manager, ROWS, body, version.getId()))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
  }

  @Test
  @DisplayName("US-07/AC-3: courseId hoặc piId hợp lệ nhưng không tồn tại -> 422")
  void missingCourseOrPiIsUnprocessable() throws Exception {
    CoursePiContributionRequest missingCourse =
        CoursePiContributionRequest.builder()
            .courseId(ApiTestFixtures.missingId())
            .piId(pi.getId())
            .contribution(PiContributionType.I)
            .build();
    mockMvc
        .perform(postAs(manager, ROWS, missingCourse, version.getId()))
        .andExpect(status().isUnprocessableEntity());

    CoursePiContributionRequest missingPi =
        CoursePiContributionRequest.builder()
            .courseId(course.getId())
            .piId(ApiTestFixtures.missingId())
            .contribution(PiContributionType.I)
            .build();
    mockMvc
        .perform(postAs(manager, ROWS, missingPi, version.getId()))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  @DisplayName(
      "US-07/AC-3 (âm): courseId/piId sai định dạng -> 400; phiên bản không tồn tại -> 404")
  void malformedIdsAndUnknownVersion() throws Exception {
    CoursePiContributionRequest badCourse =
        CoursePiContributionRequest.builder()
            .courseId("khong-phai-objectid")
            .piId(pi.getId())
            .contribution(PiContributionType.I)
            .build();
    mockMvc
        .perform(postAs(manager, ROWS, badCourse, version.getId()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("courseId")));

    mockMvc
        .perform(postAs(manager, ROWS, request(PiContributionType.I), ApiTestFixtures.missingId()))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(postAs(manager, ROWS, request(PiContributionType.I), "xyz"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("US-40/AC-1: body rỗng, {} và JSON hỏng -> 400 VALIDATION_ERROR")
  void emptyAndMalformedBodies() throws Exception {
    mockMvc
        .perform(postAs(manager, ROWS, null, version.getId()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    mockMvc.perform(postAs(manager, ROWS, "{", version.getId())).andExpect(status().isBadRequest());
    mockMvc
        .perform(postAs(manager, ROWS, "{}", version.getId()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("courseId")))
        .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("piId")))
        .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("contribution")));
  }

  // ------------------------------------------------------------------ xóa

  @Test
  @DisplayName("US-07: xóa dòng ma trận -> 204; xóa lại -> 404; dòng của phiên bản khác -> 404")
  void deleteRow() throws Exception {
    CoursePiContribution row = fixtures.contribution(version, course, pi, PiContributionType.R);
    ProgramVersion other = fixtures.programVersion(fixtures.program(), ProgramVersionStatus.DRAFT);

    mockMvc
        .perform(deleteAs(manager, ROW_BY_ID, other.getId(), row.getId()))
        .andExpect(status().isNotFound());
    assertThat(mongo().findById(row.getId(), CoursePiContribution.class)).isNotNull();

    mockMvc
        .perform(deleteAs(manager, ROW_BY_ID, version.getId(), row.getId()))
        .andExpect(status().isNoContent());
    assertThat(mongo().findById(row.getId(), CoursePiContribution.class)).isNull();
    mockMvc
        .perform(deleteAs(manager, ROW_BY_ID, version.getId(), row.getId()))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(deleteAs(manager, ROW_BY_ID, version.getId(), "khong-hop-le"))
        .andExpect(status().isBadRequest());
  }

  // ------------------------------------------------------------------ quyền

  @Test
  @DisplayName("US-03/AC-2: không đăng nhập -> 401 trên GET, POST, DELETE ma trận")
  void anonymousIsUnauthorized() throws Exception {
    CoursePiContribution row = fixtures.contribution(version, course, pi, PiContributionType.I);
    mockMvc.perform(getAs(null, ROWS, version.getId())).andExpect(status().isUnauthorized());
    mockMvc
        .perform(postAs(null, ROWS, request(PiContributionType.I), version.getId()))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(deleteAs(null, ROW_BY_ID, version.getId(), row.getId()))
        .andExpect(status().isUnauthorized());
  }

  @ParameterizedTest(name = "{0} không được ghi ma trận")
  @EnumSource(
      value = Role.class,
      names = {"PROGRAM_MANAGER"},
      mode = EnumSource.Mode.EXCLUDE)
  @DisplayName("F02 mục 6: chỉ PROGRAM_MANAGER ghi ma trận; vai trò khác -> 403, đọc vẫn được")
  void onlyProgramManagerMayWrite(Role role) throws Exception {
    String bearer = fixtures.bearerFor(role, null);
    CoursePiContribution row = fixtures.contribution(version, course, pi, PiContributionType.I);

    mockMvc
        .perform(postAs(bearer, ROWS, request(PiContributionType.M), version.getId()))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(deleteAs(bearer, ROW_BY_ID, version.getId(), row.getId()))
        .andExpect(status().isForbidden());
    assertThat(mongo().findById(row.getId(), CoursePiContribution.class)).isNotNull();
    mockMvc
        .perform(getAs(bearer, ROWS, version.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)));
  }
}
