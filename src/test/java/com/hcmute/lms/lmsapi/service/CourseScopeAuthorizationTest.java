package com.hcmute.lms.lmsapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hcmute.lms.lmsapi.document.Clo;
import com.hcmute.lms.lmsapi.document.CloPiLink;
import com.hcmute.lms.lmsapi.document.ProgramCourse;
import com.hcmute.lms.lmsapi.document.ProgramVersion;
import com.hcmute.lms.lmsapi.document.RoleAssignment;
import com.hcmute.lms.lmsapi.document.Syllabus;
import com.hcmute.lms.lmsapi.document.UserScope;
import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import com.hcmute.lms.lmsapi.enums.AccessType;
import com.hcmute.lms.lmsapi.enums.Role;
import com.hcmute.lms.lmsapi.repository.CloPiLinkRepository;
import com.hcmute.lms.lmsapi.repository.CloRepository;
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
@DisplayName("canAccessCourse/Syllabus/Clo/CloPiLink Unit Tests (F03 / ADR 0009)")
class CourseScopeAuthorizationTest {

  private static final String COURSE_ID = "671dcba5f123456789abc101";
  private static final String VERSION_A = "671dcba5f123456789abc102";
  private static final String VERSION_B = "671dcba5f123456789abc103";
  private static final String PROGRAM_A = "671dcba5f123456789abc001";
  private static final String PROGRAM_B = "671dcba5f123456789abc002";
  private static final String PROGRAM_OTHER = "671dcba5f123456789abc099";
  private static final String SYLLABUS_ID = "671dcba5f123456789abc201";
  private static final String CLO_ID = "671dcba5f123456789abc301";
  private static final String LINK_ID = "671dcba5f123456789abc401";

  @Mock private CourseSectionRepository courseSectionRepository;
  @Mock private ProgramVersionRepository programVersionRepository;
  @Mock private ProgramCourseRepository programCourseRepository;
  @Mock private SyllabusRepository syllabusRepository;
  @Mock private CloRepository cloRepository;
  @Mock private CloPiLinkRepository cloPiLinkRepository;

  @InjectMocks private ScopeAuthorizationServiceImpl service;

  private UserPrincipal principal(Role role, List<String> programIds) {
    UserScope scope =
        programIds == null ? null : UserScope.builder().programIds(programIds).build();
    return UserPrincipal.builder()
        .id("671dcba5f123456789abc00a")
        .username("user")
        .roleAssignments(
            new ArrayList<>(List.of(RoleAssignment.builder().role(role).scope(scope).build())))
        .build();
  }

  private void courseInVersion(String versionId, String programId) {
    when(programVersionRepository.findById(versionId))
        .thenReturn(
            Optional.of(ProgramVersion.builder().id(versionId).programId(programId).build()));
  }

  private static ProgramCourse placement(String versionId) {
    return ProgramCourse.builder().programVersionId(versionId).courseId(COURSE_ID).build();
  }

  @Test
  @DisplayName("PM có scope khớp một CTĐT chứa học phần được phép ghi")
  void pmInScope_allowed() {
    when(programCourseRepository.findByCourseId(COURSE_ID))
        .thenReturn(List.of(placement(VERSION_A)));
    courseInVersion(VERSION_A, PROGRAM_A);

    assertThat(
            service.canAccessCourse(
                principal(Role.PROGRAM_MANAGER, List.of(PROGRAM_A)), COURSE_ID, AccessType.WRITE))
        .isTrue();
  }

  @Test
  @DisplayName("PM ngoài scope bị từ chối")
  void pmOutOfScope_denied() {
    when(programCourseRepository.findByCourseId(COURSE_ID))
        .thenReturn(List.of(placement(VERSION_A)));
    courseInVersion(VERSION_A, PROGRAM_A);

    assertThat(
            service.canAccessCourse(
                principal(Role.PROGRAM_MANAGER, List.of(PROGRAM_OTHER)),
                COURSE_ID,
                AccessType.WRITE))
        .isFalse();
  }

  @Test
  @DisplayName("Học phần thuộc nhiều CTĐT: một CTĐT trong scope là đủ (phép HỢP)")
  void courseInSeveralPrograms_unionOfScopes() {
    when(programCourseRepository.findByCourseId(COURSE_ID))
        .thenReturn(List.of(placement(VERSION_A), placement(VERSION_B)));
    courseInVersion(VERSION_A, PROGRAM_A);
    courseInVersion(VERSION_B, PROGRAM_B);

    assertThat(
            service.canAccessCourse(
                principal(Role.PROGRAM_MANAGER, List.of(PROGRAM_B)), COURSE_ID, AccessType.WRITE))
        .isTrue();
  }

