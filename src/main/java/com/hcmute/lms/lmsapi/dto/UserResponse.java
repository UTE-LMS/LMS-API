package com.hcmute.lms.lmsapi.dto;

import com.hcmute.lms.lmsapi.document.RoleAssignment;
import com.hcmute.lms.lmsapi.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO đại diện cho thông tin người dùng trả về qua REST API (F01 / US-02).
 * Tuyệt đối không chứa passwordHash hay tokenVersion nhằm đảm bảo an toàn thông tin theo ADR 0003.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    /**
     * Định danh duy nhất của người dùng (ObjectId 24 hex chars).
     */
    private String id;

    /**
     * Tên đăng nhập của người dùng (bất biến).
     */
    private String username;

    /**
     * Địa chỉ email của người dùng.
     */
    private String email;

    /**
     * Họ và tên đầy đủ của người dùng.
     */
    private String fullName;

    /**
     * Trạng thái hoạt động của tài khoản (ACTIVE, LOCKED).
     */
    private UserStatus status;

    /**
     * Danh sách các phân công vai trò và phạm vi phụ trách.
     */
    @Builder.Default
    private List<RoleAssignment> roleAssignments = new ArrayList<>();

    /**
     * Thời điểm tạo tài khoản (ISO-8601 UTC).
     */
    private Instant createdAt;

    /**
     * Thời điểm cập nhật tài khoản gần nhất (ISO-8601 UTC).
     */
    private Instant updatedAt;
}
