package com.hcmute.lms.lmsapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Yêu cầu mở lớp học phần (US-19). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SectionRequest {

  @NotBlank(message = "Mã lớp học phần không được để trống")
  @Pattern(
      regexp = "^[A-Za-z0-9._-]{2,40}$",
      message =
          "Mã lớp học phần chỉ được chứa chữ cái, số, dấu chấm, gạch ngang, gạch dưới và dài từ 2 đến 40 ký tự")
  private String code;

  @NotBlank(message = "ID học phần không được để trống")
  @Pattern(regexp = "^[0-9a-fA-F]{24}$", message = "ID học phần phải là ObjectId 24 ký tự hex")
  private String courseId;

  @NotBlank(message = "ID đề cương không được để trống")
  @Pattern(regexp = "^[0-9a-fA-F]{24}$", message = "ID đề cương phải là ObjectId 24 ký tự hex")
  private String syllabusId;

  @NotBlank(message = "ID học kỳ không được để trống")
  @Pattern(regexp = "^[0-9a-fA-F]{24}$", message = "ID học kỳ phải là ObjectId 24 ký tự hex")
  private String termId;

  @NotEmpty(message = "Lớp học phần phải có ít nhất một giảng viên phụ trách")
  private List<
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "ID giảng viên phải là ObjectId 24 ký tự hex")
          String>
      lecturerIds;
}
