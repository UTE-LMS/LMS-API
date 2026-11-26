package com.hcmute.lms.lmsapi.mapper.impl;

import com.hcmute.lms.lmsapi.document.Faculty;
import com.hcmute.lms.lmsapi.document.User;
import com.hcmute.lms.lmsapi.dto.CreateUserRequest;
import com.hcmute.lms.lmsapi.dto.UserResponse;
import com.hcmute.lms.lmsapi.enums.UserStatus;
import com.hcmute.lms.lmsapi.mapper.UserMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Hiện thực interface {@link UserMapper}. Đảm bảo che chắn hoàn toàn passwordHash và tokenVersion
 * khỏi dữ liệu trả về client.
 */
@Component
public class UserMapperImpl implements UserMapper {

  @Override
  public UserResponse toResponse(User user) {
    return toResponse(user, null);
  }

  @Override
  public UserResponse toResponse(User user, Faculty faculty) {
    if (user == null) {
      return null;
    }

    return UserResponse.builder()
        .id(user.getId())
        .username(user.getUsername())
        .email(user.getEmail())
        .fullName(user.getFullName())
        .status(user.getStatus())
        .facultyId(user.getFacultyId())
        .facultyCode(faculty != null ? faculty.getCode() : null)
        .facultyName(faculty != null ? faculty.getName() : null)
        .roles(user.getRoles() != null ? new ArrayList<>(user.getRoles()) : new ArrayList<>())
        .createdAt(user.getCreatedAt())
        .updatedAt(user.getUpdatedAt())
        .build();
  }

  @Override
  public List<UserResponse> toResponseList(List<User> users) {
    return toResponseList(users, Collections.emptyMap());
  }

  @Override
  public List<UserResponse> toResponseList(List<User> users, Map<String, Faculty> facultiesById) {
    if (users == null) {
      return new ArrayList<>();
    }
    Map<String, Faculty> lookup = facultiesById != null ? facultiesById : Collections.emptyMap();
    return users.stream()
        .map(u -> toResponse(u, u.getFacultyId() != null ? lookup.get(u.getFacultyId()) : null))
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
        .roles(request.getRoles() != null ? new ArrayList<>(request.getRoles()) : new ArrayList<>())
        .createdAt(now)
        .updatedAt(now)
        .build();
  }
}
