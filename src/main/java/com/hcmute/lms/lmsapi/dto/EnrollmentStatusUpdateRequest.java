package com.hcmute.lms.lmsapi.dto;

import com.hcmute.lms.lmsapi.enums.EnrollmentStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Yêu cầu rút khỏi lớp (WITHDRAWN) hoặc ghi danh lại (ENROLLED) (US-20/AC-4). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnrollmentStatusUpdateRequest {

  @NotNull(message = "Trạng thái ghi danh không được để trống")
  private EnrollmentStatus status;
}
