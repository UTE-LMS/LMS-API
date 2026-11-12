package com.hcmute.lms.lmsapi.repository;

import com.hcmute.lms.lmsapi.document.Score;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ScoreRepository extends MongoRepository<Score, String> {

  List<Score> findBySectionIdAndAssessmentId(String sectionId, String assessmentId);

  boolean existsByAssessmentId(String assessmentId);

  long countByImportBatchId(String importBatchId);

  void deleteByImportBatchId(String importBatchId);
}
