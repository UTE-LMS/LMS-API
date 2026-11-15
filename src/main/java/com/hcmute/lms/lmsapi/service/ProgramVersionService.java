package com.hcmute.lms.lmsapi.service;

import com.hcmute.lms.lmsapi.dto.PageResponse;
import com.hcmute.lms.lmsapi.dto.ProgramVersionRequest;
import com.hcmute.lms.lmsapi.dto.ProgramVersionResponse;
import com.hcmute.lms.lmsapi.dto.ProgramVersionStatusUpdateRequest;
import com.hcmute.lms.lmsapi.dto.ProgramVersionUpdateRequest;
import com.hcmute.lms.lmsapi.enums.ProgramVersionStatus;

public interface ProgramVersionService {

  PageResponse<ProgramVersionResponse> getProgramVersions(int page, int size, String programId);

  default PageResponse<ProgramVersionResponse> getProgramVersions(
      int page,
      int size,
      String programId,
      String facultyId,
      String cohort,
      ProgramVersionStatus status,
      String search) {
    return getProgramVersions(page, size, programId);
  }

  ProgramVersionResponse getProgramVersionById(String id);

  ProgramVersionResponse createProgramVersion(ProgramVersionRequest request);

  ProgramVersionResponse updateProgramVersion(String id, ProgramVersionUpdateRequest request);

  ProgramVersionResponse updateProgramVersionStatus(
      String id, ProgramVersionStatusUpdateRequest request);
}
