package com.hcmute.lms.lmsapi.mapper.impl;

import com.hcmute.lms.lmsapi.document.Assessment;
import com.hcmute.lms.lmsapi.document.AssessmentCloWeight;
import com.hcmute.lms.lmsapi.document.AssessmentItem;
import com.hcmute.lms.lmsapi.document.CoverageSummary;
import com.hcmute.lms.lmsapi.document.OutcomeMapping;
import com.hcmute.lms.lmsapi.dto.AssessmentCloWeightDto;
import com.hcmute.lms.lmsapi.dto.AssessmentItemDto;
import com.hcmute.lms.lmsapi.dto.AssessmentResponse;
import com.hcmute.lms.lmsapi.dto.CoverageResponse;
import com.hcmute.lms.lmsapi.dto.OutcomeMappingDto;
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
              .bloomLevel(dto.getBloomLevel())
              .outcomeMappings(
                  dto.getOutcomeMappings() == null
                      ? new ArrayList<>()
                      : dto.getOutcomeMappings().stream()
                          .map(
                              m ->
                                  OutcomeMapping.builder()
                                      .cloId(m.getCloId())
                                      .maxScore(m.getMaxScore())
                                      .build())
                          .toList())
              .build());
    }
    return items;
  }

  @Override
  public List<AssessmentItemDto> toCopyDtos(List<AssessmentItem> items) {
    if (items == null) {
      return Collections.emptyList();
    }
    return items.stream().map(item -> toItemDto(item, false)).toList();
  }

  private AssessmentItemDto toItemDto(AssessmentItem item, boolean keepId) {
    return AssessmentItemDto.builder()
        .id(keepId ? item.getId() : null)
        .code(item.getCode())
        .orderIndex(item.getOrderIndex())
        .bloomLevel(item.getBloomLevel())
        .outcomeMappings(
            item.getOutcomeMappings() == null
                ? new ArrayList<>()
                : item.getOutcomeMappings().stream()
                    .map(
                        m ->
                            OutcomeMappingDto.builder()
                                .cloId(m.getCloId())
                                .maxScore(m.getMaxScore())
                                .build())
                    .toList())
        .build();
  }

  @Override
  public List<AssessmentCloWeight> toCloWeights(List<AssessmentCloWeightDto> dtos) {
    if (dtos == null) {
      return new ArrayList<>();
    }
    return dtos.stream()
        .map(d -> AssessmentCloWeight.builder().cloId(d.getCloId()).weight(d.getWeight()).build())
        .toList();
  }

  @Override
  public List<AssessmentCloWeightDto> toCloWeightDtos(List<AssessmentCloWeight> weights) {
    if (weights == null) {
      return new ArrayList<>();
    }
    return weights.stream()
        .map(
            w -> AssessmentCloWeightDto.builder().cloId(w.getCloId()).weight(w.getWeight()).build())
        .toList();
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
        .status(a.getStatus())
        .locked(a.isLocked())
        .cloWeights(toCloWeightDtos(a.getCloWeights()))
        .coverageSummary(toCoverageResponse(a.getCoverageSummary(), null))
        .items(
            a.getItems() == null
                ? Collections.emptyList()
                : a.getItems().stream().map(item -> toItemDto(item, true)).toList())
        .revision(a.getRevision())
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
            s.getByClo() == null
                ? Collections.emptyList()
                : s.getByClo().stream()
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
            s.getByBloom() == null
                ? Collections.emptyList()
                : s.getByBloom().stream()
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
