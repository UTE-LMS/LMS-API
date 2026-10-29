package com.hcmute.lms.lmsapi.controller;

import com.hcmute.lms.lmsapi.dto.PiRequest;
import com.hcmute.lms.lmsapi.dto.PiResponse;
import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import com.hcmute.lms.lmsapi.dto.VerificationRequest;
import com.hcmute.lms.lmsapi.service.PiService;
import com.hcmute.lms.lmsapi.service.ProgramVerificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/program-versions/{programVersionId}/pis")
@RequiredArgsConstructor
public class PiController {

    private final PiService piService;
    private final ProgramVerificationService programVerificationService;

    @GetMapping
    public ResponseEntity<List<PiResponse>> getPis(
            @PathVariable("programVersionId")
            @Pattern(regexp = "^[0-9a-fA-F]{24}$", message = "ID phiên bản phải là ObjectId 24 ký tự hex") String programVersionId) {
        return ResponseEntity.ok(piService.getPis(programVersionId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PiResponse> getPi(
            @PathVariable("programVersionId")
            @Pattern(regexp = "^[0-9a-fA-F]{24}$", message = "ID phiên bản phải là ObjectId 24 ký tự hex") String programVersionId,
            @PathVariable("id")
            @Pattern(regexp = "^[0-9a-fA-F]{24}$", message = "ID PI phải là ObjectId 24 ký tự hex") String id) {
        return ResponseEntity.ok(piService.getPi(programVersionId, id));
    }

    @PostMapping
    @PreAuthorize("hasRole('PROGRAM_MANAGER')")
    public ResponseEntity<PiResponse> createPi(
            @PathVariable("programVersionId")
            @Pattern(regexp = "^[0-9a-fA-F]{24}$", message = "ID phiên bản phải là ObjectId 24 ký tự hex") String programVersionId,
            @Valid @RequestBody PiRequest request) {
        log.info("API request: POST /api/v1/program-versions/{}/pis with code [{}]", programVersionId, request.getCode());
        return ResponseEntity.status(HttpStatus.CREATED).body(piService.createPi(programVersionId, request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('PROGRAM_MANAGER')")
    public ResponseEntity<PiResponse> updatePi(
            @PathVariable("programVersionId")
            @Pattern(regexp = "^[0-9a-fA-F]{24}$", message = "ID phiên bản phải là ObjectId 24 ký tự hex") String programVersionId,
            @PathVariable("id")
            @Pattern(regexp = "^[0-9a-fA-F]{24}$", message = "ID PI phải là ObjectId 24 ký tự hex") String id,
            @Valid @RequestBody PiRequest request) {
        log.info("API request: PUT /api/v1/program-versions/{}/pis/{}", programVersionId, id);
        return ResponseEntity.ok(piService.updatePi(programVersionId, id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('PROGRAM_MANAGER')")
    public ResponseEntity<Void> deletePi(
            @PathVariable("programVersionId")
            @Pattern(regexp = "^[0-9a-fA-F]{24}$", message = "ID phiên bản phải là ObjectId 24 ký tự hex") String programVersionId,
            @PathVariable("id")
            @Pattern(regexp = "^[0-9a-fA-F]{24}$", message = "ID PI phải là ObjectId 24 ký tự hex") String id) {
        log.info("API request: DELETE /api/v1/program-versions/{}/pis/{}", programVersionId, id);
        piService.deletePi(programVersionId, id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/verification")
    @PreAuthorize("hasRole('QA_OFFICER')")
    public ResponseEntity<PiResponse> verifyPi(
            @PathVariable("programVersionId")
            @Pattern(regexp = "^[0-9a-fA-F]{24}$", message = "ID phiên bản phải là ObjectId 24 ký tự hex") String programVersionId,
            @PathVariable("id")
            @Pattern(regexp = "^[0-9a-fA-F]{24}$", message = "ID PI phải là ObjectId 24 ký tự hex") String id,
            @Valid @RequestBody VerificationRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        log.info("API request: PUT /api/v1/program-versions/{}/pis/{}/verification to [{}]",
                programVersionId, id, request.getStatus());
        return ResponseEntity.ok(programVerificationService.verifyPi(programVersionId, id, request, principal));
    }
}
