package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.document.AuditLog;
import com.hcmute.lms.lmsapi.dto.AuditLogResponse;
import com.hcmute.lms.lmsapi.dto.PageResponse;
import com.hcmute.lms.lmsapi.enums.AuditAction;
import com.hcmute.lms.lmsapi.exception.PageableValidationException;
import com.hcmute.lms.lmsapi.mapper.AuditLogMapper;
import com.hcmute.lms.lmsapi.repository.AuditLogRepository;
import com.hcmute.lms.lmsapi.service.AuditLogQueryService;
import com.hcmute.lms.lmsapi.util.PageableValidationUtils;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/** Hiện thực {@link AuditLogQueryService}: kiểm tra tham số, truy vấn repository, ánh xạ DTO. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogQueryServiceImpl implements AuditLogQueryService {

  private final AuditLogRepository auditLogRepository;
  private final AuditLogMapper auditLogMapper;

  @Override
  public PageResponse<AuditLogResponse> searchAuditLogs(
      String userId,
      String entityCollection,
      String entityId,
      AuditAction action,
      Instant from,
      Instant to,
      int page,
      int size) {
    Pageable pageable = PageableValidationUtils.createPageable(page, size);

    if (from != null && to != null && from.isAfter(to)) {
      throw new PageableValidationException(
          "from", "Thời điểm bắt đầu (from) không được sau thời điểm kết thúc (to)", from);
    }

    log.debug(
        "Searching audit logs: userId={} entity={}:{} action={} from={} to={} page={} size={}",
        userId,
        entityCollection,
        entityId,
        action,
        from,
        to,
        page,
        size);

    Page<AuditLog> result =
        auditLogRepository.searchLogs(
            userId, entityCollection, entityId, action, from, to, pageable);
    List<AuditLogResponse> content =
        result.getContent().stream().map(auditLogMapper::toResponse).toList();

    return PageResponse.of(
        content, result.getNumber(), result.getSize(), result.getTotalElements());
  }
}
