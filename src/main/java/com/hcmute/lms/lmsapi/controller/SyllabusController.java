package com.hcmute.lms.lmsapi.controller;

import com.hcmute.lms.lmsapi.dto.AssessmentScoreTemplate;
import com.hcmute.lms.lmsapi.dto.SyllabusCsvImportResponse;
import com.hcmute.lms.lmsapi.dto.SyllabusResponse;
import com.hcmute.lms.lmsapi.dto.SyllabusStatusUpdateRequest;
import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import com.hcmute.lms.lmsapi.service.SyllabusImportService;
import com.hcmute.lms.lmsapi.service.SyllabusService;
import com.hcmute.lms.lmsapi.util.ImportTemplateResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
  public ResponseEntity<List<SyllabusResponse>> getSyllabi(
      @PathVariable("courseId")
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "ID học phần phải là ObjectId 24 ký tự hex")
          String courseId,
      @RequestParam(required = false) com.hcmute.lms.lmsapi.enums.SyllabusStatus status,
      @RequestParam(required = false) String effectiveFromTermId,
      @RequestParam(required = false) String search) {
    return ResponseEntity.ok(
        syllabusService.getSyllabi(courseId, status, effectiveFromTermId, search));
  }

  @GetMapping("/program-versions/{programVersionId}/syllabi")
  public ResponseEntity<List<SyllabusResponse>> getSyllabiByProgramVersion(
      @PathVariable("programVersionId")
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "ID phiên bản CTĐT phải là ObjectId 24 ký tự hex")
          String programVersionId,
      @RequestParam(required = false) String courseId,
      @RequestParam(required = false) com.hcmute.lms.lmsapi.enums.SyllabusStatus status,
      @RequestParam(required = false) String effectiveFromTermId,
      @RequestParam(required = false) String search) {
    return ResponseEntity.ok(
        syllabusService.getSyllabiByProgramVersion(
            programVersionId, courseId, status, effectiveFromTermId, search));
  }

  @GetMapping(
      value = "/courses/{courseId}/syllabi/import-template",
      produces = {ImportTemplateResponses.CSV_MEDIA_TYPE, ImportTemplateResponses.XLSX_MEDIA_TYPE})
  @PreAuthorize("hasAnyRole('PROGRAM_MANAGER', 'ADMIN')")
  public ResponseEntity<byte[]> getImportTemplateCsv(
      @PathVariable("courseId")
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "ID học phần phải là ObjectId 24 ký tự hex")
          String courseId,
      @RequestParam(name = "format", defaultValue = "csv") String format) {
    log.info("API request: GET /api/v1/courses/{}/syllabi/import-template", courseId);
    AssessmentScoreTemplate template = syllabusImportService.generateTemplateCsv();
    return ImportTemplateResponses.of(template, format);
  }

  @PostMapping(
      value = "/courses/{courseId}/syllabi/import/preview",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize(
      "hasRole('ADMIN') or (hasRole('PROGRAM_MANAGER') and @scopeAuthorizationService.canManageCourseSyllabus(authentication.principal, #courseId))")
  public ResponseEntity<SyllabusCsvImportResponse> previewImportSyllabusCsv(
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
    return importSyllabusCsv(
        courseId, version, programVersionId, effectiveFromTermId, file, principal);
  }

  @PostMapping(
      value = "/courses/{courseId}/syllabi/import",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize(
      "hasRole('ADMIN') or (hasRole('PROGRAM_MANAGER') and @scopeAuthorizationService.canManageCourseSyllabus(authentication.principal, #courseId))")
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
      "hasRole('ADMIN') or (hasRole('PROGRAM_MANAGER') and @scopeAuthorizationService.canManageSyllabus(authentication.principal, #id))")
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
  public ResponseEntity<SyllabusResponse> getSyllabus(
      @PathVariable("id")
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "ID đề cương phải là ObjectId 24 ký tự hex")
          String id) {
    return ResponseEntity.ok(syllabusService.getSyllabus(id));
  }

  @PutMapping("/syllabi/{id}/lock")
  @PreAuthorize(
      "hasRole('ADMIN') or (hasRole('PROGRAM_MANAGER') and @scopeAuthorizationService.canManageSyllabus(authentication.principal, #id))")
  public ResponseEntity<SyllabusResponse> lockSyllabus(
      @PathVariable("id")
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "ID đề cương phải là ObjectId 24 ký tự hex")
          String id) {
    log.info("API request: PUT /api/v1/syllabi/{}/lock", id);
    return ResponseEntity.ok(
        syllabusService.updateStatus(id, com.hcmute.lms.lmsapi.enums.SyllabusStatus.ARCHIVED));
  }

  @PutMapping("/syllabi/{id}/status")
  @PreAuthorize(
      "hasRole('ADMIN') or (hasRole('PROGRAM_MANAGER') and @scopeAuthorizationService.canManageSyllabus(authentication.principal, #id))")
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
