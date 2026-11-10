package com.hcmute.lms.lmsapi.enums;

import java.util.Set;

/**
 * Trạng thái bài đánh giá bản lớp (NEW-01, ADR 0001 phương án A): DRAFT -> SUBMITTED -> APPROVED |
 * REJECTED; REJECTED được sửa lại thành DRAFT; APPROVED -> SCORE_ENTERED -> LOCKED do F05b trở đi.
 */
public enum AssessmentStatus {
  DRAFT,
  SUBMITTED,
  APPROVED,
  REJECTED,
  SCORE_ENTERED,
  LOCKED;

  private static final Set<AssessmentStatus> LOCKED_STATUSES =
      Set.of(APPROVED, SCORE_ENTERED, LOCKED);

  /** Trạng thái làm {@code isLocked = true}: cấm sửa items[] (validator của MongoDB ép khớp). */
  public boolean locksStructure() {
    return LOCKED_STATUSES.contains(this);
  }
}
