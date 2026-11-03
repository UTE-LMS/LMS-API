package com.hcmute.lms.lmsapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyllabusRequest {

  @NotBlank(message = "Phiên bản đề cương không được để trống")
  private String version;

  @Pattern(
      regexp = "^[0-9a-fA-F]{24}$",
      message = "effectiveFromTermId phải là ObjectId 24 ký tự hex")
  private String effectiveFromTermId;

  @Pattern(
      regexp = "^[0-9a-fA-F]{24}$",
      message = "effectiveToTermId phải là ObjectId 24 ký tự hex")
  private String effectiveToTermId;

  private Map<String, Object> extra;
}
