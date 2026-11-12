package com.hcmute.lms.lmsapi.dto;

import com.hcmute.lms.lmsapi.enums.ImportSourceSystem;
import com.hcmute.lms.lmsapi.enums.ReimportPolicy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Tham số tạo lô nhập (ADR 0014, v0.7). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportBatchCreateRequest {

  private ImportSourceSystem sourceSystem;

  private ReimportPolicy reimportPolicy;

  private ImportColumnMappingDto columnMapping;
}
