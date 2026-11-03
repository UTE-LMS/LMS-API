package com.hcmute.lms.lmsapi.repository;

import com.hcmute.lms.lmsapi.document.ProgramCourse;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProgramCourseRepository extends MongoRepository<ProgramCourse, String> {

  boolean existsByProgramVersionIdAndCourseId(String programVersionId, String courseId);

  Optional<ProgramCourse> findByProgramVersionIdAndCourseId(
      String programVersionId, String courseId);

  List<ProgramCourse> findByProgramVersionIdOrderBySemesterAscCourseIdAsc(String programVersionId);

  List<ProgramCourse> findByCourseId(String courseId);

  boolean existsByCourseId(String courseId);

  void deleteByProgramVersionIdAndCourseId(String programVersionId, String courseId);
}
