package com.hcmute.lms.lmsapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hcmute.lms.lmsapi.document.CourseSection;
import com.hcmute.lms.lmsapi.document.ProgramCourse;
import com.hcmute.lms.lmsapi.document.ProgramVersion;
import com.hcmute.lms.lmsapi.document.RoleAssignment;
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
@DisplayName("canManageCourseSections/canManageSection/canAccessLoadedSection (F05a / US-19)")
class SectionScopeAuthorizationTest {

  private static final String COURSE_ID = "671dcba5f123456789abc101";
  private static final String SECTION_ID = "671dcba5f123456789abc501";
  private static final String VERSION_ID = "671dcba5f123456789abc102";
  private static final String PROGRAM_ID = "671dcba5f123456789abc001";
  private static final String OTHER_PROGRAM_ID = "671dcba5f123456789abc099";
  private static final String LECTURER_ID = "671dcba5f123456789abc00a";

  @Mock private CourseSectionRepository courseSectionRepository;
  @Mock private CourseRepository courseRepository;
  @Mock private ProgramVersionRepository programVersionRepository;
  @Mock private ProgramCourseRepository programCourseRepository;
  @Mock private SyllabusRepository syllabusRepository;
  @Mock private CloRepository cloRepository;
  @Mock private CloPiLinkRepository cloPiLinkRepository;

  @InjectMocks private ScopeAuthorizationServiceImpl service;

  private UserPrincipal principal(Role role, UserScope scope) {
    return UserPrincipal.builder()
        .id(LECTURER_ID)
        .username("user")
        .roleAssignments(
            new ArrayList<>(List.of(RoleAssignment.builder().role(role).scope(scope).build())))
        .build();
  }

  private UserScope programs(String... programIds) {
    return UserScope.builder().programIds(new ArrayList<>(List.of(programIds))).build();
  }

  private void courseBelongsToProgram(String programId) {
    when(programCourseRepository.findByCourseId(COURSE_ID))
        .thenReturn(List.of(ProgramCourse.builder().programVersionId(VERSION_ID).build()));
    when(programVersionRepository.findById(VERSION_ID))
        .thenReturn(Optional.of(ProgramVersion.builder().programId(programId).build()));
  }

  @Test
  @DisplayName("ADMIN luôn được quản lý lớp của mọi học phần, không cần tra CTĐT")
  void admin_alwaysAllowed() {
    assertThat(service.canManageCourseSections(principal(Role.ADMIN, null), COURSE_ID)).isTrue();
    verifyNoInteractions(programCourseRepository, programVersionRepository);
  }

  @Test
  @DisplayName("PROGRAM_MANAGER có CTĐT chứa học phần trong scope.programIds -> được phép")
  void programManagerInScope_allowed() {
    courseBelongsToProgram(PROGRAM_ID);

    assertThat(
            service.canManageCourseSections(
                principal(Role.PROGRAM_MANAGER, programs(PROGRAM_ID)), COURSE_ID))
        .isTrue();
  }

  @Test
  @DisplayName("PROGRAM_MANAGER có CTĐT khác (ngoài phạm vi) -> bị từ chối")
  void programManagerOutOfScope_denied() {
    courseBelongsToProgram(OTHER_PROGRAM_ID);

    assertThat(
            service.canManageCourseSections(
                principal(Role.PROGRAM_MANAGER, programs(PROGRAM_ID)), COURSE_ID))
        .isFalse();
  }

  @Test
  @DisplayName("PROGRAM_MANAGER với học phần chưa thuộc CTĐT nào -> chỉ ADMIN được phép")
  void programManagerCourseWithoutProgram_denied() {
    when(programCourseRepository.findByCourseId(COURSE_ID)).thenReturn(List.of());

    assertThat(
            service.canManageCourseSections(
                principal(Role.PROGRAM_MANAGER, programs(PROGRAM_ID)), COURSE_ID))
        .isFalse();
  }

  @Test
  @DisplayName("PROGRAM_MANAGER scope rỗng (không có programIds) -> bị từ chối, không tra CSDL")
  void programManagerEmptyScope_denied() {
    assertThat(
            service.canManageCourseSections(
                principal(Role.PROGRAM_MANAGER, UserScope.builder().build()), COURSE_ID))
        .isFalse();
    assertThat(service.canManageCourseSections(principal(Role.PROGRAM_MANAGER, null), COURSE_ID))
        .isFalse();
    verifyNoInteractions(programCourseRepository);
  }

