package com.hcmute.lms.lmsapi.enums;

/**
 * Danh mục 4 vai trò người dùng cốt lõi trong hệ thống LMS-API (ADR 0014, DB Schema v0.7). Tinh gọn
 * triệt để từ 7 vai trò cũ (loại bỏ DEPARTMENT_HEAD do bỏ duyệt đề, STUDENT không đăng nhập, và
 * thống nhất cán bộ ĐBCL / khảo thí / xem báo cáo thành QA).
 */
public enum Role {

  /** Quản trị viên hệ thống: toàn quyền trên toàn bộ tài nguyên (READ & WRITE). */
  ADMIN,

  /**
   * Quản lý chương trình đào tạo & học thuật: quản lý CTĐT, đề cương, chuẩn đầu ra (PLO, PI, CLO),
   * phân công học phần, tính toán chuẩn đầu ra cấp CTĐT / Khóa tuyển sinh.
   */
  PROGRAM_MANAGER,

  /**
   * Giảng viên: phụ trách lớp học phần, soạn bài đánh giá, tự khóa ma trận đề, tải CSV mẫu, nhập
   * điểm CSV (Vắng / trống = 0.0), tính toán CLO lớp học phần.
   */
  LECTURER,

  /**
   * Cán bộ Đảm bảo chất lượng / Khảo thí: quyền xem kết quả đo lường, báo cáo chuẩn đầu ra và thống
   * kê chất lượng (READ-only) theo phạm vi phân công.
   */
  QA
}
