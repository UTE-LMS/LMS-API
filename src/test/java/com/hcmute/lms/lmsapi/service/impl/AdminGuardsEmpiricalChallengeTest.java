package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.document.Role;
import com.hcmute.lms.lmsapi.document.RoleAssignment;
import com.hcmute.lms.lmsapi.document.User;
import com.hcmute.lms.lmsapi.document.UserStatus;
import com.hcmute.lms.lmsapi.dto.UpdateUserRequest;
import com.hcmute.lms.lmsapi.dto.UpdateUserStatusRequest;
import com.hcmute.lms.lmsapi.dto.UserResponse;
import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
import com.hcmute.lms.lmsapi.mapper.UserMapper;
import com.hcmute.lms.lmsapi.repository.UserRepository;
import com.hcmute.lms.lmsapi.service.ScopeValidationService;
import com.hcmute.lms.lmsapi.validator.PasswordPolicyValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Empirical Challenge: Admin Guards Stress & Edge Case Verification (Challenger 1 - PR 4).
 * Rigorously challenges:
 * 1. Guard 1: Admin self-lock prevention.
 * 2. Guard 2: Admin self-demotion prevention (single role, multi-role, partial role drop).
 * 3. Guard 3A: Last active Admin lock prevention (boundary counts: 0, 1 vs 2+; status: ACTIVE vs LOCKED).
 * 4. Guard 3B: Last active Admin demotion prevention (boundary counts: 0, 1 vs 2+; status: ACTIVE vs LOCKED).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Empirical Challenge: Admin Guards Adversarial Stress Tests (PR 4 / US-02)")
class AdminGuardsEmpiricalChallengeTest {

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

    private static final String CURRENT_ADMIN_ID = "671dcba5f123456789abcd01";
    private static final String OTHER_ADMIN_ID = "671dcba5f123456789abcd02";
    private static final String NON_ADMIN_ID = "671dcba5f123456789abcd03";

    private User currentAdmin;
    private User otherAdmin;
    private User nonAdminUser;
    private UserResponse mockResponse;

    @BeforeEach
    void setUp() {
        currentAdmin = User.builder()
                .id(CURRENT_ADMIN_ID)
                .username("current_admin")
                .email("admin@hcmute.edu.vn")
                .fullName("[DỮ LIỆU THỬ NGHIỆM] Quản Trị Viên Hiện Tại")
                .status(UserStatus.ACTIVE)
                .tokenVersion(0L)
                .roleAssignments(new ArrayList<>(List.of(RoleAssignment.builder().role(Role.ADMIN).build())))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        otherAdmin = User.builder()
                .id(OTHER_ADMIN_ID)
                .username("other_admin")
                .email("other_admin@hcmute.edu.vn")
                .fullName("[DỮ LIỆU THỬ NGHIỆM] Quản Trị Viên Khác")
                .status(UserStatus.ACTIVE)
                .tokenVersion(0L)
                .roleAssignments(new ArrayList<>(List.of(RoleAssignment.builder().role(Role.ADMIN).build())))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        nonAdminUser = User.builder()
                .id(NON_ADMIN_ID)
                .username("lecturer_user")
                .email("lecturer@hcmute.edu.vn")
                .fullName("[DỮ LIỆU THỬ NGHIỆM] Giảng Viên")
                .status(UserStatus.ACTIVE)
                .tokenVersion(0L)
                .roleAssignments(new ArrayList<>(List.of(RoleAssignment.builder().role(Role.LECTURER).build())))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        mockResponse = UserResponse.builder()
                .id(CURRENT_ADMIN_ID)
                .username("mock_user")
                .status(UserStatus.ACTIVE)
                .build();
    }

    // =========================================================================
    // GUARD 1: SELF-LOCK PREVENTION
    // =========================================================================
    @Nested
    @DisplayName("Guard 1 Empirical Challenge: Admin Self-Lock Prevention")
    class SelfLockGuardTests {

