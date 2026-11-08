package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.document.Assessment;
import com.hcmute.lms.lmsapi.document.AssessmentItem;
import com.hcmute.lms.lmsapi.document.CourseSection;
import com.hcmute.lms.lmsapi.dto.AssessmentItemDto;
import com.hcmute.lms.lmsapi.dto.AssessmentItemsRequest;
import com.hcmute.lms.lmsapi.dto.AssessmentRequest;
import com.hcmute.lms.lmsapi.dto.AssessmentResponse;
import com.hcmute.lms.lmsapi.dto.AssessmentUpdateRequest;
import com.hcmute.lms.lmsapi.dto.CoverageResponse;
import com.hcmute.lms.lmsapi.enums.AssessmentScope;
import com.hcmute.lms.lmsapi.enums.AssessmentStatus;
import com.hcmute.lms.lmsapi.enums.DataGranularity;
import com.hcmute.lms.lmsapi.exception.AppException;
import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
import com.hcmute.lms.lmsapi.exception.ErrorCode;
import com.hcmute.lms.lmsapi.exception.ResourceNotFoundException;
import com.hcmute.lms.lmsapi.mapper.AssessmentMapper;
import com.hcmute.lms.lmsapi.repository.AssessmentRepository;
import com.hcmute.lms.lmsapi.repository.CloRepository;
import com.hcmute.lms.lmsapi.repository.CourseSectionRepository;
import com.hcmute.lms.lmsapi.repository.SyllabusRepository;
import com.hcmute.lms.lmsapi.service.AssessmentService;
import com.hcmute.lms.lmsapi.util.ObjectIdUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssessmentServiceImpl implements AssessmentService {

  private final AssessmentRepository assessmentRepository;
  private final SyllabusRepository syllabusRepository;
  private final CourseSectionRepository sectionRepository;
  private final CloRepository cloRepository;
  private final AssessmentItemValidator itemValidator;
  private final AssessmentCoverageCalculator coverageCalculator;
  private final AssessmentMapper assessmentMapper;

  @Override
  public List<AssessmentResponse> getTemplates(String syllabusId) {
    requireSyllabus(syllabusId);
    return assessmentMapper.toResponseList(
        assessmentRepository.findBySyllabusIdAndScopeOrderByCodeAsc(
            syllabusId, AssessmentScope.SYLLABUS_TEMPLATE));
  }

  @Override
  public AssessmentResponse createTemplate(String syllabusId, AssessmentRequest request) {
    requireSyllabus(syllabusId);
    if (request.getTemplateId() != null) {
      throw new BusinessRuleViolationException("templateId chỉ dùng khi tạo bài đánh giá cho lớp");
    }
    String code = request.getCode().trim();
    if (assessmentRepository.existsBySyllabusIdAndScopeAndCode(
        syllabusId, AssessmentScope.SYLLABUS_TEMPLATE, code)) {
      throw duplicateCode(code);
    }

    Assessment assessment =
        newAssessment(
            AssessmentScope.SYLLABUS_TEMPLATE, syllabusId, null, request, null, request.getItems());
    return save(assessment, code);
  }

  @Override
  public List<AssessmentResponse> getSectionAssessments(String sectionId) {
    requireSection(sectionId);
    return assessmentMapper.toResponseList(
        assessmentRepository.findBySectionIdOrderByCodeAsc(sectionId));
  }

  @Override
  public AssessmentResponse createSectionAssessment(String sectionId, AssessmentRequest request) {
    CourseSection section = requireSection(sectionId);
    String syllabusId = section.getSyllabusId();
    String code = request.getCode().trim();
    if (assessmentRepository.existsBySyllabusIdAndSectionIdAndCode(syllabusId, sectionId, code)) {
      throw duplicateCode(code);
    }

    Assessment template = null;
    List<AssessmentItemDto> itemDtos = request.getItems();
    if (request.getTemplateId() != null) {
      if (request.getItems() != null && !request.getItems().isEmpty()) {
        throw new BusinessRuleViolationException(
            "Chỉ chọn một trong hai: sao chép từ khung (templateId) hoặc tự khai báo items");
      }
      template = requireTemplateOf(syllabusId, request.getTemplateId());
      itemDtos = assessmentMapper.toCopyDtos(template.getItems());
    }

    Assessment assessment =
        newAssessment(AssessmentScope.SECTION, syllabusId, sectionId, request, template, itemDtos);
    return save(assessment, code);
  }

  @Override
  public AssessmentResponse getAssessment(String id) {
    return assessmentMapper.toResponse(requireAssessment(id));
  }

  @Override
  public AssessmentResponse updateAssessment(String id, AssessmentUpdateRequest request) {
    Assessment assessment = requireAssessment(id);
    requireEditable(assessment);

    assessment.setName(request.getName().trim());
    assessment.setType(request.getType());
    assessment.setPhase(request.getPhase());
    assessment.setScheduledAt(request.getScheduledAt());
    assessment.setTotalPoints(request.getTotalPoints());
    assessment.setWeightInCourse(request.getWeightInCourse());
    assessment.setDataGranularity(
        request.getDataGranularity() != null ? request.getDataGranularity() : DataGranularity.ITEM);
    reopenIfRejected(assessment);

    Assessment saved = assessmentRepository.save(assessment);
    log.info("Cập nhật bài đánh giá id=[{}] mã [{}]", saved.getId(), saved.getCode());
    return assessmentMapper.toResponse(saved);
  }

  @Override
  public AssessmentResponse replaceItems(String id, AssessmentItemsRequest request) {
    Assessment assessment = requireAssessment(id);
    requireEditable(assessment);

    itemValidator.validate(assessment.getSyllabusId(), request.getItems());
    List<AssessmentItem> items = assessmentMapper.toItems(request.getItems());
    assessment.setItems(items);
    assessment.setCoverageSummary(coverageCalculator.compute(items));
    reopenIfRejected(assessment);

    Assessment saved = assessmentRepository.save(assessment);
    log.info(
        "Thay items bài đánh giá id=[{}] mã [{}]: {} phần tử",
        saved.getId(),
        saved.getCode(),
        items.size());
    return assessmentMapper.toResponse(saved);
  }

  @Override
  public CoverageResponse getCoverage(String id) {
    Assessment assessment = requireAssessment(id);
    var summary = coverageCalculator.compute(assessment.getItems());

    Map<String, String> cloCodes = new HashMap<>();
    cloRepository
        .findAllById(summary.getByClo().stream().map(c -> c.getCloId()).toList())
        .forEach(clo -> cloCodes.put(clo.getId(), clo.getCode()));
    return assessmentMapper.toCoverageResponse(summary, cloCodes);
  }

  private Assessment newAssessment(
      AssessmentScope scope,
      String syllabusId,
      String sectionId,
      AssessmentRequest request,
      Assessment template,
      List<AssessmentItemDto> itemDtos) {
    itemValidator.validate(syllabusId, itemDtos);
    List<AssessmentItem> items = assessmentMapper.toItems(itemDtos);

    return Assessment.builder()
        .syllabusId(syllabusId)
        .sectionId(sectionId)
        .templateId(template != null ? template.getId() : null)
        .code(request.getCode().trim())
        .name(request.getName().trim())
        .scope(scope)
        .type(pick(request.getType(), template == null ? null : template.getType()))
        .phase(pick(request.getPhase(), template == null ? null : template.getPhase()))
        .scheduledAt(request.getScheduledAt())
        .totalPoints(
            pick(request.getTotalPoints(), template == null ? null : template.getTotalPoints()))
        .weightInCourse(
            pick(
                request.getWeightInCourse(),
                template == null ? null : template.getWeightInCourse()))
        .dataGranularity(
            pick(
                request.getDataGranularity(),
                template == null ? DataGranularity.ITEM : template.getDataGranularity()))
        .status(AssessmentStatus.DRAFT)
        .locked(false)
        .items(items)
        .coverageSummary(coverageCalculator.compute(items))
        .build();
  }

  private <T> T pick(T requested, T fallback) {
    return requested != null ? requested : fallback;
  }

  private AssessmentResponse save(Assessment assessment, String code) {
    try {
      Assessment saved = assessmentRepository.save(assessment);
      log.info(
          "Tạo bài đánh giá id=[{}] mã [{}] phạm vi [{}]",
          saved.getId(),
          saved.getCode(),
          saved.getScope());
      return assessmentMapper.toResponse(saved);
    } catch (DuplicateKeyException ex) {
      throw duplicateCode(code);
    }
  }

  /**
   * Khung luôn sửa được. Bản lớp chỉ sửa được ở DRAFT hoặc REJECTED; từ khi gửi duyệt trở đi cấu
   * trúc bị khóa để người duyệt và điểm sau này gắn đúng vào ma trận đã duyệt (US-15/AC-5).
   */
  private void requireEditable(Assessment assessment) {
    if (assessment.getScope() == AssessmentScope.SYLLABUS_TEMPLATE) {
      return;
    }
    AssessmentStatus status = assessment.getStatus();
    if (status != AssessmentStatus.DRAFT && status != AssessmentStatus.REJECTED) {
      throw new BusinessRuleViolationException(
          String.format(
              "Không thể sửa bài đánh giá ở trạng thái %s%s",
              status, assessment.isLocked() ? " (cấu trúc đã bị khóa)" : ""));
    }
  }

  /** Sửa lại bài bị từ chối thì đưa về DRAFT để gửi duyệt lại (REJECTED -> DRAFT). */
  private void reopenIfRejected(Assessment assessment) {
    if (assessment.getStatus() == AssessmentStatus.REJECTED) {
      assessment.setStatus(AssessmentStatus.DRAFT);
    }
  }

  private Assessment requireTemplateOf(String syllabusId, String templateId) {
    Assessment template =
        assessmentRepository
            .findById(templateId)
            .orElseThrow(
                () -> new BusinessRuleViolationException("Khung không tồn tại: " + templateId));
    if (template.getScope() != AssessmentScope.SYLLABUS_TEMPLATE
        || !syllabusId.equals(template.getSyllabusId())) {
      throw new BusinessRuleViolationException(
          "Khung [" + templateId + "] không phải khung của đề cương áp dụng cho lớp");
    }
    return template;
  }

  private Assessment requireAssessment(String id) {
    ObjectIdUtils.requireValid(id, "assessmentId");
    return assessmentRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Bài đánh giá không tồn tại: " + id));
  }

  private void requireSyllabus(String syllabusId) {
    ObjectIdUtils.requireValid(syllabusId, "syllabusId");
    if (!syllabusRepository.existsById(syllabusId)) {
      throw new ResourceNotFoundException("Đề cương không tồn tại: " + syllabusId);
    }
  }

  private CourseSection requireSection(String sectionId) {
    ObjectIdUtils.requireValid(sectionId, "sectionId");
    return sectionRepository
        .findById(sectionId)
        .orElseThrow(
            () -> new ResourceNotFoundException("Lớp học phần không tồn tại: " + sectionId));
  }

  private AppException duplicateCode(String code) {
    return new AppException(
        ErrorCode.CONFLICT,
        HttpStatus.CONFLICT,
        String.format("Mã bài đánh giá [%s] đã tồn tại trong phạm vi này", code));
  }
}
