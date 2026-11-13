package com.hcmute.lms.lmsapi.dto;

import com.hcmute.lms.lmsapi.document.CalculationRun;
import com.hcmute.lms.lmsapi.enums.CalculationScope;
import com.hcmute.lms.lmsapi.enums.CalculationStatus;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Thông tin phản hồi của một lượt chạy tính toán (F06). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalculationRunResponse {
  private String id;
  private CalculationScope scope;
  private CalculationRun.ScopeRef scopeRef;
  private String lockKey;
  private String programVersionId;
  private List<String> syllabusIds;
  private boolean official;
  private CalculationRun.DataSnapshot dataSnapshot;
  private boolean stale;
  private String staleReason;
  private CalculationStatus status;
  private String errorMessage;
  private String triggeredBy;
  private Instant startedAt;
  private Instant finishedAt;
  private long studentResultCount;
  private long aggregateResultCount;
}
