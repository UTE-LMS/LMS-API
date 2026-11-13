package com.hcmute.lms.lmsapi.controller;

import com.hcmute.lms.lmsapi.dto.PloResponse;
import com.hcmute.lms.lmsapi.service.PloService;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/program-versions/{programVersionId}/plos")
@RequiredArgsConstructor
public class PloController {

  private final PloService ploService;

  @GetMapping
  public ResponseEntity<List<PloResponse>> getPlos(
      @PathVariable("programVersionId")
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "ID phiên bản phải là ObjectId 24 ký tự hex")
          String programVersionId) {
    return ResponseEntity.ok(ploService.getPlos(programVersionId));
  }

  @GetMapping("/{id}")
  public ResponseEntity<PloResponse> getPlo(
      @PathVariable("programVersionId")
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "ID phiên bản phải là ObjectId 24 ký tự hex")
          String programVersionId,
      @PathVariable("id")
          @Pattern(regexp = "^[0-9a-fA-F]{24}$", message = "ID PLO phải là ObjectId 24 ký tự hex")
          String id) {
    return ResponseEntity.ok(ploService.getPlo(programVersionId, id));
  }
}
