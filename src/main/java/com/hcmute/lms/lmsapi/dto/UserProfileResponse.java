package com.hcmute.lms.lmsapi.dto;

import com.hcmute.lms.lmsapi.document.RoleAssignment;
import com.hcmute.lms.lmsapi.document.User;
import com.hcmute.lms.lmsapi.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Phản hồi hồ sơ người dùng cá nhân (UserProfileResponse) cho GET /api/v1/auth/me
 * và lồng trong phản hồi đăng nhập LoginResponse.user (F01 / API-CONTRACT §5.2.1, §5.2.3).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {

    private String id;
    private String username;
    private String email;
    private String fullName;
    private UserStatus status;

    @Builder.Default
    private List<RoleAssignment> roleAssignments = new ArrayList<>();

    public static UserProfileResponse from(User user) {
        if (user == null) {
            return null;
        }
        return UserProfileResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .status(user.getStatus())
                .roleAssignments(user.getRoleAssignments() != null ? new ArrayList<>(user.getRoleAssignments()) : new ArrayList<>())
                .build();
    }

    public static UserProfileResponse from(UserPrincipal principal) {
        if (principal == null) {
            return null;
        }
        return UserProfileResponse.builder()
                .id(principal.getId())
                .username(principal.getUsername())
                .email(principal.getEmail())
                .fullName(principal.getFullName())
                .status(principal.getStatus())
                .roleAssignments(principal.getRoleAssignments() != null ? new ArrayList<>(principal.getRoleAssignments()) : new ArrayList<>())
                .build();
    }
}
