package com.hcmute.lms.lmsapi.dto;

import com.hcmute.lms.lmsapi.enums.ApprovalAction;
import com.hcmute.lms.lmsapi.enums.AssessmentPhase;
import com.hcmute.lms.lmsapi.enums.AssessmentScope;
import com.hcmute.lms.lmsapi.enums.AssessmentStatus;
import com.hcmute.lms.lmsapi.enums.AssessmentType;
import com.hcmute.lms.lmsapi.enums.DataGranularity;
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
  private Double totalPoints;
  private Double weightInCourse;
  private DataGranularity dataGranularity;

  /** false khi chỉ có điểm tổng ({@code TOTAL_ONLY}): bài này không đo được CLO/PI. */
  private boolean measurableByOutcome;

  private AssessmentStatus status;

  /** true khi cấu trúc items[] bị khóa (đã duyệt trở đi). */
  private boolean locked;

  private Approval approval;
  private CoverageResponse coverageSummary;
  private List<AssessmentItemDto> items;
  private Long revision;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Approval {
    private String submittedBy;
    private Instant submittedAt;
    private String reviewedBy;
    private Instant reviewedAt;
    private String reviewNote;
    private List<HistoryEntry> history;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class HistoryEntry {
    private ApprovalAction action;
    private String actorId;
    private Instant at;
    private String note;
  }
}