        @Test
        @DisplayName("Challenger 1.1: Admin self-lock must throw BusinessRuleViolationException (HTTP 422)")
        void selfLock_MustThrow422() {
            when(userRepository.findById(CURRENT_ADMIN_ID)).thenReturn(Optional.of(currentAdmin));

            UpdateUserStatusRequest lockRequest = new UpdateUserStatusRequest(UserStatus.LOCKED);

            assertThatThrownBy(() -> userService.updateStatus(CURRENT_ADMIN_ID, lockRequest, CURRENT_ADMIN_ID))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("ADMIN_GUARD")
                    .hasMessageContaining("tự khóa tài khoản của chính mình");

            verify(userRepository, never()).incrementTokenVersion(anyString());
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("Challenger 1.2: Admin self-unlock (status == ACTIVE) is permitted (no-op/safe save)")
        void selfUnlock_Allowed() {
            when(userRepository.findById(CURRENT_ADMIN_ID)).thenReturn(Optional.of(currentAdmin));
            when(userRepository.save(any(User.class))).thenReturn(currentAdmin);
            when(userMapper.toResponse(currentAdmin)).thenReturn(mockResponse);

            UpdateUserStatusRequest activateRequest = new UpdateUserStatusRequest(UserStatus.ACTIVE);

            UserResponse response = userService.updateStatus(CURRENT_ADMIN_ID, activateRequest, CURRENT_ADMIN_ID);

            assertThat(response).isNotNull();
            verify(userRepository).save(currentAdmin);
            verify(userRepository, never()).incrementTokenVersion(anyString());
        }

        @Test
        @DisplayName("Challenger 1.3: Admin locking a non-self non-admin user must succeed and increment tokenVersion")
        void lockOtherNonAdmin_Succeeds() {
            when(userRepository.findById(NON_ADMIN_ID)).thenReturn(Optional.of(nonAdminUser));
            when(userRepository.save(any(User.class))).thenReturn(nonAdminUser);
            when(userRepository.incrementTokenVersion(NON_ADMIN_ID)).thenReturn(1L);
            when(userMapper.toResponse(any(User.class))).thenReturn(mockResponse);

            UpdateUserStatusRequest lockRequest = new UpdateUserStatusRequest(UserStatus.LOCKED);

            UserResponse response = userService.updateStatus(NON_ADMIN_ID, lockRequest, CURRENT_ADMIN_ID);

            assertThat(response).isNotNull();
            verify(userRepository).incrementTokenVersion(NON_ADMIN_ID);
        }
    }

    // =========================================================================
    // GUARD 2: SELF-DEMOTION PREVENTION
    // =========================================================================
    @Nested
    @DisplayName("Guard 2 Empirical Challenge: Admin Self-Demotion Prevention")
    class SelfDemoteGuardTests {

