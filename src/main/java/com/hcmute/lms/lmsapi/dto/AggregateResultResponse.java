package com.hcmute.lms.lmsapi.dto;

import com.hcmute.lms.lmsapi.document.AggregateResult;
import com.hcmute.lms.lmsapi.enums.AssessmentType;
import com.hcmute.lms.lmsapi.enums.ResultLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Phản hồi kết quả tổng hợp theo bài đánh giá / lớp / khóa / CTĐT (F06). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AggregateResultResponse {
  private String id;
  private String runId;
  private ResultLevel level;
  private String subjectId;
  private String subjectCode;
  private String sectionId;
  private String sectionCode;
  private String courseCode;
  private String courseName;
  private String assessmentId;
  private String assessmentName;
  private AssessmentType assessmentType;
  private String termCode;
  private AggregateResult.Scope scope;
  private AggregateResult.Counts counts;
  private int evaluated;
  private int insufficientData;
  private Double mean;
  private Double passRate;
  private Double passRateOfEnrolled;
  private Double passThreshold;
  private Double dataCompleteness;
  private String programVersionId;
  private String programCode;
  private String programName;
  private String programVersion;
}
