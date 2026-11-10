package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.document.CourseSection;
import com.hcmute.lms.lmsapi.document.RoleAssignment;
import com.hcmute.lms.lmsapi.document.UserScope;
import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import com.hcmute.lms.lmsapi.enums.AccessType;
import com.hcmute.lms.lmsapi.enums.AssessmentScope;
import com.hcmute.lms.lmsapi.enums.Role;
import com.hcmute.lms.lmsapi.repository.AssessmentRepository;
import com.hcmute.lms.lmsapi.repository.CloPiLinkRepository;
import com.hcmute.lms.lmsapi.repository.CloRepository;
import com.hcmute.lms.lmsapi.repository.CourseRepository;
import com.hcmute.lms.lmsapi.repository.CourseSectionRepository;
import com.hcmute.lms.lmsapi.repository.EnrollmentRepository;
import com.hcmute.lms.lmsapi.repository.ImportBatchRepository;
import com.hcmute.lms.lmsapi.repository.ProgramCourseRepository;
import com.hcmute.lms.lmsapi.repository.ProgramVersionRepository;
import com.hcmute.lms.lmsapi.repository.SyllabusRepository;
import com.hcmute.lms.lmsapi.service.ScopeAuthorizationService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Hiện thực dịch vụ phân quyền phạm vi truy cập tài nguyên lớp học phần, chương trình đào tạo, học
 * phần, đề cương và CLO (F01 / ADR 0003, ADR 0004, ADR 0005, ADR 0010). Triển khai ma trận phân
 * quyền vai trò × phạm vi × READ/WRITE, quy tắc lecturerIds độc lập, định nghĩa strict empty scope
 * và tính toán quyền tổng hợp theo phép HỢP (UNION).
 */
@Slf4j
@Service("scopeAuthorizationService")
@RequiredArgsConstructor
public class ScopeAuthorizationServiceImpl implements ScopeAuthorizationService {

  private final CourseSectionRepository courseSectionRepository;
  private final CourseRepository courseRepository;
  private final ProgramVersionRepository programVersionRepository;
  private final ProgramCourseRepository programCourseRepository;
  private final SyllabusRepository syllabusRepository;
  private final CloRepository cloRepository;
  private final CloPiLinkRepository cloPiLinkRepository;
  private final EnrollmentRepository enrollmentRepository;
  private final AssessmentRepository assessmentRepository;
  private final ImportBatchRepository importBatchRepository;

  @Override
  public boolean canAccessSection(
      UserPrincipal principal, String sectionId, AccessType accessType) {
    // Nguyên tắc Fail-Closed: từ chối truy cập nếu thiếu tham số đầu vào cần thiết
    if (principal == null || sectionId == null || sectionId.isBlank() || accessType == null) {
      log.debug(
          "canAccessSection: Fail-closed due to null or blank parameters (principal={}, sectionId={}, accessType={})",
          principal != null ? principal.getUsername() : null,
          sectionId,
          accessType);
      return false;
    }

    if (principal.getRoleAssignments() == null || principal.getRoleAssignments().isEmpty()) {
      log.debug(
          "canAccessSection: Fail-closed - user [{}] has no role assignments",
          principal.getUsername());
      return false;
    }

    // Truy vấn lớp học phần từ repository; nếu không tồn tại -> fail-closed trả về false
    Optional<CourseSection> sectionOpt = courseSectionRepository.findById(sectionId.trim());
    if (sectionOpt.isEmpty()) {
      log.debug("canAccessSection: Fail-closed - section [{}] not found in database", sectionId);
      return false;
    }

    return canAccessLoadedSection(principal, sectionOpt.get(), accessType);
  }

  @Override
  public boolean canAccessLoadedSection(
      UserPrincipal principal, CourseSection section, AccessType accessType) {
    if (principal == null || section == null || accessType == null) {
      return false;
    }
    if (principal.getRoleAssignments() == null || principal.getRoleAssignments().isEmpty()) {
      return false;
    }
    String sectionId = section.getId();

    // Duyệt qua từng phân công vai trò của người dùng; nếu có ít nhất 1 vai trò hợp lệ -> cho phép
    for (RoleAssignment assignment : principal.getRoleAssignments()) {
      if (checkAssignment(assignment, principal, section, accessType)) {
        log.debug(
            "canAccessSection: Access GRANTED to user [{}] for section [{}] with accessType [{}] via role [{}]",
            principal.getUsername(),
            sectionId,
            accessType,
            assignment.getRole());
        return true;
      }
    }

    log.debug(
        "canAccessSection: Access DENIED to user [{}] for section [{}] with accessType [{}]",
        principal.getUsername(),
        sectionId,
        accessType);
    return false;
  }

