package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.document.AuditEntity;
import com.hcmute.lms.lmsapi.enums.AuditAction;
import com.hcmute.lms.lmsapi.service.AuditService;

/** Tiện ích gọi {@link AuditService} an toàn ngay cả khi unit test không inject AuditService. */
final class AuditLogHelper {

  private AuditLogHelper() {}

  static void record(
      AuditService auditService,
      AuditAction action,
      String collection,
      String id,
      Object before,
      Object after,
      String reason) {
    if (auditService == null || id == null || id.isBlank()) {
      return;
    }
    auditService.record(
        action, AuditEntity.builder().collection(collection).id(id).build(), before, after, reason);
  }
}
