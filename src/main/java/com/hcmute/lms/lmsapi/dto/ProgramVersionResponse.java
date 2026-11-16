package com.hcmute.lms.lmsapi.dto;

import com.hcmute.lms.lmsapi.enums.ProgramVersionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProgramVersionResponse {

  private String id;
  private String programId;
  private String version;
  private Integer totalCredits;
  private Double durationYears;
  private String applicableCohort;
  private ProgramVersionStatus status;
  private VerificationResponse verification;
}
