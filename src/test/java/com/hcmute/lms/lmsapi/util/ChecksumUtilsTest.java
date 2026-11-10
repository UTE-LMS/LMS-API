package com.hcmute.lms.lmsapi.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("US-21/AC-3: ChecksumUtils băm SHA-256 để chặn nhập trùng")
class ChecksumUtilsTest {

  @Test
  @DisplayName("US-21/AC-3: khớp vector kiểm thử chuẩn NIST của SHA-256 (\"abc\" và chuỗi rỗng)")
  void matchesPublishedSha256Vectors() {
    // Hai giá trị kỳ vọng lấy từ vector chuẩn FIPS 180-4 / NIST, không tính bằng chính mã này.
    assertThat(ChecksumUtils.sha256Hex("abc".getBytes(StandardCharsets.UTF_8)))
        .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    assertThat(ChecksumUtils.sha256Hex(new byte[0]))
        .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
  }

  @Test
  @DisplayName("US-21/AC-3: đổi một byte thì băm khác")
  void oneByteChangesTheDigest() {
    assertThat(ChecksumUtils.sha256Hex(new byte[] {1, 2, 3}))
        .isNotEqualTo(ChecksumUtils.sha256Hex(new byte[] {1, 2, 4}))
        .hasSize(64);
  }
}
