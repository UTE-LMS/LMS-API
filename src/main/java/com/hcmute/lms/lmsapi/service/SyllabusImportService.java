package com.hcmute.lms.lmsapi.service;

import com.hcmute.lms.lmsapi.dto.AssessmentScoreTemplate;
import com.hcmute.lms.lmsapi.dto.SyllabusCsvImportResponse;
import org.springframework.web.multipart.MultipartFile;

public interface SyllabusImportService {

  AssessmentScoreTemplate generateTemplateCsv();

  SyllabusCsvImportResponse importSyllabusCsv(
      String courseId,
      String version,
      String programVersionId,
      String effectiveFromTermId,
      MultipartFile file,
      String actorId);

  SyllabusCsvImportResponse confirmImport(String syllabusId, String actorId);
}
