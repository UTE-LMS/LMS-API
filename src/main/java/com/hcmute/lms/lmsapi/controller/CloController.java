package com.hcmute.lms.lmsapi.controller;

import com.hcmute.lms.lmsapi.dto.CloResponse;
import com.hcmute.lms.lmsapi.service.CloService;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

  @GetMapping("/clos/{id}")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<CloResponse> getClo(
      @PathVariable("id")
          @Pattern(regexp = "^[0-9a-fA-F]{24}$", message = "ID CLO phải là ObjectId 24 ký tự hex")
          String id) {
    return ResponseEntity.ok(cloService.getClo(id));
  }
}
