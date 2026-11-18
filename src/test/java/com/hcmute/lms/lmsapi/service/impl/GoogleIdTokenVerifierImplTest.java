package com.hcmute.lms.lmsapi.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
import com.hcmute.lms.lmsapi.service.GoogleIdTokenVerifier.GoogleIdentity;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Unit test xác minh ID token Google (ADR 0016). Token được ký bằng khóa RSA sinh ngay trong test
 * và giải mã bằng khóa công khai tương ứng, không gọi Google. Client ID và email là dữ liệu giả.
 */
@DisplayName("ADR 0016: GoogleIdTokenVerifierImpl")
class GoogleIdTokenVerifierImplTest {

  private static final String CLIENT_ID = "test-client.apps.googleusercontent.com";
  private static final String EMAIL = "gv.test@example.invalid";

  private static RSAPrivateKey privateKey;
  private static RSAPrivateKey otherPrivateKey;
  private static GoogleIdTokenVerifierImpl verifier;

  @BeforeAll
  static void generateKeys() throws NoSuchAlgorithmException {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    KeyPair pair = generator.generateKeyPair();
    privateKey = (RSAPrivateKey) pair.getPrivate();
    otherPrivateKey = (RSAPrivateKey) generator.generateKeyPair().getPrivate();

    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withPublicKey((RSAPublicKey) pair.getPublic()).build();
    decoder.setJwtValidator(GoogleIdTokenVerifierImpl.validator(CLIENT_ID));
    verifier = new GoogleIdTokenVerifierImpl(CLIENT_ID, () -> decoder);
  }

  /** Token hợp lệ mặc định; mỗi test sửa một điều kiện. */
  private static JWTClaimsSet.Builder validClaims() {
    Instant now = Instant.now();
    return new JWTClaimsSet.Builder()
        .issuer("https://accounts.google.com")
        .audience(CLIENT_ID)
        .subject("1234567890")
        .claim("email", EMAIL)
        .claim("email_verified", true)
        .claim("name", "Giảng viên thử nghiệm")
        .issueTime(Date.from(now.minusSeconds(10)))
        .expirationTime(Date.from(now.plusSeconds(600)));
  }

  private static String sign(JWTClaimsSet claims, RSAPrivateKey key) {
    try {
      SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).build(), claims);
      jwt.sign(new RSASSASigner(key));
      return jwt.serialize();
    } catch (JOSEException ex) {
      throw new IllegalStateException(ex);
    }
  }

  private static void assertRejected(String token, String messagePart) {
    assertThatThrownBy(() -> verifier.verify(token))
        .isInstanceOf(BadCredentialsException.class)
        .hasMessageContaining(messagePart);
  }

  @Test
  @DisplayName("Token hợp lệ: trả về subject, email và tên")
  void validToken() {
    GoogleIdentity identity = verifier.verify(sign(validClaims().build(), privateKey));

    assertThat(identity.subject()).isEqualTo("1234567890");
    assertThat(identity.email()).isEqualTo(EMAIL);
    assertThat(identity.name()).isEqualTo("Giảng viên thử nghiệm");
  }

  @Test
  @DisplayName("Nơi phát hành 'accounts.google.com' (không có https://) cũng được chấp nhận")
  void bareIssuerAccepted() {
    String token = sign(validClaims().issuer("accounts.google.com").build(), privateKey);

    assertThat(verifier.verify(token).email()).isEqualTo(EMAIL);
  }

  @Test
  @DisplayName("email_verified dạng chuỗi \"true\" được chấp nhận")
  void emailVerifiedAsString() {
    String token = sign(validClaims().claim("email_verified", "true").build(), privateKey);

    assertThat(verifier.verify(token).email()).isEqualTo(EMAIL);
  }

  @Test
  @DisplayName("Token cấp cho ứng dụng khác (sai audience) bị từ chối")
  void wrongAudience() {
    String token =
        sign(
            validClaims().audience("ung-dung-khac.apps.googleusercontent.com").build(), privateKey);

    assertRejected(token, "không hợp lệ");
  }

  @Test
  @DisplayName("Không phải Google phát hành (sai iss) bị từ chối")
  void wrongIssuer() {
    String token = sign(validClaims().issuer("https://evil.example").build(), privateKey);

    assertRejected(token, "không hợp lệ");
  }

  @Test
  @DisplayName("Token đã hết hạn bị từ chối")
  void expired() {
    Instant past = Instant.now().minusSeconds(3600);
    String token =
        sign(
            validClaims()
                .issueTime(Date.from(past.minusSeconds(60)))
                .expirationTime(Date.from(past))
                .build(),
            privateKey);

    assertRejected(token, "không hợp lệ");
  }

  @Test
  @DisplayName(
      "Token thiếu iss hoặc thiếu aud bị từ chối bằng 401, không thành lỗi 500 (NullPointerException)")
  void missingIssuerOrAudience() {
    JWTClaimsSet withoutIss = new JWTClaimsSet.Builder(validClaims().build()).issuer(null).build();
    JWTClaimsSet withoutAud =
        new JWTClaimsSet.Builder(validClaims().build()).audience((String) null).build();

    assertRejected(sign(withoutIss, privateKey), "không hợp lệ");
    assertRejected(sign(withoutAud, privateKey), "không hợp lệ");
  }

  @Test
  @DisplayName("Chữ ký bằng khóa khác (giả mạo) bị từ chối")
  void forgedSignature() {
    String token = sign(validClaims().build(), otherPrivateKey);

    assertRejected(token, "không hợp lệ");
  }

  @Test
  @DisplayName("Chuỗi rác không phải JWT bị từ chối")
  void garbage() {
    assertRejected("khong-phai-jwt", "không hợp lệ");
  }

  @Test
  @DisplayName("Email chưa được Google xác minh bị từ chối")
  void emailNotVerified() {
    assertRejected(
        sign(validClaims().claim("email_verified", false).build(), privateKey),
        "chưa được xác minh");
    assertRejected(
        sign(
            new JWTClaimsSet.Builder(validClaims().build()).claim("email_verified", null).build(),
            privateKey),
        "chưa được xác minh");
  }

  @Test
  @DisplayName("Token không có email bị từ chối")
  void missingEmail() {
    String token =
        sign(
            new JWTClaimsSet.Builder(validClaims().build()).claim("email", null).build(),
            privateKey);

    assertRejected(token, "không cung cấp email");
  }

  @Test
  @DisplayName(
      "Chưa cấu hình client ID: isConfigured=false và verify báo chưa cấu hình, không gọi bộ giải mã")
  void notConfigured() {
    GoogleIdTokenVerifierImpl unconfigured =
        new GoogleIdTokenVerifierImpl(
            "  ",
            () -> {
              throw new AssertionError("không được tạo bộ giải mã khi chưa cấu hình");
            });

    assertThat(unconfigured.isConfigured()).isFalse();
    assertThatThrownBy(() -> unconfigured.verify("bat-ky"))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessageContaining("chưa được cấu hình");
    assertThat(verifier.isConfigured()).isTrue();
  }
}
