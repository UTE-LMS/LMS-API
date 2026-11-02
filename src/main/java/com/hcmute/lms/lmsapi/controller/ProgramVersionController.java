package com.hcmute.lms.lmsapi.controller;

import com.hcmute.lms.lmsapi.dto.PageResponse;
import com.hcmute.lms.lmsapi.dto.ProgramVersionRequest;
import com.hcmute.lms.lmsapi.dto.ProgramVersionResponse;
import com.hcmute.lms.lmsapi.dto.ProgramVersionUpdateRequest;
import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import com.hcmute.lms.lmsapi.dto.VerificationRequest;
import com.hcmute.lms.lmsapi.service.ProgramVerificationService;
import com.hcmute.lms.lmsapi.service.ProgramVersionService;
import com.hcmute.lms.lmsapi.util.PageableValidationUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
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
@RequestMapping("/api/v1/program-versions")
@RequiredArgsConstructor
public class ProgramVersionController {

    private final ProgramVersionService programVersionService;
    private final ProgramVerificationService programVerificationService;

    @GetMapping
    public ResponseEntity<PageResponse<ProgramVersionResponse>> getProgramVersions(
            @RequestParam(defaultValue = "0", required = false)
            @Min(value = 0, message = "Chỉ số trang phải lớn hơn hoặc bằng 0") Integer page,
            @RequestParam(defaultValue = "20", required = false)
            @Min(value = 1, message = "Kích thước trang phải lớn hơn hoặc bằng 1")
            @Max(value = 100, message = "Kích thước trang tối đa 100") Integer size,
            @RequestParam(required = false) String programId) {
        PageableValidationUtils.validate(page, size);
        return ResponseEntity.ok(programVersionService.getProgramVersions(
                page != null ? page : PageableValidationUtils.DEFAULT_PAGE,
                size != null ? size : PageableValidationUtils.DEFAULT_SIZE,
                programId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProgramVersionResponse> getProgramVersionById(
            @PathVariable("id")
            @Pattern(regexp = "^[0-9a-fA-F]{24}$", message = "ID phiên bản phải là ObjectId 24 ký tự hex") String id) {
        return ResponseEntity.ok(programVersionService.getProgramVersionById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('PROGRAM_MANAGER')")
    public ResponseEntity<ProgramVersionResponse> createProgramVersion(
            @Valid @RequestBody ProgramVersionRequest request) {
        log.info("API request: POST /api/v1/program-versions for program [{}]", request.getProgramId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(programVersionService.createProgramVersion(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('PROGRAM_MANAGER')")
    public ResponseEntity<ProgramVersionResponse> updateProgramVersion(
            @PathVariable("id")
            @Pattern(regexp = "^[0-9a-fA-F]{24}$", message = "ID phiên bản phải là ObjectId 24 ký tự hex") String id,
            @Valid @RequestBody ProgramVersionUpdateRequest request) {
        log.info("API request: PUT /api/v1/program-versions/{}", id);
        return ResponseEntity.ok(programVersionService.updateProgramVersion(id, request));
    }

    @PutMapping("/{id}/verification")
    @PreAuthorize("hasRole('QA_OFFICER')")
    public ResponseEntity<ProgramVersionResponse> verifyProgramVersion(
            @PathVariable("id")
            @Pattern(regexp = "^[0-9a-fA-F]{24}$", message = "ID phiên bản phải là ObjectId 24 ký tự hex") String id,
            @Valid @RequestBody VerificationRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        log.info("API request: PUT /api/v1/program-versions/{}/verification to [{}]", id, request.getStatus());
        return ResponseEntity.ok(programVerificationService.verifyProgramVersion(id, request, principal));
    }
}
