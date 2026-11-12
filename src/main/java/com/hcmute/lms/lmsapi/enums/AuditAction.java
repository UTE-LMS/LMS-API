package com.hcmute.lms.lmsapi.enums;

/**
 * Danh mục hành động kiểm toán khớp chính xác với $jsonSchema của collection audit_logs trong
 * doc/database_import.js (v0.7).
 */
public enum AuditAction {
  SCORE_UPDATE,
  IMPORT_CONFIRM,
  ASSESSMENT_LOCK,
  ASSESSMENT_UNLOCK,
  RUN_CALCULATION,
  LOGIN
}
