package com.hcmute.lms.lmsapi.mapper.impl;

import com.hcmute.lms.lmsapi.document.Pi;
import com.hcmute.lms.lmsapi.dto.PiResponse;
import com.hcmute.lms.lmsapi.mapper.PiMapper;
import com.hcmute.lms.lmsapi.mapper.VerificationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PiMapperImpl implements PiMapper {

    private final VerificationMapper verificationMapper;

    @Override
    public PiResponse toResponse(Pi pi) {
        if (pi == null) {
            return null;
        }
        return PiResponse.builder()
                .id(pi.getId())
                .programVersionId(pi.getProgramVersionId())
                .ploId(pi.getPloId())
                .code(pi.getCode())
                .displayOrder(pi.getDisplayOrder())
                .nameOriginalTerm(pi.getNameOriginalTerm())
                .contentOriginal(pi.getContentOriginal())
                .contentShort(pi.getContentShort())
                .capabilityLevelRaw(pi.getCapabilityLevelRaw())
                .targetPassRate(pi.getTargetPassRate())
                .verification(verificationMapper.toResponse(pi.getVerification()))
                .reviewNote(pi.getReviewNote())
                .build();
    }

    @Override
    public List<PiResponse> toResponseList(List<Pi> pis) {
        if (pis == null) {
            return new ArrayList<>();
        }
        return pis.stream().map(this::toResponse).toList();
    }
}
