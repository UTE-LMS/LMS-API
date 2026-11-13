package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.document.RoleAssignment;
import com.hcmute.lms.lmsapi.document.User;
import com.hcmute.lms.lmsapi.document.UserScope;
import com.hcmute.lms.lmsapi.dto.AssignCourseRequest;
import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import com.hcmute.lms.lmsapi.dto.UserResponse;
import com.hcmute.lms.lmsapi.enums.Role;
import com.hcmute.lms.lmsapi.enums.UserStatus;
import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
import com.hcmute.lms.lmsapi.exception.ResourceNotFoundException;
import com.hcmute.lms.lmsapi.mapper.UserMapper;
import com.hcmute.lms.lmsapi.repository.CourseRepository;
import com.hcmute.lms.lmsapi.repository.UserRepository;
import com.hcmute.lms.lmsapi.service.DepartmentService;
import com.hcmute.lms.lmsapi.util.ObjectIdUtils;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
  public List<UserResponse> getDepartmentLecturers(UserPrincipal principal, String courseId) {
    if (courseId != null && !courseId.isBlank()) {
      ObjectIdUtils.requireValid(courseId.trim(), "courseId");
      if (!courseRepository.existsById(courseId.trim())) {
        throw new ResourceNotFoundException("Học phần không tồn tại: " + courseId);
      }
    }

    List<User> allActiveLecturers =
        userRepository.findByRoleAssignmentsRoleAndStatus(Role.LECTURER, UserStatus.ACTIVE);

    if (principal == null) {
      return allActiveLecturers.stream().map(userMapper::toResponse).toList();
    }

    Set<String> deptProgramIds = extractDepartmentProgramIds(principal);
    Set<String> deptCourseIds = extractDepartmentCourseIds(principal);

    return allActiveLecturers.stream()
        .filter(
            lecturer ->
                isLecturerWithinDepartmentScope(lecturer, deptProgramIds, deptCourseIds, courseId))
        .map(userMapper::toResponse)
        .toList();
  }

  @Override
  public UserResponse assignCourseToLecturer(AssignCourseRequest request, UserPrincipal principal) {
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

    validateLecturerScopeForDepartmentHead(user, principal, request.getCourseId());

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
  public UserResponse unassignCourseFromLecturer(
      AssignCourseRequest request, UserPrincipal principal) {
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

    validateLecturerScopeForDepartmentHead(user, principal, request.getCourseId());

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

  private void validateLecturerScopeForDepartmentHead(
      User lecturer, UserPrincipal principal, String courseId) {
    if (principal == null) {
      return;
    }

    Set<String> deptProgramIds = extractDepartmentProgramIds(principal);
    Set<String> deptCourseIds = extractDepartmentCourseIds(principal);

    if (!isLecturerWithinDepartmentScope(lecturer, deptProgramIds, deptCourseIds, courseId)) {
      throw new BusinessRuleViolationException(
          "LECTURER_OUT_OF_DEPARTMENT_SCOPE",
          "Giảng viên không thuộc phạm vi Khoa/Ngành (programIds) của Trưởng bộ môn");
    }
  }

  private boolean isLecturerWithinDepartmentScope(
      User lecturer, Set<String> deptProgramIds, Set<String> deptCourseIds, String targetCourseId) {
    RoleAssignment lecturerAssignment = findLecturerAssignment(lecturer);
    if (lecturerAssignment == null) {
      return false;
    }

    UserScope lecScope = lecturerAssignment.getScope();
    List<String> lecProgramIds =
        lecScope != null && lecScope.getProgramIds() != null ? lecScope.getProgramIds() : List.of();
    List<String> lecCourseIds =
        lecScope != null && lecScope.getCourseIds() != null ? lecScope.getCourseIds() : List.of();

    // Nếu giảng viên đã được gán sẵn môn học thuộc phạm vi Bộ môn thì luôn cho phép nhìn thấy / gỡ
    if (targetCourseId != null
        && !targetCourseId.isBlank()
        && lecCourseIds.contains(targetCourseId.trim())) {
      return true;
    }
    if (!deptCourseIds.isEmpty() && lecCourseIds.stream().anyMatch(deptCourseIds::contains)) {
      return true;
    }

    // Lọc theo phạm vi Khoa/Ngành (scope.programIds) giữa Trưởng bộ môn và Giảng viên
    if (!deptProgramIds.isEmpty()) {
      return lecProgramIds.stream().anyMatch(deptProgramIds::contains);
    }

    // Trường hợp Trưởng bộ môn không cấu hình giới hạn programIds: chỉ khớp các giảng viên cũng
    // không bị ràng buộc sang Khoa/Ngành khác
    return lecProgramIds.isEmpty();
  }

  private Set<String> extractDepartmentProgramIds(UserPrincipal principal) {
    Set<String> result = new LinkedHashSet<>();
    if (principal.getRoleAssignments() == null) {
      return result;
    }
    for (RoleAssignment assignment : principal.getRoleAssignments()) {
      if (assignment != null
          && (assignment.getRole() == Role.PROGRAM_MANAGER || assignment.getRole() == Role.ADMIN)
          && assignment.getScope() != null
          && assignment.getScope().getProgramIds() != null) {
        for (String pid : assignment.getScope().getProgramIds()) {
          if (pid != null && !pid.isBlank()) {
            result.add(pid.trim());
          }
        }
      }
    }
    return result;
  }

  private Set<String> extractDepartmentCourseIds(UserPrincipal principal) {
    Set<String> result = new LinkedHashSet<>();
    if (principal.getRoleAssignments() == null) {
      return result;
    }
    for (RoleAssignment assignment : principal.getRoleAssignments()) {
      if (assignment != null
          && (assignment.getRole() == Role.PROGRAM_MANAGER || assignment.getRole() == Role.ADMIN)
          && assignment.getScope() != null
          && assignment.getScope().getCourseIds() != null) {
        for (String cid : assignment.getScope().getCourseIds()) {
          if (cid != null && !cid.isBlank()) {
            result.add(cid.trim());
          }
        }
      }
    }
    return result;
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
