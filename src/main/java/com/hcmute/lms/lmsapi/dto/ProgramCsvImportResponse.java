package com.hcmute.lms.lmsapi.dto;

import com.hcmute.lms.lmsapi.enums.ProgramVersionStatus;
import com.hcmute.lms.lmsapi.enums.VerificationStatus;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProgramCsvImportResponse {

  private String programVersionId;
  private String programId;
  private String version;
  private ProgramVersionStatus status;
  private VerificationStatus verificationStatus;
  private int ploCount;
  private int piCount;
  private int courseCount;
  private List<String> warnings;
  private List<PloResponse> plos;
  private List<PiResponse> pis;
}
