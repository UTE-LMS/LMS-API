package com.hcmute.lms.lmsapi.controller;

import com.hcmute.lms.lmsapi.dto.CloPiLinkRequest;
import com.hcmute.lms.lmsapi.dto.CloPiLinkResponse;
import com.hcmute.lms.lmsapi.dto.CloPiMatrixResponse;
import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import com.hcmute.lms.lmsapi.dto.VerificationRequest;
import com.hcmute.lms.lmsapi.service.CloPiLinkService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CloPiLinkController {

  private final CloPiLinkService cloPiLinkService;

  @GetMapping("/syllabi/{syllabusId}/clo-pi-links")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<List<CloPiLinkResponse>> getLinks(
      @PathVariable("syllabusId")
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "ID đề cương phải là ObjectId 24 ký tự hex")
          String syllabusId) {
    return ResponseEntity.ok(cloPiLinkService.getLinks(syllabusId));
  }

  @PostMapping("/syllabi/{syllabusId}/clo-pi-links")
  @PreAuthorize(
      "hasRole('ADMIN') or (hasRole('PROGRAM_MANAGER') and @scopeAuthorizationService.canAccessProgramVersion(authentication.principal, #request.programVersionId, T(com.hcmute.lms.lmsapi.enums.AccessType).WRITE))")
  public ResponseEntity<CloPiLinkResponse> createLink(
      @PathVariable("syllabusId")
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "ID đề cương phải là ObjectId 24 ký tự hex")
          String syllabusId,
      @Valid @RequestBody CloPiLinkRequest request) {
    log.info(
        "API request: POST /api/v1/syllabi/{}/clo-pi-links with cloId=[{}], piId=[{}]",
        syllabusId,
        request.getCloId(),
        request.getPiId());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(cloPiLinkService.createLink(syllabusId, request));
  }

  @DeleteMapping("/clo-pi-links/{id}")
  @PreAuthorize(
      "hasRole('ADMIN') or (hasRole('PROGRAM_MANAGER') and @scopeAuthorizationService.canAccessCloPiLink(authentication.principal, #id, T(com.hcmute.lms.lmsapi.enums.AccessType).WRITE))")
  public ResponseEntity<Void> deleteLink(
      @PathVariable("id")
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "ID liên kết phải là ObjectId 24 ký tự hex")
          String id) {
    log.info("API request: DELETE /api/v1/clo-pi-links/{}", id);
    cloPiLinkService.deleteLink(id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/syllabi/{syllabusId}/clo-pi-matrix")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<CloPiMatrixResponse> getMatrix(
      @PathVariable("syllabusId")
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "ID đề cương phải là ObjectId 24 ký tự hex")
          String syllabusId,
      @RequestParam("programVersionId")
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "ID phiên bản CTĐT phải là ObjectId 24 ký tự hex")
          String programVersionId) {
    return ResponseEntity.ok(cloPiLinkService.getMatrix(syllabusId, programVersionId));
  }

  @PutMapping("/clo-pi-links/{id}/verification")
  @PreAuthorize("hasRole('QA')")
  public ResponseEntity<CloPiLinkResponse> verifyLink(
      @PathVariable("id")
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "ID liên kết phải là ObjectId 24 ký tự hex")
          String id,
      @Valid @RequestBody VerificationRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.info("API request: PUT /api/v1/clo-pi-links/{}/verification", id);
    return ResponseEntity.ok(cloPiLinkService.verifyLink(id, request, principal));
  }
}
