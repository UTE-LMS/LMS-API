package com.hcmute.lms.lmsapi.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hcmute.lms.lmsapi.document.User;
import com.hcmute.lms.lmsapi.dto.UpdateProfileRequest;
import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import com.hcmute.lms.lmsapi.dto.UserProfileResponse;
import com.hcmute.lms.lmsapi.enums.AuditAction;
import com.hcmute.lms.lmsapi.enums.Role;
import com.hcmute.lms.lmsapi.repository.UserRepository;
import com.hcmute.lms.lmsapi.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.security.authentication.BadCredentialsException;

/** Unit test AuthServiceImpl.updateProfile (F01: trang Tài khoản của tôi). Dữ liệu giả. */
@ExtendWith(MockitoExtension.class)
@DisplayName("F01: AuthServiceImpl.updateProfile (người dùng tự sửa họ tên)")
class AuthServiceUpdateProfileTest {

  private static final String USER_ID = "64b000000000000000000001";

  @Mock private UserRepository userRepository;
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
            .passwordHash("hash")
            .fullName("Tên cũ")
            .tokenVersion(3L)
            .roles(List.of(Role.LECTURER))
            .build();
    principal = UserPrincipal.from(user);
  }

  @Test
  @DisplayName(
      "Thành công: trim họ tên, chỉ đổi họ tên, giữ nguyên email/vai trò/tokenVersion, ghi audit")
  void updateProfile_success() {
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

    UserProfileResponse response =
        authService.updateProfile(
            principal, UpdateProfileRequest.builder().fullName("  Tên mới  ").build(), httpRequest);

    ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(saved.capture());
    assertThat(saved.getValue().getFullName()).isEqualTo("Tên mới");
    assertThat(saved.getValue().getEmail()).isEqualTo("gv_test@example.invalid");
    assertThat(saved.getValue().getRoles()).containsExactly(Role.LECTURER);
    assertThat(saved.getValue().getTokenVersion()).isEqualTo(3L);
    verify(auditService)
        .record(eq(AuditAction.UPDATE), any(), any(), any(), any(), eq(USER_ID), any());
    assertThat(response.getFullName()).isEqualTo("Tên mới");
  }

  @Test
  @DisplayName("Người dùng không còn trong CSDL: ném BadCredentialsException và không lưu")
  void updateProfile_userMissing() {
    when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                authService.updateProfile(
                    principal,
                    UpdateProfileRequest.builder().fullName("Tên mới").build(),
                    httpRequest))
        .isInstanceOf(BadCredentialsException.class);

    verify(userRepository, never()).save(any());
  }
}
