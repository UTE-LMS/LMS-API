package com.hcmute.lms.lmsapi.repository;

import com.hcmute.lms.lmsapi.document.Faculty;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface FacultyRepository extends MongoRepository<Faculty, String> {

  Optional<Faculty> findByCode(String code);

  boolean existsByCode(String code);

  boolean existsByName(String name);

  boolean existsByNameAndIdNot(String name, String id);

  Page<Faculty> findByIsActive(boolean isActive, Pageable pageable);

  Page<Faculty> findByCodeContainingIgnoreCaseOrNameContainingIgnoreCase(
      String code, String name, Pageable pageable);

  Page<Faculty> findByIsActiveAndCodeContainingIgnoreCaseOrIsActiveAndNameContainingIgnoreCase(
      boolean isActive1, String code, boolean isActive2, String name, Pageable pageable);
}
