package com.hcmute.lms.lmsapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FacultyUpdateRequest {

  @NotBlank(message = "Tên khoa không được để trống")
  @Size(min = 3, max = 255, message = "Tên khoa phải từ 3 đến 255 ký tự")
  private String name;

  @Size(max = 1000, message = "Mô tả tối đa 1000 ký tự")
  private String description;

  private Boolean isActive;
}
