package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.document.User;
import com.hcmute.lms.lmsapi.enums.UserStatus;
import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import com.hcmute.lms.lmsapi.repository.UserRepository;
import com.hcmute.lms.lmsapi.service.AuthSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Service;

/**
 * Hiện thực dịch vụ kiểm tra phiên làm việc người dùng (F01 / ADR 0003).
 * Đảm bảo độ bao phủ JaCoCo trong mô-đun lõi xác thực.
 * Sử dụng RFC 6750 ASCII compliant descriptions cho InvalidBearerTokenException.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthSessionServiceImpl implements AuthSessionService {

    private final UserRepository userRepository;

    @Override
    public UserPrincipal validateUserSession(String userId, long tokenVersion) throws AuthenticationException {
        if (userId == null || userId.isBlank()) {
            log.warn("Phiên làm việc không hợp lệ: userId bị trống");
            throw new InvalidBearerTokenException("Token subject (userId) cannot be null or blank");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("Phiên làm việc không hợp lệ: không tìm thấy người dùng có id={}", userId);
                    return new InvalidBearerTokenException("User does not exist or invalid session");
                });

        if (user.getStatus() != UserStatus.ACTIVE) {
            log.warn("Phiên làm việc bị từ chối: tài khoản user id={} đang ở trạng thái {}", userId, user.getStatus());
            throw new InvalidBearerTokenException("User account is locked or inactive");
        }

        if (user.getTokenVersion() != tokenVersion) {
            log.warn("Phiên làm việc bị thu hồi: tokenVersion của token ({}) khác với tokenVersion trong DB ({}) của user id={}",
                    tokenVersion, user.getTokenVersion(), userId);
            throw new InvalidBearerTokenException("Session has been revoked due to tokenVersion mismatch");
        }

        return UserPrincipal.from(user);
    }

    @Override
    public UserPrincipal validateUserSession(Jwt jwt) throws AuthenticationException {
        if (jwt == null) {
            throw new InvalidBearerTokenException("Token cannot be null");
        }

        String userId = jwt.getSubject();
        Object tokenVersionClaim = jwt.getClaim("tokenVersion");
        if (tokenVersionClaim == null) {
            log.warn("Token JWT thiếu claim tokenVersion cho subject: {}", userId);
            throw new InvalidBearerTokenException("Token is missing tokenVersion claim");
        }

        long tokenVersion;
        if (tokenVersionClaim instanceof Number num) {
            tokenVersion = num.longValue();
        } else {
            try {
                tokenVersion = Long.parseLong(tokenVersionClaim.toString());
            } catch (NumberFormatException e) {
                log.warn("Không thể chuyển đổi tokenVersion claim '{}' thành số nguyên", tokenVersionClaim);
                throw new InvalidBearerTokenException("tokenVersion claim is not a valid number");
            }
        }

        return validateUserSession(userId, tokenVersion);
    }
}
