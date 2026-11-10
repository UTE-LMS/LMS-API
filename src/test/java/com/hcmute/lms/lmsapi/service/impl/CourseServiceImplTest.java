package com.hcmute.lms.lmsapi.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hcmute.lms.lmsapi.document.Course;
import com.hcmute.lms.lmsapi.dto.CourseImportResultResponse;
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
import java.nio.charset.StandardCharsets;
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
import org.springframework.mock.web.MockMultipartFile;

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

  @Test
  @DisplayName("Import CSV: Tệp rỗng hoặc null ném lỗi VALIDATION_ERROR")
  void importCourses_nullOrEmptyFile_throwsValidationException() {
    MockMultipartFile emptyFile =
        new MockMultipartFile("file", "courses.csv", "text/csv", new byte[0]);

    assertThatThrownBy(() -> courseService.importCourses(emptyFile))
        .isInstanceOf(AppException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);

    assertThatThrownBy(() -> courseService.importCourses(null))
        .isInstanceOf(AppException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);
  }

  @Test
  @DisplayName("Import CSV: Định dạng file không phải .csv ném lỗi VALIDATION_ERROR")
  void importCourses_invalidExtension_throwsValidationException() {
    MockMultipartFile excelFile =
        new MockMultipartFile(
            "file", "courses.xlsx", "application/vnd.ms-excel", "test".getBytes());

    assertThatThrownBy(() -> courseService.importCourses(excelFile))
        .isInstanceOf(AppException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);
  }

  @Test
  @DisplayName("Import CSV: Thiếu cột bắt buộc (code, name, credits) ném lỗi VALIDATION_ERROR")
  void importCourses_missingHeaders_throwsValidationException() {
    String csv = "ma_mon,ten_mon\nCS101,Lap trinh";
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "courses.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

    assertThatThrownBy(() -> courseService.importCourses(file))
        .isInstanceOf(AppException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR)
        .hasMessageContaining("thiếu các cột bắt buộc");
  }

  @Test
  @DisplayName("Import CSV: Tệp chỉ có header không có dữ liệu ném lỗi VALIDATION_ERROR")
  void importCourses_noDataRows_throwsValidationException() {
    String csv = "code,name,credits\n";
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "courses.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

    assertThatThrownBy(() -> courseService.importCourses(file))
        .isInstanceOf(AppException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR)
        .hasMessageContaining("không có dữ liệu học phần");
  }

  @Test
  @DisplayName(
      "Import CSV: Xử lý cơ chế Best effort vừa lưu dòng hợp lệ vừa báo lỗi chi tiết các dòng sai")
  void importCourses_bestEffort_processesValidAndReportsErrors() {
    String csv =
        "code,name,credits\n"
            + "MATH101,Toán rời rạc,3\n" // Hợp lệ -> Thành công (row 2)
            + "MISSING_COLS\n" // Thiếu cột -> Lỗi (row 3)
            + "INVALID@CODE,Tin học cơ bản,2\n" // Sai regex code -> Lỗi (row 4)
            + "CS102,,3\n" // Tên trống -> Lỗi (row 5)
            + "CS103,Mạng máy tính,-1\n" // Tín chỉ âm -> Lỗi (row 6)
            + "MATH101,Toán rời rạc trùng file,3\n" // Trùng trong file -> Lỗi (row 7)
            + "EXISTING101,Triết học Mác Lê-nin,3\n"; // Trùng trong CSDL -> Lỗi (row 8)

    MockMultipartFile file =
        new MockMultipartFile(
            "file", "courses.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

    when(courseRepository.existsByCode("MATH101")).thenReturn(false);
    when(courseRepository.existsByCode("EXISTING101")).thenReturn(true);
    when(courseRepository.save(any(Course.class)))
        .thenAnswer(inv -> inv.getArgument(0, Course.class));

    CourseImportResultResponse result = courseService.importCourses(file);

    assertThat(result.getTotalRows()).isEqualTo(7);
    assertThat(result.getSuccessCount()).isEqualTo(1);
    assertThat(result.getFailureCount()).isEqualTo(6);
    assertThat(result.getErrors()).hasSize(6);

    // Kiểm chứng chi tiết các dòng lỗi
    assertThat(result.getErrors().get(0).getRow()).isEqualTo(3);
    assertThat(result.getErrors().get(0).getMessage()).contains("thiếu cột");

    assertThat(result.getErrors().get(1).getRow()).isEqualTo(4);
    assertThat(result.getErrors().get(1).getMessage()).contains("Mã học phần chỉ được chứa");

    assertThat(result.getErrors().get(2).getRow()).isEqualTo(5);
    assertThat(result.getErrors().get(2).getMessage()).contains("Tên học phần không được để trống");

    assertThat(result.getErrors().get(3).getRow()).isEqualTo(6);
    assertThat(result.getErrors().get(3).getMessage()).contains("Số tín chỉ phải không âm");

    assertThat(result.getErrors().get(4).getRow()).isEqualTo(7);
    assertThat(result.getErrors().get(4).getMessage()).contains("bị trùng lặp trong tệp CSV");

    assertThat(result.getErrors().get(5).getRow()).isEqualTo(8);
    assertThat(result.getErrors().get(5).getMessage()).contains("đã tồn tại trong hệ thống");

    // Xác nhận đã lưu 1 môn thành công
    verify(courseRepository).save(any(Course.class));
  }
}
