package com.hcmute.lms.lmsapi.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.hcmute.lms.lmsapi.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Cấu trúc JSON chuẩn trả về khi xảy ra lỗi (US-40).
 * Đặt tại package com.hcmute.lms.lmsapi.dto theo chuẩn Single Responsibility Principle (SRP).
 * <pre>
 * {
 *   "timestamp": "2026-10-27T05:00:00Z",
 *   "status": 400,
 *   "code": "VALIDATION_ERROR",
 *   "message": "Dữ liệu yêu cầu không hợp lệ",
 *   "path": "/api/v1/resource",
 *   "fieldErrors": [ ... ]
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {

    /**
     * Thời điểm xảy ra lỗi, định dạng chuẩn ISO-8601 UTC (ví dụ: "2026-10-27T05:00:00Z").
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant timestamp;

    /**
     * Mã trạng thái HTTP (400, 401, 403, 404, 409, 422, 500).
     */
    private int status;

    /**
     * Hằng enum mã lỗi nghiệp vụ.
     */
    private ErrorCode code;

    /**
     * Thông điệp giải thích lỗi thân thiện với người dùng/frontend.
     */
    private String message;

    /**
     * URI của yêu cầu gây ra lỗi (ví dụ: "/api/courses/123").
     */
    private String path;

    /**
     * Danh sách lỗi chi tiết theo từng trường (nếu có; bỏ qua khi null hoặc rỗng).
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<FieldErrorDetail> fieldErrors;
}
