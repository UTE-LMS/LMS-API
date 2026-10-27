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

@DisplayName("Empirical Challenge: PasswordPolicyValidator UTF-8 Edge & Stress Tests")
public class PasswordPolicyValidatorEmpiricalChallengeTest {

    private PasswordPolicyValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PasswordPolicyValidator();
    }

    @Nested
    @DisplayName("1. Exact Boundary Tests (7, 8, 72, 73 bytes)")
    class ExactBoundaryTests {

        @Test
        @DisplayName("Boundary: Exactly 7 ASCII bytes (1 byte below minimum) must be rejected")
        void testExact7Bytes() {
            String pwd7 = "1234567";
            assertThat(pwd7.getBytes(StandardCharsets.UTF_8).length).isEqualTo(7);
            assertThat(validator.isValid(pwd7)).isFalse();
            assertThat(validator.getByteLength(pwd7)).isEqualTo(7);

            assertThatThrownBy(() -> validator.validate(pwd7))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("PASSWORD_POLICY")
                    .hasMessageContaining("7 bytes");
        }

        @Test
        @DisplayName("Boundary: Exactly 8 ASCII bytes (minimum valid boundary) must be accepted")
        void testExact8Bytes() {
            String pwd8 = "12345678";
            assertThat(pwd8.getBytes(StandardCharsets.UTF_8).length).isEqualTo(8);
            assertThat(validator.isValid(pwd8)).isTrue();
            assertThat(validator.getByteLength(pwd8)).isEqualTo(8);
            validator.validate(pwd8); // must not throw
        }

        @Test
        @DisplayName("Boundary: Exactly 72 ASCII bytes (maximum valid boundary) must be accepted")
        void testExact72Bytes() {
            String pwd72 = "K".repeat(72);
            assertThat(pwd72.getBytes(StandardCharsets.UTF_8).length).isEqualTo(72);
            assertThat(validator.isValid(pwd72)).isTrue();
            assertThat(validator.getByteLength(pwd72)).isEqualTo(72);
            validator.validate(pwd72); // must not throw
        }

        @Test
        @DisplayName("Boundary: Exactly 73 ASCII bytes (1 byte above BCrypt limit) must be rejected")
        void testExact73Bytes() {
            String pwd73 = "K".repeat(73);
            assertThat(pwd73.getBytes(StandardCharsets.UTF_8).length).isEqualTo(73);
            assertThat(validator.isValid(pwd73)).isFalse();
            assertThat(validator.getByteLength(pwd73)).isEqualTo(73);

            assertThatThrownBy(() -> validator.validate(pwd73))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("PASSWORD_POLICY")
                    .hasMessageContaining("73 bytes");
        }
    }

    @Nested
    @DisplayName("2. Vietnamese Multi-byte UTF-8 Characters (2-byte and 3-byte)")
    class VietnameseMultiByteChallengeTests {

        @Test
        @DisplayName("2-byte character 'ơ' (U+01A1): 3 characters = 6 bytes (< 8) rejected; 4 characters = 8 bytes accepted")
        void testVietnamese2ByteBoundaries() {
            // 'ơ' is 2 bytes in UTF-8
            String threeO = "ơơơ";
            assertThat(threeO.getBytes(StandardCharsets.UTF_8).length).isEqualTo(6);
            assertThat(validator.isValid(threeO)).isFalse();

            String fourO = "ơơơơ";
            assertThat(fourO.length()).isEqualTo(4); // Only 4 characters in Java!
            assertThat(fourO.getBytes(StandardCharsets.UTF_8).length).isEqualTo(8);
            assertThat(validator.isValid(fourO)).isTrue();
            validator.validate(fourO);

            // Exactly 36 'ơ' chars = 72 bytes -> valid
            String maxO = "ơ".repeat(36);
            assertThat(maxO.getBytes(StandardCharsets.UTF_8).length).isEqualTo(72);
            assertThat(validator.isValid(maxO)).isTrue();
            validator.validate(maxO);

            // 37 'ơ' chars = 74 bytes -> invalid
            String overflowO = "ơ".repeat(37);
            assertThat(overflowO.getBytes(StandardCharsets.UTF_8).length).isEqualTo(74);
            assertThat(validator.isValid(overflowO)).isFalse();
            assertThatThrownBy(() -> validator.validate(overflowO))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("74 bytes");
        }

        @Test
        @DisplayName("3-byte character 'ế' (U+1E7F): 2 chars + 1 ASCII = 7 bytes rejected; 2 chars + 2 ASCII = 8 bytes accepted")
        void testVietnamese3ByteBoundaries() {
            // 'ế' is 3 bytes in UTF-8
            String sevenBytes = "ếếa"; // 3 + 3 + 1 = 7 bytes
            assertThat(sevenBytes.getBytes(StandardCharsets.UTF_8).length).isEqualTo(7);
            assertThat(validator.isValid(sevenBytes)).isFalse();

            String eightBytes = "ếếab"; // 3 + 3 + 1 + 1 = 8 bytes (4 characters)
            assertThat(eightBytes.getBytes(StandardCharsets.UTF_8).length).isEqualTo(8);
            assertThat(validator.isValid(eightBytes)).isTrue();
            validator.validate(eightBytes);

            // Exactly 24 'ế' chars = 72 bytes -> valid
            String maxE = "ế".repeat(24);
            assertThat(maxE.length()).isEqualTo(24);
            assertThat(maxE.getBytes(StandardCharsets.UTF_8).length).isEqualTo(72);
            assertThat(validator.isValid(maxE)).isTrue();
            validator.validate(maxE);

            // 24 'ế' chars + 1 ASCII 'a' = 73 bytes -> invalid
            String overflow73 = "ế".repeat(24) + "a";
            assertThat(overflow73.getBytes(StandardCharsets.UTF_8).length).isEqualTo(73);
            assertThat(validator.isValid(overflow73)).isFalse();
            assertThatThrownBy(() -> validator.validate(overflow73))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("73 bytes");

            // 25 'ế' chars = 75 bytes -> invalid
            String overflowE = "ế".repeat(25);
            assertThat(overflowE.getBytes(StandardCharsets.UTF_8).length).isEqualTo(75);
            assertThat(validator.isValid(overflowE)).isFalse();
        }
    }

    @Nested
    @DisplayName("3. 4-Byte Emoji & Compound Unicode Characters")
    class Emoji4ByteTests {

        @Test
        @DisplayName("Single 4-byte emoji (4 bytes) is rejected (< 8 bytes)")
        void testSingleEmojiRejected() {
            String singleEmoji = "\uD83D\uDE00"; // 😀 (U+1F600)
            assertThat(singleEmoji.getBytes(StandardCharsets.UTF_8).length).isEqualTo(4);
            assertThat(validator.isValid(singleEmoji)).isFalse();
            assertThat(validator.getByteLength(singleEmoji)).isEqualTo(4);

            assertThatThrownBy(() -> validator.validate(singleEmoji))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("4 bytes");
        }

        @Test
        @DisplayName("1 emoji + 3 ASCII characters = 7 bytes (rejected)")
        void testEmoji7BytesRejected() {
            String emoji7 = "\uD83D\uDE00abc"; // 4 + 3 = 7 bytes
            assertThat(emoji7.getBytes(StandardCharsets.UTF_8).length).isEqualTo(7);
            assertThat(validator.isValid(emoji7)).isFalse();
        }

        @Test
        @DisplayName("2 emojis (2 x 4 = 8 bytes) is valid at the minimum boundary")
        void testTwoEmojisAccepted() {
            String twoEmojis = "\uD83D\uDE00\uD83D\uDE80"; // 😀🚀 = 8 bytes
            assertThat(twoEmojis.getBytes(StandardCharsets.UTF_8).length).isEqualTo(8);
            assertThat(validator.isValid(twoEmojis)).isTrue();
            validator.validate(twoEmojis);
        }

        @Test
        @DisplayName("Vietnam flag emoji 🇻🇳 (2 regional indicator symbols = 8 bytes) is valid")
        void testFlagEmojiAccepted() {
            String vietnamFlag = "\uD83C\uDDFB\uD83C\uDDF3"; // 🇻🇳
            assertThat(vietnamFlag.getBytes(StandardCharsets.UTF_8).length).isEqualTo(8);
            assertThat(validator.isValid(vietnamFlag)).isTrue();
            validator.validate(vietnamFlag);
        }

        @Test
        @DisplayName("18 emojis (18 x 4 = 72 bytes) is valid at the maximum boundary")
        void test18EmojisAccepted() {
            String emoji18 = "\uD83D\uDE00".repeat(18); // 18 * 4 = 72 bytes
            assertThat(emoji18.getBytes(StandardCharsets.UTF_8).length).isEqualTo(72);
            assertThat(validator.isValid(emoji18)).isTrue();
            validator.validate(emoji18);
        }

        @Test
        @DisplayName("18 emojis + 1 ASCII char (72 + 1 = 73 bytes) is rejected")
        void test18EmojisPlusOneRejected() {
            String emoji73 = "\uD83D\uDE00".repeat(18) + "!";
            assertThat(emoji73.getBytes(StandardCharsets.UTF_8).length).isEqualTo(73);
            assertThat(validator.isValid(emoji73)).isFalse();
            assertThatThrownBy(() -> validator.validate(emoji73))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("73 bytes");
        }

        @Test
        @DisplayName("19 emojis (19 x 4 = 76 bytes) is rejected")
        void test19EmojisRejected() {
            String emoji19 = "\uD83D\uDE00".repeat(19);
            assertThat(emoji19.getBytes(StandardCharsets.UTF_8).length).isEqualTo(76);
            assertThat(validator.isValid(emoji19)).isFalse();
        }
    }

    @Nested
    @DisplayName("4. Mixed Heterogeneous Strings (ASCII + 2-byte + 3-byte + 4-byte)")
    class MixedHeterogeneousTests {

        @Test
        @DisplayName("Mixed characters totaling exactly 8 bytes is valid")
        void testMixedExact8Bytes() {
            // 'A'(1) + 'b'(1) + 'ơ'(2) + 'ế'(3) + 'c'(1) = 8 bytes
            String mixed8 = "Abơếc";
            assertThat(mixed8.getBytes(StandardCharsets.UTF_8).length).isEqualTo(8);
            assertThat(validator.isValid(mixed8)).isTrue();
            validator.validate(mixed8);
        }

        @Test
        @DisplayName("Mixed characters totaling exactly 72 bytes is valid")
        void testMixedExact72Bytes() {
            // 17 emojis (68 bytes) + 'ơ'(2) + 'a'(1) + 'b'(1) = 72 bytes
            String mixed72 = "\uD83D\uDE00".repeat(17) + "ơab";
            assertThat(mixed72.getBytes(StandardCharsets.UTF_8).length).isEqualTo(72);
            assertThat(validator.isValid(mixed72)).isTrue();
            validator.validate(mixed72);
        }

        @Test
        @DisplayName("Mixed characters totaling 73 bytes is rejected")
        void testMixedExact73Bytes() {
            // 17 emojis (68 bytes) + 'ế'(3) + 'a'(1) + 'b'(1) = 73 bytes
            String mixed73 = "\uD83D\uDE00".repeat(17) + "ếab";
            assertThat(mixed73.getBytes(StandardCharsets.UTF_8).length).isEqualTo(73);
            assertThat(validator.isValid(mixed73)).isFalse();
            assertThatThrownBy(() -> validator.validate(mixed73))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("73 bytes");
        }
    }

    @Nested
    @DisplayName("5. Null, Empty, Whitespace & Error Formatting")
    class NullEmptyWhitespaceTests {

        @Test
        @DisplayName("null input returns false, byte length 0, and throws exception with 0 bytes")
        void testNullHandling() {
            assertThat(validator.isValid(null)).isFalse();
            assertThat(validator.getByteLength(null)).isEqualTo(0);

            assertThatThrownBy(() -> validator.validate(null))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("PASSWORD_POLICY")
                    .hasMessageContaining("0 bytes");
        }

        @Test
        @DisplayName("Empty string returns false, byte length 0, and throws exception with 0 bytes")
        void testEmptyHandling() {
            assertThat(validator.isValid("")).isFalse();
            assertThat(validator.getByteLength("")).isEqualTo(0);

            assertThatThrownBy(() -> validator.validate(""))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("PASSWORD_POLICY")
                    .hasMessageContaining("0 bytes");
        }

        @ParameterizedTest(name = "Whitespace string of length {0}")
        @ValueSource(ints = {1, 2, 6, 7})
        void testUnder8WhitespaceRejected(int len) {
            String spaces = " ".repeat(len);
            assertThat(validator.isValid(spaces)).isFalse();
        }

        @Test
        @DisplayName("Whitespace string of 8 spaces has 8 UTF-8 bytes and is accepted by length validator")
        void test8SpacesAccepted() {
            String spaces8 = " ".repeat(8);
            assertThat(validator.isValid(spaces8)).isTrue();
            assertThat(validator.getByteLength(spaces8)).isEqualTo(8);
        }

        @Test
        @DisplayName("Whitespace string of 73 spaces has 73 UTF-8 bytes and is rejected")
        void test73SpacesRejected() {
            String spaces73 = " ".repeat(73);
            assertThat(validator.isValid(spaces73)).isFalse();
        }
    }
}
