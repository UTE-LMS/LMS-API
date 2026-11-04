package com.hcmute.lms.lmsapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TermRequest {

  @NotBlank(message = "Mã học kỳ không được để trống")
  @Pattern(
      regexp = "^[A-Za-z0-9_-]{2,30}$",
      message =
          "Mã học kỳ chỉ được chứa chữ cái, số, gạch ngang, gạch dưới và dài từ 2 đến 30 ký tự")
  private String code;

  private String academicYear;

  private Instant startDate;

  private Instant endDate;
}
