package com.hcmute.lms.lmsapi.controller;

import com.hcmute.lms.lmsapi.dto.PiResponse;
import com.hcmute.lms.lmsapi.service.PiService;
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
@RequestMapping("/api/v1/program-versions/{programVersionId}/pis")
@RequiredArgsConstructor
public class PiController {

  private final PiService piService;

  @GetMapping
  public ResponseEntity<List<PiResponse>> getPis(
      @PathVariable("programVersionId")
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "ID phiên bản phải là ObjectId 24 ký tự hex")
          String programVersionId) {
    return ResponseEntity.ok(piService.getPis(programVersionId));
  }

  @GetMapping("/{id}")
  public ResponseEntity<PiResponse> getPi(
      @PathVariable("programVersionId")
          @Pattern(
              regexp = "^[0-9a-fA-F]{24}$",
              message = "ID phiên bản phải là ObjectId 24 ký tự hex")
          String programVersionId,
      @PathVariable("id")
          @Pattern(regexp = "^[0-9a-fA-F]{24}$", message = "ID PI phải là ObjectId 24 ký tự hex")
          String id) {
    return ResponseEntity.ok(piService.getPi(programVersionId, id));
  }
}
