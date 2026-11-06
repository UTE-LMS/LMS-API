package com.hcmute.lms.lmsapi.controller;

import com.hcmute.lms.lmsapi.dto.AssignCourseRequest;
import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import com.hcmute.lms.lmsapi.dto.UserResponse;
import com.hcmute.lms.lmsapi.service.DepartmentService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/departments")
@RequiredArgsConstructor
public class DepartmentController {

  private final DepartmentService departmentService;

  @GetMapping("/lecturers")
  @PreAuthorize(
      "#courseId == null || #courseId.isBlank()"
          + " ? hasAnyRole('ADMIN', 'DEPARTMENT_HEAD')"
          + " : @scopeAuthorizationService.canAssignCourse(authentication.principal, #courseId)")
  public ResponseEntity<List<UserResponse>> getDepartmentLecturers(
      @RequestParam(required = false) String courseId,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.debug(
        "API request: GET /api/v1/departments/lecturers courseId=[{}] by user=[{}]",
        courseId,
        principal != null ? principal.getUsername() : null);
    return ResponseEntity.ok(departmentService.getDepartmentLecturers(principal, courseId));
  }

  @PostMapping("/assign-course")
  @PreAuthorize(
      "@scopeAuthorizationService.canAssignCourse(authentication.principal, #request.courseId)")
  public ResponseEntity<UserResponse> assignCourse(
      @Valid @RequestBody AssignCourseRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.info(
        "API request: POST /api/v1/departments/assign-course courseId=[{}] to lecturerId=[{}]",
        request.getCourseId(),
        request.getLecturerId());
    return ResponseEntity.ok(departmentService.assignCourseToLecturer(request, principal));
  }

  @PostMapping("/unassign-course")
  @PreAuthorize(
      "@scopeAuthorizationService.canAssignCourse(authentication.principal, #request.courseId)")
  public ResponseEntity<UserResponse> unassignCourse(
      @Valid @RequestBody AssignCourseRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.info(
        "API request: POST /api/v1/departments/unassign-course courseId=[{}] from lecturerId=[{}]",
        request.getCourseId(),
        request.getLecturerId());
    return ResponseEntity.ok(departmentService.unassignCourseFromLecturer(request, principal));
  }
}
