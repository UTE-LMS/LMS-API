package com.hcmute.lms.lmsapi.repository;

import com.hcmute.lms.lmsapi.document.ProgramVersion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProgramVersionRepository extends MongoRepository<ProgramVersion, String> {

    Page<ProgramVersion> findByProgramId(String programId, Pageable pageable);

    boolean existsByProgramIdAndVersion(String programId, String version);

    boolean existsByProgramIdAndVersionAndIdNot(String programId, String version, String id);
}
