package com.hcmute.lms.lmsapi.controller;

import com.hcmute.lms.lmsapi.dto.AssessmentItemsRequest;
import com.hcmute.lms.lmsapi.dto.AssessmentRequest;
import com.hcmute.lms.lmsapi.dto.AssessmentResponse;
import com.hcmute.lms.lmsapi.dto.AssessmentUpdateRequest;
import com.hcmute.lms.lmsapi.dto.CoverageResponse;
import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import com.hcmute.lms.lmsapi.service.AssessmentService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API bài đánh giá: khung, bản lớp, items, độ phủ và khóa/mở khóa ma trận (ADR 0014, v0.7). */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AssessmentController {

  private static final String ID_PATTERN = "^[0-9a-fA-F]{24}$";
  private static final String READ = "T(com.hcmute.lms.lmsapi.enums.AccessType).READ";
  private static final String WRITE = "T(com.hcmute.lms.lmsapi.enums.AccessType).WRITE";

  private final AssessmentService assessmentService;

  // ------------------------------------------------------------------ khung theo đề cương

  @GetMapping("/syllabi/{syllabusId}/assessment-templates")
  @PreAuthorize(
      "@scopeAuthorizationService.canAccessSyllabus(authentication.principal, #syllabusId, "
          + READ
          + ")")
  public ResponseEntity<List<AssessmentResponse>> getTemplates(
      @PathVariable("syllabusId")
          @Pattern(regexp = ID_PATTERN, message = "ID đề cương phải là ObjectId 24 ký tự hex")
          String syllabusId) {
    return ResponseEntity.ok(assessmentService.getTemplates(syllabusId));
  }

  @PostMapping("/syllabi/{syllabusId}/assessment-templates")
  @PreAuthorize(
      "hasAnyRole('LECTURER', 'PROGRAM_MANAGER') and"
          + " @scopeAuthorizationService.canAccessSyllabus(authentication.principal, #syllabusId, "
          + WRITE
          + ")")
  public ResponseEntity<AssessmentResponse> createTemplate(
      @PathVariable("syllabusId")
          @Pattern(regexp = ID_PATTERN, message = "ID đề cương phải là ObjectId 24 ký tự hex")
          String syllabusId,
      @Valid @RequestBody AssessmentRequest request) {
    log.info(
        "API request: POST /syllabi/{}/assessment-templates code=[{}]",
        syllabusId,
        request.getCode());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(assessmentService.createTemplate(syllabusId, request));
  }

  // ------------------------------------------------------------------ bản lớp

  @GetMapping("/sections/{sectionId}/assessments")
  @PreAuthorize(
      "@scopeAuthorizationService.canAccessSection(authentication.principal, #sectionId, "
          + READ
          + ")")
  public ResponseEntity<List<AssessmentResponse>> getSectionAssessments(
      @PathVariable("sectionId")
          @Pattern(regexp = ID_PATTERN, message = "ID lớp học phần phải là ObjectId 24 ký tự hex")
          String sectionId) {
    return ResponseEntity.ok(assessmentService.getSectionAssessments(sectionId));
  }

  @PostMapping("/sections/{sectionId}/assessments")
  @PreAuthorize(
      "hasRole('LECTURER') and"
          + " @scopeAuthorizationService.canAccessSection(authentication.principal, #sectionId, "
          + WRITE
          + ")")
  public ResponseEntity<AssessmentResponse> createSectionAssessment(
      @PathVariable("sectionId")
          @Pattern(regexp = ID_PATTERN, message = "ID lớp học phần phải là ObjectId 24 ký tự hex")
          String sectionId,
      @Valid @RequestBody AssessmentRequest request) {
    log.info("API request: POST /sections/{}/assessments code=[{}]", sectionId, request.getCode());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(assessmentService.createSectionAssessment(sectionId, request));
  }

  // ------------------------------------------------------------------ một bài đánh giá

  @GetMapping("/assessments/{id}")
  @PreAuthorize(
      "@scopeAuthorizationService.canAccessAssessment(authentication.principal, #id, " + READ + ")")
  public ResponseEntity<AssessmentResponse> getAssessment(
      @PathVariable("id")
          @Pattern(regexp = ID_PATTERN, message = "ID bài đánh giá phải là ObjectId 24 ký tự hex")
          String id) {
    return ResponseEntity.ok(assessmentService.getAssessment(id));
  }

  @PutMapping("/assessments/{id}")
  @PreAuthorize(
      "hasAnyRole('LECTURER', 'PROGRAM_MANAGER') and"
          + " @scopeAuthorizationService.canAccessAssessment(authentication.principal, #id, "
          + WRITE
          + ")")
  public ResponseEntity<AssessmentResponse> updateAssessment(
      @PathVariable("id")
          @Pattern(regexp = ID_PATTERN, message = "ID bài đánh giá phải là ObjectId 24 ký tự hex")
          String id,
      @Valid @RequestBody AssessmentUpdateRequest request) {
    log.info("API request: PUT /assessments/{}", id);
    return ResponseEntity.ok(assessmentService.updateAssessment(id, request));
  }

  @PutMapping("/assessments/{id}/items")
  @PreAuthorize(
      "hasAnyRole('LECTURER', 'PROGRAM_MANAGER') and"
          + " @scopeAuthorizationService.canAccessAssessment(authentication.principal, #id, "
          + WRITE
          + ")")
  public ResponseEntity<AssessmentResponse> replaceItems(
      @PathVariable("id")
          @Pattern(regexp = ID_PATTERN, message = "ID bài đánh giá phải là ObjectId 24 ký tự hex")
          String id,
      @Valid @RequestBody AssessmentItemsRequest request) {
    log.info("API request: PUT /assessments/{}/items", id);
    return ResponseEntity.ok(assessmentService.replaceItems(id, request));
  }

  @GetMapping("/assessments/{id}/coverage")
  @PreAuthorize(
      "@scopeAuthorizationService.canAccessAssessment(authentication.principal, #id, " + READ + ")")
  public ResponseEntity<CoverageResponse> getCoverage(
      @PathVariable("id")
          @Pattern(regexp = ID_PATTERN, message = "ID bài đánh giá phải là ObjectId 24 ký tự hex")
          String id) {
    return ResponseEntity.ok(assessmentService.getCoverage(id));
  }

  // ------------------------------------------------------------------ khóa / mở khóa / xóa bài

  @PostMapping("/assessments/{id}/lock")
  @PreAuthorize(
      "hasRole('LECTURER') and"
          + " @scopeAuthorizationService.canAccessAssessment(authentication.principal, #id, "
          + WRITE
          + ")")
  public ResponseEntity<AssessmentResponse> lock(
      @PathVariable("id")
          @Pattern(regexp = ID_PATTERN, message = "ID bài đánh giá phải là ObjectId 24 ký tự hex")
          String id,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.info("API request: POST /assessments/{}/lock by [{}]", id, principal.getUsername());
    return ResponseEntity.ok(assessmentService.lock(id, principal.getId()));
  }

  @PostMapping("/assessments/{id}/unlock")
  @PreAuthorize(
      "hasRole('LECTURER') and"
          + " @scopeAuthorizationService.canAccessAssessment(authentication.principal, #id, "
          + WRITE
          + ")")
  public ResponseEntity<AssessmentResponse> unlock(
      @PathVariable("id")
          @Pattern(regexp = ID_PATTERN, message = "ID bài đánh giá phải là ObjectId 24 ký tự hex")
          String id,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.info("API request: POST /assessments/{}/unlock by [{}]", id, principal.getUsername());
    return ResponseEntity.ok(assessmentService.unlock(id, principal.getId()));
  }

  @DeleteMapping("/assessments/{id}")
  @PreAuthorize(
      "hasRole('LECTURER') and"
          + " @scopeAuthorizationService.canAccessAssessment(authentication.principal, #id, "
          + WRITE
          + ")")
  public ResponseEntity<Void> deleteAssessment(
      @PathVariable("id")
          @Pattern(regexp = ID_PATTERN, message = "ID bài đánh giá phải là ObjectId 24 ký tự hex")
          String id,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.info("API request: DELETE /assessments/{} by [{}]", id, principal.getUsername());
    assessmentService.deleteAssessment(id, principal.getId());
    return ResponseEntity.noContent().build();
  }
}
