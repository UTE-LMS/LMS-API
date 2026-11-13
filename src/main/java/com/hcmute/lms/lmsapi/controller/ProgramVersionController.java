package com.hcmute.lms.lmsapi.controller;

import com.hcmute.lms.lmsapi.dto.AssessmentScoreTemplate;
import com.hcmute.lms.lmsapi.dto.PageResponse;
import com.hcmute.lms.lmsapi.dto.ProgramCsvImportResponse;
import com.hcmute.lms.lmsapi.dto.ProgramVersionResponse;
import com.hcmute.lms.lmsapi.dto.ProgramVersionStatusUpdateRequest;
import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import com.hcmute.lms.lmsapi.service.ProgramImportService;
import com.hcmute.lms.lmsapi.service.ProgramVersionService;
import com.hcmute.lms.lmsapi.util.PageableValidationUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
@RequestMapping("/api/v1/program-versions")
@RequiredArgsConstructor
public class ProgramVersionController {

  private final ProgramVersionService programVersionService;
  private final ProgramImportService programImportService;

  @GetMapping
  public ResponseEntity<PageResponse<ProgramVersionResponse>> getProgramVersions(
      @RequestParam(defaultValue = "0", required = false)
          @Min(value = 0, message = "Chỉ số trang phải lớn hơn hoặc bằng 0")
          Integer page,
      @RequestParam(defaultValue = "20", required = false)
          @Min(value = 1, message = "Kích thước trang phải lớn hơn hoặc bằng 1")
          @Max(value = 100, message = "Kích thước trang tối đa 100")
          Integer size,
      @RequestParam(required = false) String programId) {
    PageableValidationUtils.validate(page, size);
    return ResponseEntity.ok(
        programVersionService.getProgramVersions(
            page != null ? page : PageableValidationUtils.DEFAULT_PAGE,
            size != null ? size : PageableValidationUtils.DEFAULT_SIZE,
            programId));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ProgramVersionResponse> getProgramVersionById(
      @PathVariable("id")
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "ID phiên bản phải là ObjectId 24 ký tự hex")
          String id) {
    return ResponseEntity.ok(programVersionService.getProgramVersionById(id));
  }

  @GetMapping(value = "/import-template", produces = "text/csv; charset=UTF-8")
  @PreAuthorize("hasAnyRole('PROGRAM_MANAGER', 'ADMIN')")
  public ResponseEntity<byte[]> getImportTemplateCsv() {
    log.info("API request: GET /api/v1/program-versions/import-template");
    AssessmentScoreTemplate template = programImportService.generateTemplateCsv();
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + template.fileName() + "\"")
        .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
        .body(template.content());
  }

  @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize(
      "hasRole('ADMIN') or (hasRole('PROGRAM_MANAGER') and @scopeAuthorizationService.canAccessProgram(authentication.principal, #programId, T(com.hcmute.lms.lmsapi.enums.AccessType).WRITE))")
  public ResponseEntity<ProgramCsvImportResponse> importProgramCsv(
      @RequestParam("programId")
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "ID ngành đào tạo phải là ObjectId 24 ký tự hex")
          String programId,
      @RequestParam("version") @NotBlank(message = "Phiên bản CTĐT không được để trống")
          String version,
      @RequestPart("file") MultipartFile file,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.info(
        "API request: POST /api/v1/program-versions/import for program [{}] version [{}]",
        programId,
        version);
    String actorId = principal != null ? principal.getId() : null;
    return ResponseEntity.ok(
        programImportService.importProgramCsv(programId, version, file, actorId));
  }

  @PostMapping("/{id}/confirm-import")
  @PreAuthorize(
      "hasRole('ADMIN') or (hasRole('PROGRAM_MANAGER') and @scopeAuthorizationService.canAccessProgramVersion(authentication.principal, #id, T(com.hcmute.lms.lmsapi.enums.AccessType).WRITE))")
  public ResponseEntity<ProgramCsvImportResponse> confirmImport(
      @PathVariable("id")
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "ID phiên bản phải là ObjectId 24 ký tự hex")
          String id,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.info("API request: POST /api/v1/program-versions/{}/confirm-import", id);
    String actorId = principal != null ? principal.getId() : null;
    return ResponseEntity.ok(programImportService.confirmImport(id, actorId));
  }

  @PutMapping("/{id}/status")
  @PreAuthorize(
      "hasRole('PROGRAM_MANAGER') and @scopeAuthorizationService.canAccessProgramVersion(authentication.principal, #id, T(com.hcmute.lms.lmsapi.enums.AccessType).WRITE)")
  public ResponseEntity<ProgramVersionResponse> updateProgramVersionStatus(
      @PathVariable("id")
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "ID phiên bản phải là ObjectId 24 ký tự hex")
          String id,
      @Valid @RequestBody ProgramVersionStatusUpdateRequest request) {
    log.info(
        "API request: PUT /api/v1/program-versions/{}/status to [{}]", id, request.getStatus());
    return ResponseEntity.ok(programVersionService.updateProgramVersionStatus(id, request));
  }
}
