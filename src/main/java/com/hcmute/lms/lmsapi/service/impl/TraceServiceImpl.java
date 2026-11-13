package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.document.Assessment;
import com.hcmute.lms.lmsapi.document.AssessmentItem;
import com.hcmute.lms.lmsapi.document.Clo;
import com.hcmute.lms.lmsapi.document.ImportBatch;
import com.hcmute.lms.lmsapi.document.Score;
import com.hcmute.lms.lmsapi.document.Student;
import com.hcmute.lms.lmsapi.document.StudentResult;
import com.hcmute.lms.lmsapi.dto.ScoreTraceResponse;
import com.hcmute.lms.lmsapi.dto.StudentResultTraceResponse;
import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import com.hcmute.lms.lmsapi.enums.AccessType;
import com.hcmute.lms.lmsapi.enums.ResultInputSourceType;
import com.hcmute.lms.lmsapi.enums.ResultLevel;
import com.hcmute.lms.lmsapi.exception.ResourceNotFoundException;
import com.hcmute.lms.lmsapi.repository.AssessmentRepository;
import com.hcmute.lms.lmsapi.repository.CloRepository;
import com.hcmute.lms.lmsapi.repository.ImportBatchRepository;
import com.hcmute.lms.lmsapi.repository.PiRepository;
import com.hcmute.lms.lmsapi.repository.PloRepository;
import com.hcmute.lms.lmsapi.repository.ScoreRepository;
import com.hcmute.lms.lmsapi.repository.StudentRepository;
import com.hcmute.lms.lmsapi.repository.StudentResultRepository;
import com.hcmute.lms.lmsapi.service.ScopeAuthorizationService;
import com.hcmute.lms.lmsapi.service.TraceService;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/** Triển khai truy vết ngược từ kết quả đo lường về điểm và dòng tệp gốc (US-34). */
@Service
@RequiredArgsConstructor
public class TraceServiceImpl implements TraceService {

  private final StudentResultRepository studentResultRepository;
  private final ScoreRepository scoreRepository;
  private final ImportBatchRepository importBatchRepository;
  private final AssessmentRepository assessmentRepository;
  private final CloRepository cloRepository;
  private final PiRepository piRepository;
  private final PloRepository ploRepository;
  private final StudentRepository studentRepository;
  private final ScopeAuthorizationService scopeAuthorizationService;

