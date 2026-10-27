package com.hcmute.lms.lmsapi.dto;

import com.hcmute.lms.lmsapi.validator.Utf8ByteLength;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO yêu cầu đăng nhập hệ thống (POST /api/v1/auth/login).
 * Tuân thủ ràng buộc API-CONTRACT §5.2.1 và chính sách mật khẩu 8-72 bytes UTF-8 (ADR 0003).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    @NotBlank(message = "Tên đăng nhập không được để trống")
    @Size(min = 3, message = "Tên đăng nhập phải có ít nhất 3 ký tự")
    private String username;

    @NotBlank(message = "Mật khẩu không được để trống")
    @Utf8ByteLength(min = 8, max = 72, message = "Độ dài mật khẩu phải từ 8 đến 72 bytes UTF-8")
    private String password;
}
