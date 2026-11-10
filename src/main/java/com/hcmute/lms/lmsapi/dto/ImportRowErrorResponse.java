package com.hcmute.lms.lmsapi.dto;

import com.hcmute.lms.lmsapi.enums.ImportErrorType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Một lỗi theo dòng/cột của tệp điểm (US-22). {@code column} null khi lỗi của cả dòng. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportRowErrorResponse {

  private int rowNumber;
  private String column;
  private ImportErrorType errorType;
  private String rawValue;
  private String message;
}
