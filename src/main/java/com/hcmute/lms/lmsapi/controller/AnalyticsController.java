package com.hcmute.lms.lmsapi.controller;

import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import com.hcmute.lms.lmsapi.dto.analytics.AnalyticsMatrixResponse;
import com.hcmute.lms.lmsapi.dto.analytics.AnalyticsQueryRequest;
import com.hcmute.lms.lmsapi.exporter.ExcelExportSupport;
import com.hcmute.lms.lmsapi.service.AnalyticsService;
import com.hcmute.lms.lmsapi.util.ImportTemplateResponses;
import jakarta.validation.Valid;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller phục vụ báo cáo phân tích đa chiều và xuất CSV ma trận (Milestone 1).
 *
 * <p>Endpoints:
 *
 * <ul>
 *   <li>GET /api/v1/reports/analytics: Truy vấn ma trận phân tích đa chiều
 *   <li>GET /api/v1/reports/analytics/export: Xuất file CSV chuẩn RFC 4180 kèm UTF-8 BOM
 * </ul>
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/reports/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

  private final AnalyticsService analyticsService;

  /**
   * Truy vấn động dữ liệu phân tích chuẩn đầu ra theo 5 chiều cross-tab.
   *
   * @param request tham số truy vấn và lọc
   * @param principal thông tin người dùng trong Security Context
   * @return ma trận phân tích 2 chiều kèm các chỉ số đạt chuẩn
   */
  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  @PreAuthorize("hasAnyRole('ADMIN', 'QA', 'PROGRAM_MANAGER', 'LECTURER')")
  public ResponseEntity<AnalyticsMatrixResponse> getAnalytics(
      @Valid @ModelAttribute AnalyticsQueryRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.info(
        "API GET /api/v1/reports/analytics: dimension=[{}] courseId=[{}] programVersionId=[{}]",
        request.getDimension(),
        request.getCourseId(),
        request.getProgramVersionId());
    return ResponseEntity.ok(analyticsService.getAnalyticsMatrix(request, principal));
  }

  /**
   * Xuất dữ liệu ma trận phân tích đa chiều sang file CSV (mặc định) hoặc Excel {@code .xlsx}.
   *
   * @param request tham số truy vấn và lọc
   * @param format {@code csv} (mặc định) hoặc {@code xlsx}
   * @param principal thông tin người dùng trong Security Context
   * @return file CSV UTF-8 BOM hoặc Excel {@code .xlsx}
   */
  @GetMapping("/export")
  @PreAuthorize("hasAnyRole('ADMIN', 'QA', 'PROGRAM_MANAGER', 'LECTURER')")
  public ResponseEntity<byte[]> exportAnalytics(
      @Valid @ModelAttribute AnalyticsQueryRequest request,
      @RequestParam(name = "format", defaultValue = "csv") String format,
      @AuthenticationPrincipal UserPrincipal principal) {
    String normalized = ExcelExportSupport.normalizeFormat(format);
    boolean isXlsx = "xlsx".equals(normalized);
    log.info(
        "API GET /api/v1/reports/analytics/export: dimension=[{}] format=[{}]",
        request.getDimension(),
        normalized);
    byte[] content =
        isXlsx
            ? analyticsService.exportAnalyticsMatrixXlsx(request, principal)
            : analyticsService.exportAnalyticsMatrixCsv(request, principal);
    String dimensionStr =
        request.getDimension() != null ? request.getDimension().name().toLowerCase() : "matrix";
    String extension = isXlsx ? ".xlsx" : ".csv";
    String filename =
        "analytics-matrix-" + dimensionStr + "-" + Instant.now().toEpochMilli() + extension;
    String mediaType =
        isXlsx ? ImportTemplateResponses.XLSX_MEDIA_TYPE : ImportTemplateResponses.CSV_MEDIA_TYPE;

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
        .contentType(MediaType.parseMediaType(mediaType))
        .body(content);
  }
}
