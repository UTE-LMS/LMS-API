package com.hcmute.lms.lmsapi.service;

import com.hcmute.lms.lmsapi.dto.AggregateResultResponse;
import com.hcmute.lms.lmsapi.dto.CalculationRunRequest;
import com.hcmute.lms.lmsapi.dto.CalculationRunResponse;
import com.hcmute.lms.lmsapi.dto.PageResponse;
import com.hcmute.lms.lmsapi.dto.StudentResultResponse;
import com.hcmute.lms.lmsapi.enums.ResultLevel;

/**
 * Service chạy và tra cứu kết quả đo lường chuẩn đầu ra 4 cấp độ (F06). Các hàm danh sách đều phân
 * trang: {@code page} từ 0, {@code size} trong [1, 100].
 */
public interface CalculationService {

  CalculationRunResponse triggerCalculation(CalculationRunRequest request, String actorId);

  PageResponse<CalculationRunResponse> getCalculationRuns(
      String programVersionId, int page, int size);

  CalculationRunResponse getCalculationRun(String runId);

  /**
   * Lấy kết quả từng sinh viên của một lượt tính; mọi tham số lọc đều tùy chọn.
   *
   * @param studentId chỉ lấy kết quả của sinh viên này
   * @param subjectId chỉ lấy kết quả của CLO/PI/PLO này
   * @param passed true: chỉ kết quả Đạt; false: chỉ kết quả Chưa đạt (bỏ qua kết quả thiếu dữ liệu)
   */
  PageResponse<StudentResultResponse> getStudentResults(
      String runId,
      ResultLevel level,
      String sectionId,
      String assessmentId,
      String studentId,
      String subjectId,
      Boolean passed,
      int page,
      int size);

  PageResponse<AggregateResultResponse> getAggregateResults(
      String runId, ResultLevel level, String sectionId, int page, int size);
}
