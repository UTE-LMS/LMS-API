package com.hcmute.lms.lmsapi.dto;

import com.hcmute.lms.lmsapi.document.RoleAssignment;
import com.hcmute.lms.lmsapi.validator.Utf8ByteLength;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO yêu cầu tạo mới người dùng trong hệ thống (F01 / US-02).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserRequest {

    /**
     * Tên đăng nhập của người dùng.
     * Tối thiểu 3 ký tự, tối đa 50 ký tự, gồm chữ cái, chữ số và các ký tự '.', '_', '-'.
     */
    @NotBlank(message = "Tên đăng nhập không được để trống")
    @Size(min = 3, max = 50, message = "Tên đăng nhập phải có từ 3 đến 50 ký tự")
    @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Tên đăng nhập chỉ được chứa chữ cái, chữ số, dấu gạch dưới, gạch ngang và dấu chấm")
    private String username;

    /**
     * Địa chỉ email của người dùng.
     * Tuân thủ chuẩn định dạng email RFC 5322.
     */
    @NotBlank(message = "Email không được để trống")
    @Email(message = "Địa chỉ email không đúng định dạng", regexp = "^[a-zA-Z0-9_!#$%&'*+/=?`{|}~^.-]+@[a-zA-Z0-9.-]+$")
    private String email;

    /**
     * Mật khẩu khởi tạo của tài khoản.
     * Bắt buộc kiểm tra độ dài từ 8 đến 72 bytes UTF-8 để chống BCrypt truncation bug.
     */
    @NotBlank(message = "Mật khẩu không được để trống")
    @Utf8ByteLength(min = 8, max = 72, message = "Độ dài mật khẩu phải từ 8 đến 72 bytes UTF-8")
    private String password;

    /**
     * Họ và tên đầy đủ của người dùng.
     */
    @NotBlank(message = "Họ và tên không được để trống")
    private String fullName;

    /**
     * Danh sách phân công vai trò và phạm vi phụ trách.
     * Bắt buộc có ít nhất 1 phân công vai trò.
     */
    @NotEmpty(message = "Danh sách phân công vai trò không được để trống")
    @Valid
    @Builder.Default
    private List<RoleAssignment> roleAssignments = new ArrayList<>();
}
