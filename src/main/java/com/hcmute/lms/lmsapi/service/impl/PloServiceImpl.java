package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.document.Plo;
import com.hcmute.lms.lmsapi.document.ProgramVersion;
import com.hcmute.lms.lmsapi.document.Verification;
import com.hcmute.lms.lmsapi.dto.PloRequest;
import com.hcmute.lms.lmsapi.dto.PloResponse;
import com.hcmute.lms.lmsapi.enums.PloCategory;
import com.hcmute.lms.lmsapi.enums.ProgramVersionStatus;
import com.hcmute.lms.lmsapi.enums.VerificationStatus;
import com.hcmute.lms.lmsapi.exception.AppException;
import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
import com.hcmute.lms.lmsapi.exception.ErrorCode;
import com.hcmute.lms.lmsapi.exception.ResourceNotFoundException;
import com.hcmute.lms.lmsapi.mapper.PloMapper;
import com.hcmute.lms.lmsapi.repository.PiRepository;
import com.hcmute.lms.lmsapi.repository.PloRepository;
import com.hcmute.lms.lmsapi.repository.ProgramVersionRepository;
import com.hcmute.lms.lmsapi.service.PloService;
import com.hcmute.lms.lmsapi.util.ObjectIdUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PloServiceImpl implements PloService {

    private final PloRepository ploRepository;
    private final PiRepository piRepository;
    private final ProgramVersionRepository programVersionRepository;
    private final PloMapper ploMapper;

    @Override
    public List<PloResponse> getPlos(String programVersionId) {
        requireDraftableVersion(programVersionId, false);
        return ploMapper.toResponseList(
                ploRepository.findByProgramVersionIdOrderByDisplayOrderAsc(programVersionId));
    }

    @Override
    public PloResponse getPlo(String programVersionId, String id) {
        requireDraftableVersion(programVersionId, false);
        return ploMapper.toResponse(requirePlo(programVersionId, id));
    }

    @Override
    public PloResponse createPlo(String programVersionId, PloRequest request) {
        requireDraftableVersion(programVersionId, true);

        String code = request.getCode().trim();
        if (ploRepository.existsByProgramVersionIdAndCode(programVersionId, code)) {
            throw conflict(code);
        }

        Plo plo = Plo.builder()
                .programVersionId(programVersionId)
                .code(code)
                .displayOrder(request.getDisplayOrder())
                .category(request.getCategory() != null ? request.getCategory() : PloCategory.UNSPECIFIED)
                .contentOriginal(request.getContentOriginal())
                .contentSummary(request.getContentSummary())
                .capabilityLevelRaw(request.getCapabilityLevelRaw())
                .verification(Verification.builder().status(VerificationStatus.NEEDS_CLARIFICATION).build())
                .reviewNote(request.getReviewNote())
                .build();

        try {
            plo = ploRepository.save(plo);
        } catch (DuplicateKeyException ex) {
            log.warn("Duplicate PLO code on create: {} - {}", programVersionId, code);
            throw conflict(code);
        }
        return ploMapper.toResponse(plo);
    }

    @Override
    public PloResponse updatePlo(String programVersionId, String id, PloRequest request) {
        requireDraftableVersion(programVersionId, true);
        Plo plo = requirePlo(programVersionId, id);

        String code = request.getCode().trim();
        if (!code.equals(plo.getCode())
                && ploRepository.existsByProgramVersionIdAndCodeAndIdNot(programVersionId, code, id)) {
            throw conflict(code);
        }

        plo.setCode(code);
        plo.setDisplayOrder(request.getDisplayOrder());
        plo.setCategory(request.getCategory() != null ? request.getCategory() : PloCategory.UNSPECIFIED);
        plo.setContentOriginal(request.getContentOriginal());
        plo.setContentSummary(request.getContentSummary());
        plo.setCapabilityLevelRaw(request.getCapabilityLevelRaw());
        plo.setReviewNote(request.getReviewNote());

        try {
            plo = ploRepository.save(plo);
        } catch (DuplicateKeyException ex) {
            log.warn("Duplicate PLO code on update: {} - {}", programVersionId, code);
            throw conflict(code);
        }
        return ploMapper.toResponse(plo);
    }

    @Override
    public void deletePlo(String programVersionId, String id) {
        requireDraftableVersion(programVersionId, true);
        Plo plo = requirePlo(programVersionId, id);

        if (piRepository.countByPloId(plo.getId()) > 0) {
            throw new BusinessRuleViolationException("PLO_HAS_PIS",
                    "Không thể xóa PLO khi vẫn còn PI trực thuộc");
        }
        ploRepository.delete(plo);
    }

    private ProgramVersion requireDraftableVersion(String programVersionId, boolean requireDraft) {
        ObjectIdUtils.requireValid(programVersionId, "programVersionId");
        ProgramVersion programVersion = programVersionRepository.findById(programVersionId)
                .orElseThrow(() -> new ResourceNotFoundException("ProgramVersion", "id", programVersionId));
        if (requireDraft && programVersion.getStatus() != ProgramVersionStatus.DRAFT) {
            throw new BusinessRuleViolationException("PROGRAM_VERSION_LOCKED",
                    "Chỉ được sửa nội dung PLO khi phiên bản chương trình đào tạo ở trạng thái DRAFT");
        }
        return programVersion;
    }

    private Plo requirePlo(String programVersionId, String id) {
        ObjectIdUtils.requireValid(id, "id");
        return ploRepository.findByIdAndProgramVersionId(id, programVersionId)
                .orElseThrow(() -> new ResourceNotFoundException("PLO", "id", id));
    }

    private AppException conflict(String code) {
        return new AppException(ErrorCode.CONFLICT, HttpStatus.CONFLICT,
                "Mã PLO đã tồn tại trong phiên bản chương trình đào tạo: " + code);
    }
}
