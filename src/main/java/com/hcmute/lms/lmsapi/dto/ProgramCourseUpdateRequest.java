package com.hcmute.lms.lmsapi.dto;

import com.hcmute.lms.lmsapi.enums.CourseCategory;
import com.hcmute.lms.lmsapi.enums.CourseType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProgramCourseUpdateRequest {

  @Min(value = 1, message = "Học kỳ dự kiến phải lớn hơn hoặc bằng 1")
  private Integer semester;

  @Min(value = 0, message = "Số tín chỉ phải không âm")
  private Integer credits;

  @NotNull(message = "Loại học phần bắt buộc (COMPULSORY hoặc ELECTIVE)")
  private CourseType type;

  private CourseCategory category;

  @Builder.Default
  private List<
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "ID học phần tiên quyết phải là ObjectId 24 ký tự hex")
          String>
      prerequisiteCourseIds = new ArrayList<>();
}
