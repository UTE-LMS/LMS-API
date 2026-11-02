package com.hcmute.lms.lmsapi.mapper.impl;

import com.hcmute.lms.lmsapi.document.Plo;
import com.hcmute.lms.lmsapi.dto.PloResponse;
import com.hcmute.lms.lmsapi.mapper.PloMapper;
import com.hcmute.lms.lmsapi.mapper.VerificationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PloMapperImpl implements PloMapper {

    private final VerificationMapper verificationMapper;

    @Override
    public PloResponse toResponse(Plo plo) {
        if (plo == null) {
            return null;
        }
        return PloResponse.builder()
                .id(plo.getId())
                .programVersionId(plo.getProgramVersionId())
                .code(plo.getCode())
                .displayOrder(plo.getDisplayOrder())
                .category(plo.getCategory())
                .contentOriginal(plo.getContentOriginal())
                .contentSummary(plo.getContentSummary())
                .capabilityLevelRaw(plo.getCapabilityLevelRaw())
                .verification(verificationMapper.toResponse(plo.getVerification()))
                .reviewNote(plo.getReviewNote())
                .build();
    }

    @Override
    public List<PloResponse> toResponseList(List<Plo> plos) {
        if (plos == null) {
            return new ArrayList<>();
        }
        return plos.stream().map(this::toResponse).toList();
    }
}
