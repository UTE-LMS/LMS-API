package com.hcmute.lms.lmsapi.enums;

/**
 * Nguồn của tệp điểm (import_batches.sourceSystem). Tệp .xlsx luôn là EXCEL (hoặc MOODLE_EXPORT nếu
 * người gọi khai báo); EXCEL chỉ hợp lệ với tệp .xlsx. CSV và MOODLE_EXPORT dạng .csv đọc như CSV
 * (ADR 0013, ADR 0015).
 */
public enum ImportSourceSystem {
  EXCEL,
  CSV,
  MOODLE_EXPORT
}
