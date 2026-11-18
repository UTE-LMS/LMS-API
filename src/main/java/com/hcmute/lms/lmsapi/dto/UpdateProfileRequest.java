package com.hcmute.lms.lmsapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO người dùng tự sửa hồ sơ của mình (PUT /api/v1/auth/me). Cố ý chỉ có họ tên: email dùng để
 * định danh/liên kết tài khoản, còn vai trò, khoa và trạng thái do Admin quản lý ở /users.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {

  @NotBlank(message = "Họ và tên không được để trống")
  @Size(max = 100, message = "Họ và tên tối đa 100 ký tự")
  private String fullName;
}
