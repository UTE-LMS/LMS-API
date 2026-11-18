package com.hcmute.lms.lmsapi.service;

import com.hcmute.lms.lmsapi.dto.ChangePasswordRequest;
import com.hcmute.lms.lmsapi.dto.GoogleLoginRequest;
import com.hcmute.lms.lmsapi.dto.LoginRequest;
import com.hcmute.lms.lmsapi.dto.LoginResponse;
import com.hcmute.lms.lmsapi.dto.UpdateProfileRequest;
import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import com.hcmute.lms.lmsapi.dto.UserProfileResponse;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Service xử lý các nghiệp vụ xác thực người dùng (F01 / US-01 / ADR 0003). Tầng Business Logic
 * theo chuẩn kiến trúc 3 tầng và nguyên lý DIP của ADR 0002.
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
   * Đăng nhập bằng Google (ADR 0016): xác minh ID token, tìm tài khoản có sẵn theo email đã được
   * Google xác minh rồi cấp JWT của hệ thống. Không tự tạo tài khoản; cờ mustChangePassword giữ
   * nguyên.
   *
   * @param request ID token Google
   * @param httpRequest yêu cầu HTTP để trích xuất địa chỉ IP client cho nhật ký kiểm toán
   * @return {@link LoginResponse} giống đăng nhập bằng mật khẩu
   */
  LoginResponse loginWithGoogle(GoogleLoginRequest request, HttpServletRequest httpRequest);

  /**
   * Người dùng tự đổi mật khẩu: kiểm tra mật khẩu hiện tại, lưu hash mới, tắt cờ
   * mustChangePassword, tăng tokenVersion (thu hồi token cũ) và cấp token mới.
   *
   * @param principal người dùng đã xác thực
   * @param request mật khẩu hiện tại và mật khẩu mới
   * @param httpRequest yêu cầu HTTP để trích xuất địa chỉ IP client cho nhật ký kiểm toán
   * @return {@link LoginResponse} chứa token mới và hồ sơ đã cập nhật
   */
  LoginResponse changePassword(
      UserPrincipal principal, ChangePasswordRequest request, HttpServletRequest httpRequest);

  /**
   * Lấy thông tin hồ sơ người dùng hiện tại.
   *
   * @param principal thông tin người dùng đã xác thực
   * @return {@link UserProfileResponse} thông tin hồ sơ và phân quyền
   */
  UserProfileResponse getCurrentUserProfile(UserPrincipal principal);

  /**
   * Người dùng tự sửa họ tên của mình. Không đổi tokenVersion nên phiên hiện tại vẫn hợp lệ.
   *
   * @param principal người dùng đã xác thực
   * @param request họ tên mới
   * @param httpRequest yêu cầu HTTP để trích xuất địa chỉ IP client cho nhật ký kiểm toán
   * @return {@link UserProfileResponse} hồ sơ sau khi cập nhật
   */
  UserProfileResponse updateProfile(
      UserPrincipal principal, UpdateProfileRequest request, HttpServletRequest httpRequest);
}
