package com.hcmute.lms.lmsapi.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CloMeasurementDto {

  @DecimalMin(value = "0.0", message = "defaultWeight phải nằm trong đoạn [0, 1]")
  @DecimalMax(value = "1.0", message = "defaultWeight phải nằm trong đoạn [0, 1]")
  private Double defaultWeight;

  @DecimalMin(value = "0.0", message = "defaultPassThreshold phải nằm trong đoạn [0, 1]")
  @DecimalMax(value = "1.0", message = "defaultPassThreshold phải nằm trong đoạn [0, 1]")
  private Double defaultPassThreshold;
}
