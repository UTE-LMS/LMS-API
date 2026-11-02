package com.hcmute.lms.lmsapi.mapper.impl;

import com.hcmute.lms.lmsapi.document.CoursePiContribution;
import com.hcmute.lms.lmsapi.dto.CoursePiContributionResponse;
import com.hcmute.lms.lmsapi.mapper.CoursePiContributionMapper;
import com.hcmute.lms.lmsapi.mapper.VerificationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CoursePiContributionMapperImpl implements CoursePiContributionMapper {

    private final VerificationMapper verificationMapper;

    @Override
    public CoursePiContributionResponse toResponse(CoursePiContribution contribution) {
        if (contribution == null) {
            return null;
        }
        return CoursePiContributionResponse.builder()
                .id(contribution.getId())
                .programVersionId(contribution.getProgramVersionId())
                .courseId(contribution.getCourseId())
                .piId(contribution.getPiId())
                .contribution(contribution.getContribution())
                .verification(verificationMapper.toResponse(contribution.getVerification()))
                .build();
    }

    @Override
    public List<CoursePiContributionResponse> toResponseList(List<CoursePiContribution> contributions) {
        if (contributions == null) {
            return new ArrayList<>();
        }
        return contributions.stream().map(this::toResponse).toList();
    }
}
