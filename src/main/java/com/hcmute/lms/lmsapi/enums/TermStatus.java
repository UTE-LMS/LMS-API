package com.hcmute.lms.lmsapi.enums;

/**
 * Trạng thái học kỳ khớp với $jsonSchema của collection terms trong doc/database_import.js (§4.16).
 */
public enum TermStatus {
  OPEN,
  SCORE_LOCKED,
  CLOSED
}
