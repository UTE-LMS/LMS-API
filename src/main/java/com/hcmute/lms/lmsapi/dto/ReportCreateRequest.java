package com.hcmute.lms.lmsapi.dto;

import com.hcmute.lms.lmsapi.enums.ReportType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Yêu cầu sinh và lưu báo cáo từ kết quả đã tính của một calculation run (F07). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportCreateRequest {

  @NotNull(message = "Loại báo cáo (type) không được để trống")
  private ReportType type;

  @NotBlank(message = "runId không được để trống")
  private String runId;

  private String title;
  private String sectionId;
  private String courseId;
  private String cohort;
}
