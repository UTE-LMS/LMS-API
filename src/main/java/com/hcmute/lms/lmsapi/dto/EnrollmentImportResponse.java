package com.hcmute.lms.lmsapi.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Kết quả nhập danh sách lớp thành công (US-20/AC-1). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnrollmentImportResponse {

  private String sectionId;

  /** Số dòng dữ liệu hợp lệ trong tệp, cũng là số ghi danh mới. */
  private int totalRows;

  /** Số sinh viên mới được tạo. */
  private int newStudents;

  /** Số sinh viên đã có sẵn trong hệ thống và được dùng lại (không tạo trùng). */
  private int reusedStudents;

  /** Sĩ số (số ENROLLED) của lớp sau khi nhập, tức mẫu số của tỷ lệ đạt. */
  private int sectionEnrolledCount;

  /** Tên các cột trong tệp bị bỏ qua vì hệ thống không lưu (ví dụ họ tên, email). */
  private List<String> ignoredColumns;
}
