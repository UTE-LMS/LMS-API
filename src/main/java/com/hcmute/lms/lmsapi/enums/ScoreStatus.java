package com.hcmute.lms.lmsapi.enums;

/**
 * Trạng thái điểm khớp SCORE_STATUS_VALUES của scores (§4.22). SCORED và BLANK có rawScore (BLANK =
 * 0); bốn trạng thái còn lại rawScore = null. F05b chỉ sinh SCORED, BLANK, MISSING từ tệp; ABSENT,
 * NOT_GRADED, EXEMPT chờ Q13 (quy ước đánh dấu trong tệp) nên chưa nhập được qua tệp.
 */
public enum ScoreStatus {
  SCORED,
  BLANK,
  ABSENT,
  NOT_GRADED,
  EXEMPT,
  MISSING
}
