package com.hcmute.lms.lmsapi.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Danh mục mã lỗi thống nhất cho toàn bộ hệ thống LMS API (US-40).
 */
@Getter
public enum ErrorCode {

    /**
     * Lỗi xác thực dữ liệu đầu vào (Bean Validation, body rỗng/sai kiểu, ObjectId sai định dạng).
     * HTTP 400 Bad Request.
     */
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Dữ liệu yêu cầu không hợp lệ"),

    /**
     * Không tìm thấy tài nguyên theo định danh được yêu cầu.
     * HTTP 404 Not Found.
     */
    NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy tài nguyên yêu cầu"),

    /**
     * Yêu cầu chưa được xác thực hoặc thông tin xác thực không hợp lệ.
     * HTTP 401 Unauthorized.
     */
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Yêu cầu xác thực để truy cập tài nguyên"),

    /**
     * Người dùng đã xác thực nhưng không có quyền hạn truy cập tài nguyên hoặc phạm vi yêu cầu.
     * HTTP 403 Forbidden.
     */
    FORBIDDEN(HttpStatus.FORBIDDEN, "Bạn không có quyền truy cập tài nguyên này"),

    /**
     * Xung đột dữ liệu (trùng khóa duy nhất trong CSDL hoặc xung đột phiên bản cập nhật lạc quan revision).
     * HTTP 409 Conflict.
     */
    CONFLICT(HttpStatus.CONFLICT, "Dữ liệu bị xung đột hoặc đã tồn tại trong hệ thống"),

    /**
     * Vi phạm quy tắc nghiệp vụ hoặc vi phạm schema validator của MongoDB ($jsonSchema code 121).
     * HTTP 422 Unprocessable Entity.
     */
    BUSINESS_RULE_VIOLATION(HttpStatus.UNPROCESSABLE_ENTITY, "Vi phạm quy tắc nghiệp vụ hoặc ràng buộc hệ thống"),

    /**
     * Lỗi không lường trước phía máy chủ, thông tin chi tiết được che chắn an toàn.
     * HTTP 500 Internal Server Error.
     */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Đã xảy ra lỗi nội bộ hệ thống");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }
}
