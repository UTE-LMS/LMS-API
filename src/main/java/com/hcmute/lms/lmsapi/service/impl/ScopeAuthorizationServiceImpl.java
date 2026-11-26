package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.document.Course;
import com.hcmute.lms.lmsapi.document.CourseSection;
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
import com.hcmute.lms.lmsapi.repository.ProgramRepository;
import com.hcmute.lms.lmsapi.repository.ProgramVersionRepository;
import com.hcmute.lms.lmsapi.repository.SyllabusRepository;
import com.hcmute.lms.lmsapi.service.ScopeAuthorizationService;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
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
  private final ProgramRepository programRepository;
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

    if (principal.getRoles() == null || principal.getRoles().isEmpty()) {
      log.debug("canAccessSection: Fail-closed - user [{}] has no roles", principal.getUsername());
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
    if (principal.getRoles() == null || principal.getRoles().isEmpty()) {
      return false;
    }
    String sectionId = section.getId();

    // Duyệt qua từng vai trò của người dùng; nếu có ít nhất 1 vai trò hợp lệ -> cho phép
    for (Role role : principal.getRoles()) {
      if (checkRoleForSection(role, principal, section, accessType)) {
        log.debug(
            "canAccessSection: Access GRANTED to user [{}] for section [{}] with accessType [{}] via role [{}]",
            principal.getUsername(),
            sectionId,
            accessType,
            role);
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
    return canManageCourse(principal, courseId);
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

    if (principal.getRoles() == null || principal.getRoles().isEmpty()) {
      log.debug("canAccessProgram: Fail-closed - user [{}] has no roles", principal.getUsername());
      return false;
    }

    for (Role role : principal.getRoles()) {
      if (checkProgramRole(role, principal, programId, accessType)) {
        log.debug(
            "canAccessProgram: Access GRANTED to user [{}] for program [{}] with accessType [{}] via role [{}]",
            principal.getUsername(),
            programId,
            accessType,
            role);
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
    if (principal.getRoles() == null || principal.getRoles().isEmpty()) {
      log.debug("canAccessCourse: Fail-closed - user [{}] has no roles", principal.getUsername());
      return false;
    }

    // ADMIN luôn có toàn quyền
    if (principal.getRoles().contains(Role.ADMIN)) {
      return true;
    }

    // Môn học phải tồn tại trong CSDL
    Course course = courseRepository.findById(courseId.trim()).orElse(null);
    if (course == null) {
      log.debug("canAccessCourse: Fail-closed - course [{}] not found in database", courseId);
      return false;
    }

    for (Role role : principal.getRoles()) {
      if (checkCourseRole(role, principal, course, accessType)) {
        log.debug(
            "canAccessCourse: Access GRANTED to user [{}] for course [{}] with accessType [{}] via role [{}]",
            principal.getUsername(),
            courseId,
            accessType,
            role);
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
    if (principal.getRoles() == null || principal.getRoles().isEmpty()) {
      log.debug("canAssignCourse: Fail-closed - user [{}] has no roles", principal.getUsername());
      return false;
    }

    Course course = courseRepository.findById(courseId.trim()).orElse(null);
    if (course == null) {
      log.debug("canAssignCourse: Fail-closed - course [{}] not found in database", courseId);
      return false;
    }

    // ADMIN hoặc PROGRAM_MANAGER cùng Khoa
    return principal.getRoles().stream()
        .anyMatch(
            role -> {
              if (role == null) {
                return false;
              }
              if (role == Role.ADMIN) {
                return true;
              }
              if (role == Role.PROGRAM_MANAGER) {
                return principal.getFacultyId() != null
                    && Objects.equals(principal.getFacultyId(), course.getFacultyId());
              }
              return false;
            });
  }

  @Override
  public boolean canManageProgram(UserPrincipal principal, String programId) {
    return canManageFacultyResource(
        principal,
        programId,
        id -> programRepository.findById(id).map(p -> p.getFacultyId()),
        false);
  }

  @Override
  public boolean canManageCourse(UserPrincipal principal, String courseId) {
    return canManageFacultyResource(
        principal, courseId, id -> courseRepository.findById(id).map(Course::getFacultyId), true);
  }

  private boolean canManageFacultyResource(
      UserPrincipal principal,
      String resourceId,
      Function<String, Optional<String>> facultyLookup,
      boolean allowAdmin) {
    if (principal == null
        || resourceId == null
        || resourceId.isBlank()
        || principal.getRoles() == null) {
      return false;
    }
    if (allowAdmin && principal.getRoles().contains(Role.ADMIN)) {
      return true;
    }
    boolean isPm = principal.getRoles().contains(Role.PROGRAM_MANAGER);
    if (!isPm || principal.getFacultyId() == null) {
      return false;
    }
    return facultyLookup
        .apply(resourceId.trim())
        .map(facultyId -> Objects.equals(facultyId, principal.getFacultyId()))
        .orElse(false);
  }

  @Override
  public boolean canManageCourseSyllabus(UserPrincipal principal, String courseId) {
    return canManageCourse(principal, courseId);
  }

  @Override
  public boolean canManageSyllabus(UserPrincipal principal, String syllabusId) {
    if (syllabusId == null || syllabusId.isBlank()) {
      return false;
    }
    return syllabusRepository
        .findById(syllabusId.trim())
        .map(s -> canManageCourse(principal, s.getCourseId()))
        .orElse(false);
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

  private boolean checkCourseRole(
      Role role, UserPrincipal principal, Course course, AccessType accessType) {
    if (role == null) {
      return false;
    }

    String courseId = course.getId();
    return switch (role) {
      case ADMIN -> true;
      case LECTURER ->
          courseSectionRepository.findByCourseId(courseId).stream()
              .anyMatch(s -> isAssignedLecturer(s, principal.getId()));
      case PROGRAM_MANAGER ->
          principal.getFacultyId() != null
              && Objects.equals(principal.getFacultyId(), course.getFacultyId());
      case QA -> accessType == AccessType.READ;
    };
  }

  private boolean checkProgramRole(
      Role role, UserPrincipal principal, String programId, AccessType accessType) {
    if (role == null) {
      return false;
    }

    return switch (role) {
      case ADMIN -> accessType == AccessType.READ;
      case PROGRAM_MANAGER -> {
        if (principal.getFacultyId() == null) {
          yield false;
        }
        yield programRepository
            .findById(programId.trim())
            .map(p -> Objects.equals(p.getFacultyId(), principal.getFacultyId()))
            .orElse(false);
      }
      case QA -> accessType == AccessType.READ;
      case LECTURER -> false;
    };
  }

  private boolean checkRoleForSection(
      Role role, UserPrincipal principal, CourseSection section, AccessType accessType) {
    if (role == null) {
      return false;
    }

    String courseId = section.getCourseId();
    String userId = principal.getId();

    if (accessType == AccessType.WRITE) {
      return switch (role) {
        case ADMIN -> true;
        case LECTURER -> isAssignedLecturer(section, userId);
        case PROGRAM_MANAGER, QA -> false;
      };
    } else if (accessType == AccessType.READ) {
      return switch (role) {
        case ADMIN, QA -> true;
        case LECTURER -> isAssignedLecturer(section, userId);
        case PROGRAM_MANAGER ->
            principal.getFacultyId() != null
                && courseId != null
                && courseRepository
                    .findById(courseId)
                    .map(c -> Objects.equals(c.getFacultyId(), principal.getFacultyId()))
                    .orElse(false);
      };
    }

    return false;
  }

  private boolean isAssignedLecturer(CourseSection section, String userId) {
    if (section.getLecturerIds() == null || userId == null) {
      return false;
    }
    return section.getLecturerIds().contains(userId);
  }
}
