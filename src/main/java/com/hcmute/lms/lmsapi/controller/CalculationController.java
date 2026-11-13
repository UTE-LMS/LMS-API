package com.hcmute.lms.lmsapi.controller;

import com.hcmute.lms.lmsapi.dto.AggregateResultResponse;
import com.hcmute.lms.lmsapi.dto.CalculationRunRequest;
import com.hcmute.lms.lmsapi.dto.CalculationRunResponse;
import com.hcmute.lms.lmsapi.dto.StudentResultResponse;
import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import com.hcmute.lms.lmsapi.enums.ResultLevel;
import com.hcmute.lms.lmsapi.service.CalculationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.List;
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
  public ResponseEntity<List<CalculationRunResponse>> listCalculationRuns(
      @RequestParam(name = "programVersionId", required = false) String programVersionId) {
    return ResponseEntity.ok(calculationService.getCalculationRuns(programVersionId));
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
  public ResponseEntity<List<StudentResultResponse>> getStudentResults(
      @PathVariable("id")
          @Pattern(regexp = ID_PATTERN, message = "runId phải là ObjectId 24 ký tự hex")
          String id,
      @RequestParam(name = "level", required = false) ResultLevel level,
      @RequestParam(name = "sectionId", required = false) String sectionId,
      @RequestParam(name = "assessmentId", required = false) String assessmentId) {
    return ResponseEntity.ok(
        calculationService.getStudentResults(id, level, sectionId, assessmentId));
  }

  @GetMapping("/{id}/aggregate-results")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<List<AggregateResultResponse>> getAggregateResults(
      @PathVariable("id")
          @Pattern(regexp = ID_PATTERN, message = "runId phải là ObjectId 24 ký tự hex")
          String id,
      @RequestParam(name = "level", required = false) ResultLevel level,
      @RequestParam(name = "sectionId", required = false) String sectionId) {
    return ResponseEntity.ok(calculationService.getAggregateResults(id, level, sectionId));
  }
}
