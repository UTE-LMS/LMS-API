package com.hcmute.lms.lmsapi.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("StringNormalizationUtils: Username and Email Normalization Unit Tests")
public class StringNormalizationUtilsTest {

    @Test
    @DisplayName("trimAndLowerCase trims whitespace and converts to lower case")
    void testTrimAndLowerCase() {
        assertThat(StringNormalizationUtils.trimAndLowerCase("  Admin  ")).isEqualTo("admin");
        assertThat(StringNormalizationUtils.trimAndLowerCase("ADMIN@HCMUTE.EDU.VN")).isEqualTo("admin@hcmute.edu.vn");
        assertThat(StringNormalizationUtils.trimAndLowerCase(null)).isNull();
        assertThat(StringNormalizationUtils.trimAndLowerCase("")).isEmpty();
        assertThat(StringNormalizationUtils.trimAndLowerCase("   ")).isEmpty();
    }

    @Test
    @DisplayName("normalizeUsername trims and lowercases username")
    void testNormalizeUsername() {
        assertThat(StringNormalizationUtils.normalizeUsername("  tinmh  ")).isEqualTo("tinmh");
        assertThat(StringNormalizationUtils.normalizeUsername("TinMH")).isEqualTo("tinmh");
        assertThat(StringNormalizationUtils.normalizeUsername(null)).isNull();
    }

    @Test
    @DisplayName("normalizeEmail trims and lowercases email")
    void testNormalizeEmail() {
        assertThat(StringNormalizationUtils.normalizeEmail("  tinmh@hcmute.edu.vn  ")).isEqualTo("tinmh@hcmute.edu.vn");
        assertThat(StringNormalizationUtils.normalizeEmail("TinMH@HCMUTE.EDU.VN")).isEqualTo("tinmh@hcmute.edu.vn");
        assertThat(StringNormalizationUtils.normalizeEmail(null)).isNull();
    }
}
