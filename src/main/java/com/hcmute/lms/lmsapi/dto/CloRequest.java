package com.hcmute.lms.lmsapi.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CloRequest {

  @NotBlank(message = "Mã CLO không được để trống")
  @Pattern(
      regexp = "^CLO[0-9]+$",
      message = "Mã CLO phải có định dạng ^CLO[0-9]+$ (ví dụ: CLO1, CLO2)")
  private String code;

  private String contentOriginal;

  @NotNull(message = "Mức độ Bloom không được để trống")
  @Min(value = 1, message = "bloomLevel phải từ 1 đến 6")
  @Max(value = 6, message = "bloomLevel phải từ 1 đến 6")
  private Integer bloomLevel;

  private String bloomNote;

  @Valid private CloMeasurementDto measurement;

  private Map<String, Object> extra;
}
