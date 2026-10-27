package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.config.JwtConfig;
import com.hcmute.lms.lmsapi.document.AuditAction;
import com.hcmute.lms.lmsapi.document.AuditEntity;
import com.hcmute.lms.lmsapi.document.Role;
import com.hcmute.lms.lmsapi.document.RoleAssignment;
import com.hcmute.lms.lmsapi.document.User;
import com.hcmute.lms.lmsapi.document.UserStatus;
import com.hcmute.lms.lmsapi.dto.LoginRequest;
import com.hcmute.lms.lmsapi.dto.LoginResponse;
import com.hcmute.lms.lmsapi.repository.UserRepository;
import com.hcmute.lms.lmsapi.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit Test kiểm thử cơ chế chống Timing Attack (User Enumeration) và quy tắc bảo mật khi đăng nhập (F01 / ADR 0003).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Timing Attack Defense & Login Security Unit Tests (Mockito Verification)")
class TimingAttackDefenseTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtEncoder jwtEncoder;

    @Mock
    private JwtConfig jwtConfig;

    @Mock
    private AuditService auditService;

    @Mock
    private HttpServletRequest httpRequest;

    @InjectMocks
    private AuthServiceImpl authService;

    private User existingUser;

    @BeforeEach
    void setUp() {
        existingUser = User.builder()
                .id("671dcba5f123456789abcdef")
                .username("testuser")
                .email("testuser@hcmute.edu.vn")
                .fullName("Người Dùng Thử Nghiệm")
                .passwordHash("$2a$10$realHashForExistingUserPassword123")
                .status(UserStatus.ACTIVE)
                .tokenVersion(0L)
                .roleAssignments(List.of(
                        RoleAssignment.builder().role(Role.LECTURER).build()
                ))
                .build();
    }

    @Test
    @DisplayName("Chống Timing Attack: Khi username KHÔNG tồn tại, passwordEncoder.matches PHẢI được gọi với DUMMY_HASH")
    void login_NonExistentUser_MustCallPasswordEncoderWithDummyHash() {
        String nonExistentUsername = "unknown_user_999";
        String rawPassword = "Password123!";
        LoginRequest request = new LoginRequest(nonExistentUsername, rawPassword);

        when(userRepository.findByUsername(nonExistentUsername)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request, httpRequest))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Tên đăng nhập hoặc mật khẩu không chính xác");

        // BẮT BUỘC KIỂM TRA MOCKITO: passwordEncoder.matches phải được gọi đúng với chuỗi hash giả định
        verify(passwordEncoder).matches(eq(rawPassword), eq(AuthServiceImpl.DUMMY_HASH));

        // Tuyệt đối không sinh token và không ghi audit log
        verify(jwtEncoder, never()).encode(any());
        verify(auditService, never()).record(any(), any(), any(), any(), any(), any(String.class), any());
    }

    @Test
    @DisplayName("Chuẩn hóa Username: Username có khoảng trắng hoặc chữ hoa vẫn được chuẩn hóa trước khi truy vấn và dummy hash")
    void login_UsernameWithWhitespaceAndUppercase_IsNormalized() {
        String rawInputUsername = "   Admin_User_Test   ";
        String normalized = "admin_user_test";
        String rawPassword = "SecretPassword123!";
        LoginRequest request = new LoginRequest(rawInputUsername, rawPassword);

        when(userRepository.findByUsername(normalized)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request, httpRequest))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Tên đăng nhập hoặc mật khẩu không chính xác");

        verify(userRepository).findByUsername(normalized);
        verify(passwordEncoder).matches(eq(rawPassword), eq(AuthServiceImpl.DUMMY_HASH));
    }

    @Test
    @DisplayName("Sai mật khẩu: Ném BadCredentialsException với thông điệp chung và gọi passwordEncoder.matches với hash thật")
    void login_WrongPassword_ThrowsBadCredentialsException() {
        String rawPassword = "WrongPassword123!";
        LoginRequest request = new LoginRequest("testuser", rawPassword);

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches(rawPassword, existingUser.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request, httpRequest))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Tên đăng nhập hoặc mật khẩu không chính xác");

        verify(passwordEncoder).matches(rawPassword, existingUser.getPasswordHash());
        verify(jwtEncoder, never()).encode(any());
        verify(auditService, never()).record(any(), any(), any(), any(), any(), any(String.class), any());
    }

    @Test
    @DisplayName("Tài khoản bị LOCKED: Ném BadCredentialsException với thông điệp chung (không tiết lộ trạng thái khóa)")
    void login_LockedUser_ThrowsBadCredentialsWithGenericMessage() {
        existingUser.setStatus(UserStatus.LOCKED);
        String rawPassword = "Password123!";
        LoginRequest request = new LoginRequest("testuser", rawPassword);

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches(rawPassword, existingUser.getPasswordHash())).thenReturn(true);

        assertThatThrownBy(() -> authService.login(request, httpRequest))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Tên đăng nhập hoặc mật khẩu không chính xác");

        verify(jwtEncoder, never()).encode(any());
        verify(auditService, never()).record(any(), any(), any(), any(), any(), any(String.class), any());
    }

    @Test
    @DisplayName("Đăng nhập thành công: Sinh token HS256, ghi audit log LOGIN và trả về LoginResponse")
    void login_Success_GeneratesJwtAndRecordsAudit() {
        String rawPassword = "Password123!";
        LoginRequest request = new LoginRequest("testuser", rawPassword);

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches(rawPassword, existingUser.getPasswordHash())).thenReturn(true);
        when(jwtConfig.getTtl()).thenReturn(Duration.ofMinutes(60));
        when(jwtConfig.getIssuer()).thenReturn("lms-api");
        when(httpRequest.getRemoteAddr()).thenReturn("192.168.1.100");

        Jwt mockJwt = new Jwt("mock.jwt.token", Instant.now(), Instant.now().plusSeconds(3600),
                java.util.Map.of("alg", "HS256"), java.util.Map.of("sub", existingUser.getId()));
        when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(mockJwt);

        LoginResponse response = authService.login(request, httpRequest);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("mock.jwt.token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(3600L);
        assertThat(response.getUser()).isNotNull();
        assertThat(response.getUser().getUsername()).isEqualTo("testuser");
        assertThat(response.getUser().getStatus()).isEqualTo(UserStatus.ACTIVE);

        // Kiểm tra audit log đã được gọi đúng tham số
        ArgumentCaptor<AuditEntity> entityCaptor = ArgumentCaptor.forClass(AuditEntity.class);
        verify(auditService).record(
                eq(AuditAction.LOGIN),
                entityCaptor.capture(),
                eq(null),
                eq(null),
                eq(null),
                eq(existingUser.getId()),
                eq("192.168.1.100")
        );
        assertThat(entityCaptor.getValue().getCollection()).isEqualTo("users");
        assertThat(entityCaptor.getValue().getId()).isEqualTo(existingUser.getId());
    }
}
