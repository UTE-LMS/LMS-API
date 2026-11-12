package com.hcmute.lms.lmsapi.mapper;

import com.hcmute.lms.lmsapi.document.Assessment;
import com.hcmute.lms.lmsapi.document.AssessmentCloWeight;
import com.hcmute.lms.lmsapi.document.AssessmentItem;
import com.hcmute.lms.lmsapi.document.CoverageSummary;
import com.hcmute.lms.lmsapi.dto.AssessmentCloWeightDto;
import com.hcmute.lms.lmsapi.dto.AssessmentItemDto;
import com.hcmute.lms.lmsapi.dto.AssessmentResponse;
import com.hcmute.lms.lmsapi.dto.CoverageResponse;
import java.util.List;
import java.util.Map;

public interface AssessmentMapper {

  List<AssessmentItem> toItems(List<AssessmentItemDto> dtos);

  List<AssessmentItemDto> toCopyDtos(List<AssessmentItem> items);

  List<AssessmentCloWeight> toCloWeights(List<AssessmentCloWeightDto> dtos);

  List<AssessmentCloWeightDto> toCloWeightDtos(List<AssessmentCloWeight> weights);

  AssessmentResponse toResponse(Assessment assessment);

  List<AssessmentResponse> toResponseList(List<Assessment> assessments);

  CoverageResponse toCoverageResponse(CoverageSummary summary, Map<String, String> cloCodes);
}
