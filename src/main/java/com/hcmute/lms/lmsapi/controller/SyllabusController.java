package com.hcmute.lms.lmsapi.controller;

import com.hcmute.lms.lmsapi.dto.AssessmentScoreTemplate;
import com.hcmute.lms.lmsapi.dto.SyllabusCsvImportResponse;
import com.hcmute.lms.lmsapi.dto.SyllabusResponse;
import com.hcmute.lms.lmsapi.dto.SyllabusStatusUpdateRequest;
import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import com.hcmute.lms.lmsapi.service.SyllabusImportService;
import com.hcmute.lms.lmsapi.service.SyllabusService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SyllabusController {

  private final SyllabusService syllabusService;
  private final SyllabusImportService syllabusImportService;

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

  @GetMapping(
      value = "/courses/{courseId}/syllabi/import-template",
      produces = "text/csv; charset=UTF-8")
  @PreAuthorize("hasAnyRole('PROGRAM_MANAGER', 'ADMIN')")
  public ResponseEntity<byte[]> getImportTemplateCsv(
      @PathVariable("courseId")
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "ID học phần phải là ObjectId 24 ký tự hex")
          String courseId) {
    log.info("API request: GET /api/v1/courses/{}/syllabi/import-template", courseId);
    AssessmentScoreTemplate template = syllabusImportService.generateTemplateCsv();
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + template.fileName() + "\"")
        .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
        .body(template.content());
  }

  @PostMapping(
      value = "/courses/{courseId}/syllabi/import",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize(
      "hasRole('ADMIN') or (hasRole('PROGRAM_MANAGER') and @scopeAuthorizationService.canAccessCourse(authentication.principal, #courseId, T(com.hcmute.lms.lmsapi.enums.AccessType).WRITE))")
  public ResponseEntity<SyllabusCsvImportResponse> importSyllabusCsv(
      @PathVariable("courseId")
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "ID học phần phải là ObjectId 24 ký tự hex")
          String courseId,
      @RequestParam("version") @NotBlank(message = "Phiên bản đề cương không được để trống")
          String version,
      @RequestParam("programVersionId")
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "ID phiên bản CTĐT phải là ObjectId 24 ký tự hex")
          String programVersionId,
      @RequestParam(value = "effectiveFromTermId", required = false) String effectiveFromTermId,
      @RequestPart("file") MultipartFile file,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.info("API request: POST /api/v1/courses/{}/syllabi/import version [{}]", courseId, version);
    String actorId = principal != null ? principal.getId() : null;
    return ResponseEntity.ok(
        syllabusImportService.importSyllabusCsv(
            courseId, version, programVersionId, effectiveFromTermId, file, actorId));
  }

  @PostMapping("/syllabi/{id}/confirm-import")
  @PreAuthorize(
      "hasRole('ADMIN') or (hasRole('PROGRAM_MANAGER') and @scopeAuthorizationService.canAccessSyllabus(authentication.principal, #id, T(com.hcmute.lms.lmsapi.enums.AccessType).WRITE))")
  public ResponseEntity<SyllabusCsvImportResponse> confirmImport(
      @PathVariable("id")
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "ID đề cương phải là ObjectId 24 ký tự hex")
          String id,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.info("API request: POST /api/v1/syllabi/{}/confirm-import", id);
    String actorId = principal != null ? principal.getId() : null;
    return ResponseEntity.ok(syllabusImportService.confirmImport(id, actorId));
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
