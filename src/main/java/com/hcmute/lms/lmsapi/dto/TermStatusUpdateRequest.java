package com.hcmute.lms.lmsapi.dto;

import com.hcmute.lms.lmsapi.enums.TermStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TermStatusUpdateRequest {

  @NotNull(message = "Trạng thái học kỳ không được để trống")
  private TermStatus status;

  /**
   * Lý do giải trình bắt buộc khi mở lại học kỳ đã bị khóa sổ điểm (SCORE_LOCKED -> OPEN) per
   * US-18/AC-3.
   */
  private String reason;
}
