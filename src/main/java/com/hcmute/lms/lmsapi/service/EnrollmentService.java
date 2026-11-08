package com.hcmute.lms.lmsapi.service;

import com.hcmute.lms.lmsapi.dto.EnrollmentListResponse;
import com.hcmute.lms.lmsapi.dto.EnrollmentResponse;
import com.hcmute.lms.lmsapi.dto.EnrollmentStatusUpdateRequest;
import com.hcmute.lms.lmsapi.enums.EnrollmentStatus;

/** Ghi danh sinh viên vào lớp học phần (F05a / US-20). */
public interface EnrollmentService {

  /**
   * Danh sách ghi danh của lớp kèm số đếm ENROLLED/WITHDRAWN.
   *
   * @param status lọc theo trạng thái (null: lấy cả hai)
   */
  EnrollmentListResponse getEnrollments(String sectionId, EnrollmentStatus status);

  /**
   * Rút khỏi lớp hoặc ghi danh lại (US-20/AC-4); tính lại sĩ số của lớp trong cùng giao dịch. Chỉ
   * được phép khi lớp và học kỳ còn OPEN.
   */
  EnrollmentResponse updateStatus(
      String enrollmentId, EnrollmentStatusUpdateRequest request, String actorUsername);
}
