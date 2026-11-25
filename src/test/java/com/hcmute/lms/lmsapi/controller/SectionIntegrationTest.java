package com.hcmute.lms.lmsapi.controller;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hcmute.lms.lmsapi.document.Course;
import com.hcmute.lms.lmsapi.document.CourseSection;
import com.hcmute.lms.lmsapi.document.Faculty;
import com.hcmute.lms.lmsapi.document.Program;
import com.hcmute.lms.lmsapi.document.ProgramVersion;
import com.hcmute.lms.lmsapi.document.Syllabus;
import com.hcmute.lms.lmsapi.document.Term;
import com.hcmute.lms.lmsapi.document.User;
import com.hcmute.lms.lmsapi.dto.SectionRequest;
import com.hcmute.lms.lmsapi.dto.SectionStatusUpdateRequest;
import com.hcmute.lms.lmsapi.dto.SectionUpdateRequest;
import com.hcmute.lms.lmsapi.enums.ProgramVersionStatus;
import com.hcmute.lms.lmsapi.enums.Role;
import com.hcmute.lms.lmsapi.enums.SectionStatus;
import com.hcmute.lms.lmsapi.enums.SyllabusStatus;
import com.hcmute.lms.lmsapi.enums.TermStatus;
import com.hcmute.lms.lmsapi.testsupport.ApiTestFixtures;
import com.hcmute.lms.lmsapi.testsupport.IntegrationTestBase;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

@DisplayName("F05a: Course Sections Integration Tests (US-19)")
class SectionIntegrationTest extends IntegrationTestBase {

  private Course course;
  private Syllabus confirmedSyllabus;
  private Term openTerm;
  private User lecturerA;
  private User lecturerB;
  private Program program;

  private String adminToken;
  private String pmInScopeToken;
  private String pmOutOfScopeToken;
  private String lecturerAToken;
  private String lecturerBToken;

  @BeforeEach
  void setUpFixtures() {
    course = fixtures.course();
    confirmedSyllabus = fixtures.syllabus(course, SyllabusStatus.CONFIRMED);
    openTerm = fixtures.term();
    lecturerA = fixtures.user(Role.LECTURER);
    lecturerB = fixtures.user(Role.LECTURER);

    Faculty faculty = fixtures.faculty();
    course.setFacultyId(faculty.getId());
    course = mongo().save(course);

    program = fixtures.program();
    program.setFacultyId(faculty.getId());
    program = mongo().save(program);
    ProgramVersion version = fixtures.programVersion(program, ProgramVersionStatus.ACTIVE);
    fixtures.programCourse(version, course);

    Faculty otherFaculty = fixtures.faculty();

    adminToken = fixtures.bearerFor(Role.ADMIN);
    User pmInScope = fixtures.user(Role.PROGRAM_MANAGER);
    pmInScope.setFacultyId(faculty.getId());
    pmInScopeToken = fixtures.bearer(mongo().save(pmInScope));

    User pmOutOfScope = fixtures.user(Role.PROGRAM_MANAGER);
    pmOutOfScope.setFacultyId(otherFaculty.getId());
    pmOutOfScopeToken = fixtures.bearer(mongo().save(pmOutOfScope));

    lecturerAToken = fixtures.bearer(lecturerA);
    lecturerBToken = fixtures.bearer(lecturerB);
  }

  private SectionRequest.SectionRequestBuilder validRequest() {
    return SectionRequest.builder()
        .code(fixtures.code("SEC"))
        .courseId(course.getId())
        .syllabusId(confirmedSyllabus.getId())
        .termId(openTerm.getId())
        .lecturerIds(List.of(lecturerA.getId()));
  }

  private String createSectionAs(String token, SectionRequest request) throws Exception {
    MvcResult result =
        mockMvc
            .perform(postAs(token, "/api/v1/sections", request))
            .andExpect(status().isCreated())
            .andReturn();
    return read(result, "$.id");
  }

