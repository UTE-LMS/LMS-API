package com.hcmute.lms.lmsapi.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Danh sách ghi danh của một lớp kèm số đếm. {@code enrolled} là mẫu số của tỷ lệ đạt cấp lớp;
 * {@code withdrawn} không thuộc mẫu số nhưng vẫn được liệt kê để truy vết.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnrollmentListResponse {

  private String sectionId;
  private long enrolled;
  private long withdrawn;
  private List<EnrollmentResponse> enrollments;
}
