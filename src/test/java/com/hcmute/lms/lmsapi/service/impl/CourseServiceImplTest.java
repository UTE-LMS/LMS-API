package com.hcmute.lms.lmsapi.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hcmute.lms.lmsapi.document.Course;
import com.hcmute.lms.lmsapi.dto.CourseRequest;
import com.hcmute.lms.lmsapi.dto.CourseResponse;
import com.hcmute.lms.lmsapi.dto.CourseUpdateRequest;
import com.hcmute.lms.lmsapi.dto.PageResponse;
import com.hcmute.lms.lmsapi.exception.AppException;
import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
import com.hcmute.lms.lmsapi.exception.ErrorCode;
import com.hcmute.lms.lmsapi.exception.ResourceNotFoundException;
import com.hcmute.lms.lmsapi.mapper.CourseMapper;
import com.hcmute.lms.lmsapi.repository.CoursePiContributionRepository;
import com.hcmute.lms.lmsapi.repository.CourseRepository;
import com.hcmute.lms.lmsapi.repository.ProgramCourseRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@DisplayName("CourseServiceImpl Unit Tests (F03 / US-09)")
class CourseServiceImplTest {

  private static final String COURSE_ID = "671dcba5f123456789abc005";

  @Mock private CourseRepository courseRepository;
  @Mock private ProgramCourseRepository programCourseRepository;
  @Mock private CoursePiContributionRepository coursePiContributionRepository;
  @Mock private CourseMapper courseMapper;

  @InjectMocks private CourseServiceImpl courseService;

  private CourseRequest request() {
    return CourseRequest.builder()
        .code(" CS101 ")
        .name(" Nhập môn lập trình ")
        .credits(3)
        .extra(Map.of("note", "DỮ LIỆU THỬ NGHIỆM"))
        .build();
  }

  @Test
  @DisplayName("US-09/AC-1: Tạo học phần với mã mới thành công, chuẩn hóa khoảng trắng")
  void createCourse_success() {
    when(courseRepository.existsByCode("CS101")).thenReturn(false);
    when(courseRepository.save(any(Course.class)))
        .thenAnswer(
            invocation -> {
              Course c = invocation.getArgument(0);
              c.setId(COURSE_ID);
              return c;
            });
    when(courseMapper.toResponse(any(Course.class)))
        .thenAnswer(
            invocation -> {
              Course c = invocation.getArgument(0);
              return CourseResponse.builder()
                  .id(c.getId())
                  .code(c.getCode())
                  .name(c.getName())
                  .credits(c.getCredits())
                  .extra(c.getExtra())
                  .build();
            });

    CourseResponse response = courseService.createCourse(request());

    assertThat(response.getId()).isEqualTo(COURSE_ID);
    assertThat(response.getCode()).isEqualTo("CS101");
    assertThat(response.getName()).isEqualTo("Nhập môn lập trình");
    assertThat(response.getCredits()).isEqualTo(3);
    verify(courseRepository).save(any(Course.class));
  }

  @Test
  @DisplayName("US-09/AC-1: Trùng mã học phần ném lỗi HTTP 409 CONFLICT")
  void createCourse_duplicateCode_throwsConflict() {
    when(courseRepository.existsByCode("CS101")).thenReturn(true);

    assertThatThrownBy(() -> courseService.createCourse(request()))
        .isInstanceOf(AppException.class)
        .satisfies(
            ex -> {
              AppException appEx = (AppException) ex;
              assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.CONFLICT);
            });

    verify(courseRepository, never()).save(any(Course.class));
  }

  @Test
  @DisplayName("Lấy chi tiết học phần theo ID thành công")
  void getCourseById_success() {
    Course course = Course.builder().id(COURSE_ID).code("CS101").name("Nhập môn").build();
    when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));
    when(courseMapper.toResponse(course))
        .thenReturn(CourseResponse.builder().id(COURSE_ID).code("CS101").name("Nhập môn").build());

    CourseResponse response = courseService.getCourseById(COURSE_ID);
    assertThat(response.getCode()).isEqualTo("CS101");
  }

  @Test
  @DisplayName("Lấy chi tiết học phần không tồn tại ném lỗi ResourceNotFoundException (404)")
  void getCourseById_notFound_throwsException() {
    when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> courseService.getCourseById(COURSE_ID))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  @DisplayName("Cập nhật học phần thành công")
  void updateCourse_success() {
    Course course = Course.builder().id(COURSE_ID).code("CS101").name("Cũ").credits(2).build();
    when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));
    when(courseRepository.save(any(Course.class))).thenReturn(course);
    when(courseMapper.toResponse(any(Course.class)))
        .thenReturn(
            CourseResponse.builder().id(COURSE_ID).code("CS101").name("Mới").credits(4).build());

    CourseUpdateRequest updateReq = CourseUpdateRequest.builder().name(" Mới ").credits(4).build();
    CourseResponse response = courseService.updateCourse(COURSE_ID, updateReq);

    assertThat(response.getName()).isEqualTo("Mới");
    assertThat(response.getCredits()).isEqualTo(4);
  }

  @Test
  @DisplayName("Xóa học phần thành công khi không có tham chiếu")
  void deleteCourse_success() {
    when(courseRepository.existsById(COURSE_ID)).thenReturn(true);
    when(programCourseRepository.existsByCourseId(COURSE_ID)).thenReturn(false);
    when(coursePiContributionRepository.existsByCourseId(COURSE_ID)).thenReturn(false);

    courseService.deleteCourse(COURSE_ID);
    verify(courseRepository).deleteById(COURSE_ID);
  }

  @Test
  @DisplayName("Xóa học phần thất bại (422) khi đang được dùng trong CTĐT")
  void deleteCourse_inUseByProgramCourse_throwsException() {
    when(courseRepository.existsById(COURSE_ID)).thenReturn(true);
    when(programCourseRepository.existsByCourseId(COURSE_ID)).thenReturn(true);

    assertThatThrownBy(() -> courseService.deleteCourse(COURSE_ID))
        .isInstanceOf(BusinessRuleViolationException.class);
    verify(courseRepository, never()).deleteById(any());
  }

  @Test
  @DisplayName("Danh sách học phần phân trang và tìm kiếm")
  void getCourses_withSearch_returnsPagedResult() {
    Course course = Course.builder().id(COURSE_ID).code("CS101").name("Nhập môn").build();
    Page<Course> page = new PageImpl<>(List.of(course));
    when(courseRepository.findByNameContainingIgnoreCaseOrCodeContainingIgnoreCase(
            any(), any(), any(Pageable.class)))
        .thenReturn(page);
    when(courseMapper.toResponseList(List.of(course)))
        .thenReturn(List.of(CourseResponse.builder().id(COURSE_ID).code("CS101").build()));

    PageResponse<CourseResponse> response = courseService.getCourses(0, 10, "CS101");
    assertThat(response.getContent()).hasSize(1);
    assertThat(response.getContent().get(0).getCode()).isEqualTo("CS101");
  }
}
