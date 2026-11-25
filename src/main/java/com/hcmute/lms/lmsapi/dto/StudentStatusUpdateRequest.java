package com.hcmute.lms.lmsapi.dto;

import com.hcmute.lms.lmsapi.enums.StudentStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentStatusUpdateRequest {

  @NotNull(message = "Trạng thái sinh viên không được để trống")
  private StudentStatus status;
}
