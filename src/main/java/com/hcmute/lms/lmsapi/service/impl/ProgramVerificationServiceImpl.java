package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.document.Pi;
import com.hcmute.lms.lmsapi.document.Plo;
import com.hcmute.lms.lmsapi.document.ProgramVersion;
import com.hcmute.lms.lmsapi.document.Verification;
import com.hcmute.lms.lmsapi.dto.PiResponse;
import com.hcmute.lms.lmsapi.dto.PloResponse;
import com.hcmute.lms.lmsapi.dto.ProgramVersionResponse;
import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import com.hcmute.lms.lmsapi.dto.VerificationRequest;
import com.hcmute.lms.lmsapi.enums.AccessType;
import com.hcmute.lms.lmsapi.exception.ResourceNotFoundException;
import com.hcmute.lms.lmsapi.mapper.PiMapper;
import com.hcmute.lms.lmsapi.mapper.PloMapper;
import com.hcmute.lms.lmsapi.mapper.ProgramVersionMapper;
import com.hcmute.lms.lmsapi.repository.PiRepository;
import com.hcmute.lms.lmsapi.repository.PloRepository;
import com.hcmute.lms.lmsapi.repository.ProgramVersionRepository;
import com.hcmute.lms.lmsapi.service.ProgramVerificationService;
import com.hcmute.lms.lmsapi.service.ScopeAuthorizationService;
import com.hcmute.lms.lmsapi.util.ObjectIdUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ProgramVerificationServiceImpl implements ProgramVerificationService {

    private final ProgramVersionRepository programVersionRepository;
    private final PloRepository ploRepository;
    private final PiRepository piRepository;
    private final ProgramVersionMapper programVersionMapper;
    private final PloMapper ploMapper;
    private final PiMapper piMapper;
    private final ScopeAuthorizationService scopeAuthorizationService;

    @Override
    public ProgramVersionResponse verifyProgramVersion(String id, VerificationRequest request, UserPrincipal principal) {
        ProgramVersion programVersion = requireProgramVersion(id);
        requireProgramAccess(principal, programVersion.getProgramId());
        programVersion.setVerification(buildVerification(request, principal));
        return programVersionMapper.toResponse(programVersionRepository.save(programVersion));
    }

    @Override
    public PloResponse verifyPlo(String programVersionId, String ploId, VerificationRequest request, UserPrincipal principal) {
        ProgramVersion programVersion = requireProgramVersion(programVersionId);
        requireProgramAccess(principal, programVersion.getProgramId());
        Plo plo = requirePlo(programVersionId, ploId);
        plo.setVerification(buildVerification(request, principal));
        return ploMapper.toResponse(ploRepository.save(plo));
    }

    @Override
    public PiResponse verifyPi(String programVersionId, String piId, VerificationRequest request, UserPrincipal principal) {
        ProgramVersion programVersion = requireProgramVersion(programVersionId);
        requireProgramAccess(principal, programVersion.getProgramId());
        Pi pi = requirePi(programVersionId, piId);
        pi.setVerification(buildVerification(request, principal));
        return piMapper.toResponse(piRepository.save(pi));
    }

    private ProgramVersion requireProgramVersion(String id) {
        ObjectIdUtils.requireValid(id, "id");
        return programVersionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProgramVersion", "id", id));
    }

    private Plo requirePlo(String programVersionId, String id) {
        ObjectIdUtils.requireValid(id, "id");
        return ploRepository.findByIdAndProgramVersionId(id, programVersionId)
                .orElseThrow(() -> new ResourceNotFoundException("PLO", "id", id));
    }

    private Pi requirePi(String programVersionId, String id) {
        ObjectIdUtils.requireValid(id, "id");
        return piRepository.findByIdAndProgramVersionId(id, programVersionId)
                .orElseThrow(() -> new ResourceNotFoundException("PI", "id", id));
    }

    private void requireProgramAccess(UserPrincipal principal, String programId) {
        if (principal == null || !scopeAuthorizationService.canAccessProgram(principal, programId, AccessType.WRITE)) {
            throw new AccessDeniedException("Người dùng không có phạm vi xác nhận chương trình đào tạo này");
        }
    }

    private Verification buildVerification(VerificationRequest request, UserPrincipal principal) {
        return Verification.builder()
                .status(request.getStatus())
                .verifiedBy(principal.getId())
                .verifiedAt(Instant.now())
                .note(request.getNote())
                .build();
    }
}
