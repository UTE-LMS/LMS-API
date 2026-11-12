package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.document.Assessment;
import com.hcmute.lms.lmsapi.document.AssessmentItem;
import com.hcmute.lms.lmsapi.document.AuditEntity;
import com.hcmute.lms.lmsapi.document.CourseSection;
import com.hcmute.lms.lmsapi.document.Enrollment;
import com.hcmute.lms.lmsapi.document.ImportBatch;
import com.hcmute.lms.lmsapi.document.ImportColumnMapping;
import com.hcmute.lms.lmsapi.document.ImportRowError;
import com.hcmute.lms.lmsapi.document.ImportStats;
import com.hcmute.lms.lmsapi.document.OutcomeMapping;
import com.hcmute.lms.lmsapi.document.Score;
import com.hcmute.lms.lmsapi.document.Term;
import com.hcmute.lms.lmsapi.dto.ImportBatchCreateRequest;
import com.hcmute.lms.lmsapi.dto.ImportBatchResponse;
import com.hcmute.lms.lmsapi.dto.ImportColumnMappingDto;
import com.hcmute.lms.lmsapi.dto.ImportRowErrorResponse;
import com.hcmute.lms.lmsapi.dto.PageResponse;
import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import com.hcmute.lms.lmsapi.enums.AssessmentScope;
import com.hcmute.lms.lmsapi.enums.AssessmentStatus;
import com.hcmute.lms.lmsapi.enums.AuditAction;
import com.hcmute.lms.lmsapi.enums.EnrollmentStatus;
import com.hcmute.lms.lmsapi.enums.ImportBatchStatus;
import com.hcmute.lms.lmsapi.enums.ImportSourceSystem;
import com.hcmute.lms.lmsapi.enums.ReimportPolicy;
import com.hcmute.lms.lmsapi.enums.SectionStatus;
import com.hcmute.lms.lmsapi.enums.TermStatus;
import com.hcmute.lms.lmsapi.exception.AppException;
import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
import com.hcmute.lms.lmsapi.exception.ErrorCode;
import com.hcmute.lms.lmsapi.exception.ResourceNotFoundException;
import com.hcmute.lms.lmsapi.mapper.ImportBatchMapper;
import com.hcmute.lms.lmsapi.repository.AssessmentRepository;
import com.hcmute.lms.lmsapi.repository.CourseSectionRepository;
import com.hcmute.lms.lmsapi.repository.EnrollmentRepository;
import com.hcmute.lms.lmsapi.repository.ImportBatchRepository;
import com.hcmute.lms.lmsapi.repository.ImportRowErrorRepository;
import com.hcmute.lms.lmsapi.repository.ScoreRepository;
import com.hcmute.lms.lmsapi.repository.StudentRepository;
import com.hcmute.lms.lmsapi.repository.TermRepository;
import com.hcmute.lms.lmsapi.service.AuditService;
import com.hcmute.lms.lmsapi.service.ImportBatchService;
import com.hcmute.lms.lmsapi.service.ImportFileStorage;
import com.hcmute.lms.lmsapi.util.ChecksumUtils;
import com.hcmute.lms.lmsapi.util.ObjectIdUtils;
import com.hcmute.lms.lmsapi.util.ScoreCsvParser;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImportBatchServiceImpl implements ImportBatchService {

  public static final String DEFAULT_STUDENT_COLUMN = "studentRef";
  private static final String BATCH_COLLECTION = "import_batches";

  private final CourseSectionRepository sectionRepository;
  private final AssessmentRepository assessmentRepository;
  private final TermRepository termRepository;
  private final EnrollmentRepository enrollmentRepository;
  private final StudentRepository studentRepository;
  private final ImportBatchRepository batchRepository;
  private final ImportRowErrorRepository rowErrorRepository;
  private final ScoreRepository scoreRepository;
  private final ImportFileStorage fileStorage;
  private final ScoreRowValidator rowValidator;
  private final ImportBatchMapper batchMapper;
  private final AuditService auditService;

  @Override
  public ImportBatchResponse createBatch(
      String sectionId,
      String assessmentId,
      String fileName,
      byte[] content,
      ImportBatchCreateRequest request,
      UserPrincipal actor) {
    CourseSection section = requireSection(sectionId);
    Assessment assessment = requireSectionAssessment(section, assessmentId);
    requireScoreEntryAllowed(section, assessment);
    ImportSourceSystem source = resolveSource(request);
    if (source == ImportSourceSystem.EXCEL) {
      throw new BusinessRuleViolationException("Định dạng Excel chưa được hỗ trợ (chờ ADR)");
    }
    ReimportPolicy policy = resolveReimportPolicy(request);

    String checksum = ChecksumUtils.sha256Hex(content);
    if (batchRepository.existsBySectionIdAndAssessmentIdAndFileChecksumAndStatus(
        section.getId(), assessment.getId(), checksum, ImportBatchStatus.CONFIRMED)) {
      throw new AppException(
          ErrorCode.CONFLICT,
          "Tệp này đã được nhập và xác nhận cho bài đánh giá này (cùng checksum)");
    }
    ScoreCsvParser.Table table = ScoreCsvParser.parse(content);
    ImportColumnMapping mapping =
        resolveMapping(
            request == null ? null : request.getColumnMapping(),
            section.getId(),
            assessment,
            table);

    String batchId = new ObjectId().toHexString();
    String fileRef = fileStorage.save(batchId, content);
    ImportBatch batch =
        ImportBatch.builder()
            .id(batchId)
            .sectionId(section.getId())
            .assessmentId(assessment.getId())
            .fileName(cleanFileName(fileName))
            .fileChecksum(checksum)
            .fileRef(fileRef)
            .sourceSystem(source)
            .columnMapping(mapping)
            .reimportPolicy(policy)
            .status(ImportBatchStatus.UPLOADED)
            .uploadedBy(actor.getId())
            .createdAt(Instant.now())
            .build();
    ImportBatch saved;
    try {
      saved = batchRepository.save(batch);
    } catch (RuntimeException ex) {
      fileStorage.delete(fileRef);
      throw ex;
    }
    log.info(
        "Tạo lô nhập điểm id=[{}] lớp=[{}] bài=[{}] bởi user=[{}]",
        saved.getId(),
        section.getCode(),
        assessment.getCode(),
        actor.getUsername());
    return batchMapper.toResponse(saved);
  }

  private ImportSourceSystem resolveSource(ImportBatchCreateRequest request) {
    return request != null && request.getSourceSystem() != null
        ? request.getSourceSystem()
        : ImportSourceSystem.CSV;
  }

  private String cleanFileName(String fileName) {
    if (fileName == null || fileName.isBlank()) {
      return "scores.csv";
    }
    String name = fileName.replace('\\', '/');
    int lastSlash = name.lastIndexOf('/');
    if (lastSlash >= 0) {
      name = name.substring(lastSlash + 1);
    }
    return name.isBlank() ? "scores.csv" : name;
  }

  private ReimportPolicy resolveReimportPolicy(ImportBatchCreateRequest request) {
    ReimportPolicy policy = request == null ? null : request.getReimportPolicy();
    if (policy == null) {
      throw invalid("Thiếu chính sách nhập lại reimportPolicy");
    }
    if (policy != ReimportPolicy.REJECT_ON_CONFLICT) {
      throw new BusinessRuleViolationException(
          "Chính sách nhập lại [" + policy + "] chưa được hỗ trợ (chờ câu hỏi Q19)");
    }
    return policy;
  }

  private ImportColumnMapping resolveMapping(
      ImportColumnMappingDto requested,
      String sectionId,
      Assessment assessment,
      ScoreCsvParser.Table table) {
    ImportColumnMapping mapping;
    if (requested != null && requested.getItems() != null && !requested.getItems().isEmpty()) {
      mapping = resolveRequested(requested, assessment);
    } else {
      mapping =
          previousMapping(sectionId, assessment.getId())
              .orElseThrow(
                  () ->
                      invalid(
                          "Chưa có ánh xạ cột: gửi columnMapping (bài này chưa có lô trước để dùng lại)"));
    }
    if (table.indexOf(mapping.getStudentColumn()) < 0) {
      throw invalid("Tệp thiếu cột mã sinh viên [" + mapping.getStudentColumn() + "]");
    }
    for (ImportColumnMapping.Entry entry : mapping.getItems()) {
      if (table.indexOf(entry.getColumn()) < 0) {
        throw invalid("Tệp thiếu cột [" + entry.getColumn() + "] đã ánh xạ");
      }
    }
    return mapping;
  }

  private Optional<ImportColumnMapping> previousMapping(String sectionId, String assessmentId) {
    return batchRepository
        .findFirstBySectionIdAndAssessmentIdAndColumnMappingNotNullOrderByCreatedAtDesc(
            sectionId, assessmentId)
        .map(ImportBatch::getColumnMapping);
  }

  private ImportColumnMapping resolveRequested(
      ImportColumnMappingDto requested, Assessment assessment) {
    String studentColumn =
        requested.getStudentColumn() == null || requested.getStudentColumn().isBlank()
            ? DEFAULT_STUDENT_COLUMN
            : requested.getStudentColumn().trim();
    Set<String> columns = new HashSet<>();
    columns.add(studentColumn.toLowerCase(Locale.ROOT));
    Set<String> itemKeys = new HashSet<>();
    List<ImportColumnMapping.Entry> entries = new ArrayList<>();
    for (ImportColumnMappingDto.Entry dto : requested.getItems()) {
      if (dto == null
          || dto.getColumn() == null
          || dto.getColumn().isBlank()
          || dto.getItemId() == null
          || dto.getItemId().isBlank()) {
        throw invalid("Mỗi mục ánh xạ phải có column và item");
      }
      String column = dto.getColumn().trim();
      if (!columns.add(column.toLowerCase(Locale.ROOT))) {
        throw invalid("Cột [" + column + "] bị ánh xạ nhiều lần hoặc trùng cột mã sinh viên");
      }
      AssessmentItem item = findItem(assessment, dto.getItemId().trim());
      String cloId = dto.getCloId();
      if (cloId == null || cloId.isBlank()) {
        if (!item.getOutcomeMappings().isEmpty()) {
          cloId = item.getOutcomeMappings().get(0).getCloId();
        }
      }
      String key = item.getId() + ":" + (cloId == null ? "" : cloId);
      if (!itemKeys.add(key)) {
        throw new BusinessRuleViolationException(
            "Câu hỏi [" + item.getCode() + "] bị ánh xạ từ nhiều cột");
      }
      entries.add(
          ImportColumnMapping.Entry.builder()
              .column(column)
              .itemId(item.getId())
              .cloId(cloId)
              .build());
    }
    return ImportColumnMapping.builder().studentColumn(studentColumn).items(entries).build();
  }

  private AssessmentItem findItem(Assessment assessment, String ref) {
    for (AssessmentItem item : assessment.getItems()) {
      if (ref.equals(item.getId()) || ref.equalsIgnoreCase(item.getCode())) {
        return item;
      }
    }
    throw new BusinessRuleViolationException(
        "Không tìm thấy câu hỏi nào khớp với ID hoặc mã [" + ref + "]");
  }

  @Override
  @Transactional
  public ImportBatchResponse validateBatch(String batchId, UserPrincipal actor) {
    ImportBatch batch = requireBatch(batchId);
    if (batch.getStatus() != ImportBatchStatus.UPLOADED
        && batch.getStatus() != ImportBatchStatus.VALIDATED
        && batch.getStatus() != ImportBatchStatus.HAS_ERRORS) {
      throw new BusinessRuleViolationException(
          "Không thể kiểm tra lô ở trạng thái " + batch.getStatus());
    }
    Assessment assessment = loadAssessment(batch);
    requireScoreEntryAllowed(requireSection(batch.getSectionId()), assessment);

    ScoreRowValidator.Result result = runValidation(batch, assessment);
    rowErrorRepository.deleteByBatchId(batch.getId());
    if (!result.errors().isEmpty()) {
      List<ImportRowError> errorDocs =
          result.errors().stream()
              .map(
                  err ->
                      ImportRowError.builder()
                          .batchId(batch.getId())
                          .rowNumber(err.rowNumber())
                          .column(err.column())
                          .errorType(err.errorType())
                          .rawValue(err.rawValue())
                          .message(err.message())
                          .build())
              .toList();
      rowErrorRepository.insert(errorDocs);
    }
    batch.setStats(toStats(result));
    batch.setStatus(
        result.errors().isEmpty() ? ImportBatchStatus.VALIDATED : ImportBatchStatus.HAS_ERRORS);
    ImportBatch saved = batchRepository.save(batch);
    log.info(
        "Kiểm tra lô nhập điểm id=[{}]: {} dòng, {} dòng lỗi, trạng thái {} bởi user=[{}]",
        saved.getId(),
        result.totalRows(),
        result.errorRows(),
        saved.getStatus(),
        actor.getUsername());
    return batchMapper.toResponse(saved);
  }

  private ScoreRowValidator.Result runValidation(ImportBatch batch, Assessment assessment) {
    byte[] content = fileStorage.load(batch.getFileRef());
    if (!ChecksumUtils.sha256Hex(content).equals(batch.getFileChecksum())) {
      throw new AppException(
          ErrorCode.INTERNAL_ERROR,
          HttpStatus.INTERNAL_SERVER_ERROR,
          "Tệp gốc đã lưu không còn khớp checksum của lô " + batch.getId());
    }
    ScoreCsvParser.Table table = ScoreCsvParser.parse(content);
    ImportColumnMapping mapping = batch.getColumnMapping();
    List<ScoreRowValidator.Column> columns =
        mapping.getItems().stream()
            .map(
                entry -> {
                  String cloId = entry.getCloId();
                  if (cloId == null) {
                    AssessmentItem item = findItem(assessment, entry.getItemId());
                    if (!item.getOutcomeMappings().isEmpty()) {
                      cloId = item.getOutcomeMappings().get(0).getCloId();
                    }
                  }
                  return new ScoreRowValidator.Column(
                      table.indexOf(entry.getColumn()),
                      entry.getColumn(),
                      entry.getItemId(),
                      cloId,
                      maxScoreOf(assessment, entry.getItemId(), cloId));
                })
            .toList();

    Set<String> existingKeys =
        scoreRepository
            .findBySectionIdAndAssessmentId(batch.getSectionId(), batch.getAssessmentId())
            .stream()
            .map(
                score ->
                    ScoreRowValidator.key(
                        score.getStudentId(), score.getItemId(), score.getCloId()))
            .collect(Collectors.toSet());

    return rowValidator.validate(
        table,
        table.indexOf(mapping.getStudentColumn()),
        mapping,
        columns,
        enrolledStudentIdByRef(batch.getSectionId()),
        existingKeys);
  }

  private double maxScoreOf(Assessment assessment, String itemId, String cloId) {
    AssessmentItem item =
        assessment.getItems().stream()
            .filter(it -> itemId.equals(it.getId()))
            .findFirst()
            .orElseThrow(
                () ->
                    new BusinessRuleViolationException(
                        "Câu hỏi " + itemId + " không còn trong bài đánh giá"));
    return item.getOutcomeMappings().stream()
        .filter(m -> cloId == null || cloId.equals(m.getCloId()))
        .findFirst()
        .map(OutcomeMapping::getMaxScore)
        .orElseThrow(
            () ->
                new BusinessRuleViolationException(
                    "CLO " + cloId + " không còn trong câu hỏi " + item.getCode()));
  }

  private Map<String, String> enrolledStudentIdByRef(String sectionId) {
    List<Enrollment> enrolled =
        enrollmentRepository.findBySectionIdAndStatus(sectionId, EnrollmentStatus.ENROLLED);
    Set<String> studentIds =
        enrolled.stream().map(Enrollment::getStudentId).collect(Collectors.toSet());
    Map<String, String> refToId = new HashMap<>();
    studentRepository
        .findAllById(studentIds)
        .forEach(student -> refToId.put(student.getStudentRef(), student.getId()));
    return refToId;
  }

  private ImportStats toStats(ScoreRowValidator.Result result) {
    return ImportStats.builder()
        .totalRows(result.totalRows())
        .errorRows(result.errorRows())
        .validRows(result.totalRows() - result.errorRows())
        .scoreCount(result.drafts().size())
        .build();
  }

  @Override
  @Transactional
  public ImportBatchResponse confirmBatch(String batchId, UserPrincipal actor) {
    ImportBatch batch = requireBatch(batchId);
    if (batch.getStatus() != ImportBatchStatus.VALIDATED) {
      throw new BusinessRuleViolationException(cannotConfirmMessage(batch.getStatus()));
    }
    Assessment assessment = loadAssessment(batch);
    requireScoreEntryAllowed(requireSection(batch.getSectionId()), assessment);

    ScoreRowValidator.Result result = runValidation(batch, assessment);
    if (!result.errors().isEmpty()) {
      throw new BusinessRuleViolationException(
          "Dữ liệu đã đổi kể từ lúc kiểm tra ("
              + result.errorRows()
              + " dòng lỗi); hãy chạy lại bước kiểm tra");
    }

    Instant now = Instant.now();
    AssessmentStatus assessmentBefore = assessment.getStatus();
    batch.setStatus(ImportBatchStatus.CONFIRMED);
    batch.setConfirmedBy(actor.getId());
    batch.setConfirmedAt(now);
    batch.setStats(toStats(result));
    ImportBatch saved = batchRepository.save(batch);

    scoreRepository.insert(
        result.drafts().stream()
            .map(draft -> toScore(draft, saved, assessment, actor, now))
            .toList());

    if (assessmentBefore == AssessmentStatus.LOCKED) {
      assessment.setStatus(AssessmentStatus.SCORE_ENTERED);
      assessment.setLocked(true);
      assessmentRepository.save(assessment);
    }

    auditService.record(
        AuditAction.IMPORT_CONFIRM,
        AuditEntity.builder().collection(BATCH_COLLECTION).id(saved.getId()).build(),
        Map.of("status", ImportBatchStatus.VALIDATED.name()),
        confirmedState(saved, assessment),
        null,
        actor.getId(),
        null);
    log.info(
        "Xác nhận lô nhập điểm id=[{}]: {} điểm, bài {} -> {} bởi user=[{}]",
        saved.getId(),
        result.drafts().size(),
        assessmentBefore,
        assessment.getStatus(),
        actor.getUsername());
    return batchMapper.toResponse(saved);
  }

  private Score toScore(
      ScoreRowValidator.Draft draft,
      ImportBatch batch,
      Assessment assessment,
      UserPrincipal actor,
      Instant now) {
    return Score.builder()
        .sectionId(batch.getSectionId())
        .assessmentId(assessment.getId())
        .itemId(draft.itemId())
        .cloId(draft.cloId())
        .studentId(draft.studentId())
        .rawScore(draft.rawScore())
        .maxScore(draft.maxScore())
        .importBatchId(batch.getId())
        .sourceRow(draft.sourceRow())
        .updatedBy(actor.getId())
        .updatedAt(now)
        .build();
  }

  private Map<String, Object> confirmedState(ImportBatch batch, Assessment assessment) {
    Map<String, Object> state = new LinkedHashMap<>();
    state.put("status", batch.getStatus().name());
    state.put("scoreCount", batch.getStats().getScoreCount());
    state.put("assessmentId", assessment.getId());
    state.put("assessmentStatus", assessment.getStatus().name());
    return state;
  }

  private String cannotConfirmMessage(ImportBatchStatus status) {
    return switch (status) {
      case HAS_ERRORS -> "Lô còn lỗi (HAS_ERRORS), không xác nhận được; sửa tệp rồi tải lô mới";
      case UPLOADED -> "Lô chưa được kiểm tra (UPLOADED); hãy chạy bước kiểm tra trước";
      case CONFIRMED -> "Lô đã được xác nhận, không xác nhận lại";
      case ROLLED_BACK -> "Lô đã bị hủy (ROLLED_BACK), không xác nhận được";
      case VALIDATED -> "Lô hợp lệ";
    };
  }

  @Override
  @Transactional
  public ImportBatchResponse rollbackBatch(String batchId, UserPrincipal actor) {
    ImportBatch batch = requireBatch(batchId);
    if (batch.getStatus() == ImportBatchStatus.CONFIRMED) {
      throw new BusinessRuleViolationException("Không thể hủy lô đã xác nhận (CONFIRMED)");
    } else if (batch.getStatus() == ImportBatchStatus.ROLLED_BACK) {
      throw new BusinessRuleViolationException("Lô đã bị hủy trước đó");
    }
    batch.setStatus(ImportBatchStatus.ROLLED_BACK);
    ImportBatch saved = batchRepository.save(batch);
    log.info("Hủy lô nhập điểm id=[{}] bởi user=[{}]", saved.getId(), actor.getUsername());
    return batchMapper.toResponse(saved);
  }

  @Override
  public ImportBatchResponse getBatch(String batchId) {
    return batchMapper.toResponse(requireBatch(batchId));
  }

  @Override
  public List<ImportBatchResponse> listBatches(String sectionId, String assessmentId) {
    CourseSection section = requireSection(sectionId);
    Assessment assessment = requireSectionAssessment(section, assessmentId);
    return batchRepository
        .findBySectionIdAndAssessmentIdOrderByCreatedAtDesc(section.getId(), assessment.getId())
        .stream()
        .map(batchMapper::toResponse)
        .toList();
  }

  @Override
  public PageResponse<ImportRowErrorResponse> getErrors(String batchId, Pageable pageable) {
    ImportBatch batch = requireBatch(batchId);
    return PageResponse.from(
        rowErrorRepository
            .findByBatchIdOrderByRowNumberAscIdAsc(batch.getId(), pageable)
            .map(batchMapper::toResponse));
  }

  private void requireScoreEntryAllowed(CourseSection section, Assessment assessment) {
    if (section.getStatus() == SectionStatus.LOCKED) {
      throw new BusinessRuleViolationException(
          "Lớp [" + section.getCode() + "] đang LOCKED, không nhập điểm");
    }
    Term term =
        termRepository
            .findById(section.getTermId())
            .orElseThrow(
                () ->
                    new BusinessRuleViolationException(
                        "Học kỳ của lớp không tồn tại: " + section.getTermId()));
    if (term.getStatus() != TermStatus.OPEN) {
      throw new BusinessRuleViolationException(
          "Học kỳ [" + term.getCode() + "] đang " + term.getStatus() + ", không nhập điểm");
    }
    if (assessment.getStatus() != AssessmentStatus.LOCKED
        && assessment.getStatus() != AssessmentStatus.SCORE_ENTERED) {
      throw new BusinessRuleViolationException(
          "Chỉ nhập điểm khi bài đánh giá LOCKED hoặc SCORE_ENTERED, hiện là "
              + assessment.getStatus());
    }
  }

  private Assessment loadAssessment(ImportBatch batch) {
    return assessmentRepository
        .findById(batch.getAssessmentId())
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "Bài đánh giá của lô không tồn tại: " + batch.getAssessmentId()));
  }

  private ImportBatch requireBatch(String batchId) {
    ObjectIdUtils.requireValid(batchId, "batchId");
    return batchRepository
        .findById(batchId)
        .orElseThrow(() -> new ResourceNotFoundException("Lô nhập điểm không tồn tại: " + batchId));
  }

  private CourseSection requireSection(String sectionId) {
    ObjectIdUtils.requireValid(sectionId, "sectionId");
    return sectionRepository
        .findById(sectionId)
        .orElseThrow(
            () -> new ResourceNotFoundException("Lớp học phần không tồn tại: " + sectionId));
  }

  private Assessment requireSectionAssessment(CourseSection section, String assessmentId) {
    ObjectIdUtils.requireValid(assessmentId, "assessmentId");
    Assessment assessment =
        assessmentRepository
            .findById(assessmentId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Bài đánh giá không tồn tại: " + assessmentId));
    if (assessment.getScope() != AssessmentScope.SECTION
        || !section.getId().equals(assessment.getSectionId())) {
      throw new ResourceNotFoundException(
          "Bài đánh giá " + assessmentId + " không thuộc lớp " + section.getId());
    }
    return assessment;
  }

  private AppException invalid(String message) {
    return new AppException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, message);
  }
}
