package com.hcmute.lms.lmsapi.dto;

import com.hcmute.lms.lmsapi.enums.SectionStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Yêu cầu đổi trạng thái lớp học phần (US-19). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SectionStatusUpdateRequest {

  @NotNull(message = "Trạng thái lớp học phần không được để trống")
  private SectionStatus status;

  /** Lý do giải trình, bắt buộc khi mở lại lớp đã khóa sổ điểm (LOCKED -> OPEN/CLOSED). */
  private String reason;
}
