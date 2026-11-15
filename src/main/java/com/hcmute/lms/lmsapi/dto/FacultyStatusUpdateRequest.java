package com.hcmute.lms.lmsapi.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FacultyStatusUpdateRequest {

  @NotNull(message = "Trạng thái hoạt động không được để trống")
  private Boolean isActive;

  @Size(max = 500, message = "Lý do tối đa 500 ký tự")
  private String reason;
}
