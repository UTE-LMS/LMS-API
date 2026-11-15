package com.hcmute.lms.lmsapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FacultyAssignPmRequest {

  @NotBlank(message = "ID người dùng không được để trống")
  @Pattern(regexp = "^[0-9a-fA-F]{24}$", message = "ID người dùng phải là ObjectId 24 ký tự hex")
  private String userId;
}
