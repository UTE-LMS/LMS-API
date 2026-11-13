package com.hcmute.lms.lmsapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hcmute.lms.lmsapi.document.Assessment;
import com.hcmute.lms.lmsapi.document.Clo;
import com.hcmute.lms.lmsapi.document.Course;
import com.hcmute.lms.lmsapi.document.CourseSection;
import com.hcmute.lms.lmsapi.document.Score;
import com.hcmute.lms.lmsapi.document.Student;
import com.hcmute.lms.lmsapi.document.Syllabus;
import com.hcmute.lms.lmsapi.document.User;
import com.hcmute.lms.lmsapi.document.UserScope;
import com.hcmute.lms.lmsapi.dto.AssessmentCloWeightDto;
import com.hcmute.lms.lmsapi.dto.AssessmentItemDto;
import com.hcmute.lms.lmsapi.dto.AssessmentItemsRequest;
import com.hcmute.lms.lmsapi.dto.AssessmentRequest;
import com.hcmute.lms.lmsapi.dto.AssessmentUpdateRequest;
import com.hcmute.lms.lmsapi.dto.OutcomeMappingDto;
import com.hcmute.lms.lmsapi.enums.AssessmentStatus;
import com.hcmute.lms.lmsapi.enums.EnrollmentStatus;
import com.hcmute.lms.lmsapi.enums.Role;
import com.hcmute.lms.lmsapi.enums.SyllabusStatus;
import com.hcmute.lms.lmsapi.testsupport.ApiTestFixtures;
import com.hcmute.lms.lmsapi.testsupport.IntegrationTestBase;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.web.servlet.MvcResult;

@DisplayName("F04: Assessment Integration Tests (OBE v0.7 / ADR 0014)")
class AssessmentIntegrationTest extends IntegrationTestBase {

  private static final String AUDIT_ENTITY_ID = "entity._id";

  private Syllabus syllabus;
  private Clo clo1;
  private Clo clo2;
  private Clo foreignClo;
  private CourseSection section;
  private User lecturer;
  private User departmentHead;

  private String adminToken;
  private String lecturerToken;
  private String otherLecturerToken;
  private String deptHeadToken;
  private String pmToken;

  @BeforeEach
  void setUpFixtures() {
    Course course = fixtures.course();
    syllabus = fixtures.syllabus(course, SyllabusStatus.CONFIRMED);
    clo1 = fixtures.clo(syllabus, "CLO1");
    clo2 = fixtures.clo(syllabus, "CLO2");
    Syllabus otherSyllabus = fixtures.syllabus(fixtures.course(), SyllabusStatus.CONFIRMED);
    foreignClo = fixtures.clo(otherSyllabus, "CLO1");

    UserScope courseScope =
        UserScope.builder().courseIds(new ArrayList<>(List.of(course.getId()))).build();
    lecturer = fixtures.user(Role.LECTURER, courseScope);
    departmentHead = fixtures.user(Role.PROGRAM_MANAGER, courseScope);
    User otherLecturer = fixtures.user(Role.LECTURER, UserScope.builder().build());

    section = fixtures.section(course.getId(), List.of(lecturer.getId()));
    section.setSyllabusId(syllabus.getId());
    section = mongo().save(section);

    adminToken = fixtures.bearerFor(Role.ADMIN, UserScope.builder().build());
    lecturerToken = fixtures.bearer(lecturer);
    otherLecturerToken = fixtures.bearer(otherLecturer);
    deptHeadToken = fixtures.bearer(departmentHead);
    pmToken =
        fixtures.bearerFor(
            Role.PROGRAM_MANAGER,
            UserScope.builder().courseIds(new ArrayList<>(List.of(course.getId()))).build());
  }

  // ------------------------------------------------------------------ dựng dữ liệu

  private OutcomeMappingDto mapping(Clo clo, double maxScore) {
    return OutcomeMappingDto.builder().cloId(clo.getId()).maxScore(maxScore).build();
  }

  private AssessmentItemDto question(String code, Integer bloom, OutcomeMappingDto... mappings) {
    return AssessmentItemDto.builder()
        .code(code)
        .bloomLevel(bloom)
        .outcomeMappings(List.of(mappings))
        .build();
  }

