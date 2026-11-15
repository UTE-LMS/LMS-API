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
public class ProgramRequest {

  @Pattern(regexp = "^[0-9a-fA-F]{24}$", message = "ID Khoa phải là ObjectId 24 ký tự hex")
  private String facultyId;

  @NotBlank(message = "Mã chương trình đào tạo không được để trống")
  @Size(max = 64, message = "Mã chương trình đào tạo tối đa 64 ký tự")
  private String code;

  @NotBlank(message = "Tên chương trình đào tạo không được để trống")
  @Size(max = 255, message = "Tên chương trình đào tạo tối đa 255 ký tự")
  private String name;

  @Size(max = 100, message = "Trình độ tối đa 100 ký tự")
  private String level;

  @Size(max = 100, message = "Bằng cấp tối đa 100 ký tự")
  private String degree;
}
