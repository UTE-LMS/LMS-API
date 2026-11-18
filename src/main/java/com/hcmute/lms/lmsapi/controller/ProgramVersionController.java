package com.hcmute.lms.lmsapi.controller;

import com.hcmute.lms.lmsapi.dto.AssessmentScoreTemplate;
import com.hcmute.lms.lmsapi.dto.PageResponse;
import com.hcmute.lms.lmsapi.dto.ProgramCsvImportResponse;
import com.hcmute.lms.lmsapi.dto.ProgramVersionResponse;
import com.hcmute.lms.lmsapi.dto.ProgramVersionStatusUpdateRequest;
import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import com.hcmute.lms.lmsapi.enums.ProgramVersionStatus;
import com.hcmute.lms.lmsapi.service.ProgramImportService;
import com.hcmute.lms.lmsapi.service.ProgramVersionService;
import com.hcmute.lms.lmsapi.util.ImportTemplateResponses;
import com.hcmute.lms.lmsapi.util.PageableValidationUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
      @RequestParam(required = false) String programId,
      @RequestParam(required = false) String facultyId,
      @RequestParam(required = false) String cohort,
      @RequestParam(required = false) ProgramVersionStatus status,
      @RequestParam(required = false) String search) {
    PageableValidationUtils.validate(page, size);
    return ResponseEntity.ok(
        programVersionService.getProgramVersions(
            page != null ? page : PageableValidationUtils.DEFAULT_PAGE,
            size != null ? size : PageableValidationUtils.DEFAULT_SIZE,
            programId,
            facultyId,
            cohort,
            status,
            search));
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

  @GetMapping(
      value = "/import-template",
      produces = {ImportTemplateResponses.CSV_MEDIA_TYPE, ImportTemplateResponses.XLSX_MEDIA_TYPE})
  @PreAuthorize("hasAnyRole('PROGRAM_MANAGER', 'ADMIN')")
  public ResponseEntity<byte[]> getImportTemplateCsv(
      @RequestParam(name = "format", defaultValue = "csv") String format) {
    log.info("API request: GET /api/v1/program-versions/import-template");
    AssessmentScoreTemplate template = programImportService.generateTemplateCsv();
    return ImportTemplateResponses.of(template, format);
  }

  @PostMapping(
      value = {"/import", "/import/preview"},
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
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
      @RequestParam(value = "totalCredits", required = false)
          @Min(value = 0, message = "Tổng số tín chỉ không được âm")
          Integer totalCredits,
      @RequestParam(value = "durationYears", required = false)
          @DecimalMin(value = "0.0", message = "Thời gian đào tạo không được âm")
          Double durationYears,
      @RequestParam(value = "applicableCohort", required = false)
          @Size(max = 50, message = "Khóa áp dụng tối đa 50 ký tự")
          String applicableCohort,
      @RequestPart("file") MultipartFile file,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.info(
        "API request: POST /api/v1/program-versions/import for program [{}] version [{}]",
        programId,
        version);
    String actorId = principal != null ? principal.getId() : null;
    return ResponseEntity.ok(
        programImportService.importProgramCsv(
            programId, version, totalCredits, durationYears, applicableCohort, file, actorId));
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

  @PutMapping("/{id}/lock")
  @PreAuthorize(
      "hasRole('ADMIN') or (hasRole('PROGRAM_MANAGER') and @scopeAuthorizationService.canAccessProgramVersion(authentication.principal, #id, T(com.hcmute.lms.lmsapi.enums.AccessType).WRITE))")
  public ResponseEntity<ProgramVersionResponse> lockProgramVersion(
      @PathVariable("id")
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "ID phiên bản phải là ObjectId 24 ký tự hex")
          String id) {
    log.info("API request: PUT /api/v1/program-versions/{}/lock", id);
    return ResponseEntity.ok(
        programVersionService.updateProgramVersionStatus(
            id,
            ProgramVersionStatusUpdateRequest.builder()
                .status(com.hcmute.lms.lmsapi.enums.ProgramVersionStatus.ARCHIVED)
                .build()));
  }

  @PutMapping("/{id}/status")
  @PreAuthorize(
      "hasRole('ADMIN') or (hasRole('PROGRAM_MANAGER') and @scopeAuthorizationService.canAccessProgramVersion(authentication.principal, #id, T(com.hcmute.lms.lmsapi.enums.AccessType).WRITE))")
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
