package com.hcmute.lms.lmsapi.service;

import com.hcmute.lms.lmsapi.dto.EnrollmentImportResponse;
import com.hcmute.lms.lmsapi.dto.EnrollmentListResponse;
import com.hcmute.lms.lmsapi.dto.EnrollmentResponse;
import com.hcmute.lms.lmsapi.dto.EnrollmentStatusUpdateRequest;
import com.hcmute.lms.lmsapi.enums.EnrollmentStatus;
import java.io.InputStream;

/** Ghi danh sinh viên vào lớp học phần (F05a / US-20). */
public interface EnrollmentService {

  /**
   * Danh sách ghi danh của lớp kèm số đếm ENROLLED/WITHDRAWN.
   *
   * @param status lọc theo trạng thái (null: lấy cả hai)
   */
  EnrollmentListResponse getEnrollments(String sectionId, EnrollmentStatus status);

  /**
   * Nhập danh sách lớp từ tệp CSV (US-20/AC-1..AC-3): sinh viên mới được tạo, sinh viên đã có được
   * dùng lại, tất cả được ghi danh ENROLLED. Một thao tác thống nhất: có dòng lỗi thì không ghi gì
   * và trả lỗi theo từng dòng; thành công thì cập nhật sĩ số của lớp. Chỉ được phép khi lớp và học
   * kỳ còn OPEN.
   *
   * @param csv nội dung tệp CSV (UTF-8); luồng do người gọi đóng
   */
  EnrollmentImportResponse importEnrollments(String sectionId, InputStream csv, String actor);

  /**
   * Rút khỏi lớp hoặc ghi danh lại (US-20/AC-4); tính lại sĩ số của lớp trong cùng giao dịch. Chỉ
   * được phép khi lớp và học kỳ còn OPEN.
   */
  EnrollmentResponse updateStatus(
      String enrollmentId, EnrollmentStatusUpdateRequest request, String actorUsername);
}