        @Test
        @DisplayName("Challenger 2.1: Admin self-demote by replacing ADMIN with LECTURER must throw HTTP 422")
        void selfDemote_ToLecturer_MustThrow422() {
            when(userRepository.findById(CURRENT_ADMIN_ID)).thenReturn(Optional.of(currentAdmin));
            doNothing().when(scopeValidationService).validateRoleAssignments(any());

            UpdateUserRequest demotionRequest = UpdateUserRequest.builder()
                    .email("admin@hcmute.edu.vn")
                    .fullName("Admin Name")
                    .roleAssignments(List.of(RoleAssignment.builder().role(Role.LECTURER).build()))
                    .build();

            assertThatThrownBy(() -> userService.updateUser(CURRENT_ADMIN_ID, demotionRequest, CURRENT_ADMIN_ID))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("ADMIN_GUARD")
                    .hasMessageContaining("tự gỡ bỏ vai trò ADMIN của chính mình");

            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("Challenger 2.2: Multi-role Admin self-demote (ADMIN + LECTURER -> only LECTURER) must throw HTTP 422")
        void multiRoleAdmin_SelfDemote_MustThrow422() {
            currentAdmin.setRoleAssignments(new ArrayList<>(List.of(
                    RoleAssignment.builder().role(Role.ADMIN).build(),
                    RoleAssignment.builder().role(Role.LECTURER).build()
            )));

            when(userRepository.findById(CURRENT_ADMIN_ID)).thenReturn(Optional.of(currentAdmin));
            doNothing().when(scopeValidationService).validateRoleAssignments(any());

            UpdateUserRequest demotionRequest = UpdateUserRequest.builder()
                    .email("admin@hcmute.edu.vn")
                    .fullName("Admin Name")
                    .roleAssignments(List.of(RoleAssignment.builder().role(Role.LECTURER).build()))
                    .build();

            assertThatThrownBy(() -> userService.updateUser(CURRENT_ADMIN_ID, demotionRequest, CURRENT_ADMIN_ID))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("ADMIN_GUARD")
                    .hasMessageContaining("tự gỡ bỏ vai trò ADMIN của chính mình");

            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("Challenger 2.3: Admin updating self while retaining ADMIN role must succeed")
        void selfUpdate_RetainingAdminRole_Succeeds() {
            when(userRepository.findById(CURRENT_ADMIN_ID)).thenReturn(Optional.of(currentAdmin));
            doNothing().when(scopeValidationService).validateRoleAssignments(any());
            when(userRepository.save(any(User.class))).thenReturn(currentAdmin);
            when(userMapper.toResponse(currentAdmin)).thenReturn(mockResponse);

            UpdateUserRequest updateRequest = UpdateUserRequest.builder()
                    .email("admin@hcmute.edu.vn")
                    .fullName("Admin Updated FullName")
                    .roleAssignments(List.of(
                            RoleAssignment.builder().role(Role.ADMIN).build(),
                            RoleAssignment.builder().role(Role.VIEWER).build()
                    ))
                    .build();

            UserResponse response = userService.updateUser(CURRENT_ADMIN_ID, updateRequest, CURRENT_ADMIN_ID);

            assertThat(response).isNotNull();
            verify(userRepository).save(currentAdmin);
        }
    }

    // =========================================================================
    // GUARD 3A: LOCKING LAST ACTIVE ADMIN PREVENTION
    // =========================================================================
    @Nested
    @DisplayName("Guard 3A Empirical Challenge: Lock Last Active Admin Prevention")
    class LockLastActiveAdminGuardTests {

        @ParameterizedTest(name = "activeAdminCount = {0} must block locking other active admin with HTTP 422")
        @ValueSource(longs = {0L, 1L})
        @DisplayName("Challenger 3A.1: Locking active ADMIN when active count <= 1 must throw HTTP 422")
        void lockActiveAdmin_WhenCountLessThanOrEqual1_MustThrow422(long activeAdminCount) {
            when(userRepository.findById(OTHER_ADMIN_ID)).thenReturn(Optional.of(otherAdmin));
            when(userRepository.countByRoleAssignmentsRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE)).thenReturn(activeAdminCount);

            UpdateUserStatusRequest lockRequest = new UpdateUserStatusRequest(UserStatus.LOCKED);

            assertThatThrownBy(() -> userService.updateStatus(OTHER_ADMIN_ID, lockRequest, CURRENT_ADMIN_ID))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("ADMIN_GUARD")
                    .hasMessageContaining("duy nhất còn lại trong hệ thống");

            verify(userRepository, never()).incrementTokenVersion(anyString());
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("Challenger 3A.2: Locking active ADMIN when active count >= 2 must succeed and increment tokenVersion")
        void lockActiveAdmin_WhenCountGreaterThan1_Succeeds() {
            when(userRepository.findById(OTHER_ADMIN_ID)).thenReturn(Optional.of(otherAdmin));
            when(userRepository.countByRoleAssignmentsRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE)).thenReturn(2L);
            when(userRepository.save(any(User.class))).thenReturn(otherAdmin);
            when(userRepository.incrementTokenVersion(OTHER_ADMIN_ID)).thenReturn(1L);
            when(userMapper.toResponse(any(User.class))).thenReturn(mockResponse);

            UpdateUserStatusRequest lockRequest = new UpdateUserStatusRequest(UserStatus.LOCKED);

            UserResponse response = userService.updateStatus(OTHER_ADMIN_ID, lockRequest, CURRENT_ADMIN_ID);

            assertThat(response).isNotNull();
            verify(userRepository).incrementTokenVersion(OTHER_ADMIN_ID);
        }

        @Test
        @DisplayName("Challenger 3A.3: Locking already LOCKED ADMIN when active count == 1 does not trigger Guard 3A")
        void lockAlreadyLockedAdmin_WhenCountIs1_DoesNotTriggerGuard3A() {
            otherAdmin.setStatus(UserStatus.LOCKED);
            when(userRepository.findById(OTHER_ADMIN_ID)).thenReturn(Optional.of(otherAdmin));
            when(userRepository.save(any(User.class))).thenReturn(otherAdmin);
            when(userRepository.incrementTokenVersion(OTHER_ADMIN_ID)).thenReturn(1L);
            when(userMapper.toResponse(any(User.class))).thenReturn(mockResponse);

            UpdateUserStatusRequest lockRequest = new UpdateUserStatusRequest(UserStatus.LOCKED);

            UserResponse response = userService.updateStatus(OTHER_ADMIN_ID, lockRequest, CURRENT_ADMIN_ID);

            assertThat(response).isNotNull();
            verify(userRepository, never()).countByRoleAssignmentsRoleAndStatus(any(), any());
            verify(userRepository).incrementTokenVersion(OTHER_ADMIN_ID);
        }

        @Test
        @DisplayName("Challenger 3A.4: Locking non-admin user when active admin count == 1 is permitted")
        void lockNonAdmin_WhenActiveAdminCountIs1_Succeeds() {
            when(userRepository.findById(NON_ADMIN_ID)).thenReturn(Optional.of(nonAdminUser));
            when(userRepository.save(any(User.class))).thenReturn(nonAdminUser);
            when(userRepository.incrementTokenVersion(NON_ADMIN_ID)).thenReturn(1L);
            when(userMapper.toResponse(any(User.class))).thenReturn(mockResponse);

            UpdateUserStatusRequest lockRequest = new UpdateUserStatusRequest(UserStatus.LOCKED);

            UserResponse response = userService.updateStatus(NON_ADMIN_ID, lockRequest, CURRENT_ADMIN_ID);

            assertThat(response).isNotNull();
            verify(userRepository, never()).countByRoleAssignmentsRoleAndStatus(any(), any());
            verify(userRepository).incrementTokenVersion(NON_ADMIN_ID);
        }
    }

