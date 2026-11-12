package com.hcmute.lms.lmsapi.controller;

import com.hcmute.lms.lmsapi.dto.ImportBatchCreateRequest;
import com.hcmute.lms.lmsapi.dto.ImportBatchResponse;
import com.hcmute.lms.lmsapi.dto.ImportColumnMappingDto;
import com.hcmute.lms.lmsapi.dto.ImportRowErrorResponse;
import com.hcmute.lms.lmsapi.dto.PageResponse;
import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import com.hcmute.lms.lmsapi.enums.ImportSourceSystem;
import com.hcmute.lms.lmsapi.enums.ReimportPolicy;
import com.hcmute.lms.lmsapi.exception.AppException;
import com.hcmute.lms.lmsapi.exception.ErrorCode;
import com.hcmute.lms.lmsapi.service.ImportBatchService;
import com.hcmute.lms.lmsapi.util.PageableValidationUtils;
import jakarta.validation.constraints.Pattern;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** API nhập điểm từ tệp CSV: tải tệp, kiểm tra, xác nhận, hủy lô (F05b, US-21..24). */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ImportBatchController {

  private static final String ID_PATTERN = "^[0-9a-fA-F]{24}$";
  private static final String READ = "T(com.hcmute.lms.lmsapi.enums.AccessType).READ";
  private static final String WRITE = "T(com.hcmute.lms.lmsapi.enums.AccessType).WRITE";
  private static final String BATCHES_PATH =
      "/sections/{sectionId}/assessments/{assessmentId}/import-batches";

  private final ImportBatchService importBatchService;
  private final ObjectMapper objectMapper;

  @PostMapping(value = BATCHES_PATH, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize(
      "hasRole('LECTURER') and"
          + " @scopeAuthorizationService.canAccessSection(authentication.principal, #sectionId, "
          + WRITE
          + ")")
  public ResponseEntity<ImportBatchResponse> createBatch(
      @PathVariable("sectionId")
          @Pattern(regexp = ID_PATTERN, message = "ID lớp học phần phải là ObjectId 24 ký tự hex")
          String sectionId,
      @PathVariable("assessmentId")
          @Pattern(regexp = ID_PATTERN, message = "ID bài đánh giá phải là ObjectId 24 ký tự hex")
          String assessmentId,
      @RequestPart("file") MultipartFile file,
      @RequestParam(name = "reimportPolicy", required = false) ReimportPolicy reimportPolicy,
      @RequestParam(name = "sourceSystem", required = false) ImportSourceSystem sourceSystem,
      @RequestParam(name = "columnMapping", required = false) String columnMapping,
      @AuthenticationPrincipal UserPrincipal principal)
      throws IOException {
    log.info(
        "API request: POST /sections/{}/assessments/{}/import-batches size=[{}] by [{}]",
        sectionId,
        assessmentId,
        file.getSize(),
        principal.getUsername());
    ImportBatchCreateRequest request =
        ImportBatchCreateRequest.builder()
            .sourceSystem(sourceSystem)
            .reimportPolicy(reimportPolicy)
            .columnMapping(parseMapping(columnMapping))
            .build();
    ImportBatchResponse created =
        importBatchService.createBatch(
            sectionId,
            assessmentId,
            file.getOriginalFilename(),
            file.getBytes(),
            request,
            principal);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @GetMapping(BATCHES_PATH)
  @PreAuthorize(
      "@scopeAuthorizationService.canAccessSection(authentication.principal, #sectionId, "
          + READ
          + ")")
  public ResponseEntity<List<ImportBatchResponse>> listBatches(
      @PathVariable("sectionId")
          @Pattern(regexp = ID_PATTERN, message = "ID lớp học phần phải là ObjectId 24 ký tự hex")
          String sectionId,
      @PathVariable("assessmentId")
          @Pattern(regexp = ID_PATTERN, message = "ID bài đánh giá phải là ObjectId 24 ký tự hex")
          String assessmentId) {
    return ResponseEntity.ok(importBatchService.listBatches(sectionId, assessmentId));
  }

  @GetMapping("/import-batches/{id}")
  @PreAuthorize(
      "@scopeAuthorizationService.canAccessImportBatch(authentication.principal, #id, "
          + READ
          + ")")
  public ResponseEntity<ImportBatchResponse> getBatch(
      @PathVariable("id")
          @Pattern(regexp = ID_PATTERN, message = "ID lô nhập phải là ObjectId 24 ký tự hex")
          String id) {
    return ResponseEntity.ok(importBatchService.getBatch(id));
  }

  @GetMapping("/import-batches/{id}/errors")
  @PreAuthorize(
      "@scopeAuthorizationService.canAccessImportBatch(authentication.principal, #id, "
          + READ
          + ")")
  public ResponseEntity<PageResponse<ImportRowErrorResponse>> getErrors(
      @PathVariable("id")
          @Pattern(regexp = ID_PATTERN, message = "ID lô nhập phải là ObjectId 24 ký tự hex")
          String id,
      @RequestParam(name = "page", required = false) Integer page,
      @RequestParam(name = "size", required = false) Integer size) {
    return ResponseEntity.ok(
        importBatchService.getErrors(id, PageableValidationUtils.createPageable(page, size)));
  }

  @PostMapping("/import-batches/{id}/validate")
  @PreAuthorize(
      "hasRole('LECTURER') and"
          + " @scopeAuthorizationService.canAccessImportBatch(authentication.principal, #id, "
          + WRITE
          + ")")
  public ResponseEntity<ImportBatchResponse> validateBatch(
      @PathVariable("id")
          @Pattern(regexp = ID_PATTERN, message = "ID lô nhập phải là ObjectId 24 ký tự hex")
          String id,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.info("API request: POST /import-batches/{}/validate by [{}]", id, principal.getUsername());
    return ResponseEntity.ok(importBatchService.validateBatch(id, principal));
  }

  @PostMapping("/import-batches/{id}/confirm")
  @PreAuthorize(
      "hasRole('LECTURER') and"
          + " @scopeAuthorizationService.canAccessImportBatch(authentication.principal, #id, "
          + WRITE
          + ")")
  public ResponseEntity<ImportBatchResponse> confirmBatch(
      @PathVariable("id")
          @Pattern(regexp = ID_PATTERN, message = "ID lô nhập phải là ObjectId 24 ký tự hex")
          String id,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.info("API request: POST /import-batches/{}/confirm by [{}]", id, principal.getUsername());
    return ResponseEntity.ok(importBatchService.confirmBatch(id, principal));
  }

  @PostMapping("/import-batches/{id}/rollback")
  @PreAuthorize(
      "hasRole('LECTURER') and"
          + " @scopeAuthorizationService.canAccessImportBatch(authentication.principal, #id, "
          + WRITE
          + ")")
  public ResponseEntity<ImportBatchResponse> rollbackBatch(
      @PathVariable("id")
          @Pattern(regexp = ID_PATTERN, message = "ID lô nhập phải là ObjectId 24 ký tự hex")
          String id,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.info("API request: POST /import-batches/{}/rollback by [{}]", id, principal.getUsername());
    return ResponseEntity.ok(importBatchService.rollbackBatch(id, principal));
  }

  /** Phần form {@code columnMapping} là chuỗi JSON (form-data không gửi được đối tượng lồng). */
  private ImportColumnMappingDto parseMapping(String json) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      return objectMapper.readValue(json, ImportColumnMappingDto.class);
    } catch (JacksonException ex) {
      throw new AppException(
          ErrorCode.VALIDATION_ERROR,
          HttpStatus.BAD_REQUEST,
          "columnMapping không phải JSON hợp lệ dạng {\"studentColumn\":..., \"items\":[{\"column\":..., \"item\":...}]}");
    }
  }
}
