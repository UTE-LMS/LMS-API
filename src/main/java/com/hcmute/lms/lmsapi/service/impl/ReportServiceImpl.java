package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.document.AggregateResult;
import com.hcmute.lms.lmsapi.document.CalculationRun;
import com.hcmute.lms.lmsapi.document.CourseSection;
import com.hcmute.lms.lmsapi.document.Report;
import com.hcmute.lms.lmsapi.document.Student;
import com.hcmute.lms.lmsapi.document.StudentResult;
import com.hcmute.lms.lmsapi.dto.PageResponse;
import com.hcmute.lms.lmsapi.dto.ReportCreateRequest;
import com.hcmute.lms.lmsapi.dto.ReportResponse;
import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import com.hcmute.lms.lmsapi.enums.AccessType;
import com.hcmute.lms.lmsapi.enums.CalculationScope;
import com.hcmute.lms.lmsapi.enums.CalculationStatus;
import com.hcmute.lms.lmsapi.enums.ReportType;
import com.hcmute.lms.lmsapi.enums.ResultLevel;
import com.hcmute.lms.lmsapi.enums.ResultStatus;
import com.hcmute.lms.lmsapi.enums.Role;
import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
import com.hcmute.lms.lmsapi.exception.ResourceNotFoundException;
import com.hcmute.lms.lmsapi.exporter.ReportExcelExporter;
import com.hcmute.lms.lmsapi.repository.AggregateResultRepository;
import com.hcmute.lms.lmsapi.repository.CalculationRunRepository;
import com.hcmute.lms.lmsapi.repository.CloRepository;
import com.hcmute.lms.lmsapi.repository.CourseSectionRepository;
import com.hcmute.lms.lmsapi.repository.PiRepository;
import com.hcmute.lms.lmsapi.repository.PloRepository;
import com.hcmute.lms.lmsapi.repository.ReportRepository;
import com.hcmute.lms.lmsapi.repository.StudentRepository;
import com.hcmute.lms.lmsapi.repository.StudentResultRepository;
import com.hcmute.lms.lmsapi.service.ReportService;
import com.hcmute.lms.lmsapi.service.ScopeAuthorizationService;
import com.hcmute.lms.lmsapi.util.PageableValidationUtils;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/** Triển khai sinh, lưu và xuất báo cáo chuẩn đầu ra từ kết quả đã tính (F07). */
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

  private final ReportRepository reportRepository;
  private final CalculationRunRepository calculationRunRepository;
  private final AggregateResultRepository aggregateResultRepository;
  private final StudentResultRepository studentResultRepository;
  private final CloRepository cloRepository;
  private final PiRepository piRepository;
  private final PloRepository ploRepository;
  private final StudentRepository studentRepository;
  private final CourseSectionRepository courseSectionRepository;
  private final ScopeAuthorizationService scopeAuthorizationService;
  private final ReportExcelExporter reportExcelExporter;

  @Override
  public ReportResponse generateReport(ReportCreateRequest request, String actorId) {
    CalculationRun run =
        calculationRunRepository
            .findById(request.getRunId())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Không tìm thấy lượt tính: " + request.getRunId()));

    if (run.getStatus() != CalculationStatus.SUCCESS) {
      throw new BusinessRuleViolationException(
          "Chỉ được sinh báo cáo từ lượt tính có trạng thái SUCCESS");
    }

    verifyScopeAccess(request, run);

    Map<String, Object> params = buildParams(request);
    Map<String, Object> summary = buildSummary(request, run);
    String limitations = buildLimitations(request, run, summary);
    String title =
        request.getTitle() != null && !request.getTitle().isBlank()
            ? request.getTitle().trim()
            : defaultTitle(request.getType());

    Report saved =
        reportRepository.save(
            Report.builder()
                .type(request.getType())
                .title(title)
                .params(params)
                .runId(run.getId())
                .programVersionId(run.getProgramVersionId())
                .summary(summary)
                .limitations(limitations)
                .generatedBy(actorId)
                .createdAt(Instant.now())
                .build());
    return toResponse(saved);
  }

  @Override
  public PageResponse<ReportResponse> listReports(
      ReportType type, String programVersionId, String runId, int page, int size) {
    PageableValidationUtils.validate(page, size);

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    UserPrincipal principal =
        auth != null && auth.getPrincipal() instanceof UserPrincipal p ? p : null;

    boolean isGlobal =
        principal == null
            || principal.getRoles() == null
            || principal.getRoles().contains(Role.ADMIN)
            || principal.getRoles().contains(Role.QA);

    if (!isGlobal) {
      boolean isPm = principal.getRoles().contains(Role.PROGRAM_MANAGER);
      boolean isLecturer = principal.getRoles().contains(Role.LECTURER);
      if (isLecturer && !isPm) {
        if (type == ReportType.COURSE_PI || type == ReportType.PROGRAM_PLO) {
          throw new AccessDeniedException("Giảng viên không có quyền xem loại báo cáo này");
        }
        if (programVersionId != null && !programVersionId.isBlank()) {
          throw new AccessDeniedException(
              "Giảng viên không có quyền lọc báo cáo theo phiên bản CTĐT");
        }
      } else if (isPm) {
        if (programVersionId != null
            && !programVersionId.isBlank()
            && !scopeAuthorizationService.canAccessProgramVersion(
                principal, programVersionId, AccessType.READ)) {
          throw new AccessDeniedException("Không có quyền truy cập phiên bản CTĐT này");
        }
      }
    }

    List<Report> reports;
    if (runId != null && !runId.isBlank()) {
      reports = reportRepository.findByRunIdOrderByCreatedAtDesc(runId);
    } else if (type != null && programVersionId != null && !programVersionId.isBlank()) {
      reports =
          reportRepository.findByTypeAndProgramVersionIdOrderByCreatedAtDesc(
              type, programVersionId);
    } else {
      reports = reportRepository.findAllByOrderByCreatedAtDesc();
    }

    if (!isGlobal) {
      boolean isPm = principal.getRoles().contains(Role.PROGRAM_MANAGER);
      boolean isLecturer = principal.getRoles().contains(Role.LECTURER);
      if (isLecturer && !isPm) {
        List<String> mySectionIds =
            courseSectionRepository.findByLecturerIdsContaining(principal.getId()).stream()
                .map(CourseSection::getId)
                .toList();
        reports =
            reports.stream()
                .filter(
                    r ->
                        (r.getType() == ReportType.SECTION_CLO
                                || r.getType() == ReportType.DATA_COMPLETENESS)
                            && r.getParams() != null
                            && r.getParams().get("sectionId") instanceof String secId
                            && mySectionIds.contains(secId))
                .toList();
      } else if (isPm) {
        reports =
            reports.stream()
                .filter(r -> scopeAuthorizationService.canAccessReport(principal, r))
                .toList();
      }
    }

    List<Report> filtered =
        reports.stream()
            .filter(r -> type == null || r.getType() == type)
            .filter(
                r ->
                    programVersionId == null
                        || programVersionId.isBlank()
                        || programVersionId.equals(r.getProgramVersionId()))
            .toList();
    return PageResponse.slice(
        filtered, page, size, pageItems -> pageItems.stream().map(this::toResponse).toList());
  }

  @Override
  public ReportResponse getReport(String id) {
    Report report =
        reportRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy báo cáo: " + id));
    verifyReportReadAccess(report);
    return toResponse(report);
  }

  @Override
  public byte[] exportReportCsv(String id) {
    Report report = loadAuthorizedReport(id);

    StringBuilder csv = new StringBuilder("\uFEFF");
    csv.append("ReportId,Type,Title,RunId,Limitations\n");
    csv.append(escapeCsv(report.getId()))
        .append(',')
        .append(report.getType().name())
        .append(',')
        .append(escapeCsv(report.getTitle()))
        .append(',')
        .append(escapeCsv(report.getRunId()))
        .append(',')
        .append(escapeCsv(report.getLimitations()))
        .append("\n\n");

    Object itemsObj = report.getSummary().get("items");
    if (itemsObj instanceof List<?> items && !items.isEmpty()) {
      csv.append(
          "Level,SubjectCode,Enrolled,Evaluated,Passed,Failed,PassRate,PassRateOfEnrolled,DataCompleteness\n");
      for (Object itemObj : items) {
        if (itemObj instanceof Map<?, ?> row) {
          csv.append(escapeCsv(String.valueOf(row.get("level"))))
              .append(',')
              .append(escapeCsv(String.valueOf(row.get("subjectCode"))))
              .append(',')
              .append(row.get("enrolled"))
              .append(',')
              .append(row.get("evaluated"))
              .append(',')
              .append(row.get("passed"))
              .append(',')
              .append(row.get("failed"))
              .append(',')
              .append(row.get("passRate"))
              .append(',')
              .append(row.get("passRateOfEnrolled"))
              .append(',')
              .append(row.get("dataCompleteness"))
              .append('\n');
        }
      }
    }

    recordExport(report, "csv", "report-" + report.getId() + ".csv");
    return csv.toString().getBytes(StandardCharsets.UTF_8);
  }

  @Override
  public byte[] exportReportXlsx(String id) {
    Report report = loadAuthorizedReport(id);
    byte[] content = reportExcelExporter.exportToXlsx(report);
    recordExport(report, "xlsx", "report-" + report.getId() + ".xlsx");
    return content;
  }

  private Report loadAuthorizedReport(String id) {
    Report report =
        reportRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy báo cáo: " + id));
    verifyReportReadAccess(report);
    return report;
  }

  private void recordExport(Report report, String format, String fileRef) {
    report
        .getExports()
        .add(
            Report.ReportExport.builder()
                .format(format)
                .fileRef(fileRef)
                .generatedAt(Instant.now())
                .build());
    reportRepository.save(report);
  }

  private Map<String, Object> buildSummary(ReportCreateRequest request, CalculationRun run) {
    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("runId", run.getId());
    summary.put("scope", run.getScope().name());
    summary.put("isOfficial", run.isOfficial());
    summary.put("isStale", run.isStale());
    summary.put("startedAt", run.getStartedAt());
    summary.put("finishedAt", run.getFinishedAt());

    if (request.getType() == ReportType.DATA_COMPLETENESS) {
      buildCompletenessSummary(request, run, summary);
      return summary;
    }

    List<AggregateResult> aggregates = loadAggregatesForReport(request, run);
    Map<String, SubjectMeta> subjectMeta = loadSubjectMeta(aggregates);

    List<AggregateResult> ordered =
        aggregates.stream()
            .sorted(
                Comparator.comparing(
                    a -> {
                      SubjectMeta m = subjectMeta.get(a.getSubjectId());
                      return m != null ? m.displayOrder() : Integer.MAX_VALUE;
                    }))
            .toList();

    List<Map<String, Object>> rows = new ArrayList<>();
    boolean hasIncomplete = false;
    for (AggregateResult agg : ordered) {
      int enrolled =
          agg.getCounts() != null && agg.getCounts().getEnrolled() != null
              ? agg.getCounts().getEnrolled()
              : 0;
      int passed =
          agg.getCounts() != null && agg.getCounts().getPassed() != null
              ? agg.getCounts().getPassed()
              : 0;
      int failed =
          agg.getCounts() != null && agg.getCounts().getFailed() != null
              ? agg.getCounts().getFailed()
              : 0;
      int evaluated = passed + failed;
      int insufficient = Math.max(0, enrolled - evaluated);
      Double passRateOfEnrolled = enrolled > 0 ? (double) passed / enrolled : null;
      if (agg.getDataCompleteness() != null && agg.getDataCompleteness() < 1.0) {
        hasIncomplete = true;
      }

      SubjectMeta meta = subjectMeta.get(agg.getSubjectId());
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("level", agg.getLevel().name());
      row.put("subjectId", agg.getSubjectId());
      row.put("subjectCode", meta != null ? meta.code() : agg.getSubjectId());
      row.put("sectionId", agg.getSectionId());
      row.put("assessmentId", agg.getAssessmentId());
      row.put("enrolled", enrolled);
      row.put("evaluated", evaluated);
      row.put("passed", passed);
      row.put("failed", failed);
      row.put("insufficientData", insufficient);
      row.put("mean", agg.getMean());
      row.put("passRate", agg.getPassRate());
      row.put("passRateOfEnrolled", passRateOfEnrolled);
      row.put("passThreshold", agg.getPassThreshold());
      row.put("dataCompleteness", agg.getDataCompleteness());
      rows.add(row);
    }

    summary.put("items", rows);
    summary.put("hasIncompleteData", hasIncomplete);
    return summary;
  }

  private void buildCompletenessSummary(
      ReportCreateRequest request, CalculationRun run, Map<String, Object> summary) {
    List<StudentResult> studentResults =
        request.getSectionId() != null && !request.getSectionId().isBlank()
            ? studentResultRepository.findByRunIdAndLevelAndSectionId(
                run.getId(), ResultLevel.CLO, request.getSectionId())
            : studentResultRepository.findByRunId(run.getId());

    Map<String, String> studentRefs =
        studentRepository
            .findAllById(
                studentResults.stream().map(StudentResult::getStudentId).distinct().toList())
            .stream()
            .collect(Collectors.toMap(Student::getId, Student::getStudentCode, (a, b) -> a));

    List<Map<String, Object>> missingList = new ArrayList<>();
    long total = studentResults.size();
    long computedCount = 0;

    for (StudentResult sr : studentResults) {
      if (sr.getStatus() == ResultStatus.COMPUTED) {
        computedCount++;
      } else {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("studentResultId", sr.getId());
        item.put("studentId", sr.getStudentId());
        item.put("studentCode", studentRefs.get(sr.getStudentId()));
        item.put("level", sr.getLevel().name());
        item.put("subjectId", sr.getSubjectId());
        item.put("sectionId", sr.getSectionId());
        item.put("status", sr.getStatus().name());
        item.put("reason", "Thiếu điểm ở một hoặc nhiều bài đánh giá/CLO");
        missingList.add(item);
      }
    }

    double completeness = total > 0 ? (double) computedCount / total : 0.0;
    summary.put("totalRecords", total);
    summary.put("computedRecords", computedCount);
    summary.put("insufficientRecords", missingList.size());
    summary.put("dataCompleteness", completeness);
    summary.put("missingEntries", missingList);
    summary.put("hasIncompleteData", !missingList.isEmpty());
  }

  private List<AggregateResult> loadAggregatesForReport(
      ReportCreateRequest request, CalculationRun run) {
    return switch (request.getType()) {
      case SECTION_CLO -> {
        if (request.getSectionId() == null || request.getSectionId().isBlank()) {
          throw new BusinessRuleViolationException("Báo cáo SECTION_CLO yêu cầu sectionId");
        }
        yield aggregateResultRepository.findByRunIdAndSectionId(
            run.getId(), request.getSectionId());
      }
      case COURSE_PI -> aggregateResultRepository.findByRunIdAndLevel(run.getId(), ResultLevel.PI);
      case PROGRAM_PLO ->
          aggregateResultRepository.findByRunIdAndLevel(run.getId(), ResultLevel.PLO);
      case DATA_COMPLETENESS -> List.of();
    };
  }

  private String buildLimitations(
      ReportCreateRequest request, CalculationRun run, Map<String, Object> summary) {
    List<String> notes = new ArrayList<>();
    if (!run.isOfficial()) {
      notes.add("Kết quả không chính thức (lượt chạy thử nghiệm).");
    }
    if (run.isStale()) {
      notes.add("Dữ liệu điểm đã thay đổi sau lần tính này.");
    }
    if (Boolean.TRUE.equals(summary.get("hasIncompleteData"))) {
      notes.add("Có sinh viên chưa đủ dữ liệu để đánh giá đầy đủ (xem chi tiết mẫu số).");
    }
    if (request.getType() == ReportType.PROGRAM_PLO && run.getScope() == CalculationScope.SECTION) {
      notes.add("Chưa đủ dữ liệu để kết luận toàn chương trình.");
    }
    return String.join(" ", notes);
  }

  private Map<String, Object> buildParams(ReportCreateRequest request) {
    Map<String, Object> params = new LinkedHashMap<>();
    if (request.getSectionId() != null) {
      params.put("sectionId", request.getSectionId());
    }
    if (request.getCourseId() != null) {
      params.put("courseId", request.getCourseId());
    }
    if (request.getCohort() != null) {
      params.put("cohort", request.getCohort());
    }
    return params;
  }

  private Map<String, SubjectMeta> loadSubjectMeta(List<AggregateResult> aggregates) {
    List<String> ids = aggregates.stream().map(AggregateResult::getSubjectId).distinct().toList();
    Map<String, SubjectMeta> map = new HashMap<>();
    cloRepository
        .findAllById(ids)
        .forEach(c -> map.put(c.getId(), new SubjectMeta(c.getCode(), 0)));
    piRepository
        .findAllById(ids)
        .forEach(
            p ->
                map.put(
                    p.getId(),
                    new SubjectMeta(
                        p.getCode(), p.getDisplayOrder() != null ? p.getDisplayOrder() : 0)));
    ploRepository
        .findAllById(ids)
        .forEach(
            p ->
                map.put(
                    p.getId(),
                    new SubjectMeta(
                        p.getCode(), p.getDisplayOrder() != null ? p.getDisplayOrder() : 0)));
    return map;
  }

  private void verifyScopeAccess(ReportCreateRequest request, CalculationRun run) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
      return;
    }
    boolean isGlobal =
        principal.getRoles() == null
            || principal.getRoles().contains(Role.ADMIN)
            || principal.getRoles().contains(Role.QA);
    if (isGlobal) {
      return;
    }

    if (!scopeAuthorizationService.canAccessCalculationRun(principal, run)) {
      throw new AccessDeniedException("Không có quyền sinh báo cáo từ lượt tính toán này");
    }

    boolean isPm = principal.getRoles().contains(Role.PROGRAM_MANAGER);
    boolean isLecturer = principal.getRoles().contains(Role.LECTURER);

    if (isLecturer && !isPm) {
      if (request.getType() == ReportType.COURSE_PI
          || request.getType() == ReportType.PROGRAM_PLO) {
        throw new AccessDeniedException(
            "Giảng viên không có quyền sinh báo cáo cấp CTĐT/học phần (PI/PLO)");
      }
      if (request.getType() == ReportType.DATA_COMPLETENESS
          && (request.getSectionId() == null || request.getSectionId().isBlank())) {
        throw new AccessDeniedException(
            "Giảng viên phải chỉ định lớp học phần khi sinh báo cáo độ đầy đủ dữ liệu");
      }
      if (request.getSectionId() != null && !request.getSectionId().isBlank()) {
        if (!scopeAuthorizationService.canAccessSection(
            principal, request.getSectionId(), AccessType.READ)) {
          throw new AccessDeniedException("Không có quyền sinh báo cáo cho lớp học phần này");
        }
      } else {
        throw new AccessDeniedException("Giảng viên phải chỉ định lớp học phần để sinh báo cáo");
      }
      return;
    }

    if (isPm) {
      if (request.getType() == ReportType.SECTION_CLO) {
        if (request.getSectionId() == null
            || !scopeAuthorizationService.canAccessSection(
                principal, request.getSectionId(), AccessType.READ)) {
          throw new AccessDeniedException("Không có quyền sinh báo cáo cho lớp học phần này");
        }
      } else if (request.getType() == ReportType.COURSE_PI
          || request.getType() == ReportType.PROGRAM_PLO) {
        if (run.getProgramVersionId() == null
            || !scopeAuthorizationService.canAccessProgramVersion(
                principal, run.getProgramVersionId(), AccessType.READ)) {
          throw new AccessDeniedException("Không có quyền sinh báo cáo cho phiên bản CTĐT này");
        }
      } else if (request.getType() == ReportType.DATA_COMPLETENESS) {
        if (request.getSectionId() != null && !request.getSectionId().isBlank()) {
          if (!scopeAuthorizationService.canAccessSection(
              principal, request.getSectionId(), AccessType.READ)) {
            throw new AccessDeniedException("Không có quyền sinh báo cáo cho lớp học phần này");
          }
        } else if (run.getProgramVersionId() != null && !run.getProgramVersionId().isBlank()) {
          if (!scopeAuthorizationService.canAccessProgramVersion(
              principal, run.getProgramVersionId(), AccessType.READ)) {
            throw new AccessDeniedException("Không có quyền sinh báo cáo cho phiên bản CTĐT này");
          }
        } else {
          throw new AccessDeniedException("Không có quyền sinh báo cáo độ đầy đủ dữ liệu");
        }
      }
    }
  }

  private void verifyReportReadAccess(Report report) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
      return;
    }
    if (!scopeAuthorizationService.canAccessReport(principal, report)) {
      throw new AccessDeniedException("Không có quyền truy cập báo cáo này");
    }
  }

  private String defaultTitle(ReportType type) {
    return switch (type) {
      case SECTION_CLO -> "Báo cáo CLO theo lớp học phần";
      case COURSE_PI -> "Báo cáo PI theo học phần/khóa";
      case PROGRAM_PLO -> "Báo cáo PLO theo CTĐT";
      case DATA_COMPLETENESS -> "Báo cáo độ đầy đủ dữ liệu";
    };
  }

  private String escapeCsv(String raw) {
    if (raw == null) {
      return "";
    }
    String escaped = raw.replace("\"", "\"\"");
    return "\"" + escaped + "\"";
  }

  private ReportResponse toResponse(Report report) {
    return ReportResponse.builder()
        .id(report.getId())
        .type(report.getType())
        .title(report.getTitle())
        .params(report.getParams())
        .runId(report.getRunId())
        .programVersionId(report.getProgramVersionId())
        .summary(report.getSummary())
        .limitations(report.getLimitations())
        .exports(report.getExports())
        .generatedBy(report.getGeneratedBy())
        .createdAt(report.getCreatedAt())
        .build();
  }

  private record SubjectMeta(String code, int displayOrder) {}
}
