package com.hcmute.lms.lmsapi.exception;

import com.hcmute.lms.lmsapi.dto.FieldErrorDetail;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * Ngoại lệ khi tham số phân trang (page, size) vi phạm ràng buộc miền giá trị (HTTP 400 Bad Request).
 * Mang danh sách {@link FieldErrorDetail} theo đúng chuẩn API-CONTRACT §5.1.2.
 */
@Getter
public class PageableValidationException extends AppException {

    private final List<FieldErrorDetail> fieldErrors;

    public PageableValidationException(List<FieldErrorDetail> fieldErrors) {
        super(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "Dữ liệu yêu cầu không hợp lệ");
        this.fieldErrors = fieldErrors != null ? new ArrayList<>(fieldErrors) : new ArrayList<>();
    }

    public PageableValidationException(String field, String message, Object rejectedValue) {
        super(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "Dữ liệu yêu cầu không hợp lệ");
        this.fieldErrors = List.of(FieldErrorDetail.builder()
                .field(field)
                .message(message)
                .rejectedValue(rejectedValue)
                .build());
    }
}
