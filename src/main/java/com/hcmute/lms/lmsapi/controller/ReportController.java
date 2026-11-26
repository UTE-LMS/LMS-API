package com.hcmute.lms.lmsapi.controller;

import com.hcmute.lms.lmsapi.dto.PageResponse;
import com.hcmute.lms.lmsapi.dto.ReportCreateRequest;
import com.hcmute.lms.lmsapi.dto.ReportResponse;
import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import com.hcmute.lms.lmsapi.enums.ReportType;
import com.hcmute.lms.lmsapi.exporter.ExcelExportSupport;
import com.hcmute.lms.lmsapi.service.ReportService;
import com.hcmute.lms.lmsapi.util.ImportTemplateResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Controller báo cáo QA / Giảng viên / Quản lý CTĐT (F07: US-31, US-32, US-33). */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

  private static final String ID_PATTERN = "^[0-9a-fA-F]{24}$";

  private final ReportService reportService;

  @PostMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'PROGRAM_MANAGER', 'QA', 'LECTURER')")
  public ResponseEntity<ReportResponse> generateReport(
      @Valid @RequestBody ReportCreateRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    String actorId = principal != null ? principal.getId() : null;
    log.info(
        "API request: POST /api/v1/reports type=[{}] runId=[{}]",
        request.getType(),
        request.getRunId());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(reportService.generateReport(request, actorId));
  }

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<PageResponse<ReportResponse>> listReports(
      @RequestParam(name = "type", required = false) ReportType type,
      @RequestParam(name = "programVersionId", required = false) String programVersionId,
      @RequestParam(name = "runId", required = false) String runId,
      @RequestParam(name = "page", defaultValue = "0")
          @Min(value = 0, message = "Chỉ số trang phải lớn hơn hoặc bằng 0")
          int page,
      @RequestParam(name = "size", defaultValue = "20")
          @Min(value = 1, message = "Kích thước trang phải lớn hơn hoặc bằng 1")
          @Max(value = 100, message = "Kích thước trang tối đa 100")
          int size) {
    return ResponseEntity.ok(reportService.listReports(type, programVersionId, runId, page, size));
  }

  @GetMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<ReportResponse> getReport(
      @PathVariable("id")
          @Pattern(regexp = ID_PATTERN, message = "reportId phải là ObjectId 24 ký tự hex")
          String id) {
    return ResponseEntity.ok(reportService.getReport(id));
  }

  @PostMapping("/{id}/export")
  @PreAuthorize("hasAnyRole('ADMIN', 'PROGRAM_MANAGER', 'QA', 'LECTURER')")
  public ResponseEntity<byte[]> exportReport(
      @PathVariable("id")
          @Pattern(regexp = ID_PATTERN, message = "reportId phải là ObjectId 24 ký tự hex")
          String id,
      @RequestParam(name = "format", defaultValue = "csv") String format) {
    String normalized = ExcelExportSupport.normalizeFormat(format);
    boolean isXlsx = "xlsx".equals(normalized);
    byte[] content =
        isXlsx ? reportService.exportReportXlsx(id) : reportService.exportReportCsv(id);
    String extension = isXlsx ? ".xlsx" : ".csv";
    String mediaType =
        isXlsx ? ImportTemplateResponses.XLSX_MEDIA_TYPE : ImportTemplateResponses.CSV_MEDIA_TYPE;
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"report-" + id + extension + "\"")
        .contentType(MediaType.parseMediaType(mediaType))
        .body(content);
  }
}
