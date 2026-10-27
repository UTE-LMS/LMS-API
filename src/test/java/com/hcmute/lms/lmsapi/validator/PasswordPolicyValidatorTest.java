package com.hcmute.lms.lmsapi.validator;

import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PasswordPolicyValidator: UTF-8 Byte Length Policy Unit Tests (8-72 Bytes)")
public class PasswordPolicyValidatorTest {

    private PasswordPolicyValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PasswordPolicyValidator();
    }

    @Nested
    @DisplayName("Validation for passwords shorter than 8 UTF-8 bytes")
    class TooShortPasswords {

        @Test
        @DisplayName("null and empty password must be rejected")
        void testNullAndEmpty() {
            assertThat(validator.isValid(null)).isFalse();
            assertThat(validator.isValid("")).isFalse();
            assertThat(validator.getByteLength(null)).isEqualTo(0);
            assertThat(validator.getByteLength("")).isEqualTo(0);

            assertThatThrownBy(() -> validator.validate(null))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("PASSWORD_POLICY");

            assertThatThrownBy(() -> validator.validate(""))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("PASSWORD_POLICY");
        }

        @ParameterizedTest(name = "Password ''{0}'' with byte length < 8 must be rejected")
        @ValueSource(strings = {"a", "ab", "12345", "1234567"})
        void testUnder8Bytes(String shortPassword) {
            assertThat(shortPassword.getBytes(StandardCharsets.UTF_8).length).isLessThan(8);
            assertThat(validator.isValid(shortPassword)).isFalse();

            assertThatThrownBy(() -> validator.validate(shortPassword))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("PASSWORD_POLICY");
        }
    }

    @Nested
    @DisplayName("Validation for valid passwords within 8 to 72 UTF-8 bytes")
    class ValidPasswords {

        @Test
        @DisplayName("Exact boundary: exactly 8 ASCII bytes is valid")
        void testExact8Bytes() {
            String password8Bytes = "12345678";
            assertThat(password8Bytes.getBytes(StandardCharsets.UTF_8).length).isEqualTo(8);
            assertThat(validator.isValid(password8Bytes)).isTrue();
            validator.validate(password8Bytes); // should not throw
        }

        @Test
        @DisplayName("Exact boundary: exactly 72 ASCII bytes is valid")
        void testExact72Bytes() {
            String password72Bytes = "a".repeat(72);
            assertThat(password72Bytes.getBytes(StandardCharsets.UTF_8).length).isEqualTo(72);
            assertThat(validator.isValid(password72Bytes)).isTrue();
            validator.validate(password72Bytes); // should not throw
        }

        @ParameterizedTest(name = "Password ''{0}'' within valid range")
        @ValueSource(strings = {"Admin@123", "StrongP@ssw0rd!2026", "secret_token_123456"})
        void testTypicalValidPasswords(String pwd) {
            assertThat(validator.isValid(pwd)).isTrue();
            validator.validate(pwd);
        }
    }

    @Nested
    @DisplayName("Validation for passwords exceeding 72 UTF-8 bytes")
    class TooLongPasswords {

        @Test
        @DisplayName("Exact boundary: 73 ASCII bytes must be rejected (BCrypt truncation prevention)")
        void testExact73Bytes() {
            String password73Bytes = "a".repeat(73);
            assertThat(password73Bytes.getBytes(StandardCharsets.UTF_8).length).isEqualTo(73);
            assertThat(validator.isValid(password73Bytes)).isFalse();

            assertThatThrownBy(() -> validator.validate(password73Bytes))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("PASSWORD_POLICY")
                    .hasMessageContaining("73 bytes");
        }

        @Test
        @DisplayName("100 bytes password must be rejected")
        void test100Bytes() {
            String password100Bytes = "x".repeat(100);
            assertThat(validator.isValid(password100Bytes)).isFalse();
            assertThatThrownBy(() -> validator.validate(password100Bytes))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }
    }

    @Nested
    @DisplayName("Vietnamese multi-byte UTF-8 character edge cases")
    class VietnameseMultiByteTests {

        @Test
        @DisplayName("Vietnamese password with 11 chars but 15 UTF-8 bytes is valid")
        void testVietnameseValidPassword() {
            String vnPassword = "MậtKhẩu@123";
            // 'M'(1) + 'ậ'(3) + 't'(1) + 'K'(1) + 'h'(1) + 'ẩ'(3) + 'u'(1) + '@'(1) + '1'(1) + '2'(1) + '3'(1) = 15 bytes
            int byteLength = vnPassword.getBytes(StandardCharsets.UTF_8).length;
            assertThat(vnPassword.length()).isEqualTo(11);
            assertThat(byteLength).isEqualTo(15);
            assertThat(validator.isValid(vnPassword)).isTrue();
            validator.validate(vnPassword);
        }

        @Test
        @DisplayName("Vietnamese password with 6 chars but 11 UTF-8 bytes satisfies >= 8 bytes policy")
        void testShortCharCountButValidByteLength() {
            // 'Đ'(2) + 'ạ'(3) + 'i'(1) + 'H'(1) + 'ọ'(3) + 'c'(1) = 11 bytes
            String shortVn = "ĐạiHọc";
            assertThat(shortVn.length()).isEqualTo(6); // Only 6 characters!
            assertThat(shortVn.getBytes(StandardCharsets.UTF_8).length).isEqualTo(11); // 11 bytes >= 8
            assertThat(validator.isValid(shortVn)).isTrue();
        }

        @Test
        @DisplayName("Vietnamese string of 30 characters exceeding 72 bytes must be rejected (BCrypt truncation vulnerability)")
        void testVietnameseExceeding72BytesDespiteLowCharCount() {
            // 'ệ' is 3 bytes in UTF-8
            // 30 characters of 'ệ' = 90 bytes!
            // @Size(max=72) would accept 30 characters, but BCrypt truncates at 72 bytes.
            // PasswordPolicyValidator correctly measures UTF-8 bytes and rejects it!
            String vnLong = "ệ".repeat(30);
            assertThat(vnLong.length()).isEqualTo(30);
            assertThat(vnLong.getBytes(StandardCharsets.UTF_8).length).isEqualTo(90);

            assertThat(validator.isValid(vnLong)).isFalse();
            assertThatThrownBy(() -> validator.validate(vnLong))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("90 bytes");
        }
    }
}
