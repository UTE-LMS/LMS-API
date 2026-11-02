package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.document.CoursePiContribution;
import com.hcmute.lms.lmsapi.document.Pi;
import com.hcmute.lms.lmsapi.dto.CoursePiContributionRequest;
import com.hcmute.lms.lmsapi.dto.CoursePiContributionResponse;
import com.hcmute.lms.lmsapi.enums.PiContributionType;
import com.hcmute.lms.lmsapi.exception.AppException;
import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
import com.hcmute.lms.lmsapi.exception.ErrorCode;
import com.hcmute.lms.lmsapi.mapper.CoursePiContributionMapper;
import com.hcmute.lms.lmsapi.repository.CoursePiContributionRepository;
import com.hcmute.lms.lmsapi.repository.CourseRepository;
import com.hcmute.lms.lmsapi.repository.PiRepository;
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
@DisplayName("CoursePiContributionServiceImpl Unit Tests (F02 / US-07)")
class CoursePiContributionServiceImplTest {

    private static final String VERSION_ID = "671dcba5f123456789abc002";
    private static final String COURSE_ID = "671dcba5f123456789abc005";
    private static final String PI_ID = "671dcba5f123456789abc004";
    private static final String CONTRIBUTION_ID = "671dcba5f123456789abc006";

    @Mock
    private CoursePiContributionRepository contributionRepository;

    @Mock
    private ProgramVersionRepository programVersionRepository;

    @Mock
    private PiRepository piRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CoursePiContributionMapper mapper;

    @InjectMocks
    private CoursePiContributionServiceImpl service;

    private CoursePiContributionRequest request() {
        return CoursePiContributionRequest.builder()
                .courseId(COURSE_ID)
                .piId(PI_ID)
                .contribution(PiContributionType.R)
                .build();
    }

    @Test
    @DisplayName("Gán học phần với PI thành công")
    void create_success() {
        when(programVersionRepository.existsById(VERSION_ID)).thenReturn(true);
        when(piRepository.findByIdAndProgramVersionId(PI_ID, VERSION_ID))
                .thenReturn(Optional.of(Pi.builder().id(PI_ID).build()));
        when(courseRepository.existsById(COURSE_ID)).thenReturn(true);
        when(contributionRepository.existsByProgramVersionIdAndCourseIdAndPiId(VERSION_ID, COURSE_ID, PI_ID))
                .thenReturn(false);
        when(contributionRepository.save(any(CoursePiContribution.class))).thenAnswer(invocation -> {
            CoursePiContribution contribution = invocation.getArgument(0);
            contribution.setId(CONTRIBUTION_ID);
            return contribution;
        });
        when(mapper.toResponse(any(CoursePiContribution.class))).thenReturn(
                CoursePiContributionResponse.builder().id(CONTRIBUTION_ID).contribution(PiContributionType.R).build());

        CoursePiContributionResponse response = service.createContribution(VERSION_ID, request());

        assertThat(response.getId()).isEqualTo(CONTRIBUTION_ID);
        verify(contributionRepository).save(any(CoursePiContribution.class));
    }

    @Test
    @DisplayName("Gán trùng bộ ba (phiên bản, học phần, PI) trả 409")
    void create_duplicate_conflict() {
        when(programVersionRepository.existsById(VERSION_ID)).thenReturn(true);
        when(piRepository.findByIdAndProgramVersionId(PI_ID, VERSION_ID))
                .thenReturn(Optional.of(Pi.builder().id(PI_ID).build()));
        when(courseRepository.existsById(COURSE_ID)).thenReturn(true);
        when(contributionRepository.existsByProgramVersionIdAndCourseIdAndPiId(VERSION_ID, COURSE_ID, PI_ID))
                .thenReturn(true);

        assertThatThrownBy(() -> service.createContribution(VERSION_ID, request()))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    @DisplayName("Học phần không tồn tại trả 422")
    void create_courseMissing_businessRule() {
        when(programVersionRepository.existsById(VERSION_ID)).thenReturn(true);
        when(piRepository.findByIdAndProgramVersionId(PI_ID, VERSION_ID))
                .thenReturn(Optional.of(Pi.builder().id(PI_ID).build()));
        when(courseRepository.existsById(COURSE_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.createContribution(VERSION_ID, request()))
                .isInstanceOf(BusinessRuleViolationException.class);
        verify(contributionRepository, never()).save(any());
    }

    @Test
    @DisplayName("PI không thuộc phiên bản trả 422")
    void create_piMissing_businessRule() {
        when(programVersionRepository.existsById(VERSION_ID)).thenReturn(true);
        when(piRepository.findByIdAndProgramVersionId(PI_ID, VERSION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createContribution(VERSION_ID, request()))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @DisplayName("Xóa đóng góp thành công")
    void delete_success() {
        CoursePiContribution contribution = CoursePiContribution.builder().id(CONTRIBUTION_ID).build();
        when(programVersionRepository.existsById(VERSION_ID)).thenReturn(true);
        when(contributionRepository.findByIdAndProgramVersionId(CONTRIBUTION_ID, VERSION_ID))
                .thenReturn(Optional.of(contribution));

        service.deleteContribution(VERSION_ID, CONTRIBUTION_ID);

        verify(contributionRepository).delete(contribution);
    }
}
