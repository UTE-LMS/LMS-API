package com.hcmute.lms.lmsapi.service;

import com.hcmute.lms.lmsapi.dto.PageResponse;
import com.hcmute.lms.lmsapi.dto.ProgramRequest;
import com.hcmute.lms.lmsapi.dto.ProgramResponse;

public interface ProgramService {

  PageResponse<ProgramResponse> getPrograms(int page, int size);

  default PageResponse<ProgramResponse> getPrograms(
      int page, int size, String facultyId, String level, String degree, String search) {
    return getPrograms(page, size);
  }

  ProgramResponse getProgramById(String id);

  ProgramResponse createProgram(ProgramRequest request);

  ProgramResponse updateProgram(String id, ProgramRequest request);
}
