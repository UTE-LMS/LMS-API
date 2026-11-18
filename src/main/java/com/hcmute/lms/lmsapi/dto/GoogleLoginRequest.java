package com.hcmute.lms.lmsapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO đăng nhập bằng Google (POST /api/v1/auth/google): ID token do Google Identity Services cấp.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoogleLoginRequest {

  @NotBlank(message = "Thiếu mã đăng nhập Google (idToken)")
  @Size(max = 8192, message = "Mã đăng nhập Google quá dài")
  private String idToken;
}
