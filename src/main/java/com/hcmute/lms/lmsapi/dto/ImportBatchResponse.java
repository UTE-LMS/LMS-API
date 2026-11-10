package com.hcmute.lms.lmsapi.dto;

import com.hcmute.lms.lmsapi.enums.EmptyCellAs;
import com.hcmute.lms.lmsapi.enums.ImportBatchStatus;
import com.hcmute.lms.lmsapi.enums.ImportSourceSystem;
import com.hcmute.lms.lmsapi.enums.ReimportPolicy;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Lô nhập điểm trả về cho UI (F05b). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportBatchResponse {

  private String id;
  private String sectionId;
  private String assessmentId;
  private String fileName;
  private String fileChecksum;
  private String fileRef;
  private ImportSourceSystem sourceSystem;
  private ImportColumnMappingDto columnMapping;
  private EmptyCellAs emptyCellAs;
  private ReimportPolicy reimportPolicy;
  private ImportBatchStatus status;
  private Stats stats;
  private String uploadedBy;
  private String confirmedBy;
  private Instant confirmedAt;
  private Instant createdAt;

  /** Null cho tới khi lô được kiểm tra (validate). */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Stats {

    private int totalRows;
    private int validRows;
    private int errorRows;
    private int scoreCount;
  }
}
