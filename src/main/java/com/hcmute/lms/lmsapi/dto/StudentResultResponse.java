package com.hcmute.lms.lmsapi.dto;

import com.hcmute.lms.lmsapi.document.StudentResult;
import com.hcmute.lms.lmsapi.enums.ResultLevel;
import com.hcmute.lms.lmsapi.enums.ResultStatus;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Phản hồi kết quả đo lường của từng sinh viên (F06). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentResultResponse {
  private String id;
  private String runId;
  private ResultLevel level;
  private String subjectId;
  private String subjectCode;
  private String sectionId;
  private String assessmentId;
  private String studentId;
  private String studentRef;
  private String termId;
  private ResultStatus status;
  private Double value;
  private Double passThreshold;
  private Boolean passed;
  private StudentResult.Completeness completeness;
  private List<StudentResult.ResultInput> inputs;
  private String programVersionId;
  private String syllabusId;
}
