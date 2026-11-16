package com.hcmute.lms.lmsapi.service;

import com.hcmute.lms.lmsapi.dto.AssessmentScoreTemplate;
import com.hcmute.lms.lmsapi.dto.ProgramCsvImportResponse;
import org.springframework.web.multipart.MultipartFile;

public interface ProgramImportService {

  AssessmentScoreTemplate generateTemplateCsv();

  default ProgramCsvImportResponse importProgramCsv(
      String programId, String version, MultipartFile file, String actorId) {
    return importProgramCsv(programId, version, null, null, null, file, actorId);
  }

  ProgramCsvImportResponse importProgramCsv(
      String programId,
      String version,
      Integer totalCredits,
      Double durationYears,
      String applicableCohort,
      MultipartFile file,
      String actorId);

  ProgramCsvImportResponse confirmImport(String programVersionId, String actorId);
}
