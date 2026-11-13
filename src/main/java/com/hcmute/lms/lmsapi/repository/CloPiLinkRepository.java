package com.hcmute.lms.lmsapi.repository;

import com.hcmute.lms.lmsapi.document.CloPiLink;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CloPiLinkRepository extends MongoRepository<CloPiLink, String> {

  boolean existsByCloIdAndPiId(String cloId, String piId);

  List<CloPiLink> findBySyllabusId(String syllabusId);

  List<CloPiLink> findBySyllabusIdAndProgramVersionId(String syllabusId, String programVersionId);

  List<CloPiLink> findByProgramVersionId(String programVersionId);

  boolean existsByCloId(String cloId);

  boolean existsByPiId(String piId);

  void deleteBySyllabusId(String syllabusId);
}
