package com.hcmute.lms.lmsapi.controller;

import com.hcmute.lms.lmsapi.dto.AuditLogResponse;
import com.hcmute.lms.lmsapi.dto.PageResponse;
import com.hcmute.lms.lmsapi.enums.AuditAction;
import com.hcmute.lms.lmsapi.service.AuditLogQueryService;
import com.hcmute.lms.lmsapi.util.PageableValidationUtils;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller xem nhật ký thao tác (F01 / US-04). Chỉ đọc; việc ghi nhật ký do các nghiệp vụ
 * gọi {@code AuditService}. Chỉ ADMIN được xem, vai trò khác nhận HTTP 403.
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AuditLogController {

  private final AuditLogQueryService auditLogQueryService;

  /**
   * Danh sách nhật ký có phân trang, mới nhất trước (GET /api/v1/audit-logs). Các bộ lọc đều tùy
   * chọn và kết hợp bằng AND.
   *
   * @param action hành động kiểm toán, ví dụ {@code LOGIN}
   * @param entity tên collection của thực thể bị tác động, ví dụ {@code users}
   * @param entityId ID thực thể bị tác động
   * @param userId ObjectId (24 hex) của người thực hiện
   * @param from thời điểm bắt đầu, ISO-8601 (ví dụ {@code 2026-11-01T00:00:00Z}), bao gồm
   * @param to thời điểm kết thúc, ISO-8601, bao gồm
   * @param page chỉ số trang (0-indexed, >= 0, mặc định 0)
   * @param size kích thước trang (1..100, mặc định 20)
   */
  @GetMapping
  public ResponseEntity<PageResponse<AuditLogResponse>> getAuditLogs(
      @RequestParam(required = false) AuditAction action,
      @RequestParam(required = false) @Size(max = 64, message = "Tên collection tối đa 64 ký tự")
          String entity,
      @RequestParam(required = false) @Size(max = 64, message = "ID thực thể tối đa 64 ký tự")
          String entityId,
      @RequestParam(required = false)
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "Định dạng định danh (ObjectId) không hợp lệ")
          String userId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to,
      @RequestParam(defaultValue = "0", required = false)
          @Min(value = 0, message = "Chỉ số trang (page) phải lớn hơn hoặc bằng 0")
          Integer page,
      @RequestParam(defaultValue = "20", required = false)
          @Min(value = 1, message = "Kích thước trang (size) phải lớn hơn hoặc bằng 1")
          @Max(value = 100, message = "Kích thước trang (size) phải nằm trong khoảng từ 1 đến 100")
          Integer size) {

    log.debug("API request: GET /api/v1/audit-logs page [{}] size [{}]", page, size);
    PageableValidationUtils.validate(page, size);
    return ResponseEntity.ok(
        auditLogQueryService.searchAuditLogs(
            userId,
            entity,
            entityId,
            action,
            from,
            to,
            page != null ? page : PageableValidationUtils.DEFAULT_PAGE,
            size != null ? size : PageableValidationUtils.DEFAULT_SIZE));
  }
}
