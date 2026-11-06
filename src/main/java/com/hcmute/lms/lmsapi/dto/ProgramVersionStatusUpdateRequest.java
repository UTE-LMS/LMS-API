package com.hcmute.lms.lmsapi.dto;

import com.hcmute.lms.lmsapi.enums.ProgramVersionStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProgramVersionStatusUpdateRequest {

  @NotNull(message = "Trạng thái phiên bản không được để trống")
  private ProgramVersionStatus status;
}
