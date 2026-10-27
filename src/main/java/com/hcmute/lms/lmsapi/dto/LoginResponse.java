package com.hcmute.lms.lmsapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO phản hồi đăng nhập thành công (HTTP 200 OK) tại POST /api/v1/auth/login.
 * Cung cấp JWT access token và thông tin hồ sơ người dùng (API-CONTRACT §5.2.1).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    /**
     * JWT Access Token ký bằng Nimbus (HS256).
     */
    private String accessToken;

    /**
     * Loại token (mặc định "Bearer").
     */
    @Builder.Default
    private String tokenType = "Bearer";

    /**
     * Thời gian sống của token tính bằng giây (ví dụ: 3600 cho 60 phút).
     */
    private long expiresIn;

    /**
     * Thông tin hồ sơ và phân quyền của người dùng vừa đăng nhập.
     */
    private UserProfileResponse user;
}
