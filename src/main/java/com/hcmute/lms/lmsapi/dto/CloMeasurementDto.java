package com.hcmute.lms.lmsapi.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CloMeasurementDto {

  @NotNull(message = "defaultPassThreshold không được để trống")
  @DecimalMin(value = "0.0", inclusive = false, message = "defaultPassThreshold phải lớn hơn 0")
  @DecimalMax(value = "1.0", message = "defaultPassThreshold không được vượt quá 1")
  private Double defaultPassThreshold;
}
