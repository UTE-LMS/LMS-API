package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.document.AggregateResult;
import com.hcmute.lms.lmsapi.document.Assessment;
import com.hcmute.lms.lmsapi.document.CalculationRun;
import com.hcmute.lms.lmsapi.document.Clo;
import com.hcmute.lms.lmsapi.document.CourseSection;
import com.hcmute.lms.lmsapi.document.Enrollment;
import com.hcmute.lms.lmsapi.document.Plo;
import com.hcmute.lms.lmsapi.document.ProgramCourse;
import com.hcmute.lms.lmsapi.document.ProgramVersion;
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
import com.hcmute.lms.lmsapi.service.AnalyticsService;
import com.hcmute.lms.lmsapi.service.ScopeAuthorizationService;
import com.hcmute.lms.lmsapi.util.PageableValidationUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * Hiện thực nghiệp vụ báo cáo phân tích đa chiều (Milestone 1).
 *
 * <p>Đặc tính kỹ thuật:
 *
 * <ul>
 *   <li>Không tạo mới collection hoặc thay đổi schema MongoDB.
 *   <li>Đọc và tổng hợp độc quyền từ các lượt tính có trạng thái SUCCESS trong aggregate_results và
 *       student_results.
 *   <li>Tuân thủ bất biến 10 trường chỉ số và xử lý mẫu số bằng 0 (trả về null thay vì 0.0 hoặc
 *       0%).
 *   <li>Kiểm soát phân quyền theo vai trò và phạm vi thông qua ScopeAuthorizationService.
 *   <li>Hỗ trợ xuất CSV RFC 4180 kèm UTF-8 BOM.
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsServiceImpl implements AnalyticsService {

  private final MongoTemplate mongoTemplate;
  private final CalculationRunRepository calculationRunRepository;
  private final AggregateResultRepository aggregateResultRepository;
  private final StudentResultRepository studentResultRepository;
  private final CourseSectionRepository courseSectionRepository;
  private final TermRepository termRepository;
  private final ProgramCourseRepository programCourseRepository;
  private final CloRepository cloRepository;
  private final PiRepository piRepository;
  private final PloRepository ploRepository;
  private final AssessmentRepository assessmentRepository;
  private final ProgramVersionRepository programVersionRepository;
  private final ScopeAuthorizationService scopeAuthorizationService;
  private final MatrixCsvExporter matrixCsvExporter;
  private final MatrixExcelExporter matrixExcelExporter;
  private final UserRepository userRepository;
  private final StudentRepository studentRepository;
  private final EnrollmentRepository enrollmentRepository;

  @Override
  public AnalyticsMatrixResponse getAnalyticsMatrix(
      AnalyticsQueryRequest request, UserPrincipal principal) {
    if (request == null || request.getDimension() == null) {
      throw new IllegalArgumentException(
          "Yêu cầu truy vấn hoặc chiều phân tích không được để trống");
    }

    if (request.getDimension() == AnalyticsDimension.STUDENT) {
      int effectivePage =
          request.getPage() != null ? request.getPage() : PageableValidationUtils.DEFAULT_PAGE;
      int effectiveSize =
          request.getSize() != null ? request.getSize() : PageableValidationUtils.DEFAULT_SIZE;
      PageableValidationUtils.validate(effectivePage, effectiveSize);
      AnalyticsMatrixResponse filtered = buildFilteredMatrix(request, principal);
      return paginateStudentColumns(filtered, effectivePage, effectiveSize);
    }

    return buildFilteredMatrix(request, principal);
  }

  private AnalyticsMatrixResponse buildFilteredMatrix(
      AnalyticsQueryRequest request, UserPrincipal principal) {
    // 1. Xác thực phân quyền truy cập phạm vi (Scope-based Access Control)
    verifyScope(request, principal);

    // 2. Xác định lượt tính toán nguồn (Source of Truth - chỉ lấy lượt tính SUCCESS)
    CalculationRun targetRun = resolveCalculationRun(request, principal);

    // 3. Phân luồng tổng hợp theo các chiều phân tích
    AnalyticsMatrixResponse response =
        switch (request.getDimension()) {
          case TIME_TERM -> buildTimeTermMatrix(request, targetRun);
          case COURSE_SECTIONS -> buildCourseSectionsMatrix(request, targetRun, principal);
          case COHORT -> buildCohortMatrix(request, targetRun);
          case ASSESSMENT_COMPONENT -> buildAssessmentComponentMatrix(request, targetRun);
          case CURRICULUM_CLASSIFICATION -> buildCurriculumClassificationMatrix(request, targetRun);
          case COURSE_LECTURER -> buildCourseLecturerMatrix(request, targetRun, principal);
          case STUDENT -> buildStudentMatrix(request, targetRun, principal);
        };

    // 4. Lọc theo chuẩn đích danh và theo Đạt / Chưa đạt (sau khi đã tổng hợp)
    return AnalyticsServiceImpl.applyResultFilters(response, request);
  }

  private AnalyticsMatrixResponse paginateStudentColumns(
      AnalyticsMatrixResponse response, int page, int size) {
    List<MatrixHeader> allColumns =
        response.getColumns() != null ? response.getColumns() : List.of();
    long totalElements = allColumns.size();
    int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
    int from = (int) Math.min((long) page * size, totalElements);
    int to = (int) Math.min((long) from + size, totalElements);
    boolean isLast = totalElements == 0 || page >= totalPages - 1;

    List<MatrixHeader> pagedColumns = new ArrayList<>(allColumns.subList(from, to));
    List<List<MatrixMetricCell>> pagedMatrix = new ArrayList<>();
    if (response.getMatrix() != null) {
      for (List<MatrixMetricCell> row : response.getMatrix()) {
        int rowFrom = Math.min(from, row.size());
        int rowTo = Math.min(to, row.size());
        pagedMatrix.add(new ArrayList<>(row.subList(rowFrom, rowTo)));
      }
    }

    response.setColumns(pagedColumns);
    response.setMatrix(pagedMatrix);
    response.setPage(page);
    response.setSize(size);
    response.setTotalElements(totalElements);
    response.setTotalPages(totalPages);
    response.setLast(isLast);
    return response;
  }

  /**
   * Lọc dòng theo {@code subjectIds} và lọc dòng/cột theo {@code passed}. Ô còn lại giữ nguyên, nên
   * mẫu số và tỷ lệ của từng ô không đổi.
   */
  static AnalyticsMatrixResponse applyResultFilters(
      AnalyticsMatrixResponse response, AnalyticsQueryRequest request) {
    boolean filterSubjects =
        request.getSubjectIds() != null
            && !request.getSubjectIds().isEmpty()
            && request.getDimension() != AnalyticsDimension.CURRICULUM_CLASSIFICATION;
    Boolean passed = request.getPassed();
    if (!filterSubjects && passed == null) {
      return response;
    }

    List<MatrixHeader> rows = response.getRows();
    List<MatrixHeader> columns = response.getColumns();
    List<List<MatrixMetricCell>> matrix = response.getMatrix();

    List<Integer> keptRows = new ArrayList<>();
    for (int i = 0; i < rows.size(); i++) {
      MatrixHeader row = rows.get(i);
      if (filterSubjects
          && !request.getSubjectIds().contains(row.getId())
          && !request.getSubjectIds().contains(row.getCode())) {
        continue;
      }
      if (passed != null && !rowHasMatch(matrix, i, passed)) {
        continue;
      }
      keptRows.add(i);
    }

    List<Integer> keptColumns = new ArrayList<>();
    for (int col = 0; col < columns.size(); col++) {
      final int c = col;
      if (passed == null
          || keptRows.stream().anyMatch(i -> cellMatchesPassed(cellAt(matrix, i, c), passed))) {
        keptColumns.add(col);
      }
    }

    List<List<MatrixMetricCell>> newMatrix = new ArrayList<>();
    for (int i : keptRows) {
      List<MatrixMetricCell> newRow = new ArrayList<>();
      for (int col : keptColumns) {
        newRow.add(cellAt(matrix, i, col));
      }
      newMatrix.add(newRow);
    }
    response.setRows(keptRows.stream().map(rows::get).toList());
    response.setColumns(keptColumns.stream().map(columns::get).toList());
    response.setMatrix(newMatrix);
    return response;
  }

  private static boolean rowHasMatch(List<List<MatrixMetricCell>> matrix, int row, boolean passed) {
    return row < matrix.size()
        && matrix.get(row).stream().anyMatch(cell -> cellMatchesPassed(cell, passed));
  }

  private static MatrixMetricCell cellAt(List<List<MatrixMetricCell>> matrix, int row, int col) {
    if (row >= matrix.size()) {
      return null;
    }
    List<MatrixMetricCell> r = matrix.get(row);
    return col < r.size() ? r.get(col) : null;
  }

  private static boolean cellMatchesPassed(MatrixMetricCell cell, boolean passed) {
    if (cell == null) {
      return false;
    }
    Integer count = passed ? cell.getPassed() : cell.getFailed();
    return count != null && count > 0;
  }

  @Override
  public byte[] exportAnalyticsMatrixCsv(AnalyticsQueryRequest request, UserPrincipal principal) {
    if (request == null || request.getDimension() == null) {
      throw new IllegalArgumentException(
          "Yêu cầu truy vấn hoặc chiều phân tích không được để trống");
    }
    AnalyticsMatrixResponse matrix = buildFilteredMatrix(request, principal);
    return matrixCsvExporter.exportToCsv(matrix);
  }

  @Override
  public byte[] exportAnalyticsMatrixXlsx(AnalyticsQueryRequest request, UserPrincipal principal) {
    if (request == null || request.getDimension() == null) {
      throw new IllegalArgumentException(
          "Yêu cầu truy vấn hoặc chiều phân tích không được để trống");
    }
    AnalyticsMatrixResponse matrix = buildFilteredMatrix(request, principal);
    return matrixExcelExporter.exportToXlsx(matrix);
  }

  // =========================================================================
  // XÁC THỰC PHÂN QUYỀN (R3: SCOPE-BASED ACCESS CONTROL)
  // =========================================================================

  private record RoleFlags(
      boolean isAdmin,
      boolean isQa,
      boolean hasGlobalReadAccess,
      boolean isPm,
      boolean isLecturer) {}

  private RoleFlags resolveRoleFlags(UserPrincipal principal) {
    if (principal == null) {
      throw new AccessDeniedException("Người dùng chưa được xác thực");
    }
    List<Role> roles = principal.getRoles() != null ? principal.getRoles() : List.of();
    boolean isAdmin = roles.contains(Role.ADMIN);
    boolean isQa = roles.contains(Role.QA);
    boolean isPm = roles.contains(Role.PROGRAM_MANAGER);
    boolean isLecturer = roles.contains(Role.LECTURER);
    return new RoleFlags(isAdmin, isQa, isAdmin || isQa, isPm, isLecturer);
  }

  private void verifyScope(AnalyticsQueryRequest request, UserPrincipal principal) {
    RoleFlags roles = resolveRoleFlags(principal);
    if (roles.hasGlobalReadAccess()) {
      return;
    }

    boolean isQa = roles.isQa();
    boolean isLecturer = roles.isLecturer();
    boolean isPm = roles.isPm();

    boolean hasProgramVersion =
        request.getProgramVersionId() != null && !request.getProgramVersionId().isBlank();
    boolean hasCourse = request.getCourseId() != null && !request.getCourseId().isBlank();
    boolean hasSections = request.getSectionIds() != null && !request.getSectionIds().isEmpty();

    // Ràng buộc cho PROGRAM_MANAGER: Không cho phép truy vấn toàn hệ thống khi bỏ trống tham số
    if (isPm && !isQa) {
      if (!hasProgramVersion && !hasCourse) {
        throw new AccessDeniedException(
            "Quản lý CTĐT phải chỉ định phiên bản CTĐT hoặc học phần thuộc phạm vi quản lý");
      }
      if (request.getDimension() == AnalyticsDimension.ASSESSMENT_COMPONENT) {
        boolean validCourse =
            hasCourse
                && scopeAuthorizationService.canAccessCourse(
                    principal, request.getCourseId(), AccessType.READ);
        boolean validPv =
            hasProgramVersion
                && scopeAuthorizationService.canAccessProgramVersion(
                    principal, request.getProgramVersionId(), AccessType.READ);
        if (!validCourse && !validPv) {
          throw new AccessDeniedException(
              "Quản lý CTĐT phải chỉ định phiên bản CTĐT hoặc học phần thuộc phạm vi quản lý khi xem phân rã thành phần đánh giá");
        }
      }
    }

    // Ràng buộc cho LECTURER: Không cho phép truy cập báo cáo ngoài phạm vi phân công giảng dạy
    if (isLecturer && !isPm && !isQa) {
      if (request.getDimension() == AnalyticsDimension.COHORT) {
        throw new AccessDeniedException(
            "Giảng viên không có quyền truy cập báo cáo chuẩn đầu ra theo khóa");
      }

      if (request.getDimension() == AnalyticsDimension.TIME_TERM) {
        if (!hasCourse && !hasSections) {
          throw new AccessDeniedException(
              "Giảng viên phải chỉ định học phần hoặc lớp học phần phụ trách khi xem xu hướng thời gian");
        }
      }

      if (request.getDimension() == AnalyticsDimension.ASSESSMENT_COMPONENT) {
        if (!hasCourse || !isLecturerTaughtCourse(principal, request.getCourseId())) {
          throw new AccessDeniedException(
              "Giảng viên phải chỉ định học phần phụ trách khi xem phân rã thành phần đánh giá");
        }
      }

      if (request.getDimension() == AnalyticsDimension.CURRICULUM_CLASSIFICATION) {
        if (request.getClassificationType() == null
            || request.getClassificationType() == CurriculumClassificationType.PLO_CATEGORY) {
          throw new AccessDeniedException(
              "Giảng viên không có quyền truy cập báo cáo phân loại PLO");
        }
        if (!hasCourse) {
          throw new AccessDeniedException(
              "Giảng viên chỉ được xem phân loại chương trình trong phạm vi học phần phụ trách");
        }
      }

      if (request.getDimension() == AnalyticsDimension.COURSE_LECTURER) {
        if (!hasCourse && !hasSections) {
          throw new AccessDeniedException(
              "Giảng viên phải chỉ định học phần hoặc lớp học phần phụ trách khi xem hiệu quả giảng dạy");
        }
        if (hasCourse && !isLecturerTaughtCourse(principal, request.getCourseId())) {
          throw new AccessDeniedException(
              "Giảng viên không phụ trách học phần: " + request.getCourseId());
        }
        if (request.getLecturerId() != null && !request.getLecturerId().equals(principal.getId())) {
          throw new AccessDeniedException(
              "Giảng viên chỉ được xem hiệu quả giảng dạy của chính mình");
        }
        if (request.getLecturerIds() != null && !request.getLecturerIds().isEmpty()) {
          for (String lId : request.getLecturerIds()) {
            if (!principal.getId().equals(lId)) {
              throw new AccessDeniedException(
                  "Giảng viên chỉ được xem hiệu quả giảng dạy của chính mình");
            }
          }
        }
      }

      if (request.getDimension() == AnalyticsDimension.STUDENT) {
        if (!hasCourse && !hasSections) {
          throw new AccessDeniedException(
              "Giảng viên phải chỉ định học phần hoặc lớp học phần phụ trách khi xem kết quả theo sinh viên");
        }
        if (hasCourse && !isLecturerTaughtCourse(principal, request.getCourseId())) {
          throw new AccessDeniedException(
              "Giảng viên không phụ trách học phần: " + request.getCourseId());
        }
      }
    }

    // Kiểm tra quyền theo sectionIds nếu có
    if (hasSections) {
      for (String secId : request.getSectionIds()) {
        if (!scopeAuthorizationService.canAccessSection(principal, secId, AccessType.READ)) {
          throw new AccessDeniedException("Không có quyền truy cập lớp học phần: " + secId);
        }
      }
    }

    // Kiểm tra quyền theo courseId nếu có
    if (hasCourse) {
      boolean canAccessCourse =
          scopeAuthorizationService.canAccessCourse(
              principal, request.getCourseId(), AccessType.READ);
      if (!canAccessCourse) {
        // Cho phép Giảng viên nếu phụ trách ít nhất một lớp học phần trong môn học này
        boolean teachesSectionInCourse = isLecturerTaughtCourse(principal, request.getCourseId());
        if (!teachesSectionInCourse) {
          throw new AccessDeniedException(
              "Không có quyền truy cập học phần: " + request.getCourseId());
        }
      }
    }

    // Kiểm tra quyền theo programVersionId nếu có
    if (hasProgramVersion) {
      if (!scopeAuthorizationService.canAccessProgramVersion(
          principal, request.getProgramVersionId(), AccessType.READ)) {
        throw new AccessDeniedException(
            "Không có quyền truy cập phiên bản CTĐT: " + request.getProgramVersionId());
      }
    }
  }

  private boolean isLecturerTaughtCourse(UserPrincipal principal, String courseId) {
    if (courseId == null || courseId.isBlank()) {
      return false;
    }
    if (scopeAuthorizationService.canAccessCourse(principal, courseId, AccessType.READ)) {
      return true;
    }
    return courseSectionRepository.findByCourseId(courseId.trim()).stream()
        .anyMatch(
            s -> scopeAuthorizationService.canAccessLoadedSection(principal, s, AccessType.READ));
  }

  // =========================================================================
  // NGUỒN DỮ LIỆU TÍNH TOÁN (R2: SOURCE OF TRUTH)
  // =========================================================================

  private CalculationRun resolveCalculationRun(
      AnalyticsQueryRequest request, UserPrincipal principal) {
    if (request.getRunId() != null && !request.getRunId().isBlank()) {
      CalculationRun run =
          calculationRunRepository
              .findById(request.getRunId())
              .orElseThrow(
                  () ->
                      new BusinessRuleViolationException(
                          "Không tìm thấy lượt tính toán: " + request.getRunId()));
      if (run.getStatus() != CalculationStatus.SUCCESS) {
        throw new BusinessRuleViolationException(
            "Chỉ được tổng hợp dữ liệu từ lượt tính có trạng thái SUCCESS");
      }
      validateRunAuthorization(run, principal, request);
      return run;
    }

    // Nếu không chỉ định runId, tự động tìm lượt tính SUCCESS gần nhất theo scope
    if (request.getProgramVersionId() != null && !request.getProgramVersionId().isBlank()) {
      List<CalculationRun> runs =
          calculationRunRepository.findByProgramVersionIdOrderByStartedAtDesc(
              request.getProgramVersionId());
      Optional<CalculationRun> successRun =
          runs.stream().filter(r -> r.getStatus() == CalculationStatus.SUCCESS).findFirst();
      if (successRun.isPresent()) {
        return successRun.get();
      }
    }

    // Tìm kiếm trong toàn bộ các lượt tính SUCCESS mới nhất
    List<CalculationRun> allRuns = calculationRunRepository.findAllByOrderByStartedAtDesc();
    return allRuns.stream()
        .filter(r -> r.getStatus() == CalculationStatus.SUCCESS)
        .findFirst()
        .orElse(null);
  }

  private void validateRunAuthorization(
      CalculationRun run, UserPrincipal principal, AnalyticsQueryRequest request) {
    RoleFlags roles = resolveRoleFlags(principal);
    if (roles.hasGlobalReadAccess()) {
      return;
    }

    boolean isQa = roles.isQa();
    boolean isPm = roles.isPm();
    boolean isLecturer = roles.isLecturer();

    if (isPm && !isQa) {
      boolean authorized = false;
      String pvId = run.getProgramVersionId();
      if (pvId != null && !pvId.isBlank()) {
        try {
          if (scopeAuthorizationService.canAccessProgramVersion(principal, pvId, AccessType.READ)) {
            authorized = true;
          }
        } catch (Exception ignored) {
          // Tiếp tục kiểm tra phạm vi học phần nếu có
        }
      }
      if (!authorized && run.getScopeRef() != null && run.getScopeRef().getCourseIds() != null) {
        authorized =
            run.getScopeRef().getCourseIds().stream()
                .anyMatch(
                    cId ->
                        scopeAuthorizationService.canAccessCourse(principal, cId, AccessType.READ));
      }

      if (!authorized) {
        throw new AccessDeniedException("Không có quyền truy cập lượt tính toán: " + run.getId());
      }
    } else if (isLecturer && !isPm && !isQa) {
      boolean authorized = false;
      if (run.getScopeRef() != null) {
        if (run.getScopeRef().getSectionIds() != null
            && !run.getScopeRef().getSectionIds().isEmpty()) {
          authorized =
              run.getScopeRef().getSectionIds().stream()
                  .anyMatch(
                      sId ->
                          scopeAuthorizationService.canAccessSection(
                              principal, sId, AccessType.READ));
        }
        if (!authorized
            && run.getScopeRef().getCourseIds() != null
            && !run.getScopeRef().getCourseIds().isEmpty()) {
          authorized =
              run.getScopeRef().getCourseIds().stream()
                  .anyMatch(cId -> isLecturerTaughtCourse(principal, cId));
        }
      }

      if (!authorized
          && run.getProgramVersionId() != null
          && !run.getProgramVersionId().isBlank()) {
        String courseId = request.getCourseId();
        if ((courseId == null || courseId.isBlank())
            && request.getSectionIds() != null
            && !request.getSectionIds().isEmpty()) {
          courseId =
              courseSectionRepository
                  .findById(request.getSectionIds().get(0))
                  .map(CourseSection::getCourseId)
                  .orElse(null);
        }
        if (courseId != null
            && !courseId.isBlank()
            && isLecturerTaughtCourse(principal, courseId)) {
          authorized =
              programCourseRepository.existsByProgramVersionIdAndCourseId(
                  run.getProgramVersionId(), courseId);
        }
      }

      if (!authorized) {
        throw new AccessDeniedException("Không có quyền truy cập lượt tính toán: " + run.getId());
      }
    }
  }

  // =========================================================================
  // CHIỀU 1: XU HƯỚNG THỜI GIAN / HỌC KỲ (TIME_TERM)
  // =========================================================================

  private AnalyticsMatrixResponse buildTimeTermMatrix(
      AnalyticsQueryRequest request, CalculationRun run) {
    // Tự động suy ra courseId từ lớp đầu tiên nếu courseId bị trống
    if ((request.getCourseId() == null || request.getCourseId().isBlank())
        && request.getSectionIds() != null
        && !request.getSectionIds().isEmpty()) {
      courseSectionRepository
          .findById(request.getSectionIds().get(0))
          .ifPresent(s -> request.setCourseId(s.getCourseId()));
    }

    ResultLevel level =
        request.getLevel() != null
            ? request.getLevel()
            : (request.getCourseId() != null
                ? ResultLevel.CLO
                : (request.getProgramVersionId() != null ? ResultLevel.PLO : ResultLevel.CLO));

    boolean isOfficial = run != null && run.isOfficial();
    boolean isStale = run != null && run.isStale();
    String runId = run != null ? run.getId() : null;

    // 1. Xác định các cột: Các học kỳ (Terms)
    List<Term> terms =
        request.getTermIds() != null && !request.getTermIds().isEmpty()
            ? termRepository.findAllById(request.getTermIds())
            : termRepository.findAllByOrderByStartDateDesc();
    List<MatrixHeader> columns = toTermHeaders(terms);

    // 2. Xác định các dòng: CLO, PI, hoặc PLO
    List<MatrixHeader> rows = new ArrayList<>();
    if (level == ResultLevel.CLO) {
      rows.addAll(toCloHeaders(findClosForCourse(request.getCourseId())));
    } else if (level == ResultLevel.PLO && request.getProgramVersionId() != null) {
      rows.addAll(loadPloHeaders(request.getProgramVersionId()));
    } else if (level == ResultLevel.PI && request.getProgramVersionId() != null) {
      rows.addAll(loadPiHeaders(request.getProgramVersionId()));
    }

    // 3. Tổng hợp ô ma trận [dòng = subject, cột = term]
    List<List<MatrixMetricCell>> matrix = new ArrayList<>();
    if (runId != null) {
      List<AggregateResult> allAggregates =
          aggregateResultRepository.findByRunIdAndLevel(runId, level);

      // Tra cứu termId của section nếu aggregateResult chưa gắn sẵn scope.termId
      Map<String, String> sectionToTermMap = buildSectionToTermMap();

      for (MatrixHeader row : rows) {
        List<MatrixMetricCell> rowCells = new ArrayList<>();
        for (MatrixHeader col : columns) {
          String termId = col.getId();
          List<AggregateResult> matched =
              allAggregates.stream()
                  .filter(a -> Objects.equals(a.getSubjectId(), row.getId()))
                  .filter(a -> matchesTerm(a, termId, sectionToTermMap))
                  .toList();

          rowCells.add(combineAggregates(matched, isOfficial, isStale));
        }
        matrix.add(rowCells);
      }
    } else {
      matrix = fillEmptyMatrix(rows.size(), columns.size(), isOfficial, isStale);
    }

    return AnalyticsMatrixResponse.builder()
        .dimension(AnalyticsDimension.TIME_TERM)
        .level(level)
        .runId(runId)
        .isOfficial(isOfficial)
        .isStale(isStale)
        .columns(columns)
        .rows(rows)
        .matrix(matrix)
        .build();
  }

  // =========================================================================
  // CHIỀU 2: SO SÁNH CÁC LỚP TRONG CÙNG HỌC PHẦN (COURSE_SECTIONS)
  // =========================================================================

  private AnalyticsMatrixResponse buildCourseSectionsMatrix(
      AnalyticsQueryRequest request, CalculationRun run, UserPrincipal principal) {
    String courseId = resolveCourseId(request);

    if (courseId == null || courseId.isBlank()) {
      throw new IllegalArgumentException(
          "Học phần (courseId) là bắt buộc đối với chiều COURSE_SECTIONS");
    }

    boolean isOfficial = run != null && run.isOfficial();
    boolean isStale = run != null && run.isStale();
    String runId = run != null ? run.getId() : null;

    // 1. Xác định các cột: Các lớp học phần (Parallel Sections)
    List<CourseSection> sections;
    if (request.getSectionIds() != null && !request.getSectionIds().isEmpty()) {
      sections = courseSectionRepository.findAllById(request.getSectionIds());
    } else {
      sections = courseSectionRepository.findByCourseId(courseId);
      if (request.getTermIds() != null && !request.getTermIds().isEmpty()) {
        sections =
            sections.stream().filter(s -> request.getTermIds().contains(s.getTermId())).toList();
      }
    }

    // Lọc lại các lớp mà người dùng có quyền đọc (fail-closed cho Giảng viên)
    sections =
        sections.stream()
            .filter(
                s ->
                    scopeAuthorizationService.canAccessLoadedSection(principal, s, AccessType.READ))
            .sorted(Comparator.comparing(CourseSection::getCode))
            .toList();

    if (sections.isEmpty()
        && request.getSectionIds() != null
        && !request.getSectionIds().isEmpty()) {
      throw new AccessDeniedException(
          "Bạn không có quyền truy cập vào các lớp học phần được yêu cầu");
    }

    List<MatrixHeader> columns = toSectionHeaders(sections);

    // 2. Xác định các dòng: Danh sách CLO của học phần
    List<MatrixHeader> rows = toCloHeaders(findClosForCourse(courseId));

    // 3. Tổng hợp ô ma trận [dòng = CLO, cột = Lớp]
    List<List<MatrixMetricCell>> matrix = new ArrayList<>();
    if (runId != null) {
      List<AggregateResult> aggregates =
          aggregateResultRepository.findByRunIdAndLevel(runId, ResultLevel.CLO);

      for (MatrixHeader row : rows) {
        List<MatrixMetricCell> rowCells = new ArrayList<>();
        for (MatrixHeader col : columns) {
          Optional<AggregateResult> matched =
              aggregates.stream()
                  .filter(
                      a ->
                          Objects.equals(a.getSubjectId(), row.getId())
                              && Objects.equals(a.getSectionId(), col.getId()))
                  .findFirst();

          rowCells.add(
              matched
                  .map(agg -> toMetricCell(agg, isOfficial, isStale))
                  .orElseGet(() -> MatrixMetricCell.empty(isOfficial, isStale)));
        }
        matrix.add(rowCells);
      }
    } else {
      matrix = fillEmptyMatrix(rows.size(), columns.size(), isOfficial, isStale);
    }

    return AnalyticsMatrixResponse.builder()
        .dimension(AnalyticsDimension.COURSE_SECTIONS)
        .level(ResultLevel.CLO)
        .runId(runId)
        .isOfficial(isOfficial)
        .isStale(isStale)
        .columns(columns)
        .rows(rows)
        .matrix(matrix)
        .build();
  }

  // =========================================================================
  // CHIỀU 3: ĐỐI SÁNH THEO KHÓA TUYỂN SINH (COHORT)
  // =========================================================================

  private AnalyticsMatrixResponse buildCohortMatrix(
      AnalyticsQueryRequest request, CalculationRun run) {
    String programVersionId = request.getProgramVersionId();
    if (programVersionId == null || programVersionId.isBlank()) {
      throw new IllegalArgumentException(
          "Phiên bản CTĐT (programVersionId) là bắt buộc đối với chiều COHORT");
    }

    ResultLevel level = request.getLevel() != null ? request.getLevel() : ResultLevel.PLO;
    boolean isOfficial = run != null && run.isOfficial();
    boolean isStale = run != null && run.isStale();
    String runId = run != null ? run.getId() : null;

    // 1. Xác định các cột: Các khóa tuyển sinh (Cohorts)
    List<String> cohorts = request.getCohorts();
    if (cohorts == null || cohorts.isEmpty()) {
      ProgramVersion pv = programVersionRepository.findById(programVersionId).orElse(null);
      if (pv != null && pv.getApplicableCohort() != null && !pv.getApplicableCohort().isBlank()) {
        cohorts = List.of(pv.getApplicableCohort().trim());
      } else {
        cohorts = List.of("K21", "K22", "K23", "K24", "K25");
      }
    }

    List<MatrixHeader> columns =
        cohorts.stream()
            .map(c -> MatrixHeader.builder().id(c).code(c).name("Khóa " + c).build())
            .toList();

    // 2. Xác định các dòng: PLO hoặc PI
    List<MatrixHeader> rows =
        level == ResultLevel.PLO
            ? loadPloHeaders(programVersionId)
            : loadPiHeaders(programVersionId);

    // 3. Tổng hợp ô ma trận [dòng = PLO/PI, cột = Cohort]
    List<List<MatrixMetricCell>> matrix = new ArrayList<>();
    if (runId != null) {
      List<AggregateResult> aggregates =
          aggregateResultRepository.findByRunIdAndLevel(runId, level);

      // Tra cứu studentId sang cohort để dự phòng khi lượt tính không phân rã theo cohort trong
      // aggregate_results
      Map<String, String> studentToCohortMap = buildStudentToCohortMap(programVersionId);
      List<StudentResult> studentResults =
          studentResultRepository.findByRunIdAndLevel(runId, level);

      for (MatrixHeader row : rows) {
        List<MatrixMetricCell> rowCells = new ArrayList<>();
        for (MatrixHeader col : columns) {
          String cohort = col.getId();

          // Thử tra cứu từ aggregate_results trước
          Optional<AggregateResult> aggOpt =
              aggregates.stream()
                  .filter(
                      a ->
                          Objects.equals(a.getSubjectId(), row.getId())
                              && a.getScope() != null
                              && Objects.equals(a.getScope().getCohort(), cohort))
                  .findFirst();

          if (aggOpt.isPresent()) {
            rowCells.add(toMetricCell(aggOpt.get(), isOfficial, isStale));
          } else {
            // Tổng hợp từ student_results theo cohort của sinh viên
            List<StudentResult> studentMatches =
                studentResults.stream()
                    .filter(sr -> Objects.equals(sr.getSubjectId(), row.getId()))
                    .filter(sr -> Objects.equals(studentToCohortMap.get(sr.getStudentId()), cohort))
                    .toList();

            if (!studentMatches.isEmpty()) {
              int enrolled = studentMatches.size();
              int evaluated =
                  (int)
                      studentMatches.stream()
                          .filter(
                              sr ->
                                  sr.getStatus() == ResultStatus.COMPUTED && sr.getValue() != null)
                          .count();
              int passed =
                  (int)
                      studentMatches.stream()
                          .filter(sr -> Boolean.TRUE.equals(sr.getPassed()))
                          .count();
              int failed = Math.max(0, evaluated - passed);
              Double mean =
                  evaluated > 0
                      ? studentMatches.stream()
                          .filter(
                              sr ->
                                  sr.getStatus() == ResultStatus.COMPUTED && sr.getValue() != null)
                          .mapToDouble(StudentResult::getValue)
                          .average()
                          .orElse(0.0)
                      : null;

              rowCells.add(
                  MatrixMetricCell.calculate(
                      enrolled, passed, failed, mean, null, isOfficial, isStale));
            } else {
              rowCells.add(MatrixMetricCell.empty(isOfficial, isStale));
            }
          }
        }
        matrix.add(rowCells);
      }
    } else {
      matrix = fillEmptyMatrix(rows.size(), columns.size(), isOfficial, isStale);
    }

    return AnalyticsMatrixResponse.builder()
        .dimension(AnalyticsDimension.COHORT)
        .level(level)
        .runId(runId)
        .isOfficial(isOfficial)
        .isStale(isStale)
        .columns(columns)
        .rows(rows)
        .matrix(matrix)
        .build();
  }

  // =========================================================================
  // CHIỀU 4: PHÂN RÃ THEO THÀNH PHẦN ĐÁNH GIÁ (ASSESSMENT_COMPONENT)
  // =========================================================================

  private AnalyticsMatrixResponse buildAssessmentComponentMatrix(
      AnalyticsQueryRequest request, CalculationRun run) {
    boolean isOfficial = run != null && run.isOfficial();
    boolean isStale = run != null && run.isStale();
    String runId = run != null ? run.getId() : null;

    // 1. Xác định các cột: Các loại bài đánh giá (Assessment Types)
    List<AssessmentType> types = request.getAssessmentTypes();
    if (types == null || types.isEmpty()) {
      types =
          List.of(
              AssessmentType.QUIZ,
              AssessmentType.PRACTICAL,
              AssessmentType.PROJECT,
              AssessmentType.MIDTERM,
              AssessmentType.FINAL,
              AssessmentType.OTHER);
    }

    List<MatrixHeader> columns =
        types.stream()
            .map(t -> MatrixHeader.builder().id(t.name()).code(t.name()).name(t.name()).build())
            .toList();

    // 2. Xác định các dòng: Danh sách CLO
    List<Clo> clos;
    if (request.getCourseId() != null && !request.getCourseId().isBlank()) {
      clos = findClosForCourse(request.getCourseId());
    } else if (request.getProgramVersionId() != null && !request.getProgramVersionId().isBlank()) {
      List<ProgramCourse> pcs =
          programCourseRepository.findByProgramVersionIdOrderBySemesterAscCourseIdAsc(
              request.getProgramVersionId().trim());
      List<String> courseIds =
          pcs.stream()
              .map(ProgramCourse::getCourseId)
              .filter(cid -> cid != null && !cid.isBlank())
              .distinct()
              .toList();
      if (courseIds.isEmpty()) {
        clos = Collections.emptyList();
      } else {
        clos = mongoTemplate.find(Query.query(Criteria.where("courseId").in(courseIds)), Clo.class);
      }
    } else {
      clos = Collections.emptyList();
    }
    List<MatrixHeader> rows = toCloHeaders(clos);

    // 3. Tổng hợp ô ma trận [dòng = CLO, cột = AssessmentType]
    List<List<MatrixMetricCell>> matrix = new ArrayList<>();
    if (runId != null) {
      List<AggregateResult> aggregates =
          aggregateResultRepository.findByRunIdAndLevel(runId, ResultLevel.ASSESSMENT_CLO);

      // Tra cứu assessmentId -> AssessmentType
      Map<String, AssessmentType> assessmentTypeMap = buildAssessmentTypeMap();

      for (MatrixHeader row : rows) {
        List<MatrixMetricCell> rowCells = new ArrayList<>();
        for (MatrixHeader col : columns) {
          AssessmentType targetType = AssessmentType.valueOf(col.getId());
          List<AggregateResult> matched =
              aggregates.stream()
                  .filter(a -> Objects.equals(a.getSubjectId(), row.getId()))
                  .filter(a -> assessmentTypeMap.get(a.getAssessmentId()) == targetType)
                  .toList();

          rowCells.add(combineAggregates(matched, isOfficial, isStale));
        }
        matrix.add(rowCells);
      }
    } else {
      matrix = fillEmptyMatrix(rows.size(), columns.size(), isOfficial, isStale);
    }

    return AnalyticsMatrixResponse.builder()
        .dimension(AnalyticsDimension.ASSESSMENT_COMPONENT)
        .level(ResultLevel.ASSESSMENT_CLO)
        .runId(runId)
        .isOfficial(isOfficial)
        .isStale(isStale)
        .columns(columns)
        .rows(rows)
        .matrix(matrix)
        .build();
  }

  // =========================================================================
  // CHIỀU 5: PHÂN LOẠI KHUNG CHƯƠNG TRÌNH ĐÀO TẠO (CURRICULUM_CLASSIFICATION)
  // =========================================================================

  private AnalyticsMatrixResponse buildCurriculumClassificationMatrix(
      AnalyticsQueryRequest request, CalculationRun run) {
    CurriculumClassificationType classificationType =
        request.getClassificationType() != null
            ? request.getClassificationType()
            : CurriculumClassificationType.PLO_CATEGORY;

    boolean isOfficial = run != null && run.isOfficial();
    boolean isStale = run != null && run.isStale();
    String runId = run != null ? run.getId() : null;

    List<MatrixHeader> rows = new ArrayList<>();
    List<MatrixHeader> columns = new ArrayList<>();
    List<List<MatrixMetricCell>> matrix = new ArrayList<>();

    switch (classificationType) {
      case PLO_CATEGORY -> {
        // Dòng: Các nhóm PLO Category (GENERAL, SPECIALIZED, UNSPECIFIED)
        rows.add(
            MatrixHeader.builder()
                .id("GENERAL")
                .code("GENERAL")
                .name("Chuẩn đầu ra chung (General PLO)")
                .build());
        rows.add(
            MatrixHeader.builder()
                .id("SPECIALIZED")
                .code("SPECIALIZED")
                .name("Chuẩn đầu ra chuyên ngành (Specialized PLO)")
                .build());
        rows.add(
            MatrixHeader.builder()
                .id("UNSPECIFIED")
                .code("UNSPECIFIED")
                .name("Chưa phân loại (Unspecified PLO)")
                .build());

        // Cột: Các khóa tuyển sinh hoặc học kỳ
        List<String> cohorts =
            request.getCohorts() != null && !request.getCohorts().isEmpty()
                ? request.getCohorts()
                : List.of("K23", "K24", "K25");
        columns =
            cohorts.stream()
                .map(c -> MatrixHeader.builder().id(c).code(c).name("Khóa " + c).build())
                .toList();

        if (runId != null && request.getProgramVersionId() != null) {
          List<Plo> plos =
              ploRepository.findByProgramVersionIdOrderByDisplayOrderAsc(
                  request.getProgramVersionId());
          List<AggregateResult> aggregates =
              aggregateResultRepository.findByRunIdAndLevel(runId, ResultLevel.PLO);

          for (MatrixHeader row : rows) {
            PloCategory cat = PloCategory.valueOf(row.getId());
            Set<String> ploIdsInCat =
                plos.stream()
                    .filter(
                        p ->
                            (p.getCategory() == null && cat == PloCategory.UNSPECIFIED)
                                || p.getCategory() == cat)
                    .map(Plo::getId)
                    .collect(Collectors.toSet());

            List<MatrixMetricCell> rowCells = new ArrayList<>();
            for (MatrixHeader col : columns) {
              String cohort = col.getId();
              List<AggregateResult> matched =
                  aggregates.stream()
                      .filter(a -> ploIdsInCat.contains(a.getSubjectId()))
                      .filter(
                          a ->
                              a.getScope() != null
                                  && Objects.equals(a.getScope().getCohort(), cohort))
                      .toList();

              rowCells.add(combineAggregates(matched, isOfficial, isStale));
            }
            matrix.add(rowCells);
          }
        } else {
          matrix = fillEmptyMatrix(rows.size(), columns.size(), isOfficial, isStale);
        }
      }

      case COURSE_TYPE -> {
        // Dòng: Tính chất học phần (COMPULSORY vs ELECTIVE)
        rows.add(
            MatrixHeader.builder()
                .id("COMPULSORY")
                .code("COMPULSORY")
                .name("Học phần bắt buộc (Compulsory)")
                .build());
        rows.add(
            MatrixHeader.builder()
                .id("ELECTIVE")
                .code("ELECTIVE")
                .name("Học phần tự chọn (Elective)")
                .build());

        // Cột: Các học kỳ
        List<Term> terms =
            request.getTermIds() != null && !request.getTermIds().isEmpty()
                ? termRepository.findAllById(request.getTermIds())
                : termRepository.findAllByOrderByStartDateDesc();
        columns = toTermHeaders(terms);

        if (runId != null && request.getProgramVersionId() != null) {
          List<ProgramCourse> pcs =
              programCourseRepository.findByProgramVersionIdOrderBySemesterAscCourseIdAsc(
                  request.getProgramVersionId());
          List<AggregateResult> aggregates =
              aggregateResultRepository.findByRunIdAndLevel(runId, ResultLevel.CLO);
          Map<String, String> sectionToTermMap = buildSectionToTermMap();
          Map<String, String> cloToCourseMap = buildCloToCourseMap();

          for (MatrixHeader row : rows) {
            CourseType targetType = CourseType.valueOf(row.getId());
            Set<String> courseIdsInType =
                pcs.stream()
                    .filter(pc -> pc.getType() == targetType)
                    .map(ProgramCourse::getCourseId)
                    .collect(Collectors.toSet());

            List<MatrixMetricCell> rowCells = new ArrayList<>();
            for (MatrixHeader col : columns) {
              String termId = col.getId();
              List<AggregateResult> matched =
                  aggregates.stream()
                      .filter(
                          a -> {
                            String cId = cloToCourseMap.get(a.getSubjectId());
                            return cId != null && courseIdsInType.contains(cId);
                          })
                      .filter(a -> matchesTerm(a, termId, sectionToTermMap))
                      .toList();

              rowCells.add(combineAggregates(matched, isOfficial, isStale));
            }
            matrix.add(rowCells);
          }
        } else {
          matrix = fillEmptyMatrix(rows.size(), columns.size(), isOfficial, isStale);
        }
      }

      case BLOOM_LEVEL -> {
        // Dòng: 6 thang nhận thức Bloom (1 đến 6) - Bất biến: chỉ gom nhóm, không quy đổi điểm số
        String[] bloomLabels = {
          "Nhớ (Remember)",
          "Hiểu (Understand)",
          "Vận dụng (Apply)",
          "Phân tích (Analyze)",
          "Đánh giá (Evaluate)",
          "Sáng tạo (Create)"
        };
        for (int i = 1; i <= 6; i++) {
          rows.add(
              MatrixHeader.builder()
                  .id("BLOOM_" + i)
                  .code("BLOOM_" + i)
                  .name("Mức " + i + ": " + bloomLabels[i - 1])
                  .bloomLevel(i)
                  .build());
        }

        // Cột: Các học kỳ hoặc các lớp
        if (request.getCourseId() != null) {
          columns = toSectionHeaders(courseSectionRepository.findByCourseId(request.getCourseId()));
        } else {
          columns = toTermHeaders(termRepository.findAllByOrderByStartDateDesc());
        }

        if (runId != null) {
          List<Clo> clos = findClosForCourse(request.getCourseId());
          Map<Integer, Set<String>> closByBloom =
              clos.stream()
                  .filter(c -> c.getBloomLevel() != null)
                  .collect(
                      Collectors.groupingBy(
                          Clo::getBloomLevel, Collectors.mapping(Clo::getId, Collectors.toSet())));

          List<AggregateResult> aggregates =
              aggregateResultRepository.findByRunIdAndLevel(runId, ResultLevel.CLO);

          for (MatrixHeader row : rows) {
            int levelNum = row.getBloomLevel();
            Set<String> cloIdsAtLevel = closByBloom.getOrDefault(levelNum, Collections.emptySet());

            List<MatrixMetricCell> rowCells = new ArrayList<>();
            for (MatrixHeader col : columns) {
              List<AggregateResult> matched =
                  aggregates.stream()
                      .filter(a -> cloIdsAtLevel.contains(a.getSubjectId()))
                      .filter(
                          a ->
                              Objects.equals(a.getSectionId(), col.getId())
                                  || (a.getScope() != null
                                      && Objects.equals(a.getScope().getTermId(), col.getId())))
                      .toList();

              rowCells.add(combineAggregates(matched, isOfficial, isStale));
            }
            matrix.add(rowCells);
          }
        } else {
          matrix = fillEmptyMatrix(rows.size(), columns.size(), isOfficial, isStale);
        }
      }
    }

    return AnalyticsMatrixResponse.builder()
        .dimension(AnalyticsDimension.CURRICULUM_CLASSIFICATION)
        .level(ResultLevel.CLO)
        .runId(runId)
        .isOfficial(isOfficial)
        .isStale(isStale)
        .columns(columns)
        .rows(rows)
        .matrix(matrix)
        .build();
  }

  // =========================================================================
  // CHIỀU 6: ĐÁNH GIÁ HIỆU QUẢ GIẢNG DẠY MÔN HỌC THEO GIẢNG VIÊN (COURSE_LECTURER)
  // =========================================================================

  private AnalyticsMatrixResponse buildCourseLecturerMatrix(
      AnalyticsQueryRequest request, CalculationRun run, UserPrincipal principal) {
    String courseId = resolveCourseId(request);

    if (courseId == null || courseId.isBlank()) {
      throw new IllegalArgumentException(
          "Học phần (courseId) là bắt buộc đối với chiều COURSE_LECTURER");
    }

    boolean isOfficial = run != null && run.isOfficial();
    boolean isStale = run != null && run.isStale();
    String runId = run != null ? run.getId() : null;

    // 1. Xác định các lớp học phần
    List<CourseSection> sections;
    if (request.getSectionIds() != null && !request.getSectionIds().isEmpty()) {
      sections = courseSectionRepository.findAllById(request.getSectionIds());
    } else {
      sections = courseSectionRepository.findByCourseId(courseId.trim());
      if (request.getTermIds() != null && !request.getTermIds().isEmpty()) {
        sections =
            sections.stream().filter(s -> request.getTermIds().contains(s.getTermId())).toList();
      }
    }

    // 2. Xác định danh sách giảng viên phụ trách (Cột)
    RoleFlags roles = resolveRoleFlags(principal);
    boolean isLecturerOnly =
        roles.isLecturer() && !roles.isPm() && !roles.isQa() && !roles.isAdmin();

    Set<String> uniqueLecturerIds =
        sections.stream()
            .map(CourseSection::getLecturerIds)
            .filter(Objects::nonNull)
            .flatMap(List::stream)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

    List<String> targetLecturerIds;
    if (isLecturerOnly) {
      targetLecturerIds = List.of(principal.getId());
    } else if (request.getLecturerId() != null && !request.getLecturerId().isBlank()) {
      targetLecturerIds = List.of(request.getLecturerId().trim());
    } else if (request.getLecturerIds() != null && !request.getLecturerIds().isEmpty()) {
      targetLecturerIds =
          request.getLecturerIds().stream().filter(uniqueLecturerIds::contains).toList();
    } else {
      targetLecturerIds = new ArrayList<>(uniqueLecturerIds);
    }

    List<User> lecturers =
        targetLecturerIds.isEmpty() ? List.of() : userRepository.findAllById(targetLecturerIds);
    lecturers = new ArrayList<>(lecturers);
    lecturers.sort(
        Comparator.comparing(User::getUsername, Comparator.nullsLast(String::compareTo)));

    List<MatrixHeader> columns =
        lecturers.stream()
            .map(
                u ->
                    MatrixHeader.builder()
                        .id(u.getId())
                        .code(u.getUsername())
                        .name(u.getFullName() != null ? u.getFullName() : u.getUsername())
                        .build())
            .toList();

    // 3. Dòng là các CLO của học phần
    List<MatrixHeader> rows = toCloHeaders(findClosForCourse(courseId));

    // 4. Tổng hợp ô ma trận [dòng = CLO, cột = Giảng viên]
    List<List<MatrixMetricCell>> matrix = new ArrayList<>();
    if (runId != null) {
      List<AggregateResult> aggregates =
          aggregateResultRepository.findByRunIdAndLevel(runId, ResultLevel.CLO);

      for (MatrixHeader row : rows) {
        List<MatrixMetricCell> rowCells = new ArrayList<>();
        for (MatrixHeader col : columns) {
          String lecturerId = col.getId();
          List<String> assignedSectionIds =
              sections.stream()
                  .filter(
                      s -> s.getLecturerIds() != null && s.getLecturerIds().contains(lecturerId))
                  .map(CourseSection::getId)
                  .toList();

          List<AggregateResult> matched =
              aggregates.stream()
                  .filter(
                      a ->
                          Objects.equals(a.getSubjectId(), row.getId())
                              && assignedSectionIds.contains(a.getSectionId()))
                  .toList();

          rowCells.add(combineAggregates(matched, isOfficial, isStale));
        }
        matrix.add(rowCells);
      }
    } else {
      matrix = fillEmptyMatrix(rows.size(), columns.size(), isOfficial, isStale);
    }

    return AnalyticsMatrixResponse.builder()
        .dimension(AnalyticsDimension.COURSE_LECTURER)
        .level(ResultLevel.CLO)
        .runId(runId)
        .isOfficial(isOfficial)
        .isStale(isStale)
        .columns(columns)
        .rows(rows)
        .matrix(matrix)
        .build();
  }

  // =========================================================================
  // CHIỀU 7: PHÂN TÍCH KẾT QUẢ THEO SINH VIÊN (STUDENT)
  // =========================================================================

  private AnalyticsMatrixResponse buildStudentMatrix(
      AnalyticsQueryRequest request, CalculationRun run, UserPrincipal principal) {
    String courseId = resolveCourseId(request);

    ResultLevel level;
    List<MatrixHeader> rows = new ArrayList<>();

    if (courseId != null && !courseId.isBlank()) {
      level = ResultLevel.CLO;
      rows.addAll(toCloHeaders(findClosForCourse(courseId)));
    } else if (request.getProgramVersionId() != null && !request.getProgramVersionId().isBlank()) {
      level =
          request.getLevel() != null && request.getLevel() == ResultLevel.PI
              ? ResultLevel.PI
              : ResultLevel.PLO;
      if (level == ResultLevel.PI) {
        rows.addAll(loadPiHeaders(request.getProgramVersionId()));
      } else {
        rows.addAll(loadPloHeaders(request.getProgramVersionId()));
      }
    } else if (request.getSectionIds() != null && !request.getSectionIds().isEmpty()) {
      level = ResultLevel.CLO;
      CourseSection sec =
          courseSectionRepository.findById(request.getSectionIds().get(0)).orElse(null);
      if (sec != null && sec.getCourseId() != null) {
        rows.addAll(toCloHeaders(findClosForCourse(sec.getCourseId())));
      }
    } else {
      throw new IllegalArgumentException(
          "Cần chỉ định học phần (courseId), lớp học phần (sectionIds), hoặc phiên bản CTĐT (programVersionId) đối với chiều STUDENT");
    }

    boolean isOfficial = run != null && run.isOfficial();
    boolean isStale = run != null && run.isStale();
    String runId = run != null ? run.getId() : null;

    // 1. Xác định danh sách sinh viên mục tiêu (Cột)
    List<Student> students = resolveTargetStudents(request, courseId, run, level, principal);
    List<MatrixHeader> columns =
        students.stream()
            .map(
                s ->
                    MatrixHeader.builder()
                        .id(s.getId())
                        .code(s.getStudentCode())
                        .name(s.getFullName() != null ? s.getFullName() : s.getStudentCode())
                        .build())
            .toList();

    // 2. Tổng hợp ô ma trận từ student_results
    List<List<MatrixMetricCell>> matrix = new ArrayList<>();
    if (runId != null && !columns.isEmpty() && !rows.isEmpty()) {
      List<StudentResult> studentResults =
          studentResultRepository.findByRunIdAndLevel(runId, level);

      // Tra cứu nhanh: subjectId + "#" + studentId -> StudentResult
      Map<String, StudentResult> resultMap = new LinkedHashMap<>();
      for (StudentResult sr : studentResults) {
        if (sr.getSubjectId() != null && sr.getStudentId() != null) {
          String key = sr.getSubjectId() + "#" + sr.getStudentId();
          resultMap.putIfAbsent(key, sr);
        }
      }

      for (MatrixHeader row : rows) {
        List<MatrixMetricCell> rowCells = new ArrayList<>();
        for (MatrixHeader col : columns) {
          String key = row.getId() + "#" + col.getId();
          StudentResult sr = resultMap.get(key);

          if (sr != null && sr.getStatus() == ResultStatus.COMPUTED && sr.getValue() != null) {
            int passed = Boolean.TRUE.equals(sr.getPassed()) ? 1 : 0;
            int failed = passed == 1 ? 0 : 1;
            Double mean = sr.getValue();
            rowCells.add(
                MatrixMetricCell.calculate(1, passed, failed, mean, null, isOfficial, isStale));
          } else if (sr != null && sr.getStatus() != ResultStatus.COMPUTED) {
            rowCells.add(
                MatrixMetricCell.builder()
                    .enrolled(1)
                    .evaluated(0)
                    .passed(0)
                    .failed(0)
                    .insufficientData(1)
                    .mean(null)
                    .passRate(null)
                    .passRateOfEnrolled(null)
                    .isOfficial(isOfficial)
                    .isStale(isStale)
                    .build());
          } else {
            rowCells.add(MatrixMetricCell.empty(isOfficial, isStale));
          }
        }
        matrix.add(rowCells);
      }
    } else {
      matrix = fillEmptyMatrix(rows.size(), columns.size(), isOfficial, isStale);
    }

    return AnalyticsMatrixResponse.builder()
        .dimension(AnalyticsDimension.STUDENT)
        .level(level)
        .runId(runId)
        .isOfficial(isOfficial)
        .isStale(isStale)
        .columns(columns)
        .rows(rows)
        .matrix(matrix)
        .build();
  }

  private List<Student> resolveTargetStudents(
      AnalyticsQueryRequest request,
      String courseId,
      CalculationRun run,
      ResultLevel level,
      UserPrincipal principal) {
    RoleFlags roles = resolveRoleFlags(principal);

    if (request.getStudentIds() != null && !request.getStudentIds().isEmpty()) {
      List<Student> list = studentRepository.findAllById(request.getStudentIds());
      list = new ArrayList<>(list);
      list.sort(
          Comparator.comparing(Student::getStudentCode, Comparator.nullsLast(String::compareTo)));
      return filterStudentsBySearch(list, request.getSearch());
    }
    if (request.getStudentId() != null && !request.getStudentId().isBlank()) {
      List<Student> single =
          studentRepository.findById(request.getStudentId().trim()).map(List::of).orElse(List.of());
      return filterStudentsBySearch(single, request.getSearch());
    }

    // Thu thập danh sách sectionIds hợp lệ
    List<CourseSection> sections;
    if (request.getSectionIds() != null && !request.getSectionIds().isEmpty()) {
      sections = courseSectionRepository.findAllById(request.getSectionIds());
    } else if (courseId != null && !courseId.isBlank()) {
      sections = courseSectionRepository.findByCourseId(courseId.trim());
      if (request.getTermIds() != null && !request.getTermIds().isEmpty()) {
        sections =
            sections.stream().filter(s -> request.getTermIds().contains(s.getTermId())).toList();
      }
    } else {
      sections = List.of();
    }

    // Lọc lại các lớp mà người dùng có quyền đọc
    if (!roles.hasGlobalReadAccess()) {
      sections =
          sections.stream()
              .filter(
                  s ->
                      scopeAuthorizationService.canAccessLoadedSection(
                          principal, s, AccessType.READ))
              .toList();
    }

    if (!sections.isEmpty()) {
      List<String> secIds = sections.stream().map(CourseSection::getId).toList();
      List<Enrollment> enrollments =
          enrollmentRepository.findBySectionIdInAndStatus(secIds, EnrollmentStatus.ENROLLED);
      Set<String> studentIds =
          enrollments.stream()
              .map(Enrollment::getStudentId)
              .filter(Objects::nonNull)
              .collect(Collectors.toSet());
      return filterStudentsBySearch(
          filterAndSortStudents(studentIds, request.getCohorts()), request.getSearch());
    }

    // Nếu theo CTĐT hoặc không có lớp trực tiếp: tìm qua student_results của run
    if (run != null) {
      List<StudentResult> srs = studentResultRepository.findByRunIdAndLevel(run.getId(), level);
      Set<String> studentIds =
          srs.stream()
              .map(StudentResult::getStudentId)
              .filter(Objects::nonNull)
              .collect(Collectors.toSet());
      return filterStudentsBySearch(
          filterAndSortStudents(studentIds, request.getCohorts()), request.getSearch());
    }

    return List.of();
  }

  private List<Student> filterStudentsBySearch(List<Student> students, String search) {
    if (search == null || search.isBlank() || students.isEmpty()) {
      return students;
    }
    String keyword = search.trim().toLowerCase(Locale.ROOT);
    return students.stream()
        .filter(
            s ->
                (s.getStudentCode() != null
                        && s.getStudentCode().toLowerCase(Locale.ROOT).contains(keyword))
                    || (s.getFullName() != null
                        && s.getFullName().toLowerCase(Locale.ROOT).contains(keyword)))
        .toList();
  }

  private String resolveCourseId(AnalyticsQueryRequest request) {
    if (request.getCourseId() != null && !request.getCourseId().isBlank()) {
      return request.getCourseId().trim();
    }
    if (request.getSectionIds() != null && !request.getSectionIds().isEmpty()) {
      return courseSectionRepository
          .findById(request.getSectionIds().get(0))
          .map(CourseSection::getCourseId)
          .orElse(null);
    }
    return null;
  }

  private List<Student> filterAndSortStudents(Set<String> studentIds, List<String> cohorts) {
    if (studentIds == null || studentIds.isEmpty()) {
      return List.of();
    }
    List<Student> list = studentRepository.findAllById(studentIds);
    if (cohorts != null && !cohorts.isEmpty()) {
      list =
          list.stream()
              .filter(s -> s.getCohort() != null && cohorts.contains(s.getCohort()))
              .toList();
    }
    list = new ArrayList<>(list);
    list.sort(
        Comparator.comparing(Student::getStudentCode, Comparator.nullsLast(String::compareTo)));
    return list;
  }

  // =========================================================================
  // HELPER METHODS VÀ TÍNH TOÁN AN TOÀN (ZERO-DENOMINATOR SAFETY)
  // =========================================================================

  private int safeCount(Integer value) {
    return value != null ? value : 0;
  }

  private MatrixMetricCell toMetricCell(AggregateResult agg, boolean isOfficial, boolean isStale) {
    AggregateResult.Counts counts = agg.getCounts();
    int enrolled = counts != null ? safeCount(counts.getEnrolled()) : 0;
    int passed = counts != null ? safeCount(counts.getPassed()) : 0;
    int failed = counts != null ? safeCount(counts.getFailed()) : 0;
    return MatrixMetricCell.calculate(
        enrolled, passed, failed, agg.getMean(), agg.getPassRate(), isOfficial, isStale);
  }

  private boolean matchesTerm(
      AggregateResult agg, String termId, Map<String, String> sectionToTermMap) {
    if (agg.getScope() != null && Objects.equals(agg.getScope().getTermId(), termId)) {
      return true;
    }
    return agg.getSectionId() != null
        && Objects.equals(sectionToTermMap.get(agg.getSectionId()), termId);
  }

  private List<MatrixHeader> toTermHeaders(List<Term> terms) {
    return terms.stream()
        .map(
            t ->
                MatrixHeader.builder()
                    .id(t.getId())
                    .code(t.getCode())
                    .name(
                        t.getAcademicYear() != null
                            ? t.getAcademicYear() + " - " + t.getCode()
                            : t.getCode())
                    .build())
        .toList();
  }

  private List<MatrixHeader> toSectionHeaders(List<CourseSection> sections) {
    return sections.stream()
        .map(s -> MatrixHeader.builder().id(s.getId()).code(s.getCode()).name(s.getCode()).build())
        .toList();
  }

  private List<MatrixHeader> toCloHeaders(List<Clo> clos) {
    return clos.stream()
        .map(
            c ->
                MatrixHeader.builder()
                    .id(c.getId())
                    .code(c.getCode())
                    .name(c.getContentOriginal())
                    .bloomLevel(c.getBloomLevel())
                    .passThreshold(
                        c.getMeasurement() != null
                            ? c.getMeasurement().getDefaultPassThreshold()
                            : null)
                    .build())
        .toList();
  }

  private List<MatrixHeader> loadPloHeaders(String programVersionId) {
    return ploRepository.findByProgramVersionIdOrderByDisplayOrderAsc(programVersionId).stream()
        .map(
            plo ->
                MatrixHeader.builder()
                    .id(plo.getId())
                    .code(plo.getCode())
                    .passThreshold(plo.getPassThreshold())
                    .name(
                        plo.getContentSummary() != null
                            ? plo.getContentSummary()
                            : plo.getContentOriginal())
                    .build())
        .toList();
  }

  private List<MatrixHeader> loadPiHeaders(String programVersionId) {
    return piRepository.findByProgramVersionIdOrderByDisplayOrderAsc(programVersionId).stream()
        .map(
            pi ->
                MatrixHeader.builder()
                    .id(pi.getId())
                    .code(pi.getCode())
                    .passThreshold(pi.getPassThreshold())
                    .name(
                        pi.getContentShort() != null
                            ? pi.getContentShort()
                            : pi.getContentOriginal())
                    .build())
        .toList();
  }

  /**
   * Kết hợp nhiều bản ghi AggregateResult thành một ô chỉ số duy nhất, xử lý mẫu số bằng 0 nghiêm
   * ngặt (trả về null nếu evaluated == 0 hoặc enrolled == 0).
   */
  private MatrixMetricCell combineAggregates(
      List<AggregateResult> list, boolean isOfficial, boolean isStale) {
    if (list == null || list.isEmpty()) {
      return MatrixMetricCell.empty(isOfficial, isStale);
    }

    int totalEnrolled = 0;
    int totalPassed = 0;
    int totalFailed = 0;
    double weightedMeanSum = 0.0;
    int totalMeanEvaluated = 0;

    for (AggregateResult agg : list) {
      if (agg.getCounts() != null) {
        int e = safeCount(agg.getCounts().getEnrolled());
        int p = safeCount(agg.getCounts().getPassed());
        int f = safeCount(agg.getCounts().getFailed());
        totalEnrolled += e;
        totalPassed += p;
        totalFailed += f;

        int evaluated = p + f;
        if (evaluated > 0 && agg.getMean() != null) {
          weightedMeanSum += agg.getMean() * evaluated;
          totalMeanEvaluated += evaluated;
        }
      }
    }

    Double overallMean = totalMeanEvaluated > 0 ? (weightedMeanSum / totalMeanEvaluated) : null;

    return MatrixMetricCell.calculate(
        totalEnrolled, totalPassed, totalFailed, overallMean, null, isOfficial, isStale);
  }

  private List<List<MatrixMetricCell>> fillEmptyMatrix(
      int rowCount, int colCount, boolean isOfficial, boolean isStale) {
    List<List<MatrixMetricCell>> matrix = new ArrayList<>();
    for (int r = 0; r < rowCount; r++) {
      List<MatrixMetricCell> row = new ArrayList<>();
      for (int c = 0; c < colCount; c++) {
        row.add(MatrixMetricCell.empty(isOfficial, isStale));
      }
      matrix.add(row);
    }
    return matrix;
  }

  public List<Clo> findClosForCourse(String courseId) {
    if (courseId == null || courseId.isBlank()) {
      return Collections.emptyList();
    }
    return mongoTemplate.find(
        Query.query(Criteria.where("courseId").is(courseId.trim())), Clo.class);
  }

  private Map<String, String> buildSectionToTermMap() {
    List<CourseSection> sections = courseSectionRepository.findAll();
    Map<String, String> map = new LinkedHashMap<>();
    for (CourseSection s : sections) {
      if (s.getId() != null && s.getTermId() != null) {
        map.put(s.getId(), s.getTermId());
      }
    }
    return map;
  }

  private Map<String, String> buildCloToCourseMap() {
    List<Clo> clos = cloRepository.findAll();
    Map<String, String> map = new LinkedHashMap<>();
    for (Clo c : clos) {
      if (c.getId() != null && c.getCourseId() != null) {
        map.put(c.getId(), c.getCourseId());
      }
    }
    return map;
  }

  private Map<String, String> buildStudentToCohortMap(String programVersionId) {
    Query q = new Query();
    if (programVersionId != null && !programVersionId.isBlank()) {
      q.addCriteria(Criteria.where("programVersionId").is(programVersionId));
    }
    List<Student> students = mongoTemplate.find(q, Student.class);
    Map<String, String> map = new LinkedHashMap<>();
    for (Student s : students) {
      if (s.getId() != null && s.getCohort() != null) {
        map.put(s.getId(), s.getCohort());
      }
    }
    return map;
  }

  private Map<String, AssessmentType> buildAssessmentTypeMap() {
    List<Assessment> assessments = assessmentRepository.findAll();
    Map<String, AssessmentType> map = new LinkedHashMap<>();
    for (Assessment a : assessments) {
      if (a.getId() != null && a.getType() != null) {
        map.put(a.getId(), a.getType());
      }
    }
    return map;
  }
}
