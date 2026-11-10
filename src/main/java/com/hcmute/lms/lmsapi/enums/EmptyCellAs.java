package com.hcmute.lms.lmsapi.enums;

/**
 * Cách hiểu ô điểm để trống, do người nhập chọn cho từng lô (import_batches.emptyCellAs). BLANK: đã
 * dự thi bỏ trống, tính 0 có ghi nhận. MISSING: thiếu dữ liệu, rawScore = null, không được coi là
 * 0. Hệ thống không tự đoán nên không có giá trị mặc định.
 */
public enum EmptyCellAs {
  BLANK,
  MISSING
}
