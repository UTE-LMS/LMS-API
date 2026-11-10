package com.hcmute.lms.lmsapi.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hcmute.lms.lmsapi.document.Assessment;
import com.hcmute.lms.lmsapi.document.AssessmentApproval;
import com.hcmute.lms.lmsapi.document.AssessmentItem;
import com.hcmute.lms.lmsapi.dto.AssessmentRejectRequest;
import com.hcmute.lms.lmsapi.dto.AssessmentResponse;
import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import com.hcmute.lms.lmsapi.enums.ApprovalAction;
import com.hcmute.lms.lmsapi.enums.AssessmentScope;
import com.hcmute.lms.lmsapi.enums.AssessmentStatus;
import com.hcmute.lms.lmsapi.enums.AuditAction;
import com.hcmute.lms.lmsapi.enums.ItemKind;
import com.hcmute.lms.lmsapi.exception.BusinessRuleViolationException;
import com.hcmute.lms.lmsapi.exception.ResourceNotFoundException;
import com.hcmute.lms.lmsapi.mapper.impl.AssessmentMapperImpl;
import com.hcmute.lms.lmsapi.repository.AssessmentRepository;
import com.hcmute.lms.lmsapi.service.AuditService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
@DisplayName("AssessmentApprovalServiceImpl (NEW-01)")
class AssessmentApprovalServiceImplTest {

  private static final String ID = "671dcba5f123456789abc800";
  private static final String LECTURER_ID = "671dcba5f123456789abc00a";
  private static final String REVIEWER_ID = "671dcba5f123456789abc00b";

  @Mock private AssessmentRepository assessmentRepository;
  @Mock private AuditService auditService;

  private AssessmentApprovalServiceImpl service;

  private final UserPrincipal lecturer =
      UserPrincipal.builder().id(LECTURER_ID).username("lecturer").build();
  private final UserPrincipal reviewer =
      UserPrincipal.builder().id(REVIEWER_ID).username("head").build();

  @BeforeEach
  void setUp() {
    service =
        new AssessmentApprovalServiceImpl(
            assessmentRepository, auditService, new AssessmentMapperImpl());
  }

  private AssessmentItem item(double maxScore) {
    return AssessmentItem.builder()
        .id("671dcba5f123456789abc900")
        .code("Q")
        .kind(ItemKind.QUESTION)
        .maxScore(maxScore)
        .build();
  }

  private Assessment assessment(AssessmentStatus status, Double totalPoints, double... scores) {
    List<AssessmentItem> items = new ArrayList<>();
    for (double score : scores) {
      items.add(item(score));
    }
    return Assessment.builder()
        .id(ID)
        .scope(AssessmentScope.SECTION)
        .status(status)
        .locked(status.locksStructure())
        .totalPoints(totalPoints)
        .items(items)
        .build();
  }

  private void found(Assessment assessment) {
    when(assessmentRepository.findById(ID)).thenReturn(Optional.of(assessment));
  }

  private void savesWhatItIsGiven() {
    when(assessmentRepository.save(any(Assessment.class))).thenAnswer(i -> i.getArgument(0));
  }

  @Test
  @DisplayName(
      "NEW-01/AC-2: biên 0,001 - lệch 0,0009 gửi được; lệch 0,0011 và đúng 0,001 bị từ chối")
  void submit_totalPointsToleranceBoundary() {
    Assessment ok = assessment(AssessmentStatus.DRAFT, 10.0009, 4, 6);
    found(ok);
    savesWhatItIsGiven();
    assertThat(service.submit(ID, lecturer).getStatus()).isEqualTo(AssessmentStatus.SUBMITTED);

    for (double total : new double[] {10.0011, 9.9989, 12}) {
      found(assessment(AssessmentStatus.DRAFT, total, 4, 6));
      assertThatThrownBy(() -> service.submit(ID, lecturer))
          .isInstanceOf(BusinessRuleViolationException.class)
          .hasMessageContaining("lệch");
    }
  }

  @Test
  @DisplayName("NEW-01/AC-2: không có phần tử hoặc thiếu totalPoints -> 422")
  void submit_requiresItemsAndTotal() {
    found(assessment(AssessmentStatus.DRAFT, 10.0));
    assertThatThrownBy(() -> service.submit(ID, lecturer))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessageContaining("chưa có câu hỏi");

    found(assessment(AssessmentStatus.DRAFT, null, 10));
    assertThatThrownBy(() -> service.submit(ID, lecturer))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessageContaining("totalPoints");
    verify(assessmentRepository, never()).save(any());
  }

  @Test
  @DisplayName("NEW-01/AC-1: gửi duyệt ghi người gửi, giờ gửi, history SUBMIT; không khóa cấu trúc")
  void submit_recordsApproval() {
    found(assessment(AssessmentStatus.DRAFT, 10.0, 10));
    savesWhatItIsGiven();

    AssessmentResponse response = service.submit(ID, lecturer);

    assertThat(response.isLocked()).isFalse();
    assertThat(response.getApproval().getSubmittedBy()).isEqualTo(LECTURER_ID);
    assertThat(response.getApproval().getSubmittedAt()).isNotNull();
    assertThat(response.getApproval().getHistory())
        .extracting(AssessmentResponse.HistoryEntry::getAction)
        .containsExactly(ApprovalAction.SUBMIT);
  }

