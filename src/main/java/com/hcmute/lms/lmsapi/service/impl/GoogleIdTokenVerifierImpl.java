package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
import com.hcmute.lms.lmsapi.service.GoogleIdTokenVerifier;
import java.util.List;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/**
 * Xác minh ID token của Google bằng bộ giải mã JWT của Spring Security (Nimbus), không thêm
 * dependency (ADR 0016). Khóa công khai lấy từ JWKS của Google và được Nimbus lưu đệm; không gọi
 * mạng khi khởi động.
 */
@Slf4j
@Component
public class GoogleIdTokenVerifierImpl implements GoogleIdTokenVerifier {

  /** Hai giá trị `iss` Google dùng cho ID token. */
  static final List<String> ISSUERS = List.of("https://accounts.google.com", "accounts.google.com");

  private static final String INVALID_MESSAGE = "Mã đăng nhập Google không hợp lệ hoặc đã hết hạn";

  private final String clientId;
  private final Supplier<JwtDecoder> decoderSupplier;
  private volatile JwtDecoder decoder;

  @Autowired
  public GoogleIdTokenVerifierImpl(
      @Value("${app.security.google.client-id:}") String clientId,
      @Value("${app.security.google.jwk-set-uri:https://www.googleapis.com/oauth2/v3/certs}")
          String jwkSetUri) {
    this(clientId, () -> buildDecoder(clientId, jwkSetUri));
  }

  /** Dùng cho kiểm thử: truyền sẵn bộ giải mã (khóa công khai tự sinh) thay vì JWKS của Google. */
  GoogleIdTokenVerifierImpl(String clientId, Supplier<JwtDecoder> decoderSupplier) {
    this.clientId = clientId == null ? "" : clientId.trim();
    this.decoderSupplier = decoderSupplier;
  }

  @Override
  public boolean isConfigured() {
    return !clientId.isEmpty();
  }

  @Override
  public GoogleIdentity verify(String idToken) {
    if (!isConfigured()) {
      throw new BusinessRuleViolationException("Đăng nhập bằng Google chưa được cấu hình");
    }
    Jwt jwt;
    try {
      jwt = decoder().decode(idToken);
    } catch (JwtException ex) {
      log.warn("ID token Google bị từ chối: {}", ex.getMessage());
      throw new BadCredentialsException(INVALID_MESSAGE, ex);
    }

    String email = jwt.getClaimAsString("email");
    if (email == null || email.isBlank()) {
      throw new BadCredentialsException("Tài khoản Google không cung cấp email");
    }
    if (!isEmailVerified(jwt)) {
      throw new BadCredentialsException("Email Google chưa được xác minh");
    }
    return new GoogleIdentity(jwt.getSubject(), email, jwt.getClaimAsString("name"));
  }

  /** Google gửi `email_verified` dạng boolean; chấp nhận cả chuỗi "true" cho an toàn. */
  private static boolean isEmailVerified(Jwt jwt) {
    Object value = jwt.getClaim("email_verified");
    return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
  }

  private JwtDecoder decoder() {
    JwtDecoder local = decoder;
    if (local == null) {
      synchronized (this) {
        local = decoder;
        if (local == null) {
          local = decoderSupplier.get();
          decoder = local;
        }
      }
    }
    return local;
  }

  private static JwtDecoder buildDecoder(String clientId, String jwkSetUri) {
    NimbusJwtDecoder nimbus =
        NimbusJwtDecoder.withJwkSetUri(jwkSetUri).jwsAlgorithm(SignatureAlgorithm.RS256).build();
    nimbus.setJwtValidator(validator(clientId == null ? "" : clientId.trim()));
    return nimbus;
  }

  /** Bộ kiểm tra: thời hạn, nơi phát hành là Google, đối tượng nhận là client ID của hệ thống. */
  static OAuth2TokenValidator<Jwt> validator(String clientId) {
    // List.of(...).contains(null) ném NullPointerException nên phải kiểm tra null trước (token
    // thiếu iss/aud
    // phải bị từ chối bằng 401, không được thành lỗi 500)
    OAuth2TokenValidator<Jwt> issuer =
        jwt -> {
          String iss = jwt.getClaimAsString("iss");
          return iss != null && ISSUERS.contains(iss)
              ? OAuth2TokenValidatorResult.success()
              : OAuth2TokenValidatorResult.failure(
                  new OAuth2Error("invalid_token", "Nơi phát hành token không phải Google", null));
        };
    OAuth2TokenValidator<Jwt> audience =
        jwt -> {
          List<String> aud = jwt.getAudience();
          return aud != null && aud.contains(clientId)
              ? OAuth2TokenValidatorResult.success()
              : OAuth2TokenValidatorResult.failure(
                  new OAuth2Error("invalid_token", "Token không dành cho ứng dụng này", null));
        };
    return new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(), issuer, audience);
  }
}
