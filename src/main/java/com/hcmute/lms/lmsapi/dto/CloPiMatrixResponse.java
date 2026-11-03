package com.hcmute.lms.lmsapi.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CloPiMatrixResponse {

  private String syllabusId;
  private String programVersionId;
  private List<CloResponse> clos;
  private List<PiResponse> pis;
  private List<CloPiLinkCellDto> links;
}
