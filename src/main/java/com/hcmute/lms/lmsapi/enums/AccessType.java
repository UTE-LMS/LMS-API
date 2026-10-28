package com.hcmute.lms.lmsapi.enums;

/**
 * Loại hành vi truy cập tài nguyên lớp học phần trong ma trận phân quyền phạm vi (F01 / ADR 0003).
 */
public enum AccessType {

    /**
     * Hành vi Đọc dữ liệu: Xem thông tin lớp học, danh sách sinh viên ghi danh, bảng điểm và kết quả đo lường.
     */
    READ,

    /**
     * Hành vi Ghi dữ liệu: Nhập điểm sinh viên, cập nhật điểm, lập và chỉnh sửa bài đánh giá của lớp học phần.
     */
    WRITE
}
