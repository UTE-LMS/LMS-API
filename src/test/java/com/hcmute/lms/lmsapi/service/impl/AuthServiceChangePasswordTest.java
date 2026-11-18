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
import com.hcmute.lms.lmsapi.dto.ChangePasswordRequest;
import com.hcmute.lms.lmsapi.dto.LoginResponse;
import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import com.hcmute.lms.lmsapi.enums.AuditAction;
import com.hcmute.lms.lmsapi.enums.Role;
import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
import com.hcmute.lms.lmsapi.repository.UserRepository;
import com.hcmute.lms.lmsapi.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

/**
 * Unit test đổi mật khẩu lần đầu (F01): AuthServiceImpl.changePassword. Dữ liệu giả, không dùng
 * MongoDB.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("F01: AuthServiceImpl.changePassword (đổi mật khẩu lần đầu)")
class AuthServiceChangePasswordTest {

  private static final String USER_ID = "64b000000000000000000001";
  private static final String OLD_HASH = "old-hash";

  @Mock private UserRepository userRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private JwtEncoder jwtEncoder;
  @Mock private JwtConfig jwtConfig;
  @Mock private AuditService auditService;
  @Mock private HttpServletRequest httpRequest;

  @InjectMocks private AuthServiceImpl authService;

  private User user;
  private UserPrincipal principal;

  @BeforeEach
  void setUp() {
    user =
        User.builder()
            .id(USER_ID)
            .username("gv_test")
            .email("gv_test@example.invalid")
            .passwordHash(OLD_HASH)
            .fullName("[DỮ LIỆU THỬ NGHIỆM] Giảng viên")
            .tokenVersion(3L)
            .mustChangePassword(true)
            .roles(List.of(Role.LECTURER))
            .build();
    principal = UserPrincipal.from(user);
  }

  private ChangePasswordRequest request(String current, String next) {
    return ChangePasswordRequest.builder().currentPassword(current).newPassword(next).build();
  }

  @Test
  @DisplayName("Thành công: đổi hash, tắt cờ, tokenVersion 3 -> 4, ghi audit UPDATE, cấp token mới")
  void changePassword_success() {
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("OldPass123!", OLD_HASH)).thenReturn(true);
    when(passwordEncoder.matches("NewPass456!", OLD_HASH)).thenReturn(false);
    when(passwordEncoder.encode("NewPass456!")).thenReturn("new-hash");
    when(jwtConfig.getTtl()).thenReturn(Duration.ofMinutes(60));
    when(jwtConfig.getIssuer()).thenReturn("lms-api");
    Jwt jwt =
        Jwt.withTokenValue("token-moi")
            .header("alg", "HS256")
            .subject(USER_ID)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();
    when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(jwt);

    LoginResponse response =
        authService.changePassword(principal, request("OldPass123!", "NewPass456!"), httpRequest);

    ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(saved.capture());
    assertThat(saved.getValue().getPasswordHash()).isEqualTo("new-hash");
    assertThat(saved.getValue().isMustChangePassword()).isFalse();
    assertThat(saved.getValue().getTokenVersion()).isEqualTo(4L);
    verify(auditService)
        .record(eq(AuditAction.UPDATE), any(), any(), any(), any(), eq(USER_ID), any());
    assertThat(response.getAccessToken()).isEqualTo("token-moi");
    assertThat(response.getUser().isMustChangePassword()).isFalse();
  }

  @Test
  @DisplayName("Sai mật khẩu hiện tại: ném WRONG_CURRENT_PASSWORD và không lưu gì")
  void changePassword_wrongCurrentPassword() {
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("Sai-mat-khau", OLD_HASH)).thenReturn(false);

    assertThatThrownBy(
            () ->
                authService.changePassword(
                    principal, request("Sai-mat-khau", "NewPass456!"), httpRequest))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessageContaining("Mật khẩu hiện tại không chính xác");

    verify(userRepository, never()).save(any());
    assertThat(user.isMustChangePassword()).isTrue();
  }

  @Test
  @DisplayName("Mật khẩu mới trùng mật khẩu cũ: bị từ chối, cờ vẫn bật")
  void changePassword_sameAsOld() {
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("OldPass123!", OLD_HASH)).thenReturn(true);

    assertThatThrownBy(
            () ->
                authService.changePassword(
                    principal, request("OldPass123!", "OldPass123!"), httpRequest))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessageContaining("phải khác");

    verify(userRepository, never()).save(any());
    assertThat(user.isMustChangePassword()).isTrue();
  }
}
