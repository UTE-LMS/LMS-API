package com.hcmute.lms.lmsapi.config;

import com.hcmute.lms.lmsapi.document.Role;
import com.hcmute.lms.lmsapi.document.User;
import com.hcmute.lms.lmsapi.document.UserStatus;
import com.hcmute.lms.lmsapi.repository.UserRepository;
import com.hcmute.lms.lmsapi.validator.PasswordPolicyValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InitialAdminSeeder: Unit Tests (Mockito, Zero Database Wipe)")
public class InitialAdminSeederTest {

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
    @DisplayName("When users collection is not empty, seeder skips creation")
    void testWhenUsersExist_SeederDoesNothing() {
        when(userRepository.count()).thenReturn(2L);

        seeder.run(null);

        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    @DisplayName("When users collection is empty and environment variables are empty, seeder logs WARN and does not save")
    void testWhenUsersEmpty_AndEnvVarsMissing_SeederDoesNotSave() {
        when(userRepository.count()).thenReturn(0L);
        ReflectionTestUtils.setField(seeder, "initialAdminUsername", "");
        ReflectionTestUtils.setField(seeder, "initialAdminEmail", "");
        ReflectionTestUtils.setField(seeder, "initialAdminPassword", "");

        seeder.run(null);

        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    @DisplayName("When users collection is empty but partial env vars provided, seeder does not save")
    void testWhenUsersEmpty_AndPartialEnvVars_SeederDoesNotSave() {
        when(userRepository.count()).thenReturn(0L);
        ReflectionTestUtils.setField(seeder, "initialAdminUsername", "admin");
        ReflectionTestUtils.setField(seeder, "initialAdminEmail", "");
        ReflectionTestUtils.setField(seeder, "initialAdminPassword", "secret12345");

        seeder.run(null);

        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    @DisplayName("When users collection is empty and password violates policy (< 8 bytes), seeder logs ERROR and does not save")
    void testWhenPasswordViolatesPolicy_SeederDoesNotSave() {
        when(userRepository.count()).thenReturn(0L);
        ReflectionTestUtils.setField(seeder, "initialAdminUsername", "admin");
        ReflectionTestUtils.setField(seeder, "initialAdminEmail", "admin@hcmute.edu.vn");
        ReflectionTestUtils.setField(seeder, "initialAdminPassword", "12345");

        when(passwordPolicyValidator.isValid("12345")).thenReturn(false);
        when(passwordPolicyValidator.getByteLength("12345")).thenReturn(5);

        seeder.run(null);

        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    @DisplayName("When users collection is empty and valid credentials provided, seeder creates ACTIVE ADMIN user with tokenVersion 0")
    void testWhenValidCredentialsProvided_SeederCreatesAdmin() {
        when(userRepository.count()).thenReturn(0L);
        ReflectionTestUtils.setField(seeder, "initialAdminUsername", "  Admin  ");
        ReflectionTestUtils.setField(seeder, "initialAdminEmail", "  Admin@HCMUTE.EDU.VN  ");
        ReflectionTestUtils.setField(seeder, "initialAdminPassword", "P@ssw0rd!2026");

        when(passwordPolicyValidator.isValid("P@ssw0rd!2026")).thenReturn(true);
        when(passwordEncoder.encode("P@ssw0rd!2026")).thenReturn("$2a$10$hashedDummyBCryptPasswordString123");

        seeder.run(null);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User created = userCaptor.getValue();
        assertThat(created).isNotNull();
        assertThat(created.getUsername()).isEqualTo("admin"); // Trimmed & lowercase
        assertThat(created.getEmail()).isEqualTo("admin@hcmute.edu.vn"); // Trimmed & lowercase
        assertThat(created.getPasswordHash()).isEqualTo("$2a$10$hashedDummyBCryptPasswordString123");
        assertThat(created.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(created.getTokenVersion()).isEqualTo(0L);
        assertThat(created.getFullName()).isEqualTo("System Administrator");
        assertThat(created.getRoleAssignments()).hasSize(1);
        assertThat(created.getRoleAssignments().get(0).getRole()).isEqualTo(Role.ADMIN);
        assertThat(created.getRoleAssignments().get(0).getScope()).isNull();
        assertThat(created.getCreatedAt()).isNotNull();
        assertThat(created.getUpdatedAt()).isNotNull();
    }
}
