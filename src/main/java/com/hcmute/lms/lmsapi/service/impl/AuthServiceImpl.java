package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.config.JwtConfig;
import com.hcmute.lms.lmsapi.document.AuditAction;
import com.hcmute.lms.lmsapi.document.AuditEntity;
import com.hcmute.lms.lmsapi.document.User;
import com.hcmute.lms.lmsapi.document.UserStatus;
import com.hcmute.lms.lmsapi.dto.LoginRequest;
import com.hcmute.lms.lmsapi.dto.LoginResponse;
import com.hcmute.lms.lmsapi.dto.UserProfileResponse;
import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import com.hcmute.lms.lmsapi.repository.UserRepository;
import com.hcmute.lms.lmsapi.service.AuditService;
import com.hcmute.lms.lmsapi.service.AuthService;
import com.hcmute.lms.lmsapi.util.StringNormalizationUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Hiện thực dịch vụ xác thực người dùng (F01 / US-01 / ADR 0003).
 * Xử lý đăng nhập an toàn (chống timing attack), sinh JWT HS256, ghi nhật ký kiểm toán và đăng xuất tức thời.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /**
     * Chuỗi hash giả định dùng để duy trì thời gian so khớp BCrypt khi username không tồn tại,
     * ngăn chặn kẻ tấn công dò quét sự tồn tại của tài khoản (Timing Attack / User Enumeration).
     */
    public static final String DUMMY_HASH = "$2a$10$wN1QGgGv3P7W2B.rP.VnVOuO7XjQJ6JqHqN6K6M8g.f8Zz0yTq4S.";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final JwtConfig jwtConfig;
    private final AuditService auditService;

    @Override
    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        String normalizedUsername = StringNormalizationUtils.normalizeUsername(request.getUsername());
        Optional<User> userOpt = userRepository.findByUsername(normalizedUsername);

        // 1. Chống timing attack khi username không tồn tại
        if (userOpt.isEmpty()) {
            passwordEncoder.matches(request.getPassword(), DUMMY_HASH);
            log.warn("Đăng nhập thất bại: người dùng '{}' không tồn tại", normalizedUsername);
            throw new BadCredentialsException("Tên đăng nhập hoặc mật khẩu không chính xác");
        }

        User user = userOpt.get();

        // 2. Kiểm tra mật khẩu
        boolean passwordMatches = passwordEncoder.matches(request.getPassword(), user.getPasswordHash());
        if (!passwordMatches) {
            log.warn("Đăng nhập thất bại: sai mật khẩu cho người dùng '{}'", normalizedUsername);
            throw new BadCredentialsException("Tên đăng nhập hoặc mật khẩu không chính xác");
        }

        // 3. Kiểm tra trạng thái tài khoản bị khóa (LOCKED)
        if (user.getStatus() != UserStatus.ACTIVE) {
            log.warn("Đăng nhập thất bại: tài khoản '{}' đang bị khóa ({})", normalizedUsername, user.getStatus());
            throw new BadCredentialsException("Tên đăng nhập hoặc mật khẩu không chính xác");
        }

        // 4. Sinh JWT Access Token (HS256) chứa sub và tokenVersion
        Instant now = Instant.now();
        Duration ttl = jwtConfig.getTtl();
        Instant expiresAt = now.plus(ttl);

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtConfig.getIssuer())
                .subject(user.getId())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("tokenVersion", user.getTokenVersion())
                .build();

        JwsHeader jwsHeader = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claims)).getTokenValue();

        // 5. Ghi nhật ký kiểm toán LOGIN
        AuditEntity auditEntity = AuditEntity.builder()
                .collection("users")
                .id(user.getId())
                .build();
        String clientIp = resolveClientIp(httpRequest);
        auditService.record(AuditAction.LOGIN, auditEntity, null, null, null, user.getId(), clientIp);

        log.info("Người dùng '{}' (id={}) đăng nhập thành công", user.getUsername(), user.getId());

        // 6. Trả về LoginResponse
        return LoginResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(ttl.toSeconds())
                .user(UserProfileResponse.from(user))
                .build();
    }

    @Override
    public void logout(String userId) {
        if (userId != null && !userId.isBlank()) {
            long updated = userRepository.incrementTokenVersion(userId);
            log.info("Đã thực hiện đăng xuất cho user id={}, bản ghi bị ảnh hưởng: {}", userId, updated);
        }
    }

    @Override
    public UserProfileResponse getCurrentUserProfile(UserPrincipal principal) {
        if (principal == null) {
            throw new BadCredentialsException("Chưa xác thực người dùng");
        }
        return UserProfileResponse.from(principal);
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
