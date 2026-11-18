package com.hcmute.lms.lmsapi.controller;

import com.hcmute.lms.lmsapi.dto.ChangePasswordRequest;
import com.hcmute.lms.lmsapi.dto.LoginRequest;
import com.hcmute.lms.lmsapi.dto.LoginResponse;
import com.hcmute.lms.lmsapi.dto.UpdateProfileRequest;
import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import com.hcmute.lms.lmsapi.dto.UserProfileResponse;
import com.hcmute.lms.lmsapi.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller xử lý các yêu cầu xác thực người dùng (F01 / US-01 / ADR 0003). Tiền tố chuẩn:
 * /api/v1/auth theo hợp đồng giao tiếp liên repo API-CONTRACT §5.2.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;

  /** Đăng nhập hệ thống (POST /api/v1/auth/login). Endpoint công khai (permitAll). */
  @PostMapping("/login")
  public ResponseEntity<LoginResponse> login(
      @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
    log.info("Nhận yêu cầu đăng nhập cho username: {}", request.getUsername());
    LoginResponse response = authService.login(request, httpRequest);
    return ResponseEntity.ok(response);
  }

  /** Đăng xuất hệ thống (POST /api/v1/auth/logout). Yêu cầu Bearer token hợp lệ. */
  @PostMapping("/logout")
  public ResponseEntity<Map<String, String>> logout(
      @AuthenticationPrincipal UserPrincipal principal) {
    log.info("Nhận yêu cầu đăng xuất từ user id={}", principal.getId());
    authService.logout(principal.getId());
    return ResponseEntity.ok(Map.of("message", "Đăng xuất thành công"));
  }

  /**
   * Đổi mật khẩu của chính mình (POST /api/v1/auth/change-password). Là endpoint duy nhất ngoài /me
   * và /logout mà tài khoản còn cờ mustChangePassword được gọi.
   */
  @PostMapping("/change-password")
  public ResponseEntity<LoginResponse> changePassword(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody ChangePasswordRequest request,
      HttpServletRequest httpRequest) {
    log.info("Nhận yêu cầu đổi mật khẩu từ user id={}", principal.getId());
    return ResponseEntity.ok(authService.changePassword(principal, request, httpRequest));
  }

  /**
   * Lấy thông tin tài khoản cá nhân của phiên hiện tại (GET /api/v1/auth/me). Yêu cầu Bearer token
   * hợp lệ.
   */
  @GetMapping("/me")
  public ResponseEntity<UserProfileResponse> me(@AuthenticationPrincipal UserPrincipal principal) {
    log.info("Nhận yêu cầu lấy thông tin cá nhân từ user id={}", principal.getId());
    UserProfileResponse response = authService.getCurrentUserProfile(principal);
    return ResponseEntity.ok(response);
  }

  /** Người dùng tự sửa họ tên của mình (PUT /api/v1/auth/me). Yêu cầu Bearer token hợp lệ. */
  @PutMapping("/me")
  public ResponseEntity<UserProfileResponse> updateMe(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody UpdateProfileRequest request,
      HttpServletRequest httpRequest) {
    log.info("Nhận yêu cầu sửa hồ sơ cá nhân từ user id={}", principal.getId());
    return ResponseEntity.ok(authService.updateProfile(principal, request, httpRequest));
  }
}
