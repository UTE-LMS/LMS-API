package com.hcmute.lms.lmsapi.dto;

import com.hcmute.lms.lmsapi.document.AuditEntity;
import com.hcmute.lms.lmsapi.enums.AuditAction;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO trả về một dòng nhật ký thao tác (F01 / US-04). {@code before}/{@code after} đã được làm sạch
 * trường nhạy cảm (mật khẩu, token, secret...) trước khi ra khỏi API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogResponse {

  private String id;

  /** Thời điểm ghi nhận hành động. */
  private Instant at;

  /** ID người thực hiện; {@code null} nếu hành động không gắn với người dùng đã xác thực. */
  private String userId;

  private AuditAction action;

  /** Thực thể bị tác động {@code { collection, id }}. */
  private AuditEntity entity;

  private Object before;

  private Object after;

  private String reason;

  private String ip;
}
