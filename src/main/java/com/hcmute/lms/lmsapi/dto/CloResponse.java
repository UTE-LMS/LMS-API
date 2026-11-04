package com.hcmute.lms.lmsapi.dto;

import com.hcmute.lms.lmsapi.document.Verification;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CloResponse {

  private String id;
  private String syllabusId;
  private String courseId;
  private String code;
  private String contentOriginal;
  private Integer bloomLevel;
  private String bloomNote;
  private CloMeasurementDto measurement;
  private Verification verification;
  private Map<String, Object> extra;
}