  @Override
  public boolean canManageCourseSections(UserPrincipal principal, String courseId) {
    if (principal == null
        || courseId == null
        || courseId.isBlank()
        || principal.getRoleAssignments() == null) {
      return false;
    }

    for (RoleAssignment assignment : principal.getRoleAssignments()) {
      if (assignment == null || assignment.getRole() == null) {
        continue;
      }
      if (assignment.getRole() == Role.ADMIN) {
        return true;
      }
      if (assignment.getRole() == Role.PROGRAM_MANAGER
          && courseInManagedProgram(assignment.getScope(), courseId.trim())) {
        return true;
      }
    }
    log.debug(
        "canManageCourseSections: Access DENIED to user [{}] for course [{}]",
        principal.getUsername(),
        courseId);
    return false;
  }

  @Override
  public boolean canManageSection(UserPrincipal principal, String sectionId) {
    if (sectionId == null || sectionId.isBlank()) {
      return false;
    }
    return courseSectionRepository
        .findById(sectionId.trim())
        .map(section -> canManageCourseSections(principal, section.getCourseId()))
        .orElseGet(
            () -> {
              log.debug("canManageSection: Fail-closed - section [{}] not found", sectionId);
              return false;
            });
  }

  @Override
  public boolean canAccessAssessment(
      UserPrincipal principal, String assessmentId, AccessType accessType) {
    if (assessmentId == null || assessmentId.isBlank()) {
      return false;
    }
    return assessmentRepository
        .findById(assessmentId.trim())
        .map(
            assessment ->
                assessment.getScope() == AssessmentScope.SECTION
                    ? canAccessSection(principal, assessment.getSectionId(), accessType)
                    : canAccessSyllabus(principal, assessment.getSyllabusId(), accessType))
        .orElseGet(
            () -> {
              log.debug(
                  "canAccessAssessment: Fail-closed - assessment [{}] not found", assessmentId);
              return false;
            });
  }

  @Override
  public boolean canAccessImportBatch(
      UserPrincipal principal, String batchId, AccessType accessType) {
    if (batchId == null || batchId.isBlank()) {
      return false;
    }
    return importBatchRepository
        .findById(batchId.trim())
        .map(batch -> canAccessSection(principal, batch.getSectionId(), accessType))
        .orElseGet(
            () -> {
              log.debug("canAccessImportBatch: Fail-closed - import batch [{}] not found", batchId);
              return false;
            });
  }

  @Override
  public boolean canReviewAssessment(UserPrincipal principal, String assessmentId) {
    if (principal == null
        || principal.getRoleAssignments() == null
        || assessmentId == null
        || assessmentId.isBlank()) {
      return false;
    }
    return assessmentRepository
        .findById(assessmentId.trim())
        .filter(assessment -> assessment.getScope() == AssessmentScope.SECTION)
        .flatMap(assessment -> syllabusRepository.findById(assessment.getSyllabusId()))
        .map(
            syllabus ->
                principal.getRoleAssignments().stream()
                    .anyMatch(
                        a ->
                            a != null
                                && a.getRole() == Role.DEPARTMENT_HEAD
                                && matchesCourseScope(a.getScope(), syllabus.getCourseId())))
        .orElse(false);
  }

  @Override
  public boolean canAccessEnrollment(
      UserPrincipal principal, String enrollmentId, AccessType accessType) {
    if (enrollmentId == null || enrollmentId.isBlank()) {
      return false;
    }
    return enrollmentRepository
        .findById(enrollmentId.trim())
        .map(enrollment -> canAccessSection(principal, enrollment.getSectionId(), accessType))
        .orElseGet(
            () -> {
              log.debug(
                  "canAccessEnrollment: Fail-closed - enrollment [{}] not found", enrollmentId);
              return false;
            });
  }

