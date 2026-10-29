package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.document.Pi;
import com.hcmute.lms.lmsapi.document.Plo;
import com.hcmute.lms.lmsapi.document.ProgramVersion;
import com.hcmute.lms.lmsapi.dto.PiResponse;
import com.hcmute.lms.lmsapi.dto.PloResponse;
import com.hcmute.lms.lmsapi.dto.ProgramVersionResponse;
import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import com.hcmute.lms.lmsapi.dto.VerificationRequest;
import com.hcmute.lms.lmsapi.enums.AccessType;
import com.hcmute.lms.lmsapi.enums.VerificationStatus;
import com.hcmute.lms.lmsapi.mapper.PiMapper;
import com.hcmute.lms.lmsapi.mapper.PloMapper;
import com.hcmute.lms.lmsapi.mapper.ProgramVersionMapper;
import com.hcmute.lms.lmsapi.repository.PiRepository;
import com.hcmute.lms.lmsapi.repository.PloRepository;
import com.hcmute.lms.lmsapi.repository.ProgramVersionRepository;
import com.hcmute.lms.lmsapi.service.ScopeAuthorizationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProgramVerificationServiceImpl Unit Tests (F02 / US-08 / ADR 0004)")
class ProgramVerificationServiceImplTest {

    private static final String PROGRAM_ID = "671dcba5f123456789abc001";
    private static final String VERSION_ID = "671dcba5f123456789abc002";
    private static final String PLO_ID = "671dcba5f123456789abc003";
    private static final String PI_ID = "671dcba5f123456789abc004";
    private static final String QA_ID = "671dcba5f123456789abc00a";

    @Mock
    private ProgramVersionRepository programVersionRepository;

    @Mock
    private PloRepository ploRepository;

    @Mock
    private PiRepository piRepository;

    @Mock
    private ProgramVersionMapper programVersionMapper;

    @Mock
    private PloMapper ploMapper;

    @Mock
    private PiMapper piMapper;

    @Mock
    private ScopeAuthorizationService scopeAuthorizationService;

    @InjectMocks
    private ProgramVerificationServiceImpl service;

    private UserPrincipal principal() {
        return UserPrincipal.builder().id(QA_ID).username("qa").build();
    }

    private ProgramVersion version() {
        return ProgramVersion.builder().id(VERSION_ID).programId(PROGRAM_ID).build();
    }

    private VerificationRequest request() {
        return VerificationRequest.builder().status(VerificationStatus.VERIFIED).note("Đã kiểm tra").build();
    }

    @Test
    @DisplayName("QA xác nhận phiên bản CTĐT trong phạm vi thành công")
    void verifyVersion_success() {
        when(programVersionRepository.findById(VERSION_ID)).thenReturn(Optional.of(version()));
        when(scopeAuthorizationService.canAccessProgram(any(), eq(PROGRAM_ID), eq(AccessType.WRITE))).thenReturn(true);
        when(programVersionRepository.save(any(ProgramVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(programVersionMapper.toResponse(any(ProgramVersion.class))).thenReturn(
                ProgramVersionResponse.builder().id(VERSION_ID).build());

        service.verifyProgramVersion(VERSION_ID, request(), principal());

        verify(programVersionRepository).save(any(ProgramVersion.class));
    }

    @Test
    @DisplayName("QA ngoài phạm vi chương trình bị từ chối 403")
    void verifyVersion_outOfScope_accessDenied() {
        when(programVersionRepository.findById(VERSION_ID)).thenReturn(Optional.of(version()));
        when(scopeAuthorizationService.canAccessProgram(any(), eq(PROGRAM_ID), eq(AccessType.WRITE))).thenReturn(false);

        assertThatThrownBy(() -> service.verifyProgramVersion(VERSION_ID, request(), principal()))
                .isInstanceOf(AccessDeniedException.class);
        verify(programVersionRepository, never()).save(any());
    }

    @Test
    @DisplayName("QA xác nhận PLO ghi nhận verifiedBy và verifiedAt")
    void verifyPlo_success() {
        when(programVersionRepository.findById(VERSION_ID)).thenReturn(Optional.of(version()));
        when(scopeAuthorizationService.canAccessProgram(any(), eq(PROGRAM_ID), eq(AccessType.WRITE))).thenReturn(true);
        Plo plo = Plo.builder().id(PLO_ID).programVersionId(VERSION_ID).build();
        when(ploRepository.findByIdAndProgramVersionId(PLO_ID, VERSION_ID)).thenReturn(Optional.of(plo));
        when(ploRepository.save(any(Plo.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ploMapper.toResponse(any(Plo.class))).thenReturn(PloResponse.builder().id(PLO_ID).build());

        service.verifyPlo(VERSION_ID, PLO_ID, request(), principal());

        assertThat(plo.getVerification().getStatus()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(plo.getVerification().getVerifiedBy()).isEqualTo(QA_ID);
        assertThat(plo.getVerification().getVerifiedAt()).isNotNull();
    }

    @Test
    @DisplayName("QA xác nhận PI thành công")
    void verifyPi_success() {
        when(programVersionRepository.findById(VERSION_ID)).thenReturn(Optional.of(version()));
        when(scopeAuthorizationService.canAccessProgram(any(), eq(PROGRAM_ID), eq(AccessType.WRITE))).thenReturn(true);
        Pi pi = Pi.builder().id(PI_ID).programVersionId(VERSION_ID).build();
        when(piRepository.findByIdAndProgramVersionId(PI_ID, VERSION_ID)).thenReturn(Optional.of(pi));
        when(piRepository.save(any(Pi.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(piMapper.toResponse(any(Pi.class))).thenReturn(PiResponse.builder().id(PI_ID).build());

        service.verifyPi(VERSION_ID, PI_ID, request(), principal());

        assertThat(pi.getVerification().getStatus()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(pi.getVerification().getVerifiedBy()).isEqualTo(QA_ID);
    }
}
