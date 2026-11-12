package com.hcmute.lms.lmsapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hcmute.lms.lmsapi.document.Assessment;
import com.hcmute.lms.lmsapi.document.CourseSection;
import com.hcmute.lms.lmsapi.document.UserScope;
import com.hcmute.lms.lmsapi.enums.AssessmentStatus;
import com.hcmute.lms.lmsapi.enums.Role;
import com.hcmute.lms.lmsapi.enums.SectionStatus;
import com.hcmute.lms.lmsapi.enums.TermStatus;
import com.hcmute.lms.lmsapi.service.ImportFileStorage;
import com.hcmute.lms.lmsapi.testsupport.ApiTestFixtures;
import com.hcmute.lms.lmsapi.testsupport.ScoreImportIntegrationBase;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

@DisplayName("F05b: tải tệp điểm, ánh xạ cột, chống nhập trùng (US-21)")
class ImportBatchUploadIntegrationTest extends ScoreImportIntegrationBase {

  @Autowired private ImportFileStorage fileStorage;

  private String goodCsv() {
    return csv(s1.getStudentRef() + ",3,6", s2.getStudentRef() + ",4,0");
  }

  private static String sha256(String text) throws Exception {
    return HexFormat.of()
        .formatHex(
            MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  @DisplayName(
      "US-21/AC-1: tải CSV hợp lệ cho lớp + bài APPROVED -> lô UPLOADED có checksum, tên tệp, người tải, tệp gốc đã lưu")
  void us21_ac1_uploadCreatesUploadedBatch() throws Exception {
    String csv = goodCsv();

    MvcResult result =
        mockMvc
            .perform(upload(lecturerToken, csv, "MISSING", defaultMapping()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("UPLOADED"))
            .andExpect(jsonPath("$.sectionId").value(section.getId()))
            .andExpect(jsonPath("$.assessmentId").value(assessment.getId()))
            .andExpect(jsonPath("$.fileName").value("scores.csv"))
            .andExpect(jsonPath("$.fileChecksum").value(sha256(csv)))
            .andExpect(jsonPath("$.sourceSystem").value("CSV"))
            .andExpect(jsonPath("$.reimportPolicy").value("REJECT_ON_CONFLICT"))
            .andExpect(jsonPath("$.uploadedBy").value(lecturer.getId()))
            .andExpect(jsonPath("$.stats").doesNotExist())
            .andReturn();

    String batchId = read(result, "$.id");
    String fileRef = read(result, "$.fileRef");
    assertThat(fileRef).isEqualTo(batchId + ".csv");
    assertThat(new String(fileStorage.load(fileRef), StandardCharsets.UTF_8)).isEqualTo(csv);
    Document stored = batchDocument(batchId);
    assertThat(stored.getString("status")).isEqualTo("UPLOADED");
    assertThat(stored.getString("fileChecksum")).isEqualTo(sha256(csv));
    assertThat(stored.getDate("createdAt")).isNotNull();
  }

  @Test
  @DisplayName(
      "US-21/AC-2: ánh xạ cột (externalRef hoặc _id) được xác định về _id câu hỏi, lưu trong lô và dùng lại cho lô sau cùng bài")
  void us21_ac2_columnMappingIsStoredAndReused() throws Exception {
    MvcResult first =
        mockMvc
            .perform(upload(lecturerToken, goodCsv(), "BLANK", defaultMapping()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.columnMapping.studentColumn").value("studentRef"))
            .andExpect(jsonPath("$.columnMapping.items", hasSize(2)))
            .andExpect(jsonPath("$.columnMapping.items[0].column").value("Q1"))
            .andExpect(jsonPath("$.columnMapping.items[0].item").value(itemId(0)))
            .andExpect(jsonPath("$.columnMapping.items[1].column").value("Q2"))
            .andExpect(jsonPath("$.columnMapping.items[1].item").value(itemId(1)))
            .andReturn();

    String firstId = read(first, "$.id");

    // Lô sau: tệp khác, không gửi columnMapping -> dùng lại ánh xạ của lô trước.
    mockMvc
        .perform(upload(lecturerToken, csv(s3.getStudentRef() + ",1,1"), "BLANK", null))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.not(firstId)))
        .andExpect(jsonPath("$.columnMapping.items[0].item").value(itemId(0)))
        .andExpect(jsonPath("$.columnMapping.items[1].item").value(itemId(1)));
  }

  @Test
  @DisplayName(
      "US-21/AC-2: tên cột tùy ý và cột sinh viên đặt tên khác được ánh xạ theo cấu hình, không cần tên Q1/Q2")
  void us21_ac2_customColumnNames() throws Exception {
    String csv = "Mã SV;Câu 1.5;Điểm câu 2\n" + s1.getStudentRef() + ";3;6\n";
    String mapping =
        "{\"studentColumn\":\"Mã SV\",\"items\":[{\"column\":\"Câu 1.5\",\"item\":\""
            + itemId(0)
            + "\"},{\"column\":\"Điểm câu 2\",\"item\":\""
            + externalRef(1)
            + "\"}]}";

    mockMvc
        .perform(upload(lecturerToken, csv, "MISSING", mapping))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.columnMapping.studentColumn").value("Mã SV"))
        .andExpect(jsonPath("$.columnMapping.items[0].column").value("Câu 1.5"))
        .andExpect(jsonPath("$.columnMapping.items[1].item").value(itemId(1)));
  }

  @Test
  @DisplayName(
      "US-21/AC-3: tải lại đúng tệp đã CONFIRMED (cùng lớp, bài, checksum) -> 409, không tạo lô mới")
  void us21_ac3_sameConfirmedFileIsRejected() throws Exception {
    String csv = goodCsv();
    String id = uploadAndValidate(csv, "MISSING");
    mockMvc.perform(postAction(lecturerToken, id, "confirm")).andExpect(status().isOk());
    long batchesBefore = countBatches();

    mockMvc
        .perform(upload(lecturerToken, csv, "MISSING", defaultMapping()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"));

    assertThat(countBatches()).isEqualTo(batchesBefore);
  }

  @Test
  @DisplayName(
      "US-21/AC-3 (biên): tệp trùng checksum nhưng lô trước chưa CONFIRMED (hoặc đã hủy) vẫn được tải lại")
  void us21_ac3_sameFileAllowedWhenPreviousWasNotConfirmed() throws Exception {
    String csv = goodCsv();
    String first = uploadOk(csv, "MISSING");
    mockMvc.perform(postAction(lecturerToken, first, "rollback")).andExpect(status().isOk());

    mockMvc
        .perform(upload(lecturerToken, csv, "MISSING", defaultMapping()))
        .andExpect(status().isCreated());
    mockMvc
        .perform(upload(lecturerToken, csv, "MISSING", defaultMapping()))
        .andExpect(status().isCreated());

    assertThat(countBatches()).isEqualTo(3);
  }

  @Test
  @DisplayName("US-21/AC-3 (biên): cùng nội dung nhưng ở bài/lớp khác không bị coi là nhập trùng")
  void us21_ac3_checksumIsScopedToSectionAndAssessment() throws Exception {
    String csv = goodCsv();
    String id = uploadAndValidate(csv, "MISSING");
    mockMvc.perform(postAction(lecturerToken, id, "confirm")).andExpect(status().isOk());
    Assessment second = fixtures.sectionAssessment(section, AssessmentStatus.LOCKED, 4, 6);
    String mapping =
        "{\"items\":[{\"column\":\"Q1\",\"item\":\""
            + second.getItems().get(0).getId()
            + "\"},{\"column\":\"Q2\",\"item\":\""
            + second.getItems().get(1).getId()
            + "\"}]}";

    mockMvc
        .perform(uploadTo(lecturerToken, section.getId(), second.getId(), csv, "MISSING", mapping))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("US-21/AC-4: bài DRAFT chưa LOCKED -> 422, không tạo lô")
  void us21_ac4_assessmentNotOpenForScores() throws Exception {
    Assessment closed = fixtures.sectionAssessment(section, AssessmentStatus.DRAFT, 4, 6);
    String mapping =
        "{\"items\":[{\"column\":\"Q1\",\"item\":\"" + closed.getItems().get(0).getId() + "\"}]}";

    mockMvc
        .perform(
            uploadTo(lecturerToken, section.getId(), closed.getId(), goodCsv(), "MISSING", mapping))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
        .andExpect(jsonPath("$.message").value(containsString("DRAFT")));

    assertThat(countBatches()).isZero();
  }

  @Test
  @DisplayName("US-21/AC-4: bài SCORE_ENTERED vẫn nhận lô mới (nhập thêm)")
  void us21_ac4_scoreEnteredAllowed() throws Exception {
    Assessment entered = fixtures.sectionAssessment(section, AssessmentStatus.SCORE_ENTERED, 4, 6);
    String mapping =
        "{\"items\":[{\"column\":\"Q1\",\"item\":\"" + entered.getItems().get(0).getId() + "\"}]}";
    mockMvc
        .perform(
            uploadTo(
                lecturerToken, section.getId(), entered.getId(), goodCsv(), "MISSING", mapping))
        .andExpect(status().isCreated());
  }

  @ParameterizedTest
  @EnumSource(
      value = TermStatus.class,
      names = {"SCORE_LOCKED", "CLOSED"})
  @DisplayName(
      "US-21/AC-4 & US-18/AC-2: học kỳ SCORE_LOCKED/CLOSED chặn nhập điểm -> 422, không tạo lô")
  void us21_ac4_termLockedBlocksImport(TermStatus status) throws Exception {
    setTermStatus(status);

    mockMvc
        .perform(upload(lecturerToken, goodCsv(), "MISSING", defaultMapping()))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.message").value(containsString(status.name())));

    assertThat(countBatches()).isZero();
  }

  @Test
  @DisplayName(
      "US-21/AC-4: lớp LOCKED chặn nhập điểm, lớp CLOSED (chỉ đóng băng ghi danh) vẫn nhập được")
  void us21_ac4_sectionLockedBlocksImport() throws Exception {
    setSectionStatus(SectionStatus.CLOSED);
    mockMvc
        .perform(upload(lecturerToken, goodCsv(), "MISSING", defaultMapping()))
        .andExpect(status().isCreated());

    setSectionStatus(SectionStatus.LOCKED);
    mockMvc
        .perform(
            upload(lecturerToken, csv(s3.getStudentRef() + ",1,1"), "MISSING", defaultMapping()))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.message").value(containsString("LOCKED")));
    assertThat(countBatches()).isEqualTo(1);
  }

  @Test
  @DisplayName("US-21: bài của lớp khác hoặc không tồn tại -> 404; id sai định dạng -> 400")
  void us21_assessmentMustBelongToSection() throws Exception {
    CourseSection otherSection = fixtures.section(section.getCourseId(), List.of(lecturer.getId()));
    Assessment foreign = fixtures.sectionAssessment(otherSection, AssessmentStatus.LOCKED, 4);

    mockMvc
        .perform(
            uploadTo(
                lecturerToken,
                section.getId(),
                foreign.getId(),
                goodCsv(),
                "MISSING",
                defaultMapping()))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            uploadTo(
                lecturerToken,
                section.getId(),
                ApiTestFixtures.missingId(),
                goodCsv(),
                "MISSING",
                defaultMapping()))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            uploadTo(
                lecturerToken,
                section.getId(),
                "khong-phai-objectid",
                goodCsv(),
                "MISSING",
                defaultMapping()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("US-23/AC-2: thiếu reimportPolicy -> 400")
  void us23_ac2_reimportPolicyIsMandatory() throws Exception {
    mockMvc
        .perform(
            rawUpload(lecturerToken, section.getId(), assessment.getId(), goodCsv())
                .param("columnMapping", defaultMapping()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

    assertThat(countBatches()).isZero();
  }

  @Test
  @DisplayName(
      "US-23/AC-2: chỉ REJECT_ON_CONFLICT được chấp nhận; OVERWRITE/UPDATE_BLANK_ONLY chờ Q19 -> 422; EXCEL -> 422")
  void us23_ac2_onlyRejectOnConflictAndNoExcel() throws Exception {
    for (String policy : new String[] {"OVERWRITE", "UPDATE_BLANK_ONLY"}) {
      mockMvc
          .perform(
              rawUpload(lecturerToken, section.getId(), assessment.getId(), goodCsv())
                  .param("emptyCellAs", "BLANK")
                  .param("reimportPolicy", policy)
                  .param("columnMapping", defaultMapping()))
          .andExpect(status().isUnprocessableEntity())
          .andExpect(jsonPath("$.message").value(containsString("Q19")));
    }
    mockMvc
        .perform(
            rawUpload(lecturerToken, section.getId(), assessment.getId(), goodCsv())
                .param("emptyCellAs", "BLANK")
                .param("reimportPolicy", "REJECT_ON_CONFLICT")
                .param("sourceSystem", "EXCEL")
                .param("columnMapping", defaultMapping()))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.message").value(containsString("Excel")));
    mockMvc
        .perform(
            rawUpload(lecturerToken, section.getId(), assessment.getId(), goodCsv())
                .param("emptyCellAs", "BLANK")
                .param("reimportPolicy", "REJECT_ON_CONFLICT")
                .param("sourceSystem", "MOODLE_EXPORT")
                .param("columnMapping", defaultMapping()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.sourceSystem").value("MOODLE_EXPORT"));

    assertThat(countBatches()).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "US-22/AC-4: lỗi cấp tệp (rỗng, chỉ tiêu đề, thiếu cột, ánh xạ sai, JSON sai) -> 400/422 rõ ràng, không 500, không tạo lô")
  void us22_ac4_fileLevelErrorsCreateNoBatch() throws Exception {
    assertRejected(upload(lecturerToken, "", "BLANK", defaultMapping()), 400, "rỗng");
    assertRejected(
        upload(lecturerToken, "studentRef,Q1,Q2\n", "BLANK", defaultMapping()),
        400,
        "không có dòng dữ liệu");
    assertRejected(
        upload(lecturerToken, "ref,Q1,Q2\nA,1,2\n", "BLANK", defaultMapping()),
        400,
        "thiếu cột mã sinh viên");
    assertRejected(
        upload(lecturerToken, "studentRef,Q1\nA,1\n", "BLANK", defaultMapping()),
        400,
        "thiếu cột [Q2]");
    assertRejected(
        upload(lecturerToken, "studentRef,Q1,q1\nA,1,2\n", "BLANK", defaultMapping()),
        400,
        "nhiều lần");
    assertRejected(upload(lecturerToken, goodCsv(), "BLANK", "{không phải json"), 400, "JSON");
    assertRejected(upload(lecturerToken, goodCsv(), "BLANK", null), 400, "Chưa có ánh xạ");
    assertRejected(
        upload(
            lecturerToken,
            goodCsv(),
            "BLANK",
            "{\"items\":[{\"column\":\"Q1\",\"item\":\"khong-co-cau-nay\"}]}"),
        422,
        "khớp");
    assertRejected(
        upload(
            lecturerToken,
            goodCsv(),
            "BLANK",
            "{\"items\":[{\"column\":\"Q1\",\"item\":\""
                + itemId(0)
                + "\"},"
                + "{\"column\":\"Q2\",\"item\":\""
                + itemId(0)
                + "\"}]}"),
        422,
        "nhiều cột");
    assertRejected(
        upload(
            lecturerToken,
            goodCsv(),
            "BLANK",
            "{\"items\":[{\"column\":\"Q1\",\"item\":\""
                + itemId(0)
                + "\"},"
                + "{\"column\":\"q1\",\"item\":\""
                + itemId(1)
                + "\"}]}"),
        400,
        "nhiều lần");
    assertRejected(
        upload(lecturerToken, goodCsv(), "BLANK", "{\"items\":[{\"column\":\"Q1\"}]}"),
        400,
        "column và item");

    assertThat(countBatches()).isZero();
  }

  private void assertRejected(
      org.springframework.test.web.servlet.RequestBuilder request, int expected, String part)
      throws Exception {
    mockMvc
        .perform(request)
        .andExpect(status().is(expected))
        .andExpect(jsonPath("$.message").value(containsString(part)));
  }

  @Test
  @DisplayName("US-21: ánh xạ hai cột cùng khớp một câu hỏi -> 422")
  void us21_duplicateItemMapping() throws Exception {
    mockMvc
        .perform(
            upload(
                lecturerToken,
                goodCsv(),
                "BLANK",
                "{\"items\":[{\"column\":\"Q1\",\"item\":\""
                    + itemId(0)
                    + "\"},{\"column\":\"Q2\",\"item\":\""
                    + itemId(0)
                    + "\"}]}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.message").value(containsString("nhiều")));
  }

  @Test
  @DisplayName(
      "Chung: người ngoài lớp (GV khác), ADMIN, vai trò khác và người chưa đăng nhập không tải được lô")
  void authorizationOnUpload() throws Exception {
    UserScope inSection =
        UserScope.builder().sectionIds(new java.util.ArrayList<>(List.of(section.getId()))).build();
    String qa = fixtures.bearerFor(Role.QA_OFFICER, inSection);
    String pm = fixtures.bearerFor(Role.PROGRAM_MANAGER, inSection);
    String head = fixtures.bearerFor(Role.DEPARTMENT_HEAD, inSection);

    mockMvc
        .perform(upload(otherLecturerToken, goodCsv(), "BLANK", defaultMapping()))
        .andExpect(status().isForbidden());
    // Plan F05b §6: chỉ LECTURER (lớp mình) nhập điểm; ADMIN và các vai trò đọc không có quyền này.
    for (String token : new String[] {adminToken, qa, pm, head}) {
      mockMvc
          .perform(upload(token, goodCsv(), "BLANK", defaultMapping()))
          .andExpect(status().isForbidden());
    }
    mockMvc
        .perform(upload(null, goodCsv(), "BLANK", defaultMapping()))
        .andExpect(status().isUnauthorized());

    assertThat(countBatches()).isZero();
  }
}
