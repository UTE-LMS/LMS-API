package com.hcmute.lms.lmsapi.mapper;

import com.hcmute.lms.lmsapi.document.Program;
import com.hcmute.lms.lmsapi.dto.ProgramResponse;

import java.util.List;

public interface ProgramMapper {

    ProgramResponse toResponse(Program program);

    List<ProgramResponse> toResponseList(List<Program> programs);
}
