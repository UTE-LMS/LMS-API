package com.hcmute.lms.lmsapi.mapper.impl;

import com.hcmute.lms.lmsapi.document.User;
import com.hcmute.lms.lmsapi.enums.UserStatus;
import com.hcmute.lms.lmsapi.dto.CreateUserRequest;
import com.hcmute.lms.lmsapi.dto.UserResponse;
import com.hcmute.lms.lmsapi.mapper.UserMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Hiện thực interface {@link UserMapper}.
 * Đảm bảo che chắn hoàn toàn passwordHash và tokenVersion khỏi dữ liệu trả về client.
 */
@Component
public class UserMapperImpl implements UserMapper {

    @Override
    public UserResponse toResponse(User user) {
        if (user == null) {
            return null;
        }

        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .status(user.getStatus())
                .roleAssignments(user.getRoleAssignments() != null ? new ArrayList<>(user.getRoleAssignments()) : new ArrayList<>())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    @Override
    public List<UserResponse> toResponseList(List<User> users) {
        if (users == null) {
            return new ArrayList<>();
        }
        return users.stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public User toDocument(CreateUserRequest request, String passwordHash) {
        if (request == null) {
            return null;
        }

        Instant now = Instant.now();
        return User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordHash)
                .fullName(request.getFullName())
                .status(UserStatus.ACTIVE)
                .tokenVersion(0L)
                .roleAssignments(request.getRoleAssignments() != null ? new ArrayList<>(request.getRoleAssignments()) : new ArrayList<>())
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
