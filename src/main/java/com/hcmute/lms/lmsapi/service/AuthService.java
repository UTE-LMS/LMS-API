package com.hcmute.lms.lmsapi.service;

import com.hcmute.lms.lmsapi.dto.LoginRequest;
import com.hcmute.lms.lmsapi.dto.LoginResponse;
import com.hcmute.lms.lmsapi.dto.UserProfileResponse;
import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Service xử lý các nghiệp vụ xác thực người dùng (F01 / US-01 / ADR 0003).
 * Tầng Business Logic theo chuẩn kiến trúc 3 tầng và nguyên lý DIP của ADR 0002.
 */
public interface AuthService {

    /**
     * Xác thực thông tin đăng nhập, sinh JWT token và ghi nhận kiểm toán LOGIN.
     *
     * @param request thông tin đăng nhập (username, password)
     * @param httpRequest yêu cầu HTTP để trích xuất địa chỉ IP client
     * @return {@link LoginResponse} chứa token và thông tin người dùng
     */
    LoginResponse login(LoginRequest request, HttpServletRequest httpRequest);

    /**
     * Đăng xuất người dùng hiện tại, tăng nguyên tử tokenVersion để thu hồi phiên.
     *
     * @param userId ID định danh của người dùng
     */
    void logout(String userId);

    /**
     * Lấy thông tin hồ sơ người dùng hiện tại.
     *
     * @param principal thông tin người dùng đã xác thực
     * @return {@link UserProfileResponse} thông tin hồ sơ và phân quyền
     */
    UserProfileResponse getCurrentUserProfile(UserPrincipal principal);
}
