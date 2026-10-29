package com.hcmute.lms.lmsapi.mapper;

import com.hcmute.lms.lmsapi.document.CoursePiContribution;
import com.hcmute.lms.lmsapi.dto.CoursePiContributionResponse;

import java.util.List;

public interface CoursePiContributionMapper {

    CoursePiContributionResponse toResponse(CoursePiContribution contribution);

    List<CoursePiContributionResponse> toResponseList(List<CoursePiContribution> contributions);
}
