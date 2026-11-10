package com.hcmute.lms.lmsapi.enums;

/** Loại lỗi theo dòng, khớp enum đóng của import_row_errors.errorType (§4.21). */
public enum ImportErrorType {
  INVALID_STUDENT_CODE,
  SCORE_OUT_OF_RANGE,
  DUPLICATE_RECORD,
  MISSING_FIELD
}
