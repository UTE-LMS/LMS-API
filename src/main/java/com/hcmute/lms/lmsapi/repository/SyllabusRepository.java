package com.hcmute.lms.lmsapi.repository;

import com.hcmute.lms.lmsapi.document.Syllabus;
import com.hcmute.lms.lmsapi.enums.SyllabusStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SyllabusRepository extends MongoRepository<Syllabus, String> {

  boolean existsByCourseIdAndVersion(String courseId, String version);

  Optional<Syllabus> findByCourseIdAndVersion(String courseId, String version);

  List<Syllabus> findByCourseId(String courseId);

  List<Syllabus> findByCourseIdAndStatus(String courseId, SyllabusStatus status);

  List<Syllabus> findByCourseIdOrderByVersionAsc(String courseId);

  boolean existsByCourseId(String courseId);

  boolean existsByEffectiveFromTermIdOrEffectiveToTermId(String fromTermId, String toTermId);
}
