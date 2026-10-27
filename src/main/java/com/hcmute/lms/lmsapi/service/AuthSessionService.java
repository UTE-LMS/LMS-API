package com.hcmute.lms.lmsapi.service;

import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Dịch vụ kiểm tra và xác thực phiên làm việc của người dùng (US-01 / ADR 0003).
 * Xác thực userId (sub) và tokenVersion từ JWT so với cơ sở dữ liệu MongoDB.
 * Ném {@link AuthenticationException} khi tài khoản bị khóa hoặc tokenVersion không khớp để trả về HTTP 401.
 */
public interface AuthSessionService {

    /**
     * Xác thực phiên người dùng theo userId và tokenVersion từ token.
     *
     * @param userId ID định danh của người dùng (subject trong JWT)
     * @param tokenVersion số phiên bảo mật trong JWT
     * @return đối tượng {@link UserPrincipal} đã nạp thông tin tươi từ CSDL
     * @throws AuthenticationException nếu người dùng không tồn tại, bị LOCKED, hoặc tokenVersion không khớp
     */
    UserPrincipal validateUserSession(String userId, long tokenVersion) throws AuthenticationException;

    /**
     * Xác thực phiên người dùng trực tiếp từ đối tượng {@link Jwt}.
     *
     * @param jwt đối tượng JWT đã giải mã
     * @return đối tượng {@link UserPrincipal} đã nạp thông tin tươi từ CSDL
     * @throws AuthenticationException nếu token thiếu claim hoặc phiên không hợp lệ
     */
    UserPrincipal validateUserSession(Jwt jwt) throws AuthenticationException;
}
