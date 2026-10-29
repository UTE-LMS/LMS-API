package com.hcmute.lms.lmsapi.mapper.impl;

import com.hcmute.lms.lmsapi.document.ProgramVersion;
import com.hcmute.lms.lmsapi.dto.ProgramVersionResponse;
import com.hcmute.lms.lmsapi.mapper.ProgramVersionMapper;
import com.hcmute.lms.lmsapi.mapper.VerificationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ProgramVersionMapperImpl implements ProgramVersionMapper {

    private final VerificationMapper verificationMapper;

    @Override
    public ProgramVersionResponse toResponse(ProgramVersion programVersion) {
        if (programVersion == null) {
            return null;
        }
        return ProgramVersionResponse.builder()
                .id(programVersion.getId())
                .programId(programVersion.getProgramId())
                .version(programVersion.getVersion())
                .totalCredits(programVersion.getTotalCredits())
                .durationYears(programVersion.getDurationYears())
                .applicableCohorts(programVersion.getApplicableCohorts() != null
                        ? new ArrayList<>(programVersion.getApplicableCohorts())
                        : new ArrayList<>())
                .status(programVersion.getStatus())
                .verification(verificationMapper.toResponse(programVersion.getVerification()))
                .build();
    }

    @Override
    public List<ProgramVersionResponse> toResponseList(List<ProgramVersion> programVersions) {
        if (programVersions == null) {
            return new ArrayList<>();
        }
        return programVersions.stream().map(this::toResponse).toList();
    }
}
