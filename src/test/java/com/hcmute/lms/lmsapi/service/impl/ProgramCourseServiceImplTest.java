package com.hcmute.lms.lmsapi.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hcmute.lms.lmsapi.document.Course;
import com.hcmute.lms.lmsapi.document.ProgramCourse;
import com.hcmute.lms.lmsapi.dto.ProgramCourseRequest;
import com.hcmute.lms.lmsapi.dto.ProgramCourseResponse;
import com.hcmute.lms.lmsapi.dto.ProgramCourseUpdateRequest;
import com.hcmute.lms.lmsapi.enums.CourseCategory;
import com.hcmute.lms.lmsapi.enums.CourseType;
import com.hcmute.lms.lmsapi.exception.AppException;
import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
import com.hcmute.lms.lmsapi.exception.ErrorCode;
import com.hcmute.lms.lmsapi.exception.ResourceNotFoundException;
import com.hcmute.lms.lmsapi.mapper.ProgramCourseMapper;
import com.hcmute.lms.lmsapi.repository.CourseRepository;
import com.hcmute.lms.lmsapi.repository.ProgramCourseRepository;
import com.hcmute.lms.lmsapi.repository.ProgramVersionRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProgramCourseServiceImpl Unit Tests (F03 / US-09)")
class ProgramCourseServiceImplTest {

  private static final String VERSION_ID = "671dcba5f123456789abc002";
  private static final String COURSE_ID = "671dcba5f123456789abc005";
  private static final String PREREQ_ID = "671dcba5f123456789abc006";
  private static final String PREV_ID = "671dcba5f123456789abc004";

  @Mock private ProgramCourseRepository programCourseRepository;
  @Mock private ProgramVersionRepository programVersionRepository;
  @Mock private CourseRepository courseRepository;
  @Mock private ProgramCourseMapper programCourseMapper;

  @InjectMocks private ProgramCourseServiceImpl programCourseService;

  private ProgramCourseRequest request(String courseId, List<String> prereqs) {
    return ProgramCourseRequest.builder()
        .courseId(courseId)
        .semester(2)
        .credits(3)
        .type(CourseType.COMPULSORY)
        .category(CourseCategory.MAJOR_FOUNDATION)
        .prerequisiteCourseIds(prereqs)
        .previousCourseIds(List.of())
        .build();
  }

  private ProgramCourseRequest request(
      String courseId, List<String> prereqs, List<String> previous) {
    return ProgramCourseRequest.builder()
        .courseId(courseId)
        .semester(2)
        .credits(3)
        .type(CourseType.COMPULSORY)
        .category(CourseCategory.MAJOR_FOUNDATION)
        .prerequisiteCourseIds(prereqs)
        .previousCourseIds(previous)
        .build();
  }

