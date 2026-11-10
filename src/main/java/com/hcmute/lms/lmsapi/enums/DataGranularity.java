package com.hcmute.lms.lmsapi.enums;

/**
 * Độ chi tiết của dữ liệu điểm. {@code TOTAL_ONLY} chỉ có điểm tổng nên <b>không đo được CLO/PI</b>
 * (plan F04 §4 quy tắc 11).
 */
public enum DataGranularity {
  ITEM,
  TOTAL_ONLY
}
