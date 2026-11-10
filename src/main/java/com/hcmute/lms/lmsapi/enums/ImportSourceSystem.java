package com.hcmute.lms.lmsapi.enums;

/**
 * Nguồn của tệp điểm (import_batches.sourceSystem). F05b chỉ đọc CSV: CSV và MOODLE_EXPORT (đều là
 * CSV) được nhận, EXCEL bị từ chối cho tới khi có ADR về thư viện Excel (ADR 0013, TD-32).
 */
public enum ImportSourceSystem {
  EXCEL,
  CSV,
  MOODLE_EXPORT
}
