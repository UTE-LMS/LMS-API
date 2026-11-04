package com.hcmute.lms.lmsapi.repository;

import com.hcmute.lms.lmsapi.document.Clo;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CloRepository extends MongoRepository<Clo, String> {

  boolean existsBySyllabusIdAndCode(String syllabusId, String code);

  List<Clo> findBySyllabusIdOrderByCodeAsc(String syllabusId);

  boolean existsBySyllabusId(String syllabusId);

  long countBySyllabusId(String syllabusId);
}
