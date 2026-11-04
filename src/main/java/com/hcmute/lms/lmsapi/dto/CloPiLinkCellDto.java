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
public class CloPiLinkCellDto {

  private String cloId;
  private String piId;
  private String linkId;
  private Double weight;
  private Verification verification;
}
