package com.hcmute.lms.lmsapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hcmute.lms.lmsapi.document.Student;
import com.hcmute.lms.lmsapi.enums.TermStatus;
import com.hcmute.lms.lmsapi.testsupport.ScoreImportIntegrationBase;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@DisplayName("F05b: xác nhận và hủy lô nhập điểm (US-24)")
class ImportBatchConfirmIntegrationTest extends ScoreImportIntegrationBase {

  /** Ba sinh viên, hai câu: Q1 tối đa 4, Q2 tối đa 6 (ví dụ minh họa). */
  private String threeStudentsCsv() {
    return csv(
        s1.getStudentRef() + ",3,6", // dòng 2
        s2.getStudentRef() + ",4,0", // dòng 3: 0 gõ rõ ràng là SCORED 0
        s3.getStudentRef() + ",0,2.5"); // dòng 4
  }

  private Document scoreOf(Student student, int itemIndex) {
    return mongo()
        .findOne(
            Query.query(
                Criteria.where("studentId")
                    .is(new ObjectId(student.getId()))
                    .and("itemId")
                    .is(new ObjectId(itemId(itemIndex)))),
            Document.class,
            "scores");
  }

  private static double number(Document score, String field) {
    return ((Number) score.get(field)).doubleValue();
  }

  @Test
  @DisplayName(
      "US-24/AC-1: xác nhận lô VALIDATED -> CONFIRMED; 6 điểm (3 SV x 2 câu) + trạng thái lô + bài SCORE_ENTERED + audit IMPORT_CONFIRM cùng được ghi")
  void us24_ac1_confirmWritesEverythingTogether() throws Exception {
    String id = uploadAndValidate(threeStudentsCsv(), "BLANK");
    assertThat(assessmentStatus()).isEqualTo("APPROVED");
    assertThat(scoresOfSection()).isEmpty();

    mockMvc
        .perform(postAction(lecturerToken, id, "confirm"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CONFIRMED"))
        .andExpect(jsonPath("$.confirmedBy").value(lecturer.getId()))
        .andExpect(jsonPath("$.confirmedAt").exists())
        .andExpect(jsonPath("$.stats.scoreCount").value(6));

    // 3 sinh viên x 2 câu = 6 điểm
    List<Document> scores = scoresOfSection();
    assertThat(scores).hasSize(6);
    Document s1q1 = scoreOf(s1, 0);
    assertThat(s1q1.getString("status")).isEqualTo("SCORED");
    assertThat(number(s1q1, "rawScore")).isEqualTo(3.0);
    assertThat(number(s1q1, "maxScore")).isEqualTo(4.0);
    assertThat(s1q1.getInteger("revision")).isEqualTo(1);
    assertThat(s1q1.getInteger("sourceRow")).isEqualTo(2);
    assertThat(s1q1.getObjectId("importBatchId")).isEqualTo(new ObjectId(id));
    assertThat(s1q1.getObjectId("sectionId")).isEqualTo(new ObjectId(section.getId()));
    assertThat(s1q1.getObjectId("assessmentId")).isEqualTo(new ObjectId(assessment.getId()));
    assertThat(s1q1.getObjectId("updatedBy")).isEqualTo(new ObjectId(lecturer.getId()));
    assertThat(s1q1.getDate("updatedAt")).isBeforeOrEqualTo(new Date());
    assertThat(number(scoreOf(s1, 1), "rawScore")).isEqualTo(6.0);
    assertThat(number(scoreOf(s1, 1), "maxScore")).isEqualTo(6.0);
    // s2: Q2 gõ "0" -> SCORED 0 (không phải BLANK), sourceRow = 3
    assertThat(scoreOf(s2, 1).getString("status")).isEqualTo("SCORED");
    assertThat(number(scoreOf(s2, 1), "rawScore")).isEqualTo(0.0);
    assertThat(scoreOf(s2, 1).getInteger("sourceRow")).isEqualTo(3);
    // s3: Q2 = 2.5 ở dòng 4
    assertThat(number(scoreOf(s3, 1), "rawScore")).isEqualTo(2.5);
    assertThat(scoreOf(s3, 1).getInteger("sourceRow")).isEqualTo(4);

    Document batch = batchDocument(id);
    assertThat(batch.getString("status")).isEqualTo("CONFIRMED");
    assertThat(batch.getObjectId("confirmedBy")).isEqualTo(new ObjectId(lecturer.getId()));
    assertThat(batch.getDate("confirmedAt")).isNotNull();
    assertThat(assessmentStatus()).isEqualTo("SCORE_ENTERED");
    Document assessmentDoc =
        mongo()
            .findOne(
                Query.query(Criteria.where("_id").is(new ObjectId(assessment.getId()))),
                Document.class,
                "assessments");
    assertThat(assessmentDoc.getBoolean("isLocked")).isTrue();

    assertThat(importConfirmAudits(id)).isEqualTo(1);
    Document audit =
        mongo()
            .findOne(
                Query.query(
                    Criteria.where("action")
                        .is("IMPORT_CONFIRM")
                        .and("entity._id")
                        .is(new ObjectId(id))),
                Document.class,
                "audit_logs");
    assertThat(audit.getObjectId("userId")).isEqualTo(new ObjectId(lecturer.getId()));
    assertThat(audit.get("before", Document.class).getString("status")).isEqualTo("VALIDATED");
    Document after = audit.get("after", Document.class);
    assertThat(after.getString("status")).isEqualTo("CONFIRMED");
    assertThat(after.getInteger("scoreCount")).isEqualTo(6);
    assertThat(after.getString("assessmentStatus")).isEqualTo("SCORE_ENTERED");
  }

  @Test
  @DisplayName(
      "US-24/AC-1: chỉ lô đầu chuyển bài sang SCORE_ENTERED; lô thứ hai giữ nguyên và cộng thêm điểm")
  void us24_ac1_onlyFirstBatchChangesAssessmentStatus() throws Exception {
    Student s4 = enroll(com.hcmute.lms.lmsapi.enums.EnrollmentStatus.ENROLLED);
    String first = uploadAndValidate(csv(s1.getStudentRef() + ",3,6"), "BLANK");
    String second = uploadAndValidate(csv(s4.getStudentRef() + ",1,2"), "BLANK");

    mockMvc.perform(postAction(lecturerToken, first, "confirm")).andExpect(status().isOk());
    assertThat(assessmentStatus()).isEqualTo("SCORE_ENTERED");
    mockMvc.perform(postAction(lecturerToken, second, "confirm")).andExpect(status().isOk());

    assertThat(assessmentStatus()).isEqualTo("SCORE_ENTERED");
    // Lô 1: 1 SV x 2 câu = 2 điểm; lô 2: 1 SV x 2 câu = 2 điểm -> 4
    assertThat(scoresOfSection()).hasSize(4);
    assertThat(scoreOf(s4, 0).getObjectId("importBatchId")).isEqualTo(new ObjectId(second));
    assertThat(scoreOf(s1, 0).getObjectId("importBatchId")).isEqualTo(new ObjectId(first));
  }

  @Test
  @DisplayName(
      "US-24/AC-3: lô HAS_ERRORS, UPLOADED (chưa kiểm tra), CONFIRMED, ROLLED_BACK đều không xác nhận được (422) và không ghi gì")
  void us24_ac3_onlyValidatedBatchCanBeConfirmed() throws Exception {
    String hasErrors = uploadOk(csv("NOPE,1,1"), "BLANK");
    mockMvc.perform(postAction(lecturerToken, hasErrors, "validate")).andExpect(status().isOk());
    mockMvc
        .perform(postAction(lecturerToken, hasErrors, "confirm"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
        .andExpect(jsonPath("$.message").value(containsString("HAS_ERRORS")));

    String uploaded = uploadOk(csv(s1.getStudentRef() + ",3,6"), "BLANK");
    mockMvc
        .perform(postAction(lecturerToken, uploaded, "confirm"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.message").value(containsString("UPLOADED")));

    String rolledBack = uploadOk(csv(s2.getStudentRef() + ",3,6"), "BLANK");
    mockMvc.perform(postAction(lecturerToken, rolledBack, "rollback")).andExpect(status().isOk());
    mockMvc
        .perform(postAction(lecturerToken, rolledBack, "confirm"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.message").value(containsString("ROLLED_BACK")));

    assertThat(scoresOfSection()).isEmpty();
    assertThat(assessmentStatus()).isEqualTo("APPROVED");

    String ok = uploadAndValidate(csv(s3.getStudentRef() + ",3,6"), "BLANK");
    mockMvc.perform(postAction(lecturerToken, ok, "confirm")).andExpect(status().isOk());
    mockMvc
        .perform(postAction(lecturerToken, ok, "confirm"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.message").value(containsString("đã được xác nhận")));
    assertThat(scoresOfSection()).hasSize(2);
    assertThat(importConfirmAudits(ok)).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "US-24: ghi danh đổi sau lúc kiểm tra (sinh viên rút lớp) -> xác nhận bị từ chối 422, lô vẫn VALIDATED, không ghi gì")
  void us24_confirmRechecksEnrollment() throws Exception {
    String id = uploadAndValidate(threeStudentsCsv(), "BLANK");
    mongo()
        .updateFirst(
            Query.query(Criteria.where("studentId").is(new ObjectId(s2.getId()))),
            new Update().set("status", "WITHDRAWN"),
            "enrollments");

    mockMvc
        .perform(postAction(lecturerToken, id, "confirm"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.message").value(containsString("đã đổi")));

    assertThat(batchDocument(id).getString("status")).isEqualTo("VALIDATED");
    assertThat(scoresOfSection()).isEmpty();
    assertThat(assessmentStatus()).isEqualTo("APPROVED");
    assertThat(importConfirmAudits(id)).isZero();
  }

  @Test
  @DisplayName(
      "US-24: hai lô cùng VALIDATED trùng một (sinh viên, câu): xác nhận lô A xong thì lô B bị từ chối, điểm của A giữ nguyên")
  void us24_secondBatchWithSameKeysIsRejectedAfterFirstConfirm() throws Exception {
    String a = uploadAndValidate(csv(s1.getStudentRef() + ",3,6"), "BLANK");
    String b =
        uploadAndValidate(csv(s1.getStudentRef() + ",1,1", s2.getStudentRef() + ",2,2"), "BLANK");

    mockMvc.perform(postAction(lecturerToken, a, "confirm")).andExpect(status().isOk());
    mockMvc
        .perform(postAction(lecturerToken, b, "confirm"))
        .andExpect(status().isUnprocessableEntity());

    assertThat(batchDocument(b).getString("status")).isEqualTo("VALIDATED");
    assertThat(scoresOfSection()).hasSize(2);
    assertThat(number(scoreOf(s1, 0), "rawScore")).isEqualTo(3.0);
    assertThat(scoreOf(s2, 0)).isNull();
  }

  @Test
  @DisplayName(
      "US-24 & US-18/AC-2: học kỳ bị khóa điểm sau khi kiểm tra -> không xác nhận được, không ghi gì")
  void us24_confirmRechecksTermLock() throws Exception {
    String id = uploadAndValidate(threeStudentsCsv(), "BLANK");
    setTermStatus(TermStatus.SCORE_LOCKED);

    mockMvc
        .perform(postAction(lecturerToken, id, "confirm"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.message").value(containsString("SCORE_LOCKED")));

    assertThat(scoresOfSection()).isEmpty();
    assertThat(assessmentStatus()).isEqualTo("APPROVED");
  }

  @Test
  @DisplayName(
      "US-24: hai người xác nhận cùng một lô đồng thời -> đúng một thành công, điểm chỉ ghi một lần, một bản audit")
  void us24_concurrentConfirmOnlyOneWins() throws Exception {
    String id = uploadAndValidate(threeStudentsCsv(), "BLANK");
    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    try {
      List<Future<Integer>> results = new ArrayList<>();
      for (int i = 0; i < 2; i++) {
        Callable<Integer> call =
            () -> {
              start.await();
              return mockMvc
                  .perform(postAction(lecturerToken, id, "confirm"))
                  .andReturn()
                  .getResponse()
                  .getStatus();
            };
        results.add(pool.submit(call));
      }
      start.countDown();
      List<Integer> statuses = new ArrayList<>();
      for (Future<Integer> result : results) {
        statuses.add(result.get());
      }

      assertThat(statuses).filteredOn(code -> code == 200).hasSize(1);
      // Người thua nhận 409 (xung đột ghi) hoặc 422 (đã thấy lô CONFIRMED), không bao giờ 500
      assertThat(statuses)
          .filteredOn(code -> code != 200)
          .allMatch(code -> code == 409 || code == 422);
    } finally {
      pool.shutdownNow();
    }

    assertThat(scoresOfSection()).hasSize(6);
    assertThat(importConfirmAudits(id)).isEqualTo(1);
    assertThat(batchDocument(id).getString("status")).isEqualTo("CONFIRMED");
  }

  @Test
  @DisplayName(
      "US-24/AC-4: hủy lô chưa xác nhận -> ROLLED_BACK, không có điểm; lô đã CONFIRMED không hủy được (422); hủy hai lần 422")
  void us24_ac4_rollbackOnlyUnconfirmedBatches() throws Exception {
    String uploaded = uploadOk(threeStudentsCsv(), "BLANK");
    mockMvc
        .perform(postAction(lecturerToken, uploaded, "rollback"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ROLLED_BACK"));
    mockMvc
        .perform(postAction(lecturerToken, uploaded, "rollback"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.message").value(containsString("đã bị hủy")));

    String validated = uploadAndValidate(csv(s1.getStudentRef() + ",3,6"), "BLANK");
    mockMvc
        .perform(postAction(lecturerToken, validated, "rollback"))
        .andExpect(jsonPath("$.status").value("ROLLED_BACK"));

    String erroneous = uploadOk(csv("NOPE,1,1"), "BLANK");
    mockMvc.perform(postAction(lecturerToken, erroneous, "validate")).andExpect(status().isOk());
    mockMvc
        .perform(postAction(lecturerToken, erroneous, "rollback"))
        .andExpect(jsonPath("$.status").value("ROLLED_BACK"));

    String confirmed = uploadAndValidate(csv(s2.getStudentRef() + ",3,6"), "BLANK");
    mockMvc.perform(postAction(lecturerToken, confirmed, "confirm")).andExpect(status().isOk());
    mockMvc
        .perform(postAction(lecturerToken, confirmed, "rollback"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.message").value(containsString("CONFIRMED")));
    assertThat(batchDocument(confirmed).getString("status")).isEqualTo("CONFIRMED");
    assertThat(scoresOfSection()).hasSize(2);
  }

  @Test
  @DisplayName(
      "Chung: người ngoài lớp không xác nhận/hủy được lô (403); ADMIN không có quyền ghi điểm (403); lô vẫn VALIDATED")
  void authorizationOnConfirmAndRollback() throws Exception {
    String id = uploadAndValidate(threeStudentsCsv(), "BLANK");

    for (String action : new String[] {"confirm", "rollback"}) {
      mockMvc.perform(postAction(otherLecturerToken, id, action)).andExpect(status().isForbidden());
      mockMvc.perform(postAction(adminToken, id, action)).andExpect(status().isForbidden());
      mockMvc.perform(postAction(null, id, action)).andExpect(status().isUnauthorized());
    }

    assertThat(batchDocument(id).getString("status")).isEqualTo("VALIDATED");
    assertThat(scoresOfSection()).isEmpty();
  }

  @Test
  @DisplayName(
      "Chung (ví dụ minh họa): lớp 4 ghi danh, tệp chỉ có 3 SV -> 6 điểm, sinh viên thứ 4 không có điểm nào, nhập vẫn hợp lệ")
  void studentsAbsentFromFileHaveNoScores() throws Exception {
    Student s4 = enroll(com.hcmute.lms.lmsapi.enums.EnrollmentStatus.ENROLLED);
    String id = uploadAndValidate(threeStudentsCsv(), "MISSING");
    mockMvc.perform(postAction(lecturerToken, id, "confirm")).andExpect(status().isOk());

    assertThat(scoresOfSection()).hasSize(6);
    assertThat(scoreOf(s4, 0)).isNull();
    assertThat(scoreOf(s4, 1)).isNull();
  }
}
