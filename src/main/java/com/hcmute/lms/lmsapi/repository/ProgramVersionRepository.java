package com.hcmute.lms.lmsapi.repository;

import com.hcmute.lms.lmsapi.document.ProgramVersion;
import com.hcmute.lms.lmsapi.enums.ProgramVersionStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProgramVersionRepository extends MongoRepository<ProgramVersion, String> {

  Page<ProgramVersion> findByProgramId(String programId, Pageable pageable);

  List<ProgramVersion> findByProgramIdIn(Collection<String> programIds);

  Optional<ProgramVersion> findByProgramIdAndVersion(String programId, String version);

  List<ProgramVersion> findByProgramIdAndStatus(String programId, ProgramVersionStatus status);

  boolean existsByProgramIdAndVersion(String programId, String version);

  boolean existsByProgramIdAndVersionAndIdNot(String programId, String version, String id);

  boolean existsByIdInAndStatus(Collection<String> ids, ProgramVersionStatus status);

  boolean existsByIdInAndStatusIn(
      Collection<String> ids, Collection<ProgramVersionStatus> statuses);

  boolean existsByProgramIdAndStatusIn(String programId, Collection<ProgramVersionStatus> statuses);

  boolean existsByProgramIdInAndStatus(Collection<String> programIds, ProgramVersionStatus status);

  boolean existsByProgramIdInAndStatusIn(
      Collection<String> programIds, Collection<ProgramVersionStatus> statuses);
}