  @Test
  @DisplayName("US-09/AC-2: Gán học phần vào CTĐT thành công khi chưa trùng và tiên quyết hợp lệ")
  void addCourseToProgramVersion_success() {
    Course course = Course.builder().id(COURSE_ID).code("CS102").credits(3).build();
    when(programVersionRepository.existsById(VERSION_ID)).thenReturn(true);
    when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));
    when(programCourseRepository.existsByProgramVersionIdAndCourseId(VERSION_ID, COURSE_ID))
        .thenReturn(false);
    when(courseRepository.existsById(PREREQ_ID)).thenReturn(true);
    when(courseRepository.existsById(PREV_ID)).thenReturn(true);
    when(programCourseRepository.save(any(ProgramCourse.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(programCourseMapper.toResponse(any(ProgramCourse.class), any(Course.class)))
        .thenReturn(
            ProgramCourseResponse.builder()
                .programVersionId(VERSION_ID)
                .courseId(COURSE_ID)
                .semester(2)
                .type(CourseType.COMPULSORY)
                .category(CourseCategory.MAJOR_FOUNDATION)
                .previousCourseIds(List.of(PREV_ID))
                .prerequisiteCourseIds(List.of(PREREQ_ID))
                .build());

    ProgramCourseResponse response =
        programCourseService.addCourseToProgramVersion(
            VERSION_ID, request(COURSE_ID, List.of(PREREQ_ID), List.of(PREV_ID)));

    assertThat(response.getCourseId()).isEqualTo(COURSE_ID);
    assertThat(response.getPrerequisiteCourseIds()).containsExactly(PREREQ_ID);
    assertThat(response.getPreviousCourseIds()).containsExactly(PREV_ID);
    verify(programCourseRepository).save(any(ProgramCourse.class));
  }

  @Test
  @DisplayName("US-09/AC-2: Trùng cặp (programVersionId, courseId) ném HTTP 409 CONFLICT")
  void addCourseToProgramVersion_duplicate_throwsConflict() {
    Course course = Course.builder().id(COURSE_ID).code("CS102").build();
    when(programVersionRepository.existsById(VERSION_ID)).thenReturn(true);
    when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));
    when(programCourseRepository.existsByProgramVersionIdAndCourseId(VERSION_ID, COURSE_ID))
        .thenReturn(true);

    assertThatThrownBy(
            () ->
                programCourseService.addCourseToProgramVersion(
                    VERSION_ID, request(COURSE_ID, List.of())))
        .isInstanceOf(AppException.class)
        .satisfies(
            ex -> {
              AppException appEx = (AppException) ex;
              assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.CONFLICT);
            });

    verify(programCourseRepository, never()).save(any());
  }

  @Test
  @DisplayName("US-09/AC-3: prerequisiteCourseIds chứa chính nó ném HTTP 422")
  void addCourseToProgramVersion_selfPrerequisite_throwsException() {
    Course course = Course.builder().id(COURSE_ID).code("CS102").build();
    when(programVersionRepository.existsById(VERSION_ID)).thenReturn(true);
    when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));
    when(programCourseRepository.existsByProgramVersionIdAndCourseId(VERSION_ID, COURSE_ID))
        .thenReturn(false);

    assertThatThrownBy(
            () ->
                programCourseService.addCourseToProgramVersion(
                    VERSION_ID, request(COURSE_ID, List.of(COURSE_ID))))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessageContaining("PREREQUISITE_SELF_REFERENCE");

    verify(programCourseRepository, never()).save(any());
  }

  @Test
  @DisplayName("US-09/AC-3: prerequisiteCourseIds chứa học phần không tồn tại ném HTTP 422")
  void addCourseToProgramVersion_nonExistentPrerequisite_throwsException() {
    Course course = Course.builder().id(COURSE_ID).code("CS102").build();
    when(programVersionRepository.existsById(VERSION_ID)).thenReturn(true);
    when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));
    when(programCourseRepository.existsByProgramVersionIdAndCourseId(VERSION_ID, COURSE_ID))
        .thenReturn(false);
    when(courseRepository.existsById(PREREQ_ID)).thenReturn(false);

    assertThatThrownBy(
            () ->
                programCourseService.addCourseToProgramVersion(
                    VERSION_ID, request(COURSE_ID, List.of(PREREQ_ID))))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessageContaining("PREREQUISITE_NOT_FOUND");

    verify(programCourseRepository, never()).save(any());
  }

  @Test
  @DisplayName("US-09/AC-3: previousCourseIds chứa chính nó ném HTTP 422")
  void addCourseToProgramVersion_selfPreviousCourse_throwsException() {
    Course course = Course.builder().id(COURSE_ID).code("CS102").build();
    when(programVersionRepository.existsById(VERSION_ID)).thenReturn(true);
    when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));
    when(programCourseRepository.existsByProgramVersionIdAndCourseId(VERSION_ID, COURSE_ID))
        .thenReturn(false);

    assertThatThrownBy(
            () ->
                programCourseService.addCourseToProgramVersion(
                    VERSION_ID, request(COURSE_ID, List.of(), List.of(COURSE_ID))))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessageContaining("PREVIOUS_COURSE_SELF_REFERENCE");

    verify(programCourseRepository, never()).save(any());
  }

  @Test
  @DisplayName("US-09/AC-3: previousCourseIds chứa học phần không tồn tại ném HTTP 422")
  void addCourseToProgramVersion_nonExistentPreviousCourse_throwsException() {
    Course course = Course.builder().id(COURSE_ID).code("CS102").build();
    when(programVersionRepository.existsById(VERSION_ID)).thenReturn(true);
    when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));
    when(programCourseRepository.existsByProgramVersionIdAndCourseId(VERSION_ID, COURSE_ID))
        .thenReturn(false);
    when(courseRepository.existsById(PREV_ID)).thenReturn(false);

    assertThatThrownBy(
            () ->
                programCourseService.addCourseToProgramVersion(
                    VERSION_ID, request(COURSE_ID, List.of(), List.of(PREV_ID))))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessageContaining("PREVIOUS_COURSE_NOT_FOUND");

    verify(programCourseRepository, never()).save(any());
  }

  @Test
  @DisplayName("Học phần vừa là tiên quyết vừa là học trước ném HTTP 422")
  void addCourseToProgramVersion_prerequisiteAndPreviousConflict_throwsException() {
    Course course = Course.builder().id(COURSE_ID).code("CS102").build();
    when(programVersionRepository.existsById(VERSION_ID)).thenReturn(true);
    when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));
    when(programCourseRepository.existsByProgramVersionIdAndCourseId(VERSION_ID, COURSE_ID))
        .thenReturn(false);
    when(courseRepository.existsById(PREREQ_ID)).thenReturn(true);

    assertThatThrownBy(
            () ->
                programCourseService.addCourseToProgramVersion(
                    VERSION_ID, request(COURSE_ID, List.of(PREREQ_ID), List.of(PREREQ_ID))))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessageContaining("PREREQUISITE_AND_PREVIOUS_CONFLICT");

    verify(programCourseRepository, never()).save(any());
  }

  @Test
  @DisplayName("Gán học phần khi CTĐT không tồn tại ném HTTP 422")
  void addCourseToProgramVersion_versionNotFound_throwsException() {
    when(programVersionRepository.existsById(VERSION_ID)).thenReturn(false);

    assertThatThrownBy(
            () ->
                programCourseService.addCourseToProgramVersion(
                    VERSION_ID, request(COURSE_ID, List.of())))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessageContaining("PROGRAM_VERSION_NOT_FOUND");
  }

  @Test
  @DisplayName("Gán học phần khi học phần không tồn tại ném HTTP 422")
  void addCourseToProgramVersion_courseNotFound_throwsException() {
    when(programVersionRepository.existsById(VERSION_ID)).thenReturn(true);
    when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                programCourseService.addCourseToProgramVersion(
                    VERSION_ID, request(COURSE_ID, List.of())))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessageContaining("COURSE_NOT_FOUND");
  }

  @Test
  @DisplayName("Cập nhật thông tin gán học phần trong CTĐT thành công")
  void updateProgramCourse_success() {
    ProgramCourse pc =
        ProgramCourse.builder()
            .programVersionId(VERSION_ID)
            .courseId(COURSE_ID)
            .semester(1)
            .build();
    when(programVersionRepository.existsById(VERSION_ID)).thenReturn(true);
    when(programCourseRepository.findByProgramVersionIdAndCourseId(VERSION_ID, COURSE_ID))
        .thenReturn(Optional.of(pc));
    when(courseRepository.existsById(PREREQ_ID)).thenReturn(true);
    when(courseRepository.existsById(PREV_ID)).thenReturn(true);
    when(programCourseRepository.save(any(ProgramCourse.class))).thenReturn(pc);
    when(programCourseMapper.toResponse(any(), any()))
        .thenReturn(
            ProgramCourseResponse.builder()
                .programVersionId(VERSION_ID)
                .courseId(COURSE_ID)
                .semester(3)
                .build());

    ProgramCourseUpdateRequest updateReq =
        ProgramCourseUpdateRequest.builder()
            .semester(3)
            .credits(3)
            .type(CourseType.ELECTIVE)
            .previousCourseIds(List.of(PREV_ID))
            .prerequisiteCourseIds(List.of(PREREQ_ID))
            .build();

    ProgramCourseResponse response =
        programCourseService.updateProgramCourse(VERSION_ID, COURSE_ID, updateReq);
    assertThat(response.getSemester()).isEqualTo(3);
  }

  @Test
  @DisplayName("Xóa gán học phần khỏi CTĐT thành công")
  void removeCourseFromProgramVersion_success() {
    when(programVersionRepository.existsById(VERSION_ID)).thenReturn(true);
    when(programCourseRepository.existsByProgramVersionIdAndCourseId(VERSION_ID, COURSE_ID))
        .thenReturn(true);

    programCourseService.removeCourseFromProgramVersion(VERSION_ID, COURSE_ID);
    verify(programCourseRepository).deleteByProgramVersionIdAndCourseId(VERSION_ID, COURSE_ID);
  }

  @Test
  @DisplayName("Xóa gán học phần không tồn tại ném ResourceNotFoundException (404)")
  void removeCourseFromProgramVersion_notFound_throwsException() {
    when(programVersionRepository.existsById(VERSION_ID)).thenReturn(true);
    when(programCourseRepository.existsByProgramVersionIdAndCourseId(VERSION_ID, COURSE_ID))
        .thenReturn(false);

    assertThatThrownBy(
            () -> programCourseService.removeCourseFromProgramVersion(VERSION_ID, COURSE_ID))
        .isInstanceOf(ResourceNotFoundException.class);
  }
}
