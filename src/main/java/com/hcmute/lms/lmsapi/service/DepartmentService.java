package com.hcmute.lms.lmsapi.service;

import com.hcmute.lms.lmsapi.dto.AssignCourseRequest;
import com.hcmute.lms.lmsapi.dto.UserResponse;

public interface DepartmentService {

  /**
   * Phân công môn học cho Giảng viên biên soạn đề cương (ADR 0010). Thêm {@code courseId} vào
   * {@code scope.courseIds} của vai trò LECTURER của người dùng đích.
   *
   * @param request thông tin môn học và giảng viên
   * @return thông tin người dùng giảng viên sau khi phân công
   */
  UserResponse assignCourseToLecturer(AssignCourseRequest request);

  /**
   * Hủy phân công môn học khỏi Giảng viên.
   *
   * @param request thông tin môn học và giảng viên
   * @return thông tin người dùng giảng viên sau khi hủy phân công
   */
  UserResponse unassignCourseFromLecturer(AssignCourseRequest request);
}