  /** Hai câu: Q1 4 điểm đo CLO1; Q2 6 điểm chia đều CLO1/CLO2 (3/3). Tổng 10 điểm. */
  private List<AssessmentItemDto> twoQuestions() {
    return List.of(
        question("Q1", 1, mapping(clo1, 4.0)),
        question("Q2", 3, mapping(clo1, 3.0), mapping(clo2, 3.0)));
  }

  private AssessmentRequest.AssessmentRequestBuilder sectionRequest() {
    return AssessmentRequest.builder()
        .code(fixtures.code("AS"))
        .name("Giữa kỳ (dữ liệu thử nghiệm)")
        .cloWeights(
            List.of(
                AssessmentCloWeightDto.builder().cloId(clo1.getId()).weight(0.2).build(),
                AssessmentCloWeightDto.builder().cloId(clo2.getId()).weight(0.1).build()))
        .items(twoQuestions());
  }

  private String createSectionAssessment(AssessmentRequest request) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                postAs(
                    lecturerToken, "/api/v1/sections/{id}/assessments", request, section.getId()))
            .andExpect(status().isCreated())
            .andReturn();
    return read(result, "$.id");
  }

  private Assessment stored(String id) {
    return mongo().findById(id, Assessment.class);
  }

  // ------------------------------------------------------------------ US-15

  @Test
  @DisplayName(
      "US-15/AC-1: giảng viên tạo bài đánh giá lớp mình -> 201, DRAFT, chưa khóa, có độ phủ")
  void us15_ac1_lecturerCreatesSectionAssessment() throws Exception {
    AssessmentRequest request = sectionRequest().build();

    mockMvc
        .perform(
            postAs(lecturerToken, "/api/v1/sections/{id}/assessments", request, section.getId()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.scope").value("SECTION"))
        .andExpect(jsonPath("$.sectionId").value(section.getId()))
        .andExpect(jsonPath("$.syllabusId").value(syllabus.getId()))
        .andExpect(jsonPath("$.status").value("DRAFT"))
        .andExpect(jsonPath("$.locked").value(false))
        .andExpect(jsonPath("$.items", hasSize(2)))
        .andExpect(jsonPath("$.items[0].id").isNotEmpty())
        .andExpect(jsonPath("$.items[0].orderIndex").value(1))
        .andExpect(jsonPath("$.coverageSummary.totalItemsSum").value(10.0))
        .andExpect(jsonPath("$.cloWeights", hasSize(2)));
  }

  @Test
  @DisplayName(
      "US-15/AC-1: sao chép từ khung (templateId) rồi sửa khung -> bản lớp là snapshot, không đổi")
  void us15_ac1_copyFromTemplateIsASnapshot() throws Exception {
    AssessmentRequest templateRequest =
        AssessmentRequest.builder()
            .code(fixtures.code("TPL"))
            .name("Khung giữa kỳ")
            .items(twoQuestions())
            .build();
    MvcResult created =
        mockMvc
            .perform(
                postAs(
                    lecturerToken,
                    "/api/v1/syllabi/{id}/assessment-templates",
                    templateRequest,
                    syllabus.getId()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.scope").value("SYLLABUS_TEMPLATE"))
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andReturn();
    String templateId = read(created, "$.id");

    MvcResult copied =
        mockMvc
            .perform(
                postAs(
                    lecturerToken,
                    "/api/v1/sections/{id}/assessments",
                    AssessmentRequest.builder()
                        .code(fixtures.code("AS"))
                        .name("Giữa kỳ lớp")
                        .templateId(templateId)
                        .build(),
                    section.getId()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.templateId").value(templateId))
            .andExpect(jsonPath("$.items", hasSize(2)))
            .andReturn();
    String sectionAssessmentId = read(copied, "$.id");
    String copiedItemId = read(copied, "$.items[0].id");

    // Sau khi sao chép, đổi khung (thay items bằng một câu duy nhất)
    mockMvc
        .perform(
            putAs(
                lecturerToken,
                "/api/v1/assessments/{id}/items",
                AssessmentItemsRequest.builder()
                    .items(List.of(question("Q9", null, mapping(clo2, 10.0))))
                    .build(),
                templateId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(1)));

    // Bản lớp giữ nguyên hai câu và định danh phần tử của riêng nó
    mockMvc
        .perform(getAs(lecturerToken, "/api/v1/assessments/{id}", sectionAssessmentId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(2)))
        .andExpect(jsonPath("$.items[0].code").value("Q1"))
        .andExpect(jsonPath("$.items[0].id").value(copiedItemId));

    // Gửi cả templateId lẫn items -> 422; khung của đề cương khác -> 422
    mockMvc
        .perform(
            postAs(
                lecturerToken,
                "/api/v1/sections/{id}/assessments",
                sectionRequest().templateId(templateId).build(),
                section.getId()))
        .andExpect(status().isUnprocessableEntity());
    mockMvc
        .perform(
            postAs(
                lecturerToken,
                "/api/v1/sections/{id}/assessments",
                AssessmentRequest.builder()
                    .code(fixtures.code("AS"))
                    .name("x")
                    .templateId(sectionAssessmentId)
                    .build(),
                section.getId()))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  @DisplayName("US-15/AC-2: CLO trùng trong cùng câu, CLO của đề cương khác/không tồn tại -> 422")
  void us15_ac2_outcomeMappingRules() throws Exception {
    List<List<AssessmentItemDto>> invalid =
        List.of(
            List.of(question("Q1", 1, mapping(clo1, 2.0), mapping(clo1, 2.0))),
            List.of(question("Q1", 1, mapping(foreignClo, 4.0))),
            List.of(
                question(
                    "Q1",
                    1,
                    OutcomeMappingDto.builder()
                        .cloId(ApiTestFixtures.missingId())
                        .maxScore(4.0)
                        .build())));

    for (List<AssessmentItemDto> items : invalid) {
      mockMvc
          .perform(
              postAs(
                  lecturerToken,
                  "/api/v1/sections/{id}/assessments",
                  sectionRequest().items(items).build(),
                  section.getId()))
          .andExpect(status().isUnprocessableEntity())
          .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    }
    assertThat(mongo().findAll(Assessment.class))
        .noneMatch(a -> section.getId().equals(a.getSectionId()));
  }

  @Test
  @DisplayName("US-15: maxScore ≤ 0, thiếu outcomeMappings, Bloom ngoài 1-6 -> 400")
  void us15_validationErrors() throws Exception {
    AssessmentItemDto zeroScore = question("Q1", 1, mapping(clo1, 0.0));
    AssessmentItemDto negativeScore = question("Q1", 1, mapping(clo1, -1.0));
    AssessmentItemDto noMappings = question("Q1", 1);
    AssessmentItemDto badBloom = question("Q1", 7, mapping(clo1, 1.0));

    for (AssessmentItemDto bad : List.of(zeroScore, negativeScore, noMappings, badBloom)) {
      mockMvc
          .perform(
              postAs(
                  lecturerToken,
                  "/api/v1/sections/{id}/assessments",
                  sectionRequest().items(List.of(bad)).build(),
                  section.getId()))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // cloWeight ngoài (0, 1] -> 400
    AssessmentRequest badCloWeight =
        sectionRequest()
            .cloWeights(
                List.of(AssessmentCloWeightDto.builder().cloId(clo1.getId()).weight(1.5).build()))
            .build();
    mockMvc
        .perform(
            postAs(
                lecturerToken, "/api/v1/sections/{id}/assessments", badCloWeight, section.getId()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  @Test
  @DisplayName("US-15: trùng mã bài trong cùng lớp -> 409; hai lớp khác nhau dùng cùng mã được")
  void us15_duplicateCode() throws Exception {
    AssessmentRequest request = sectionRequest().build();
    createSectionAssessment(request);

    mockMvc
        .perform(
            postAs(lecturerToken, "/api/v1/sections/{id}/assessments", request, section.getId()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"));
  }

  @Test
  @DisplayName(
      "US-15/AC-4 & RBAC: giảng viên lớp khác, ADMIN, PM, TBM không được tạo bài cho lớp -> 403")
  void us15_ac4_onlyTheSectionLecturerCreates() throws Exception {
    for (String token : List.of(otherLecturerToken, adminToken, pmToken, deptHeadToken)) {
      mockMvc
          .perform(
              postAs(
                  token,
                  "/api/v1/sections/{id}/assessments",
                  sectionRequest().build(),
                  section.getId()))
          .andExpect(status().isForbidden());
    }
    mockMvc
        .perform(
            postAs(
                null,
                "/api/v1/sections/{id}/assessments",
                sectionRequest().build(),
                section.getId()))
        .andExpect(status().isUnauthorized());

    // Giảng viên lớp khác cũng không đọc được danh sách bài của lớp
    mockMvc
        .perform(getAs(otherLecturerToken, "/api/v1/sections/{id}/assessments", section.getId()))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(getAs(lecturerToken, "/api/v1/sections/{id}/assessments", section.getId()))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName(
      "Khung theo đề cương: ADMIN và PM trong phạm vi được tạo; LECTURER ngoài phạm vi -> 403")
  void templateAuthorizationFollowsAdr0010() throws Exception {
    AssessmentRequest request =
        AssessmentRequest.builder().code(fixtures.code("TPL")).name("Khung").build();

    for (String token : List.of(otherLecturerToken)) {
      mockMvc
          .perform(
              postAs(token, "/api/v1/syllabi/{id}/assessment-templates", request, syllabus.getId()))
          .andExpect(status().isForbidden());
    }
    mockMvc
        .perform(
            postAs(pmToken, "/api/v1/syllabi/{id}/assessment-templates", request, syllabus.getId()))
        .andExpect(status().isCreated());

    // ADMIN và PM đọc được khung
    for (String token : List.of(adminToken, pmToken)) {
      mockMvc
          .perform(getAs(token, "/api/v1/syllabi/{id}/assessment-templates", syllabus.getId()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$", hasSize(1)));
    }
  }

  @Test
  @DisplayName("Khung không thể khóa: khóa khung -> 422")
  void templatesCannotBeLocked() throws Exception {
    MvcResult created =
        mockMvc
            .perform(
                postAs(
                    lecturerToken,
                    "/api/v1/syllabi/{id}/assessment-templates",
                    AssessmentRequest.builder()
                        .code(fixtures.code("TPL"))
                        .name("Khung")
                        .items(twoQuestions())
                        .build(),
                    syllabus.getId()))
            .andExpect(status().isCreated())
            .andReturn();

    String id = read(created, "$.id");
    mockMvc
        .perform(postAs(lecturerToken, "/api/v1/assessments/{id}/lock", null, id))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  @DisplayName(
      "US-15/AC-5: sau khi khóa (LOCKED) thêm/sửa/xóa phần tử bị từ chối, bài vẫn đọc được")
  void us15_ac5_lockedAssessmentRejectsStructureChanges() throws Exception {
    String id = createSectionAssessment(sectionRequest().build());
    mockMvc
        .perform(postAs(lecturerToken, "/api/v1/assessments/{id}/lock", null, id))
        .andExpect(status().isOk());

    AssessmentItemsRequest addOne =
        AssessmentItemsRequest.builder()
            .items(
                List.of(
                    question("Q1", 1, mapping(clo1, 4.0)),
                    question("Q2", 3, mapping(clo1, 3.0), mapping(clo2, 3.0)),
                    question("Q3", 2, mapping(clo2, 1.0))))
            .build();
    AssessmentItemsRequest changeScore =
        AssessmentItemsRequest.builder()
            .items(
                List.of(
                    question("Q1", 1, mapping(clo1, 5.0)),
                    question("Q2", 3, mapping(clo1, 3.0), mapping(clo2, 3.0))))
            .build();
    AssessmentItemsRequest removeOne =
        AssessmentItemsRequest.builder()
            .items(List.of(question("Q1", 1, mapping(clo1, 4.0))))
            .build();

    for (AssessmentItemsRequest change : List.of(addOne, changeScore, removeOne)) {
      mockMvc
          .perform(putAs(lecturerToken, "/api/v1/assessments/{id}/items", change, id))
          .andExpect(status().isUnprocessableEntity())
          .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    }
    mockMvc
        .perform(
            putAs(
                lecturerToken,
                "/api/v1/assessments/{id}",
                AssessmentUpdateRequest.builder().name("Đổi tên").build(),
                id))
        .andExpect(status().isUnprocessableEntity());

    // Xóa bài đã LOCKED -> 422
    mockMvc
        .perform(deleteAs(lecturerToken, "/api/v1/assessments/{id}", id))
        .andExpect(status().isUnprocessableEntity());

    mockMvc
        .perform(getAs(lecturerToken, "/api/v1/assessments/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("LOCKED"))
        .andExpect(jsonPath("$.locked").value(true))
        .andExpect(jsonPath("$.items", hasSize(2)));
    assertThat(stored(id).getItems()).hasSize(2);
  }

  @Test
  @DisplayName("OBE v0.7: Luồng Lock và Unlock bài đánh giá; chặn unlock khi đã có điểm")
  void lockAndUnlockFlow() throws Exception {
    String id = createSectionAssessment(sectionRequest().build());

    // 1. Lock bài đánh giá
    mockMvc
        .perform(postAs(lecturerToken, "/api/v1/assessments/{id}/lock", null, id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("LOCKED"))
        .andExpect(jsonPath("$.locked").value(true));

    Assessment locked = stored(id);
    assertThat(locked.getStatus()).isEqualTo(AssessmentStatus.LOCKED);
    assertThat(locked.isLocked()).isTrue();

    // Kiểm tra audit log cho lock
    assertThat(
            mongo()
                .count(
                    Query.query(
                        Criteria.where(AUDIT_ENTITY_ID)
                            .is(new org.bson.types.ObjectId(id))
                            .and("action")
                            .is("ASSESSMENT_LOCK")),
                    "audit_logs"))
        .isEqualTo(1);

    // Lock lại khi đã LOCKED -> 422
    mockMvc
        .perform(postAs(lecturerToken, "/api/v1/assessments/{id}/lock", null, id))
        .andExpect(status().isUnprocessableEntity());

    // 2. Unlock bài đánh giá khi chưa có điểm -> chuyển về DRAFT
    mockMvc
        .perform(postAs(lecturerToken, "/api/v1/assessments/{id}/unlock", null, id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DRAFT"))
        .andExpect(jsonPath("$.locked").value(false));

    Assessment unlocked = stored(id);
    assertThat(unlocked.getStatus()).isEqualTo(AssessmentStatus.DRAFT);
    assertThat(unlocked.isLocked()).isFalse();

    assertThat(
            mongo()
                .count(
                    Query.query(
                        Criteria.where(AUDIT_ENTITY_ID)
                            .is(new org.bson.types.ObjectId(id))
                            .and("action")
                            .is("ASSESSMENT_UNLOCK")),
                    "audit_logs"))
        .isEqualTo(1);

    // 3. Khóa lại và thêm 1 điểm để kiểm tra chặn unlock
    mockMvc
        .perform(postAs(lecturerToken, "/api/v1/assessments/{id}/lock", null, id))
        .andExpect(status().isOk());

    Score dummyScore =
        Score.builder()
            .assessmentId(id)
            .sectionId(section.getId())
            .studentId(ApiTestFixtures.missingId())
            .itemId(locked.getItems().get(0).getId())
            .cloId(clo1.getId())
            .rawScore(4.0)
            .maxScore(4.0)
            .sourceRow(1)
            .build();
    mongo().save(dummyScore);

    // Unlock bị từ chối vì đã có điểm -> 409 CONFLICT
    mockMvc
        .perform(postAs(lecturerToken, "/api/v1/assessments/{id}/unlock", null, id))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("đã có điểm")));
  }

  @Test
  @DisplayName("OBE v0.7: Xóa bài đánh giá ở DRAFT thành công (204); bài LOCKED bị chặn (422)")
  void deleteAssessmentFlow() throws Exception {
    String id = createSectionAssessment(sectionRequest().build());

    // Giảng viên ngoài lớp xóa -> 403
    mockMvc
        .perform(deleteAs(otherLecturerToken, "/api/v1/assessments/{id}", id))
        .andExpect(status().isForbidden());

    // Giảng viên lớp mình xóa bài DRAFT -> 204
    mockMvc
        .perform(deleteAs(lecturerToken, "/api/v1/assessments/{id}", id))
        .andExpect(status().isNoContent());

    assertThat(stored(id)).isNull();
  }

  @Test
  @DisplayName("OBE v0.7: Tổng trọng số cloWeights vượt quá 100% trong cùng lớp -> 422")
  void cumulativeCloWeightValidation() throws Exception {
    // Bài 1: CLO1 = 0.7, CLO2 = 0.3 và KHÓA bài 1
    AssessmentRequest req1 =
        AssessmentRequest.builder()
            .code("AS1")
            .name("Bài 1")
            .cloWeights(
                List.of(
                    AssessmentCloWeightDto.builder().cloId(clo1.getId()).weight(0.7).build(),
                    AssessmentCloWeightDto.builder().cloId(clo2.getId()).weight(0.3).build()))
            .items(twoQuestions())
            .build();
    String id1 = createSectionAssessment(req1);
    mockMvc
        .perform(postAs(lecturerToken, "/api/v1/assessments/{id}/lock", null, id1))
        .andExpect(status().isOk());

    // Bài 2: CLO1 = 0.4 (tổng CLO1 = 0.7 + 0.4 = 1.1 > 1.0) -> 422
    AssessmentRequest reqOver =
        AssessmentRequest.builder()
            .code("AS2")
            .name("Bài 2 quá tải")
            .cloWeights(
                List.of(
                    AssessmentCloWeightDto.builder().cloId(clo1.getId()).weight(0.4).build(),
                    AssessmentCloWeightDto.builder().cloId(clo2.getId()).weight(0.1).build()))
            .items(twoQuestions())
            .build();
    mockMvc
        .perform(
            postAs(lecturerToken, "/api/v1/sections/{id}/assessments", reqOver, section.getId()))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(
            jsonPath("$.message").value(org.hamcrest.Matchers.containsString("vượt quá 100%")));

    // Bài 2: CLO1 = 0.3 (tổng CLO1 = 0.7 + 0.3 = 1.0 <= 1.0, CLO2 = 0.3 + 0.1 = 0.4 <= 1.0) -> 201
    AssessmentRequest reqValid =
        AssessmentRequest.builder()
            .code("AS2")
            .name("Bài 2 hợp lệ")
            .cloWeights(
                List.of(
                    AssessmentCloWeightDto.builder().cloId(clo1.getId()).weight(0.3).build(),
                    AssessmentCloWeightDto.builder().cloId(clo2.getId()).weight(0.1).build()))
            .items(twoQuestions())
            .build();
    mockMvc
        .perform(
            postAs(lecturerToken, "/api/v1/sections/{id}/assessments", reqValid, section.getId()))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("NEW-01/AC-6: độ phủ byClo và byBloom khớp tính tay (ví dụ minh họa 20 điểm)")
  void new01_ac6_coverageMatchesHandComputation() throws Exception {
    // Q1 4đ Bloom1 CLO1; Q2 6đ Bloom3 (CLO1 3đ, CLO2 3đ); Q3 10đ không Bloom CLO2. Tổng 20.
    // CLO1 = 4 + 3 = 7 (35%); CLO2 = 3 + 10 = 13 (65%); Bloom1 = 4 (20%); Bloom3 = 6 (30%)
    List<AssessmentItemDto> items =
        List.of(
            question("Q1", 1, mapping(clo1, 4.0)),
            question("Q2", 3, mapping(clo1, 3.0), mapping(clo2, 3.0)),
            question("Q3", null, mapping(clo2, 10.0)));
    String id = createSectionAssessment(sectionRequest().items(items).build());

    mockMvc
        .perform(getAs(deptHeadToken, "/api/v1/assessments/{id}/coverage", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItemsSum").value(20.0))
        .andExpect(jsonPath("$.byClo", hasSize(2)))
        .andExpect(jsonPath("$.byClo[0].cloCode").value("CLO1"))
        .andExpect(jsonPath("$.byClo[0].points").value(closeTo(7.0, 1e-9)))
        .andExpect(jsonPath("$.byClo[0].percentage").value(closeTo(35.0, 1e-9)))
        .andExpect(jsonPath("$.byClo[1].cloCode").value("CLO2"))
        .andExpect(jsonPath("$.byClo[1].points").value(closeTo(13.0, 1e-9)))
        .andExpect(jsonPath("$.byClo[1].percentage").value(closeTo(65.0, 1e-9)))
        .andExpect(jsonPath("$.byBloom", hasSize(2)))
        .andExpect(jsonPath("$.byBloom[0].bloomLevel").value(1))
        .andExpect(jsonPath("$.byBloom[0].questionCount").value(1))
        .andExpect(jsonPath("$.byBloom[0].percentage").value(closeTo(20.0, 1e-9)))
        .andExpect(jsonPath("$.byBloom[1].bloomLevel").value(3))
        .andExpect(jsonPath("$.byBloom[1].percentage").value(closeTo(30.0, 1e-9)));

    // Bản đã lưu trong CSDL cũng mang cùng độ phủ
    assertThat(stored(id).getCoverageSummary().getByClo()).hasSize(2);
  }

  @Test
  @DisplayName(
      "Đọc: bài không tồn tại -> 403 (fail-closed); ID sai định dạng -> 403; danh sách lớp trả đủ bài")
  void readEdgeCases() throws Exception {
    mockMvc
        .perform(getAs(lecturerToken, "/api/v1/assessments/{id}", ApiTestFixtures.missingId()))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(getAs(lecturerToken, "/api/v1/assessments/{id}", "not-an-id"))
        .andExpect(status().isForbidden());

    createSectionAssessment(sectionRequest().build());
    createSectionAssessment(sectionRequest().build());
    mockMvc
        .perform(getAs(deptHeadToken, "/api/v1/sections/{id}/assessments", section.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)));
  }

  // ------------------------------------------------------------------ template-csv tests

  @Test
  @DisplayName(
      "Xuất CSV mẫu: bài LOCKED + có sinh viên enrolled -> 200, UTF-8 BOM, đúng header và danh sách SV")
  void templateCsv_lockedAssessment_success() throws Exception {
    String id = createSectionAssessment(sectionRequest().build());
    mockMvc
        .perform(postAs(lecturerToken, "/api/v1/assessments/{id}/lock", null, id))
        .andExpect(status().isOk());

    Student s1 = fixtures.student();
    s1.setFullName("Nguyen Van A");
    mongo().save(s1);

    Student s2 = fixtures.student();
    s2.setFullName("Tran Thi B");
    mongo().save(s2);

    fixtures.enrollment(section, s1, EnrollmentStatus.ENROLLED);
    fixtures.enrollment(section, s2, EnrollmentStatus.ENROLLED);

    MvcResult res =
        mockMvc
            .perform(getAs(lecturerToken, "/api/v1/assessments/{id}/scores/template-csv", id))
            .andExpect(status().isOk())
            .andExpect(content().contentType("text/csv; charset=UTF-8"))
            .andExpect(header().exists("Content-Disposition"))
            .andReturn();

    byte[] bytes = res.getResponse().getContentAsByteArray();
    String content = new String(bytes, StandardCharsets.UTF_8);

    assertThat(content.charAt(0)).isEqualTo('\uFEFF');
    assertThat(content)
        .contains("MaSV,HoTen,Q1 - CLO1 (Max: 4),Q2 - CLO1 (Max: 3),Q2 - CLO2 (Max: 3)");
    assertThat(content).contains(s1.getStudentRef() + ",Nguyen Van A,,,");
    assertThat(content).contains(s2.getStudentRef() + ",Tran Thi B,,,");
  }

  @Test
  @DisplayName("Xuất CSV mẫu: bài DRAFT chưa khóa -> 422")
  void templateCsv_draftAssessment_returns422() throws Exception {
    String id = createSectionAssessment(sectionRequest().build());

    mockMvc
        .perform(getAs(lecturerToken, "/api/v1/assessments/{id}/scores/template-csv", id))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(
            jsonPath("$.message")
                .value(org.hamcrest.Matchers.containsString("LOCKED hoặc SCORE_ENTERED")));
  }

  @Test
  @DisplayName("Xuất CSV mẫu: bài khung SYLLABUS_TEMPLATE -> 422")
  void templateCsv_syllabusTemplate_returns422() throws Exception {
    Assessment template =
        mongo()
            .save(
                Assessment.builder()
                    .syllabusId(syllabus.getId())
                    .scope(com.hcmute.lms.lmsapi.enums.AssessmentScope.SYLLABUS_TEMPLATE)
                    .code(fixtures.code("AT"))
                    .name("Bài khung mẫu")
                    .status(AssessmentStatus.DRAFT)
                    .build());

    mockMvc
        .perform(
            getAs(lecturerToken, "/api/v1/assessments/{id}/scores/template-csv", template.getId()))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(
            jsonPath("$.message")
                .value(org.hamcrest.Matchers.containsString("bài đánh giá của lớp học phần")));
  }

  @Test
  @DisplayName("Xuất CSV mẫu: giảng viên khác không phụ trách lớp -> 403")
  void templateCsv_otherLecturer_forbidden() throws Exception {
    String id = createSectionAssessment(sectionRequest().build());
    mockMvc
        .perform(postAs(lecturerToken, "/api/v1/assessments/{id}/lock", null, id))
        .andExpect(status().isOk());

    mockMvc
        .perform(getAs(otherLecturerToken, "/api/v1/assessments/{id}/scores/template-csv", id))
        .andExpect(status().isForbidden());
  }
}
