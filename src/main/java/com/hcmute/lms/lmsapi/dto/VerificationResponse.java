package com.hcmute.lms.lmsapi.dto;

import com.hcmute.lms.lmsapi.enums.VerificationStatus;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationResponse {

  private VerificationStatus status;
  private String verifiedBy;
  private Instant verifiedAt;
  private String note;
}
