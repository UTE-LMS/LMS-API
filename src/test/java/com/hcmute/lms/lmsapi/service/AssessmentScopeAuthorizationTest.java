package com.hcmute.lms.lmsapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.hcmute.lms.lmsapi.document.Assessment;
import com.hcmute.lms.lmsapi.document.CourseSection;
import com.hcmute.lms.lmsapi.document.RoleAssignment;
import com.hcmute.lms.lmsapi.document.Syllabus;
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
import com.hcmute.lms.lmsapi.repository.ProgramCourseRepository;
import com.hcmute.lms.lmsapi.repository.ProgramVersionRepository;
import com.hcmute.lms.lmsapi.repository.SyllabusRepository;
import com.hcmute.lms.lmsapi.service.impl.ScopeAuthorizationServiceImpl;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("canAccessAssessment (F04)")
class AssessmentScopeAuthorizationTest {

  private static final String COURSE_ID = "671dcba5f123456789abc101";
  private static final String SYLLABUS_ID = "671dcba5f123456789abc200";
  private static final String SECTION_ID = "671dcba5f123456789abc501";
  private static final String TEMPLATE_ID = "671dcba5f123456789abc801";
  private static final String SECTION_ASSESSMENT_ID = "671dcba5f123456789abc802";
  private static final String USER_ID = "671dcba5f123456789abc00a";

  @Mock private CourseSectionRepository courseSectionRepository;
  @Mock private CourseRepository courseRepository;
  @Mock private ProgramVersionRepository programVersionRepository;
  @Mock private ProgramCourseRepository programCourseRepository;
  @Mock private SyllabusRepository syllabusRepository;
  @Mock private CloRepository cloRepository;
  @Mock private CloPiLinkRepository cloPiLinkRepository;
  @Mock private EnrollmentRepository enrollmentRepository;
  @Mock private AssessmentRepository assessmentRepository;

  @InjectMocks private ScopeAuthorizationServiceImpl service;

  @BeforeEach
  void stubData() {
    lenient()
        .when(assessmentRepository.findById(TEMPLATE_ID))
        .thenReturn(
            Optional.of(
                Assessment.builder()
                    .id(TEMPLATE_ID)
                    .scope(AssessmentScope.SYLLABUS_TEMPLATE)
                    .syllabusId(SYLLABUS_ID)
                    .build()));
    lenient()
        .when(assessmentRepository.findById(SECTION_ASSESSMENT_ID))
        .thenReturn(
            Optional.of(
                Assessment.builder()
                    .id(SECTION_ASSESSMENT_ID)
                    .scope(AssessmentScope.SECTION)
                    .syllabusId(SYLLABUS_ID)
                    .sectionId(SECTION_ID)
                    .build()));
    lenient()
        .when(syllabusRepository.findById(SYLLABUS_ID))
        .thenReturn(Optional.of(Syllabus.builder().id(SYLLABUS_ID).courseId(COURSE_ID).build()));
    lenient().when(courseRepository.existsById(COURSE_ID)).thenReturn(true);
    lenient()
        .when(courseSectionRepository.findById(SECTION_ID))
        .thenReturn(
            Optional.of(
                CourseSection.builder()
                    .id(SECTION_ID)
                    .courseId(COURSE_ID)
                    .lecturerIds(new ArrayList<>(List.of(USER_ID)))
                    .build()));
  }

  private UserPrincipal principal(Role role, String... courseIds) {
    UserScope scope = UserScope.builder().courseIds(new ArrayList<>(List.of(courseIds))).build();
    return UserPrincipal.builder()
        .id(USER_ID)
        .username("user")
        .roleAssignments(
            new ArrayList<>(List.of(RoleAssignment.builder().role(role).scope(scope).build())))
        .build();
  }

  @Test
  @DisplayName(
      "Bản lớp: quyền theo lớp - giảng viên của lớp đọc/ghi được, giảng viên khác thì không")
  void sectionAssessmentFollowsSectionAccess() {
    UserPrincipal assigned = principal(Role.LECTURER);
    assertThat(service.canAccessAssessment(assigned, SECTION_ASSESSMENT_ID, AccessType.READ))
        .isTrue();
    assertThat(service.canAccessAssessment(assigned, SECTION_ASSESSMENT_ID, AccessType.WRITE))
        .isTrue();

    UserPrincipal stranger =
        UserPrincipal.builder()
            .id("671dcba5f123456789abc0ff")
            .username("stranger")
            .roleAssignments(
                new ArrayList<>(
                    List.of(
                        RoleAssignment.builder()
                            .role(Role.LECTURER)
                            .scope(UserScope.builder().build())
                            .build())))
            .build();
    assertThat(service.canAccessAssessment(stranger, SECTION_ASSESSMENT_ID, AccessType.READ))
        .isFalse();
  }

  @Test
  @DisplayName(
      "Khung: quyền theo đề cương (ADR 0010) - PM và giảng viên có học phần trong scope ghi được, QA chỉ đọc")
  void templateFollowsSyllabusAccess() {
    assertThat(
            service.canAccessAssessment(
                principal(Role.PROGRAM_MANAGER, COURSE_ID), TEMPLATE_ID, AccessType.WRITE))
        .isTrue();
    assertThat(
            service.canAccessAssessment(
                principal(Role.QA, COURSE_ID), TEMPLATE_ID, AccessType.WRITE))
        .isFalse();
    assertThat(
            service.canAccessAssessment(
                principal(Role.QA, COURSE_ID), TEMPLATE_ID, AccessType.READ))
        .isTrue();
  }

  @Test
  @DisplayName("canAccessAssessment: fail-closed khi bài không tồn tại hoặc ID rỗng")
  void accessFailClosed() {
    when(assessmentRepository.findById("671dcba5f123456789abc8ff")).thenReturn(Optional.empty());

    assertThat(
            service.canAccessAssessment(
                principal(Role.ADMIN), "671dcba5f123456789abc8ff", AccessType.READ))
        .isFalse();
    assertThat(service.canAccessAssessment(principal(Role.ADMIN), " ", AccessType.READ)).isFalse();
    assertThat(service.canAccessAssessment(principal(Role.ADMIN), null, AccessType.READ)).isFalse();
  }
}
