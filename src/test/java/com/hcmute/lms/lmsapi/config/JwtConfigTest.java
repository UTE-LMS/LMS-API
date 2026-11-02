package com.hcmute.lms.lmsapi.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Kiểm thử đơn vị {@link JwtConfig} (F01 / ADR 0003): khóa tối thiểu 32 bytes, phân tích TTL, ký và
 * xác thực JWT HS256. Dữ liệu thử nghiệm: các khóa dưới đây là chuỗi giả, không dùng ở bất kỳ môi
 * trường thật nào.
 *
 * <p>Plan F01 mục "Xác thực" nêu các quy tắc này nhưng chưa gắn mã AC riêng nên tên test dùng nhãn
 * "US-01 (xác thực JWT, chưa có AC)". Đề xuất bổ sung AC cho F01.
 */
@DisplayName("JwtConfig Unit Tests (F01 / US-01, xác thực JWT HS256)")
class JwtConfigTest {

  private static final String LABEL = "US-01 (xác thực JWT, chưa có AC): ";
  private static final String TEST_SECRET_32_BYTES = "k".repeat(JwtConfig.MIN_SECRET_BYTES);
  private static final String OTHER_SECRET_32_BYTES = "z".repeat(JwtConfig.MIN_SECRET_BYTES);

  private static JwtConfig configWith(String secret, String ttl) {
    JwtConfig config = new JwtConfig();
    ReflectionTestUtils.setField(config, "secret", secret);
    ReflectionTestUtils.setField(config, "ttlString", ttl);
    ReflectionTestUtils.setField(config, "issuer", "lms-api-test");
    return config;
  }

  @Nested
  @DisplayName("validateSecret: fail-fast khi khóa JWT ngắn hơn 32 bytes")
  class ValidateSecretTests {

    @Test
    @DisplayName(LABEL + "khóa đúng 32 bytes (biên dưới) được chấp nhận")
    void exactlyMinimumBytes_isAccepted() {
      JwtConfig config = configWith(TEST_SECRET_32_BYTES, "60m");

      config.validateSecret();

      assertThat(config.getIssuer()).isEqualTo("lms-api-test");
    }

    @Test
    @DisplayName(LABEL + "khóa 31 bytes (ngay dưới biên) bị từ chối, thông báo nêu độ dài thật")
    void oneByteBelowMinimum_isRejected() {
      JwtConfig config = configWith("k".repeat(JwtConfig.MIN_SECRET_BYTES - 1), "60m");

      assertThatThrownBy(config::validateSecret)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("hiện tại: 31 bytes");
    }

    @Test
    @DisplayName(LABEL + "khóa null bị từ chối với độ dài 0 bytes")
    void nullSecret_isRejected() {
      JwtConfig config = configWith(null, "60m");

      assertThatThrownBy(config::validateSecret)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("hiện tại: 0 bytes");
    }

    @Test
    @DisplayName(LABEL + "độ dài tính theo BYTE UTF-8, không theo số ký tự")
    void lengthIsMeasuredInUtf8Bytes() {
      // 11 ký tự 'ế' (3 bytes mỗi ký tự trong UTF-8) = 33 bytes >= 32 dù chỉ có 11 ký tự.
      JwtConfig multiByte = configWith("ế".repeat(11), "60m");
      multiByte.validateSecret();

      // 10 ký tự 'ế' = 30 bytes < 32.
      JwtConfig tooShort = configWith("ế".repeat(10), "60m");
      assertThatThrownBy(tooShort::validateSecret)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("hiện tại: 30 bytes");
    }
  }

  @Nested
  @DisplayName("getTtl: phân tích chuỗi TTL")
  class TtlTests {

    @ParameterizedTest(name = "[{index}] \"{0}\" -> {1} giây")
    @CsvSource({
      "60m, 3600",
      "1h, 3600",
      "3600s, 3600",
      "2d, 172800",
      "90, 90",
      "' 5M ', 300",
      "15M, 900"
    })
    @DisplayName(
        LABEL
            + "hỗ trợ hậu tố m/h/s/d, số trần là giây, bỏ khoảng trắng, không phân biệt hoa thường")
    void validTtl_isParsed(String ttl, long expectedSeconds) {
      assertThat(configWith(TEST_SECRET_32_BYTES, ttl).getTtl())
          .isEqualTo(Duration.ofSeconds(expectedSeconds));
    }

