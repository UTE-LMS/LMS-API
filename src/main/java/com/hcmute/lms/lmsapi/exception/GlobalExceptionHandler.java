package com.hcmute.lms.lmsapi.exception;

import com.hcmute.lms.lmsapi.dto.ErrorResponse;
import com.hcmute.lms.lmsapi.dto.FieldErrorDetail;
import com.mongodb.MongoBulkWriteException;
import com.mongodb.MongoServerException;
import com.mongodb.MongoWriteException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Xử lý ngoại lệ tập trung cho toàn bộ ứng dụng LMS-API (US-40).
 * Đảm bảo mọi phản hồi lỗi đều tuân thủ cấu trúc ErrorResponse thống nhất.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // =========================================================================
    // 1. NHÓM HTTP 400: VALIDATION & INPUT ERRORS (ErrorCode.VALIDATION_ERROR)
    // =========================================================================

    /**
     * Xử lý lỗi Bean Validation trên @RequestBody (@Valid DTO).
     * US-40/AC-1: Trả về HTTP 400, code = VALIDATION_ERROR, kèm danh sách fieldErrors.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        List<FieldErrorDetail> fieldErrors = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(fe ->
                fieldErrors.add(FieldErrorDetail.builder()
                        .field(fe.getField())
                        .message(fe.getDefaultMessage())
                        .rejectedValue(sanitizeRejectedValue(fe.getField(), fe.getRejectedValue()))
                        .build())
        );

        ex.getBindingResult().getGlobalErrors().forEach(ge ->
                fieldErrors.add(FieldErrorDetail.builder()
                        .field(ge.getObjectName())
                        .message(ge.getDefaultMessage())
                        .rejectedValue(null)
                        .build())
        );

        log.warn("Validation error on request [{}]: {} field error(s)", request.getRequestURI(), fieldErrors.size());

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .code(ErrorCode.VALIDATION_ERROR)
                .message("Dữ liệu yêu cầu không hợp lệ")
                .path(request.getRequestURI())
                .fieldErrors(fieldErrors)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Xử lý lỗi validation tham số phương thức trong Spring Boot (HandlerMethodValidationException).
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleHandlerMethodValidation(
            HandlerMethodValidationException ex, HttpServletRequest request) {

        List<FieldErrorDetail> fieldErrors = new ArrayList<>();
        ex.getParameterValidationResults().forEach(result -> {
            String paramName = result.getMethodParameter().getParameterName();
            String cleanFieldName = (paramName != null && paramName.contains("."))
                    ? paramName.substring(paramName.lastIndexOf('.') + 1)
                    : paramName;
            result.getResolvableErrors().forEach(error ->
                    fieldErrors.add(FieldErrorDetail.builder()
                            .field(cleanFieldName)
                            .message(error.getDefaultMessage())
                            .rejectedValue(result.getArgument())
                            .build())
            );
        });

        log.warn("Handler method validation error on request [{}]: {} error(s)", request.getRequestURI(), fieldErrors.size());

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .code(ErrorCode.VALIDATION_ERROR)
                .message("Tham số yêu cầu không hợp lệ")
                .path(request.getRequestURI())
                .fieldErrors(fieldErrors)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Xử lý vi phạm ràng buộc cấp Service/Repository (Jakarta ConstraintViolationException).
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {

        List<FieldErrorDetail> fieldErrors = new ArrayList<>();
        ex.getConstraintViolations().forEach(cv -> {
            String propertyPath = cv.getPropertyPath() != null ? cv.getPropertyPath().toString() : "";
            String fieldName = propertyPath.contains(".") ? propertyPath.substring(propertyPath.lastIndexOf('.') + 1) : propertyPath;
            fieldErrors.add(FieldErrorDetail.builder()
                    .field(fieldName)
                    .message(cv.getMessage())
                    .rejectedValue(cv.getInvalidValue())
                    .build());
        });

        log.warn("Constraint violation on request [{}]: {} violation(s)", request.getRequestURI(), fieldErrors.size());

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .code(ErrorCode.VALIDATION_ERROR)
                .message("Ràng buộc dữ liệu không thỏa mãn")
                .path(request.getRequestURI())
                .fieldErrors(fieldErrors)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Xử lý trường hợp thân yêu cầu rỗng hoặc định dạng JSON sai cú pháp.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {

        log.warn("Malformed or missing HTTP request body on [{}]: {}", request.getRequestURI(), ex.getMessage());

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .code(ErrorCode.VALIDATION_ERROR)
                .message("Thân yêu cầu (body) không đúng định dạng JSON hoặc bị thiếu")
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Xử lý sai kiểu dữ liệu ở path hoặc query param (đặc biệt: ObjectId sai định dạng ở @PathVariable).
     * US-40/AC-1: ObjectId sai định dạng ở path trả về HTTP 400 thay vì 500.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {

        String paramName = ex.getName();
        Object value = ex.getValue();
        Class<?> requiredType = ex.getRequiredType();
        String typeName = requiredType != null ? requiredType.getSimpleName() : "không xác định";

        String userMessage;
        if (requiredType != null && (requiredType.equals(ObjectId.class) || paramName.toLowerCase().endsWith("id"))) {
            userMessage = String.format("Định dạng định danh (ObjectId) '%s' không hợp lệ", value);
        } else {
            userMessage = String.format("Tham số '%s' có giá trị '%s' không đúng định dạng (yêu cầu kiểu: %s)",
                    paramName, value, typeName);
        }

        log.warn("Type mismatch on request [{}]: parameter '{}' with value '{}' expected type '{}'",
                request.getRequestURI(), paramName, value, typeName);

        List<FieldErrorDetail> fieldErrors = List.of(
                FieldErrorDetail.builder()
                        .field(paramName)
                        .message(userMessage)
                        .rejectedValue(value)
                        .build()
        );

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .code(ErrorCode.VALIDATION_ERROR)
                .message(userMessage)
                .path(request.getRequestURI())
                .fieldErrors(fieldErrors)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Xử lý thiếu query parameter bắt buộc.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex, HttpServletRequest request) {

        String paramName = ex.getParameterName();
        log.warn("Missing required parameter '{}' on request [{}]", paramName, request.getRequestURI());

        List<FieldErrorDetail> fieldErrors = List.of(
                FieldErrorDetail.builder()
                        .field(paramName)
                        .message("Tham số này là bắt buộc")
                        .rejectedValue(null)
                        .build()
        );

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .code(ErrorCode.VALIDATION_ERROR)
                .message(String.format("Thiếu tham số bắt buộc: '%s'", paramName))
                .path(request.getRequestURI())
                .fieldErrors(fieldErrors)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Xử lý thiếu header bắt buộc.
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingRequestHeader(
            MissingRequestHeaderException ex, HttpServletRequest request) {

        String headerName = ex.getHeaderName();
        log.warn("Missing required header '{}' on request [{}]", headerName, request.getRequestURI());

        List<FieldErrorDetail> fieldErrors = List.of(
                FieldErrorDetail.builder()
                        .field(headerName)
                        .message("Header này là bắt buộc")
                        .rejectedValue(null)
                        .build()
        );

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .code(ErrorCode.VALIDATION_ERROR)
                .message(String.format("Thiếu header bắt buộc: '%s'", headerName))
                .path(request.getRequestURI())
                .fieldErrors(fieldErrors)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Xử lý IllegalArgumentException từ tầng domain/validator thủ công.
     * Tự động nhận diện nếu liên quan đến vi phạm quy tắc SCORE_UPDATE để trả về 422.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {

        log.warn("IllegalArgumentException on request [{}]: {}", request.getRequestURI(), ex.getMessage());

        ErrorCode code = ErrorCode.VALIDATION_ERROR;
        HttpStatus status = HttpStatus.BAD_REQUEST;

        if (ex.getMessage() != null && ex.getMessage().contains("SCORE_UPDATE")) {
            code = ErrorCode.BUSINESS_RULE_VIOLATION;
            status = HttpStatus.UNPROCESSABLE_ENTITY;
        }

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(status.value())
                .code(code)
                .message(ex.getMessage() != null ? ex.getMessage() : "Đối số yêu cầu không hợp lệ")
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(status).body(response);
    }

    /**
     * Xử lý ngoại lệ vi phạm tham số phân trang page/size (PageableValidationException).
     * US-02 / API-CONTRACT §5.1.2: Trả về HTTP 400, code = VALIDATION_ERROR kèm fieldErrors.
     */
    @ExceptionHandler(PageableValidationException.class)
    public ResponseEntity<ErrorResponse> handlePageableValidation(
            PageableValidationException ex, HttpServletRequest request) {

        log.warn("Pageable validation error on request [{}]: {} field error(s)", request.getRequestURI(), ex.getFieldErrors().size());

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .code(ErrorCode.VALIDATION_ERROR)
                .message("Dữ liệu yêu cầu không hợp lệ")
                .path(request.getRequestURI())
                .fieldErrors(ex.getFieldErrors())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // =========================================================================
    // 2. NHÓM HTTP 401 & 403: BẢO MẬT & PHÂN QUYỀN
    // =========================================================================

    /**
     * Xử lý lỗi xác thực người dùng (HTTP 401 Unauthorized, ErrorCode.UNAUTHORIZED).
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(
            AuthenticationException ex, HttpServletRequest request) {

        log.warn("Authentication failed on request [{}]: {}", request.getRequestURI(), ex.getMessage());

        String message = (ex.getMessage() != null && !ex.getMessage().isBlank())
                ? ex.getMessage()
                : "Chưa xác thực hoặc phiên làm việc đã hết hạn";

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.UNAUTHORIZED.value())
                .code(ErrorCode.UNAUTHORIZED)
                .message(message)
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    /**
     * Xử lý lỗi từ chối truy cập do không đủ quyền hạn/phạm vi (HTTP 403 Forbidden, ErrorCode.FORBIDDEN).
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(
            AccessDeniedException ex, HttpServletRequest request) {

        log.warn("Access denied on request [{}]: {}", request.getRequestURI(), ex.getMessage());

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.FORBIDDEN.value())
                .code(ErrorCode.FORBIDDEN)
                .message("Bạn không có quyền thực hiện thao tác này")
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    // =========================================================================
    // 3. NHÓM HTTP 404: KHÔNG TÌM THẤY TÀI NGUYÊN (ErrorCode.NOT_FOUND)
    // =========================================================================

    /**
     * Xử lý ResourceNotFoundException bắn ra từ các tầng nghiệp vụ/Service.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {

        log.info("Resource not found on request [{}]: {}", request.getRequestURI(), ex.getMessage());

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.NOT_FOUND.value())
                .code(ErrorCode.NOT_FOUND)
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * Xử lý khi client gọi URL không tồn tại (Spring Boot 4 / Spring 6/7 NoResourceFoundException).
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(
            NoResourceFoundException ex, HttpServletRequest request) {

        log.info("No resource found for URL [{}]: {}", request.getRequestURI(), ex.getMessage());

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.NOT_FOUND.value())
                .code(ErrorCode.NOT_FOUND)
                .message(String.format("Không tìm thấy tài nguyên tại đường dẫn: %s", request.getRequestURI()))
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    // =========================================================================
    // 4. NHÓM HTTP 409: XUNG ĐỘT DỮ LIỆU & TRÙNG KHÓA (ErrorCode.CONFLICT)
    // =========================================================================

    /**
     * Xử lý vi phạm chỉ mục duy nhất trong MongoDB (mã lỗi 11000).
     * US-40/AC-3: Khi ghi trùng khóa duy nhất (DuplicateKeyException) -> HTTP 409, code = CONFLICT.
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateKeyException(
            DuplicateKeyException ex, HttpServletRequest request) {

        log.warn("DuplicateKeyException on request [{}]: {}", request.getRequestURI(), ex.getMessage());

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.CONFLICT.value())
                .code(ErrorCode.CONFLICT)
                .message("Dữ liệu bị trùng lặp khóa duy nhất trong hệ thống")
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    /**
     * Xử lý xung đột phiên bản lạc quan (Optimistic Locking failure qua trường revision).
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockingFailure(
            OptimisticLockingFailureException ex, HttpServletRequest request) {

        log.warn("Optimistic locking conflict on request [{}]: {}", request.getRequestURI(), ex.getMessage());

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.CONFLICT.value())
                .code(ErrorCode.CONFLICT)
                .message("Dữ liệu đã bị thay đổi bởi phiên làm việc khác, vui lòng tải lại và thử lại")
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    // =========================================================================
    // 5. NHÓM HTTP 422: VI PHẠM QUY TẮC NGHIỆP VỤ & $jsonSchema (ErrorCode.BUSINESS_RULE_VIOLATION)
    // =========================================================================

    /**
     * Xử lý vi phạm quy tắc nghiệp vụ rõ ràng (BusinessRuleViolationException).
     * US-40/AC-2: Vi phạm quy tắc nghiệp vụ -> HTTP 422, code = BUSINESS_RULE_VIOLATION.
     */
    @ExceptionHandler(BusinessRuleViolationException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRuleViolation(
            BusinessRuleViolationException ex, HttpServletRequest request) {

        log.warn("Business rule violation on request [{}]: {}", request.getRequestURI(), ex.getMessage());

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.UNPROCESSABLE_ENTITY.value())
                .code(ErrorCode.BUSINESS_RULE_VIOLATION)
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
    }

    /**
     * Xử lý ngoại lệ vi phạm ràng buộc toàn vẹn dữ liệu từ MongoDB:
     * - Bắt lỗi vi phạm $jsonSchema validator (MongoDB error code 121) -> HTTP 422.
     * - Bắt lỗi trùng khóa duy nhất (code 11000 nếu lọt vào đây) -> HTTP 409.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex, HttpServletRequest request) {

        if (isMongoDocumentValidationFailure(ex)) {
            log.warn("MongoDB $jsonSchema validator failure (code 121) on request [{}]: {}",
                    request.getRequestURI(), ex.getMessage());

            ErrorResponse response = ErrorResponse.builder()
                    .timestamp(Instant.now())
                    .status(HttpStatus.UNPROCESSABLE_ENTITY.value())
                    .code(ErrorCode.BUSINESS_RULE_VIOLATION)
                    .message("Dữ liệu không thỏa mãn ràng buộc schema của cơ sở dữ liệu (mã 121)")
                    .path(request.getRequestURI())
                    .build();

            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
        }

        if (isMongoDuplicateKeyError(ex)) {
            log.warn("MongoDB duplicate key error (code 11000) inside DataIntegrityViolation on [{}]: {}",
                    request.getRequestURI(), ex.getMessage());

            ErrorResponse response = ErrorResponse.builder()
                    .timestamp(Instant.now())
                    .status(HttpStatus.CONFLICT.value())
                    .code(ErrorCode.CONFLICT)
                    .message("Dữ liệu bị trùng lặp khóa duy nhất trong hệ thống")
                    .path(request.getRequestURI())
                    .build();

            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }

        return handleAllUncaughtException(ex, request);
    }

    // =========================================================================
    // 6. NHÓM ĐIỀU PHỐI CHUNG & HTTP 500: LỖI NỘI BỘ (ErrorCode.INTERNAL_ERROR)
    // =========================================================================

    /**
     * Xử lý ngoại lệ ứng dụng tổng quát kế thừa từ AppException.
     */
    @ExceptionHandler(AppException.class)
    public ResponseEntity<ErrorResponse> handleAppException(
            AppException ex, HttpServletRequest request) {

        HttpStatus status = ex.getHttpStatus() != null ? ex.getHttpStatus() : HttpStatus.INTERNAL_SERVER_ERROR;
        ErrorCode code = ex.getErrorCode() != null ? ex.getErrorCode() : ErrorCode.INTERNAL_ERROR;

        log.warn("AppException [{} - {}] on request [{}]: {}", code, status, request.getRequestURI(), ex.getMessage());

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(status.value())
                .code(code)
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(status).body(response);
    }

    /**
     * Xử lý tất cả các ngoại lệ không lường trước (Fallback Handler).
     * US-40/AC-4: Trả về HTTP 500, code = INTERNAL_ERROR, tuyệt đối KHÔNG chứa stack trace hay tên class nội bộ.
     * Log chi tiết đầy đủ ở phía server để phục vụ điều tra sự cố.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAllUncaughtException(
            Exception ex, HttpServletRequest request) {

        // Ghi log chi tiết kèm đầy đủ stack trace ở phía server
        log.error("Unhandled internal server error occurred while processing request [{}]:", request.getRequestURI(), ex);

        // Che chắn an toàn tuyệt đối khi gửi về cho client
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .code(ErrorCode.INTERNAL_ERROR)
                .message("Đã xảy ra lỗi hệ thống không mong muốn. Vui lòng liên hệ quản trị viên.")
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    // =========================================================================
    // 7. HELPER METHODS ĐỂ BÓC TÁCH NGUYÊN NHÂN TỪ MONGODB DRIVER & SANITIZE
    // =========================================================================

    private boolean isMongoDocumentValidationFailure(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof MongoServerException mse && mse.getCode() == 121) {
                return true;
            }
            if (current instanceof MongoWriteException mwe && mwe.getError().getCode() == 121) {
                return true;
            }
            if (current instanceof MongoBulkWriteException mbwe) {
                for (var err : mbwe.getWriteErrors()) {
                    if (err.getCode() == 121) return true;
                }
            }
            String message = current.getMessage();
            if (message != null && (message.contains("code 121") || message.contains("Document failed validation") || message.contains("DocumentValidationFailure"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isMongoDuplicateKeyError(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof MongoServerException mse && mse.getCode() == 11000) {
                return true;
            }
            if (current instanceof MongoWriteException mwe && mwe.getError().getCode() == 11000) {
                return true;
            }
            if (current instanceof MongoBulkWriteException mbwe) {
                for (var err : mbwe.getWriteErrors()) {
                    if (err.getCode() == 11000) return true;
                }
            }
            String message = current.getMessage();
            if (message != null && (message.contains("code 11000") || message.contains("duplicate key") || message.contains("E11000"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private Object sanitizeRejectedValue(String field, Object value) {
        if (field != null && field.toLowerCase().matches(".*(password|hash|token|secret).*")) {
            return "[PROTECTED]";
        }
        return value;
    }
}
