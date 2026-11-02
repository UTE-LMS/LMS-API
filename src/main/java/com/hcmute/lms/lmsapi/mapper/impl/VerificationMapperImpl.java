package com.hcmute.lms.lmsapi.mapper.impl;

import com.hcmute.lms.lmsapi.document.Verification;
import com.hcmute.lms.lmsapi.dto.VerificationResponse;
import com.hcmute.lms.lmsapi.mapper.VerificationMapper;
import org.springframework.stereotype.Component;

@Component
public class VerificationMapperImpl implements VerificationMapper {

    @Override
    public VerificationResponse toResponse(Verification verification) {
        if (verification == null) {
            return null;
        }
        return VerificationResponse.builder()
                .status(verification.getStatus())
                .verifiedBy(verification.getVerifiedBy())
                .verifiedAt(verification.getVerifiedAt())
                .note(verification.getNote())
                .build();
    }
}
