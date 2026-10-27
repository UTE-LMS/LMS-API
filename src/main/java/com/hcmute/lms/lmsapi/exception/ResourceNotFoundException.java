package com.hcmute.lms.lmsapi.exception;

import org.springframework.http.HttpStatus;

/**
 * Ngoại lệ khi không tìm thấy tài nguyên trong hệ thống (HTTP 404).
 */
public class ResourceNotFoundException extends AppException {

    public ResourceNotFoundException(String message) {
        super(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, message);
    }

    public ResourceNotFoundException(String message, Throwable cause) {
        super(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, message, cause);
    }

    public ResourceNotFoundException(String resourceName, String fieldName, Object fieldValue) {
        super(
            ErrorCode.NOT_FOUND,
            HttpStatus.NOT_FOUND,
            String.format("%s không tìm thấy với %s: '%s'", resourceName, fieldName, fieldValue)
        );
    }
}
