package com.hcmute.lms.lmsapi.exception;

import org.springframework.http.HttpStatus;

/**
 * Ngoại lệ khi vi phạm các bất biến và quy tắc nghiệp vụ (HTTP 422).
 * Ví dụ: Tổng trọng số bài thi != 1.0, nhập điểm khi bài thi chưa APPROVED, thiếu reason khi sửa điểm...
 */
public class BusinessRuleViolationException extends AppException {

    public BusinessRuleViolationException(String message) {
        super(ErrorCode.BUSINESS_RULE_VIOLATION, HttpStatus.UNPROCESSABLE_ENTITY, message);
    }

    public BusinessRuleViolationException(String message, Throwable cause) {
        super(ErrorCode.BUSINESS_RULE_VIOLATION, HttpStatus.UNPROCESSABLE_ENTITY, message, cause);
    }

    public BusinessRuleViolationException(String ruleName, String message) {
        super(
            ErrorCode.BUSINESS_RULE_VIOLATION,
            HttpStatus.UNPROCESSABLE_ENTITY,
            String.format("[%s] %s", ruleName, message)
        );
    }
}
