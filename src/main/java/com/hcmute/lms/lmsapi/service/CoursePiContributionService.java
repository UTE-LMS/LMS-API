package com.hcmute.lms.lmsapi.service;

import com.hcmute.lms.lmsapi.dto.CoursePiContributionRequest;
import com.hcmute.lms.lmsapi.dto.CoursePiContributionResponse;

import java.util.List;

public interface CoursePiContributionService {

    List<CoursePiContributionResponse> getContributions(String programVersionId);

    CoursePiContributionResponse createContribution(String programVersionId, CoursePiContributionRequest request);

    void deleteContribution(String programVersionId, String id);
}
