package com.hcmute.lms.lmsapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FacultyTransferPmRequest {

  @NotBlank(message = "ID người dùng không được để trống")
  @Pattern(regexp = "^[0-9a-fA-F]{24}$", message = "ID người dùng phải là ObjectId 24 ký tự hex")
  private String userId;

  @NotBlank(message = "ID Khoa đích không được để trống")
  @Pattern(regexp = "^[0-9a-fA-F]{24}$", message = "ID Khoa đích phải là ObjectId 24 ký tự hex")
  private String targetFacultyId;

  @Size(max = 500, message = "Lý do chuyển công tác tối đa 500 ký tự")
  private String transferReason;
}
