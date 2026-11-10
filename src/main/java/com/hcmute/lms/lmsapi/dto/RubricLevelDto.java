package com.hcmute.lms.lmsapi.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Một mức rubric. {@code level} là mức rubric 1-4, không phải mức Bloom (US-16/AC-2). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RubricLevelDto {

  @NotNull(message = "Mức rubric không được để trống")
  @Min(value = 1, message = "Mức rubric phải từ 1 đến 4")
  @Max(value = 4, message = "Mức rubric phải từ 1 đến 4")
  private Integer level;

  @Size(max = 500, message = "Mô tả mức rubric dài tối đa 500 ký tự")
  private String description;

  @NotNull(message = "Điểm của mức rubric không được để trống")
  @PositiveOrZero(message = "Điểm của mức rubric không được âm")
  private Double score;
}
