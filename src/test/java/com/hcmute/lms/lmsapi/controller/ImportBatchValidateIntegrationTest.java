package com.hcmute.lms.lmsapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hcmute.lms.lmsapi.document.Student;
import com.hcmute.lms.lmsapi.document.UserScope;
import com.hcmute.lms.lmsapi.enums.EnrollmentStatus;
import com.hcmute.lms.lmsapi.enums.Role;
import com.hcmute.lms.lmsapi.enums.TermStatus;
import com.hcmute.lms.lmsapi.testsupport.ApiTestFixtures;
import com.hcmute.lms.lmsapi.testsupport.ScoreImportIntegrationBase;
import java.util.ArrayList;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@DisplayName("F05b: kiểm tra tệp điểm, lỗi theo dòng/cột, ô trống, nhập lại (US-22, US-23)")
class ImportBatchValidateIntegrationTest extends ScoreImportIntegrationBase {

  /**
   * Tệp có lỗi cài sẵn (s4 là sinh viên ENROLLED thứ tư). Dòng trong tệp (dòng 1 là tiêu đề):
   *
   * <pre>
   * 2: s1,3,6       hợp lệ
   * 3: s2,4.01,6    Q1 vượt tối đa 4
   * 4: NOPE,1,1     mã lạ
   * 5: withdrawn    đã rút khỏi lớp
   * 6: s1,2,2       trùng s1 ở dòng 2
   * 7: s3,x,7       Q1 không phải số; Q2 = 7 > 6
   * 8: ,1,1         thiếu mã sinh viên
   * 9: s4,1         thiếu ô Q2
   * </pre>
   */
  private String errorCsv(Student s4) {
    return csv(
        s1.getStudentRef() + ",3,6",
        s2.getStudentRef() + ",4.01,6",
        "NOPE,1,1",
        withdrawn.getStudentRef() + ",1,1",
        s1.getStudentRef() + ",2,2",
        s3.getStudentRef() + ",x,7",
        ",1,1",
        s4.getStudentRef() + ",1");
  }

