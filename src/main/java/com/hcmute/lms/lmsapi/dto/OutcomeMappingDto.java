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

/** CLO mà một phần tử đo và phần điểm chia cho CLO đó (ADR 0011). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutcomeMappingDto {

  @NotBlank(message = "ID CLO không được để trống")
  @Pattern(regexp = "^[0-9a-fA-F]{24}$", message = "ID CLO phải là ObjectId 24 ký tự hex")
  private String cloId;

  @NotNull(message = "Phần điểm (weight) không được để trống")
  @DecimalMin(value = "0", inclusive = false, message = "Phần điểm phải lớn hơn 0")
  @DecimalMax(value = "1", message = "Phần điểm không được lớn hơn 1")
  private Double weight;
}