  @Override
  public StudentResultTraceResponse traceStudentResult(String studentResultId) {
    StudentResult result =
        studentResultRepository
            .findById(studentResultId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Không tìm thấy kết quả sinh viên: " + studentResultId));

    verifyStudentResultAccess(result);

    String studentRef =
        studentRepository
            .findById(result.getStudentId())
            .map(Student::getStudentRef)
            .orElse(result.getStudentId());
    String subjectCode = resolveSubjectCode(result.getLevel(), result.getSubjectId());

    List<StudentResultTraceResponse.TraceInputItem> tracedInputs = new ArrayList<>();
    for (StudentResult.ResultInput input : result.getInputs()) {
      String label = resolveInputLabel(input);
      tracedInputs.add(
          StudentResultTraceResponse.TraceInputItem.builder()
              .sourceType(input.getSourceType())
              .refId(input.getRefId())
              .label(label)
              .normalizedValue(input.getNormalizedValue())
              .weight(input.getWeight())
              .build());
    }

    return StudentResultTraceResponse.builder()
        .id(result.getId())
        .runId(result.getRunId())
        .level(result.getLevel())
        .subjectId(result.getSubjectId())
        .subjectCode(subjectCode)
        .studentId(result.getStudentId())
        .studentRef(studentRef)
        .status(result.getStatus())
        .value(result.getValue())
        .passThreshold(result.getPassThreshold())
        .passed(result.getPassed())
        .inputs(tracedInputs)
        .build();
  }

  @Override
  public ScoreTraceResponse traceScore(String scoreId) {
    Score score =
        scoreRepository
            .findById(scoreId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Không tìm thấy bản ghi điểm: " + scoreId));

    verifySectionReadAccess(score.getSectionId());

    String studentRef =
        studentRepository
            .findById(score.getStudentId())
            .map(Student::getStudentRef)
            .orElse(score.getStudentId());
    String cloCode =
        cloRepository.findById(score.getCloId()).map(Clo::getCode).orElse(score.getCloId());
    String itemCode = resolveItemCode(score.getAssessmentId(), score.getItemId());
    ImportBatch batch =
        score.getImportBatchId() != null
            ? importBatchRepository.findById(score.getImportBatchId()).orElse(null)
            : null;

    return ScoreTraceResponse.builder()
        .scoreId(score.getId())
        .sectionId(score.getSectionId())
        .assessmentId(score.getAssessmentId())
        .itemId(score.getItemId())
        .itemCode(itemCode)
        .cloId(score.getCloId())
        .cloCode(cloCode)
        .studentId(score.getStudentId())
        .studentRef(studentRef)
        .rawScore(score.getRawScore())
        .maxScore(score.getMaxScore())
        .importBatchId(score.getImportBatchId())
        .sourceRow(score.getSourceRow())
        .fileName(batch != null ? batch.getFileName() : null)
        .sourceSystem(batch != null ? batch.getSourceSystem() : null)
        .importedBy(
            batch != null
                ? (batch.getConfirmedBy() != null ? batch.getConfirmedBy() : batch.getUploadedBy())
                : null)
        .importedAt(
            batch != null
                ? (batch.getConfirmedAt() != null ? batch.getConfirmedAt() : batch.getCreatedAt())
                : null)
        .build();
  }

  private String resolveInputLabel(StudentResult.ResultInput input) {
    if (input.getSourceType() == ResultInputSourceType.SCORE) {
      return scoreRepository
          .findById(input.getRefId())
          .map(
              sc ->
                  "Điểm "
                      + resolveItemCode(sc.getAssessmentId(), sc.getItemId())
                      + " ("
                      + sc.getRawScore()
                      + "/"
                      + sc.getMaxScore()
                      + ")")
          .orElse("Score#" + input.getRefId());
    }
    return studentResultRepository
        .findById(input.getRefId())
        .map(
            sr -> sr.getLevel().name() + " " + resolveSubjectCode(sr.getLevel(), sr.getSubjectId()))
        .orElse("Result#" + input.getRefId());
  }

  private String resolveItemCode(String assessmentId, String itemId) {
    if (assessmentId == null || itemId == null) {
      return itemId;
    }
    Assessment assessment = assessmentRepository.findById(assessmentId).orElse(null);
    if (assessment == null || assessment.getItems() == null) {
      return itemId;
    }
    return assessment.getItems().stream()
        .filter(i -> itemId.equals(i.getId()))
        .map(AssessmentItem::getCode)
        .findFirst()
        .orElse(itemId);
  }

  private String resolveSubjectCode(ResultLevel level, String subjectId) {
    if (subjectId == null) {
      return null;
    }
    return switch (level) {
      case ASSESSMENT_CLO, CLO ->
          cloRepository.findById(subjectId).map(Clo::getCode).orElse(subjectId);
      case PI -> piRepository.findById(subjectId).map(p -> p.getCode()).orElse(subjectId);
      case PLO -> ploRepository.findById(subjectId).map(p -> p.getCode()).orElse(subjectId);
    };
  }

  private void verifyStudentResultAccess(StudentResult result) {
    if (result.getSectionId() != null) {
      verifySectionReadAccess(result.getSectionId());
      return;
    }
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
      return;
    }
    if (result.getProgramVersionId() != null
        && !scopeAuthorizationService.canAccessProgramVersion(
            principal, result.getProgramVersionId(), AccessType.READ)) {
      throw new AccessDeniedException("Không có quyền truy vết kết quả này");
    }
  }

  private void verifySectionReadAccess(String sectionId) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
      return;
    }
    if (sectionId != null
        && !scopeAuthorizationService.canAccessSection(principal, sectionId, AccessType.READ)) {
      throw new AccessDeniedException("Không có quyền truy vết dữ liệu lớp học phần này");
    }
  }
}
