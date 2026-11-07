package com.hcmute.lms.lmsapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hcmute.lms.lmsapi.document.Clo;
import com.hcmute.lms.lmsapi.document.CloPiLink;
import com.hcmute.lms.lmsapi.document.ProgramVersion;
import com.hcmute.lms.lmsapi.document.RoleAssignment;
import com.hcmute.lms.lmsapi.document.Syllabus;
import com.hcmute.lms.lmsapi.document.UserScope;
import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import com.hcmute.lms.lmsapi.enums.AccessType;
import com.hcmute.lms.lmsapi.enums.Role;
import com.hcmute.lms.lmsapi.repository.CloPiLinkRepository;
import com.hcmute.lms.lmsapi.repository.CloRepository;
import com.hcmute.lms.lmsapi.repository.CourseRepository;
import com.hcmute.lms.lmsapi.repository.CourseSectionRepository;
import com.hcmute.lms.lmsapi.repository.ProgramCourseRepository;
import com.hcmute.lms.lmsapi.repository.ProgramVersionRepository;
import com.hcmute.lms.lmsapi.repository.SyllabusRepository;
import com.hcmute.lms.lmsapi.service.impl.ScopeAuthorizationServiceImpl;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("canAccessCourse/Syllabus/Clo/CloPiLink Unit Tests (F03 / ADR 0010)")
class CourseScopeAuthorizationTest {

  private static final String COURSE_ID = "671dcba5f123456789abc101";
  private static final String OTHER_COURSE_ID = "671dcba5f123456789abc102";
  private static final String VERSION_A = "671dcba5f123456789abc102";
  private static final String PROGRAM_A = "671dcba5f123456789abc001";
  private static final String PROGRAM_OTHER = "671dcba5f123456789abc099";
  private static final String SYLLABUS_ID = "671dcba5f123456789abc201";
  private static final String CLO_ID = "671dcba5f123456789abc301";
  private static final String LINK_ID = "671dcba5f123456789abc401";

  @Mock private CourseSectionRepository courseSectionRepository;
  @Mock private CourseRepository courseRepository;
  @Mock private ProgramVersionRepository programVersionRepository;
  @Mock private ProgramCourseRepository programCourseRepository;
  @Mock private SyllabusRepository syllabusRepository;
  @Mock private CloRepository cloRepository;
  @Mock private CloPiLinkRepository cloPiLinkRepository;

  @InjectMocks private ScopeAuthorizationServiceImpl service;

  private UserPrincipal principalWithCourses(Role role, List<String> courseIds) {
    UserScope scope = courseIds == null ? null : UserScope.builder().courseIds(courseIds).build();
    return UserPrincipal.builder()
        .id("671dcba5f123456789abc00a")
        .username("user")
        .roleAssignments(
            new ArrayList<>(List.of(RoleAssignment.builder().role(role).scope(scope).build())))
        .build();
  }

  private UserPrincipal principalWithPrograms(Role role, List<String> programIds) {
    UserScope scope =
        programIds == null ? null : UserScope.builder().programIds(programIds).build();
    return UserPrincipal.builder()
        .id("671dcba5f123456789abc00a")
        .username("user")
        .roleAssignments(
            new ArrayList<>(List.of(RoleAssignment.builder().role(role).scope(scope).build())))
        .build();
  }

  @Test
  @DisplayName("ADMIN luôn có quyền truy cập học phần (không cần kiểm tra scope)")
  void admin_alwaysAllowed() {
    assertThat(
            service.canAccessCourse(
                principalWithCourses(Role.ADMIN, null), COURSE_ID, AccessType.WRITE))
        .isTrue();
    verifyNoInteractions(courseRepository);
  }

  @Test
  @DisplayName("DEPARTMENT_HEAD có môn học trong scope.courseIds -> được phép ghi (ADR 0010)")
  void departmentHeadInScope_allowed() {
    when(courseRepository.existsById(COURSE_ID)).thenReturn(true);

    assertThat(
            service.canAccessCourse(
                principalWithCourses(Role.DEPARTMENT_HEAD, List.of(COURSE_ID)),
                COURSE_ID,
                AccessType.WRITE))
        .isTrue();
  }

