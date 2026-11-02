package com.hcmute.lms.lmsapi.repository;

import com.hcmute.lms.lmsapi.document.Plo;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PloRepository extends MongoRepository<Plo, String> {

  List<Plo> findByProgramVersionIdOrderByDisplayOrderAsc(String programVersionId);

  Optional<Plo> findByIdAndProgramVersionId(String id, String programVersionId);

  boolean existsByProgramVersionIdAndCode(String programVersionId, String code);

  boolean existsByProgramVersionIdAndCodeAndIdNot(String programVersionId, String code, String id);
}
