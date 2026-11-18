package com.hcmute.lms.lmsapi.dto;

import com.hcmute.lms.lmsapi.validator.Utf8ByteLength;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO yêu cầu đổi mật khẩu của chính người dùng (POST /api/v1/auth/change-password). Mật khẩu mới
 * theo chính sách 8-72 bytes UTF-8 như lúc tạo tài khoản (ADR 0003).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangePasswordRequest {

  @NotBlank(message = "Mật khẩu hiện tại không được để trống")
  private String currentPassword;

  @NotBlank(message = "Mật khẩu mới không được để trống")
  @Utf8ByteLength(min = 8, max = 72, message = "Độ dài mật khẩu phải từ 8 đến 72 bytes UTF-8")
  private String newPassword;
}
