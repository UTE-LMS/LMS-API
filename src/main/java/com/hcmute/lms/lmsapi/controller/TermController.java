package com.hcmute.lms.lmsapi.controller;

import com.hcmute.lms.lmsapi.dto.TermRequest;
import com.hcmute.lms.lmsapi.dto.TermResponse;
import com.hcmute.lms.lmsapi.dto.TermStatusUpdateRequest;
import com.hcmute.lms.lmsapi.dto.TermUpdateRequest;
import com.hcmute.lms.lmsapi.service.TermService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.security.Principal;
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
@RequestMapping("/api/v1/terms")
@RequiredArgsConstructor
public class TermController {

  private final TermService termService;

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<List<TermResponse>> getTerms() {
    return ResponseEntity.ok(termService.getTerms());
  }

  @GetMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<TermResponse> getTerm(
      @PathVariable("id")
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "ID học kỳ phải là ObjectId 24 ký tự hex")
          String id) {
    return ResponseEntity.ok(termService.getTerm(id));
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'PROGRAM_MANAGER')")
  public ResponseEntity<TermResponse> createTerm(@Valid @RequestBody TermRequest request) {
    log.info("API request: POST /api/v1/terms code=[{}]", request.getCode());
    return ResponseEntity.status(HttpStatus.CREATED).body(termService.createTerm(request));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN', 'PROGRAM_MANAGER')")
  public ResponseEntity<TermResponse> updateTerm(
      @PathVariable("id")
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "ID học kỳ phải là ObjectId 24 ký tự hex")
          String id,
      @Valid @RequestBody TermUpdateRequest request) {
    log.info("API request: PUT /api/v1/terms/{}", id);
    return ResponseEntity.ok(termService.updateTerm(id, request));
  }

  @PutMapping("/{id}/status")
  @PreAuthorize("hasAnyRole('ADMIN', 'PROGRAM_MANAGER')")
  public ResponseEntity<TermResponse> updateStatus(
      @PathVariable("id")
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "ID học kỳ phải là ObjectId 24 ký tự hex")
          String id,
      @Valid @RequestBody TermStatusUpdateRequest request,
      Principal principal) {
    String actor = principal != null ? principal.getName() : "system";
    log.info(
        "API request: PUT /api/v1/terms/{}/status to [{}] by [{}]", id, request.getStatus(), actor);
    return ResponseEntity.ok(termService.updateStatus(id, request, actor));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Void> deleteTerm(
      @PathVariable("id")
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "ID học kỳ phải là ObjectId 24 ký tự hex")
          String id) {
    log.info("API request: DELETE /api/v1/terms/{}", id);
    termService.deleteTerm(id);
    return ResponseEntity.noContent().build();
  }
}
