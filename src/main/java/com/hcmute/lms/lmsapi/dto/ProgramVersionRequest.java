package com.hcmute.lms.lmsapi.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProgramVersionRequest {

  @NotBlank(message = "ID chương trình đào tạo không được để trống")
  @Pattern(
      regexp = "^[0-9a-fA-F]{24}$",
      message = "ID chương trình đào tạo phải là ObjectId 24 ký tự hex")
  private String programId;

  @NotBlank(message = "Phiên bản chương trình đào tạo không được để trống")
  @Size(max = 100, message = "Phiên bản tối đa 100 ký tự")
  private String version;

  @PositiveOrZero(message = "Tổng số tín chỉ không được âm")
  private Integer totalCredits;

  @DecimalMin(value = "0.0", message = "Thời gian đào tạo không được âm")
  private Double durationYears;

  @Size(max = 50, message = "Khóa áp dụng tối đa 50 ký tự")
  private String applicableCohort;
}