  @Test
  @DisplayName(
      "US-22/AC-1 & AC-2 & AC-3: tệp lỗi cài sẵn -> HAS_ERRORS, đúng từng dòng/cột/loại/giá trị gốc, không báo nhầm dòng đúng")
  void us22_errorsAreReportedAtTheExactRowAndColumn() throws Exception {
    Student s4 = enroll(EnrollmentStatus.ENROLLED);
    String id = uploadOk(errorCsv(s4), "BLANK");

    mockMvc
        .perform(postAction(lecturerToken, id, "validate"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("HAS_ERRORS"))
        // 8 dòng dữ liệu; 7 dòng lỗi (dòng 3..9); 1 dòng hợp lệ (dòng 2) -> 2 điểm (Q1, Q2)
        .andExpect(jsonPath("$.stats.totalRows").value(8))
        .andExpect(jsonPath("$.stats.errorRows").value(7))
        .andExpect(jsonPath("$.stats.validRows").value(1))
        .andExpect(jsonPath("$.stats.scoreCount").value(2));

    // 8 lỗi: dòng 7 có hai lỗi (Q1 và Q2). Dòng 2 (hợp lệ) không xuất hiện.
    mockMvc
        .perform(getAs(lecturerToken, "/api/v1/import-batches/{id}/errors", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(8))
        .andExpect(jsonPath("$.content", hasSize(8)))
        .andExpect(jsonPath("$.content[0].rowNumber").value(3))
        .andExpect(jsonPath("$.content[0].column").value("Q1"))
        .andExpect(jsonPath("$.content[0].errorType").value("SCORE_OUT_OF_RANGE"))
        .andExpect(jsonPath("$.content[0].rawValue").value("4.01"))
        .andExpect(jsonPath("$.content[1].rowNumber").value(4))
        .andExpect(jsonPath("$.content[1].column").value("studentRef"))
        .andExpect(jsonPath("$.content[1].errorType").value("INVALID_STUDENT_CODE"))
        .andExpect(jsonPath("$.content[1].rawValue").value("NOPE"))
        .andExpect(jsonPath("$.content[2].rowNumber").value(5))
        .andExpect(jsonPath("$.content[2].errorType").value("INVALID_STUDENT_CODE"))
        .andExpect(jsonPath("$.content[2].rawValue").value(withdrawn.getStudentRef()))
        .andExpect(jsonPath("$.content[3].rowNumber").value(6))
        .andExpect(jsonPath("$.content[3].errorType").value("DUPLICATE_RECORD"))
        .andExpect(jsonPath("$.content[3].rawValue").value(s1.getStudentRef()))
        .andExpect(jsonPath("$.content[4].rowNumber").value(7))
        .andExpect(jsonPath("$.content[4].column").value("Q1"))
        .andExpect(jsonPath("$.content[4].errorType").value("SCORE_OUT_OF_RANGE"))
        .andExpect(jsonPath("$.content[4].rawValue").value("x"))
        .andExpect(jsonPath("$.content[5].rowNumber").value(7))
        .andExpect(jsonPath("$.content[5].column").value("Q2"))
        .andExpect(jsonPath("$.content[5].rawValue").value("7"))
        .andExpect(jsonPath("$.content[6].rowNumber").value(8))
        .andExpect(jsonPath("$.content[6].errorType").value("MISSING_FIELD"))
        .andExpect(jsonPath("$.content[6].column").value("studentRef"))
        .andExpect(jsonPath("$.content[7].rowNumber").value(9))
        .andExpect(jsonPath("$.content[7].errorType").value("MISSING_FIELD"))
        .andExpect(jsonPath("$.content[7].column").value("Q2"));

    // Kiểm tra trực tiếp CSDL: batchId lưu dạng ObjectId, mỗi lỗi có rowNumber số nguyên.
    List<Document> rows =
        mongo()
            .find(
                Query.query(Criteria.where("batchId").is(new org.bson.types.ObjectId(id))),
                Document.class,
                "import_row_errors");
    assertThat(rows).hasSize(8);
    // Chưa có điểm nào được ghi ở bước kiểm tra.
    assertThat(scoresOfSection()).isEmpty();
  }

  @Test
  @DisplayName(
      "US-22: phân trang lỗi (size 3: 3+3+2), sắp theo số dòng; size/page ngoài biên -> 400")
  void us22_errorsArePaged() throws Exception {
    Student s4 = enroll(EnrollmentStatus.ENROLLED);
    String id = uploadAndValidateAllowErrors(errorCsv(s4));

    mockMvc
        .perform(getAs(lecturerToken, "/api/v1/import-batches/{id}/errors?page=0&size=3", id))
        .andExpect(jsonPath("$.content", hasSize(3)))
        .andExpect(jsonPath("$.totalPages").value(3))
        .andExpect(jsonPath("$.last").value(false));
    mockMvc
        .perform(getAs(lecturerToken, "/api/v1/import-batches/{id}/errors?page=2&size=3", id))
        .andExpect(jsonPath("$.content", hasSize(2)))
        .andExpect(jsonPath("$.content[1].rowNumber").value(9))
        .andExpect(jsonPath("$.last").value(true));
    mockMvc
        .perform(getAs(lecturerToken, "/api/v1/import-batches/{id}/errors?size=0", id))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(getAs(lecturerToken, "/api/v1/import-batches/{id}/errors?page=-1", id))
        .andExpect(status().isBadRequest());
  }

  private String uploadAndValidateAllowErrors(String csv) throws Exception {
    String id = uploadOk(csv, "BLANK");
    mockMvc.perform(postAction(lecturerToken, id, "validate")).andExpect(status().isOk());
    return id;
  }

  @Test
  @DisplayName(
      "US-22: tệp hợp lệ -> VALIDATED, không có lỗi; kiểm tra lại nhiều lần không nhân đôi lỗi")
  void us22_validFileAndRevalidateIsIdempotent() throws Exception {
    String good = uploadOk(csv(s1.getStudentRef() + ",3,6"), "BLANK");
    mockMvc
        .perform(postAction(lecturerToken, good, "validate"))
        .andExpect(jsonPath("$.status").value("VALIDATED"))
        .andExpect(jsonPath("$.stats.errorRows").value(0))
        .andExpect(jsonPath("$.stats.scoreCount").value(2));
    mockMvc
        .perform(getAs(lecturerToken, "/api/v1/import-batches/{id}/errors", good))
        .andExpect(jsonPath("$.totalElements").value(0));

    Student s4 = enroll(EnrollmentStatus.ENROLLED);
    String bad = uploadOk(errorCsv(s4), "BLANK");
    for (int i = 0; i < 3; i++) {
      mockMvc.perform(postAction(lecturerToken, bad, "validate")).andExpect(status().isOk());
    }
    mockMvc
        .perform(getAs(lecturerToken, "/api/v1/import-batches/{id}/errors", bad))
        .andExpect(jsonPath("$.totalElements").value(8));
  }

  @Test
  @DisplayName(
      "US-22: sau khi sửa ghi danh, kiểm tra lại cùng lô đổi kết quả (sinh viên ghi danh lại -> hết lỗi)")
  void us22_revalidateSeesNewEnrollment() throws Exception {
    String id = uploadAndValidateAllowErrors(csv(withdrawn.getStudentRef() + ",1,1"));
    assertThat(batchDocument(id).getString("status")).isEqualTo("HAS_ERRORS");

    mongo()
        .updateFirst(
            Query.query(
                Criteria.where("studentId").is(new org.bson.types.ObjectId(withdrawn.getId()))),
            new org.springframework.data.mongodb.core.query.Update().set("status", "ENROLLED"),
            "enrollments");

    mockMvc
        .perform(postAction(lecturerToken, id, "validate"))
        .andExpect(jsonPath("$.status").value("VALIDATED"));
    mockMvc
        .perform(getAs(lecturerToken, "/api/v1/import-batches/{id}/errors", id))
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  @DisplayName(
      "US-22: kiểm tra lô đã CONFIRMED/ROLLED_BACK -> 422; học kỳ khóa điểm sau khi tải -> 422")
  void us22_validateRespectsStateAndLocks() throws Exception {
    String confirmed = uploadAndValidate(csv(s1.getStudentRef() + ",3,6"), "BLANK");
    mockMvc.perform(postAction(lecturerToken, confirmed, "confirm")).andExpect(status().isOk());
    mockMvc
        .perform(postAction(lecturerToken, confirmed, "validate"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.message").value(containsString("CONFIRMED")));

    String rolledBack = uploadOk(csv(s2.getStudentRef() + ",3,6"), "BLANK");
    mockMvc.perform(postAction(lecturerToken, rolledBack, "rollback")).andExpect(status().isOk());
    mockMvc
        .perform(postAction(lecturerToken, rolledBack, "validate"))
        .andExpect(status().isUnprocessableEntity());

    String open = uploadOk(csv(s3.getStudentRef() + ",3,6"), "BLANK");
    setTermStatus(TermStatus.SCORE_LOCKED);
    mockMvc
        .perform(postAction(lecturerToken, open, "validate"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.message").value(containsString("SCORE_LOCKED")));
  }

  @Test
  @DisplayName("US-23/AC-1: ô trống tự động thành điểm 0.0 sau khi xác nhận")
  void us23_ac1_emptyCellAssignedZero() throws Exception {
    String id =
        uploadAndValidate(csv(s1.getStudentRef() + ",,6", s2.getStudentRef() + ",4,"), "BLANK");
    mockMvc.perform(postAction(lecturerToken, id, "confirm")).andExpect(status().isOk());

    Document s1q1 = scoreOf(s1, 0);
    assertThat(((Number) s1q1.get("rawScore")).doubleValue()).isEqualTo(0.0);
    assertThat(((Number) s1q1.get("maxScore")).doubleValue()).isEqualTo(4.0);
    Document s2q2 = scoreOf(s2, 1);
    assertThat(((Number) s2q2.get("rawScore")).doubleValue()).isEqualTo(0.0);
    // Ô có điểm lưu đúng điểm
    assertThat(((Number) scoreOf(s1, 1).get("rawScore")).doubleValue()).isEqualTo(6.0);
  }

  @Test
  @DisplayName("US-23/AC-1: điểm 0 gõ rõ ràng hay ô trống đều lưu rawScore = 0.0 hợp lệ")
  void us23_ac1_explicitZeroAndBlankBothZero() throws Exception {
    String id = uploadAndValidate(csv(s1.getStudentRef() + ",0,"), "BLANK");
    mockMvc.perform(postAction(lecturerToken, id, "confirm")).andExpect(status().isOk());

    assertThat(((Number) scoreOf(s1, 0).get("rawScore")).doubleValue()).isEqualTo(0.0);
    assertThat(((Number) scoreOf(s1, 1).get("rawScore")).doubleValue()).isEqualTo(0.0);
  }

  @Test
  @DisplayName(
      "US-23/AC-2: REJECT_ON_CONFLICT - lô sau trùng (sinh viên, câu) đã có điểm -> HAS_ERRORS, không xác nhận được, điểm cũ giữ nguyên")
  void us23_ac2_conflictIsRejectedAndOldScoresStay() throws Exception {
    String first =
        uploadAndValidate(csv(s1.getStudentRef() + ",3,6", s2.getStudentRef() + ",4,0"), "BLANK");
    mockMvc.perform(postAction(lecturerToken, first, "confirm")).andExpect(status().isOk());

    // Lô 2: s1 đã có điểm cả hai câu (dòng 2 lỗi kép); s3 chưa có (dòng 3 hợp lệ).
    String second =
        uploadOk(csv(s1.getStudentRef() + ",1,1", s3.getStudentRef() + ",2,2"), "BLANK");
    mockMvc
        .perform(postAction(lecturerToken, second, "validate"))
        .andExpect(jsonPath("$.status").value("HAS_ERRORS"))
        .andExpect(jsonPath("$.stats.totalRows").value(2))
        .andExpect(jsonPath("$.stats.errorRows").value(1))
        .andExpect(jsonPath("$.stats.scoreCount").value(2));
    mockMvc
        .perform(getAs(lecturerToken, "/api/v1/import-batches/{id}/errors", second))
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.content[0].rowNumber").value(2))
        .andExpect(jsonPath("$.content[0].column").value("Q1"))
        .andExpect(jsonPath("$.content[0].errorType").value("DUPLICATE_RECORD"))
        .andExpect(jsonPath("$.content[1].column").value("Q2"))
        .andExpect(jsonPath("$.content[1].errorType").value("DUPLICATE_RECORD"));

    mockMvc
        .perform(postAction(lecturerToken, second, "confirm"))
        .andExpect(status().isUnprocessableEntity());

    assertThat(scoresOfSection()).hasSize(4);
    assertThat(((Number) scoreOf(s1, 0).get("rawScore")).doubleValue()).isEqualTo(3.0);
    assertThat(((Number) scoreOf(s1, 1).get("rawScore")).doubleValue()).isEqualTo(6.0);
    assertThat(scoresOfSection())
        .extracting(d -> d.getObjectId("studentId").toHexString())
        .doesNotContain(s3.getId());
  }

  @Test
  @DisplayName(
      "Chung: người ngoài lớp không kiểm tra/xem lỗi lô; QA_OFFICER trong phạm vi lớp xem được nhưng không kiểm tra; id lạ -> 403/400")
  void authorizationOnBatchEndpoints() throws Exception {
    String id = uploadAndValidateAllowErrors(csv("NOPE,1,1"));
    String qa =
        fixtures.bearerFor(
            Role.QA_OFFICER,
            UserScope.builder().sectionIds(new ArrayList<>(List.of(section.getId()))).build());

    mockMvc
        .perform(postAction(otherLecturerToken, id, "validate"))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(getAs(otherLecturerToken, "/api/v1/import-batches/{id}", id))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(getAs(otherLecturerToken, "/api/v1/import-batches/{id}/errors", id))
        .andExpect(status().isForbidden());
    mockMvc.perform(postAction(qa, id, "validate")).andExpect(status().isForbidden());
    mockMvc
        .perform(getAs(qa, "/api/v1/import-batches/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("HAS_ERRORS"));
    mockMvc.perform(getAs(qa, "/api/v1/import-batches/{id}/errors", id)).andExpect(status().isOk());
    mockMvc
        .perform(
            getAs(
                lecturerToken,
                "/api/v1/sections/{s}/assessments/{a}/import-batches",
                section.getId(),
                assessment.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)));
    mockMvc
        .perform(
            getAs(
                otherLecturerToken,
                "/api/v1/sections/{s}/assessments/{a}/import-batches",
                section.getId(),
                assessment.getId()))
        .andExpect(status().isForbidden());
    // Lô không tồn tại: fail-closed 403
    mockMvc
        .perform(getAs(lecturerToken, "/api/v1/import-batches/{id}", ApiTestFixtures.missingId()))
        .andExpect(status().isForbidden());
    // Id sai định dạng: @PreAuthorize (fail-closed) chạy trước kiểm tra @Pattern nên là 403, giống
    // các API theo lớp của F05a (TD-22).
    mockMvc
        .perform(getAs(lecturerToken, "/api/v1/import-batches/{id}", "abc"))
        .andExpect(status().isForbidden());
    mockMvc.perform(postAction(null, id, "validate")).andExpect(status().isUnauthorized());
  }

  /** Điểm của sinh viên ở câu thứ {@code itemIndex} (0 = Q1). */
  private Document scoreOf(Student student, int itemIndex) {
    return mongo()
        .findOne(
            Query.query(
                Criteria.where("studentId")
                    .is(new org.bson.types.ObjectId(student.getId()))
                    .and("itemId")
                    .is(new org.bson.types.ObjectId(itemId(itemIndex)))),
            Document.class,
            "scores");
  }
}
