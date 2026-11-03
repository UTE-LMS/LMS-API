package com.hcmute.lms.lmsapi.repository;

import com.hcmute.lms.lmsapi.document.CoursePiContribution;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CoursePiContributionRepository
    extends MongoRepository<CoursePiContribution, String> {

  List<CoursePiContribution> findByProgramVersionIdOrderByCourseIdAscPiIdAsc(
      String programVersionId);

  Optional<CoursePiContribution> findByIdAndProgramVersionId(String id, String programVersionId);

  boolean existsByProgramVersionIdAndCourseIdAndPiId(
      String programVersionId, String courseId, String piId);

  boolean existsByCourseId(String courseId);

  long countByPiId(String piId);
}
