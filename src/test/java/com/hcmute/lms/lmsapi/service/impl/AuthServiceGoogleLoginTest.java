package com.hcmute.lms.lmsapi.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hcmute.lms.lmsapi.config.JwtConfig;
import com.hcmute.lms.lmsapi.document.User;
import com.hcmute.lms.lmsapi.dto.GoogleLoginRequest;
import com.hcmute.lms.lmsapi.dto.LoginResponse;
import com.hcmute.lms.lmsapi.enums.AuditAction;
import com.hcmute.lms.lmsapi.enums.Role;
import com.hcmute.lms.lmsapi.enums.UserStatus;
import com.hcmute.lms.lmsapi.repository.UserRepository;
import com.hcmute.lms.lmsapi.service.AuditService;
import com.hcmute.lms.lmsapi.service.GoogleIdTokenVerifier;
import com.hcmute.lms.lmsapi.service.GoogleIdTokenVerifier.GoogleIdentity;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

/** Unit test AuthServiceImpl.loginWithGoogle (ADR 0016). Dữ liệu giả, không gọi Google. */
@ExtendWith(MockitoExtension.class)
@DisplayName("ADR 0016: AuthServiceImpl.loginWithGoogle")
class AuthServiceGoogleLoginTest {

  private static final String USER_ID = "64b000000000000000000001";

  @Mock private UserRepository userRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private JwtEncoder jwtEncoder;
  @Mock private JwtConfig jwtConfig;
  @Mock private AuditService auditService;
  @Mock private GoogleIdTokenVerifier googleVerifier;
  @Mock private HttpServletRequest httpRequest;

  @InjectMocks private AuthServiceImpl authService;

  private User user;

  private static GoogleLoginRequest request() {
    return GoogleLoginRequest.builder().idToken("id-token-gia").build();
  }

  @BeforeEach
  void setUp() {
    user =
        User.builder()
            .id(USER_ID)
            .username("gv_test")
            .email("gv_test@example.invalid")
            .passwordHash("hash")
            .fullName("[DỮ LIỆU THỬ NGHIỆM] Giảng viên")
            .status(UserStatus.ACTIVE)
            .tokenVersion(2L)
            .roles(List.of(Role.LECTURER))
            .build();
  }

  private void stubTokenIssuing() {
    when(jwtConfig.getTtl()).thenReturn(Duration.ofMinutes(60));
    when(jwtConfig.getIssuer()).thenReturn("lms-api");
    Jwt jwt =
        Jwt.withTokenValue("token-he-thong")
            .header("alg", "HS256")
            .subject(USER_ID)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();
    when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(jwt);
  }

  @Test
  @DisplayName(
      "Email khớp tài khoản ACTIVE: cấp token hệ thống, ghi audit LOGIN, tokenVersion không đổi")
  void success() {
    when(googleVerifier.verify("id-token-gia"))
        .thenReturn(new GoogleIdentity("sub-1", "gv_test@example.invalid", "Tên Google"));
    when(userRepository.findByEmail("gv_test@example.invalid")).thenReturn(Optional.of(user));
    stubTokenIssuing();

    LoginResponse response = authService.loginWithGoogle(request(), httpRequest);

    assertThat(response.getAccessToken()).isEqualTo("token-he-thong");
    assertThat(response.getUser().getUsername()).isEqualTo("gv_test");
    assertThat(user.getTokenVersion()).isEqualTo(2L);
    verify(auditService)
        .record(
            eq(AuditAction.LOGIN),
            any(),
            any(),
            any(),
            eq("Đăng nhập bằng Google"),
            eq(USER_ID),
            any());
    verify(userRepository, never()).save(any());
  }

  @Test
  @DisplayName("Email Google viết hoa/thừa khoảng trắng vẫn khớp tài khoản (chuẩn hóa chữ thường)")
  void emailIsNormalised() {
    when(googleVerifier.verify("id-token-gia"))
        .thenReturn(new GoogleIdentity("sub-1", "  GV_Test@Example.Invalid ", null));
    when(userRepository.findByEmail("gv_test@example.invalid")).thenReturn(Optional.of(user));
    stubTokenIssuing();

    assertThat(authService.loginWithGoogle(request(), httpRequest).getUser().getId())
        .isEqualTo(USER_ID);
  }

  @Test
  @DisplayName("Tài khoản còn cờ đổi mật khẩu lần đầu: Google không gỡ cờ, phản hồi vẫn mang cờ")
  void keepsMustChangePasswordFlag() {
    user.setMustChangePassword(true);
    when(googleVerifier.verify("id-token-gia"))
        .thenReturn(new GoogleIdentity("sub-1", "gv_test@example.invalid", null));
    when(userRepository.findByEmail("gv_test@example.invalid")).thenReturn(Optional.of(user));
    stubTokenIssuing();

    LoginResponse response = authService.loginWithGoogle(request(), httpRequest);

    assertThat(response.getUser().isMustChangePassword()).isTrue();
    assertThat(user.isMustChangePassword()).isTrue();
    verify(userRepository, never()).save(any());
  }

  @Test
  @DisplayName(
      "Email chưa có tài khoản: bị từ chối, không tự tạo tài khoản, không cấp token, không audit")
  void unknownEmail() {
    when(googleVerifier.verify("id-token-gia"))
        .thenReturn(new GoogleIdentity("sub-1", "la@example.invalid", null));
    when(userRepository.findByEmail("la@example.invalid")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> authService.loginWithGoogle(request(), httpRequest))
        .isInstanceOf(BadCredentialsException.class)
        .hasMessageContaining("chưa được cấp tài khoản");

    verify(userRepository, never()).save(any());
    verify(jwtEncoder, never()).encode(any());
    verify(auditService, never())
        .record(any(), any(), any(), any(), any(), any(String.class), any());
  }

  @Test
  @DisplayName("Tài khoản bị khóa: bị từ chối, không cấp token")
  void lockedAccount() {
    user.setStatus(UserStatus.LOCKED);
    when(googleVerifier.verify("id-token-gia"))
        .thenReturn(new GoogleIdentity("sub-1", "gv_test@example.invalid", null));
    when(userRepository.findByEmail("gv_test@example.invalid")).thenReturn(Optional.of(user));

    assertThatThrownBy(() -> authService.loginWithGoogle(request(), httpRequest))
        .isInstanceOf(BadCredentialsException.class)
        .hasMessageContaining("bị khóa");

    verify(jwtEncoder, never()).encode(any());
  }

  @Test
  @DisplayName(
      "Token Google không hợp lệ: lỗi từ bộ xác minh được ném thẳng, không tra cứu người dùng")
  void invalidGoogleToken() {
    when(googleVerifier.verify("id-token-gia"))
        .thenThrow(new BadCredentialsException("Mã đăng nhập Google không hợp lệ hoặc đã hết hạn"));

    assertThatThrownBy(() -> authService.loginWithGoogle(request(), httpRequest))
        .isInstanceOf(BadCredentialsException.class);

    verify(userRepository, never()).findByEmail(any());
  }
}
