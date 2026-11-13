package com.hcmute.lms.lmsapi.service;

import com.hcmute.lms.lmsapi.dto.ReportCreateRequest;
import com.hcmute.lms.lmsapi.dto.ReportResponse;
import com.hcmute.lms.lmsapi.enums.ReportType;
import java.util.List;

/** Service sinh, lưu và xuất báo cáo chuẩn đầu ra từ kết quả đã tính (F07). */
public interface ReportService {

  ReportResponse generateReport(ReportCreateRequest request, String actorId);

  List<ReportResponse> listReports(ReportType type, String programVersionId, String runId);

  ReportResponse getReport(String id);

  byte[] exportReportCsv(String id);
}
