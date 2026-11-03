package com.hcmute.lms.lmsapi.controller;

import com.hcmute.lms.lmsapi.dto.ProgramCourseRequest;
import com.hcmute.lms.lmsapi.dto.ProgramCourseResponse;
import com.hcmute.lms.lmsapi.dto.ProgramCourseUpdateRequest;
import com.hcmute.lms.lmsapi.service.ProgramCourseService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/program-versions/{programVersionId}/courses")
@RequiredArgsConstructor
public class ProgramCourseController {

  private final ProgramCourseService programCourseService;

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<List<ProgramCourseResponse>> getCourses(
      @PathVariable("programVersionId")
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "ID phiên bản phải là ObjectId 24 ký tự hex")
          String programVersionId) {
    return ResponseEntity.ok(programCourseService.getCoursesByProgramVersion(programVersionId));
  }

  @GetMapping("/{courseId}")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<ProgramCourseResponse> getCourse(
      @PathVariable("programVersionId")
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "ID phiên bản phải là ObjectId 24 ký tự hex")
          String programVersionId,
      @PathVariable("courseId")
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "ID học phần phải là ObjectId 24 ký tự hex")
          String courseId) {
    return ResponseEntity.ok(programCourseService.getProgramCourse(programVersionId, courseId));
  }

  @PostMapping
  @PreAuthorize(
      "hasRole('ADMIN') or (hasRole('PROGRAM_MANAGER') and @scopeAuthorizationService.canAccessProgramVersion(authentication.principal, #programVersionId, T(com.hcmute.lms.lmsapi.enums.AccessType).WRITE))")
  public ResponseEntity<ProgramCourseResponse> addCourseToProgramVersion(
      @PathVariable("programVersionId")
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "ID phiên bản phải là ObjectId 24 ký tự hex")
          String programVersionId,
      @Valid @RequestBody ProgramCourseRequest request) {
    log.info(
        "API request: POST /api/v1/program-versions/{}/courses with courseId [{}]",
        programVersionId,
        request.getCourseId());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(programCourseService.addCourseToProgramVersion(programVersionId, request));
  }

  @PutMapping("/{courseId}")
  @PreAuthorize(
      "hasRole('ADMIN') or (hasRole('PROGRAM_MANAGER') and @scopeAuthorizationService.canAccessProgramVersion(authentication.principal, #programVersionId, T(com.hcmute.lms.lmsapi.enums.AccessType).WRITE))")
  public ResponseEntity<ProgramCourseResponse> updateProgramCourse(
      @PathVariable("programVersionId")
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "ID phiên bản phải là ObjectId 24 ký tự hex")
          String programVersionId,
      @PathVariable("courseId")
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "ID học phần phải là ObjectId 24 ký tự hex")
          String courseId,
      @Valid @RequestBody ProgramCourseUpdateRequest request) {
    log.info("API request: PUT /api/v1/program-versions/{}/courses/{}", programVersionId, courseId);
    return ResponseEntity.ok(
        programCourseService.updateProgramCourse(programVersionId, courseId, request));
  }

  @DeleteMapping("/{courseId}")
  @PreAuthorize(
      "hasRole('ADMIN') or (hasRole('PROGRAM_MANAGER') and @scopeAuthorizationService.canAccessProgramVersion(authentication.principal, #programVersionId, T(com.hcmute.lms.lmsapi.enums.AccessType).WRITE))")
  public ResponseEntity<Void> removeCourseFromProgramVersion(
      @PathVariable("programVersionId")
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "ID phiên bản phải là ObjectId 24 ký tự hex")
          String programVersionId,
      @PathVariable("courseId")
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "ID học phần phải là ObjectId 24 ký tự hex")
          String courseId) {
    log.info(
        "API request: DELETE /api/v1/program-versions/{}/courses/{}", programVersionId, courseId);
    programCourseService.removeCourseFromProgramVersion(programVersionId, courseId);
    return ResponseEntity.noContent().build();
  }
}
