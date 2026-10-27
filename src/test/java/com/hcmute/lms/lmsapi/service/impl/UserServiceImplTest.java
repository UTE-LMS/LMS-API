package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.document.Role;
import com.hcmute.lms.lmsapi.document.RoleAssignment;
import com.hcmute.lms.lmsapi.document.User;
import com.hcmute.lms.lmsapi.document.UserStatus;
import com.hcmute.lms.lmsapi.dto.CreateUserRequest;
import com.hcmute.lms.lmsapi.dto.PageResponse;
import com.hcmute.lms.lmsapi.dto.UpdateUserRequest;
import com.hcmute.lms.lmsapi.dto.UpdateUserStatusRequest;
import com.hcmute.lms.lmsapi.dto.UserResponse;
import com.hcmute.lms.lmsapi.exception.AppException;
import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
import com.hcmute.lms.lmsapi.exception.ErrorCode;
import com.hcmute.lms.lmsapi.exception.ResourceNotFoundException;
import com.hcmute.lms.lmsapi.mapper.UserMapper;
import com.hcmute.lms.lmsapi.repository.UserRepository;
import com.hcmute.lms.lmsapi.repository.UserRepositoryCustom;
import com.hcmute.lms.lmsapi.service.ScopeValidationService;
import com.hcmute.lms.lmsapi.validator.PasswordPolicyValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserServiceImpl Unit Tests (F01 / US-02)")
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Spy
    private PasswordPolicyValidator passwordPolicyValidator = new PasswordPolicyValidator();

    @Mock
    private ScopeValidationService scopeValidationService;

    @InjectMocks
    private UserServiceImpl userService;

    private static final String ADMIN_ID = "671dcba5f123456789abcd01";
    private static final String TARGET_USER_ID = "671dcba5f123456789abcd02";
    private static final String OTHER_ADMIN_ID = "671dcba5f123456789abcd03";

    private User sampleTargetUser;
    private UserResponse sampleUserResponse;

    @BeforeEach
    void setUp() {
        sampleTargetUser = User.builder()
                .id(TARGET_USER_ID)
                .username("target_user")
                .email("target@hcmute.edu.vn")
                .fullName("Target User")
                .status(UserStatus.ACTIVE)
                .tokenVersion(0L)
                .roleAssignments(List.of(RoleAssignment.builder().role(Role.LECTURER).build()))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        sampleUserResponse = UserResponse.builder()
                .id(TARGET_USER_ID)
                .username("target_user")
                .email("target@hcmute.edu.vn")
                .fullName("Target User")
                .status(UserStatus.ACTIVE)
                .build();
    }

    @Nested
    @DisplayName("Create User Tests (POST /api/v1/users)")
    class CreateUserTests {

        @Test
        @DisplayName("Should successfully create user when all validations pass")
        void shouldCreateUserSuccessfully() {
            CreateUserRequest request = CreateUserRequest.builder()
                    .username("NEW_USER ")
                    .email(" NEW_USER@HCMUTE.EDU.VN ")
                    .password("ValidPassword123!")
                    .fullName("New User")
                    .roleAssignments(List.of(RoleAssignment.builder().role(Role.LECTURER).build()))
                    .build();

            when(userRepository.existsByUsername("new_user")).thenReturn(false);
            when(userRepository.existsByEmail("new_user@hcmute.edu.vn")).thenReturn(false);
            when(passwordEncoder.encode("ValidPassword123!")).thenReturn("hashed_password");
            doNothing().when(scopeValidationService).validateRoleAssignments(any());

            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User saved = invocation.getArgument(0);
                saved.setId(TARGET_USER_ID);
                return saved;
            });

            when(userMapper.toResponse(any(User.class))).thenReturn(sampleUserResponse);

            UserResponse response = userService.createUser(request);

            assertThat(response).isNotNull();
            verify(passwordEncoder).encode("ValidPassword123!");
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("Should reject user creation when username already exists (HTTP 409 CONFLICT)")
        void shouldRejectDuplicateUsername() {
            CreateUserRequest request = CreateUserRequest.builder()
                    .username("existing_user")
                    .email("user@hcmute.edu.vn")
                    .password("Password123!")
                    .fullName("User")
                    .roleAssignments(List.of(RoleAssignment.builder().role(Role.STUDENT).build()))
                    .build();

            when(userRepository.existsByUsername("existing_user")).thenReturn(true);

            assertThatThrownBy(() -> userService.createUser(request))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.CONFLICT));

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should reject user creation when email already exists (HTTP 409 CONFLICT)")
        void shouldRejectDuplicateEmail() {
            CreateUserRequest request = CreateUserRequest.builder()
                    .username("new_user")
                    .email("existing@hcmute.edu.vn")
                    .password("Password123!")
                    .fullName("User")
                    .roleAssignments(List.of(RoleAssignment.builder().role(Role.STUDENT).build()))
                    .build();

            when(userRepository.existsByUsername("new_user")).thenReturn(false);
            when(userRepository.existsByEmail("existing@hcmute.edu.vn")).thenReturn(true);

            assertThatThrownBy(() -> userService.createUser(request))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.CONFLICT));

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should reject user creation when scope validation fails (HTTP 422)")
        void shouldRejectInvalidScopeOnCreate() {
            CreateUserRequest request = CreateUserRequest.builder()
                    .username("new_user")
                    .email("new@hcmute.edu.vn")
                    .password("Password123!")
                    .fullName("User")
                    .roleAssignments(List.of(RoleAssignment.builder().role(Role.LECTURER).build()))
                    .build();

            when(userRepository.existsByUsername("new_user")).thenReturn(false);
            when(userRepository.existsByEmail("new@hcmute.edu.vn")).thenReturn(false);
            doThrow(new BusinessRuleViolationException("SCOPE_NOT_FOUND", "Course not found"))
                    .when(scopeValidationService).validateRoleAssignments(any());

            assertThatThrownBy(() -> userService.createUser(request))
                    .isInstanceOf(BusinessRuleViolationException.class);

            verify(userRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Get Users & User Detail Tests (GET /api/v1/users)")
    class GetUsersTests {

        @Test
        @DisplayName("getUsers should return PageResponse with valid envelope")
        void shouldReturnPageResponse() {
            Page<User> page = new PageImpl<>(List.of(sampleTargetUser));
            when(userRepository.findAll(any(Pageable.class))).thenReturn(page);
            when(userMapper.toResponseList(any())).thenReturn(List.of(sampleUserResponse));

            PageResponse<UserResponse> response = userService.getUsers(0, 20);

            assertThat(response).isNotNull();
            assertThat(response.getContent()).hasSize(1);
            assertThat(response.getTotalElements()).isEqualTo(1);
            assertThat(response.getPage()).isZero();
            assertThat(response.isLast()).isTrue();
        }

        @Test
        @DisplayName("getUserById should return UserResponse for valid ID")
        void shouldReturnUserById() {
            when(userRepository.findById(TARGET_USER_ID)).thenReturn(Optional.of(sampleTargetUser));
            when(userMapper.toResponse(sampleTargetUser)).thenReturn(sampleUserResponse);

            UserResponse response = userService.getUserById(TARGET_USER_ID);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(TARGET_USER_ID);
        }

        @Test
        @DisplayName("getUserById should throw AppException for malformed ObjectId (400)")
        void shouldThrowForMalformedObjectId() {
            assertThatThrownBy(() -> userService.getUserById("invalid-hex-id"))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        }

        @Test
        @DisplayName("getUserById should throw ResourceNotFoundException when user absent (404)")
        void shouldThrowNotFoundWhenUserAbsent() {
            when(userRepository.findById(TARGET_USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getUserById(TARGET_USER_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("ADMIN Guards & Update User Tests (PUT /api/v1/users/{id})")
    class UpdateUserTests {

        @Test
        @DisplayName("Should successfully update email, fullName, and roles while preserving username")
        void shouldUpdateUserSuccessfully() {
            UpdateUserRequest request = UpdateUserRequest.builder()
                    .email("updated@hcmute.edu.vn")
                    .fullName("Updated Name")
                    .roleAssignments(List.of(RoleAssignment.builder().role(Role.LECTURER).build()))
                    .build();

            when(userRepository.findById(TARGET_USER_ID)).thenReturn(Optional.of(sampleTargetUser));
            when(userRepository.findByEmail("updated@hcmute.edu.vn")).thenReturn(Optional.empty());
            doNothing().when(scopeValidationService).validateRoleAssignments(any());
            when(userRepository.save(any(User.class))).thenReturn(sampleTargetUser);
            when(userMapper.toResponse(sampleTargetUser)).thenReturn(sampleUserResponse);

            UserResponse response = userService.updateUser(TARGET_USER_ID, request, ADMIN_ID);

            assertThat(response).isNotNull();
            assertThat(sampleTargetUser.getUsername()).isEqualTo("target_user"); // immutable
            verify(userRepository).save(sampleTargetUser);
        }

        @Test
        @DisplayName("Guard 2: Should reject ADMIN self-demotion (HTTP 422)")
        void shouldRejectAdminSelfDemotion() {
            User adminUser = User.builder()
                    .id(ADMIN_ID)
                    .username("admin")
                    .email("admin@hcmute.edu.vn")
                    .roleAssignments(List.of(RoleAssignment.builder().role(Role.ADMIN).build()))
                    .status(UserStatus.ACTIVE)
                    .build();

            UpdateUserRequest demotionRequest = UpdateUserRequest.builder()
                    .email("admin@hcmute.edu.vn")
                    .fullName("Admin User")
                    .roleAssignments(List.of(RoleAssignment.builder().role(Role.LECTURER).build())) // no ADMIN
                    .build();

            when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(adminUser));
            doNothing().when(scopeValidationService).validateRoleAssignments(any());

            assertThatThrownBy(() -> userService.updateUser(ADMIN_ID, demotionRequest, ADMIN_ID))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("ADMIN_GUARD")
                    .hasMessageContaining("tự gỡ bỏ vai trò ADMIN của chính mình");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Guard 3B: Should reject demoting the last active ADMIN in system (HTTP 422)")
        void shouldRejectDemotingLastActiveAdmin() {
            User lastAdmin = User.builder()
                    .id(OTHER_ADMIN_ID)
                    .username("last_admin")
                    .email("last_admin@hcmute.edu.vn")
                    .roleAssignments(List.of(RoleAssignment.builder().role(Role.ADMIN).build()))
                    .status(UserStatus.ACTIVE)
                    .build();

            UpdateUserRequest demotionRequest = UpdateUserRequest.builder()
                    .email("last_admin@hcmute.edu.vn")
                    .fullName("Last Admin")
                    .roleAssignments(List.of(RoleAssignment.builder().role(Role.LECTURER).build())) // no ADMIN
                    .build();

            when(userRepository.findById(OTHER_ADMIN_ID)).thenReturn(Optional.of(lastAdmin));
            doNothing().when(scopeValidationService).validateRoleAssignments(any());
            when(userRepository.countByRoleAssignmentsRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE)).thenReturn(1L);

            assertThatThrownBy(() -> userService.updateUser(OTHER_ADMIN_ID, demotionRequest, ADMIN_ID))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("ADMIN_GUARD")
                    .hasMessageContaining("duy nhất còn lại trong hệ thống");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should allow demoting an ADMIN if more than 1 active ADMINs exist")
        void shouldAllowDemotingAdminWhenMultipleAdminsExist() {
            User admin2 = User.builder()
                    .id(OTHER_ADMIN_ID)
                    .username("admin2")
                    .email("admin2@hcmute.edu.vn")
                    .roleAssignments(new ArrayList<>(List.of(RoleAssignment.builder().role(Role.ADMIN).build())))
                    .status(UserStatus.ACTIVE)
                    .build();

            UpdateUserRequest demotionRequest = UpdateUserRequest.builder()
                    .email("admin2@hcmute.edu.vn")
                    .fullName("Admin Two")
                    .roleAssignments(List.of(RoleAssignment.builder().role(Role.LECTURER).build()))
                    .build();

            when(userRepository.findById(OTHER_ADMIN_ID)).thenReturn(Optional.of(admin2));
            doNothing().when(scopeValidationService).validateRoleAssignments(any());
            when(userRepository.countByRoleAssignmentsRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE)).thenReturn(2L);
            when(userRepository.save(any(User.class))).thenReturn(admin2);
            when(userMapper.toResponse(admin2)).thenReturn(sampleUserResponse);

            UserResponse response = userService.updateUser(OTHER_ADMIN_ID, demotionRequest, ADMIN_ID);

            assertThat(response).isNotNull();
            verify(userRepository).save(admin2);
        }
    }

    @Nested
    @DisplayName("Status Update & Lock Revocation Tests (PUT /api/v1/users/{id}/status)")
    class StatusUpdateTests {

        @Test
        @DisplayName("Guard 1: Should reject ADMIN self-lock (HTTP 422)")
        void shouldRejectAdminSelfLock() {
            User adminUser = User.builder()
                    .id(ADMIN_ID)
                    .username("admin")
                    .roleAssignments(List.of(RoleAssignment.builder().role(Role.ADMIN).build()))
                    .status(UserStatus.ACTIVE)
                    .build();

            UpdateUserStatusRequest lockRequest = UpdateUserStatusRequest.builder()
                    .status(UserStatus.LOCKED)
                    .build();

            when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(adminUser));

            assertThatThrownBy(() -> userService.updateStatus(ADMIN_ID, lockRequest, ADMIN_ID))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("ADMIN_GUARD")
                    .hasMessageContaining("tự khóa tài khoản của chính mình");

            verify(userRepository, never()).incrementTokenVersion(anyString());
        }

        @Test
        @DisplayName("Guard 3A: Should reject locking the last active ADMIN in system (HTTP 422)")
        void shouldRejectLockingLastActiveAdmin() {
            User lastAdmin = User.builder()
                    .id(OTHER_ADMIN_ID)
                    .username("last_admin")
                    .roleAssignments(List.of(RoleAssignment.builder().role(Role.ADMIN).build()))
                    .status(UserStatus.ACTIVE)
                    .build();

            UpdateUserStatusRequest lockRequest = UpdateUserStatusRequest.builder()
                    .status(UserStatus.LOCKED)
                    .build();

            when(userRepository.findById(OTHER_ADMIN_ID)).thenReturn(Optional.of(lastAdmin));
            when(userRepository.countByRoleAssignmentsRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE)).thenReturn(1L);

            assertThatThrownBy(() -> userService.updateStatus(OTHER_ADMIN_ID, lockRequest, ADMIN_ID))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("ADMIN_GUARD")
                    .hasMessageContaining("duy nhất còn lại trong hệ thống");

            verify(userRepository, never()).incrementTokenVersion(anyString());
        }

        @Test
        @DisplayName("Should lock non-admin user and call incrementTokenVersion to revoke tokens")
        void shouldLockUserAndIncrementTokenVersion() {
            UpdateUserStatusRequest lockRequest = UpdateUserStatusRequest.builder()
                    .status(UserStatus.LOCKED)
                    .build();

            when(userRepository.findById(TARGET_USER_ID)).thenReturn(Optional.of(sampleTargetUser));
            when(userRepository.save(any(User.class))).thenReturn(sampleTargetUser);
            when(userRepository.incrementTokenVersion(TARGET_USER_ID)).thenReturn(1L);
            when(userMapper.toResponse(any(User.class))).thenReturn(sampleUserResponse);

            UserResponse response = userService.updateStatus(TARGET_USER_ID, lockRequest, ADMIN_ID);

            assertThat(response).isNotNull();
            assertThat(sampleTargetUser.getStatus()).isEqualTo(UserStatus.LOCKED);
            verify(userRepository).incrementTokenVersion(TARGET_USER_ID);
        }

        @Test
        @DisplayName("Should unlock user to ACTIVE without resetting or decrementing tokenVersion")
        void shouldUnlockUserToActive() {
            sampleTargetUser.setStatus(UserStatus.LOCKED);
            sampleTargetUser.setTokenVersion(3L);

            UpdateUserStatusRequest unlockRequest = UpdateUserStatusRequest.builder()
                    .status(UserStatus.ACTIVE)
                    .build();

            when(userRepository.findById(TARGET_USER_ID)).thenReturn(Optional.of(sampleTargetUser));
            when(userRepository.save(any(User.class))).thenReturn(sampleTargetUser);
            when(userMapper.toResponse(any(User.class))).thenReturn(sampleUserResponse);

            UserResponse response = userService.updateStatus(TARGET_USER_ID, unlockRequest, ADMIN_ID);

            assertThat(response).isNotNull();
            assertThat(sampleTargetUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(sampleTargetUser.getTokenVersion()).isEqualTo(3L); // tokenVersion remains 3L
            verify(userRepository, never()).incrementTokenVersion(anyString());
        }
    }
}