  @Test
  @DisplayName(
      "Các vai trò khác (LECTURER, DEPARTMENT_HEAD, QA_OFFICER, VIEWER, STUDENT) bị từ chối")
  void otherRoles_denied() {
    for (Role role :
        List.of(Role.LECTURER, Role.DEPARTMENT_HEAD, Role.QA_OFFICER, Role.VIEWER, Role.STUDENT)) {
      assertThat(
              service.canManageCourseSections(
                  principal(
                      role,
                      UserScope.builder()
                          .programIds(new ArrayList<>(List.of(PROGRAM_ID)))
                          .courseIds(new ArrayList<>(List.of(COURSE_ID)))
                          .build()),
                  COURSE_ID))
          .as("role %s", role)
          .isFalse();
    }
  }

  @Test
  @DisplayName("Fail-closed: principal/courseId null hoặc rỗng -> false")
  void failClosedOnNullOrBlank() {
    assertThat(service.canManageCourseSections(null, COURSE_ID)).isFalse();
    assertThat(service.canManageCourseSections(principal(Role.ADMIN, null), null)).isFalse();
    assertThat(service.canManageCourseSections(principal(Role.ADMIN, null), " ")).isFalse();
    assertThat(service.canManageSection(principal(Role.ADMIN, null), null)).isFalse();
    assertThat(service.canManageSection(principal(Role.ADMIN, null), " ")).isFalse();
  }

  @Test
  @DisplayName("canManageSection suy courseId từ lớp; lớp không tồn tại -> false")
  void canManageSection_resolvesCourseFromSection() {
    when(courseSectionRepository.findById(SECTION_ID))
        .thenReturn(
            Optional.of(CourseSection.builder().id(SECTION_ID).courseId(COURSE_ID).build()));
    courseBelongsToProgram(PROGRAM_ID);

    assertThat(
            service.canManageSection(
                principal(Role.PROGRAM_MANAGER, programs(PROGRAM_ID)), SECTION_ID))
        .isTrue();

    when(courseSectionRepository.findById("671dcba5f123456789abc5ff")).thenReturn(Optional.empty());
    assertThat(
            service.canManageSection(
                principal(Role.PROGRAM_MANAGER, programs(PROGRAM_ID)), "671dcba5f123456789abc5ff"))
        .isFalse();
  }

  @Test
  @DisplayName("canAccessLoadedSection: giảng viên có tên trong lecturerIds đọc/ghi được lớp")
  void canAccessLoadedSection_assignedLecturer() {
    CourseSection section =
        CourseSection.builder()
            .id(SECTION_ID)
            .courseId(COURSE_ID)
            .lecturerIds(new ArrayList<>(List.of(LECTURER_ID)))
            .build();
    UserPrincipal lecturer = principal(Role.LECTURER, UserScope.builder().build());

    assertThat(service.canAccessLoadedSection(lecturer, section, AccessType.READ)).isTrue();
    assertThat(service.canAccessLoadedSection(lecturer, section, AccessType.WRITE)).isTrue();

    section.setLecturerIds(new ArrayList<>(List.of("671dcba5f123456789abc0ff")));
    assertThat(service.canAccessLoadedSection(lecturer, section, AccessType.READ)).isFalse();
  }

  @Test
  @DisplayName("canAccessLoadedSection: fail-closed khi thiếu tham số hoặc không có vai trò")
  void canAccessLoadedSection_failClosed() {
    CourseSection section = CourseSection.builder().id(SECTION_ID).build();

    assertThat(service.canAccessLoadedSection(null, section, AccessType.READ)).isFalse();
    assertThat(service.canAccessLoadedSection(principal(Role.ADMIN, null), null, AccessType.READ))
        .isFalse();
    assertThat(service.canAccessLoadedSection(principal(Role.ADMIN, null), section, null))
        .isFalse();
    assertThat(
            service.canAccessLoadedSection(
                UserPrincipal.builder().id(LECTURER_ID).build(), section, AccessType.READ))
        .isFalse();
  }
}
