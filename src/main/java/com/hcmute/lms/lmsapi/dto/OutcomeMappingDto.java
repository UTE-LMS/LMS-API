package com.hcmute.lms.lmsapi.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** CLO mà câu hỏi đo và điểm tối đa dành cho CLO đó (ADR 0014, v0.7). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutcomeMappingDto {

  @NotBlank(message = "ID CLO không được để trống")
  @Pattern(regexp = "^[0-9a-fA-F]{24}$", message = "ID CLO phải là ObjectId 24 ký tự hex")
  private String cloId;

  @NotNull(message = "Điểm tối đa (maxScore) không được để trống")
  @DecimalMin(value = "0.0", inclusive = false, message = "Điểm tối đa phải lớn hơn 0")
  private Double maxScore;
}