    // =========================================================================
    // GUARD 3B: DEMOTING LAST ACTIVE ADMIN PREVENTION
    // =========================================================================
    @Nested
    @DisplayName("Guard 3B Empirical Challenge: Demote Last Active Admin Prevention")
    class DemoteLastActiveAdminGuardTests {

        @ParameterizedTest(name = "activeAdminCount = {0} must block demoting other active admin with HTTP 422")
        @ValueSource(longs = {0L, 1L})
        @DisplayName("Challenger 3B.1: Demoting other active ADMIN when active count <= 1 must throw HTTP 422")
        void demoteActiveAdmin_WhenCountLessThanOrEqual1_MustThrow422(long activeAdminCount) {
            when(userRepository.findById(OTHER_ADMIN_ID)).thenReturn(Optional.of(otherAdmin));
            doNothing().when(scopeValidationService).validateRoleAssignments(any());
            when(userRepository.countByRoleAssignmentsRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE)).thenReturn(activeAdminCount);

            UpdateUserRequest demotionRequest = UpdateUserRequest.builder()
                    .email("other_admin@hcmute.edu.vn")
                    .fullName("Other Admin")
                    .roleAssignments(List.of(RoleAssignment.builder().role(Role.LECTURER).build()))
                    .build();

            assertThatThrownBy(() -> userService.updateUser(OTHER_ADMIN_ID, demotionRequest, CURRENT_ADMIN_ID))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("ADMIN_GUARD")
                    .hasMessageContaining("duy nhất còn lại trong hệ thống");

            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("Challenger 3B.2: Demoting other active ADMIN when active count >= 2 must succeed")
        void demoteActiveAdmin_WhenCountGreaterThan1_Succeeds() {
            when(userRepository.findById(OTHER_ADMIN_ID)).thenReturn(Optional.of(otherAdmin));
            doNothing().when(scopeValidationService).validateRoleAssignments(any());
            when(userRepository.countByRoleAssignmentsRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE)).thenReturn(2L);
            when(userRepository.save(any(User.class))).thenReturn(otherAdmin);
            when(userMapper.toResponse(any(User.class))).thenReturn(mockResponse);

