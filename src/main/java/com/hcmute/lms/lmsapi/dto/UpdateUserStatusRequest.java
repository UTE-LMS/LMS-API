package com.hcmute.lms.lmsapi.dto;

import com.hcmute.lms.lmsapi.enums.UserStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO yêu cầu cập nhật trạng thái hoạt động (Khóa / Mở khóa) của người dùng (F01 / US-02).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserStatusRequest {

    /**
     * Trạng thái hoạt động mới của tài khoản (ACTIVE, LOCKED).
     */
    @NotNull(message = "Trạng thái người dùng không được để trống")
    private UserStatus status;
}
