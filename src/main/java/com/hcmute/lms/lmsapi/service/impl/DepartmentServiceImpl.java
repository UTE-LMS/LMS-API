package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.document.RoleAssignment;
import com.hcmute.lms.lmsapi.document.User;
import com.hcmute.lms.lmsapi.document.UserScope;
import com.hcmute.lms.lmsapi.dto.AssignCourseRequest;
import com.hcmute.lms.lmsapi.dto.UserResponse;
import com.hcmute.lms.lmsapi.enums.Role;
import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
import com.hcmute.lms.lmsapi.exception.ResourceNotFoundException;
import com.hcmute.lms.lmsapi.mapper.UserMapper;
import com.hcmute.lms.lmsapi.repository.CourseRepository;
import com.hcmute.lms.lmsapi.repository.UserRepository;
import com.hcmute.lms.lmsapi.service.DepartmentService;
import com.hcmute.lms.lmsapi.util.ObjectIdUtils;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DepartmentServiceImpl implements DepartmentService {

  private final UserRepository userRepository;
  private final CourseRepository courseRepository;
  private final UserMapper userMapper;

  @Override
  public UserResponse assignCourseToLecturer(AssignCourseRequest request) {
    ObjectIdUtils.requireValid(request.getCourseId(), "courseId");
    ObjectIdUtils.requireValid(request.getLecturerId(), "lecturerId");

    if (!courseRepository.existsById(request.getCourseId())) {
      throw new ResourceNotFoundException("Học phần không tồn tại: " + request.getCourseId());
    }

    User user =
        userRepository
            .findById(request.getLecturerId())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Giảng viên không tồn tại: " + request.getLecturerId()));

    RoleAssignment lecturerAssignment = findLecturerAssignment(user);
    if (lecturerAssignment == null) {
      throw new BusinessRuleViolationException(
          "Người dùng được phân công phải có vai trò LECTURER");
    }

    UserScope scope = lecturerAssignment.getScope();
    if (scope == null) {
      scope = UserScope.builder().courseIds(new ArrayList<>()).build();
      lecturerAssignment.setScope(scope);
    }
    if (scope.getCourseIds() == null) {
      scope.setCourseIds(new ArrayList<>());
    }

    if (!scope.getCourseIds().contains(request.getCourseId())) {
      scope.getCourseIds().add(request.getCourseId());
      user.setTokenVersion(user.getTokenVersion() + 1);
      user = userRepository.save(user);
      log.info(
          "Đã phân công môn học id=[{}] cho giảng viên id=[{}] ({})",
          request.getCourseId(),
          user.getId(),
          user.getUsername());
    }

    return userMapper.toResponse(user);
  }

  @Override
  public UserResponse unassignCourseFromLecturer(AssignCourseRequest request) {
    ObjectIdUtils.requireValid(request.getCourseId(), "courseId");
    ObjectIdUtils.requireValid(request.getLecturerId(), "lecturerId");

    User user =
        userRepository
            .findById(request.getLecturerId())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Giảng viên không tồn tại: " + request.getLecturerId()));

    RoleAssignment lecturerAssignment = findLecturerAssignment(user);
    if (lecturerAssignment == null) {
      throw new BusinessRuleViolationException(
          "Người dùng được phân công phải có vai trò LECTURER");
    }

    UserScope scope = lecturerAssignment.getScope();
    if (scope != null
        && scope.getCourseIds() != null
        && scope.getCourseIds().remove(request.getCourseId())) {
      user.setTokenVersion(user.getTokenVersion() + 1);
      user = userRepository.save(user);
      log.info(
          "Đã hủy phân công môn học id=[{}] khỏi giảng viên id=[{}] ({})",
          request.getCourseId(),
          user.getId(),
          user.getUsername());
    }

    return userMapper.toResponse(user);
  }

  private RoleAssignment findLecturerAssignment(User user) {
    if (user.getRoleAssignments() == null) {
      return null;
    }
    return user.getRoleAssignments().stream()
        .filter(a -> a != null && a.getRole() == Role.LECTURER)
        .findFirst()
        .orElse(null);
  }
}
