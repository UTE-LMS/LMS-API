package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.document.Pi;
import com.hcmute.lms.lmsapi.document.ProgramVersion;
import com.hcmute.lms.lmsapi.document.Verification;
import com.hcmute.lms.lmsapi.dto.PiRequest;
import com.hcmute.lms.lmsapi.dto.PiResponse;
import com.hcmute.lms.lmsapi.enums.ProgramVersionStatus;
import com.hcmute.lms.lmsapi.enums.VerificationStatus;
import com.hcmute.lms.lmsapi.exception.AppException;
import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
import com.hcmute.lms.lmsapi.exception.ErrorCode;
import com.hcmute.lms.lmsapi.exception.ResourceNotFoundException;
import com.hcmute.lms.lmsapi.mapper.PiMapper;
import com.hcmute.lms.lmsapi.repository.CoursePiContributionRepository;
import com.hcmute.lms.lmsapi.repository.PiRepository;
import com.hcmute.lms.lmsapi.repository.PloRepository;
import com.hcmute.lms.lmsapi.repository.ProgramReferenceRepository;
import com.hcmute.lms.lmsapi.repository.ProgramVersionRepository;
import com.hcmute.lms.lmsapi.service.PiService;
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
public class PiServiceImpl implements PiService {

    private final PiRepository piRepository;
    private final PloRepository ploRepository;
    private final ProgramVersionRepository programVersionRepository;
    private final CoursePiContributionRepository coursePiContributionRepository;
    private final ProgramReferenceRepository programReferenceRepository;
    private final PiMapper piMapper;

    @Override
    public List<PiResponse> getPis(String programVersionId) {
        requireDraftableVersion(programVersionId, false);
        return piMapper.toResponseList(
                piRepository.findByProgramVersionIdOrderByDisplayOrderAsc(programVersionId));
    }

    @Override
    public PiResponse getPi(String programVersionId, String id) {
        requireDraftableVersion(programVersionId, false);
        return piMapper.toResponse(requirePi(programVersionId, id));
    }

    @Override
    public PiResponse createPi(String programVersionId, PiRequest request) {
        requireDraftableVersion(programVersionId, true);
        requirePloInVersion(request.getPloId(), programVersionId);

        String code = request.getCode().trim();
        if (piRepository.existsByProgramVersionIdAndCode(programVersionId, code)) {
            throw conflict(code);
        }

        Pi pi = Pi.builder()
                .programVersionId(programVersionId)
                .ploId(request.getPloId())
                .code(code)
                .displayOrder(request.getDisplayOrder())
                .nameOriginalTerm(request.getNameOriginalTerm())
                .contentOriginal(request.getContentOriginal())
                .contentShort(request.getContentShort())
                .capabilityLevelRaw(request.getCapabilityLevelRaw())
                .targetPassRate(request.getTargetPassRate())
                .verification(Verification.builder().status(VerificationStatus.NEEDS_CLARIFICATION).build())
                .reviewNote(request.getReviewNote())
                .build();

        try {
            pi = piRepository.save(pi);
        } catch (DuplicateKeyException ex) {
            log.warn("Duplicate PI code on create: {} - {}", programVersionId, code);
            throw conflict(code);
        }
        return piMapper.toResponse(pi);
    }

    @Override
    public PiResponse updatePi(String programVersionId, String id, PiRequest request) {
        requireDraftableVersion(programVersionId, true);
        Pi pi = requirePi(programVersionId, id);
        requirePloInVersion(request.getPloId(), programVersionId);

        String code = request.getCode().trim();
        if (!code.equals(pi.getCode())
                && piRepository.existsByProgramVersionIdAndCodeAndIdNot(programVersionId, code, id)) {
            throw conflict(code);
        }

        pi.setPloId(request.getPloId());
        pi.setCode(code);
        pi.setDisplayOrder(request.getDisplayOrder());
        pi.setNameOriginalTerm(request.getNameOriginalTerm());
        pi.setContentOriginal(request.getContentOriginal());
        pi.setContentShort(request.getContentShort());
        pi.setCapabilityLevelRaw(request.getCapabilityLevelRaw());
        pi.setTargetPassRate(request.getTargetPassRate());
        pi.setReviewNote(request.getReviewNote());

        try {
            pi = piRepository.save(pi);
        } catch (DuplicateKeyException ex) {
            log.warn("Duplicate PI code on update: {} - {}", programVersionId, code);
            throw conflict(code);
        }
        return piMapper.toResponse(pi);
    }

    @Override
    public void deletePi(String programVersionId, String id) {
        requireDraftableVersion(programVersionId, true);
        Pi pi = requirePi(programVersionId, id);

        if (coursePiContributionRepository.countByPiId(pi.getId()) > 0
                || programReferenceRepository.existsPiReference(pi.getId())) {
            throw new BusinessRuleViolationException("PI_REFERENCED",
                    "Không thể xóa PI khi vẫn còn học phần, CLO hoặc câu hỏi/đề đánh giá tham chiếu");
        }
        piRepository.delete(pi);
    }

    private ProgramVersion requireDraftableVersion(String programVersionId, boolean requireDraft) {
        ObjectIdUtils.requireValid(programVersionId, "programVersionId");
        ProgramVersion programVersion = programVersionRepository.findById(programVersionId)
                .orElseThrow(() -> new ResourceNotFoundException("ProgramVersion", "id", programVersionId));
        if (requireDraft && programVersion.getStatus() != ProgramVersionStatus.DRAFT) {
            throw new BusinessRuleViolationException("PROGRAM_VERSION_LOCKED",
                    "Chỉ được sửa nội dung PI khi phiên bản chương trình đào tạo ở trạng thái DRAFT");
        }
        return programVersion;
    }

    private Pi requirePi(String programVersionId, String id) {
        ObjectIdUtils.requireValid(id, "id");
        return piRepository.findByIdAndProgramVersionId(id, programVersionId)
                .orElseThrow(() -> new ResourceNotFoundException("PI", "id", id));
    }

    private void requirePloInVersion(String ploId, String programVersionId) {
        ObjectIdUtils.requireValid(ploId, "ploId");
        if (ploRepository.findByIdAndProgramVersionId(ploId, programVersionId).isEmpty()) {
            throw new BusinessRuleViolationException("PI_PLO_MISMATCH",
                    "PLO không tồn tại trong phiên bản chương trình đào tạo: " + ploId);
        }
    }

    private AppException conflict(String code) {
        return new AppException(ErrorCode.CONFLICT, HttpStatus.CONFLICT,
                "Mã PI đã tồn tại trong phiên bản chương trình đào tạo: " + code);
    }
}
