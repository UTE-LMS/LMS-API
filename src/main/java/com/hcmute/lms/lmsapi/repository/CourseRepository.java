package com.hcmute.lms.lmsapi.repository;

import com.hcmute.lms.lmsapi.document.Course;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CourseRepository extends MongoRepository<Course, String> {

  boolean existsByCode(String code);

  Optional<Course> findByCode(String code);

  Page<Course> findByNameContainingIgnoreCaseOrCodeContainingIgnoreCase(
      String name, String code, Pageable pageable);

  Page<Course> findByFacultyId(String facultyId, Pageable pageable);

  Page<Course> findByFacultyIdAndNameContainingIgnoreCaseOrFacultyIdAndCodeContainingIgnoreCase(
      String facultyId1, String name, String facultyId2, String code, Pageable pageable);
}
