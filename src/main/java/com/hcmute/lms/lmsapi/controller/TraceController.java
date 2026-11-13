package com.hcmute.lms.lmsapi.controller;

import com.hcmute.lms.lmsapi.dto.ScoreTraceResponse;
import com.hcmute.lms.lmsapi.dto.StudentResultTraceResponse;
import com.hcmute.lms.lmsapi.service.TraceService;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Controller truy vết ngược kết quả đo lường về điểm và dòng tệp gốc (US-34). */
@Validated
@RestController
@RequestMapping("/api/v1/trace")
@RequiredArgsConstructor
public class TraceController {

  private static final String ID_PATTERN = "^[0-9a-fA-F]{24}$";

  private final TraceService traceService;

  @GetMapping("/student-results/{id}")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<StudentResultTraceResponse> traceStudentResult(
      @PathVariable("id")
          @Pattern(regexp = ID_PATTERN, message = "studentResultId phải là ObjectId 24 ký tự hex")
          String id) {
    return ResponseEntity.ok(traceService.traceStudentResult(id));
  }

  @GetMapping("/scores/{id}")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<ScoreTraceResponse> traceScore(
      @PathVariable("id")
          @Pattern(regexp = ID_PATTERN, message = "scoreId phải là ObjectId 24 ký tự hex")
          String id) {
    return ResponseEntity.ok(traceService.traceScore(id));
  }
}
