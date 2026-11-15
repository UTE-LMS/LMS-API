package com.hcmute.lms.lmsapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FacultyRequest {

  @NotBlank(message = "Mã khoa không được để trống")
  @Pattern(
      regexp = "^[A-Z0-9_-]{2,20}$",
      message = "Mã khoa phải gồm 2-20 ký tự chữ in hoa, số, gạch dưới hoặc gạch ngang")
  private String code;

  @NotBlank(message = "Tên khoa không được để trống")
  @Size(min = 3, max = 255, message = "Tên khoa phải từ 3 đến 255 ký tự")
  private String name;

  @Size(max = 1000, message = "Mô tả tối đa 1000 ký tự")
  private String description;
}
