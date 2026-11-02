package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.document.ProgramVersion;
import com.hcmute.lms.lmsapi.document.Verification;
import com.hcmute.lms.lmsapi.dto.PageResponse;
import com.hcmute.lms.lmsapi.dto.ProgramVersionRequest;
import com.hcmute.lms.lmsapi.dto.ProgramVersionResponse;
import com.hcmute.lms.lmsapi.dto.ProgramVersionUpdateRequest;
import com.hcmute.lms.lmsapi.enums.ProgramVersionStatus;
import com.hcmute.lms.lmsapi.enums.VerificationStatus;
import com.hcmute.lms.lmsapi.exception.AppException;
import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
import com.hcmute.lms.lmsapi.exception.ErrorCode;
import com.hcmute.lms.lmsapi.exception.ResourceNotFoundException;
import com.hcmute.lms.lmsapi.mapper.ProgramVersionMapper;
import com.hcmute.lms.lmsapi.repository.ProgramRepository;
import com.hcmute.lms.lmsapi.repository.ProgramVersionRepository;
import com.hcmute.lms.lmsapi.service.ProgramVersionService;
import com.hcmute.lms.lmsapi.util.ObjectIdUtils;
import com.hcmute.lms.lmsapi.util.PageableValidationUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProgramVersionServiceImpl implements ProgramVersionService {

    private final ProgramVersionRepository programVersionRepository;
    private final ProgramRepository programRepository;
    private final ProgramVersionMapper programVersionMapper;

    @Override
    public PageResponse<ProgramVersionResponse> getProgramVersions(int page, int size, String programId) {
        PageableValidationUtils.validate(page, size);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "version"));

        Page<ProgramVersion> versionPage;
        if (programId == null || programId.isBlank()) {
            versionPage = programVersionRepository.findAll(pageable);
        } else {
            ObjectIdUtils.requireValid(programId, "programId");
            versionPage = programVersionRepository.findByProgramId(programId, pageable);
        }

        List<ProgramVersionResponse> content = programVersionMapper.toResponseList(versionPage.getContent());
        return PageResponse.of(content, versionPage.getNumber(), versionPage.getSize(), versionPage.getTotalElements());
    }

    @Override
    public ProgramVersionResponse getProgramVersionById(String id) {
        return programVersionMapper.toResponse(requireProgramVersion(id));
    }

    @Override
    public ProgramVersionResponse createProgramVersion(ProgramVersionRequest request) {
        ObjectIdUtils.requireValid(request.getProgramId(), "programId");
        if (!programRepository.existsById(request.getProgramId())) {
            throw new BusinessRuleViolationException("PROGRAM_VERSION_PROGRAM",
                    "Chương trình đào tạo không tồn tại: " + request.getProgramId());
        }

        String version = request.getVersion().trim();
        if (programVersionRepository.existsByProgramIdAndVersion(request.getProgramId(), version)) {
            throw conflict(version);
        }

        ProgramVersion programVersion = ProgramVersion.builder()
                .programId(request.getProgramId())
                .version(version)
                .totalCredits(request.getTotalCredits())
                .durationYears(request.getDurationYears())
                .applicableCohorts(request.getApplicableCohorts() != null
                        ? new ArrayList<>(request.getApplicableCohorts())
                        : new ArrayList<>())
                .status(ProgramVersionStatus.DRAFT)
                .verification(Verification.builder().status(VerificationStatus.NEEDS_CLARIFICATION).build())
                .build();

        try {
            programVersion = programVersionRepository.save(programVersion);
        } catch (DuplicateKeyException ex) {
            log.warn("Duplicate program version on create: {} - {}", request.getProgramId(), version);
            throw conflict(version);
        }
        return programVersionMapper.toResponse(programVersion);
    }

    @Override
    public ProgramVersionResponse updateProgramVersion(String id, ProgramVersionUpdateRequest request) {
        ProgramVersion programVersion = requireProgramVersion(id);

        String version = request.getVersion().trim();
        if (!version.equals(programVersion.getVersion())
                && programVersionRepository.existsByProgramIdAndVersionAndIdNot(programVersion.getProgramId(), version, id)) {
            throw conflict(version);
        }

        validateStatusTransition(programVersion.getStatus(), request.getStatus());

        programVersion.setVersion(version);
        programVersion.setTotalCredits(request.getTotalCredits());
        programVersion.setDurationYears(request.getDurationYears());
        programVersion.setApplicableCohorts(request.getApplicableCohorts() != null
                ? new ArrayList<>(request.getApplicableCohorts())
                : new ArrayList<>());
        if (request.getStatus() != null) {
            programVersion.setStatus(request.getStatus());
        }

        try {
            programVersion = programVersionRepository.save(programVersion);
        } catch (DuplicateKeyException ex) {
            log.warn("Duplicate program version on update: {} - {}", programVersion.getProgramId(), version);
            throw conflict(version);
        }
        return programVersionMapper.toResponse(programVersion);
    }

    private ProgramVersion requireProgramVersion(String id) {
        ObjectIdUtils.requireValid(id, "id");
        return programVersionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProgramVersion", "id", id));
    }

    private void validateStatusTransition(ProgramVersionStatus current, ProgramVersionStatus next) {
        if (next == null || next == current) {
            return;
        }
        if (current == null) {
            if (next != ProgramVersionStatus.DRAFT) {
                throw invalidTransition(current, next);
            }
            return;
        }
        if (next.ordinal() != current.ordinal() + 1) {
            throw invalidTransition(current, next);
        }
    }

    private BusinessRuleViolationException invalidTransition(ProgramVersionStatus current, ProgramVersionStatus next) {
        return new BusinessRuleViolationException("PROGRAM_VERSION_STATUS",
                String.format("Không thể chuyển trạng thái từ %s sang %s; chỉ cho phép DRAFT -> ACTIVE -> ARCHIVED", current, next));
    }

    private AppException conflict(String version) {
        return new AppException(ErrorCode.CONFLICT, HttpStatus.CONFLICT,
                "Phiên bản chương trình đào tạo đã tồn tại: " + version);
    }
}
