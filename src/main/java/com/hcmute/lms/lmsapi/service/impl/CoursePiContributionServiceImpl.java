package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.document.CoursePiContribution;
import com.hcmute.lms.lmsapi.document.Verification;
import com.hcmute.lms.lmsapi.dto.CoursePiContributionRequest;
import com.hcmute.lms.lmsapi.dto.CoursePiContributionResponse;
import com.hcmute.lms.lmsapi.enums.VerificationStatus;
import com.hcmute.lms.lmsapi.exception.AppException;
import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
import com.hcmute.lms.lmsapi.exception.ErrorCode;
import com.hcmute.lms.lmsapi.exception.ResourceNotFoundException;
import com.hcmute.lms.lmsapi.mapper.CoursePiContributionMapper;
import com.hcmute.lms.lmsapi.repository.CoursePiContributionRepository;
import com.hcmute.lms.lmsapi.repository.CourseRepository;
import com.hcmute.lms.lmsapi.repository.PiRepository;
import com.hcmute.lms.lmsapi.repository.ProgramVersionRepository;
import com.hcmute.lms.lmsapi.service.CoursePiContributionService;
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
public class CoursePiContributionServiceImpl implements CoursePiContributionService {

    private final CoursePiContributionRepository coursePiContributionRepository;
    private final ProgramVersionRepository programVersionRepository;
    private final PiRepository piRepository;
    private final CourseRepository courseRepository;
    private final CoursePiContributionMapper coursePiContributionMapper;

    @Override
    public List<CoursePiContributionResponse> getContributions(String programVersionId) {
        requireProgramVersion(programVersionId);
        return coursePiContributionMapper.toResponseList(
                coursePiContributionRepository.findByProgramVersionIdOrderByCourseIdAscPiIdAsc(programVersionId));
    }

    @Override
    public CoursePiContributionResponse createContribution(String programVersionId, CoursePiContributionRequest request) {
        requireProgramVersion(programVersionId);

        if (piRepository.findByIdAndProgramVersionId(request.getPiId(), programVersionId).isEmpty()) {
            throw new BusinessRuleViolationException("CONTRIBUTION_PI_MISMATCH",
                    "PI không tồn tại trong phiên bản chương trình đào tạo: " + request.getPiId());
        }
        if (!courseRepository.existsById(request.getCourseId())) {
            throw new BusinessRuleViolationException("CONTRIBUTION_COURSE_NOT_FOUND",
                    "Học phần không tồn tại: " + request.getCourseId());
        }
        if (coursePiContributionRepository.existsByProgramVersionIdAndCourseIdAndPiId(
                programVersionId, request.getCourseId(), request.getPiId())) {
            throw conflict();
        }

        CoursePiContribution contribution = CoursePiContribution.builder()
                .programVersionId(programVersionId)
                .courseId(request.getCourseId())
                .piId(request.getPiId())
                .contribution(request.getContribution())
                .verification(Verification.builder().status(VerificationStatus.NEEDS_CLARIFICATION).build())
                .build();

        try {
            contribution = coursePiContributionRepository.save(contribution);
        } catch (DuplicateKeyException ex) {
            log.warn("Duplicate course PI contribution: {} - {} - {}",
                    programVersionId, request.getCourseId(), request.getPiId());
            throw conflict();
        }
        return coursePiContributionMapper.toResponse(contribution);
    }

    @Override
    public void deleteContribution(String programVersionId, String id) {
        requireProgramVersion(programVersionId);
        ObjectIdUtils.requireValid(id, "id");
        CoursePiContribution contribution = coursePiContributionRepository
                .findByIdAndProgramVersionId(id, programVersionId)
                .orElseThrow(() -> new ResourceNotFoundException("CoursePiContribution", "id", id));
        coursePiContributionRepository.delete(contribution);
    }

    private void requireProgramVersion(String programVersionId) {
        ObjectIdUtils.requireValid(programVersionId, "programVersionId");
        if (!programVersionRepository.existsById(programVersionId)) {
            throw new ResourceNotFoundException("ProgramVersion", "id", programVersionId);
        }
    }

    private AppException conflict() {
        return new AppException(ErrorCode.CONFLICT, HttpStatus.CONFLICT,
                "Học phần đã được gán với PI này trong phiên bản chương trình đào tạo");
    }
}
