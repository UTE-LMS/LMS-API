package com.hcmute.lms.lmsapi.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO trả về chi tiết điểm theo từng sinh viên, câu hỏi và CLO của một bài đánh giá. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentScoreItemResponse {

  private String id;
  private String sectionId;
  private String assessmentId;
  private String studentId;
  private String studentCode;
  private String studentName;
  private String itemId;
  private String itemCode;
  private Integer bloomLevel;
  private String cloId;
  private String cloCode;
  private Double rawScore;
  private Double maxScore;
  private String importBatchId;
  private Integer sourceRow;
  private Instant updatedAt;
}
