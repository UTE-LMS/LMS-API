package com.hcmute.lms.lmsapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hcmute.lms.lmsapi.document.Assessment;
import com.hcmute.lms.lmsapi.document.Clo;
import com.hcmute.lms.lmsapi.document.Course;
import com.hcmute.lms.lmsapi.document.CourseSection;
import com.hcmute.lms.lmsapi.document.RoleAssignment;
import com.hcmute.lms.lmsapi.document.Syllabus;
import com.hcmute.lms.lmsapi.document.User;
import com.hcmute.lms.lmsapi.document.UserScope;
import com.hcmute.lms.lmsapi.dto.AssessmentItemDto;
import com.hcmute.lms.lmsapi.dto.AssessmentItemsRequest;
import com.hcmute.lms.lmsapi.dto.AssessmentRejectRequest;
import com.hcmute.lms.lmsapi.dto.AssessmentRequest;
import com.hcmute.lms.lmsapi.dto.AssessmentUpdateRequest;
import com.hcmute.lms.lmsapi.dto.OutcomeMappingDto;
import com.hcmute.lms.lmsapi.dto.RubricDto;
import com.hcmute.lms.lmsapi.dto.RubricLevelDto;
import com.hcmute.lms.lmsapi.enums.AssessmentStatus;
import com.hcmute.lms.lmsapi.enums.DataGranularity;
import com.hcmute.lms.lmsapi.enums.ItemKind;
import com.hcmute.lms.lmsapi.enums.Role;
import com.hcmute.lms.lmsapi.enums.SyllabusStatus;
import com.hcmute.lms.lmsapi.testsupport.ApiTestFixtures;
import com.hcmute.lms.lmsapi.testsupport.IntegrationTestBase;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.web.servlet.MvcResult;

@DisplayName("F04: Assessment Integration Tests (US-15, US-16, NEW-01)")
class AssessmentIntegrationTest extends IntegrationTestBase {

  /**
   * Spring Data lưu thuộc tính tên "id" của đối tượng lồng nhau thành "_id", nên mã thực thể trong
   * audit_logs nằm ở entity._id và là ObjectId khi mã hợp lệ (xem doc/tech-debt.md TD-34).
   */
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
  private String otherDeptHeadToken;
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
    departmentHead = fixtures.user(Role.DEPARTMENT_HEAD, courseScope);
    User otherLecturer = fixtures.user(Role.LECTURER, UserScope.builder().build());
    User otherDeptHead =
        fixtures.user(
            Role.DEPARTMENT_HEAD,
            UserScope.builder()
                .courseIds(new ArrayList<>(List.of(ApiTestFixtures.missingId())))
                .build());

    section = fixtures.section(course.getId(), List.of(lecturer.getId()));
    section.setSyllabusId(syllabus.getId());
    section = mongo().save(section);

