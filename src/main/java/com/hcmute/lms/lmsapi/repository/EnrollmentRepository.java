package com.hcmute.lms.lmsapi.repository;

import com.hcmute.lms.lmsapi.document.Enrollment;
import com.hcmute.lms.lmsapi.enums.EnrollmentStatus;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EnrollmentRepository extends MongoRepository<Enrollment, String> {

  List<Enrollment> findBySectionId(String sectionId);

  List<Enrollment> findBySectionIdAndStatus(String sectionId, EnrollmentStatus status);

  long countBySectionIdAndStatus(String sectionId, EnrollmentStatus status);

  boolean existsBySectionId(String sectionId);
}
