package com.hcmute.lms.lmsapi.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hcmute.lms.lmsapi.document.Score;
import com.hcmute.lms.lmsapi.enums.AuditAction;
import com.hcmute.lms.lmsapi.repository.ScoreRepository;
import com.hcmute.lms.lmsapi.service.AuditService;
import com.hcmute.lms.lmsapi.testsupport.ScoreImportIntegrationBase;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * US-24/AC-2 trên MongoDB replica set thật (khung ADR 0008): lỗi giữa chừng khi xác nhận thì không
 * còn điểm nửa vời, lô không CONFIRMED, bài không đổi trạng thái và không có audit mồ côi. Lỗi được
 * chèn bằng spy ở hai chỗ: sau khi đã ghi một phần điểm, và ở bước cuối (ghi audit).
 */
@DisplayName(
    "US-24/AC-2: xác nhận lô nhập điểm rollback trọn vẹn khi lỗi giữa chừng (replica set rs0)")
class ImportConfirmRollbackIntegrationTest extends ScoreImportIntegrationBase {

  private static final String FAKE_FAILURE = "DỮ LIỆU THỬ NGHIỆM: lỗi giả lập giữa chừng";

  @MockitoSpyBean private AuditService auditService;
  @MockitoSpyBean private ScoreRepository scoreRepository;

  private String validatedBatch() throws Exception {
    return uploadAndValidate(
        csv(
            s1.getStudentRef() + ",3,6",
            s2.getStudentRef() + ",4,0",
            s3.getStudentRef() + ",0,2.5"),
        "BLANK");
  }

  private void assertNothingWasWritten(String batchId) {
    assertThat(scoresOfSection()).as("không có điểm nào được ghi").isEmpty();
    assertThat(batchDocument(batchId).getString("status"))
        .as("lô không CONFIRMED")
        .isEqualTo("VALIDATED");
    assertThat(batchDocument(batchId).get("confirmedAt")).isNull();
    assertThat(assessmentStatus()).as("bài vẫn LOCKED").isEqualTo("LOCKED");
    assertThat(importConfirmAudits(batchId)).as("không có audit mồ côi").isZero();
  }

  @Test
  @DisplayName(
      "US-24/AC-2: ghi điểm thứ 3 thất bại (đã ghi 2 điểm trong transaction) -> không còn điểm nào, lô, bài và audit nguyên trạng")
  void us24_ac2_failureWhileWritingScoresRollsEverythingBack() throws Exception {
    String id = validatedBatch();
    doAnswer(
            invocation -> {
              List<Score> all = new ArrayList<>();
              invocation.<Iterable<Score>>getArgument(0).forEach(all::add);
              // Ghi thật 2 điểm đầu trong cùng transaction rồi mới thất bại ở điểm thứ 3.
              mongo().insertAll(all.subList(0, 2));
              assertThat(all).hasSize(6);
              throw new IllegalStateException(FAKE_FAILURE);
            })
        .when(scoreRepository)
        .insert(org.mockito.ArgumentMatchers.<Iterable<Score>>any());

    mockMvc
        .perform(postAction(lecturerToken, id, "confirm"))
        .andExpect(status().isInternalServerError());

    verify(scoreRepository).insert(org.mockito.ArgumentMatchers.<Iterable<Score>>any());
    assertNothingWasWritten(id);
  }

  @Test
  @DisplayName(
      "US-24/AC-2: ghi audit ở bước cuối thất bại -> 6 điểm, trạng thái lô và trạng thái bài đã ghi đều bị rollback")
  void us24_ac2_failureAtTheLastStepRollsEverythingBack() throws Exception {
    String id = validatedBatch();
    doThrow(new IllegalStateException(FAKE_FAILURE))
        .when(auditService)
        .record(eq(AuditAction.IMPORT_CONFIRM), any(), any(), any(), any(), anyString(), any());

    mockMvc
        .perform(postAction(lecturerToken, id, "confirm"))
        .andExpect(status().isInternalServerError());

    assertNothingWasWritten(id);
  }

  @Test
  @DisplayName(
      "US-24/AC-2: sau khi lỗi giả lập hết, xác nhận lại cùng lô thành công trọn vẹn (6 điểm, CONFIRMED, SCORE_ENTERED, 1 audit)")
  void us24_ac2_batchCanBeConfirmedAfterARolledBackAttempt() throws Exception {
    String id = validatedBatch();
    doThrow(new IllegalStateException(FAKE_FAILURE))
        .when(auditService)
        .record(eq(AuditAction.IMPORT_CONFIRM), any(), any(), any(), any(), anyString(), any());
    mockMvc
        .perform(postAction(lecturerToken, id, "confirm"))
        .andExpect(status().isInternalServerError());
    org.mockito.Mockito.reset(auditService);

    mockMvc.perform(postAction(lecturerToken, id, "confirm")).andExpect(status().isOk());

    assertThat(scoresOfSection()).hasSize(6);
    assertThat(batchDocument(id).getString("status")).isEqualTo("CONFIRMED");
    assertThat(assessmentStatus()).isEqualTo("SCORE_ENTERED");
    assertThat(importConfirmAudits(id)).isEqualTo(1);
  }
}
