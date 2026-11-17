package com.hcmute.lms.lmsapi.controller;

import com.hcmute.lms.lmsapi.dto.AggregateResultResponse;
import com.hcmute.lms.lmsapi.dto.CalculationRunRequest;
import com.hcmute.lms.lmsapi.dto.CalculationRunResponse;
import com.hcmute.lms.lmsapi.dto.PageResponse;
import com.hcmute.lms.lmsapi.dto.StudentResultResponse;
import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import com.hcmute.lms.lmsapi.enums.ResultLevel;
import com.hcmute.lms.lmsapi.service.CalculationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
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

/** Controller đo lường chuẩn đầu ra 4 cấp độ (F06: ASSESSMENT_CLO -> CLO -> PI -> PLO). */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/calculation-runs")
@RequiredArgsConstructor
public class CalculationController {

  private static final String ID_PATTERN = "^[0-9a-fA-F]{24}$";

  private final CalculationService calculationService;

  @PostMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'PROGRAM_MANAGER', 'LECTURER', 'QA')")
  public ResponseEntity<CalculationRunResponse> triggerCalculation(
      @Valid @RequestBody CalculationRunRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    String actorId = principal != null ? principal.getId() : null;
    log.info("API request: POST /api/v1/calculation-runs scope=[{}]", request.getScope());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(calculationService.triggerCalculation(request, actorId));
  }

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<PageResponse<CalculationRunResponse>> listCalculationRuns(
      @RequestParam(name = "programVersionId", required = false) String programVersionId,
      @RequestParam(name = "page", defaultValue = "0")
          @Min(value = 0, message = "Chỉ số trang phải lớn hơn hoặc bằng 0")
          int page,
      @RequestParam(name = "size", defaultValue = "20")
          @Min(value = 1, message = "Kích thước trang phải lớn hơn hoặc bằng 1")
          @Max(value = 100, message = "Kích thước trang tối đa 100")
          int size) {
    return ResponseEntity.ok(calculationService.getCalculationRuns(programVersionId, page, size));
  }

  @GetMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<CalculationRunResponse> getCalculationRun(
      @PathVariable("id")
          @Pattern(regexp = ID_PATTERN, message = "runId phải là ObjectId 24 ký tự hex")
          String id) {
    return ResponseEntity.ok(calculationService.getCalculationRun(id));
  }

  @GetMapping("/{id}/student-results")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<PageResponse<StudentResultResponse>> getStudentResults(
      @PathVariable("id")
          @Pattern(regexp = ID_PATTERN, message = "runId phải là ObjectId 24 ký tự hex")
          String id,
      @RequestParam(name = "level", required = false) ResultLevel level,
      @RequestParam(name = "sectionId", required = false) String sectionId,
      @RequestParam(name = "assessmentId", required = false) String assessmentId,
      @RequestParam(name = "studentId", required = false) String studentId,
      @RequestParam(name = "subjectId", required = false) String subjectId,
      @RequestParam(name = "passed", required = false) Boolean passed,
      @RequestParam(name = "page", defaultValue = "0")
          @Min(value = 0, message = "Chỉ số trang phải lớn hơn hoặc bằng 0")
          int page,
      @RequestParam(name = "size", defaultValue = "20")
          @Min(value = 1, message = "Kích thước trang phải lớn hơn hoặc bằng 1")
          @Max(value = 100, message = "Kích thước trang tối đa 100")
          int size) {
    return ResponseEntity.ok(
        calculationService.getStudentResults(
            id, level, sectionId, assessmentId, studentId, subjectId, passed, page, size));
  }

  @GetMapping("/{id}/aggregate-results")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<PageResponse<AggregateResultResponse>> getAggregateResults(
      @PathVariable("id")
          @Pattern(regexp = ID_PATTERN, message = "runId phải là ObjectId 24 ký tự hex")
          String id,
      @RequestParam(name = "level", required = false) ResultLevel level,
      @RequestParam(name = "sectionId", required = false) String sectionId,
      @RequestParam(name = "page", defaultValue = "0")
          @Min(value = 0, message = "Chỉ số trang phải lớn hơn hoặc bằng 0")
          int page,
      @RequestParam(name = "size", defaultValue = "20")
          @Min(value = 1, message = "Kích thước trang phải lớn hơn hoặc bằng 1")
          @Max(value = 100, message = "Kích thước trang tối đa 100")
          int size) {
    return ResponseEntity.ok(
        calculationService.getAggregateResults(id, level, sectionId, page, size));
  }
}
