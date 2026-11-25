package com.hcmute.lms.lmsapi.service;

import com.hcmute.lms.lmsapi.dto.AssessmentScoreTemplate;
import com.hcmute.lms.lmsapi.dto.CsvImportResultResponse;
import com.hcmute.lms.lmsapi.dto.EnrollmentImportResponse;
import com.hcmute.lms.lmsapi.dto.EnrollmentListResponse;
import com.hcmute.lms.lmsapi.dto.EnrollmentResponse;
import com.hcmute.lms.lmsapi.dto.EnrollmentStatusUpdateRequest;
import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import com.hcmute.lms.lmsapi.enums.EnrollmentStatus;
import java.io.InputStream;
import org.springframework.web.multipart.MultipartFile;

/** Ghi danh sinh viên vào lớp học phần (F05a / US-20). */
public interface EnrollmentService {

  /**
   * Danh sách ghi danh của lớp (có phân trang, lọc trạng thái và tìm kiếm theo mã/họ tên sinh viên)
   * kèm tổng số đếm ENROLLED/WITHDRAWN toàn lớp.
   *
   * @param status lọc theo trạng thái (null: lấy cả hai)
   * @param search tìm theo mã SV hoặc họ tên (không phân biệt hoa thường, null: không lọc)
   * @param page chỉ số trang (0-indexed)
   * @param size kích thước trang (1..100)
   */
  EnrollmentListResponse getEnrollments(
      String sectionId, EnrollmentStatus status, String search, int page, int size);

  /**
   * Nhập danh sách lớp từ tệp CSV (US-20/AC-1..AC-3): chỉ ghi danh ENROLLED những sinh viên đã tồn
   * tại trong hệ thống; mã sinh viên chưa tồn tại được báo lỗi theo dòng. Một thao tác thống nhất:
   * có dòng lỗi thì không ghi gì và trả lỗi theo từng dòng; thành công thì cập nhật sĩ số của lớp.
   * Chỉ được phép khi lớp và học kỳ còn OPEN.
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

  AssessmentScoreTemplate generateTemplateCsv();

  AssessmentScoreTemplate generateBulkTemplateCsv();

  CsvImportResultResponse importEnrollmentsBulk(MultipartFile file, UserPrincipal principal);
}
