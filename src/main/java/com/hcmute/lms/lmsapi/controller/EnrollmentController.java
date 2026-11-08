package com.hcmute.lms.lmsapi.controller;

import com.hcmute.lms.lmsapi.dto.EnrollmentListResponse;
import com.hcmute.lms.lmsapi.dto.EnrollmentResponse;
import com.hcmute.lms.lmsapi.dto.EnrollmentStatusUpdateRequest;
import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import com.hcmute.lms.lmsapi.enums.EnrollmentStatus;
import com.hcmute.lms.lmsapi.service.EnrollmentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API ghi danh sinh viên vào lớp học phần (F05a / US-20). */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class EnrollmentController {

  private final EnrollmentService enrollmentService;

  @GetMapping("/sections/{sectionId}/enrollments")
  @PreAuthorize(
      "@scopeAuthorizationService.canAccessSection(authentication.principal, #sectionId, T(com.hcmute.lms.lmsapi.enums.AccessType).READ)")
  public ResponseEntity<EnrollmentListResponse> getEnrollments(
      @PathVariable("sectionId")
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "ID lớp học phần phải là ObjectId 24 ký tự hex")
          String sectionId,
      @RequestParam(name = "status", required = false) EnrollmentStatus status) {
    return ResponseEntity.ok(enrollmentService.getEnrollments(sectionId, status));
  }

  @PutMapping("/enrollments/{id}/status")
  @PreAuthorize(
      "@scopeAuthorizationService.canAccessEnrollment(authentication.principal, #id, T(com.hcmute.lms.lmsapi.enums.AccessType).WRITE)")
  public ResponseEntity<EnrollmentResponse> updateStatus(
      @PathVariable("id")
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "ID ghi danh phải là ObjectId 24 ký tự hex")
          String id,
      @Valid @RequestBody EnrollmentStatusUpdateRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    String actor = principal != null ? principal.getUsername() : "system";
    log.info(
        "API request: PUT /api/v1/enrollments/{}/status to [{}] by [{}]",
        id,
        request.getStatus(),
        actor);
    return ResponseEntity.ok(enrollmentService.updateStatus(id, request, actor));
  }
}
