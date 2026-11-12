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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CloPiLinkRequest {

  @NotBlank(message = "programVersionId không được để trống")
  @Pattern(regexp = "^[0-9a-fA-F]{24}$", message = "programVersionId phải là ObjectId 24 ký tự hex")
  private String programVersionId;

  @NotBlank(message = "cloId không được để trống")
  @Pattern(regexp = "^[0-9a-fA-F]{24}$", message = "cloId phải là ObjectId 24 ký tự hex")
  private String cloId;

  @NotBlank(message = "piId không được để trống")
  @Pattern(regexp = "^[0-9a-fA-F]{24}$", message = "piId phải là ObjectId 24 ký tự hex")
  private String piId;

  @NotNull(message = "weight không được để trống")
  @DecimalMin(
      value = "0.0",
      inclusive = false,
      message = "weight phải lớn hơn 0 và nhỏ hơn hoặc bằng 1")
  @DecimalMax(
      value = "1.0",
      inclusive = true,
      message = "weight phải lớn hơn 0 và nhỏ hơn hoặc bằng 1")
  private Double weight;
}
