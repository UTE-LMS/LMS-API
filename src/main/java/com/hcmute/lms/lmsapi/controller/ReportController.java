package com.hcmute.lms.lmsapi.controller;

import com.hcmute.lms.lmsapi.dto.ReportCreateRequest;
import com.hcmute.lms.lmsapi.dto.ReportResponse;
import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import com.hcmute.lms.lmsapi.enums.ReportType;
import com.hcmute.lms.lmsapi.service.ReportService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.List;
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
    log.info("API request: POST /api/v1/reports type=[{}] runId=[{}]", request.getType(), request.getRunId());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(reportService.generateReport(request, actorId));
  }

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<List<ReportResponse>> listReports(
      @RequestParam(name = "type", required = false) ReportType type,
      @RequestParam(name = "programVersionId", required = false) String programVersionId,
      @RequestParam(name = "runId", required = false) String runId) {
    return ResponseEntity.ok(reportService.listReports(type, programVersionId, runId));
  }

  @GetMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<ReportResponse> getReport(
      @PathVariable("id")
          @Pattern(regexp = ID_PATTERN, message = "reportId phải là ObjectId 24 ký tự hex")
          String id) {
    return ResponseEntity.ok(reportService.getReport(id));
  }

  @PostMapping(value = "/{id}/export", produces = "text/csv; charset=UTF-8")
  @PreAuthorize("hasAnyRole('ADMIN', 'PROGRAM_MANAGER', 'QA', 'LECTURER')")
  public ResponseEntity<byte[]> exportReport(
      @PathVariable("id")
          @Pattern(regexp = ID_PATTERN, message = "reportId phải là ObjectId 24 ký tự hex")
          String id) {
    byte[] content = reportService.exportReportCsv(id);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"report-" + id + ".csv\"")
        .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
        .body(content);
  }
}
