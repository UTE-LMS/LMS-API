package com.hcmute.lms.lmsapi.repository;

import com.hcmute.lms.lmsapi.document.ImportBatch;
import com.hcmute.lms.lmsapi.enums.ImportBatchStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ImportBatchRepository extends MongoRepository<ImportBatch, String> {

  List<ImportBatch> findBySectionIdAndAssessmentIdOrderByCreatedAtDesc(
      String sectionId, String assessmentId);

  boolean existsBySectionIdAndAssessmentIdAndFileChecksumAndStatus(
      String sectionId, String assessmentId, String fileChecksum, ImportBatchStatus status);

  /** Lô gần nhất của bài có lưu ánh xạ cột, để dùng lại cho lô sau (US-21/AC-2). */
  Optional<ImportBatch>
      findFirstBySectionIdAndAssessmentIdAndColumnMappingNotNullOrderByCreatedAtDesc(
          String sectionId, String assessmentId);
}
