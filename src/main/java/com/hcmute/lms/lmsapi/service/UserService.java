package com.hcmute.lms.lmsapi.service;

import com.hcmute.lms.lmsapi.dto.CreateUserRequest;
import com.hcmute.lms.lmsapi.dto.PageResponse;
import com.hcmute.lms.lmsapi.dto.UpdateUserRequest;
import com.hcmute.lms.lmsapi.dto.UpdateUserStatusRequest;
import com.hcmute.lms.lmsapi.dto.UserResponse;

/**
 * Interface nghiệp vụ cho phân hệ quản lý người dùng (F01 / US-02).
 * Cung cấp các thao tác CRUD người dùng, phân trang, kiểm tra guard quản trị viên và thu hồi phiên làm việc.
 */
public interface UserService {

    /**
     * Tạo mới một tài khoản người dùng trong hệ thống (POST /api/v1/users).
     *
     * @param request dữ liệu yêu cầu tạo mới
     * @return UserResponse chứa thông tin người dùng vừa tạo
     */
    UserResponse createUser(CreateUserRequest request);

    /**
     * Lấy danh sách người dùng theo khuôn dạng phân trang chuẩn hệ thống (GET /api/v1/users).
     *
     * @param page chỉ số trang (0-indexed, >= 0)
     * @param size kích thước trang (1 <= size <= 100)
     * @return PageResponse chứa danh sách UserResponse và thông tin phân trang
     */
    PageResponse<UserResponse> getUsers(int page, int size);

    /**
     * Lấy chi tiết thông tin một người dùng theo ID (GET /api/v1/users/{id}).
     *
     * @param id định danh người dùng (ObjectId 24 hex)
     * @return UserResponse chi tiết
     */
    UserResponse getUserById(String id);

    /**
     * Cập nhật thông tin người dùng (PUT /api/v1/users/{id}).
     * Username là bất biến, không cập nhật password qua API này.
     *
     * @param id định danh người dùng cần cập nhật
     * @param request dữ liệu cập nhật
     * @param currentUserId ID của người dùng đang thực hiện yêu cầu (phục vụ ADMIN Guard)
     * @return UserResponse thông tin sau khi cập nhật
     */
    UserResponse updateUser(String id, UpdateUserRequest request, String currentUserId);

    /**
     * Cập nhật trạng thái Khóa / Mở khóa của người dùng (PUT /api/v1/users/{id}/status).
     * Khi chuyển sang LOCKED, tự động tăng nguyên tử tokenVersion để thu hồi toàn bộ token cũ.
     *
     * @param id định danh người dùng cần cập nhật trạng thái
     * @param request trạng thái mới (ACTIVE hoặc LOCKED)
     * @param currentUserId ID của người dùng đang thực hiện yêu cầu (phục vụ ADMIN Guard)
     * @return UserResponse thông tin sau khi cập nhật trạng thái
     */
    UserResponse updateStatus(String id, UpdateUserStatusRequest request, String currentUserId);
}
