package com.hcmute.lms.lmsapi.controller;

import com.hcmute.lms.lmsapi.dto.PageResponse;
import com.hcmute.lms.lmsapi.dto.ProgramRequest;
import com.hcmute.lms.lmsapi.dto.ProgramResponse;
import com.hcmute.lms.lmsapi.service.ProgramService;
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
@RequestMapping("/api/v1/programs")
@RequiredArgsConstructor
public class ProgramController {

    private final ProgramService programService;

    @GetMapping
    public ResponseEntity<PageResponse<ProgramResponse>> getPrograms(
            @RequestParam(defaultValue = "0", required = false)
            @Min(value = 0, message = "Chỉ số trang phải lớn hơn hoặc bằng 0") Integer page,
            @RequestParam(defaultValue = "20", required = false)
            @Min(value = 1, message = "Kích thước trang phải lớn hơn hoặc bằng 1")
            @Max(value = 100, message = "Kích thước trang tối đa 100") Integer size) {
        PageableValidationUtils.validate(page, size);
        return ResponseEntity.ok(programService.getPrograms(
                page != null ? page : PageableValidationUtils.DEFAULT_PAGE,
                size != null ? size : PageableValidationUtils.DEFAULT_SIZE));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProgramResponse> getProgramById(
            @PathVariable("id")
            @Pattern(regexp = "^[0-9a-fA-F]{24}$", message = "ID chương trình đào tạo phải là ObjectId 24 ký tự hex") String id) {
        return ResponseEntity.ok(programService.getProgramById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('PROGRAM_MANAGER')")
    public ResponseEntity<ProgramResponse> createProgram(@Valid @RequestBody ProgramRequest request) {
        log.info("API request: POST /api/v1/programs with code [{}]", request.getCode());
        return ResponseEntity.status(HttpStatus.CREATED).body(programService.createProgram(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('PROGRAM_MANAGER')")
    public ResponseEntity<ProgramResponse> updateProgram(
            @PathVariable("id")
            @Pattern(regexp = "^[0-9a-fA-F]{24}$", message = "ID chương trình đào tạo phải là ObjectId 24 ký tự hex") String id,
            @Valid @RequestBody ProgramRequest request) {
        log.info("API request: PUT /api/v1/programs/{}", id);
        return ResponseEntity.ok(programService.updateProgram(id, request));
    }
}
