package com.hcmute.lms.lmsapi.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hcmute.lms.lmsapi.document.AuditLog;
import com.hcmute.lms.lmsapi.dto.AuditLogResponse;
import com.hcmute.lms.lmsapi.dto.PageResponse;
import com.hcmute.lms.lmsapi.enums.AuditAction;
import com.hcmute.lms.lmsapi.exception.PageableValidationException;
import com.hcmute.lms.lmsapi.mapper.AuditLogMapper;
import com.hcmute.lms.lmsapi.repository.AuditLogRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@DisplayName("US-04: AuditLogQueryServiceImpl")
class AuditLogQueryServiceImplTest {

  @Mock private AuditLogRepository auditLogRepository;
  @Mock private AuditLogMapper auditLogMapper;
  @InjectMocks private AuditLogQueryServiceImpl service;

  @Test
  @DisplayName("US-04/AC-1: chuyển tiêu chí cho repository và ánh xạ từng dòng sang DTO")
  void delegatesToRepositoryAndMapsRows() {
    Instant from = Instant.parse("2026-10-01T00:00:00Z");
    Instant to = Instant.parse("2026-10-02T00:00:00Z");
    AuditLog row = AuditLog.builder().id("1").build();
    AuditLogResponse dto = AuditLogResponse.builder().id("1").build();
    Pageable pageable = PageRequest.of(1, 5);
    when(auditLogRepository.searchLogs(
            "507f1f77bcf86cd799439011", "users", "x", AuditAction.LOGIN, from, to, pageable))
        .thenReturn(new PageImpl<>(List.of(row), pageable, 6));
    when(auditLogMapper.toResponse(row)).thenReturn(dto);

    PageResponse<AuditLogResponse> result =
        service.searchAuditLogs(
            "507f1f77bcf86cd799439011", "users", "x", AuditAction.LOGIN, from, to, 1, 5);

    assertThat(result.getContent()).containsExactly(dto);
    assertThat(result.getPage()).isEqualTo(1);
    assertThat(result.getSize()).isEqualTo(5);
    assertThat(result.getTotalElements()).isEqualTo(6);
    assertThat(result.getTotalPages()).isEqualTo(2);
    assertThat(result.isLast()).isTrue();
  }

  @Test
  @DisplayName("US-04/AC-5: from sau to bị từ chối và không truy vấn CSDL")
  void rejectsFromAfterTo() {
    Instant from = Instant.parse("2026-10-03T00:00:00Z");
    Instant to = Instant.parse("2026-10-01T00:00:00Z");

    assertThatThrownBy(() -> service.searchAuditLogs(null, null, null, null, from, to, 0, 20))
        .isInstanceOf(PageableValidationException.class);

    verify(auditLogRepository, never())
        .searchLogs(any(), any(), any(), any(), any(), any(), any(Pageable.class));
  }

  @Test
  @DisplayName("US-04/AC-5: from bằng to vẫn hợp lệ (khoảng chỉ một thời điểm)")
  void acceptsFromEqualTo() {
    Instant t = Instant.parse("2026-10-01T00:00:00Z");
    when(auditLogRepository.searchLogs(
            eq(null), eq(null), eq(null), eq(null), eq(t), eq(t), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

    PageResponse<AuditLogResponse> result =
        service.searchAuditLogs(null, null, null, null, t, t, 0, 20);

    assertThat(result.getContent()).isEmpty();
    assertThat(result.isLast()).isTrue();
  }

  @Test
  @DisplayName("US-04/AC-5: size ngoài 1..100 hoặc page âm bị từ chối")
  void rejectsOutOfRangePaging() {
    assertThatThrownBy(() -> service.searchAuditLogs(null, null, null, null, null, null, -1, 20))
        .isInstanceOf(PageableValidationException.class);
    assertThatThrownBy(() -> service.searchAuditLogs(null, null, null, null, null, null, 0, 101))
        .isInstanceOf(PageableValidationException.class);
  }
}
