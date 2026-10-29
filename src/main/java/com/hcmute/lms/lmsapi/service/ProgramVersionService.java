package com.hcmute.lms.lmsapi.service;

import com.hcmute.lms.lmsapi.dto.PageResponse;
import com.hcmute.lms.lmsapi.dto.ProgramVersionRequest;
import com.hcmute.lms.lmsapi.dto.ProgramVersionResponse;
import com.hcmute.lms.lmsapi.dto.ProgramVersionUpdateRequest;

public interface ProgramVersionService {

    PageResponse<ProgramVersionResponse> getProgramVersions(int page, int size, String programId);

    ProgramVersionResponse getProgramVersionById(String id);

    ProgramVersionResponse createProgramVersion(ProgramVersionRequest request);

    ProgramVersionResponse updateProgramVersion(String id, ProgramVersionUpdateRequest request);
}
