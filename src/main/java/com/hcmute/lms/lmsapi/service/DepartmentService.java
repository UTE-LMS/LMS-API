package com.hcmute.lms.lmsapi.service;

import com.hcmute.lms.lmsapi.dto.AssignCourseRequest;
import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import com.hcmute.lms.lmsapi.dto.UserResponse;
import java.util.List;

public interface DepartmentService {

  /**
   * Lấy danh sách Giảng viên (LECTURER đang ACTIVE) thuộc phạm vi Khoa/Ngành (scope.programIds) của
   * Trưởng bộ môn (hoặc toàn bộ nếu người gọi là ADMIN) để phục vụ phân công đề cương (ADR 0010).
   *
   * @param principal thông tin người dùng đang thực hiện yêu cầu
   * @param courseId mã định danh học phần (tùy chọn) để kiểm tra sự tồn tại của học phần
   * @return danh sách thông tin giảng viên thuộc phạm vi
   */
  List<UserResponse> getDepartmentLecturers(UserPrincipal principal, String courseId);

  /**
   * Phân công môn học cho Giảng viên biên soạn đề cương (ADR 0010). Thêm {@code courseId} vào
   * {@code scope.courseIds} của vai trò LECTURER của người dùng đích, đồng thời kiểm tra giảng viên
   * phải thuộc cùng phạm vi Khoa/Ngành ({@code scope.programIds}) với Trưởng bộ môn.
   *
   * @param request thông tin môn học và giảng viên
   * @param principal thông tin người dùng đang thực hiện yêu cầu
   * @return thông tin người dùng giảng viên sau khi phân công
   */
  UserResponse assignCourseToLecturer(AssignCourseRequest request, UserPrincipal principal);

  default UserResponse assignCourseToLecturer(AssignCourseRequest request) {
    return assignCourseToLecturer(request, null);
  }

  /**
   * Hủy phân công môn học khỏi Giảng viên.
   *
   * @param request thông tin môn học và giảng viên
   * @param principal thông tin người dùng đang thực hiện yêu cầu
   * @return thông tin người dùng giảng viên sau khi hủy phân công
   */
  UserResponse unassignCourseFromLecturer(AssignCourseRequest request, UserPrincipal principal);

  default UserResponse unassignCourseFromLecturer(AssignCourseRequest request) {
    return unassignCourseFromLecturer(request, null);
  }
}
