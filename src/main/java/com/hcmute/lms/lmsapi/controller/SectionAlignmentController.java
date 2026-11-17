package com.hcmute.lms.lmsapi.controller;

import com.hcmute.lms.lmsapi.dto.AssessmentAlignmentResponse;
import com.hcmute.lms.lmsapi.service.AssessmentAlignmentService;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Kiểm tra độ bám sát chuẩn đầu ra của bộ đề trong một lớp học phần (chỉ đọc). */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/sections")
@RequiredArgsConstructor
public class SectionAlignmentController {

  private static final String ID_PATTERN = "^[0-9a-fA-F]{24}$";
  private static final String READ = "T(com.hcmute.lms.lmsapi.enums.AccessType).READ";

  private final AssessmentAlignmentService alignmentService;

  @GetMapping("/{sectionId}/assessment-alignment")
  @PreAuthorize(
      "@scopeAuthorizationService.canAccessSection(authentication.principal, #sectionId, "
          + READ
          + ")")
  public ResponseEntity<AssessmentAlignmentResponse> getAssessmentAlignment(
      @PathVariable("sectionId")
          @Pattern(regexp = ID_PATTERN, message = "sectionId phải là ObjectId 24 ký tự hex")
          String sectionId) {
    log.info("API request: GET /api/v1/sections/{}/assessment-alignment", sectionId);
    return ResponseEntity.ok(alignmentService.getSectionAlignment(sectionId));
  }
}
