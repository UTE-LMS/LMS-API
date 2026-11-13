package com.hcmute.lms.lmsapi.repository;

import com.hcmute.lms.lmsapi.document.StudentResult;
import com.hcmute.lms.lmsapi.enums.ResultLevel;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface StudentResultRepository extends MongoRepository<StudentResult, String> {

  List<StudentResult> findByRunId(String runId);

  List<StudentResult> findByRunIdAndLevel(String runId, ResultLevel level);

  List<StudentResult> findByRunIdAndLevelAndSectionId(
      String runId, ResultLevel level, String sectionId);

  List<StudentResult> findByRunIdAndLevelAndSectionIdAndAssessmentId(
      String runId, ResultLevel level, String sectionId, String assessmentId);

  List<StudentResult> findByRunIdAndStudentId(String runId, String studentId);
}
