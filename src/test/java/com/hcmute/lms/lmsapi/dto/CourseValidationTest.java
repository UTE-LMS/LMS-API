package com.hcmute.lms.lmsapi.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.hcmute.lms.lmsapi.enums.CourseCategory;
import com.hcmute.lms.lmsapi.enums.CourseType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("F03 / US-09: DTO Bean Validation Unit Tests")
class CourseValidationTest {

  private static Validator validator;

  @BeforeAll
  static void setUp() {
    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @Test
  @DisplayName("CourseRequest hợp lệ")
  void validCourseRequest() {
    CourseRequest request =
        CourseRequest.builder()
            .code("CS101")
            .name("Nhập môn lập trình (DỮ LIỆU THỬ NGHIỆM)")
            .credits(3)
            .build();

    Set<ConstraintViolation<CourseRequest>> violations = validator.validate(request);
    assertThat(violations).isEmpty();
  }

  @Test
  @DisplayName("CourseRequest mã học phần rỗng hoặc chứa ký tự đặc biệt không hợp lệ")
  void invalidCourseRequest_code() {
    CourseRequest blankCode =
        CourseRequest.builder().code("   ").name("Nhập môn").credits(3).build();
    assertThat(validator.validate(blankCode)).isNotEmpty();

    CourseRequest invalidChar =
        CourseRequest.builder().code("CS@101#").name("Nhập môn").credits(3).build();
    assertThat(validator.validate(invalidChar)).isNotEmpty();
  }

  @Test
  @DisplayName("CourseRequest số tín chỉ âm bị từ chối")
  void invalidCourseRequest_negativeCredits() {
    CourseRequest negativeCredits =
        CourseRequest.builder().code("CS101").name("Nhập môn").credits(-1).build();
    assertThat(validator.validate(negativeCredits)).isNotEmpty();
  }

  @Test
  @DisplayName("ProgramCourseRequest hợp lệ")
  void validProgramCourseRequest() {
    ProgramCourseRequest request =
        ProgramCourseRequest.builder()
            .courseId("671dcba5f123456789abc001")
            .semester(1)
            .credits(3)
            .type(CourseType.COMPULSORY)
            .category(CourseCategory.FOUNDATION)
            .prerequisiteCourseIds(List.of("671dcba5f123456789abc002"))
            .previousCourseIds(List.of("671dcba5f123456789abc003"))
            .build();

    Set<ConstraintViolation<ProgramCourseRequest>> violations = validator.validate(request);
    assertThat(violations).isEmpty();
  }

  @Test
  @DisplayName("ProgramCourseRequest ObjectId sai định dạng hoặc type null")
  void invalidProgramCourseRequest() {
    ProgramCourseRequest invalidObjectId =
        ProgramCourseRequest.builder()
            .courseId("invalid-hex-id")
            .semester(1)
            .credits(3)
            .type(CourseType.COMPULSORY)
            .build();
    assertThat(validator.validate(invalidObjectId)).isNotEmpty();

    ProgramCourseRequest nullType =
        ProgramCourseRequest.builder()
            .courseId("671dcba5f123456789abc001")
            .semester(1)
            .credits(3)
            .type(null)
            .build();
    assertThat(validator.validate(nullType)).isNotEmpty();

    ProgramCourseRequest invalidSemester =
        ProgramCourseRequest.builder()
            .courseId("671dcba5f123456789abc001")
            .semester(0)
            .type(CourseType.COMPULSORY)
            .build();
    assertThat(validator.validate(invalidSemester)).isNotEmpty();

    ProgramCourseRequest invalidPreviousId =
        ProgramCourseRequest.builder()
            .courseId("671dcba5f123456789abc001")
            .semester(1)
            .type(CourseType.COMPULSORY)
            .previousCourseIds(List.of("invalid-hex-id"))
            .build();
    assertThat(validator.validate(invalidPreviousId)).isNotEmpty();
  }
}
