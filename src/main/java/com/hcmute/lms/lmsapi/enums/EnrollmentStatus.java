package com.hcmute.lms.lmsapi.enums;

/**
 * Trạng thái ghi danh khớp $jsonSchema của collection enrollments. Chỉ {@code ENROLLED} thuộc mẫu
 * số của tỷ lệ đạt cấp lớp; {@code WITHDRAWN} được giữ lại làm lịch sử, không xóa bản ghi.
 */
public enum EnrollmentStatus {
  ENROLLED,
  WITHDRAWN
}