  @Test
  @DisplayName("NEW-01/AC-3: duyệt đặt status và isLocked cùng lúc, ghi audit ASSESSMENT_APPROVE")
  void approve_locksAndAudits() {
    Assessment submitted = assessment(AssessmentStatus.SUBMITTED, 10.0, 10);
    submitted.setApproval(
        AssessmentApproval.builder().submittedBy(LECTURER_ID).history(new ArrayList<>()).build());
    found(submitted);
    savesWhatItIsGiven();

    AssessmentResponse response = service.approve(ID, reviewer);

    assertThat(response.getStatus()).isEqualTo(AssessmentStatus.APPROVED);
    assertThat(response.isLocked()).isTrue();
    assertThat(response.getApproval().getReviewedBy()).isEqualTo(REVIEWER_ID);
    verify(auditService)
        .record(
            eq(AuditAction.ASSESSMENT_APPROVE),
            any(),
            any(),
            any(),
            eq(null),
            eq(REVIEWER_ID),
            eq(null));
  }

  @Test
  @DisplayName("NEW-01/AC-4: từ chối ghi ghi chú vào reviewNote, history và audit; isLocked=false")
  void reject_recordsNote() {
    Assessment submitted = assessment(AssessmentStatus.SUBMITTED, 10.0, 10);
    submitted.setApproval(
        AssessmentApproval.builder().submittedBy(LECTURER_ID).history(new ArrayList<>()).build());
    found(submitted);
    savesWhatItIsGiven();

    AssessmentResponse response =
        service.reject(
            ID, AssessmentRejectRequest.builder().reviewNote("  Thiếu CLO2  ").build(), reviewer);

    assertThat(response.getStatus()).isEqualTo(AssessmentStatus.REJECTED);
    assertThat(response.isLocked()).isFalse();
    assertThat(response.getApproval().getReviewNote()).isEqualTo("Thiếu CLO2");
    verify(auditService)
        .record(
            eq(AuditAction.ASSESSMENT_REJECT),
            any(),
            any(),
            any(),
            eq("Thiếu CLO2"),
            eq(REVIEWER_ID),
            eq(null));
  }

  @Test
  @DisplayName("NEW-01/AC-5: người gửi duyệt không tự duyệt hoặc tự từ chối -> AccessDenied (403)")
  void submitterCannotReviewOwnAssessment() {
    Assessment submitted = assessment(AssessmentStatus.SUBMITTED, 10.0, 10);
    submitted.setApproval(
        AssessmentApproval.builder().submittedBy(REVIEWER_ID).history(new ArrayList<>()).build());
    found(submitted);

    assertThatThrownBy(() -> service.approve(ID, reviewer))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(
            () ->
                service.reject(
                    ID, AssessmentRejectRequest.builder().reviewNote("x").build(), reviewer))
        .isInstanceOf(AccessDeniedException.class);
    verify(assessmentRepository, never()).save(any());
    verify(auditService, never())
        .record(any(), any(), any(), any(), any(), any(String.class), any());
  }

  @Test
  @DisplayName("NEW-01/AC-7: mọi chuyển trạng thái sai thứ tự -> 422 và không ghi")
  void invalidTransitions() {
    for (AssessmentStatus status :
        List.of(
            AssessmentStatus.DRAFT,
            AssessmentStatus.APPROVED,
            AssessmentStatus.REJECTED,
            AssessmentStatus.SCORE_ENTERED,
            AssessmentStatus.LOCKED)) {
      found(assessment(status, 10.0, 10));
      assertThatThrownBy(() -> service.approve(ID, reviewer))
          .as("approve từ %s", status)
          .isInstanceOf(BusinessRuleViolationException.class);
      assertThatThrownBy(
              () ->
                  service.reject(
                      ID, AssessmentRejectRequest.builder().reviewNote("x").build(), reviewer))
          .as("reject từ %s", status)
          .isInstanceOf(BusinessRuleViolationException.class);
    }
    for (AssessmentStatus status :
        List.of(
            AssessmentStatus.SUBMITTED,
            AssessmentStatus.APPROVED,
            AssessmentStatus.REJECTED,
            AssessmentStatus.SCORE_ENTERED,
            AssessmentStatus.LOCKED)) {
      found(assessment(status, 10.0, 10));
      assertThatThrownBy(() -> service.submit(ID, lecturer))
          .as("submit từ %s", status)
          .isInstanceOf(BusinessRuleViolationException.class);
    }
    verify(assessmentRepository, never()).save(any());
  }

  @Test
  @DisplayName("Khung theo đề cương không đi qua quy trình duyệt; bài không tồn tại -> 404")
  void templateAndMissing() {
    Assessment template = assessment(AssessmentStatus.DRAFT, 10.0, 10);
    template.setScope(AssessmentScope.SYLLABUS_TEMPLATE);
    found(template);
    assertThatThrownBy(() -> service.submit(ID, lecturer))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessageContaining("Khung");

    when(assessmentRepository.findById(ID)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.approve(ID, reviewer))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  @DisplayName("AssessmentStatus.locksStructure đúng cho APPROVED, SCORE_ENTERED, LOCKED")
  void lockedStatuses() {
    assertThat(AssessmentStatus.APPROVED.locksStructure()).isTrue();
    assertThat(AssessmentStatus.SCORE_ENTERED.locksStructure()).isTrue();
    assertThat(AssessmentStatus.LOCKED.locksStructure()).isTrue();
    assertThat(AssessmentStatus.DRAFT.locksStructure()).isFalse();
    assertThat(AssessmentStatus.SUBMITTED.locksStructure()).isFalse();
    assertThat(AssessmentStatus.REJECTED.locksStructure()).isFalse();
  }
}