            UpdateUserRequest demotionRequest = UpdateUserRequest.builder()
                    .email("other_admin@hcmute.edu.vn")
                    .fullName("Other Admin")
                    .roleAssignments(List.of(RoleAssignment.builder().role(Role.LECTURER).build()))
                    .build();

            UserResponse response = userService.updateUser(OTHER_ADMIN_ID, demotionRequest, CURRENT_ADMIN_ID);

            assertThat(response).isNotNull();
            verify(userRepository).save(otherAdmin);
        }

        @Test
        @DisplayName("Challenger 3B.3: Demoting already LOCKED ADMIN when active count == 1 does not trigger Guard 3B")
        void demoteAlreadyLockedAdmin_WhenCountIs1_Succeeds() {
            otherAdmin.setStatus(UserStatus.LOCKED);
            when(userRepository.findById(OTHER_ADMIN_ID)).thenReturn(Optional.of(otherAdmin));
            doNothing().when(scopeValidationService).validateRoleAssignments(any());
            when(userRepository.save(any(User.class))).thenReturn(otherAdmin);
            when(userMapper.toResponse(any(User.class))).thenReturn(mockResponse);

            UpdateUserRequest demotionRequest = UpdateUserRequest.builder()
                    .email("other_admin@hcmute.edu.vn")
                    .fullName("Other Admin")
                    .roleAssignments(List.of(RoleAssignment.builder().role(Role.LECTURER).build()))
                    .build();

            UserResponse response = userService.updateUser(OTHER_ADMIN_ID, demotionRequest, CURRENT_ADMIN_ID);

            assertThat(response).isNotNull();
            verify(userRepository, never()).countByRoleAssignmentsRoleAndStatus(any(), any());
            verify(userRepository).save(otherAdmin);
        }

        @Test
        @DisplayName("Challenger 3B.4: Updating non-admin user when active admin count == 1 does not check Guard 3B")
        void updateNonAdmin_WhenActiveCountIs1_Succeeds() {
            when(userRepository.findById(NON_ADMIN_ID)).thenReturn(Optional.of(nonAdminUser));
            doNothing().when(scopeValidationService).validateRoleAssignments(any());
            when(userRepository.save(any(User.class))).thenReturn(nonAdminUser);
            when(userMapper.toResponse(any(User.class))).thenReturn(mockResponse);

            UpdateUserRequest updateRequest = UpdateUserRequest.builder()
                    .email("lecturer@hcmute.edu.vn")
                    .fullName("Lecturer Updated")
                    .roleAssignments(List.of(RoleAssignment.builder().role(Role.STUDENT).build()))
                    .build();

            UserResponse response = userService.updateUser(NON_ADMIN_ID, updateRequest, CURRENT_ADMIN_ID);

            assertThat(response).isNotNull();
            verify(userRepository, never()).countByRoleAssignmentsRoleAndStatus(any(), any());
            verify(userRepository).save(nonAdminUser);
        }
    }
}
