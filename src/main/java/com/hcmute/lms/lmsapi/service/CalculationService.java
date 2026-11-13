package com.hcmute.lms.lmsapi.service;

import com.hcmute.lms.lmsapi.dto.AggregateResultResponse;
import com.hcmute.lms.lmsapi.dto.CalculationRunRequest;
import com.hcmute.lms.lmsapi.dto.CalculationRunResponse;
import com.hcmute.lms.lmsapi.dto.StudentResultResponse;
import com.hcmute.lms.lmsapi.enums.ResultLevel;
import java.util.List;

/** Service chạy và tra cứu kết quả đo lường chuẩn đầu ra 4 cấp độ (F06). */
public interface CalculationService {

  CalculationRunResponse triggerCalculation(CalculationRunRequest request, String actorId);

  List<CalculationRunResponse> getCalculationRuns(String programVersionId);

  CalculationRunResponse getCalculationRun(String runId);

  List<StudentResultResponse> getStudentResults(
      String runId, ResultLevel level, String sectionId, String assessmentId);

  List<AggregateResultResponse> getAggregateResults(
      String runId, ResultLevel level, String sectionId);
}
