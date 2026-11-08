package com.hcmute.lms.lmsapi.mapper.impl;

import com.hcmute.lms.lmsapi.document.Assessment;
import com.hcmute.lms.lmsapi.document.AssessmentApproval;
import com.hcmute.lms.lmsapi.document.AssessmentItem;
import com.hcmute.lms.lmsapi.document.AssessmentRubric;
import com.hcmute.lms.lmsapi.document.CoverageSummary;
import com.hcmute.lms.lmsapi.document.OutcomeMapping;
import com.hcmute.lms.lmsapi.document.RubricLevel;
import com.hcmute.lms.lmsapi.dto.AssessmentItemDto;
import com.hcmute.lms.lmsapi.dto.AssessmentResponse;
import com.hcmute.lms.lmsapi.dto.CoverageResponse;
import com.hcmute.lms.lmsapi.dto.OutcomeMappingDto;
import com.hcmute.lms.lmsapi.dto.RubricDto;
import com.hcmute.lms.lmsapi.dto.RubricLevelDto;
import com.hcmute.lms.lmsapi.enums.DataGranularity;
import com.hcmute.lms.lmsapi.mapper.AssessmentMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Component;

@Component
public class AssessmentMapperImpl implements AssessmentMapper {

  @Override
  public List<AssessmentItem> toItems(List<AssessmentItemDto> dtos) {
    List<AssessmentItem> items = new ArrayList<>();
    if (dtos == null) {
      return items;
    }
    for (int i = 0; i < dtos.size(); i++) {
      AssessmentItemDto dto = dtos.get(i);
      items.add(
          AssessmentItem.builder()
              .id(dto.getId() != null ? dto.getId() : new ObjectId().toHexString())
              .code(dto.getCode())
              .orderIndex(dto.getOrderIndex() != null ? dto.getOrderIndex() : i + 1)
              .title(dto.getTitle())
              .kind(dto.getKind())
              .maxScore(dto.getMaxScore())
              .bloomLevel(dto.getBloomLevel())
              .topic(dto.getTopic())
              .externalRef(dto.getExternalRef())
              .outcomeMappings(
                  dto.getOutcomeMappings().stream()
                      .map(
                          m ->
                              OutcomeMapping.builder()
                                  .cloId(m.getCloId())
                                  .weight(m.getWeight())
                                  .build())
                      .toList())
              .rubric(toRubric(dto.getRubric()))
              .build());
    }
    return items;
  }

  private AssessmentRubric toRubric(RubricDto dto) {
    if (dto == null) {
      return null;
    }
    return AssessmentRubric.builder()
        .levels(
            dto.getLevels().stream()
                .map(
                    l ->
                        RubricLevel.builder()
                            .level(l.getLevel())
                            .description(l.getDescription())
                            .score(l.getScore())
                            .build())
                .toList())
        .build();
  }

  @Override
  public List<AssessmentItemDto> toCopyDtos(List<AssessmentItem> items) {
    return items.stream().map(item -> toItemDto(item, false)).toList();
  }

  private AssessmentItemDto toItemDto(AssessmentItem item, boolean keepId) {
    return AssessmentItemDto.builder()
        .id(keepId ? item.getId() : null)
        .code(item.getCode())
        .orderIndex(item.getOrderIndex())
        .title(item.getTitle())
        .kind(item.getKind())
        .maxScore(item.getMaxScore())
        .bloomLevel(item.getBloomLevel())
        .topic(item.getTopic())
        .externalRef(item.getExternalRef())
        .outcomeMappings(
            item.getOutcomeMappings().stream()
                .map(
                    m ->
                        OutcomeMappingDto.builder()
                            .cloId(m.getCloId())
                            .weight(m.getWeight())
                            .build())
                .toList())
        .rubric(
            item.getRubric() == null
                ? null
                : RubricDto.builder()
                    .levels(
                        item.getRubric().getLevels().stream()
                            .map(
                                l ->
                                    RubricLevelDto.builder()
                                        .level(l.getLevel())
                                        .description(l.getDescription())
                                        .score(l.getScore())
                                        .build())
                            .toList())
                    .build())
        .build();
  }

  @Override
  public AssessmentResponse toResponse(Assessment a) {
    if (a == null) {
      return null;
    }
    return AssessmentResponse.builder()
        .id(a.getId())
        .syllabusId(a.getSyllabusId())
        .sectionId(a.getSectionId())
        .templateId(a.getTemplateId())
        .code(a.getCode())
        .name(a.getName())
        .scope(a.getScope())
        .type(a.getType())
        .phase(a.getPhase())
        .scheduledAt(a.getScheduledAt())
        .totalPoints(a.getTotalPoints())
        .weightInCourse(a.getWeightInCourse())
        .dataGranularity(a.getDataGranularity())
        .measurableByOutcome(a.getDataGranularity() == DataGranularity.ITEM)
        .status(a.getStatus())
        .locked(a.isLocked())
        .approval(toApproval(a.getApproval()))
        .coverageSummary(toCoverageResponse(a.getCoverageSummary(), null))
        .items(a.getItems().stream().map(item -> toItemDto(item, true)).toList())
        .revision(a.getRevision())
        .build();
  }

  private AssessmentResponse.Approval toApproval(AssessmentApproval approval) {
    if (approval == null) {
      return null;
    }
    return AssessmentResponse.Approval.builder()
        .submittedBy(approval.getSubmittedBy())
        .submittedAt(approval.getSubmittedAt())
        .reviewedBy(approval.getReviewedBy())
        .reviewedAt(approval.getReviewedAt())
        .reviewNote(approval.getReviewNote())
        .history(
            approval.getHistory().stream()
                .map(
                    h ->
                        AssessmentResponse.HistoryEntry.builder()
                            .action(h.getAction())
                            .actorId(h.getActorId())
                            .at(h.getAt())
                            .note(h.getNote())
                            .build())
                .toList())
        .build();
  }

  @Override
  public List<AssessmentResponse> toResponseList(List<Assessment> assessments) {
    if (assessments == null || assessments.isEmpty()) {
      return Collections.emptyList();
    }
    return assessments.stream().map(this::toResponse).toList();
  }

  @Override
  public CoverageResponse toCoverageResponse(CoverageSummary s, Map<String, String> cloCodes) {
    if (s == null) {
      return null;
    }
    return CoverageResponse.builder()
        .totalItemsSum(s.getTotalItemsSum())
        .byClo(
            s.getByClo().stream()
                .map(
                    c ->
                        CoverageResponse.ByClo.builder()
                            .cloId(c.getCloId())
                            .cloCode(cloCodes == null ? null : cloCodes.get(c.getCloId()))
                            .points(c.getPoints())
                            .percentage(c.getPercentage())
                            .build())
                .toList())
        .byBloom(
            s.getByBloom().stream()
                .map(
                    b ->
                        CoverageResponse.ByBloom.builder()
                            .bloomLevel(b.getBloomLevel())
                            .questionCount(b.getQuestionCount())
                            .points(b.getPoints())
                            .percentage(b.getPercentage())
                            .build())
                .toList())
        .build();
  }
}
