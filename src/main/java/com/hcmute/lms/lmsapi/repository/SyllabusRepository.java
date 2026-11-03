package com.hcmute.lms.lmsapi.repository;

import com.hcmute.lms.lmsapi.document.Syllabus;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SyllabusRepository extends MongoRepository<Syllabus, String> {

  boolean existsByCourseIdAndVersion(String courseId, String version);

  List<Syllabus> findByCourseId(String courseId);

  List<Syllabus> findByCourseIdOrderByVersionAsc(String courseId);

  boolean existsByCourseId(String courseId);

  boolean existsByEffectiveFromTermIdOrEffectiveToTermId(String fromTermId, String toTermId);
}
