package com.hcmute.lms.lmsapi.controller;

import com.hcmute.lms.lmsapi.dto.AssessmentScoreTemplate;
import com.hcmute.lms.lmsapi.dto.PageResponse;
import com.hcmute.lms.lmsapi.dto.ProgramCourseCsvImportResponse;
import com.hcmute.lms.lmsapi.dto.ProgramCourseResponse;
import com.hcmute.lms.lmsapi.enums.CourseCategory;
import com.hcmute.lms.lmsapi.enums.CourseType;
import com.hcmute.lms.lmsapi.service.ProgramCourseService;
import com.hcmute.lms.lmsapi.util.ImportTemplateResponses;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/program-versions/{programVersionId}/courses")
@RequiredArgsConstructor
public class ProgramCourseController {

  private final ProgramCourseService programCourseService;

  @GetMapping
  public ResponseEntity<PageResponse<ProgramCourseResponse>> getCourses(
      @PathVariable("programVersionId")
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "ID phiên bản phải là ObjectId 24 ký tự hex")
          String programVersionId,
      @RequestParam(required = false) Integer semester,
      @RequestParam(required = false) CourseType type,
      @RequestParam(required = false) CourseCategory category,
      @RequestParam(required = false) String search,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok(
        programCourseService.getCoursesByProgramVersion(
            programVersionId, semester, type, category, search, page, size));
  }

  @GetMapping("/{courseId}")
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

  @GetMapping(
      value = "/import-template",
      produces = {ImportTemplateResponses.CSV_MEDIA_TYPE, ImportTemplateResponses.XLSX_MEDIA_TYPE})
  @PreAuthorize("hasAnyRole('PROGRAM_MANAGER', 'ADMIN')")
  public ResponseEntity<byte[]> getImportTemplateCsv(
      @RequestParam(name = "format", defaultValue = "csv") String format) {
    log.info("API request: GET /api/v1/program-versions/{id}/courses/import-template");
    AssessmentScoreTemplate template = programCourseService.generateTemplateCsv();
    return ImportTemplateResponses.of(template, format);
  }

  @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize(
      "hasRole('ADMIN') or (hasRole('PROGRAM_MANAGER') and @scopeAuthorizationService.canAccessProgramVersion(authentication.principal, #programVersionId, T(com.hcmute.lms.lmsapi.enums.AccessType).WRITE))")
  public ResponseEntity<ProgramCourseCsvImportResponse> importProgramCoursesCsv(
      @PathVariable("programVersionId")
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "ID phiên bản phải là ObjectId 24 ký tự hex")
          String programVersionId,
      @RequestPart("file") MultipartFile file) {
    log.info("API request: POST /api/v1/program-versions/{}/courses/import", programVersionId);
    return ResponseEntity.ok(programCourseService.importProgramCoursesCsv(programVersionId, file));
  }
}
