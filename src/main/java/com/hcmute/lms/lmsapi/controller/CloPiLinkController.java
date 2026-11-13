package com.hcmute.lms.lmsapi.controller;

import com.hcmute.lms.lmsapi.dto.CloPiLinkResponse;
import com.hcmute.lms.lmsapi.dto.CloPiMatrixResponse;
import com.hcmute.lms.lmsapi.service.CloPiLinkService;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
}
