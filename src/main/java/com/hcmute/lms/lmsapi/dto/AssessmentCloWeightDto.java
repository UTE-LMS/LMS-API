package com.hcmute.lms.lmsapi.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO tỷ lệ % đóng góp W_{p,j} của bài kiểm tra cho CLO. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentCloWeightDto {

  @NotBlank(message = "ID CLO không được để trống")
  @Pattern(regexp = "^[0-9a-fA-F]{24}$", message = "ID CLO phải là ObjectId 24 ký tự hex")
  private String cloId;

  @NotNull(message = "Trọng số không được để trống")
  @DecimalMin(value = "0.0", inclusive = false, message = "Trọng số phải lớn hơn 0")
  @DecimalMax(value = "1.0", message = "Trọng số không được vượt quá 1")
  private Double weight;
}
