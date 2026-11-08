package com.hcmute.lms.lmsapi.service.impl;

import com.hcmute.lms.lmsapi.document.ApprovalHistoryEntry;
import com.hcmute.lms.lmsapi.document.Assessment;
import com.hcmute.lms.lmsapi.document.AssessmentApproval;
import com.hcmute.lms.lmsapi.document.AssessmentItem;
import com.hcmute.lms.lmsapi.document.AuditEntity;
import com.hcmute.lms.lmsapi.dto.AssessmentRejectRequest;
import com.hcmute.lms.lmsapi.dto.AssessmentResponse;
import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import com.hcmute.lms.lmsapi.enums.ApprovalAction;
import com.hcmute.lms.lmsapi.enums.AssessmentScope;
import com.hcmute.lms.lmsapi.enums.AssessmentStatus;
import com.hcmute.lms.lmsapi.enums.AuditAction;
import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
import com.hcmute.lms.lmsapi.exception.ResourceNotFoundException;
import com.hcmute.lms.lmsapi.mapper.AssessmentMapper;
import com.hcmute.lms.lmsapi.repository.AssessmentRepository;
import com.hcmute.lms.lmsapi.service.AssessmentApprovalService;
import com.hcmute.lms.lmsapi.service.AuditService;
import com.hcmute.lms.lmsapi.util.ObjectIdUtils;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssessmentApprovalServiceImpl implements AssessmentApprovalService {

  private static final String COLLECTION = "assessments";

  /** Sai số cho phép giữa totalPoints và Σ maxScore (plan F04 §4 quy tắc, NEW-01/AC-2). */
  static final double TOTAL_POINTS_TOLERANCE = 0.001;

  private final AssessmentRepository assessmentRepository;
  private final AuditService auditService;
  private final AssessmentMapper assessmentMapper;

  @Override
  @Transactional
  public AssessmentResponse submit(String id, UserPrincipal actor) {
    Assessment assessment = requireSectionAssessment(id);
    requireStatus(assessment, AssessmentStatus.DRAFT, AssessmentStatus.SUBMITTED);
    validateReadyToSubmit(assessment);

    Instant now = Instant.now();
    AssessmentApproval approval = approvalOf(assessment);
    approval.setSubmittedBy(actor.getId());
    approval.setSubmittedAt(now);
    // Lượt gửi mới: xóa kết quả duyệt của lượt trước (lịch sử vẫn giữ trong history)
    approval.setReviewedBy(null);
    approval.setReviewedAt(null);
    approval.setReviewNote(null);
    approval.getHistory().add(historyEntry(ApprovalAction.SUBMIT, actor, now, null));

    assessment.setStatus(AssessmentStatus.SUBMITTED);
    assessment.setLocked(false);
    Assessment saved = assessmentRepository.save(assessment);
    log.info("Gửi duyệt bài đánh giá id=[{}] bởi user=[{}]", saved.getId(), actor.getUsername());
    return assessmentMapper.toResponse(saved);
  }

  @Override
  @Transactional
  public AssessmentResponse approve(String id, UserPrincipal actor) {
    Assessment assessment = requireSectionAssessment(id);
    requireStatus(assessment, AssessmentStatus.SUBMITTED, AssessmentStatus.APPROVED);
    requireNotSubmitter(assessment, actor);

    Map<String, Object> before = snapshot(assessment);
    Instant now = Instant.now();
    AssessmentApproval approval = approvalOf(assessment);
    approval.setReviewedBy(actor.getId());
    approval.setReviewedAt(now);
    approval.setReviewNote(null);
    approval.getHistory().add(historyEntry(ApprovalAction.APPROVE, actor, now, null));

    // status và isLocked phải đổi trong cùng một lần ghi (validator của MongoDB ép khớp)
    assessment.setStatus(AssessmentStatus.APPROVED);
    assessment.setLocked(AssessmentStatus.APPROVED.locksStructure());
    Assessment saved = assessmentRepository.save(assessment);

    auditService.record(
        AuditAction.ASSESSMENT_APPROVE,
        AuditEntity.builder().collection(COLLECTION).id(saved.getId()).build(),
        before,
        snapshot(saved),
        null,
        actor.getId(),
        null);
    log.info(
        "Duyệt ma trận bài đánh giá id=[{}] bởi user=[{}]", saved.getId(), actor.getUsername());
    return assessmentMapper.toResponse(saved);
  }

  @Override
  @Transactional
  public AssessmentResponse reject(
      String id, AssessmentRejectRequest request, UserPrincipal actor) {
    Assessment assessment = requireSectionAssessment(id);
    requireStatus(assessment, AssessmentStatus.SUBMITTED, AssessmentStatus.REJECTED);
    requireNotSubmitter(assessment, actor);

    String note = request.getReviewNote().trim();
    Map<String, Object> before = snapshot(assessment);
    Instant now = Instant.now();
    AssessmentApproval approval = approvalOf(assessment);
    approval.setReviewedBy(actor.getId());
    approval.setReviewedAt(now);
    approval.setReviewNote(note);
    approval.getHistory().add(historyEntry(ApprovalAction.REJECT, actor, now, note));

    assessment.setStatus(AssessmentStatus.REJECTED);
    assessment.setLocked(AssessmentStatus.REJECTED.locksStructure());
    Assessment saved = assessmentRepository.save(assessment);

    auditService.record(
        AuditAction.ASSESSMENT_REJECT,
        AuditEntity.builder().collection(COLLECTION).id(saved.getId()).build(),
        before,
        snapshot(saved),
        note,
        actor.getId(),
        null);
    log.info(
        "Từ chối ma trận bài đánh giá id=[{}] bởi user=[{}]", saved.getId(), actor.getUsername());
    return assessmentMapper.toResponse(saved);
  }

  /** Gửi duyệt cần ≥ 1 phần tử và tổng điểm khai báo khớp Σ maxScore (NEW-01/AC-2). */
  private void validateReadyToSubmit(Assessment assessment) {
    if (assessment.getItems().isEmpty()) {
      throw new BusinessRuleViolationException(
          "Không thể gửi duyệt bài đánh giá chưa có câu hỏi/tiêu chí nào");
    }
    if (assessment.getTotalPoints() == null) {
      throw new BusinessRuleViolationException(
          "Cần khai báo tổng điểm (totalPoints) trước khi gửi duyệt");
    }
    double sum = assessment.getItems().stream().mapToDouble(AssessmentItem::getMaxScore).sum();
    if (Math.abs(assessment.getTotalPoints() - sum) >= TOTAL_POINTS_TOLERANCE) {
      throw new BusinessRuleViolationException(
          String.format(
              "Tổng điểm khai báo (%s) lệch tổng điểm các phần tử (%s) quá %s",
              assessment.getTotalPoints(), sum, TOTAL_POINTS_TOLERANCE));
    }
  }

  /** Người gửi duyệt không được tự duyệt/từ chối bài của chính mình (plan F04 §4 quy tắc 9). */
  private void requireNotSubmitter(Assessment assessment, UserPrincipal actor) {
    AssessmentApproval approval = assessment.getApproval();
    if (approval != null
        && actor.getId() != null
        && actor.getId().equals(approval.getSubmittedBy())) {
      throw new AccessDeniedException("Người gửi duyệt không được tự duyệt bài của mình");
    }
  }

  private void requireStatus(
      Assessment assessment, AssessmentStatus expected, AssessmentStatus target) {
    if (assessment.getStatus() != expected) {
      throw new BusinessRuleViolationException(
          String.format(
              "Không thể chuyển bài đánh giá từ trạng thái %s sang %s",
              assessment.getStatus(), target));
    }
  }

  private Assessment requireSectionAssessment(String id) {
    ObjectIdUtils.requireValid(id, "assessmentId");
    Assessment assessment =
        assessmentRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Bài đánh giá không tồn tại: " + id));
    if (assessment.getScope() != AssessmentScope.SECTION) {
      throw new BusinessRuleViolationException(
          "Khung theo đề cương không đi qua quy trình duyệt; chỉ bài đánh giá của lớp mới được duyệt");
    }
    return assessment;
  }

  private AssessmentApproval approvalOf(Assessment assessment) {
    if (assessment.getApproval() == null) {
      assessment.setApproval(AssessmentApproval.builder().history(new ArrayList<>()).build());
    }
    return assessment.getApproval();
  }

  private ApprovalHistoryEntry historyEntry(
      ApprovalAction action, UserPrincipal actor, Instant at, String note) {
    return ApprovalHistoryEntry.builder()
        .action(action)
        .actorId(actor.getId())
        .at(at)
        .note(note)
        .build();
  }

  private Map<String, Object> snapshot(Assessment assessment) {
    Map<String, Object> state = new LinkedHashMap<>();
    state.put("status", assessment.getStatus().name());
    state.put("isLocked", assessment.isLocked());
    return state;
  }
}
