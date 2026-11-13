package com.hcmute.lms.lmsapi.dto;

import com.hcmute.lms.lmsapi.enums.ImportSourceSystem;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Phản hồi truy vết từ một bản ghi Score về lô nhập và dòng tệp CSV gốc (US-34). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoreTraceResponse {
  private String scoreId;
  private String sectionId;
  private String assessmentId;
  private String itemId;
  private String itemCode;
  private String cloId;
  private String cloCode;
  private String studentId;
  private String studentRef;
  private Double rawScore;
  private Double maxScore;
  private String importBatchId;
  private Integer sourceRow;
  private String fileName;
  private ImportSourceSystem sourceSystem;
  private String importedBy;
  private Instant importedAt;
}
