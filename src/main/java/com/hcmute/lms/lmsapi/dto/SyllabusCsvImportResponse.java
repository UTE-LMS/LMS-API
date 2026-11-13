package com.hcmute.lms.lmsapi.dto;

import com.hcmute.lms.lmsapi.enums.SyllabusStatus;
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
public class SyllabusCsvImportResponse {

  private String syllabusId;
  private String courseId;
  private String version;
  private SyllabusStatus status;
  private VerificationStatus verificationStatus;
  private int cloCount;
  private int linkCount;
  private List<String> warnings;
  private List<CloResponse> clos;
  private List<CloPiLinkResponse> links;
}
