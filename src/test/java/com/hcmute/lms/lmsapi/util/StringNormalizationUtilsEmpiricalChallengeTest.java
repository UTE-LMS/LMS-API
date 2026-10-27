package com.hcmute.lms.lmsapi.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Empirical Challenge: StringNormalizationUtils Edge & Adversarial Tests")
public class StringNormalizationUtilsEmpiricalChallengeTest {

    @Nested
    @DisplayName("1. Null and Empty String Edge Cases")
    class NullAndEmptyTests {

        @Test
        @DisplayName("null input returns null across all normalization methods")
        void testNullHandling() {
            assertThat(StringNormalizationUtils.trimAndLowerCase(null)).isNull();
            assertThat(StringNormalizationUtils.normalizeUsername(null)).isNull();
            assertThat(StringNormalizationUtils.normalizeEmail(null)).isNull();
        }

        @Test
        @DisplayName("Empty string '' returns empty string ''")
        void testEmptyString() {
            assertThat(StringNormalizationUtils.trimAndLowerCase("")).isEmpty();
            assertThat(StringNormalizationUtils.normalizeUsername("")).isEmpty();
            assertThat(StringNormalizationUtils.normalizeEmail("")).isEmpty();
        }

        @ParameterizedTest(name = "Whitespace-only string: ''{0}''")
        @ValueSource(strings = {
                " ",
                "    ",
                "\t",
                "\t\t\t",
                "\n",
                "\r\n",
                " \t \r\n \t "
        })
        @DisplayName("Strings containing only whitespaces, tabs, newlines normalize to empty string")
        void testWhitespaceOnlyStrings(String whitespaceOnly) {
            assertThat(StringNormalizationUtils.trimAndLowerCase(whitespaceOnly)).isEmpty();
            assertThat(StringNormalizationUtils.normalizeUsername(whitespaceOnly)).isEmpty();
            assertThat(StringNormalizationUtils.normalizeEmail(whitespaceOnly)).isEmpty();
        }
    }

    @Nested
    @DisplayName("2. Leading, Trailing Whitespace, Tabs, and Newlines Trimming")
    class WhitespaceAndTabsTrimmingTests {

        @Test
        @DisplayName("Trims mixed leading and trailing spaces, tabs, newlines")
        void testMixedWhitespaceTrimming() {
            String rawUsername = "\r\n\t  TinMH  \t\r\n";
            assertThat(StringNormalizationUtils.normalizeUsername(rawUsername)).isEqualTo("tinmh");

            String rawEmail = "\t  admin@hcmute.edu.vn  \r\n";
            assertThat(StringNormalizationUtils.normalizeEmail(rawEmail)).isEqualTo("admin@hcmute.edu.vn");
        }

        @Test
        @DisplayName("Preserves internal spaces and internal characters")
        void testInternalSpacesPreserved() {
            String withInternalSpaces = "  John   Doe  ";
            assertThat(StringNormalizationUtils.trimAndLowerCase(withInternalSpaces)).isEqualTo("john   doe");
        }

        @Test
        @DisplayName("Preserves internal tabs and punctuation")
        void testInternalTabsPreserved() {
            String withInternalTab = "\tuser\tname\t";
            assertThat(StringNormalizationUtils.normalizeUsername(withInternalTab)).isEqualTo("user\tname");
        }
    }

    @Nested
    @DisplayName("3. Case Conversion & Locale Robustness (Locale.ROOT)")
    class CaseConversionAndLocaleTests {

        @Test
        @DisplayName("All uppercase characters converted to lowercase")
        void testUppercaseConversion() {
            assertThat(StringNormalizationUtils.normalizeUsername("ADMIN")).isEqualTo("admin");
            assertThat(StringNormalizationUtils.normalizeEmail("STUDENT_QA@HCMUTE.EDU.VN"))
                    .isEqualTo("student_qa@hcmute.edu.vn");
        }

        @Test
        @DisplayName("Turkish I edge case: 'I' must normalize to 'i', never to dotless 'ı'")
        void testTurkishIProblem() {
            // In Turkish locale, "I".toLowerCase() becomes "\u0131" (dotless ı).
            // Using Locale.ROOT guarantees "I".toLowerCase(Locale.ROOT) produces standard "i" (ASCII 105).
            String input = "INFO_ID";
            String normalized = StringNormalizationUtils.trimAndLowerCase(input);
            assertThat(normalized).isEqualTo("info_id");
            assertThat(normalized.charAt(0)).isEqualTo('i');
            assertThat(normalized.charAt(5)).isEqualTo('i');
            assertThat(normalized).doesNotContain("\u0131"); // ensure no dotless ı
        }

        @Test
        @DisplayName("Vietnamese accented uppercase characters converted to lowercase properly")
        void testVietnameseCaseConversion() {
            String vnUpper = "  NGUYỄN VĂN A  ";
            assertThat(StringNormalizationUtils.trimAndLowerCase(vnUpper)).isEqualTo("nguyễn văn a");

            String vnChars = "ĐÂÊÔƠƯ";
            assertThat(StringNormalizationUtils.trimAndLowerCase(vnChars)).isEqualTo("đâêôơư");
        }
    }

    @Nested
    @DisplayName("4. Architectural & Class Invariant Tests")
    class ArchitectureTests {

        @Test
        @DisplayName("Utility class constructor is private and throws UnsupportedOperationException or is inaccessible")
        void testPrivateConstructor() throws Exception {
            Constructor<StringNormalizationUtils> constructor = StringNormalizationUtils.class.getDeclaredConstructor();
            assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();
            constructor.setAccessible(true);
            // Instantiating via reflection should succeed or throw
            try {
                StringNormalizationUtils instance = constructor.newInstance();
                assertThat(instance).isNotNull();
            } catch (InvocationTargetException e) {
                // Also acceptable if constructor throws
                assertThat(e.getCause()).isNotNull();
            }
        }
    }
}
