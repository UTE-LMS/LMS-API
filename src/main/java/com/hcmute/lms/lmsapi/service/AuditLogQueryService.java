package com.hcmute.lms.lmsapi.service;

import com.hcmute.lms.lmsapi.dto.AuditLogResponse;
import com.hcmute.lms.lmsapi.dto.PageResponse;
import com.hcmute.lms.lmsapi.enums.AuditAction;
import java.time.Instant;

/**
 * Dịch vụ đọc/lọc nhật ký thao tác (F01 / US-04). Tách khỏi {@link AuditService} (chỉ ghi) để mỗi
 * interface một trách nhiệm.
 */
public interface AuditLogQueryService {

  /**
   * Tìm nhật ký theo các tiêu chí tùy chọn, mới nhất trước.
   *
   * @param userId ObjectId (hex) người thực hiện (tùy chọn)
   * @param entityCollection tên collection của thực thể bị tác động (tùy chọn)
   * @param entityId ID thực thể bị tác động (tùy chọn)
   * @param action hành động kiểm toán (tùy chọn)
   * @param from thời điểm bắt đầu, bao gồm (tùy chọn)
   * @param to thời điểm kết thúc, bao gồm (tùy chọn)
   * @param page chỉ số trang, zero-indexed
   * @param size kích thước trang, 1..100
   * @throws com.hcmute.lms.lmsapi.exception.PageableValidationException nếu page/size ngoài biên
   *     hoặc {@code from} sau {@code to} (HTTP 400)
   */
  PageResponse<AuditLogResponse> searchAuditLogs(
      String userId,
      String entityCollection,
      String entityId,
      AuditAction action,
      Instant from,
      Instant to,
      int page,
      int size);
}
