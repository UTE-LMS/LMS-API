package com.hcmute.lms.lmsapi.controller;

import com.hcmute.lms.lmsapi.dto.AssessmentScoreTemplate;
import com.hcmute.lms.lmsapi.dto.CsvImportResultResponse;
import com.hcmute.lms.lmsapi.dto.FacultyAssignPmRequest;
import com.hcmute.lms.lmsapi.dto.FacultyRequest;
import com.hcmute.lms.lmsapi.dto.FacultyResponse;
import com.hcmute.lms.lmsapi.dto.FacultyStatusUpdateRequest;
import com.hcmute.lms.lmsapi.dto.FacultyTransferPmRequest;
import com.hcmute.lms.lmsapi.dto.FacultyUpdateRequest;
import com.hcmute.lms.lmsapi.dto.PageResponse;
import com.hcmute.lms.lmsapi.dto.UserResponse;
import com.hcmute.lms.lmsapi.service.FacultyService;
import com.hcmute.lms.lmsapi.util.ImportTemplateResponses;
import com.hcmute.lms.lmsapi.util.PageableValidationUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/faculties")
@RequiredArgsConstructor
public class FacultyController {

  private final FacultyService facultyService;

  @PostMapping
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<FacultyResponse> createFaculty(@Valid @RequestBody FacultyRequest request) {
    log.info("API request: POST /api/v1/faculties with code [{}]", request.getCode());
    return ResponseEntity.status(HttpStatus.CREATED).body(facultyService.createFaculty(request));
  }

  @GetMapping
  public ResponseEntity<PageResponse<FacultyResponse>> getFaculties(
      @RequestParam(defaultValue = "0", required = false)
          @Min(value = 0, message = "Chỉ số trang phải lớn hơn hoặc bằng 0")
          Integer page,
      @RequestParam(defaultValue = "20", required = false)
          @Min(value = 1, message = "Kích thước trang phải lớn hơn hoặc bằng 1")
          @Max(value = 100, message = "Kích thước trang tối đa 100")
          Integer size,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) Boolean isActive) {
    PageableValidationUtils.validate(page, size);
    return ResponseEntity.ok(
        facultyService.getFaculties(
            page != null ? page : PageableValidationUtils.DEFAULT_PAGE,
            size != null ? size : PageableValidationUtils.DEFAULT_SIZE,
            search,
            isActive));
  }

  @GetMapping("/{id}")
  public ResponseEntity<FacultyResponse> getFacultyById(
      @PathVariable("id")
          @Pattern(regexp = "^[0-9a-fA-F]{24}$", message = "ID Khoa phải là ObjectId 24 ký tự hex")
          String id) {
    return ResponseEntity.ok(facultyService.getFacultyById(id));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<FacultyResponse> updateFaculty(
      @PathVariable("id")
          @Pattern(regexp = "^[0-9a-fA-F]{24}$", message = "ID Khoa phải là ObjectId 24 ký tự hex")
          String id,
      @Valid @RequestBody FacultyUpdateRequest request) {
    log.info("API request: PUT /api/v1/faculties/{}", id);
    return ResponseEntity.ok(facultyService.updateFaculty(id, request));
  }

  @PutMapping("/{id}/status")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<FacultyResponse> updateFacultyStatus(
      @PathVariable("id")
          @Pattern(regexp = "^[0-9a-fA-F]{24}$", message = "ID Khoa phải là ObjectId 24 ký tự hex")
          String id,
      @Valid @RequestBody FacultyStatusUpdateRequest request) {
    log.info("API request: PUT /api/v1/faculties/{}/status", id);
    return ResponseEntity.ok(facultyService.updateFacultyStatus(id, request));
  }

  @GetMapping("/{facultyId}/program-managers")
  @PreAuthorize(
      "hasRole('ADMIN') or (hasRole('PROGRAM_MANAGER') and authentication.principal.facultyId == #facultyId)")
  public ResponseEntity<List<UserResponse>> getProgramManagers(
      @PathVariable("facultyId")
          @Pattern(regexp = "^[0-9a-fA-F]{24}$", message = "ID Khoa phải là ObjectId 24 ký tự hex")
          String facultyId) {
    return ResponseEntity.ok(facultyService.getProgramManagers(facultyId));
  }

  @PostMapping("/{facultyId}/program-managers")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<UserResponse> assignProgramManager(
      @PathVariable("facultyId")
          @Pattern(regexp = "^[0-9a-fA-F]{24}$", message = "ID Khoa phải là ObjectId 24 ký tự hex")
          String facultyId,
      @Valid @RequestBody FacultyAssignPmRequest request) {
    log.info("API request: POST /api/v1/faculties/{}/program-managers", facultyId);
    return ResponseEntity.ok(facultyService.assignProgramManager(facultyId, request));
  }

  @DeleteMapping("/{facultyId}/program-managers/{userId}")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Void> unassignProgramManager(
      @PathVariable("facultyId")
          @Pattern(regexp = "^[0-9a-fA-F]{24}$", message = "ID Khoa phải là ObjectId 24 ký tự hex")
          String facultyId,
      @PathVariable("userId")
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "ID người dùng phải là ObjectId 24 ký tự hex")
          String userId) {
    log.info("API request: DELETE /api/v1/faculties/{}/program-managers/{}", facultyId, userId);
    facultyService.unassignProgramManager(facultyId, userId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/transfer-program-manager")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<UserResponse> transferProgramManager(
      @Valid @RequestBody FacultyTransferPmRequest request) {
    log.info("API request: POST /api/v1/faculties/transfer-program-manager");
    return ResponseEntity.ok(facultyService.transferProgramManager(request));
  }

  @GetMapping(
      value = "/import-template",
      produces = {ImportTemplateResponses.CSV_MEDIA_TYPE, ImportTemplateResponses.XLSX_MEDIA_TYPE})
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<byte[]> getImportTemplateCsv(
      @RequestParam(name = "format", defaultValue = "csv") String format) {
    log.info("API request: GET /api/v1/faculties/import-template");
    AssessmentScoreTemplate template = facultyService.generateTemplateCsv();
    return ImportTemplateResponses.of(template, format);
  }

  @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<CsvImportResultResponse> importFaculties(
      @RequestPart("file") MultipartFile file) {
    log.info(
        "API request: POST /api/v1/faculties/import with file [{}]",
        file != null ? file.getOriginalFilename() : "null");
    return ResponseEntity.ok(facultyService.importFaculties(file));
  }
}
