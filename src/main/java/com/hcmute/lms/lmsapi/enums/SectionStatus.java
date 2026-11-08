package com.hcmute.lms.lmsapi.enums;

/**
 * Trạng thái lớp học phần khớp với $jsonSchema của collection course_sections trong
 * doc/database_import.js (§4.17). {@code LOCKED} khóa nhập/sửa điểm của riêng lớp đó (F05b kiểm
 * tra); cả {@code CLOSED} và {@code LOCKED} đều đóng băng danh sách ghi danh (mẫu số của tỷ lệ
 * đạt).
 */
public enum SectionStatus {
  OPEN,
  CLOSED,
  LOCKED
}
