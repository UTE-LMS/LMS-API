package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.document.Assessment;
import com.hcmute.lms.lmsapi.document.AssessmentCloWeight;
import com.hcmute.lms.lmsapi.document.AssessmentItem;
import com.hcmute.lms.lmsapi.document.AuditEntity;
import com.hcmute.lms.lmsapi.document.CourseSection;
import com.hcmute.lms.lmsapi.document.Enrollment;
import com.hcmute.lms.lmsapi.document.OutcomeMapping;
import com.hcmute.lms.lmsapi.document.Student;
import com.hcmute.lms.lmsapi.dto.AssessmentItemDto;
import com.hcmute.lms.lmsapi.dto.AssessmentItemsRequest;
import com.hcmute.lms.lmsapi.dto.AssessmentRequest;
import com.hcmute.lms.lmsapi.dto.AssessmentResponse;
import com.hcmute.lms.lmsapi.dto.AssessmentScoreTemplate;
import com.hcmute.lms.lmsapi.dto.AssessmentUpdateRequest;
import com.hcmute.lms.lmsapi.dto.CoverageResponse;
import com.hcmute.lms.lmsapi.enums.AssessmentScope;
import com.hcmute.lms.lmsapi.enums.AssessmentStatus;
import com.hcmute.lms.lmsapi.enums.AuditAction;
import com.hcmute.lms.lmsapi.enums.EnrollmentStatus;
import com.hcmute.lms.lmsapi.exception.AppException;
import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
import com.hcmute.lms.lmsapi.exception.ErrorCode;
import com.hcmute.lms.lmsapi.exception.ResourceNotFoundException;
import com.hcmute.lms.lmsapi.mapper.AssessmentMapper;
import com.hcmute.lms.lmsapi.repository.AssessmentRepository;
import com.hcmute.lms.lmsapi.repository.CloRepository;
import com.hcmute.lms.lmsapi.repository.CourseSectionRepository;
import com.hcmute.lms.lmsapi.repository.EnrollmentRepository;
import com.hcmute.lms.lmsapi.repository.ScoreRepository;
import com.hcmute.lms.lmsapi.repository.StudentRepository;
import com.hcmute.lms.lmsapi.repository.SyllabusRepository;
import com.hcmute.lms.lmsapi.service.AssessmentService;
import com.hcmute.lms.lmsapi.service.AuditService;
import com.hcmute.lms.lmsapi.util.ObjectIdUtils;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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
  private final ScoreRepository scoreRepository;
  private final EnrollmentRepository enrollmentRepository;
  private final StudentRepository studentRepository;
  private final AssessmentItemValidator itemValidator;
  private final AssessmentCoverageCalculator coverageCalculator;
  private final AssessmentMapper assessmentMapper;
  private final AuditService auditService;

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

    if (request.getCloWeights() != null) {
      itemValidator.validateCloWeights(
          assessment.getSyllabusId(),
          assessment.getSectionId(),
          assessment.getId(),
          request.getCloWeights(),
          assessmentMapper.toCopyDtos(assessment.getItems()));
      assessment.setCloWeights(assessmentMapper.toCloWeights(request.getCloWeights()));
    }

    Assessment saved = assessmentRepository.save(assessment);
    log.info("Cập nhật bài đánh giá id=[{}] mã [{}]", saved.getId(), saved.getCode());
    return assessmentMapper.toResponse(saved);
  }

  @Override
  public AssessmentResponse replaceItems(String id, AssessmentItemsRequest request) {
    Assessment assessment = requireAssessment(id);
    requireEditable(assessment);

    itemValidator.validate(assessment.getSyllabusId(), request.getItems());
    if (assessment.getCloWeights() != null && !assessment.getCloWeights().isEmpty()) {
      itemValidator.validateCloWeights(
          assessment.getSyllabusId(),
          assessment.getSectionId(),
          assessment.getId(),
          assessmentMapper.toCloWeightDtos(assessment.getCloWeights()),
          request.getItems());
    }

    List<AssessmentItem> items = assessmentMapper.toItems(request.getItems());
    assessment.setItems(items);
    assessment.setCoverageSummary(coverageCalculator.compute(items));

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

  @Override
  public AssessmentResponse lock(String id, String actorId) {
    Assessment assessment = requireAssessment(id);
    if (assessment.getScope() != AssessmentScope.SECTION) {
      throw new BusinessRuleViolationException(
          "Chỉ bài đánh giá bản lớp (SECTION) mới thực hiện khóa ma trận đề");
    }
    if (assessment.getStatus() != AssessmentStatus.DRAFT) {
      throw new BusinessRuleViolationException(
          "Chỉ bài đánh giá ở trạng thái DRAFT mới có thể khóa");
    }
    if (assessment.getItems() == null || assessment.getItems().isEmpty()) {
      throw new BusinessRuleViolationException(
          "Bài đánh giá phải có ít nhất 1 câu hỏi (item) trước khi khóa");
    }

    itemValidator.validateCloWeights(
        assessment.getSyllabusId(),
        assessment.getSectionId(),
        assessment.getId(),
        assessmentMapper.toCloWeightDtos(assessment.getCloWeights()),
        assessmentMapper.toCopyDtos(assessment.getItems()));

    assessment.setStatus(AssessmentStatus.LOCKED);
    assessment.setLocked(true);
    Assessment saved = assessmentRepository.save(assessment);
    log.info(
        "Khóa bài đánh giá id=[{}] mã [{}] bởi actor=[{}]",
        saved.getId(),
        saved.getCode(),
        actorId);

    auditService.record(
        AuditAction.ASSESSMENT_LOCK,
        AuditEntity.builder().collection("assessments").id(saved.getId()).build(),
        null,
        saved,
        "Khóa ma trận bài đánh giá",
        actorId,
        null);

    return assessmentMapper.toResponse(saved);
  }

  @Override
  public AssessmentResponse unlock(String id, String actorId) {
    Assessment assessment = requireAssessment(id);
    if (assessment.getStatus() != AssessmentStatus.LOCKED) {
      throw new BusinessRuleViolationException(
          "Chỉ bài đánh giá ở trạng thái LOCKED mới có thể mở khóa");
    }
    if (scoreRepository.existsByAssessmentId(id)) {
      throw new AppException(
          ErrorCode.CONFLICT,
          HttpStatus.CONFLICT,
          "Không thể mở khóa bài đánh giá đã có điểm nhập trong hệ thống");
    }

    assessment.setStatus(AssessmentStatus.DRAFT);
    assessment.setLocked(false);
    Assessment saved = assessmentRepository.save(assessment);
    log.info(
        "Mở khóa bài đánh giá id=[{}] mã [{}] bởi actor=[{}]",
        saved.getId(),
        saved.getCode(),
        actorId);

    auditService.record(
        AuditAction.ASSESSMENT_UNLOCK,
        AuditEntity.builder().collection("assessments").id(saved.getId()).build(),
        null,
        saved,
        "Mở khóa ma trận bài đánh giá",
        actorId,
        null);

    return assessmentMapper.toResponse(saved);
  }

  @Override
  public void deleteAssessment(String id, String actorId) {
    Assessment assessment = requireAssessment(id);
    if (assessment.getStatus() != AssessmentStatus.DRAFT) {
      throw new BusinessRuleViolationException(
          "Chỉ bài đánh giá ở trạng thái DRAFT mới có thể xóa");
    }
    if (scoreRepository.existsByAssessmentId(id)) {
      throw new AppException(
          ErrorCode.CONFLICT, HttpStatus.CONFLICT, "Không thể xóa bài đánh giá đã có điểm");
    }
    assessmentRepository.delete(assessment);
    log.info(
        "Xóa bài đánh giá id=[{}] mã [{}] bởi actor=[{}]",
        assessment.getId(),
        assessment.getCode(),
        actorId);
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

    List<AssessmentCloWeight> cloWeights = null;
    if (request.getCloWeights() != null) {
      itemValidator.validateCloWeights(
          syllabusId, sectionId, null, request.getCloWeights(), itemDtos);
      cloWeights = assessmentMapper.toCloWeights(request.getCloWeights());
    } else if (template != null && template.getCloWeights() != null) {
      cloWeights = template.getCloWeights();
    }

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
        .status(AssessmentStatus.DRAFT)
        .locked(false)
        .cloWeights(cloWeights != null ? cloWeights : List.of())
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

  private void requireEditable(Assessment assessment) {
    if (assessment.getScope() == AssessmentScope.SYLLABUS_TEMPLATE) {
      return;
    }
    AssessmentStatus status = assessment.getStatus();
    if (status != AssessmentStatus.DRAFT) {
      throw new BusinessRuleViolationException(
          String.format(
              "Không thể sửa bài đánh giá ở trạng thái %s%s",
              status, assessment.isLocked() ? " (cấu trúc đã bị khóa)" : ""));
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

  @Override
  public AssessmentScoreTemplate generateScoreTemplateCsv(String id) {
    Assessment assessment = requireAssessment(id);
    if (assessment.getScope() != AssessmentScope.SECTION || assessment.getSectionId() == null) {
      throw new BusinessRuleViolationException(
          "Chỉ xuất tệp CSV mẫu cho bài đánh giá của lớp học phần");
    }
    if (assessment.getStatus() != AssessmentStatus.LOCKED
        && assessment.getStatus() != AssessmentStatus.SCORE_ENTERED) {
      throw new BusinessRuleViolationException(
          "Chỉ xuất tệp CSV mẫu khi bài đánh giá ở trạng thái LOCKED hoặc SCORE_ENTERED, hiện là "
              + assessment.getStatus());
    }
    List<AssessmentItem> items = assessment.getItems();
    if (items == null || items.isEmpty()) {
      throw new BusinessRuleViolationException(
          "Bài đánh giá chưa có câu hỏi nào để xuất tệp CSV mẫu");
    }
    long totalMappings =
        items.stream()
            .mapToLong(it -> it.getOutcomeMappings() == null ? 0 : it.getOutcomeMappings().size())
            .sum();
    if (totalMappings == 0) {
      throw new BusinessRuleViolationException(
          "Bài đánh giá chưa có ma trận câu hỏi - CLO để xuất tệp CSV mẫu");
    }

    Map<String, String> cloCodeMap = itemValidator.resolveCloCodeMap(items);

    List<Enrollment> enrollments =
        enrollmentRepository.findBySectionIdAndStatus(
            assessment.getSectionId(), EnrollmentStatus.ENROLLED);
    Set<String> studentIds =
        enrollments.stream().map(Enrollment::getStudentId).collect(Collectors.toSet());
    List<Student> students = new ArrayList<>(studentRepository.findAllById(studentIds));
    students.sort(
        Comparator.comparing(Student::getStudentRef, Comparator.nullsLast(String::compareTo)));

    StringBuilder sb = new StringBuilder();
    sb.append('\uFEFF'); // UTF-8 BOM
    sb.append("MaSV,HoTen");
    for (AssessmentItem item : items) {
      if (item.getOutcomeMappings() == null) {
        continue;
      }
      for (OutcomeMapping mapping : item.getOutcomeMappings()) {
        String cloCode = cloCodeMap.getOrDefault(mapping.getCloId(), mapping.getCloId());
        String maxScoreStr = formatScore(mapping.getMaxScore());
        String colHeader = item.getCode() + " - " + cloCode + " (Max: " + maxScoreStr + ")";
        sb.append(',').append(escapeCsv(colHeader));
      }
    }
    sb.append("\r\n");

    int scoreColCount = (int) totalMappings;
    for (Student student : students) {
      sb.append(escapeCsv(student.getStudentRef() != null ? student.getStudentRef() : ""));
      sb.append(',');
      sb.append(escapeCsv(student.getFullName() != null ? student.getFullName() : ""));
      for (int i = 0; i < scoreColCount; i++) {
        sb.append(',');
      }
      sb.append("\r\n");
    }

    byte[] content = sb.toString().getBytes(StandardCharsets.UTF_8);
    String fileName = sanitizeFileName(assessment.getCode()) + "-scores-template.csv";
    return new AssessmentScoreTemplate(fileName, content);
  }

  private String formatScore(Double score) {
    if (score == null) {
      return "0";
    }
    return BigDecimal.valueOf(score).stripTrailingZeros().toPlainString();
  }

  private String escapeCsv(String value) {
    if (value == null) {
      return "";
    }
    if (value.contains(",")
        || value.contains("\"")
        || value.contains("\n")
        || value.contains("\r")) {
      return "\"" + value.replace("\"", "\"\"") + "\"";
    }
    return value;
  }

  private String sanitizeFileName(String code) {
    if (code == null || code.isBlank()) {
      return "assessment";
    }
    return code.replaceAll("[^a-zA-Z0-9-_]", "_");
  }
}
