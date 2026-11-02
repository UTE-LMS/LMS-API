package com.hcmute.lms.lmsapi.mapper;

import com.hcmute.lms.lmsapi.document.ProgramVersion;
import com.hcmute.lms.lmsapi.dto.ProgramVersionResponse;

import java.util.List;

public interface ProgramVersionMapper {

    ProgramVersionResponse toResponse(ProgramVersion programVersion);

    List<ProgramVersionResponse> toResponseList(List<ProgramVersion> programVersions);
}
