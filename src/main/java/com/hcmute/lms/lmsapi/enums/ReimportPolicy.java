package com.hcmute.lms.lmsapi.enums;

/**
 * Chính sách khi (câu hỏi, sinh viên) đã có điểm (import_batches.reimportPolicy). Chỉ
 * REJECT_ON_CONFLICT được hiện thực; OVERWRITE và UPDATE_BLANK_ONLY chờ Q19 nên bị từ chối 422.
 */
public enum ReimportPolicy {
  REJECT_ON_CONFLICT,
  OVERWRITE,
  UPDATE_BLANK_ONLY
}
