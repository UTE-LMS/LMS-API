package com.hcmute.lms.lmsapi.repository;

import com.hcmute.lms.lmsapi.document.Pi;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PiRepository extends MongoRepository<Pi, String> {

  List<Pi> findByProgramVersionIdOrderByDisplayOrderAsc(String programVersionId);

  Optional<Pi> findByIdAndProgramVersionId(String id, String programVersionId);

  boolean existsByProgramVersionIdAndCode(String programVersionId, String code);

  boolean existsByProgramVersionIdAndCodeAndIdNot(String programVersionId, String code, String id);

  long countByPloId(String ploId);

  boolean existsByPloIdAndProgramVersionId(String ploId, String programVersionId);

  void deleteByProgramVersionId(String programVersionId);
}
