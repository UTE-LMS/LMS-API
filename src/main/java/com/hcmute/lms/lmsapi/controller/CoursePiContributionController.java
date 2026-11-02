package com.hcmute.lms.lmsapi.controller;

import com.hcmute.lms.lmsapi.dto.CoursePiContributionRequest;
import com.hcmute.lms.lmsapi.dto.CoursePiContributionResponse;
import com.hcmute.lms.lmsapi.service.CoursePiContributionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/program-versions/{programVersionId}/pi-contributions")
@RequiredArgsConstructor
public class CoursePiContributionController {

    private final CoursePiContributionService coursePiContributionService;

    @GetMapping
    public ResponseEntity<List<CoursePiContributionResponse>> getContributions(
            @PathVariable("programVersionId")
            @Pattern(regexp = "^[0-9a-fA-F]{24}$", message = "ID phiên bản phải là ObjectId 24 ký tự hex") String programVersionId) {
        return ResponseEntity.ok(coursePiContributionService.getContributions(programVersionId));
    }

    @PostMapping
    @PreAuthorize("hasRole('PROGRAM_MANAGER')")
    public ResponseEntity<CoursePiContributionResponse> createContribution(
            @PathVariable("programVersionId")
            @Pattern(regexp = "^[0-9a-fA-F]{24}$", message = "ID phiên bản phải là ObjectId 24 ký tự hex") String programVersionId,
            @Valid @RequestBody CoursePiContributionRequest request) {
        log.info("API request: POST /api/v1/program-versions/{}/pi-contributions", programVersionId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(coursePiContributionService.createContribution(programVersionId, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('PROGRAM_MANAGER')")
    public ResponseEntity<Void> deleteContribution(
            @PathVariable("programVersionId")
            @Pattern(regexp = "^[0-9a-fA-F]{24}$", message = "ID phiên bản phải là ObjectId 24 ký tự hex") String programVersionId,
            @PathVariable("id")
            @Pattern(regexp = "^[0-9a-fA-F]{24}$", message = "ID đóng góp phải là ObjectId 24 ký tự hex") String id) {
        log.info("API request: DELETE /api/v1/program-versions/{}/pi-contributions/{}", programVersionId, id);
        coursePiContributionService.deleteContribution(programVersionId, id);
        return ResponseEntity.noContent().build();
    }
}
