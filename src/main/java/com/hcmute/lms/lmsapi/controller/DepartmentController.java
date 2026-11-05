package com.hcmute.lms.lmsapi.controller;

import com.hcmute.lms.lmsapi.dto.AssignCourseRequest;
import com.hcmute.lms.lmsapi.dto.UserResponse;
import com.hcmute.lms.lmsapi.service.DepartmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/departments")
@RequiredArgsConstructor
public class DepartmentController {

  private final DepartmentService departmentService;

  @PostMapping("/assign-course")
  @PreAuthorize(
      "@scopeAuthorizationService.canAssignCourse(authentication.principal, #request.courseId)")
  public ResponseEntity<UserResponse> assignCourse(
      @Valid @RequestBody AssignCourseRequest request) {
    log.info(
        "API request: POST /api/v1/departments/assign-course courseId=[{}] to lecturerId=[{}]",
        request.getCourseId(),
        request.getLecturerId());
    return ResponseEntity.ok(departmentService.assignCourseToLecturer(request));
  }

  @PostMapping("/unassign-course")
  @PreAuthorize(
      "@scopeAuthorizationService.canAssignCourse(authentication.principal, #request.courseId)")
  public ResponseEntity<UserResponse> unassignCourse(
      @Valid @RequestBody AssignCourseRequest request) {
    log.info(
        "API request: POST /api/v1/departments/unassign-course courseId=[{}] from lecturerId=[{}]",
        request.getCourseId(),
        request.getLecturerId());
    return ResponseEntity.ok(departmentService.unassignCourseFromLecturer(request));
  }
}
