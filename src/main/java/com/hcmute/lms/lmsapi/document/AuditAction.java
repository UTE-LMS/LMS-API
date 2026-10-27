package com.hcmute.lms.lmsapi.document;

/**
 * Danh mục hành động kiểm toán khớp chính xác với $jsonSchema của collection audit_logs
 * trong doc/database_import.js.
 */
public enum AuditAction {
    SCORE_UPDATE,
    IMPORT_CONFIRM,
    ASSESSMENT_APPROVE,
    ASSESSMENT_REJECT,
    RUN_CALCULATION,
    RULE_CONFIRM,
    LOGIN
}
