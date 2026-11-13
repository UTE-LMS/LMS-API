package com.hcmute.lms.lmsapi.repository;

import com.hcmute.lms.lmsapi.document.ProgramVersion;
import com.hcmute.lms.lmsapi.enums.ProgramVersionStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProgramVersionRepository extends MongoRepository<ProgramVersion, String> {

  Page<ProgramVersion> findByProgramId(String programId, Pageable pageable);

  Optional<ProgramVersion> findByProgramIdAndVersion(String programId, String version);

  List<ProgramVersion> findByProgramIdAndStatus(String programId, ProgramVersionStatus status);

  boolean existsByProgramIdAndVersion(String programId, String version);

  boolean existsByProgramIdAndVersionAndIdNot(String programId, String version, String id);
}
