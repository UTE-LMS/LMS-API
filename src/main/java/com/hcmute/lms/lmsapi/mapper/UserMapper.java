package com.hcmute.lms.lmsapi.mapper;

import com.hcmute.lms.lmsapi.document.User;
import com.hcmute.lms.lmsapi.dto.CreateUserRequest;
import com.hcmute.lms.lmsapi.dto.UserResponse;

import java.util.List;

/**
 * Interface chuyển đổi dữ liệu người dùng giữa Document và DTO (F01 / US-02).
 * Đảm bảo nguyên lý Single Responsibility Principle (SRP) và tách biệt tầng dữ liệu.
 */
public interface UserMapper {

    /**
     * Chuyển đổi từ User document sang UserResponse DTO.
     * Tuyệt đối không ánh xạ passwordHash hay tokenVersion.
     *
     * @param user đối tượng User trong CSDL
     * @return đối tượng UserResponse, hoặc null nếu user là null
     */
    UserResponse toResponse(User user);

    /**
     * Chuyển đổi danh sách User document sang danh sách UserResponse DTO.
     *
     * @param users danh sách User trong CSDL
     * @return danh sách UserResponse
     */
    List<UserResponse> toResponseList(List<User> users);

    /**
     * Chuyển đổi từ CreateUserRequest sang User document với mật khẩu đã được băm.
     *
     * @param request yêu cầu tạo mới người dùng
     * @param passwordHash mật khẩu đã được băm bằng BCrypt
     * @return đối tượng User document sẵn sàng lưu vào CSDL
     */
    User toDocument(CreateUserRequest request, String passwordHash);
}
