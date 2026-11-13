package com.hcmute.lms.lmsapi.repository;

import com.hcmute.lms.lmsapi.document.AggregateResult;
import com.hcmute.lms.lmsapi.enums.ResultLevel;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AggregateResultRepository extends MongoRepository<AggregateResult, String> {

  List<AggregateResult> findByRunId(String runId);

  List<AggregateResult> findByRunIdAndLevel(String runId, ResultLevel level);

  List<AggregateResult> findByRunIdAndSectionId(String runId, String sectionId);
}
