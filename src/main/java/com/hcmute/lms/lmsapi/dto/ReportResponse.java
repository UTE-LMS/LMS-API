package com.hcmute.lms.lmsapi.dto;

import com.hcmute.lms.lmsapi.document.Report;
import com.hcmute.lms.lmsapi.enums.ReportType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Thông tin báo cáo đã lưu (F07). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportResponse {
  private String id;
  private ReportType type;
  private String title;
  private Map<String, Object> params;
  private String runId;
  private String programVersionId;
  private Map<String, Object> summary;
  private String limitations;
  private List<Report.ReportExport> exports;
  private String generatedBy;
  private Instant createdAt;
}
