package com.hcmute.lms.lmsapi.dto;

import com.hcmute.lms.lmsapi.document.RoleAssignment;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO yêu cầu cập nhật thông tin người dùng trong hệ thống (F01 / US-02).
 * Tuyệt đối không chứa username (bất biến sau khi tạo) và không chứa password (không đổi qua API này).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRequest {

    /**
     * Địa chỉ email mới của người dùng (cho phép cập nhật).
     * Bắt buộc tuân thủ chuẩn định dạng email RFC 5322.
     */
    @NotBlank(message = "Email không được để trống")
    @Email(message = "Địa chỉ email không đúng định dạng", regexp = "^[a-zA-Z0-9_!#$%&'*+/=?`{|}~^.-]+@[a-zA-Z0-9.-]+$")
    private String email;

    /**
     * Họ và tên đầy đủ mới của người dùng.
     */
    @NotBlank(message = "Họ và tên không được để trống")
    private String fullName;

    /**
     * Danh sách phân công vai trò và phạm vi phụ trách mới.
     * Bắt buộc có ít nhất 1 phân công vai trò.
     */
    @NotEmpty(message = "Danh sách phân công vai trò không được để trống")
    @Valid
    @Builder.Default
    private List<RoleAssignment> roleAssignments = new ArrayList<>();
}
