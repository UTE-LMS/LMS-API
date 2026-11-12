package com.hcmute.lms.lmsapi.repository;

import com.hcmute.lms.lmsapi.document.Assessment;
import com.hcmute.lms.lmsapi.enums.AssessmentScope;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AssessmentRepository extends MongoRepository<Assessment, String> {

  List<Assessment> findBySyllabusIdAndScopeOrderByCodeAsc(String syllabusId, AssessmentScope scope);

  List<Assessment> findBySectionIdOrderByCodeAsc(String sectionId);

  List<Assessment> findBySectionId(String sectionId);

  boolean existsBySyllabusIdAndScopeAndCode(String syllabusId, AssessmentScope scope, String code);

  boolean existsBySyllabusIdAndSectionIdAndCode(String syllabusId, String sectionId, String code);
}