  @Test
  @DisplayName("US-19/AC-1: ADMIN và PROGRAM_MANAGER trong phạm vi mở lớp thành công (201, OPEN)")
  void us19_ac1_createSection() throws Exception {
    SectionRequest request = validRequest().build();

    mockMvc
        .perform(postAs(adminToken, "/api/v1/sections", request))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.code").value(request.getCode()))
        .andExpect(jsonPath("$.courseId").value(course.getId()))
        .andExpect(jsonPath("$.syllabusId").value(confirmedSyllabus.getId()))
        .andExpect(jsonPath("$.termId").value(openTerm.getId()))
        .andExpect(jsonPath("$.lecturerIds[0]").value(lecturerA.getId()))
        .andExpect(jsonPath("$.lecturers[0].id").value(lecturerA.getId()))
        .andExpect(jsonPath("$.lecturers[0].username").value(lecturerA.getUsername()))
        .andExpect(jsonPath("$.lecturers[0].fullName").value(lecturerA.getFullName()))
        .andExpect(jsonPath("$.enrolledCount").value(0))
        .andExpect(jsonPath("$.status").value("OPEN"));

    // PM có program chứa học phần trong scope.programIds cũng được mở lớp
    mockMvc
        .perform(postAs(pmInScopeToken, "/api/v1/sections", validRequest().build()))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("US-19/AC-1: giảng viên trùng trong danh sách được khử trùng lặp")
  void us19_ac1_duplicateLecturersAreDeduplicated() throws Exception {
    SectionRequest request =
        validRequest().lecturerIds(List.of(lecturerA.getId(), lecturerA.getId())).build();

    mockMvc
        .perform(postAs(adminToken, "/api/v1/sections", request))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.lecturerIds", hasSize(1)));
  }

  @Test
  @DisplayName("US-19/AC-2: đề cương không thuộc học phần, đề cương chưa chốt -> 422")
  void us19_ac2_syllabusMustMatchCourseAndBeConfirmed() throws Exception {
    Course otherCourse = fixtures.course();
    Syllabus foreignSyllabus = fixtures.syllabus(otherCourse, SyllabusStatus.CONFIRMED);
    Syllabus archivedSyllabus = fixtures.syllabus(course, SyllabusStatus.ARCHIVED);

    for (String syllabusId : List.of(foreignSyllabus.getId(), archivedSyllabus.getId())) {
      mockMvc
          .perform(
              postAs(adminToken, "/api/v1/sections", validRequest().syllabusId(syllabusId).build()))
          .andExpect(status().isUnprocessableEntity())
          .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    }
  }

  @Test
  @DisplayName("US-19/AC-2: học phần, đề cương, học kỳ hoặc giảng viên không tồn tại -> 422")
  void us19_ac2_unknownReferences() throws Exception {
    String missing = ApiTestFixtures.missingId();

    mockMvc
        .perform(postAs(adminToken, "/api/v1/sections", validRequest().syllabusId(missing).build()))
        .andExpect(status().isUnprocessableEntity());
    mockMvc
        .perform(postAs(adminToken, "/api/v1/sections", validRequest().termId(missing).build()))
        .andExpect(status().isUnprocessableEntity());
    mockMvc
        .perform(
            postAs(
                adminToken,
                "/api/v1/sections",
                validRequest().lecturerIds(List.of(lecturerA.getId(), missing)).build()))
        .andExpect(status().isUnprocessableEntity());
    // Học phần không tồn tại: ADMIN qua được kiểm quyền nên service trả 422
    mockMvc
        .perform(postAs(adminToken, "/api/v1/sections", validRequest().courseId(missing).build()))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  @DisplayName("US-19/AC-2: người dùng không có vai trò LECTURER hoặc bị khóa -> 422")
  void us19_ac2_lecturerMustBeActiveLecturer() throws Exception {
    User notLecturer = fixtures.user(Role.QA);
    mockMvc
        .perform(
            postAs(
                adminToken,
                "/api/v1/sections",
                validRequest().lecturerIds(List.of(notLecturer.getId())).build()))
        .andExpect(status().isUnprocessableEntity());

    User lockedLecturer = fixtures.user(Role.LECTURER);
    lockedLecturer.setStatus(com.hcmute.lms.lmsapi.enums.UserStatus.LOCKED);
    mongo().save(lockedLecturer);
    mockMvc
        .perform(
            postAs(
                adminToken,
                "/api/v1/sections",
                validRequest().lecturerIds(List.of(lockedLecturer.getId())).build()))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  @DisplayName(
      "US-19/AC-2: học kỳ không còn OPEN (SCORE_LOCKED/CLOSED) không mở được lớp mới -> 422")
  void us19_ac2_termMustBeOpen() throws Exception {
    for (TermStatus termStatus : List.of(TermStatus.SCORE_LOCKED, TermStatus.CLOSED)) {
      Term term = fixtures.term(termStatus);
      mockMvc
          .perform(
              postAs(adminToken, "/api/v1/sections", validRequest().termId(term.getId()).build()))
          .andExpect(status().isUnprocessableEntity());
    }
  }

  @Test
  @DisplayName("US-19/AC-3: trùng mã lớp -> 409; thiếu lecturerIds/mã sai định dạng -> 400")
  void us19_ac3_duplicateCodeAndValidation() throws Exception {
    SectionRequest request = validRequest().build();
    createSectionAs(adminToken, request);

    mockMvc
        .perform(postAs(adminToken, "/api/v1/sections", request))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"));

    mockMvc
        .perform(
            postAs(adminToken, "/api/v1/sections", validRequest().lecturerIds(List.of()).build()))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(postAs(adminToken, "/api/v1/sections", validRequest().code("a b!").build()))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            postAs(adminToken, "/api/v1/sections", validRequest().courseId("not-an-id").build()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("US-19/AC-4: giảng viên chỉ thấy và đọc được lớp mình phụ trách")
  void us19_ac4_lecturerSeesOnlyOwnSections() throws Exception {
    String sectionA = createSectionAs(adminToken, validRequest().build());
    String sectionB =
        createSectionAs(adminToken, validRequest().lecturerIds(List.of(lecturerB.getId())).build());

    mockMvc
        .perform(getAs(lecturerAToken, "/api/v1/sections"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[?(@.id=='" + sectionA + "')]", hasSize(1)))
        .andExpect(
            jsonPath("$.content[?(@.id=='" + sectionA + "')].lecturers[0].fullName")
                .value(org.hamcrest.Matchers.hasItem(lecturerA.getFullName())))
        .andExpect(jsonPath("$.content[?(@.id=='" + sectionB + "')]", hasSize(0)));

    mockMvc
        .perform(getAs(lecturerAToken, "/api/v1/sections/{id}", sectionA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lecturers[0].fullName").value(lecturerA.getFullName()));
    mockMvc
        .perform(getAs(lecturerAToken, "/api/v1/sections/{id}", sectionB))
        .andExpect(status().isForbidden());

    // ADMIN thấy cả hai lớp và hỗ trợ phân trang + search theo mã lớp
    mockMvc
        .perform(getAs(adminToken, "/api/v1/sections?courseId={c}", course.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(sectionA, sectionB)));

    mockMvc
        .perform(getAs(adminToken, "/api/v1/sections?courseId={c}&page=0&size=1", course.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.totalPages").value(2))
        .andExpect(jsonPath("$.last").value(false));

    mockMvc
        .perform(getAs(adminToken, "/api/v1/sections?courseId={c}&page=1&size=1", course.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.last").value(true));
  }

  @Test
  @DisplayName(
      "US-19/AC-4: lọc theo học kỳ và search; ID/page/size sai định dạng -> 400; chưa đăng nhập -> 401")
  void us19_listFiltersAndAuthentication() throws Exception {
    SectionRequest secReq = validRequest().build();
    String section = createSectionAs(adminToken, secReq);
    Term otherTerm = fixtures.term();

    mockMvc
        .perform(getAs(adminToken, "/api/v1/sections?termId={t}", openTerm.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[?(@.id=='" + section + "')]", hasSize(1)));
    mockMvc
        .perform(
            getAs(
                adminToken,
                "/api/v1/sections?termId={t}&search={q}",
                openTerm.getId(),
                secReq.getCode().toLowerCase()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[?(@.id=='" + section + "')]", hasSize(1)));
    mockMvc
        .perform(
            getAs(
                adminToken, "/api/v1/sections?termId={t}&search=NO_SUCH_SECTION", openTerm.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(0)))
        .andExpect(jsonPath("$.totalElements").value(0));
    mockMvc
        .perform(getAs(adminToken, "/api/v1/sections?termId={t}", otherTerm.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(0)));
    mockMvc
        .perform(getAs(adminToken, "/api/v1/sections?termId=bad"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(getAs(adminToken, "/api/v1/sections?page=-1&size=20"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(getAs(adminToken, "/api/v1/sections?page=0&size=101"))
        .andExpect(status().isBadRequest());
    mockMvc.perform(getAs(null, "/api/v1/sections")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("RBAC: LECTURER không được mở lớp; PM ngoài phạm vi CTĐT không được mở/sửa/xem")
  void rbac_writeScope() throws Exception {
    mockMvc
        .perform(postAs(lecturerAToken, "/api/v1/sections", validRequest().build()))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(postAs(pmOutOfScopeToken, "/api/v1/sections", validRequest().build()))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(postAs(null, "/api/v1/sections", validRequest().build()))
        .andExpect(status().isUnauthorized());

    String section = createSectionAs(adminToken, validRequest().build());
    SectionUpdateRequest update =
        SectionUpdateRequest.builder().lecturerIds(List.of(lecturerB.getId())).build();
    SectionStatusUpdateRequest statusUpdate =
        SectionStatusUpdateRequest.builder().status(SectionStatus.CLOSED).build();

    for (String token : List.of(pmOutOfScopeToken, lecturerAToken)) {
      mockMvc
          .perform(putAs(token, "/api/v1/sections/{id}", update, section))
          .andExpect(status().isForbidden());
      mockMvc
          .perform(putAs(token, "/api/v1/sections/{id}/status", statusUpdate, section))
          .andExpect(status().isForbidden());
    }
    // PM ngoài phạm vi cũng không đọc được lớp (không có scope.sectionIds/courseIds)
    mockMvc
        .perform(getAs(pmOutOfScopeToken, "/api/v1/sections/{id}", section))
        .andExpect(status().isForbidden());
    // PM trong phạm vi quản lý được nên đọc lại được lớp do mình mở
    mockMvc
        .perform(getAs(pmInScopeToken, "/api/v1/sections/{id}", section))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("US-19: đổi giảng viên phụ trách; giảng viên mới thấy lớp, giảng viên cũ mất quyền")
  void us19_updateLecturers() throws Exception {
    String section = createSectionAs(adminToken, validRequest().build());

    mockMvc
        .perform(
            putAs(
                pmInScopeToken,
                "/api/v1/sections/{id}",
                SectionUpdateRequest.builder().lecturerIds(List.of(lecturerB.getId())).build(),
                section))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lecturerIds[0]").value(lecturerB.getId()));

    mockMvc
        .perform(getAs(lecturerBToken, "/api/v1/sections/{id}", section))
        .andExpect(status().isOk());
    mockMvc
        .perform(getAs(lecturerAToken, "/api/v1/sections/{id}", section))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            putAs(
                adminToken,
                "/api/v1/sections/{id}",
                SectionUpdateRequest.builder().lecturerIds(List.of()).build(),
                section))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("US-19: khóa lớp (LOCKED) rồi mở lại phải có lý do giải trình; thiếu lý do -> 422")
  void us19_statusTransitions() throws Exception {
    String section = createSectionAs(adminToken, validRequest().build());

    mockMvc
        .perform(
            putAs(
                pmInScopeToken,
                "/api/v1/sections/{id}/status",
                SectionStatusUpdateRequest.builder().status(SectionStatus.LOCKED).build(),
                section))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("LOCKED"));

    mockMvc
        .perform(
            putAs(
                pmInScopeToken,
                "/api/v1/sections/{id}/status",
                SectionStatusUpdateRequest.builder().status(SectionStatus.OPEN).build(),
                section))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));

    mockMvc
        .perform(
            putAs(
                pmInScopeToken,
                "/api/v1/sections/{id}/status",
                SectionStatusUpdateRequest.builder()
                    .status(SectionStatus.OPEN)
                    .reason("Phúc khảo điểm theo quyết định của Hội đồng")
                    .build(),
                section))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("OPEN"));

    // Lớp đã lưu trong CSDL với trạng thái mới
    CourseSection stored = mongo().findById(section, CourseSection.class);
    org.assertj.core.api.Assertions.assertThat(stored).isNotNull();
    org.assertj.core.api.Assertions.assertThat(stored.getStatus()).isEqualTo(SectionStatus.OPEN);
  }

  @Test
  @DisplayName("US-19: lớp không tồn tại -> 403 (fail-closed, không lộ sự tồn tại), kể cả ADMIN")
  void us19_missingSection() throws Exception {
    String missing = ApiTestFixtures.missingId();
    mockMvc
        .perform(getAs(lecturerAToken, "/api/v1/sections/{id}", missing))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(getAs(adminToken, "/api/v1/sections/{id}", missing))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Tải template và import danh sách lớp học phần qua CSV (ADMIN và PM theo khoa)")
  void importSectionsCsv_templateAndBestEffort() throws Exception {
    mockMvc
        .perform(getAs(pmInScopeToken, "/api/v1/sections/import-template"))
        .andExpect(status().isOk())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .string(
                    org.hamcrest.Matchers.containsString(
                        "code,courseCode,syllabusVersion,termCode,lecturerUsernames")));

    String secCode1 = fixtures.code("SECIMP");
    String secCode2 = fixtures.code("SECIMP");
    var file =
        new org.springframework.mock.web.MockMultipartFile(
            "file",
            "sections.csv",
            "text/csv",
            ("code,courseCode,syllabusVersion,termCode,lecturerUsernames\n"
                    + secCode1
                    + ","
                    + course.getCode()
                    + ","
                    + confirmedSyllabus.getVersion()
                    + ","
                    + openTerm.getCode()
                    + ","
                    + lecturerA.getUsername()
                    + "\n"
                    + secCode2
                    + ",UNKNOWN_COURSE,v1,"
                    + openTerm.getCode()
                    + ","
                    + lecturerA.getUsername()
                    + "\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));

    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(
                    "/api/v1/sections/import")
                .file(file)
                .header(org.springframework.http.HttpHeaders.AUTHORIZATION, pmInScopeToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalRows").value(2))
        .andExpect(jsonPath("$.successCount").value(1))
        .andExpect(jsonPath("$.failureCount").value(1));
  }

  @Test
  @DisplayName(
      "GET /api/v1/sections/lecturers: ADMIN và PM tra cứu được GV ACTIVE; LECTURER bị 403")
  void getActiveLecturers_authorizedAndFiltered() throws Exception {
    User lockedLecturer = fixtures.user(Role.LECTURER);
    lockedLecturer.setStatus(com.hcmute.lms.lmsapi.enums.UserStatus.LOCKED);
    lockedLecturer = mongo().save(lockedLecturer);

    User facultyLecturer = fixtures.user(Role.LECTURER);
    facultyLecturer.setFacultyId(program.getFacultyId());
    facultyLecturer = mongo().save(facultyLecturer);

    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                "/api/v1/sections/lecturers"))
        .andExpect(status().isUnauthorized());

    mockMvc
        .perform(getAs(lecturerAToken, "/api/v1/sections/lecturers"))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(getAs(adminToken, "/api/v1/sections/lecturers"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$[*].id",
                org.hamcrest.Matchers.hasItems(
                    lecturerA.getId(), lecturerB.getId(), facultyLecturer.getId())))
        .andExpect(
            jsonPath(
                "$[*].id",
                org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(lockedLecturer.getId()))));

    mockMvc
        .perform(
            getAs(pmInScopeToken, "/api/v1/sections/lecturers?facultyId=" + program.getFacultyId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].id", org.hamcrest.Matchers.hasItem(facultyLecturer.getId())))
        .andExpect(
            jsonPath(
                "$[*].id",
                org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(lecturerA.getId()))));

    mockMvc
        .perform(getAs(pmInScopeToken, "/api/v1/sections/lecturers?facultyId=invalid-id"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("GET /api/v1/sections: giảng viên đã bị LOCKED vẫn hiển thị tên ở lớp cũ")
  void getSections_keepsLockedLecturerSummary() throws Exception {
    User tempLecturer = fixtures.user(Role.LECTURER);
    String sectionId =
        createSectionAs(
            adminToken, validRequest().lecturerIds(List.of(tempLecturer.getId())).build());

    tempLecturer.setStatus(com.hcmute.lms.lmsapi.enums.UserStatus.LOCKED);
    mongo().save(tempLecturer);

    mockMvc
        .perform(getAs(pmInScopeToken, "/api/v1/sections/{id}", sectionId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lecturers[0].id").value(tempLecturer.getId()))
        .andExpect(jsonPath("$.lecturers[0].username").value(tempLecturer.getUsername()))
        .andExpect(jsonPath("$.lecturers[0].fullName").value(tempLecturer.getFullName()));
  }
}