  @Test
  @DisplayName("DEPARTMENT_HEAD không có môn học trong scope.courseIds -> bị từ chối")
  void departmentHeadOutOfScope_denied() {
    when(courseRepository.existsById(COURSE_ID)).thenReturn(true);

    assertThat(
            service.canAccessCourse(
                principalWithCourses(Role.DEPARTMENT_HEAD, List.of(OTHER_COURSE_ID)),
                COURSE_ID,
                AccessType.WRITE))
        .isFalse();
  }

  @Test
  @DisplayName("LECTURER có môn học trong scope.courseIds -> được phép ghi (ADR 0010)")
  void lecturerInScope_allowed() {
    when(courseRepository.existsById(COURSE_ID)).thenReturn(true);

    assertThat(
            service.canAccessCourse(
                principalWithCourses(Role.LECTURER, List.of(COURSE_ID)),
                COURSE_ID,
                AccessType.WRITE))
        .isTrue();
  }

  @Test
  @DisplayName("LECTURER không có môn học trong scope.courseIds -> bị từ chối")
  void lecturerOutOfScope_denied() {
    when(courseRepository.existsById(COURSE_ID)).thenReturn(true);

    assertThat(
            service.canAccessCourse(
                principalWithCourses(Role.LECTURER, List.of(OTHER_COURSE_ID)),
                COURSE_ID,
                AccessType.WRITE))
        .isFalse();
  }

  @Test
  @DisplayName("PROGRAM_MANAGER không có quyền ghi trên môn học/đề cương (ADR 0010)")
  void programManager_cannotWriteCourse() {
    when(courseRepository.existsById(COURSE_ID)).thenReturn(true);

    assertThat(
            service.canAccessCourse(
                principalWithPrograms(Role.PROGRAM_MANAGER, List.of(PROGRAM_A)),
                COURSE_ID,
                AccessType.WRITE))
        .isFalse();
  }

  @Test
  @DisplayName("Môn học không tồn tại trong CSDL -> bị từ chối (fail-closed)")
  void courseNotFound_denied() {
    when(courseRepository.existsById(COURSE_ID)).thenReturn(false);

    assertThat(
            service.canAccessCourse(
                principalWithCourses(Role.DEPARTMENT_HEAD, List.of(COURSE_ID)),
                COURSE_ID,
                AccessType.WRITE))
        .isFalse();
  }

  @Test
  @DisplayName("canAssignCourse: ADMIN không có vai trò Bộ môn -> bị từ chối")
  void canAssignCourse_adminDenied() {
    assertThat(service.canAssignCourse(principalWithCourses(Role.ADMIN, null), COURSE_ID))
        .isFalse();
  }

  @Test
  @DisplayName("canAssignCourse: DEPARTMENT_HEAD có môn học trong scope -> được phép phân công")
  void canAssignCourse_departmentHeadInScopeAllowed() {
    when(courseRepository.existsById(COURSE_ID)).thenReturn(true);

    assertThat(
            service.canAssignCourse(
                principalWithCourses(Role.DEPARTMENT_HEAD, List.of(COURSE_ID)), COURSE_ID))
        .isTrue();
  }

  @Test
  @DisplayName("canAssignCourse: DEPARTMENT_HEAD ngoài scope môn -> bị từ chối")
  void canAssignCourse_departmentHeadOutOfScopeDenied() {
    when(courseRepository.existsById(COURSE_ID)).thenReturn(true);

    assertThat(
            service.canAssignCourse(
                principalWithCourses(Role.DEPARTMENT_HEAD, List.of(OTHER_COURSE_ID)), COURSE_ID))
        .isFalse();
  }

  @Test
  @DisplayName("canAssignCourse: LECTURER và PROGRAM_MANAGER không có quyền phân công môn")
  void canAssignCourse_lecturerAndPmDenied() {
    when(courseRepository.existsById(COURSE_ID)).thenReturn(true);

    assertThat(
            service.canAssignCourse(
                principalWithCourses(Role.LECTURER, List.of(COURSE_ID)), COURSE_ID))
        .isFalse();
    assertThat(
            service.canAssignCourse(
                principalWithPrograms(Role.PROGRAM_MANAGER, List.of(PROGRAM_A)), COURSE_ID))
        .isFalse();
  }

