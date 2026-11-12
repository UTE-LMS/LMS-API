package com.hcmute.lms.lmsapi.dto;

import com.hcmute.lms.lmsapi.enums.AssessmentPhase;
import com.hcmute.lms.lmsapi.enums.AssessmentScope;
import com.hcmute.lms.lmsapi.enums.AssessmentStatus;
import com.hcmute.lms.lmsapi.enums.AssessmentType;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentResponse {

  private String id;
  private String syllabusId;
  private String sectionId;
  private String templateId;
  private String code;
  private String name;
  private AssessmentScope scope;
  private AssessmentType type;
  private AssessmentPhase phase;
  private Instant scheduledAt;
  private AssessmentStatus status;

  /** true khi cấu trúc items[] bị khóa (LOCKED hoặc SCORE_ENTERED). */
  private boolean locked;

  private List<AssessmentCloWeightDto> cloWeights;
  private CoverageResponse coverageSummary;
  private List<AssessmentItemDto> items;
  private Long revision;
}