  /** Học phần có nằm trong một phiên bản CTĐT thuộc chương trình trong scope.programIds không. */
  private boolean courseInManagedProgram(UserScope scope, String courseId) {
    if (scope == null || scope.getProgramIds() == null || scope.getProgramIds().isEmpty()) {
      return false;
    }
    return programCourseRepository.findByCourseId(courseId).stream()
        .map(pc -> programVersionRepository.findById(pc.getProgramVersionId()))
        .flatMap(Optional::stream)
        .anyMatch(version -> scope.getProgramIds().contains(version.getProgramId()));
  }

  @Override
  public boolean canAccessProgram(
      UserPrincipal principal, String programId, AccessType accessType) {
    if (principal == null || programId == null || programId.isBlank() || accessType == null) {
      log.debug(
          "canAccessProgram: Fail-closed due to null or blank parameters (principal={}, programId={}, accessType={})",
          principal != null ? principal.getUsername() : null,
          programId,
          accessType);
      return false;
    }

    if (principal.getRoleAssignments() == null || principal.getRoleAssignments().isEmpty()) {
      log.debug(
          "canAccessProgram: Fail-closed - user [{}] has no role assignments",
          principal.getUsername());
      return false;
    }

    for (RoleAssignment assignment : principal.getRoleAssignments()) {
      if (checkProgramAssignment(assignment, programId, accessType)) {
        log.debug(
            "canAccessProgram: Access GRANTED to user [{}] for program [{}] with accessType [{}] via role [{}]",
            principal.getUsername(),
            programId,
            accessType,
            assignment.getRole());
        return true;
      }
    }

    log.debug(
        "canAccessProgram: Access DENIED to user [{}] for program [{}] with accessType [{}]",
        principal.getUsername(),
        programId,
        accessType);
    return false;
  }

  @Override
  public boolean canAccessProgramVersion(
      UserPrincipal principal, String programVersionId, AccessType accessType) {
    if (principal == null
        || programVersionId == null
        || programVersionId.isBlank()
        || accessType == null) {
      log.debug(
          "canAccessProgramVersion: Fail-closed due to null or blank parameters (principal={}, programVersionId={}, accessType={})",
          principal != null ? principal.getUsername() : null,
          programVersionId,
          accessType);
      return false;
    }

    return programVersionRepository
        .findById(programVersionId.trim())
        .map(version -> canAccessProgram(principal, version.getProgramId(), accessType))
        .orElseGet(
            () -> {
              log.debug(
                  "canAccessProgramVersion: Fail-closed - programVersion [{}] not found in database",
                  programVersionId);
              return false;
            });
  }

  @Override
  public boolean canAccessCourse(UserPrincipal principal, String courseId, AccessType accessType) {
    if (principal == null || courseId == null || courseId.isBlank() || accessType == null) {
      log.debug("canAccessCourse: Fail-closed due to null or blank parameters");
      return false;
    }
    if (principal.getRoleAssignments() == null || principal.getRoleAssignments().isEmpty()) {
      log.debug(
          "canAccessCourse: Fail-closed - user [{}] has no role assignments",
          principal.getUsername());
      return false;
    }

    // ADMIN luôn có toàn quyền
    boolean isAdmin =
        principal.getRoleAssignments().stream()
            .anyMatch(a -> a != null && a.getRole() == Role.ADMIN);
    if (isAdmin) {
      return true;
    }

    // Môn học phải tồn tại trong CSDL
    if (!courseRepository.existsById(courseId.trim())) {
      log.debug("canAccessCourse: Fail-closed - course [{}] not found in database", courseId);
      return false;
    }

    for (RoleAssignment assignment : principal.getRoleAssignments()) {
      if (checkCourseAssignment(assignment, courseId.trim(), accessType)) {
        log.debug(
            "canAccessCourse: Access GRANTED to user [{}] for course [{}] with accessType [{}] via role [{}]",
            principal.getUsername(),
            courseId,
            accessType,
            assignment.getRole());
        return true;
      }
    }

    log.debug(
        "canAccessCourse: Access DENIED to user [{}] for course [{}] with accessType [{}]",
        principal.getUsername(),
        courseId,
        accessType);
    return false;
  }