  @Test
  @DisplayName("canAccessSyllabus: Phân quyền theo môn học của đề cương (ADR 0010)")
  void syllabus_followsCourse() {
    when(syllabusRepository.findById(SYLLABUS_ID))
        .thenReturn(Optional.of(Syllabus.builder().id(SYLLABUS_ID).courseId(COURSE_ID).build()));
    when(syllabusRepository.findById("671dcba5f123456789abc2ff")).thenReturn(Optional.empty());
    when(courseRepository.existsById(COURSE_ID)).thenReturn(true);

    assertThat(
            service.canAccessSyllabus(
                principalWithCourses(Role.LECTURER, List.of(COURSE_ID)),
                SYLLABUS_ID,
                AccessType.WRITE))
        .isTrue();
    assertThat(
            service.canAccessSyllabus(
                principalWithCourses(Role.LECTURER, List.of(OTHER_COURSE_ID)),
                SYLLABUS_ID,
                AccessType.WRITE))
        .isFalse();
    assertThat(
            service.canAccessSyllabus(
                principalWithCourses(Role.LECTURER, List.of(COURSE_ID)),
                "671dcba5f123456789abc2ff",
                AccessType.WRITE))
        .isFalse();
  }

  @Test
  @DisplayName("canAccessClo: Phân quyền theo clos.courseId (ADR 0010)")
  void clo_followsCourse() {
    when(cloRepository.findById(CLO_ID))
        .thenReturn(Optional.of(Clo.builder().id(CLO_ID).courseId(COURSE_ID).build()));
    when(cloRepository.findById("671dcba5f123456789abc3ff")).thenReturn(Optional.empty());
    when(courseRepository.existsById(COURSE_ID)).thenReturn(true);

    assertThat(
            service.canAccessClo(
                principalWithCourses(Role.LECTURER, List.of(COURSE_ID)), CLO_ID, AccessType.WRITE))
        .isTrue();
    assertThat(
            service.canAccessClo(
                principalWithCourses(Role.LECTURER, List.of(OTHER_COURSE_ID)),
                CLO_ID,
                AccessType.WRITE))
        .isFalse();
    assertThat(
            service.canAccessClo(
                principalWithCourses(Role.LECTURER, List.of(COURSE_ID)),
                "671dcba5f123456789abc3ff",
                AccessType.WRITE))
        .isFalse();
  }

  @Test
  @DisplayName("canAccessCloPiLink: Phân quyền theo programVersionId của liên kết (ADR 0010)")
  void link_followsProgramVersion() {
    when(cloPiLinkRepository.findById(LINK_ID))
        .thenReturn(
            Optional.of(CloPiLink.builder().id(LINK_ID).programVersionId(VERSION_A).build()));
    when(cloPiLinkRepository.findById("671dcba5f123456789abc4ff")).thenReturn(Optional.empty());
    when(programVersionRepository.findById(VERSION_A))
        .thenReturn(
            Optional.of(ProgramVersion.builder().id(VERSION_A).programId(PROGRAM_A).build()));

    assertThat(
            service.canAccessCloPiLink(
                principalWithPrograms(Role.PROGRAM_MANAGER, List.of(PROGRAM_A)),
                LINK_ID,
                AccessType.WRITE))
        .isTrue();
    assertThat(
            service.canAccessCloPiLink(
                principalWithPrograms(Role.PROGRAM_MANAGER, List.of(PROGRAM_OTHER)),
                LINK_ID,
                AccessType.WRITE))
        .isFalse();
    assertThat(
            service.canAccessCloPiLink(
                principalWithPrograms(Role.PROGRAM_MANAGER, List.of(PROGRAM_A)),
                "671dcba5f123456789abc4ff",
                AccessType.WRITE))
        .isFalse();
  }
}
