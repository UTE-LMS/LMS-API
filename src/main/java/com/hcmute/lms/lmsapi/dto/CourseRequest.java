package com.hcmute.lms.lmsapi.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseRequest {

  @NotBlank(message = "Mã học phần không được để trống")
  @Size(max = 64, message = "Mã học phần tối đa 64 ký tự")
  @Pattern(
      regexp = "^[A-Za-z0-9_.-]+$",
      message = "Mã học phần chỉ được chứa chữ cái, chữ số, dấu gạch dưới, gạch ngang và dấu chấm")
  private String code;

  @NotBlank(message = "Tên học phần không được để trống")
  @Size(max = 255, message = "Tên học phần tối đa 255 ký tự")
  private String name;

  @Min(value = 0, message = "Số tín chỉ phải không âm")
  private Integer credits;

  private Map<String, Object> extra;
}