  @Override
  public boolean canAssignCourse(UserPrincipal principal, String courseId) {
    if (principal == null || courseId == null || courseId.isBlank()) {
      log.debug("canAssignCourse: Fail-closed due to null or blank parameters");
      return false;
    }
    if (principal.getRoleAssignments() == null || principal.getRoleAssignments().isEmpty()) {
      log.debug(
          "canAssignCourse: Fail-closed - user [{}] has no role assignments",
          principal.getUsername());
      return false;
    }

    if (!courseRepository.existsById(courseId.trim())) {
      log.debug("canAssignCourse: Fail-closed - course [{}] not found in database", courseId);
      return false;
    }

    // Chỉ duy nhất Trưởng bộ môn (DEPARTMENT_HEAD) có courseId trong scope.courseIds mới được phân
    // công (ADMIN không có thẩm quyền Bộ môn)
    return principal.getRoleAssignments().stream()
        .anyMatch(
            a ->
                a != null
                    && a.getRole() == Role.DEPARTMENT_HEAD
                    && matchesCourseScope(a.getScope(), courseId.trim()));
  }

  @Override
  public boolean canAccessSyllabus(
      UserPrincipal principal, String syllabusId, AccessType accessType) {
    if (syllabusId == null || syllabusId.isBlank()) {
      return false;
    }
    return syllabusRepository
        .findById(syllabusId.trim())
        .map(syllabus -> canAccessCourse(principal, syllabus.getCourseId(), accessType))
        .orElseGet(
            () -> {
              log.debug("canAccessSyllabus: Fail-closed - syllabus [{}] not found", syllabusId);
              return false;
            });
  }

  @Override
  public boolean canAccessClo(UserPrincipal principal, String cloId, AccessType accessType) {
    if (cloId == null || cloId.isBlank()) {
      return false;
    }
    return cloRepository
        .findById(cloId.trim())
        .map(clo -> canAccessCourse(principal, clo.getCourseId(), accessType))
        .orElseGet(
            () -> {
              log.debug("canAccessClo: Fail-closed - CLO [{}] not found", cloId);
              return false;
            });
  }

  @Override
  public boolean canAccessCloPiLink(UserPrincipal principal, String linkId, AccessType accessType) {
    if (linkId == null || linkId.isBlank()) {
      return false;
    }
    return cloPiLinkRepository
        .findById(linkId.trim())
        .map(link -> canAccessProgramVersion(principal, link.getProgramVersionId(), accessType))
        .orElseGet(
            () -> {
              log.debug("canAccessCloPiLink: Fail-closed - link [{}] not found", linkId);
              return false;
            });
  }

  private boolean checkCourseAssignment(
      RoleAssignment assignment, String courseId, AccessType accessType) {
    if (assignment == null || assignment.getRole() == null) {
      return false;
    }

    UserScope scope = assignment.getScope();
    return switch (assignment.getRole()) {
      case ADMIN -> true;
      case DEPARTMENT_HEAD, LECTURER -> matchesCourseScope(scope, courseId);
      case PROGRAM_MANAGER, QA_OFFICER ->
          accessType == AccessType.READ
              && (matchesCourseScope(scope, courseId) || isStrictEmptyScope(scope));
      case VIEWER ->
          accessType == AccessType.READ
              && (isStrictEmptyScope(scope) || matchesCourseScope(scope, courseId));
      case STUDENT -> false;
    };
  }

  private boolean matchesCourseScope(UserScope scope, String courseId) {
    return scope != null
        && scope.getCourseIds() != null
        && scope.getCourseIds().contains(courseId.trim());
  }

  private boolean checkProgramAssignment(
      RoleAssignment assignment, String programId, AccessType accessType) {
    if (assignment == null || assignment.getRole() == null) {
      return false;
    }

    UserScope scope = assignment.getScope();
    return switch (assignment.getRole()) {
      case ADMIN -> true;
      case PROGRAM_MANAGER, QA_OFFICER -> matchesProgramScope(scope, programId);
      case VIEWER ->
          accessType == AccessType.READ
              && (isStrictEmptyScope(scope) || matchesProgramScope(scope, programId));
      case DEPARTMENT_HEAD, LECTURER, STUDENT -> false;
    };
  }

  private boolean matchesProgramScope(UserScope scope, String programId) {
    return scope != null
        && scope.getProgramIds() != null
        && scope.getProgramIds().contains(programId.trim());
  }

