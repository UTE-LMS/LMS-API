package com.hcmute.lms.lmsapi.dto;

import com.hcmute.lms.lmsapi.document.Verification;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CloPiLinkResponse {

  private String id;
  private String programVersionId;
  private String syllabusId;
  private String cloId;
  private String piId;
  private Double weight;
  private Verification verification;
}
