package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.document.Pi;
import com.hcmute.lms.lmsapi.document.Plo;
import com.hcmute.lms.lmsapi.document.ProgramVersion;
import com.hcmute.lms.lmsapi.dto.PiRequest;
import com.hcmute.lms.lmsapi.dto.PiResponse;
import com.hcmute.lms.lmsapi.enums.ProgramVersionStatus;
import com.hcmute.lms.lmsapi.exception.AppException;
import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
import com.hcmute.lms.lmsapi.exception.ErrorCode;
import com.hcmute.lms.lmsapi.mapper.PiMapper;
import com.hcmute.lms.lmsapi.repository.CoursePiContributionRepository;
import com.hcmute.lms.lmsapi.repository.PiRepository;
import com.hcmute.lms.lmsapi.repository.PloRepository;
import com.hcmute.lms.lmsapi.repository.ProgramReferenceRepository;
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
@DisplayName("PiServiceImpl Unit Tests (F02 / US-06)")
class PiServiceImplTest {

    private static final String VERSION_ID = "671dcba5f123456789abc002";
    private static final String OTHER_VERSION_ID = "671dcba5f123456789abc009";
    private static final String PLO_ID = "671dcba5f123456789abc003";
    private static final String PI_ID = "671dcba5f123456789abc004";

    @Mock
    private PiRepository piRepository;

    @Mock
    private PloRepository ploRepository;

    @Mock
    private ProgramVersionRepository programVersionRepository;

    @Mock
    private CoursePiContributionRepository coursePiContributionRepository;

    @Mock
    private ProgramReferenceRepository programReferenceRepository;

    @Mock
    private PiMapper piMapper;

    @InjectMocks
    private PiServiceImpl service;

    private ProgramVersion version(ProgramVersionStatus status) {
        return ProgramVersion.builder().id(VERSION_ID).status(status).build();
    }

    private PiRequest request() {
        return PiRequest.builder()
                .ploId(PLO_ID)
                .code("PI1.1")
                .displayOrder(1)
                .contentShort("Nội dung")
                .capabilityLevelRaw(3)
                .targetPassRate(0.8)
                .build();
    }

    @Test
    @DisplayName("Tạo PI với PLO không thuộc phiên bản trả 422")
    void create_ploNotInVersion_businessRule() {
        when(programVersionRepository.findById(VERSION_ID))
                .thenReturn(Optional.of(version(ProgramVersionStatus.DRAFT)));
        when(ploRepository.findByIdAndProgramVersionId(PLO_ID, VERSION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createPi(VERSION_ID, request()))
                .isInstanceOf(BusinessRuleViolationException.class);
        verify(piRepository, never()).save(any());
    }

    @Test
    @DisplayName("Tạo PI thành công")
    void create_success() {
        when(programVersionRepository.findById(VERSION_ID))
                .thenReturn(Optional.of(version(ProgramVersionStatus.DRAFT)));
        when(ploRepository.findByIdAndProgramVersionId(PLO_ID, VERSION_ID))
                .thenReturn(Optional.of(Plo.builder().id(PLO_ID).build()));
        when(piRepository.existsByProgramVersionIdAndCode(VERSION_ID, "PI1.1")).thenReturn(false);
        when(piRepository.save(any(Pi.class))).thenAnswer(invocation -> {
            Pi pi = invocation.getArgument(0);
            pi.setId(PI_ID);
            return pi;
        });
        when(piMapper.toResponse(any(Pi.class))).thenReturn(PiResponse.builder().id(PI_ID).code("PI1.1").build());

        PiResponse response = service.createPi(VERSION_ID, request());

        assertThat(response.getId()).isEqualTo(PI_ID);
        verify(piRepository).save(any(Pi.class));
    }

    @Test
    @DisplayName("Tạo PI trùng mã trả 409")
    void create_duplicate_conflict() {
        when(programVersionRepository.findById(VERSION_ID))
                .thenReturn(Optional.of(version(ProgramVersionStatus.DRAFT)));
        when(ploRepository.findByIdAndProgramVersionId(PLO_ID, VERSION_ID))
                .thenReturn(Optional.of(Plo.builder().id(PLO_ID).build()));
        when(piRepository.existsByProgramVersionIdAndCode(VERSION_ID, "PI1.1")).thenReturn(true);

        assertThatThrownBy(() -> service.createPi(VERSION_ID, request()))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    @DisplayName("Sửa PI khi phiên bản ACTIVE trả 422")
    void update_activeVersion_businessRule() {
        when(programVersionRepository.findById(VERSION_ID))
                .thenReturn(Optional.of(version(ProgramVersionStatus.ACTIVE)));

        assertThatThrownBy(() -> service.updatePi(VERSION_ID, PI_ID, request()))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @DisplayName("Xóa PI còn học phần tham chiếu trả 422")
    void delete_hasContribution_businessRule() {
        Pi pi = Pi.builder().id(PI_ID).programVersionId(VERSION_ID).build();
        when(programVersionRepository.findById(VERSION_ID))
                .thenReturn(Optional.of(version(ProgramVersionStatus.DRAFT)));
        when(piRepository.findByIdAndProgramVersionId(PI_ID, VERSION_ID)).thenReturn(Optional.of(pi));
        when(coursePiContributionRepository.countByPiId(PI_ID)).thenReturn(1L);

        assertThatThrownBy(() -> service.deletePi(VERSION_ID, PI_ID))
                .isInstanceOf(BusinessRuleViolationException.class);
        verify(piRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Xóa PI còn CLO/câu hỏi tham chiếu trả 422")
    void delete_hasQuestionReference_businessRule() {
        Pi pi = Pi.builder().id(PI_ID).programVersionId(VERSION_ID).build();
        when(programVersionRepository.findById(VERSION_ID))
                .thenReturn(Optional.of(version(ProgramVersionStatus.DRAFT)));
        when(piRepository.findByIdAndProgramVersionId(PI_ID, VERSION_ID)).thenReturn(Optional.of(pi));
        when(coursePiContributionRepository.countByPiId(PI_ID)).thenReturn(0L);
        when(programReferenceRepository.existsPiReference(PI_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.deletePi(VERSION_ID, PI_ID))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @DisplayName("Xóa PI không còn tham chiếu thành công")
    void delete_success() {
        Pi pi = Pi.builder().id(PI_ID).programVersionId(VERSION_ID).build();
        when(programVersionRepository.findById(VERSION_ID))
                .thenReturn(Optional.of(version(ProgramVersionStatus.DRAFT)));
        when(piRepository.findByIdAndProgramVersionId(PI_ID, VERSION_ID)).thenReturn(Optional.of(pi));
        when(coursePiContributionRepository.countByPiId(PI_ID)).thenReturn(0L);
        when(programReferenceRepository.existsPiReference(PI_ID)).thenReturn(false);

        service.deletePi(VERSION_ID, PI_ID);

        verify(piRepository).delete(pi);
    }

    @Test
    @DisplayName("Xóa PI không bao giờ nhận diện nhầm phiên bản khác")
    void delete_otherVersion_notFound() {
        when(programVersionRepository.findById(OTHER_VERSION_ID))
                .thenReturn(Optional.of(ProgramVersion.builder().id(OTHER_VERSION_ID).status(ProgramVersionStatus.DRAFT).build()));
        when(piRepository.findByIdAndProgramVersionId(PI_ID, OTHER_VERSION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deletePi(OTHER_VERSION_ID, PI_ID))
                .isInstanceOf(com.hcmute.lms.lmsapi.exception.ResourceNotFoundException.class);
    }
}
