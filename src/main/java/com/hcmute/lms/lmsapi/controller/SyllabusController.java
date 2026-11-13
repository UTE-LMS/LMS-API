package com.hcmute.lms.lmsapi.controller;

import com.hcmute.lms.lmsapi.dto.SyllabusRequest;
import com.hcmute.lms.lmsapi.dto.SyllabusResponse;
import com.hcmute.lms.lmsapi.dto.SyllabusStatusUpdateRequest;
import com.hcmute.lms.lmsapi.service.SyllabusService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SyllabusController {

  private final SyllabusService syllabusService;

  @GetMapping("/courses/{courseId}/syllabi")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<List<SyllabusResponse>> getSyllabi(
      @PathVariable("courseId")
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "ID học phần phải là ObjectId 24 ký tự hex")
          String courseId) {
    return ResponseEntity.ok(syllabusService.getSyllabi(courseId));
  }

  @PostMapping("/courses/{courseId}/syllabi")
  @PreAuthorize(
      "hasAnyRole('LECTURER', 'PROGRAM_MANAGER') and @scopeAuthorizationService.canAccessCourse(authentication.principal, #courseId, T(com.hcmute.lms.lmsapi.enums.AccessType).WRITE)")
  public ResponseEntity<SyllabusResponse> createSyllabus(
      @PathVariable("courseId")
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "ID học phần phải là ObjectId 24 ký tự hex")
          String courseId,
      @Valid @RequestBody SyllabusRequest request) {
    log.info(
        "API request: POST /api/v1/courses/{}/syllabi version [{}]",
        courseId,
        request.getVersion());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(syllabusService.createSyllabus(courseId, request));
  }

  @GetMapping("/syllabi/{id}")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<SyllabusResponse> getSyllabus(
      @PathVariable("id")
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "ID đề cương phải là ObjectId 24 ký tự hex")
          String id) {
    return ResponseEntity.ok(syllabusService.getSyllabus(id));
  }

  @PutMapping("/syllabi/{id}/status")
  @PreAuthorize(
      "hasRole('PROGRAM_MANAGER') and @scopeAuthorizationService.canAccessSyllabus(authentication.principal, #id, T(com.hcmute.lms.lmsapi.enums.AccessType).WRITE)")
  public ResponseEntity<SyllabusResponse> updateStatus(
      @PathVariable("id")
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "ID đề cương phải là ObjectId 24 ký tự hex")
          String id,
      @Valid @RequestBody SyllabusStatusUpdateRequest request) {
    log.info("API request: PUT /api/v1/syllabi/{}/status to [{}]", id, request.getStatus());
    return ResponseEntity.ok(syllabusService.updateStatus(id, request.getStatus()));
  }
}
