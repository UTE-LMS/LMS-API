package com.hcmute.lms.lmsapi.enums;

import java.util.Set;

/**
 * Trạng thái bài đánh giá bản lớp (ADR 0014, DB Schema v0.7): DRAFT -> LOCKED -> SCORE_ENTERED.
 * Giảng viên phụ trách khóa/mở khóa ma trận đề.
 */
public enum AssessmentStatus {
  DRAFT,
  LOCKED,
  SCORE_ENTERED;

  private static final Set<AssessmentStatus> LOCKED_STATUSES = Set.of(LOCKED, SCORE_ENTERED);

  /** Trạng thái làm {@code isLocked = true}: cấm sửa items[] (validator của MongoDB ép khớp). */
  public boolean locksStructure() {
    return LOCKED_STATUSES.contains(this);
  }
}
