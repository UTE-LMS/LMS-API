package com.hcmute.lms.lmsapi.dto;

import com.hcmute.lms.lmsapi.enums.SyllabusStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyllabusStatusUpdateRequest {

  @NotNull(message = "Trạng thái đề cương không được để trống")
  private SyllabusStatus status;
}
