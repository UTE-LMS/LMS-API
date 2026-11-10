package com.hcmute.lms.lmsapi.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Thống kê lô nhập (import_batches.stats). {@code scoreCount} là số bản ghi scores sẽ/đã ghi. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportStats {

  private int totalRows;
  private int validRows;
  private int errorRows;
  private int scoreCount;
}
