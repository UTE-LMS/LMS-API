package com.hcmute.lms.lmsapi.repository;

import com.hcmute.lms.lmsapi.document.CalculationRun;
import com.hcmute.lms.lmsapi.enums.CalculationScope;
import com.hcmute.lms.lmsapi.enums.CalculationStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CalculationRunRepository extends MongoRepository<CalculationRun, String> {

  boolean existsByLockKeyAndStatusIn(String lockKey, Collection<CalculationStatus> statuses);

  List<CalculationRun> findAllByOrderByStartedAtDesc();

  Page<CalculationRun> findAllByOrderByStartedAtDesc(Pageable pageable);

  List<CalculationRun> findByProgramVersionIdOrderByStartedAtDesc(String programVersionId);

  Page<CalculationRun> findByProgramVersionIdOrderByStartedAtDesc(
      String programVersionId, Pageable pageable);

  List<CalculationRun> findByScopeOrderByStartedAtDesc(CalculationScope scope);
}