    @ParameterizedTest(name = "[{index}] TTL \"{0}\" -> mặc định 60 phút")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "abc", "m", "12x", "1.5h"})
    @DisplayName(LABEL + "TTL rỗng hoặc sai định dạng dùng mặc định 60 phút")
    void invalidOrBlankTtl_fallsBackToDefault(String ttl) {
      assertThat(configWith(TEST_SECRET_32_BYTES, ttl).getTtl()).isEqualTo(Duration.ofMinutes(60));
    }
  }

  @Nested
  @DisplayName("jwtEncoder/jwtDecoder: ký và xác thực HS256")
  class EncoderDecoderTests {

    private Jwt encode(JwtConfig config, Instant issuedAt, Instant expiresAt) {
      SecretKey key = config.jwtSecretKey();
      JwtEncoder encoder = config.jwtEncoder(key);
      JwtClaimsSet claims =
          JwtClaimsSet.builder()
              .issuer("lms-api-test")
              .subject("user-id-thu-nghiem")
              .issuedAt(issuedAt)
              .expiresAt(expiresAt)
              .build();
      JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
      return encoder.encode(JwtEncoderParameters.from(header, claims));
    }

    @Test
    @DisplayName(LABEL + "token ký bằng khóa A giải mã được bằng khóa A và giữ nguyên claim")
    void roundTrip_withSameSecret_succeeds() {
      JwtConfig config = configWith(TEST_SECRET_32_BYTES, "60m");
      Instant now = Instant.now();
      Jwt token = encode(config, now, now.plus(Duration.ofMinutes(5)));

      Jwt decoded = config.jwtDecoder(config.jwtSecretKey()).decode(token.getTokenValue());

      assertThat(decoded.getSubject()).isEqualTo("user-id-thu-nghiem");
      assertThat(decoded.getClaimAsString("iss")).isEqualTo("lms-api-test");
    }

    @Test
    @DisplayName(LABEL + "token ký bằng khóa khác bị bộ giải mã từ chối")
    void tokenSignedWithOtherSecret_isRejected() {
      Instant now = Instant.now();
      Jwt foreign =
          encode(configWith(OTHER_SECRET_32_BYTES, "60m"), now, now.plus(Duration.ofMinutes(5)));
      JwtConfig config = configWith(TEST_SECRET_32_BYTES, "60m");
      JwtDecoder decoder = config.jwtDecoder(config.jwtSecretKey());

      assertThatThrownBy(() -> decoder.decode(foreign.getTokenValue()))
          .isInstanceOf(BadJwtException.class);
    }

    @Test
    @DisplayName(LABEL + "token bị sửa chữ ký bị từ chối")
    void tamperedSignature_isRejected() {
      JwtConfig config = configWith(TEST_SECRET_32_BYTES, "60m");
      Instant now = Instant.now();
      String value = encode(config, now, now.plus(Duration.ofMinutes(5))).getTokenValue();
      // Đổi ký tự giữa chữ ký (không phải ký tự cuối, vì ký tự cuối base64url có bit đệm).
      int index = value.lastIndexOf('.') + 5;
      char original = value.charAt(index);
      char replacement = original == 'A' ? 'B' : 'A';
      String tampered = value.substring(0, index) + replacement + value.substring(index + 1);
      JwtDecoder decoder = config.jwtDecoder(config.jwtSecretKey());

      assertThatThrownBy(() -> decoder.decode(tampered)).isInstanceOf(BadJwtException.class);
    }

    @Test
    @DisplayName(LABEL + "token đã hết hạn (vượt độ lệch đồng hồ mặc định 60 giây) bị từ chối")
    void expiredToken_isRejected() {
      JwtConfig config = configWith(TEST_SECRET_32_BYTES, "60m");
      Instant now = Instant.now();
      Jwt expired =
          encode(config, now.minus(Duration.ofHours(2)), now.minus(Duration.ofMinutes(5)));
      JwtDecoder decoder = config.jwtDecoder(config.jwtSecretKey());

      assertThatThrownBy(() -> decoder.decode(expired.getTokenValue()))
          .isInstanceOf(BadJwtException.class);
    }
  }
}
