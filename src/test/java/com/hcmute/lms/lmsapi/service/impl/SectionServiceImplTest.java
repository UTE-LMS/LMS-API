package com.hcmute.lms.lmsapi.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hcmute.lms.lmsapi.document.CourseSection;
import com.hcmute.lms.lmsapi.document.RoleAssignment;
import com.hcmute.lms.lmsapi.document.Syllabus;
import com.hcmute.lms.lmsapi.document.Term;
import com.hcmute.lms.lmsapi.document.User;
import com.hcmute.lms.lmsapi.dto.SectionRequest;
import com.hcmute.lms.lmsapi.dto.SectionResponse;
import com.hcmute.lms.lmsapi.dto.SectionStatusUpdateRequest;
import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import com.hcmute.lms.lmsapi.enums.AccessType;
import com.hcmute.lms.lmsapi.enums.Role;
import com.hcmute.lms.lmsapi.enums.SectionStatus;
import com.hcmute.lms.lmsapi.enums.SyllabusStatus;
import com.hcmute.lms.lmsapi.enums.TermStatus;
import com.hcmute.lms.lmsapi.enums.UserStatus;
import com.hcmute.lms.lmsapi.exception.AppException;
import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
import com.hcmute.lms.lmsapi.exception.ErrorCode;
import com.hcmute.lms.lmsapi.exception.ResourceNotFoundException;
import com.hcmute.lms.lmsapi.mapper.impl.SectionMapperImpl;
import com.hcmute.lms.lmsapi.repository.CourseRepository;
import com.hcmute.lms.lmsapi.repository.CourseSectionRepository;
import com.hcmute.lms.lmsapi.repository.SyllabusRepository;
import com.hcmute.lms.lmsapi.repository.TermRepository;
import com.hcmute.lms.lmsapi.repository.UserRepository;
import com.hcmute.lms.lmsapi.service.ScopeAuthorizationService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class SectionServiceImplTest {

  private static final String COURSE_ID = "671dcba5f123456789abc101";
  private static final String SYLLABUS_ID = "671dcba5f123456789abc201";
  private static final String TERM_ID = "671dcba5f123456789abc301";
  private static final String LECTURER_ID = "671dcba5f123456789abc401";
  private static final String SECTION_ID = "671dcba5f123456789abc501";

  @Mock private CourseSectionRepository sectionRepository;
  @Mock private CourseRepository courseRepository;
  @Mock private SyllabusRepository syllabusRepository;
  @Mock private TermRepository termRepository;
  @Mock private UserRepository userRepository;
  @Mock private ScopeAuthorizationService scopeAuthorizationService;

  private SectionServiceImpl service;

  @BeforeEach
  void setUp() {
    service =
        new SectionServiceImpl(
            sectionRepository,
            courseRepository,
            syllabusRepository,
            termRepository,
            userRepository,
            scopeAuthorizationService,
            new SectionMapperImpl());
  }

  private SectionRequest request() {
    return SectionRequest.builder()
        .code("SEC-01")
        .courseId(COURSE_ID)
        .syllabusId(SYLLABUS_ID)
        .termId(TERM_ID)
        .lecturerIds(List.of(LECTURER_ID))
        .build();
  }

  private User lecturer(UserStatus status, Role role) {
    return User.builder()
        .id(LECTURER_ID)
        .username("lecturer")
        .status(status)
        .roleAssignments(new ArrayList<>(List.of(RoleAssignment.builder().role(role).build())))
        .build();
  }

  private void stubValidReferences() {
    when(sectionRepository.existsByCode("SEC-01")).thenReturn(false);
    when(courseRepository.existsById(COURSE_ID)).thenReturn(true);
    when(syllabusRepository.findById(SYLLABUS_ID))
        .thenReturn(
            Optional.of(
                Syllabus.builder()
                    .id(SYLLABUS_ID)
                    .courseId(COURSE_ID)
                    .status(SyllabusStatus.CONFIRMED)
                    .build()));
    when(termRepository.findById(TERM_ID))
        .thenReturn(
            Optional.of(
                Term.builder().id(TERM_ID).code("2026-HK1").status(TermStatus.OPEN).build()));
    when(userRepository.findAllById(List.of(LECTURER_ID)))
        .thenReturn(List.of(lecturer(UserStatus.ACTIVE, Role.LECTURER)));
  }

  @Test
  @DisplayName("US-19/AC-1: mở lớp hợp lệ -> lưu OPEN, sĩ số 0")
  void createSection_success() {
    stubValidReferences();
    when(sectionRepository.save(any(CourseSection.class)))
        .thenAnswer(
            invocation -> {
              CourseSection s = invocation.getArgument(0);
              s.setId(SECTION_ID);
              return s;
            });

    SectionResponse response = service.createSection(request());

    assertThat(response.getId()).isEqualTo(SECTION_ID);
    assertThat(response.getStatus()).isEqualTo(SectionStatus.OPEN);
    assertThat(response.getEnrolledCount()).isZero();
    assertThat(response.getLecturerIds()).containsExactly(LECTURER_ID);
  }

  @Test
  @DisplayName("US-19/AC-3: trùng mã lớp -> 409, không kiểm tra tham chiếu, không lưu")
  void createSection_duplicateCode() {
    when(sectionRepository.existsByCode("SEC-01")).thenReturn(true);

    assertThatThrownBy(() -> service.createSection(request()))
        .isInstanceOfSatisfying(
            AppException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
    verify(sectionRepository, never()).save(any());
  }

  @Test
  @DisplayName("US-19/AC-3: đua ghi cùng mã (DuplicateKeyException) cũng trả 409")
  void createSection_duplicateKeyRace() {
    stubValidReferences();
    when(sectionRepository.save(any(CourseSection.class)))
        .thenThrow(new DuplicateKeyException("dup"));

    assertThatThrownBy(() -> service.createSection(request()))
        .isInstanceOfSatisfying(
            AppException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
  }

  @Test
  @DisplayName("US-19/AC-2: học phần không tồn tại -> 422")
  void createSection_unknownCourse() {
    when(sectionRepository.existsByCode("SEC-01")).thenReturn(false);
    when(courseRepository.existsById(COURSE_ID)).thenReturn(false);

    assertThatThrownBy(() -> service.createSection(request()))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessageContaining("Học phần không tồn tại");
  }

  @Test
  @DisplayName("US-19/AC-2: đề cương không tồn tại / khác học phần / chưa CONFIRMED -> 422")
  void createSection_syllabusRules() {
    when(sectionRepository.existsByCode("SEC-01")).thenReturn(false);
    when(courseRepository.existsById(COURSE_ID)).thenReturn(true);

    when(syllabusRepository.findById(SYLLABUS_ID)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.createSection(request()))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessageContaining("Đề cương không tồn tại");

    when(syllabusRepository.findById(SYLLABUS_ID))
        .thenReturn(
            Optional.of(
                Syllabus.builder()
                    .courseId("671dcba5f123456789abc999")
                    .status(SyllabusStatus.CONFIRMED)
                    .build()));
    assertThatThrownBy(() -> service.createSection(request()))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessageContaining("không thuộc học phần");

    when(syllabusRepository.findById(SYLLABUS_ID))
        .thenReturn(
            Optional.of(
                Syllabus.builder().courseId(COURSE_ID).status(SyllabusStatus.DRAFT).build()));
    assertThatThrownBy(() -> service.createSection(request()))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessageContaining("đã chốt");
  }

  @Test
  @DisplayName("US-19/AC-2: học kỳ không tồn tại hoặc không OPEN -> 422")
  void createSection_termRules() {
    when(sectionRepository.existsByCode("SEC-01")).thenReturn(false);
    when(courseRepository.existsById(COURSE_ID)).thenReturn(true);
    when(syllabusRepository.findById(SYLLABUS_ID))
        .thenReturn(
            Optional.of(
                Syllabus.builder().courseId(COURSE_ID).status(SyllabusStatus.CONFIRMED).build()));

    when(termRepository.findById(TERM_ID)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.createSection(request()))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessageContaining("Học kỳ không tồn tại");

    when(termRepository.findById(TERM_ID))
        .thenReturn(
            Optional.of(Term.builder().code("2026-HK1").status(TermStatus.SCORE_LOCKED).build()));
    assertThatThrownBy(() -> service.createSection(request()))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessageContaining("SCORE_LOCKED");
  }

  @Test
  @DisplayName("US-19/AC-2: giảng viên không tồn tại / bị khóa / không có vai trò LECTURER -> 422")
  void createSection_lecturerRules() {
    stubValidReferences();

    when(userRepository.findAllById(List.of(LECTURER_ID))).thenReturn(List.of());
    assertThatThrownBy(() -> service.createSection(request()))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessageContaining("Giảng viên không tồn tại");

    when(userRepository.findAllById(List.of(LECTURER_ID)))
        .thenReturn(List.of(lecturer(UserStatus.LOCKED, Role.LECTURER)));
    assertThatThrownBy(() -> service.createSection(request()))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessageContaining("ACTIVE");

    when(userRepository.findAllById(List.of(LECTURER_ID)))
        .thenReturn(List.of(lecturer(UserStatus.ACTIVE, Role.QA)));
    assertThatThrownBy(() -> service.createSection(request()))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessageContaining("LECTURER");
  }

  @Test
  @DisplayName("US-19: đổi trạng thái LOCKED -> OPEN cần lý do; có lý do thì thành công")
  void updateStatus_reopenLockedNeedsReason() {
    CourseSection locked =
        CourseSection.builder().id(SECTION_ID).code("SEC-01").status(SectionStatus.LOCKED).build();
    when(sectionRepository.findById(SECTION_ID)).thenReturn(Optional.of(locked));
    when(sectionRepository.save(any(CourseSection.class))).thenAnswer(i -> i.getArgument(0));

    assertThatThrownBy(
            () ->
                service.updateStatus(
                    SECTION_ID,
                    SectionStatusUpdateRequest.builder().status(SectionStatus.OPEN).build(),
                    "actor"))
        .isInstanceOf(BusinessRuleViolationException.class);
    verify(sectionRepository, never()).save(any());

    SectionResponse response =
        service.updateStatus(
            SECTION_ID,
            SectionStatusUpdateRequest.builder()
                .status(SectionStatus.OPEN)
                .reason("Phúc khảo")
                .build(),
            "actor");
    assertThat(response.getStatus()).isEqualTo(SectionStatus.OPEN);
  }

  @Test
  @DisplayName("US-19: đổi sang đúng trạng thái hiện tại là no-op (không ghi)")
  void updateStatus_sameStatusNoOp() {
    when(sectionRepository.findById(SECTION_ID))
        .thenReturn(
            Optional.of(CourseSection.builder().id(SECTION_ID).status(SectionStatus.OPEN).build()));

    SectionResponse response =
        service.updateStatus(
            SECTION_ID,
            SectionStatusUpdateRequest.builder().status(SectionStatus.OPEN).build(),
            "actor");

    assertThat(response.getStatus()).isEqualTo(SectionStatus.OPEN);
    verify(sectionRepository, never()).save(any());
  }

  @Test
  @DisplayName("US-19/AC-4: getSections chỉ trả lớp người gọi đọc/quản lý được, sắp theo mã")
  void getSections_filtersByAuthorization() {
    CourseSection visibleB =
        CourseSection.builder().id("b").code("SEC-B").courseId(COURSE_ID).build();
    CourseSection visibleA =
        CourseSection.builder().id("a").code("SEC-A").courseId(COURSE_ID).build();
    CourseSection hidden =
        CourseSection.builder().id("c").code("SEC-C").courseId("671dcba5f123456789abc999").build();
    UserPrincipal principal = UserPrincipal.builder().id(LECTURER_ID).username("u").build();
    when(sectionRepository.findAll()).thenReturn(List.of(visibleB, hidden, visibleA));
    when(scopeAuthorizationService.canAccessLoadedSection(principal, visibleB, AccessType.READ))
        .thenReturn(true);
    when(scopeAuthorizationService.canAccessLoadedSection(principal, visibleA, AccessType.READ))
        .thenReturn(false);
    when(scopeAuthorizationService.canManageCourseSections(principal, COURSE_ID)).thenReturn(true);
    when(scopeAuthorizationService.canAccessLoadedSection(principal, hidden, AccessType.READ))
        .thenReturn(false);
    when(scopeAuthorizationService.canManageCourseSections(principal, "671dcba5f123456789abc999"))
        .thenReturn(false);

    List<SectionResponse> result = service.getSections(principal, null, null);

    assertThat(result).extracting(SectionResponse::getCode).containsExactly("SEC-A", "SEC-B");
  }

  @Test
  @DisplayName("US-19: bộ lọc dùng đúng truy vấn theo học kỳ/học phần; ID lọc sai -> 400")
  void getSections_filterQueries() {
    UserPrincipal principal = UserPrincipal.builder().id(LECTURER_ID).username("u").build();
    when(sectionRepository.findByTermIdAndCourseId(TERM_ID, COURSE_ID)).thenReturn(List.of());
    when(sectionRepository.findByTermId(TERM_ID)).thenReturn(List.of());
    when(sectionRepository.findByCourseId(COURSE_ID)).thenReturn(List.of());

    assertThat(service.getSections(principal, TERM_ID, COURSE_ID)).isEmpty();
    assertThat(service.getSections(principal, TERM_ID, " ")).isEmpty();
    assertThat(service.getSections(principal, null, COURSE_ID)).isEmpty();
    verify(sectionRepository).findByTermIdAndCourseId(TERM_ID, COURSE_ID);
    verify(sectionRepository).findByTermId(TERM_ID);
    verify(sectionRepository).findByCourseId(COURSE_ID);

    assertThatThrownBy(() -> service.getSections(principal, "bad-id", null))
        .isInstanceOfSatisfying(
            AppException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
  }

  @Test
  @DisplayName("getSection: không tồn tại -> 404; ID sai định dạng -> 400")
  void getSection_notFoundAndInvalid() {
    when(sectionRepository.findById(SECTION_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getSection(SECTION_ID))
        .isInstanceOf(ResourceNotFoundException.class);
    assertThatThrownBy(() -> service.getSection("bad"))
        .isInstanceOfSatisfying(
            AppException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
  }
}