    adminToken = fixtures.bearerFor(Role.ADMIN, UserScope.builder().build());
    lecturerToken = fixtures.bearer(lecturer);
    otherLecturerToken = fixtures.bearer(otherLecturer);
    deptHeadToken = fixtures.bearer(departmentHead);
    otherDeptHeadToken = fixtures.bearer(otherDeptHead);
    pmToken =
        fixtures.bearerFor(
            Role.PROGRAM_MANAGER,
            UserScope.builder().courseIds(new ArrayList<>(List.of(course.getId()))).build());
  }

  // ------------------------------------------------------------------ dựng dữ liệu

  private OutcomeMappingDto mapping(Clo clo, double weight) {
    return OutcomeMappingDto.builder().cloId(clo.getId()).weight(weight).build();
  }

  private AssessmentItemDto question(
      String code, double maxScore, Integer bloom, OutcomeMappingDto... mappings) {
    return AssessmentItemDto.builder()
        .code(code)
        .kind(ItemKind.QUESTION)
        .maxScore(maxScore)
        .bloomLevel(bloom)
        .outcomeMappings(List.of(mappings))
        .build();
  }

  /** Hai câu: Q1 4 điểm đo CLO1; Q2 6 điểm chia đều CLO1/CLO2. Tổng 10 điểm. */
  private List<AssessmentItemDto> twoQuestions() {
    return List.of(
        question("Q1", 4, 1, mapping(clo1, 1.0)),
        question("Q2", 6, 3, mapping(clo1, 0.5), mapping(clo2, 0.5)));
  }

  private AssessmentRequest.AssessmentRequestBuilder sectionRequest() {
    return AssessmentRequest.builder()
        .code(fixtures.code("AS"))
        .name("Giữa kỳ (dữ liệu thử nghiệm)")
        .totalPoints(10.0)
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

  private String createSubmittedAssessment() throws Exception {
    String id = createSectionAssessment(sectionRequest().build());
    mockMvc
        .perform(postAs(lecturerToken, "/api/v1/assessments/{id}/submit", null, id))
        .andExpect(status().isOk());
    return id;
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
        .andExpect(jsonPath("$.measurableByOutcome").value(true))
        .andExpect(jsonPath("$.items", hasSize(2)))
        .andExpect(jsonPath("$.items[0].id").isNotEmpty())
        .andExpect(jsonPath("$.items[0].orderIndex").value(1))
        .andExpect(jsonPath("$.coverageSummary.totalItemsSum").value(10.0));
  }

  @Test
  @DisplayName(
      "US-15/AC-1: sao chép từ khung (templateId) rồi sửa khung -> bản lớp là snapshot, không đổi")
  void us15_ac1_copyFromTemplateIsASnapshot() throws Exception {
    AssessmentRequest templateRequest =
        AssessmentRequest.builder()
            .code(fixtures.code("TPL"))
            .name("Khung giữa kỳ")
            .totalPoints(10.0)
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
            .andExpect(jsonPath("$.totalPoints").value(10.0))
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
                    .items(List.of(question("Q9", 10, null, mapping(clo2, 1.0))))
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
  @DisplayName("US-15/AC-2: Σ weight ≠ 1, CLO trùng, CLO của đề cương khác/không tồn tại -> 422")
  void us15_ac2_outcomeMappingRules() throws Exception {
    List<List<AssessmentItemDto>> invalid =
        List.of(
            List.of(question("Q1", 4, 1, mapping(clo1, 0.5), mapping(clo2, 0.4))),
            List.of(question("Q1", 4, 1, mapping(clo1, 0.5), mapping(clo2, 0.6))),
            List.of(question("Q1", 4, 1, mapping(clo1, 0.5), mapping(clo1, 0.5))),
            List.of(question("Q1", 4, 1, mapping(foreignClo, 1.0))),
            List.of(
                question(
                    "Q1",
                    4,
                    1,
                    OutcomeMappingDto.builder()
                        .cloId(ApiTestFixtures.missingId())
                        .weight(1.0)
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
  @DisplayName(
      "US-15: maxScore ≤ 0, thiếu kind/outcomeMappings, Bloom ngoài 1-6, weight ngoài (0,1] -> 400")
  void us15_validationErrors() throws Exception {
    AssessmentItemDto zeroScore = question("Q1", 0, 1, mapping(clo1, 1.0));
    AssessmentItemDto negativeScore = question("Q1", -1, 1, mapping(clo1, 1.0));
    AssessmentItemDto noKind = question("Q1", 1, 1, mapping(clo1, 1.0));
    noKind.setKind(null);
    AssessmentItemDto noMappings = question("Q1", 1, 1);
    AssessmentItemDto badBloom = question("Q1", 1, 7, mapping(clo1, 1.0));
    AssessmentItemDto zeroWeight = question("Q1", 1, 1, mapping(clo1, 0.0));
    AssessmentItemDto weightOverOne = question("Q1", 1, 1, mapping(clo1, 1.5));

    for (AssessmentItemDto bad :
        List.of(
            zeroScore, negativeScore, noKind, noMappings, badBloom, zeroWeight, weightOverOne)) {
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
      "Khung theo đề cương: giảng viên/TBM trong phạm vi được tạo; PM, ADMIN, giảng viên ngoài phạm vi -> 403 (ADR 0010)")
  void templateAuthorizationFollowsAdr0010() throws Exception {
    AssessmentRequest request =
        AssessmentRequest.builder().code(fixtures.code("TPL")).name("Khung").build();

    for (String token : List.of(pmToken, adminToken, otherLecturerToken)) {
      mockMvc
          .perform(
              postAs(token, "/api/v1/syllabi/{id}/assessment-templates", request, syllabus.getId()))
          .andExpect(status().isForbidden());
    }
    mockMvc
        .perform(
            postAs(
                deptHeadToken,
                "/api/v1/syllabi/{id}/assessment-templates",
                request,
                syllabus.getId()))
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
  @DisplayName("Khung không đi qua quy trình duyệt: gửi duyệt khung -> 422")
  void templatesCannotBeSubmitted() throws Exception {
    MvcResult created =
        mockMvc
            .perform(
                postAs(
                    lecturerToken,
                    "/api/v1/syllabi/{id}/assessment-templates",
                    AssessmentRequest.builder()
                        .code(fixtures.code("TPL"))
                        .name("Khung")
                        .totalPoints(10.0)
                        .items(twoQuestions())
                        .build(),
                    syllabus.getId()))
            .andExpect(status().isCreated())
            .andReturn();

    String id = read(created, "$.id");
    mockMvc
        .perform(postAs(lecturerToken, "/api/v1/assessments/{id}/submit", null, id))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  @DisplayName(
      "US-15/AC-5: sau khi duyệt (isLocked) thêm/sửa/xóa phần tử bị từ chối, bài vẫn đọc được")
  void us15_ac5_lockedAssessmentRejectsStructureChanges() throws Exception {
    String id = createSubmittedAssessment();
    mockMvc
        .perform(postAs(deptHeadToken, "/api/v1/assessments/{id}/approve", null, id))
        .andExpect(status().isOk());

    AssessmentItemsRequest addOne =
        AssessmentItemsRequest.builder()
            .items(
                List.of(
                    question("Q1", 4, 1, mapping(clo1, 1.0)),
                    question("Q2", 6, 3, mapping(clo1, 0.5), mapping(clo2, 0.5)),
                    question("Q3", 1, 2, mapping(clo2, 1.0))))
            .build();
    AssessmentItemsRequest changeMaxScore =
        AssessmentItemsRequest.builder()
            .items(
                List.of(
                    question("Q1", 5, 1, mapping(clo1, 1.0)),
                    question("Q2", 6, 3, mapping(clo1, 0.5), mapping(clo2, 0.5))))
            .build();
    AssessmentItemsRequest removeOne =
        AssessmentItemsRequest.builder()
            .items(List.of(question("Q1", 4, 1, mapping(clo1, 1.0))))
            .build();

    for (AssessmentItemsRequest change : List.of(addOne, changeMaxScore, removeOne)) {
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

    mockMvc
        .perform(getAs(lecturerToken, "/api/v1/assessments/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"))
        .andExpect(jsonPath("$.locked").value(true))
        .andExpect(jsonPath("$.items", hasSize(2)));
    assertThat(stored(id).getItems()).hasSize(2);
  }

  @Test
  @DisplayName(
      "US-15: dữ liệu chỉ có điểm tổng (TOTAL_ONLY) lưu được nhưng báo rõ không đo được CLO/PI")
  void totalOnlyIsFlaggedAsNotMeasurable() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                postAs(
                    lecturerToken,
                    "/api/v1/sections/{id}/assessments",
                    sectionRequest().dataGranularity(DataGranularity.TOTAL_ONLY).build(),
                    section.getId()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.dataGranularity").value("TOTAL_ONLY"))
            .andExpect(jsonPath("$.measurableByOutcome").value(false))
            .andReturn();

    String id = read(result, "$.id");
    mockMvc
        .perform(
            putAs(
                lecturerToken,
                "/api/v1/assessments/{id}",
                AssessmentUpdateRequest.builder().name("Đổi sang ITEM").build(),
                id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.measurableByOutcome").value(true));
  }

  // ------------------------------------------------------------------ US-16

  @Test
  @DisplayName(
      "US-16/AC-1: tiêu chí CRITERION có rubric mức 1-4; mức ngoài 1-4 -> 400; điểm mức > maxScore -> 422")
  void us16_ac1_rubricLevels() throws Exception {
    AssessmentItemDto criterion = question("C1", 4, 3, mapping(clo1, 1.0));
    criterion.setKind(ItemKind.CRITERION);
    criterion.setRubric(
        RubricDto.builder()
            .levels(
                List.of(
                    RubricLevelDto.builder().level(1).description("Chưa đạt").score(1.0).build(),
                    RubricLevelDto.builder().level(4).description("Xuất sắc").score(4.0).build()))
            .build());

    mockMvc
        .perform(
            postAs(
                lecturerToken,
                "/api/v1/sections/{id}/assessments",
                sectionRequest().totalPoints(4.0).items(List.of(criterion)).build(),
                section.getId()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.items[0].kind").value("CRITERION"))
        .andExpect(jsonPath("$.items[0].rubric.levels", hasSize(2)))
        .andExpect(jsonPath("$.items[0].rubric.levels[1].level").value(4));

    for (int badLevel : new int[] {0, 5}) {
      criterion.setRubric(
          RubricDto.builder()
              .levels(List.of(RubricLevelDto.builder().level(badLevel).score(1.0).build()))
              .build());
      mockMvc
          .perform(
              postAs(
                  lecturerToken,
                  "/api/v1/sections/{id}/assessments",
                  sectionRequest().items(List.of(criterion)).build(),
                  section.getId()))
          .andExpect(status().isBadRequest());
    }

    criterion.setRubric(
        RubricDto.builder()
            .levels(List.of(RubricLevelDto.builder().level(4).score(4.5).build()))
            .build());
    mockMvc
        .perform(
            postAs(
                lecturerToken,
                "/api/v1/sections/{id}/assessments",
                sectionRequest().items(List.of(criterion)).build(),
                section.getId()))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  @DisplayName(
      "US-16/AC-2: mức Bloom (1-6) và mức rubric (1-4) là hai trường độc lập, lưu đúng từng giá trị")
  void us16_ac2_bloomAndRubricLevelAreIndependent() throws Exception {
    AssessmentItemDto criterion = question("C1", 4, 6, mapping(clo1, 1.0));
    criterion.setKind(ItemKind.CRITERION);
    criterion.setRubric(
        RubricDto.builder()
            .levels(List.of(RubricLevelDto.builder().level(1).score(4.0).build()))
            .build());

    MvcResult result =
        mockMvc
            .perform(
                postAs(
                    lecturerToken,
                    "/api/v1/sections/{id}/assessments",
                    sectionRequest().totalPoints(4.0).items(List.of(criterion)).build(),
                    section.getId()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.items[0].bloomLevel").value(6))
            .andExpect(jsonPath("$.items[0].rubric.levels[0].level").value(1))
            .andExpect(jsonPath("$.items[0].rubric.levels[0].score").value(4.0))
            .andReturn();

    Assessment saved = stored(read(result, "$.id"));
    assertThat(saved.getItems().get(0).getBloomLevel()).isEqualTo(6);
    assertThat(saved.getItems().get(0).getRubric().getLevels().get(0).getLevel()).isEqualTo(1);
  }

  // ------------------------------------------------------------------ NEW-01

  @Test
  @DisplayName(
      "NEW-01/AC-1: giảng viên gửi duyệt bài hợp lệ -> SUBMITTED, ghi người/giờ gửi và history SUBMIT")
  void new01_ac1_submit() throws Exception {
    String id = createSectionAssessment(sectionRequest().build());

    mockMvc
        .perform(postAs(lecturerToken, "/api/v1/assessments/{id}/submit", null, id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUBMITTED"))
        .andExpect(jsonPath("$.locked").value(false))
        .andExpect(jsonPath("$.approval.submittedBy").value(lecturer.getId()))
        .andExpect(jsonPath("$.approval.submittedAt").isNotEmpty())
        .andExpect(jsonPath("$.approval.history", hasSize(1)))
        .andExpect(jsonPath("$.approval.history[0].action").value("SUBMIT"))
        .andExpect(jsonPath("$.approval.history[0].actorId").value(lecturer.getId()));

    // Đang chờ duyệt: không sửa được
    mockMvc
        .perform(
            putAs(
                lecturerToken,
                "/api/v1/assessments/{id}/items",
                AssessmentItemsRequest.builder().items(twoQuestions()).build(),
                id))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  @DisplayName(
      "NEW-01/AC-2: gửi duyệt khi 0 phần tử, thiếu tổng điểm, hoặc tổng điểm lệch Σ maxScore từ 0,001 trở lên -> 422")
  void new01_ac2_submitPreconditions() throws Exception {
    String empty = createSectionAssessment(sectionRequest().items(List.of()).build());
    String noTotal = createSectionAssessment(sectionRequest().totalPoints(null).build());
    String over = createSectionAssessment(sectionRequest().totalPoints(10.0011).build());
    String under = createSectionAssessment(sectionRequest().totalPoints(9.9989).build());

    for (String id : List.of(empty, noTotal, over, under)) {
      mockMvc
          .perform(postAs(lecturerToken, "/api/v1/assessments/{id}/submit", null, id))
          .andExpect(status().isUnprocessableEntity());
      assertThat(stored(id).getStatus()).isEqualTo(AssessmentStatus.DRAFT);
    }

    // Lệch 0,0009 (< 0,001) vẫn được gửi duyệt
    String withinTolerance = createSectionAssessment(sectionRequest().totalPoints(10.0009).build());
    mockMvc
        .perform(postAs(lecturerToken, "/api/v1/assessments/{id}/submit", null, withinTolerance))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName(
      "NEW-01/AC-3: TBM duyệt -> APPROVED, isLocked=true, ghi reviewedBy/At, history và audit_logs ASSESSMENT_APPROVE")
  void new01_ac3_approve() throws Exception {
    String id = createSubmittedAssessment();

    mockMvc
        .perform(postAs(deptHeadToken, "/api/v1/assessments/{id}/approve", null, id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"))
        .andExpect(jsonPath("$.locked").value(true))
        .andExpect(jsonPath("$.approval.reviewedBy").value(departmentHead.getId()))
        .andExpect(jsonPath("$.approval.reviewedAt").isNotEmpty())
        .andExpect(jsonPath("$.approval.history", hasSize(2)))
        .andExpect(jsonPath("$.approval.history[1].action").value("APPROVE"));

    Assessment saved = stored(id);
    assertThat(saved.getStatus()).isEqualTo(AssessmentStatus.APPROVED);
    assertThat(saved.isLocked()).isTrue();

    List<org.bson.Document> audits =
        mongo()
            .find(
                Query.query(
                    Criteria.where(AUDIT_ENTITY_ID)
                        .is(new org.bson.types.ObjectId(id))
                        .and("action")
                        .is("ASSESSMENT_APPROVE")),
                org.bson.Document.class,
                "audit_logs");
    assertThat(audits).hasSize(1);
    assertThat(audits.get(0).getObjectId("userId").toHexString()).isEqualTo(departmentHead.getId());
  }

  @Test
  @DisplayName(
      "NEW-01/AC-4: từ chối kèm ghi chú -> REJECTED, isLocked=false; thiếu ghi chú -> 400; sửa lại -> DRAFT rồi gửi lại được")
  void new01_ac4_rejectAndResubmit() throws Exception {
    String id = createSubmittedAssessment();

    mockMvc
        .perform(
            postAs(
                deptHeadToken,
                "/api/v1/assessments/{id}/reject",
                AssessmentRejectRequest.builder().reviewNote("  ").build(),
                id))
        .andExpect(status().isBadRequest());
    assertThat(stored(id).getStatus()).isEqualTo(AssessmentStatus.SUBMITTED);

    mockMvc
        .perform(
            postAs(
                deptHeadToken,
                "/api/v1/assessments/{id}/reject",
                AssessmentRejectRequest.builder().reviewNote("CLO2 chưa được đo ở câu nào").build(),
                id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REJECTED"))
        .andExpect(jsonPath("$.locked").value(false))
        .andExpect(jsonPath("$.approval.reviewNote").value("CLO2 chưa được đo ở câu nào"))
        .andExpect(jsonPath("$.approval.history[1].action").value("REJECT"));

    assertThat(
            mongo()
                .count(
                    Query.query(
                        Criteria.where(AUDIT_ENTITY_ID)
                            .is(new org.bson.types.ObjectId(id))
                            .and("action")
                            .is("ASSESSMENT_REJECT")),
                    "audit_logs"))
        .isEqualTo(1);

    // Bị từ chối thì chưa gửi lại ngay được; phải sửa (REJECTED -> DRAFT) rồi mới gửi
    mockMvc
        .perform(postAs(lecturerToken, "/api/v1/assessments/{id}/submit", null, id))
        .andExpect(status().isUnprocessableEntity());
    mockMvc
        .perform(
            putAs(
                lecturerToken,
                "/api/v1/assessments/{id}/items",
                AssessmentItemsRequest.builder().items(twoQuestions()).build(),
                id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DRAFT"));
    mockMvc
        .perform(postAs(lecturerToken, "/api/v1/assessments/{id}/submit", null, id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUBMITTED"))
        .andExpect(jsonPath("$.approval.reviewNote").doesNotExist())
        .andExpect(jsonPath("$.approval.history", hasSize(3)));
  }

  @Test
  @DisplayName(
      "NEW-01/AC-5: chỉ DEPARTMENT_HEAD đúng phạm vi duyệt; giảng viên, ADMIN, PM, TBM ngoài phạm vi -> 403")
  void new01_ac5_onlyDepartmentHeadInScopeReviews() throws Exception {
    String id = createSubmittedAssessment();

    for (String token : List.of(lecturerToken, adminToken, pmToken, otherDeptHeadToken)) {
      mockMvc
          .perform(postAs(token, "/api/v1/assessments/{id}/approve", null, id))
          .andExpect(status().isForbidden());
      mockMvc
          .perform(
              postAs(
                  token,
                  "/api/v1/assessments/{id}/reject",
                  AssessmentRejectRequest.builder().reviewNote("x").build(),
                  id))
          .andExpect(status().isForbidden());
    }
    mockMvc
        .perform(postAs(null, "/api/v1/assessments/{id}/approve", null, id))
        .andExpect(status().isUnauthorized());
    assertThat(stored(id).getStatus()).isEqualTo(AssessmentStatus.SUBMITTED);
  }

  @Test
  @DisplayName(
      "NEW-01/AC-5: người gửi duyệt không tự duyệt được dù cũng giữ vai trò DEPARTMENT_HEAD -> 403")
  void new01_ac5_submitterCannotSelfApprove() throws Exception {
    UserScope scope =
        UserScope.builder().courseIds(new ArrayList<>(List.of(section.getCourseId()))).build();
    User both =
        fixtures.user(
            List.of(
                RoleAssignment.builder().role(Role.LECTURER).scope(scope).build(),
                RoleAssignment.builder().role(Role.DEPARTMENT_HEAD).scope(scope).build()));
    section.setLecturerIds(new ArrayList<>(List.of(both.getId())));
    section = mongo().save(section);
    String bothToken = fixtures.bearer(both);

    MvcResult created =
        mockMvc
            .perform(
                postAs(
                    bothToken,
                    "/api/v1/sections/{id}/assessments",
                    sectionRequest().build(),
                    section.getId()))
            .andExpect(status().isCreated())
            .andReturn();
    String id = read(created, "$.id");
    mockMvc
        .perform(postAs(bothToken, "/api/v1/assessments/{id}/submit", null, id))
        .andExpect(status().isOk());

    mockMvc
        .perform(postAs(bothToken, "/api/v1/assessments/{id}/approve", null, id))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            postAs(
                bothToken,
                "/api/v1/assessments/{id}/reject",
                AssessmentRejectRequest.builder().reviewNote("tự từ chối").build(),
                id))
        .andExpect(status().isForbidden());

    // Một TBM khác trong phạm vi duyệt được
    mockMvc
        .perform(postAs(deptHeadToken, "/api/v1/assessments/{id}/approve", null, id))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("NEW-01/AC-6: độ phủ byClo và byBloom khớp tính tay (ví dụ minh họa 20 điểm)")
  void new01_ac6_coverageMatchesHandComputation() throws Exception {
    // Q1 4đ Bloom1 CLO1; Q2 6đ Bloom3 CLO1/CLO2 mỗi CLO 0,5; Q3 10đ không Bloom CLO2. Tổng 20.
    // CLO1 = 4 + 3 = 7 (35%); CLO2 = 3 + 10 = 13 (65%); Bloom1 = 4 (20%); Bloom3 = 6 (30%)
    List<AssessmentItemDto> items =
        List.of(
            question("Q1", 4, 1, mapping(clo1, 1.0)),
            question("Q2", 6, 3, mapping(clo1, 0.5), mapping(clo2, 0.5)),
            question("Q3", 10, null, mapping(clo2, 1.0)));
    String id = createSectionAssessment(sectionRequest().totalPoints(20.0).items(items).build());

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

    // Bản đã lưu trong CSDL cũng mang cùng độ phủ (màn hình duyệt nhanh đọc từ đây)
    assertThat(stored(id).getCoverageSummary().getByClo()).hasSize(2);
  }

  @Test
  @DisplayName(
      "NEW-01/AC-7: chuyển trạng thái sai thứ tự (DRAFT->APPROVED, APPROVED->SUBMITTED, duyệt hai lần...) -> 422")
  void new01_ac7_invalidTransitions() throws Exception {
    String draft = createSectionAssessment(sectionRequest().build());
    mockMvc
        .perform(postAs(deptHeadToken, "/api/v1/assessments/{id}/approve", null, draft))
        .andExpect(status().isUnprocessableEntity());
    mockMvc
        .perform(
            postAs(
                deptHeadToken,
                "/api/v1/assessments/{id}/reject",
                AssessmentRejectRequest.builder().reviewNote("x").build(),
                draft))
        .andExpect(status().isUnprocessableEntity());

    String submitted = createSubmittedAssessment();
    mockMvc
        .perform(postAs(lecturerToken, "/api/v1/assessments/{id}/submit", null, submitted))
        .andExpect(status().isUnprocessableEntity());

    mockMvc
        .perform(postAs(deptHeadToken, "/api/v1/assessments/{id}/approve", null, submitted))
        .andExpect(status().isOk());
    // Đã duyệt: duyệt/từ chối/gửi lại đều không hợp lệ
    mockMvc
        .perform(postAs(deptHeadToken, "/api/v1/assessments/{id}/approve", null, submitted))
        .andExpect(status().isUnprocessableEntity());
    mockMvc
        .perform(
            postAs(
                deptHeadToken,
                "/api/v1/assessments/{id}/reject",
                AssessmentRejectRequest.builder().reviewNote("x").build(),
                submitted))
        .andExpect(status().isUnprocessableEntity());
    mockMvc
        .perform(postAs(lecturerToken, "/api/v1/assessments/{id}/submit", null, submitted))
        .andExpect(status().isUnprocessableEntity());
    assertThat(stored(submitted).getStatus()).isEqualTo(AssessmentStatus.APPROVED);
  }

  @Test
  @DisplayName(
      "NEW-01: hai người cùng duyệt một bài đồng thời -> đúng một người thành công, chỉ một dòng audit")
  void new01_concurrentApprovalHasASingleWinner() throws Exception {
    String id = createSubmittedAssessment();
    User secondHead =
        fixtures.user(
            Role.DEPARTMENT_HEAD,
            UserScope.builder().courseIds(new ArrayList<>(List.of(section.getCourseId()))).build());
    List<String> tokens = List.of(deptHeadToken, fixtures.bearer(secondHead));

    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch go = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      List<Future<Integer>> results = new ArrayList<>();
      for (String token : tokens) {
        results.add(
            pool.submit(
                () -> {
                  ready.countDown();
                  go.await();
                  return mockMvc
                      .perform(postAs(token, "/api/v1/assessments/{id}/approve", null, id))
                      .andReturn()
                      .getResponse()
                      .getStatus();
                }));
      }
      ready.await();
      go.countDown();

      int ok = 0;
      for (Future<Integer> result : results) {
        int code = result.get();
        if (code == 200) {
          ok++;
        } else {
          assertThat(code).isIn(409, 422);
        }
      }
      assertThat(ok).isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }

    assertThat(stored(id).getStatus()).isEqualTo(AssessmentStatus.APPROVED);
    assertThat(stored(id).getApproval().getHistory()).hasSize(2);
    assertThat(
            mongo()
                .count(
                    Query.query(
                        Criteria.where(AUDIT_ENTITY_ID)
                            .is(new org.bson.types.ObjectId(id))
                            .and("action")
                            .is("ASSESSMENT_APPROVE")),
                    "audit_logs"))
        .isEqualTo(1);
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
}
