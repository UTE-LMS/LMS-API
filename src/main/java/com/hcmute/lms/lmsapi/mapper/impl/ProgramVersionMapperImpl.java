package com.hcmute.lms.lmsapi.mapper.impl;

import com.hcmute.lms.lmsapi.document.Program;
import com.hcmute.lms.lmsapi.document.ProgramVersion;
import com.hcmute.lms.lmsapi.dto.ProgramVersionResponse;
import com.hcmute.lms.lmsapi.mapper.ProgramVersionMapper;
import com.hcmute.lms.lmsapi.mapper.VerificationMapper;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProgramVersionMapperImpl implements ProgramVersionMapper {

  private final VerificationMapper verificationMapper;

  @Override
  public ProgramVersionResponse toResponse(ProgramVersion programVersion) {
    return toResponse(programVersion, null, 0L, 0L);
  }

  @Override
  public ProgramVersionResponse toResponse(
      ProgramVersion programVersion, Program program, long ploCount, long piCount) {
    if (programVersion == null) {
      return null;
    }
    return ProgramVersionResponse.builder()
        .id(programVersion.getId())
        .programId(programVersion.getProgramId())
        .programCode(program != null ? program.getCode() : null)
        .programName(program != null ? program.getName() : null)
        .version(programVersion.getVersion())
        .totalCredits(programVersion.getTotalCredits())
        .durationYears(programVersion.getDurationYears())
        .applicableCohort(programVersion.getApplicableCohort())
        .status(programVersion.getStatus())
        .verification(verificationMapper.toResponse(programVersion.getVerification()))
        .ploCount(ploCount)
        .piCount(piCount)
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
