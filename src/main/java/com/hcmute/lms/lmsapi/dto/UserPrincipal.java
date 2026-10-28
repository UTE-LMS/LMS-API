package com.hcmute.lms.lmsapi.dto;

import com.hcmute.lms.lmsapi.document.RoleAssignment;
import com.hcmute.lms.lmsapi.document.User;
import com.hcmute.lms.lmsapi.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Principal đại diện cho người dùng đã được xác thực trong Spring Security Context (F01 / ADR 0003).
 * Được nạp động từ CSDL mỗi request nhằm đảm bảo cập nhật quyền và trạng thái tài khoản có hiệu lực ngay lập tức.
 * Đặt trong package dto theo đúng cấu trúc ADR 0002.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPrincipal implements Principal {

    /**
     * Định danh duy nhất của người dùng (ObjectId MongoDB).
     */
    private String id;

    /**
     * Tên đăng nhập của người dùng.
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
     * Phiên bản token bảo mật lưu trong CSDL.
     */
    private long tokenVersion;

    /**
     * Danh sách các cặp phân công (vai trò, phạm vi phụ trách).
     */
    @Builder.Default
    private List<RoleAssignment> roleAssignments = new ArrayList<>();

    @Override
    public String getName() {
        return username;
    }

    /**
     * Trích xuất danh sách quyền hạn (Authorities) dưới dạng ROLE_<ROLE_NAME> cho Spring Security.
     *
     * @return danh sách GrantedAuthority
     */
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (roleAssignments == null) {
            return List.of();
        }
        return roleAssignments.stream()
                .filter(ra -> ra != null && ra.getRole() != null)
                .map(ra -> new SimpleGrantedAuthority("ROLE_" + ra.getRole().name()))
                .toList();
    }

    /**
     * Chuyển đổi từ User document sang UserPrincipal.
     *
     * @param user đối tượng User trong CSDL
     * @return đối tượng UserPrincipal
     */
    public static UserPrincipal from(User user) {
        if (user == null) {
            return null;
        }
        return UserPrincipal.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .status(user.getStatus())
                .tokenVersion(user.getTokenVersion())
                .roleAssignments(user.getRoleAssignments() != null ? new ArrayList<>(user.getRoleAssignments()) : new ArrayList<>())
                .build();
    }
}
