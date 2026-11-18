package com.hcmute.lms.lmsapi.service;

/**
 * Xác minh ID token (JWT) do Google Identity Services cấp cho giao diện (ADR 0016). Tách thành
 * interface để service đăng nhập kiểm thử được mà không gọi Google.
 */
public interface GoogleIdTokenVerifier {

  /** Danh tính đã được Google xác nhận trong ID token. */
  record GoogleIdentity(String subject, String email, String name) {}

  /** Đã cấu hình Google client ID chưa; chưa cấu hình thì không đăng nhập Google được. */
  boolean isConfigured();

  /**
   * Kiểm tra chữ ký (khóa công khai của Google), thời hạn, nơi phát hành, đối tượng nhận (đúng
   * client ID của hệ thống) và email đã được Google xác minh.
   *
   * @throws org.springframework.security.authentication.BadCredentialsException nếu token không hợp
   *     lệ
   * @throws com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException nếu chưa cấu hình
   */
  GoogleIdentity verify(String idToken);
}
