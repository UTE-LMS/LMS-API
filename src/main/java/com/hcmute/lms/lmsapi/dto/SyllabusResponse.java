package com.hcmute.lms.lmsapi.dto;

import com.hcmute.lms.lmsapi.document.Verification;
import com.hcmute.lms.lmsapi.enums.SyllabusStatus;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyllabusResponse {

  private String id;
  private String courseId;
  private String version;
  private String effectiveFromTermId;
  private String effectiveToTermId;
  private SyllabusStatus status;
  private Verification verification;
  private Map<String, Object> extra;
}
