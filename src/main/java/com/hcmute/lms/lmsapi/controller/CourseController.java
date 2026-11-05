package com.hcmute.lms.lmsapi.controller;

import com.hcmute.lms.lmsapi.dto.CourseRequest;
import com.hcmute.lms.lmsapi.dto.CourseResponse;
import com.hcmute.lms.lmsapi.dto.CourseUpdateRequest;
import com.hcmute.lms.lmsapi.dto.PageResponse;
import com.hcmute.lms.lmsapi.service.CourseService;
import com.hcmute.lms.lmsapi.util.PageableValidationUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/courses")
@RequiredArgsConstructor
public class CourseController {

  private final CourseService courseService;

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<PageResponse<CourseResponse>> getCourses(
      @RequestParam(defaultValue = "0", required = false)
          @Min(value = 0, message = "Chỉ số trang phải lớn hơn hoặc bằng 0")
          Integer page,
      @RequestParam(defaultValue = "20", required = false)
          @Min(value = 1, message = "Kích thước trang phải lớn hơn hoặc bằng 1")
          @Max(value = 100, message = "Kích thước trang tối đa 100")
          Integer size,
      @RequestParam(required = false) String search) {
    PageableValidationUtils.validate(page, size);
    return ResponseEntity.ok(
        courseService.getCourses(
            page != null ? page : PageableValidationUtils.DEFAULT_PAGE,
            size != null ? size : PageableValidationUtils.DEFAULT_SIZE,
            search));
  }

  @GetMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<CourseResponse> getCourseById(
      @PathVariable("id")
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "ID học phần phải là ObjectId 24 ký tự hex")
          String id) {
    return ResponseEntity.ok(courseService.getCourseById(id));
  }

  @PostMapping
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<CourseResponse> createCourse(@Valid @RequestBody CourseRequest request) {
    log.info("API request: POST /api/v1/courses with code [{}]", request.getCode());
    return ResponseEntity.status(HttpStatus.CREATED).body(courseService.createCourse(request));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<CourseResponse> updateCourse(
      @PathVariable("id")
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "ID học phần phải là ObjectId 24 ký tự hex")
          String id,
      @Valid @RequestBody CourseUpdateRequest request) {
    log.info("API request: PUT /api/v1/courses/{}", id);
    return ResponseEntity.ok(courseService.updateCourse(id, request));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Void> deleteCourse(
      @PathVariable("id")
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "ID học phần phải là ObjectId 24 ký tự hex")
          String id) {
    log.info("API request: DELETE /api/v1/courses/{}", id);
    courseService.deleteCourse(id);
    return ResponseEntity.noContent().build();
  }
}
