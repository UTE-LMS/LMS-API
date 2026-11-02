package com.hcmute.lms.lmsapi.service;

import com.hcmute.lms.lmsapi.dto.PiResponse;
import com.hcmute.lms.lmsapi.dto.PloResponse;
import com.hcmute.lms.lmsapi.dto.ProgramVersionResponse;
import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import com.hcmute.lms.lmsapi.dto.VerificationRequest;

public interface ProgramVerificationService {

    ProgramVersionResponse verifyProgramVersion(String id, VerificationRequest request, UserPrincipal principal);

    PloResponse verifyPlo(String programVersionId, String ploId, VerificationRequest request, UserPrincipal principal);

    PiResponse verifyPi(String programVersionId, String piId, VerificationRequest request, UserPrincipal principal);
}
