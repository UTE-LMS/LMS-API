package com.hcmute.lms.lmsapi.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
public class CourseUpdateRequest {

  @NotBlank(message = "Tên học phần không được để trống")
  @Size(max = 255, message = "Tên học phần tối đa 255 ký tự")
  private String name;

  @Min(value = 0, message = "Số tín chỉ phải không âm")
  private Integer credits;

  private Map<String, Object> extra;
}
