package com.hcmute.lms.lmsapi.mapper;

import com.hcmute.lms.lmsapi.document.Verification;
import com.hcmute.lms.lmsapi.dto.VerificationResponse;

public interface VerificationMapper {

    VerificationResponse toResponse(Verification verification);
}