  @Test
  @DisplayName("Học phần chưa thuộc CTĐT nào: PM bị từ chối, ADMIN được phép")
  void orphanCourse_adminOnly() {
    when(programCourseRepository.findByCourseId(COURSE_ID)).thenReturn(List.of());

    assertThat(
            service.canAccessCourse(
                principal(Role.PROGRAM_MANAGER, List.of(PROGRAM_A)), COURSE_ID, AccessType.WRITE))
        .isFalse();
    assertThat(service.canAccessCourse(principal(Role.ADMIN, null), COURSE_ID, AccessType.WRITE))
        .isTrue();
  }

  @Test
  @DisplayName("PM có scope rỗng bị từ chối (fail-closed)")
  void pmEmptyScope_denied() {
    when(programCourseRepository.findByCourseId(COURSE_ID))
        .thenReturn(List.of(placement(VERSION_A)));
    courseInVersion(VERSION_A, PROGRAM_A);

    assertThat(
            service.canAccessCourse(
                principal(Role.PROGRAM_MANAGER, List.of()), COURSE_ID, AccessType.WRITE))
        .isFalse();
    assertThat(
            service.canAccessCourse(
                principal(Role.PROGRAM_MANAGER, null), COURSE_ID, AccessType.WRITE))
        .isFalse();
  }

  @Test
  @DisplayName("LECTURER không có quyền ghi dù học phần nằm trong CTĐT")
  void lecturer_denied() {
    when(programCourseRepository.findByCourseId(COURSE_ID))
        .thenReturn(List.of(placement(VERSION_A)));
    courseInVersion(VERSION_A, PROGRAM_A);

    assertThat(
            service.canAccessCourse(
                principal(Role.LECTURER, List.of(PROGRAM_A)), COURSE_ID, AccessType.WRITE))
        .isFalse();
  }

  @Test
  @DisplayName("Tham số null/rỗng hoặc không có phân công vai trò bị từ chối")
  void invalidInput_denied() {
    UserPrincipal pm = principal(Role.PROGRAM_MANAGER, List.of(PROGRAM_A));
    UserPrincipal noRoles = UserPrincipal.builder().id("x").username("x").build();

    assertThat(service.canAccessCourse(null, COURSE_ID, AccessType.WRITE)).isFalse();
    assertThat(service.canAccessCourse(pm, null, AccessType.WRITE)).isFalse();
    assertThat(service.canAccessCourse(pm, " ", AccessType.WRITE)).isFalse();
    assertThat(service.canAccessCourse(pm, COURSE_ID, null)).isFalse();
    assertThat(service.canAccessCourse(noRoles, COURSE_ID, AccessType.WRITE)).isFalse();
    assertThat(service.canAccessSyllabus(pm, " ", AccessType.WRITE)).isFalse();
    assertThat(service.canAccessClo(pm, null, AccessType.WRITE)).isFalse();
    assertThat(service.canAccessCloPiLink(pm, "", AccessType.WRITE)).isFalse();
    verifyNoInteractions(programCourseRepository);
  }

  @Test
  @DisplayName("Đề cương được phân quyền theo học phần của nó; không tồn tại -> từ chối")
  void syllabus_followsCourse() {
    when(syllabusRepository.findById(SYLLABUS_ID))
        .thenReturn(Optional.of(Syllabus.builder().id(SYLLABUS_ID).courseId(COURSE_ID).build()));
    when(syllabusRepository.findById("671dcba5f123456789abc2ff")).thenReturn(Optional.empty());
    when(programCourseRepository.findByCourseId(COURSE_ID))
        .thenReturn(List.of(placement(VERSION_A)));
    courseInVersion(VERSION_A, PROGRAM_A);

    assertThat(
            service.canAccessSyllabus(
                principal(Role.PROGRAM_MANAGER, List.of(PROGRAM_A)), SYLLABUS_ID, AccessType.WRITE))
        .isTrue();
    assertThat(
            service.canAccessSyllabus(
                principal(Role.PROGRAM_MANAGER, List.of(PROGRAM_OTHER)),
                SYLLABUS_ID,
                AccessType.WRITE))
        .isFalse();
    assertThat(
            service.canAccessSyllabus(
                principal(Role.PROGRAM_MANAGER, List.of(PROGRAM_A)),
                "671dcba5f123456789abc2ff",
                AccessType.WRITE))
        .isFalse();
  }

  @Test
  @DisplayName("CLO được phân quyền theo clos.courseId; không tồn tại -> từ chối")
  void clo_followsCourse() {
    when(cloRepository.findById(CLO_ID))
        .thenReturn(Optional.of(Clo.builder().id(CLO_ID).courseId(COURSE_ID).build()));
    when(cloRepository.findById("671dcba5f123456789abc3ff")).thenReturn(Optional.empty());
    when(programCourseRepository.findByCourseId(COURSE_ID))
        .thenReturn(List.of(placement(VERSION_A)));
    courseInVersion(VERSION_A, PROGRAM_A);

    assertThat(
            service.canAccessClo(
                principal(Role.PROGRAM_MANAGER, List.of(PROGRAM_A)), CLO_ID, AccessType.WRITE))
        .isTrue();
    assertThat(
            service.canAccessClo(
                principal(Role.PROGRAM_MANAGER, List.of(PROGRAM_OTHER)), CLO_ID, AccessType.WRITE))
        .isFalse();
    assertThat(
            service.canAccessClo(
                principal(Role.PROGRAM_MANAGER, List.of(PROGRAM_A)),
                "671dcba5f123456789abc3ff",
                AccessType.WRITE))
        .isFalse();
  }

