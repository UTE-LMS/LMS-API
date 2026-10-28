package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.enums.Role;
import com.hcmute.lms.lmsapi.document.RoleAssignment;
import com.hcmute.lms.lmsapi.document.User;
import com.hcmute.lms.lmsapi.document.UserScope;
import com.hcmute.lms.lmsapi.enums.UserStatus;
import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import com.hcmute.lms.lmsapi.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit Test kiểm thử logic nghiệp vụ của AuthSessionServiceImpl (F01 / ADR 0003).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthSessionServiceImpl: Session & TokenVersion Validation Unit Tests")
class AuthSessionServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AuthSessionServiceImpl authSessionService;

    private User activeUser;

    @BeforeEach
    void setUp() {
        activeUser = User.builder()
                .id("671dcba5f123456789abcdef")
                .username("test.lecturer")
                .email("lecturer@hcmute.edu.vn")
                .fullName("Nguyễn Văn Giảng Viên")
                .status(UserStatus.ACTIVE)
                .tokenVersion(5L)
                .roleAssignments(List.of(
                        RoleAssignment.builder()
                                .role(Role.LECTURER)
                                .scope(UserScope.builder()
                                        .sectionIds(List.of("671dcba5f123456789abcdef"))
                                        .build())
                                .build()
                ))
                .build();
    }

    @Test
    @DisplayName("validateUserSession(userId, tokenVersion) thành công khi user tồn tại, ACTIVE và tokenVersion khớp")
    void validateUserSession_Success() {
        when(userRepository.findById("671dcba5f123456789abcdef")).thenReturn(Optional.of(activeUser));

        UserPrincipal principal = authSessionService.validateUserSession("671dcba5f123456789abcdef", 5L);

        assertThat(principal).isNotNull();
        assertThat(principal.getId()).isEqualTo("671dcba5f123456789abcdef");
        assertThat(principal.getUsername()).isEqualTo("test.lecturer");
        assertThat(principal.getEmail()).isEqualTo("lecturer@hcmute.edu.vn");
        assertThat(principal.getFullName()).isEqualTo("Nguyễn Văn Giảng Viên");
        assertThat(principal.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(principal.getTokenVersion()).isEqualTo(5L);
        assertThat(principal.getRoleAssignments()).hasSize(1);
        assertThat(principal.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_LECTURER");
    }

    @Test
    @DisplayName("validateUserSession ném InvalidBearerTokenException khi userId null hoặc blank")
    void validateUserSession_NullOrBlankUserId_ThrowsException() {
        assertThatThrownBy(() -> authSessionService.validateUserSession(null, 5L))
                .isInstanceOf(InvalidBearerTokenException.class)
                .extracting(t -> ((InvalidBearerTokenException) t).getError().getDescription())
                .asString()
                .contains("userId");

        assertThatThrownBy(() -> authSessionService.validateUserSession("   ", 5L))
                .isInstanceOf(InvalidBearerTokenException.class)
                .extracting(t -> ((InvalidBearerTokenException) t).getError().getDescription())
                .asString()
                .contains("userId");
    }

    @Test
    @DisplayName("validateUserSession ném InvalidBearerTokenException khi user không tồn tại trong CSDL")
    void validateUserSession_UserNotFound_ThrowsException() {
        when(userRepository.findById("nonexistent_id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authSessionService.validateUserSession("nonexistent_id", 1L))
                .isInstanceOf(InvalidBearerTokenException.class)
                .extracting(t -> ((InvalidBearerTokenException) t).getError().getDescription())
                .asString()
                .contains("User does not exist");
    }

    @Test
    @DisplayName("validateUserSession ném InvalidBearerTokenException khi user ở trạng thái LOCKED")
    void validateUserSession_UserLocked_ThrowsException() {
        activeUser.setStatus(UserStatus.LOCKED);
        when(userRepository.findById("671dcba5f123456789abcdef")).thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> authSessionService.validateUserSession("671dcba5f123456789abcdef", 5L))
                .isInstanceOf(InvalidBearerTokenException.class)
                .extracting(t -> ((InvalidBearerTokenException) t).getError().getDescription())
                .asString()
                .contains("locked or inactive");
    }

    @Test
    @DisplayName("validateUserSession ném InvalidBearerTokenException khi tokenVersion không khớp (token cũ)")
    void validateUserSession_TokenVersionMismatch_ThrowsException() {
        // DB tokenVersion = 5L, token mang tokenVersion = 4L (đã logout hoặc reset)
        when(userRepository.findById("671dcba5f123456789abcdef")).thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> authSessionService.validateUserSession("671dcba5f123456789abcdef", 4L))
                .isInstanceOf(InvalidBearerTokenException.class)
                .extracting(t -> ((InvalidBearerTokenException) t).getError().getDescription())
                .asString()
                .contains("tokenVersion mismatch");
    }

    @Test
    @DisplayName("validateUserSession(jwt) thành công khi claim hợp lệ")
    void validateUserSession_FromJwt_Success() {
        Jwt jwt = new Jwt(
                "token_value",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "HS256"),
                Map.of("sub", "671dcba5f123456789abcdef", "tokenVersion", 5L)
        );

        when(userRepository.findById("671dcba5f123456789abcdef")).thenReturn(Optional.of(activeUser));

        UserPrincipal principal = authSessionService.validateUserSession(jwt);

        assertThat(principal).isNotNull();
        assertThat(principal.getId()).isEqualTo("671dcba5f123456789abcdef");
    }

    @Test
    @DisplayName("validateUserSession(jwt) hỗ trợ tokenVersion dạng Number int/Integer")
    void validateUserSession_FromJwt_WithIntegerTokenVersion() {
        Jwt jwt = new Jwt(
                "token_value",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "HS256"),
                Map.of("sub", "671dcba5f123456789abcdef", "tokenVersion", 5)
        );

        when(userRepository.findById("671dcba5f123456789abcdef")).thenReturn(Optional.of(activeUser));

        UserPrincipal principal = authSessionService.validateUserSession(jwt);

        assertThat(principal).isNotNull();
        assertThat(principal.getTokenVersion()).isEqualTo(5L);
    }

    @Test
    @DisplayName("validateUserSession(jwt) ném ngoại lệ khi jwt null hoặc thiếu claim tokenVersion")
    void validateUserSession_FromJwt_MissingClaims() {
        assertThatThrownBy(() -> authSessionService.validateUserSession((Jwt) null))
                .isInstanceOf(InvalidBearerTokenException.class)
                .extracting(t -> ((InvalidBearerTokenException) t).getError().getDescription())
                .asString()
                .contains("Token cannot be null");

        Jwt jwtWithoutTokenVersion = new Jwt(
                "token_value",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "HS256"),
                Map.of("sub", "671dcba5f123456789abcdef")
        );

        assertThatThrownBy(() -> authSessionService.validateUserSession(jwtWithoutTokenVersion))
                .isInstanceOf(InvalidBearerTokenException.class)
                .extracting(t -> ((InvalidBearerTokenException) t).getError().getDescription())
                .asString()
                .contains("missing tokenVersion");
    }

    @Test
    @DisplayName("validateUserSession(jwt) ném ngoại lệ khi tokenVersion sai định dạng số")
    void validateUserSession_FromJwt_InvalidNumberFormat() {
        Jwt jwtWithInvalidNumber = new Jwt(
                "token_value",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "HS256"),
                Map.of("sub", "671dcba5f123456789abcdef", "tokenVersion", "invalid_number")
        );

        assertThatThrownBy(() -> authSessionService.validateUserSession(jwtWithInvalidNumber))
                .isInstanceOf(InvalidBearerTokenException.class)
                .extracting(t -> ((InvalidBearerTokenException) t).getError().getDescription())
                .asString()
                .contains("not a valid number");
    }
}
