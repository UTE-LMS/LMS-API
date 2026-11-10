package com.hcmute.lms.lmsapi.enums;

/**
 * Trạng thái lô nhập điểm khớp $jsonSchema của import_batches (§4.20): UPLOADED -> VALIDATED |
 * HAS_ERRORS -> CONFIRMED; lô chưa xác nhận có thể bị hủy thành ROLLED_BACK.
 */
public enum ImportBatchStatus {
  UPLOADED,
  VALIDATED,
  HAS_ERRORS,
  CONFIRMED,
  ROLLED_BACK
}
