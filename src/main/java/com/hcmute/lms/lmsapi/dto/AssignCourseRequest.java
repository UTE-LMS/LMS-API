package com.hcmute.lms.lmsapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignCourseRequest {

  @NotBlank(message = "ID học phần không được để trống")
  @Pattern(regexp = "^[0-9a-fA-F]{24}$", message = "ID học phần phải là ObjectId 24 ký tự hex")
  private String courseId;

  @NotBlank(message = "ID giảng viên không được để trống")
  @Pattern(regexp = "^[0-9a-fA-F]{24}$", message = "ID giảng viên phải là ObjectId 24 ký tự hex")
  private String lecturerId;
}
