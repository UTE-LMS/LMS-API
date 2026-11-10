package com.hcmute.lms.lmsapi.repository;

import com.hcmute.lms.lmsapi.document.ImportRowError;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ImportRowErrorRepository extends MongoRepository<ImportRowError, String> {

  Page<ImportRowError> findByBatchIdOrderByRowNumberAscIdAsc(String batchId, Pageable pageable);

  void deleteByBatchId(String batchId);
}
