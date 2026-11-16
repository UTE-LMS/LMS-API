package com.hcmute.lms.lmsapi.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Kết quả import khung chương trình học phần theo học kỳ vào phiên bản CTĐT. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProgramCourseCsvImportResponse {
  private String programVersionId;
  private int importedCount;
  private int updatedCount;
  private List<String> warnings;
  private List<ProgramCourseResponse> courses;
}
