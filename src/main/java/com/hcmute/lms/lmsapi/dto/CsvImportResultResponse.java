package com.hcmute.lms.lmsapi.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Kết quả import dữ liệu hàng loạt qua CSV (best-effort từng dòng). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CsvImportResultResponse {

  private int totalRows;
  private int successCount;
  private int failureCount;
  private List<RowError> errors;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class RowError {
    private int row;
    private String key;
    private String message;
  }
}
