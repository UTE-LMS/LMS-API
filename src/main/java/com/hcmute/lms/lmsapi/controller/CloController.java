package com.hcmute.lms.lmsapi.controller;

import com.hcmute.lms.lmsapi.dto.CloRequest;
import com.hcmute.lms.lmsapi.dto.CloResponse;
import com.hcmute.lms.lmsapi.dto.CloUpdateRequest;
import com.hcmute.lms.lmsapi.service.CloService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.List;
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
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CloController {

  private final CloService cloService;

  @GetMapping("/syllabi/{syllabusId}/clos")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<List<CloResponse>> getClos(
      @PathVariable("syllabusId")
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "ID đề cương phải là ObjectId 24 ký tự hex")
          String syllabusId) {
    return ResponseEntity.ok(cloService.getClos(syllabusId));
  }

  @PostMapping("/syllabi/{syllabusId}/clos")
  @PreAuthorize(
      "hasRole('ADMIN') or (hasRole('PROGRAM_MANAGER') and @scopeAuthorizationService.canAccessSyllabus(authentication.principal, #syllabusId, T(com.hcmute.lms.lmsapi.enums.AccessType).WRITE))")
  public ResponseEntity<CloResponse> createClo(
      @PathVariable("syllabusId")
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "ID đề cương phải là ObjectId 24 ký tự hex")
          String syllabusId,
      @Valid @RequestBody CloRequest request) {
    log.info(
        "API request: POST /api/v1/syllabi/{}/clos with code [{}]", syllabusId, request.getCode());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(cloService.createClo(syllabusId, request));
  }

  @GetMapping("/clos/{id}")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<CloResponse> getClo(
      @PathVariable("id")
          @Pattern(regexp = "^[0-9a-fA-F]{24}$", message = "ID CLO phải là ObjectId 24 ký tự hex")
          String id) {
    return ResponseEntity.ok(cloService.getClo(id));
  }

  @PutMapping("/clos/{id}")
  @PreAuthorize(
      "hasRole('ADMIN') or (hasRole('PROGRAM_MANAGER') and @scopeAuthorizationService.canAccessClo(authentication.principal, #id, T(com.hcmute.lms.lmsapi.enums.AccessType).WRITE))")
  public ResponseEntity<CloResponse> updateClo(
      @PathVariable("id")
          @Pattern(regexp = "^[0-9a-fA-F]{24}$", message = "ID CLO phải là ObjectId 24 ký tự hex")
          String id,
      @Valid @RequestBody CloUpdateRequest request) {
    log.info("API request: PUT /api/v1/clos/{} with code [{}]", id, request.getCode());
    return ResponseEntity.ok(cloService.updateClo(id, request));
  }

  @DeleteMapping("/clos/{id}")
  @PreAuthorize(
      "hasRole('ADMIN') or (hasRole('PROGRAM_MANAGER') and @scopeAuthorizationService.canAccessClo(authentication.principal, #id, T(com.hcmute.lms.lmsapi.enums.AccessType).WRITE))")
  public ResponseEntity<Void> deleteClo(
      @PathVariable("id")
          @Pattern(regexp = "^[0-9a-fA-F]{24}$", message = "ID CLO phải là ObjectId 24 ký tự hex")
          String id) {
    log.info("API request: DELETE /api/v1/clos/{}", id);
    cloService.deleteClo(id);
    return ResponseEntity.noContent().build();
  }
}
