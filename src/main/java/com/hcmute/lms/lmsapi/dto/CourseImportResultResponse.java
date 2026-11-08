package com.hcmute.lms.lmsapi.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseImportResultResponse {

  private Integer totalRows;
  private Integer successCount;
  private Integer failureCount;
  private List<CourseImportRowError> errors;
}
