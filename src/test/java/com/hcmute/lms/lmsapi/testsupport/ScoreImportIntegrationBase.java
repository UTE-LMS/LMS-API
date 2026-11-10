package com.hcmute.lms.lmsapi.testsupport;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import com.hcmute.lms.lmsapi.document.Assessment;
import com.hcmute.lms.lmsapi.document.CourseSection;
import com.hcmute.lms.lmsapi.document.Student;
import com.hcmute.lms.lmsapi.document.Syllabus;
import com.hcmute.lms.lmsapi.document.Term;
import com.hcmute.lms.lmsapi.document.User;
import com.hcmute.lms.lmsapi.document.UserScope;
import com.hcmute.lms.lmsapi.enums.AssessmentStatus;
import com.hcmute.lms.lmsapi.enums.EnrollmentStatus;
import com.hcmute.lms.lmsapi.enums.Role;
import com.hcmute.lms.lmsapi.enums.SectionStatus;
import com.hcmute.lms.lmsapi.enums.SyllabusStatus;
import com.hcmute.lms.lmsapi.enums.TermStatus;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Dữ liệu và hàm dùng chung cho integration test nhập điểm (F05b). Dựng sẵn: một lớp với học kỳ
 * OPEN, bài đánh giá APPROVED gồm Q1 (tối đa 4) và Q2 (tối đa 6) (ví dụ minh họa, "DỮ LIỆU THỬ
 * NGHIỆM"), 3 sinh viên đang ENROLLED (s1, s2, s3) và 1 sinh viên đã WITHDRAWN.
 */
public abstract class ScoreImportIntegrationBase extends IntegrationTestBase {

  protected static final String BATCHES_URL =
      "/api/v1/sections/{sectionId}/assessments/{assessmentId}/import-batches";

  protected Term term;
  protected CourseSection section;
  protected Assessment assessment;
  protected User lecturer;
  protected Student s1;
  protected Student s2;
  protected Student s3;
  protected Student withdrawn;
  protected String lecturerToken;
  protected String otherLecturerToken;
  protected String adminToken;

  @BeforeEach
  void setUpScoreImportFixtures() {
    term = fixtures.term();
    Syllabus syllabus = fixtures.syllabus(fixtures.course(), SyllabusStatus.CONFIRMED);
    lecturer = fixtures.user(Role.LECTURER, UserScope.builder().build());
    section = fixtures.section(syllabus.getCourseId(), List.of(lecturer.getId()));
    section.setSyllabusId(syllabus.getId());
    section.setTermId(term.getId());
    section = mongo().save(section);
    assessment = fixtures.sectionAssessment(section, AssessmentStatus.APPROVED, 4, 6);

    s1 = enroll(EnrollmentStatus.ENROLLED);
    s2 = enroll(EnrollmentStatus.ENROLLED);
    s3 = enroll(EnrollmentStatus.ENROLLED);
    withdrawn = enroll(EnrollmentStatus.WITHDRAWN);

    lecturerToken = fixtures.bearer(lecturer);
    otherLecturerToken = fixtures.bearerFor(Role.LECTURER, UserScope.builder().build());
    adminToken = fixtures.bearerFor(Role.ADMIN, UserScope.builder().build());
  }

  protected Student enroll(EnrollmentStatus status) {
    Student student = fixtures.student();
    fixtures.enrollment(section, student, status);
    return student;
  }

  protected String itemId(int index) {
    return assessment.getItems().get(index).getId();
  }

  protected String externalRef(int index) {
    return assessment.getItems().get(index).getExternalRef();
  }

  /** Ánh xạ mặc định: cột Q1 theo externalRef của câu 1, cột Q2 theo _id của câu 2. */
  protected String defaultMapping() {
    return "{\"items\":[{\"column\":\"Q1\",\"item\":\""
        + externalRef(0)
        + "\"},{\"column\":\"Q2\",\"item\":\""
        + itemId(1)
        + "\"}]}";
  }

  /** Tệp CSV: dòng tiêu đề {@code studentRef,Q1,Q2} rồi các dòng cho trước (đã nối bằng \n). */
  protected static String csv(String... rows) {
    return "studentRef,Q1,Q2\n" + String.join("\n", rows) + "\n";
  }

  protected MockMultipartHttpServletRequestBuilder upload(
      String bearer, String csv, String emptyCellAs, String mappingJson) {
    return uploadTo(bearer, section.getId(), assessment.getId(), csv, emptyCellAs, mappingJson);
  }

  protected MockMultipartHttpServletRequestBuilder uploadTo(
      String bearer,
      String sectionId,
      String assessmentId,
      String csv,
      String emptyCellAs,
      String mappingJson) {
    MockMultipartHttpServletRequestBuilder builder =
        rawUpload(bearer, sectionId, assessmentId, csv)
            .param("reimportPolicy", "REJECT_ON_CONFLICT");
    if (emptyCellAs != null) {
      builder.param("emptyCellAs", emptyCellAs);
    }
    if (mappingJson != null) {
      builder.param("columnMapping", mappingJson);
    }
    return builder;
  }

  /** Chỉ có phần tệp và token; test tự thêm tham số (để thử giá trị tham số lạ). */
  protected MockMultipartHttpServletRequestBuilder rawUpload(
      String bearer, String sectionId, String assessmentId, String csv) {
    MockMultipartHttpServletRequestBuilder builder =
        multipart(BATCHES_URL, sectionId, assessmentId)
            .file(
                new MockMultipartFile(
                    "file", "scores.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)));
    return bearer == null ? builder : builder.header(HttpHeaders.AUTHORIZATION, bearer);
  }

  protected MockHttpServletRequestBuilder postAction(String bearer, String id, String action) {
    return withAuth(
        MockMvcRequestBuilders.post("/api/v1/import-batches/{id}/{action}", id, action), bearer);
  }

  /** Tải tệp (ánh xạ mặc định) và trả về id lô; kiểm tra 201. */
  protected String uploadOk(String csv, String emptyCellAs) throws Exception {
    MvcResult result =
        mockMvc.perform(upload(lecturerToken, csv, emptyCellAs, defaultMapping())).andReturn();
    org.assertj.core.api.Assertions.assertThat(result.getResponse().getStatus())
        .as(result.getResponse().getContentAsString())
        .isEqualTo(201);
    return read(result, "$.id");
  }

  /** Tải tệp rồi kiểm tra; trả về id lô. */
  protected String uploadAndValidate(String csv, String emptyCellAs) throws Exception {
    String id = uploadOk(csv, emptyCellAs);
    mockMvc
        .perform(postAction(lecturerToken, id, "validate"))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
    return id;
  }

  // ---------------------------------------------------------------- đọc CSDL

  protected long countBatches() {
    return mongo()
        .count(
            Query.query(Criteria.where("sectionId").is(new ObjectId(section.getId()))),
            "import_batches");
  }

  protected List<Document> scoresOfSection() {
    return new ArrayList<>(
        mongo()
            .find(
                Query.query(Criteria.where("sectionId").is(new ObjectId(section.getId())))
                    .with(org.springframework.data.domain.Sort.by("sourceRow", "itemId")),
                Document.class,
                "scores"));
  }

  protected Document batchDocument(String batchId) {
    return mongo()
        .findOne(
            Query.query(Criteria.where("_id").is(new ObjectId(batchId))),
            Document.class,
            "import_batches");
  }

  protected void setTermStatus(TermStatus status) {
    Term current = Objects.requireNonNull(mongo().findById(term.getId(), Term.class));
    current.setStatus(status);
    mongo().save(current);
  }

  protected void setSectionStatus(SectionStatus status) {
    CourseSection current =
        Objects.requireNonNull(mongo().findById(section.getId(), CourseSection.class));
    current.setStatus(status);
    mongo().save(current);
  }

  protected String assessmentStatus() {
    return Objects.requireNonNull(
            mongo()
                .findOne(
                    Query.query(Criteria.where("_id").is(new ObjectId(assessment.getId()))),
                    Document.class,
                    "assessments"))
        .getString("status");
  }

  protected long importConfirmAudits(String batchId) {
    // TD-34: Spring Data lưu AuditEntity.id thành entity._id (ObjectId khi mã hợp lệ)
    return mongo()
        .count(
            Query.query(
                Criteria.where("action")
                    .is("IMPORT_CONFIRM")
                    .and("entity._id")
                    .is(new ObjectId(batchId))),
            "audit_logs");
  }
}
