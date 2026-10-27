package com.hcmute.lms.lmsapi.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Cấu hình JWT mã hóa đối xứng Nimbus Resource Server (HS256) theo ADR 0003.
 * Kiểm tra fail-fast khóa JWT_SECRET tối thiểu 32 bytes (256 bits) ngay khi khởi động ứng dụng.
 */
@Slf4j
@Configuration
public class JwtConfig {

    public static final int MIN_SECRET_BYTES = 32;

    @Value("${app.security.jwt.secret:0123456789012345678901234567890123456789012345678901234567890123}")
    private String secret;

    @Value("${app.security.jwt.ttl:60m}")
    private String ttlString;

    @Getter
    @Value("${app.security.jwt.issuer:lms-api}")
    private String issuer;

    @PostConstruct
    public void validateSecret() {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            int length = secret != null ? secret.getBytes(StandardCharsets.UTF_8).length : 0;
            String msg = String.format("Khóa bí mật JWT_SECRET không hợp lệ: độ dài tối thiểu phải từ %d bytes (hiện tại: %d bytes). Hệ thống từ chối khởi động.",
                    MIN_SECRET_BYTES, length);
            log.error(msg);
            throw new IllegalStateException(msg);
        }
        log.info("JWT Configuration initialized successfully (Algorithm=HS256, Issuer={}, TTL={})",
                issuer, ttlString);
    }

    /**
     * Tạo SecretKey cho thuật toán HMAC-SHA256 từ chuỗi cấu hình.
     */
    @Bean
    public SecretKey jwtSecretKey() {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    /**
     * Bean JwtEncoder sử dụng thư viện Nimbus phục vụ ký JWT Access Token.
     */
    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        JWKSource<SecurityContext> jwkSource = new ImmutableSecret<>(jwtSecretKey);
        return new NimbusJwtEncoder(jwkSource);
    }

    /**
     * Bean JwtDecoder sử dụng thư viện Nimbus phục vụ giải mã và xác thực chữ ký JWT.
     */
    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
        return NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    /**
     * Chuyển đổi chuỗi cấu hình TTL (ví dụ "60m", "1h", "3600s") sang đối tượng {@link Duration}.
     * Mặc định là 60 phút nếu rỗng hoặc sai định dạng.
     */
    public Duration getTtl() {
        if (ttlString == null || ttlString.isBlank()) {
            return Duration.ofMinutes(60);
        }
        String clean = ttlString.trim().toLowerCase();
        try {
            if (clean.endsWith("m")) {
                return Duration.ofMinutes(Long.parseLong(clean.substring(0, clean.length() - 1)));
            } else if (clean.endsWith("h")) {
                return Duration.ofHours(Long.parseLong(clean.substring(0, clean.length() - 1)));
            } else if (clean.endsWith("s")) {
                return Duration.ofSeconds(Long.parseLong(clean.substring(0, clean.length() - 1)));
            } else if (clean.endsWith("d")) {
                return Duration.ofDays(Long.parseLong(clean.substring(0, clean.length() - 1)));
            } else {
                return Duration.ofSeconds(Long.parseLong(clean));
            }
        } catch (NumberFormatException e) {
            log.warn("Không thể phân tích chuỗi TTL '{}', dùng giá trị mặc định 60m", ttlString);
            return Duration.ofMinutes(60);
        }
    }
}
