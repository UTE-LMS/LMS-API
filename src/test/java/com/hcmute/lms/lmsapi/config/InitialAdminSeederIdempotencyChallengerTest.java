package com.hcmute.lms.lmsapi.config;

import com.hcmute.lms.lmsapi.enums.Role;
import com.hcmute.lms.lmsapi.document.User;
import com.hcmute.lms.lmsapi.enums.UserStatus;
import com.hcmute.lms.lmsapi.repository.UserRepository;
import com.hcmute.lms.lmsapi.validator.PasswordPolicyValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Empirical Challenger: Idempotency, repeated execution, and edge case challenge
 * for InitialAdminSeeder.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Empirical Challenge: InitialAdminSeeder Idempotency & Edge Cases")
public class InitialAdminSeederIdempotencyChallengerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PasswordPolicyValidator passwordPolicyValidator;

    private InitialAdminSeeder seeder;

    @BeforeEach
    void setUp() {
        seeder = new InitialAdminSeeder(userRepository, passwordEncoder, passwordPolicyValidator);
    }

    @Test
    @DisplayName("Idempotency: When users collection is non-empty, calling run() 20 times never saves any user")
    void testRepeatedExecutions_WhenUsersExist_NeverSaves() {
        when(userRepository.count()).thenReturn(3L);

        for (int i = 0; i < 20; i++) {
            seeder.run(null);
        }

        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    @DisplayName("State Transition Idempotency: Sequential startup where first call seeds and subsequent calls skip")
    void testSequentialStartup_SeedsExactlyOnce() {
        ReflectionTestUtils.setField(seeder, "initialAdminUsername", "system_admin");
        ReflectionTestUtils.setField(seeder, "initialAdminEmail", "admin@hcmute.edu.vn");
        ReflectionTestUtils.setField(seeder, "initialAdminPassword", "SecureP@ssw0rd2026");

        when(passwordPolicyValidator.isValid(anyString())).thenReturn(true);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashedAdminPasswordSample");

        // Mô phỏng trạng thái CSDL:
        // Lần 1: count() = 0 (CSDL rỗng)
        // Lần 2, 3, 4, 5: count() = 1 (đã có admin được tạo)
        AtomicLong dynamicCount = new AtomicLong(0L);
        when(userRepository.count()).thenAnswer(invocation -> dynamicCount.get());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            dynamicCount.incrementAndGet();
            return invocation.getArgument(0);
        });

        // 5 lần khởi chạy seeder liên tiếp
        for (int i = 0; i < 5; i++) {
            seeder.run(null);
        }

        // Kiểm chứng: save() chỉ được gọi ĐÚNG 1 LẦN duy nhất
        verify(userRepository, times(1)).save(any(User.class));
        verify(passwordEncoder, times(1)).encode("SecureP@ssw0rd2026");
        assertThat(dynamicCount.get()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Concurrency Defense: DuplicateKeyException is raised if two instances attempt concurrent seed")
    void testConcurrentSeed_DuplicateKeyExceptionOnSecondSave() {
        ReflectionTestUtils.setField(seeder, "initialAdminUsername", "admin");
        ReflectionTestUtils.setField(seeder, "initialAdminEmail", "admin@hcmute.edu.vn");
        ReflectionTestUtils.setField(seeder, "initialAdminPassword", "ValidPassword123");

        when(userRepository.count()).thenReturn(0L);
        when(passwordPolicyValidator.isValid(anyString())).thenReturn(true);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$dummyHash");

        // Mô phỏng trường hợp cả 2 node đều thấy count == 0, nhưng node 2 save sau khi node 1 đã commit -> DuplicateKeyException
        when(userRepository.save(any(User.class)))
                .thenThrow(new DuplicateKeyException("E11000 duplicate key error collection: users index: username_unique_idx dup key: { username: \"admin\" }"));

        assertThatThrownBy(() -> seeder.run(null))
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("E11000 duplicate key error");
    }

    @Test
    @DisplayName("Edge Case: Missing or whitespace environment variables never trigger save")
    void testMissingOrWhitespaceEnvVars_NeverSaves() {
        when(userRepository.count()).thenReturn(0L);

        // Case 1: Nulls
        ReflectionTestUtils.setField(seeder, "initialAdminUsername", null);
        ReflectionTestUtils.setField(seeder, "initialAdminEmail", null);
        ReflectionTestUtils.setField(seeder, "initialAdminPassword", null);
        seeder.run(null);

        // Case 2: Whitespace only
        ReflectionTestUtils.setField(seeder, "initialAdminUsername", "   ");
        ReflectionTestUtils.setField(seeder, "initialAdminEmail", "   ");
        ReflectionTestUtils.setField(seeder, "initialAdminPassword", "");
        seeder.run(null);

        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    @DisplayName("Edge Case: Password policy violation at boundaries prevents seeding")
    void testPasswordPolicyBoundaries_PreventsSeeding() {
        when(userRepository.count()).thenReturn(0L);
        ReflectionTestUtils.setField(seeder, "initialAdminUsername", "admin");
        ReflectionTestUtils.setField(seeder, "initialAdminEmail", "admin@hcmute.edu.vn");

        // 7 bytes password
        ReflectionTestUtils.setField(seeder, "initialAdminPassword", "1234567");
        when(passwordPolicyValidator.isValid("1234567")).thenReturn(false);
        when(passwordPolicyValidator.getByteLength("1234567")).thenReturn(7);
        seeder.run(null);

        // 73 bytes password
        String longPassword = "a".repeat(73);
        ReflectionTestUtils.setField(seeder, "initialAdminPassword", longPassword);
        when(passwordPolicyValidator.isValid(longPassword)).thenReturn(false);
        when(passwordPolicyValidator.getByteLength(longPassword)).thenReturn(73);
        seeder.run(null);

        // Vietnamese string with 30 characters but 90 bytes UTF-8
        String vietnamesePassword = "ệ".repeat(30);
        int byteLen = vietnamesePassword.getBytes(StandardCharsets.UTF_8).length;
        ReflectionTestUtils.setField(seeder, "initialAdminPassword", vietnamesePassword);
        when(passwordPolicyValidator.isValid(vietnamesePassword)).thenReturn(false);
        when(passwordPolicyValidator.getByteLength(vietnamesePassword)).thenReturn(byteLen);
        seeder.run(null);

        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    @DisplayName("Sanitization: Username and email are trimmed and converted to lowercase upon creation")
    void testNormalizationOfInitialAdminCredentials() {
        when(userRepository.count()).thenReturn(0L);
        ReflectionTestUtils.setField(seeder, "initialAdminUsername", "   SuperAdmin_HCMUTE   ");
        ReflectionTestUtils.setField(seeder, "initialAdminEmail", "   SuperAdmin@HCMUTE.EDU.VN   ");
        ReflectionTestUtils.setField(seeder, "initialAdminPassword", "ValidP@ssw0rd!2026");

        when(passwordPolicyValidator.isValid("ValidP@ssw0rd!2026")).thenReturn(true);
        when(passwordEncoder.encode("ValidP@ssw0rd!2026")).thenReturn("$2a$10$encodedSample");

        seeder.run(null);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User admin = userCaptor.getValue();
        assertThat(admin.getUsername()).isEqualTo("superadmin_hcmute");
        assertThat(admin.getEmail()).isEqualTo("superadmin@hcmute.edu.vn");
        assertThat(admin.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(admin.getTokenVersion()).isEqualTo(0L);
        assertThat(admin.getRoleAssignments()).hasSize(1);
        assertThat(admin.getRoleAssignments().get(0).getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.getRoleAssignments().get(0).getScope()).isNull();
    }
}
