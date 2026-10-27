package com.hcmute.lms.lmsapi.document;

/**
 * Danh mục 7 vai trò người dùng trong hệ thống LMS-API (F01).
 * Khớp 100% với danh mục vai trò trong CSDL MongoDB (database_import.js) và ADR 0003.
 */
public enum Role {

    /**
     * Quản trị viên hệ thống: toàn quyền trên toàn bộ tài nguyên (READ & WRITE).
     */
    ADMIN,

    /**
     * Quản lý chương trình đào tạo: quyền xem trong phạm vi CTĐT, học phần và lớp học phụ trách.
     */
    PROGRAM_MANAGER,

    /**
     * Trưởng bộ môn: quyền xem các học phần, lớp học và thẩm quyền duyệt ma trận đề thi (F04).
     */
    DEPARTMENT_HEAD,

    /**
     * Cán bộ bảo đảm chất lượng: quyền xem kết quả đo lường trong phạm vi phụ trách.
     */
    QA_OFFICER,

    /**
     * Giảng viên: quyền nhập điểm, tạo bài đánh giá (WRITE) trên các lớp thuộc phân công hoặc scope.
     */
    LECTURER,

    /**
     * Sinh viên: xem kết quả học tập cá nhân (NEW-03, không có quyền trên tài nguyên lớp trong F01).
     */
    STUDENT,

    /**
     * Người xem/Khảo thí: quyền chỉ đọc (READ-only) theo phạm vi phân công.
     */
    VIEWER
}
