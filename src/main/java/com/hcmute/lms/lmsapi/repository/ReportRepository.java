package com.hcmute.lms.lmsapi.repository;

import com.hcmute.lms.lmsapi.document.Report;
import com.hcmute.lms.lmsapi.enums.ReportType;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ReportRepository extends MongoRepository<Report, String> {

  List<Report> findAllByOrderByCreatedAtDesc();

  List<Report> findByRunIdOrderByCreatedAtDesc(String runId);

  List<Report> findByTypeAndProgramVersionIdOrderByCreatedAtDesc(
      ReportType type, String programVersionId);
}
