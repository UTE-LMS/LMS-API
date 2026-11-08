package com.hcmute.lms.lmsapi.controller;

import com.hcmute.lms.lmsapi.dto.SectionRequest;
import com.hcmute.lms.lmsapi.dto.SectionResponse;
import com.hcmute.lms.lmsapi.dto.SectionStatusUpdateRequest;
import com.hcmute.lms.lmsapi.dto.SectionUpdateRequest;
import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import com.hcmute.lms.lmsapi.service.SectionService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API lớp học phần (F05a / US-19). */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/sections")
@RequiredArgsConstructor
public class SectionController {

  private final SectionService sectionService;

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<List<SectionResponse>> getSections(
      @RequestParam(required = false) String termId,
      @RequestParam(required = false) String courseId,
      @AuthenticationPrincipal UserPrincipal principal) {
    return ResponseEntity.ok(sectionService.getSections(principal, termId, courseId));
  }

  @GetMapping("/{id}")
  @PreAuthorize(
      "@scopeAuthorizationService.canAccessSection(authentication.principal, #id, T(com.hcmute.lms.lmsapi.enums.AccessType).READ)"
          + " or @scopeAuthorizationService.canManageSection(authentication.principal, #id)")
  public ResponseEntity<SectionResponse> getSection(
      @PathVariable("id")
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "ID lớp học phần phải là ObjectId 24 ký tự hex")
          String id) {
    return ResponseEntity.ok(sectionService.getSection(id));
  }

  @PostMapping
  @PreAuthorize(
      "@scopeAuthorizationService.canManageCourseSections(authentication.principal, #request.courseId)")
  public ResponseEntity<SectionResponse> createSection(@Valid @RequestBody SectionRequest request) {
    log.info("API request: POST /api/v1/sections code=[{}]", request.getCode());
    return ResponseEntity.status(HttpStatus.CREATED).body(sectionService.createSection(request));
  }

  @PutMapping("/{id}")
  @PreAuthorize("@scopeAuthorizationService.canManageSection(authentication.principal, #id)")
  public ResponseEntity<SectionResponse> updateSection(
      @PathVariable("id")
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "ID lớp học phần phải là ObjectId 24 ký tự hex")
          String id,
      @Valid @RequestBody SectionUpdateRequest request) {
    log.info("API request: PUT /api/v1/sections/{}", id);
    return ResponseEntity.ok(sectionService.updateLecturers(id, request));
  }

  @PutMapping("/{id}/status")
  @PreAuthorize("@scopeAuthorizationService.canManageSection(authentication.principal, #id)")
  public ResponseEntity<SectionResponse> updateStatus(
      @PathVariable("id")
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "ID lớp học phần phải là ObjectId 24 ký tự hex")
          String id,
      @Valid @RequestBody SectionStatusUpdateRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    String actor = principal != null ? principal.getUsername() : "system";
    log.info(
        "API request: PUT /api/v1/sections/{}/status to [{}] by [{}]",
        id,
        request.getStatus(),
        actor);
    return ResponseEntity.ok(sectionService.updateStatus(id, request, actor));
  }
}