  /**
   * Kiểm tra quyền truy cập của một phân công vai trò cụ thể trên lớp học phần theo ma trận ADR
   * 0003.
   */
  private boolean checkAssignment(
      RoleAssignment assignment,
      UserPrincipal principal,
      CourseSection section,
      AccessType accessType) {
    if (assignment == null || assignment.getRole() == null) {
      return false;
    }

    Role role = assignment.getRole();
    UserScope scope = assignment.getScope();
    String sectionId = section.getId();
    String courseId = section.getCourseId();
    String userId = principal.getId();

    if (accessType == AccessType.WRITE) {
      return switch (role) {
        // ADMIN: Toàn quyền ghi trên toàn bộ lớp học phần
        case ADMIN -> true;

        // LECTURER: Cho phép ghi khi user còn vai trò LECTURER VÀ:
        // (1) sectionId trong scope.sectionIds, HOẶC
        // (2) courseId trong scope.courseIds, HOẶC
        // (3) userId có trong CourseSection.lecturerIds (quy tắc độc lập từ phân công giảng dạy)
        case LECTURER ->
            matchesScope(scope, sectionId, courseId) || isAssignedLecturer(section, userId);

        // Tất cả các vai trò khác không có quyền WRITE trên tài nguyên lớp học phần
        case PROGRAM_MANAGER, DEPARTMENT_HEAD, QA_OFFICER, VIEWER, STUDENT -> false;
      };
    } else if (accessType == AccessType.READ) {
      return switch (role) {
        // ADMIN: Toàn quyền đọc toàn bộ lớp học phần
        case ADMIN -> true;

        // LECTURER: Cho phép đọc khi thỏa mãn phạm vi scope hoặc nằm trong danh sách phân công
        // giảng viên
        case LECTURER ->
            matchesScope(scope, sectionId, courseId) || isAssignedLecturer(section, userId);

        // PROGRAM_MANAGER, DEPARTMENT_HEAD, QA_OFFICER:
        // Cho phép đọc khi lớp nằm trong scope.sectionIds hoặc courseId nằm trong scope.courseIds.
        // Nếu chỉ có programIds thì fail-closed (hạn chế kỹ thuật F01 chờ F02 ánh xạ CTĐT -> Lớp).
        case PROGRAM_MANAGER, DEPARTMENT_HEAD, QA_OFFICER ->
            matchesScope(scope, sectionId, courseId);

        // VIEWER:
        // - Strict empty scope (scope == null hoặc cả 3 mảng rỗng) -> Đọc toàn hệ thống.
        // - Non-empty scope -> Chỉ đọc trong phạm vi sectionIds hoặc courseIds.
        // - Nếu chỉ có programIds -> Scope không rỗng và không khớp section/course -> Fail-closed
        // (từ chối 403).
        case VIEWER -> {
          if (isStrictEmptyScope(scope)) {
            yield true;
          }
          yield matchesScope(scope, sectionId, courseId);
        }

        // STUDENT: Từ chối truy cập trên tài nguyên lớp (chờ story NEW-03 xem kết quả cá nhân)
        case STUDENT -> false;
      };
    }

    return false;
  }

  /** So khớp lớp học phần với phạm vi phân công (theo sectionIds hoặc courseIds). */
  private boolean matchesScope(UserScope scope, String sectionId, String courseId) {
    if (scope == null) {
      return false;
    }

    if (scope.getSectionIds() != null
        && sectionId != null
        && scope.getSectionIds().contains(sectionId)) {
      return true;
    }

    if (scope.getCourseIds() != null
        && courseId != null
        && scope.getCourseIds().contains(courseId)) {
      return true;
    }

    return false;
  }

  /**
   * Kiểm tra quy tắc độc lập: Giảng viên có tên trong danh sách phân công của lớp học phần hay
   * không.
   */
  private boolean isAssignedLecturer(CourseSection section, String userId) {
    if (section.getLecturerIds() == null || userId == null) {
      return false;
    }
    return section.getLecturerIds().contains(userId);
  }

  /**
   * Định nghĩa Scope rỗng nghiêm ngặt (Strict Empty Scope per ADR 0003): Scope chỉ rỗng khi scope
   * == null HOẶC cả 3 mảng programIds, courseIds, sectionIds đều rỗng/null.
   */
  private boolean isStrictEmptyScope(UserScope scope) {
    if (scope == null) {
      return true;
    }
    return scope.isEmpty();
  }
}
