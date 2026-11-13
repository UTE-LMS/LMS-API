package com.hcmute.lms.lmsapi.dto;

import com.hcmute.lms.lmsapi.enums.ResultInputSourceType;
import com.hcmute.lms.lmsapi.enums.ResultLevel;
import com.hcmute.lms.lmsapi.enums.ResultStatus;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Phản hồi truy vết một cấp từ student_results xuống các đầu vào liền kề (US-34). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentResultTraceResponse {
  private String id;
  private String runId;
  private ResultLevel level;
  private String subjectId;
  private String subjectCode;
  private String studentId;
  private String studentRef;
  private ResultStatus status;
  private Double value;
  private Double passThreshold;
  private Boolean passed;
  private List<TraceInputItem> inputs;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class TraceInputItem {
    private ResultInputSourceType sourceType;
    private String refId;
    private String label;
    private Double normalizedValue;
    private Double weight;
  }
}
