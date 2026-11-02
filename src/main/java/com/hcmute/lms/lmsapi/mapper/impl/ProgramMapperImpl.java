package com.hcmute.lms.lmsapi.mapper.impl;

import com.hcmute.lms.lmsapi.document.Program;
import com.hcmute.lms.lmsapi.dto.ProgramResponse;
import com.hcmute.lms.lmsapi.mapper.ProgramMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ProgramMapperImpl implements ProgramMapper {

    @Override
    public ProgramResponse toResponse(Program program) {
        if (program == null) {
            return null;
        }
        return ProgramResponse.builder()
                .id(program.getId())
                .code(program.getCode())
                .name(program.getName())
                .level(program.getLevel())
                .degree(program.getDegree())
                .build();
    }

    @Override
    public List<ProgramResponse> toResponseList(List<Program> programs) {
        if (programs == null) {
            return new ArrayList<>();
        }
        return programs.stream().map(this::toResponse).toList();
    }
}
