package com.hcmute.lms.lmsapi.controller;

import com.hcmute.lms.lmsapi.dto.AssessmentScoreTemplate;
import com.hcmute.lms.lmsapi.dto.CourseImportResultResponse;
import com.hcmute.lms.lmsapi.dto.CourseRequest;
import com.hcmute.lms.lmsapi.dto.CourseResponse;
import com.hcmute.lms.lmsapi.dto.CourseUpdateRequest;
import com.hcmute.lms.lmsapi.dto.PageResponse;
import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import com.hcmute.lms.lmsapi.enums.Role;
import com.hcmute.lms.lmsapi.service.CourseService;
import com.hcmute.lms.lmsapi.util.ImportTemplateResponses;
import com.hcmute.lms.lmsapi.util.PageableValidationUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/courses")
@RequiredArgsConstructor
public class CourseController {

  private final CourseService courseService;

  @GetMapping
  public ResponseEntity<PageResponse<CourseResponse>> getCourses(
      @RequestParam(defaultValue = "0", required = false)
          @Min(value = 0, message = "Chỉ số trang phải lớn hơn hoặc bằng 0")
          Integer page,
      @RequestParam(defaultValue = "20", required = false)
          @Min(value = 1, message = "Kích thước trang phải lớn hơn hoặc bằng 1")
          @Max(value = 100, message = "Kích thước trang tối đa 100")
          Integer size,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) String facultyId,
      @RequestParam(required = false) Boolean isActive) {
    PageableValidationUtils.validate(page, size);
    return ResponseEntity.ok(
        courseService.getCourses(
            page != null ? page : PageableValidationUtils.DEFAULT_PAGE,
            size != null ? size : PageableValidationUtils.DEFAULT_SIZE,
            search,
            facultyId,
            isActive));
  }

  @GetMapping("/{id}")
  public ResponseEntity<CourseResponse> getCourseById(
      @PathVariable("id")
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "ID học phần phải là ObjectId 24 ký tự hex")
          String id) {
    return ResponseEntity.ok(courseService.getCourseById(id));
  }

  @PostMapping
  @PreAuthorize(
      "hasRole('ADMIN') or (hasRole('PROGRAM_MANAGER') and (#request.facultyId == null or authentication.principal.facultyId == #request.facultyId))")
  public ResponseEntity<CourseResponse> createCourse(
      @Valid @RequestBody CourseRequest request, @AuthenticationPrincipal UserPrincipal principal) {
    if (principal != null && !hasRole(principal, Role.ADMIN)) {
      if (principal.getFacultyId() == null) {
        throw new AccessDeniedException("Program Manager chưa được gán vào Khoa nào");
      }
      if (request.getFacultyId() == null) {
        request.setFacultyId(principal.getFacultyId());
      }
    }
    log.info("API request: POST /api/v1/courses with code [{}]", request.getCode());
    return ResponseEntity.status(HttpStatus.CREATED).body(courseService.createCourse(request));
  }

  @GetMapping(
      value = "/import-template",
      produces = {ImportTemplateResponses.CSV_MEDIA_TYPE, ImportTemplateResponses.XLSX_MEDIA_TYPE})
  @PreAuthorize("hasAnyRole('ADMIN', 'PROGRAM_MANAGER')")
  public ResponseEntity<byte[]> getImportTemplateCsv(
      @RequestParam(name = "format", defaultValue = "csv") String format) {
    log.info("API request: GET /api/v1/courses/import-template");
    AssessmentScoreTemplate template = courseService.generateTemplateCsv();
    return ImportTemplateResponses.of(template, format);
  }

  @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize(
      "hasRole('ADMIN') or (hasRole('PROGRAM_MANAGER') and (#facultyId == null or authentication.principal.facultyId == #facultyId))")
  public ResponseEntity<CourseImportResultResponse> importCourses(
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "facultyId", required = false) String facultyId,
      @AuthenticationPrincipal UserPrincipal principal) {
    boolean isAdmin = principal != null && hasRole(principal, Role.ADMIN);
    String effectiveFacultyId = facultyId;
    if (principal != null && !isAdmin) {
      if (principal.getFacultyId() == null) {
        throw new AccessDeniedException("Program Manager chưa được gán vào Khoa nào");
      }
      if (effectiveFacultyId == null) {
        effectiveFacultyId = principal.getFacultyId();
      }
    }
    log.info(
        "API request: POST /api/v1/courses/import with file [{}]",
        file != null ? file.getOriginalFilename() : "null");
    return ResponseEntity.ok(courseService.importCourses(file, effectiveFacultyId, isAdmin));
  }

  @PutMapping("/{id}")
  @PreAuthorize(
      "hasRole('ADMIN') or (hasRole('PROGRAM_MANAGER') and @scopeAuthorizationService.canManageCourse(authentication.principal, #id))")
  public ResponseEntity<CourseResponse> updateCourse(
      @PathVariable("id")
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "ID học phần phải là ObjectId 24 ký tự hex")
          String id,
      @Valid @RequestBody CourseUpdateRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.info("API request: PUT /api/v1/courses/{}", id);
    if (principal != null && !hasRole(principal, Role.ADMIN)) {
      CourseResponse existing = courseService.getCourseById(id);
      if (request.getFacultyId() != null
          && !request.getFacultyId().equals(existing.getFacultyId())) {
        throw new AccessDeniedException(
            "Program Manager không có quyền chuyển Khoa chủ quản của học phần");
      }
    }
    return ResponseEntity.ok(courseService.updateCourse(id, request));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize(
      "hasRole('ADMIN') or (hasRole('PROGRAM_MANAGER') and @scopeAuthorizationService.canManageCourse(authentication.principal, #id))")
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

  private boolean hasRole(UserPrincipal principal, Role role) {
    return principal.getRoles() != null && principal.getRoles().contains(role);
  }
}
