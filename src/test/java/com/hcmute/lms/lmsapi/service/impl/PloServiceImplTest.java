package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.document.Plo;
import com.hcmute.lms.lmsapi.document.ProgramVersion;
import com.hcmute.lms.lmsapi.dto.PloRequest;
import com.hcmute.lms.lmsapi.dto.PloResponse;
import com.hcmute.lms.lmsapi.enums.PloCategory;
import com.hcmute.lms.lmsapi.enums.ProgramVersionStatus;
import com.hcmute.lms.lmsapi.exception.AppException;
import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
import com.hcmute.lms.lmsapi.exception.ErrorCode;
import com.hcmute.lms.lmsapi.mapper.PloMapper;
import com.hcmute.lms.lmsapi.repository.PiRepository;
import com.hcmute.lms.lmsapi.repository.PloRepository;
import com.hcmute.lms.lmsapi.repository.ProgramVersionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PloServiceImpl Unit Tests (F02 / US-06)")
class PloServiceImplTest {

    private static final String VERSION_ID = "671dcba5f123456789abc002";
    private static final String PLO_ID = "671dcba5f123456789abc003";

    @Mock
    private PloRepository ploRepository;

    @Mock
    private PiRepository piRepository;

    @Mock
    private ProgramVersionRepository programVersionRepository;

    @Mock
    private PloMapper ploMapper;

    @InjectMocks
    private PloServiceImpl service;

    private ProgramVersion version(ProgramVersionStatus status) {
        return ProgramVersion.builder().id(VERSION_ID).status(status).build();
    }

    private PloRequest request(String code) {
        return PloRequest.builder()
                .code(code)
                .displayOrder(1)
                .contentSummary("Nội dung")
                .capabilityLevelRaw(3)
                .build();
    }

    @Test
    @DisplayName("Tạo PLO mặc định UNSPECIFIED và NEEDS_CLARIFICATION")
    void create_success_defaults() {
        when(programVersionRepository.findById(VERSION_ID))
                .thenReturn(Optional.of(version(ProgramVersionStatus.DRAFT)));
        when(ploRepository.existsByProgramVersionIdAndCode(VERSION_ID, "PLO1")).thenReturn(false);
        when(ploRepository.save(any(Plo.class))).thenAnswer(invocation -> {
            Plo plo = invocation.getArgument(0);
            plo.setId(PLO_ID);
            return plo;
        });
        when(ploMapper.toResponse(any(Plo.class))).thenAnswer(invocation -> {
            Plo plo = invocation.getArgument(0);
            return PloResponse.builder().id(plo.getId()).code(plo.getCode()).category(plo.getCategory()).build();
        });

        PloResponse response = service.createPlo(VERSION_ID, request("PLO1"));

        assertThat(response.getCategory()).isEqualTo(PloCategory.UNSPECIFIED);
        verify(ploRepository).save(any(Plo.class));
    }

    @Test
    @DisplayName("Tạo PLO trùng mã trả 409")
    void create_duplicate_conflict() {
        when(programVersionRepository.findById(VERSION_ID))
                .thenReturn(Optional.of(version(ProgramVersionStatus.DRAFT)));
        when(ploRepository.existsByProgramVersionIdAndCode(VERSION_ID, "PLO1")).thenReturn(true);

        assertThatThrownBy(() -> service.createPlo(VERSION_ID, request("PLO1")))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    @DisplayName("Sửa PLO khi phiên bản ACTIVE trả 422")
    void update_activeVersion_businessRule() {
        when(programVersionRepository.findById(VERSION_ID))
                .thenReturn(Optional.of(version(ProgramVersionStatus.ACTIVE)));

        assertThatThrownBy(() -> service.updatePlo(VERSION_ID, PLO_ID, request("PLO1")))
                .isInstanceOf(BusinessRuleViolationException.class);
        verify(ploRepository, never()).save(any());
    }

    @Test
    @DisplayName("Xóa PLO còn PI con trả 422")
    void delete_hasPis_businessRule() {
        Plo plo = Plo.builder().id(PLO_ID).programVersionId(VERSION_ID).code("PLO1").build();
        when(programVersionRepository.findById(VERSION_ID))
                .thenReturn(Optional.of(version(ProgramVersionStatus.DRAFT)));
        when(ploRepository.findByIdAndProgramVersionId(PLO_ID, VERSION_ID)).thenReturn(Optional.of(plo));
        when(piRepository.countByPloId(PLO_ID)).thenReturn(1L);

        assertThatThrownBy(() -> service.deletePlo(VERSION_ID, PLO_ID))
                .isInstanceOf(BusinessRuleViolationException.class);
        verify(ploRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Xóa PLO không còn PI con thành công")
    void delete_success() {
        Plo plo = Plo.builder().id(PLO_ID).programVersionId(VERSION_ID).code("PLO1").build();
        when(programVersionRepository.findById(VERSION_ID))
                .thenReturn(Optional.of(version(ProgramVersionStatus.DRAFT)));
        when(ploRepository.findByIdAndProgramVersionId(PLO_ID, VERSION_ID)).thenReturn(Optional.of(plo));
        when(piRepository.countByPloId(PLO_ID)).thenReturn(0L);

        service.deletePlo(VERSION_ID, PLO_ID);

        verify(ploRepository).delete(plo);
    }
}