  @Test
  @DisplayName("Liên kết CLO–PI được phân quyền theo programVersionId của nó")
  void link_followsProgramVersion() {
    when(cloPiLinkRepository.findById(LINK_ID))
        .thenReturn(
            Optional.of(CloPiLink.builder().id(LINK_ID).programVersionId(VERSION_A).build()));
    when(cloPiLinkRepository.findById("671dcba5f123456789abc4ff")).thenReturn(Optional.empty());
    courseInVersion(VERSION_A, PROGRAM_A);

    assertThat(
            service.canAccessCloPiLink(
                principal(Role.PROGRAM_MANAGER, List.of(PROGRAM_A)), LINK_ID, AccessType.WRITE))
        .isTrue();
    assertThat(
            service.canAccessCloPiLink(
                principal(Role.PROGRAM_MANAGER, List.of(PROGRAM_OTHER)), LINK_ID, AccessType.WRITE))
        .isFalse();
    assertThat(
            service.canAccessCloPiLink(
                principal(Role.PROGRAM_MANAGER, List.of(PROGRAM_A)),
                "671dcba5f123456789abc4ff",
                AccessType.WRITE))
        .isFalse();
  }

  @Test
  @DisplayName("QA_OFFICER có scope khớp vẫn KHÔNG có quyền ghi đề cương/CLO")
  void qaOfficer_cannotWrite() {
    when(programCourseRepository.findByCourseId(COURSE_ID))
        .thenReturn(List.of(placement(VERSION_A)));
    courseInVersion(VERSION_A, PROGRAM_A);

    assertThat(
            service.canAccessCourse(
                principal(Role.QA_OFFICER, List.of(PROGRAM_A)), COURSE_ID, AccessType.WRITE))
        .isFalse();
  }

  @Test
  @DisplayName("Người dùng PM(ngành A) + QA(ngành B): quyền QA ở ngành B không thành quyền ghi")
  void pmPlusQaOnOtherProgram_doesNotAccumulate() {
    when(programCourseRepository.findByCourseId(COURSE_ID))
        .thenReturn(List.of(placement(VERSION_B)));
    courseInVersion(VERSION_B, PROGRAM_B);
    UserPrincipal multiRole =
        UserPrincipal.builder()
            .id("671dcba5f123456789abc00b")
            .username("multi")
            .roleAssignments(
                new ArrayList<>(
                    List.of(
                        RoleAssignment.builder()
                            .role(Role.PROGRAM_MANAGER)
                            .scope(UserScope.builder().programIds(List.of(PROGRAM_A)).build())
                            .build(),
                        RoleAssignment.builder()
                            .role(Role.QA_OFFICER)
                            .scope(UserScope.builder().programIds(List.of(PROGRAM_B)).build())
                            .build())))
            .build();

    assertThat(service.canAccessCourse(multiRole, COURSE_ID, AccessType.WRITE)).isFalse();
  }

  @Test
  @DisplayName("ADMIN không cần tra program_courses")
  void admin_doesNotTouchRepository() {
    assertThat(service.canAccessCourse(principal(Role.ADMIN, null), COURSE_ID, AccessType.WRITE))
        .isTrue();
    verifyNoInteractions(programCourseRepository);
  }

  @Test
  @DisplayName("Placement trỏ tới phiên bản CTĐT không còn tồn tại -> từ chối")
  void danglingPlacement_denied() {
    when(programCourseRepository.findByCourseId(COURSE_ID))
        .thenReturn(List.of(placement(VERSION_A)));
    when(programVersionRepository.findById(VERSION_A)).thenReturn(Optional.empty());

    assertThat(
            service.canAccessCourse(
                principal(Role.PROGRAM_MANAGER, List.of(PROGRAM_A)), COURSE_ID, AccessType.WRITE))
        .isFalse();
  }

  @Test
  @DisplayName("Placement ngoài scope đứng trước placement trong scope vẫn được phép")
  void outOfScopePlacementFirst_stillAllowed() {
    when(programCourseRepository.findByCourseId(COURSE_ID))
        .thenReturn(List.of(placement(VERSION_B), placement(VERSION_A)));
    courseInVersion(VERSION_B, PROGRAM_B);
    courseInVersion(VERSION_A, PROGRAM_A);

    assertThat(
            service.canAccessCourse(
                principal(Role.PROGRAM_MANAGER, List.of(PROGRAM_A)), COURSE_ID, AccessType.WRITE))
        .isTrue();
  }
}
