package com.hcmute.lms.lmsapi.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hcmute.lms.lmsapi.document.Course;
import com.hcmute.lms.lmsapi.document.Faculty;
import com.hcmute.lms.lmsapi.document.Program;
import com.hcmute.lms.lmsapi.document.User;
import com.hcmute.lms.lmsapi.dto.SyllabusStatusUpdateRequest;
import com.hcmute.lms.lmsapi.enums.Role;
import com.hcmute.lms.lmsapi.enums.SyllabusStatus;
import com.hcmute.lms.lmsapi.testsupport.IntegrationTestBase;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

@DisplayName("F02 & F03: Program & Syllabus CSV Import Workflow (UNVERIFIED -> VERIFIED)")
class ProgramAndSyllabusImportIntegrationTest extends IntegrationTestBase {

  private Program program;
  private Course course;
  private String pmToken;
  private String adminToken;
  private String lecturerToken;

  @BeforeEach
  void setUpUsersAndFixtures() {
    Faculty faculty = fixtures.faculty();
    program = fixtures.program();
    program.setFacultyId(faculty.getId());
    program = mongo().save(program);

    course = fixtures.course();
    course.setFacultyId(faculty.getId());
    course = mongo().save(course);

    User pmUser = fixtures.user(Role.PROGRAM_MANAGER);
    pmUser.setFacultyId(faculty.getId());
    pmUser = mongo().save(pmUser);
    pmToken = fixtures.bearer(pmUser);

    adminToken = fixtures.bearerFor(Role.ADMIN);
    lecturerToken = fixtures.bearerFor(Role.LECTURER);
  }

  @Test
  @DisplayName(
      "Import CTĐT & Import Đề cương bởi PROGRAM_MANAGER -> xác nhận -> chỉ đọc GET -> QA verification")
  void fullImportConfirmAndReadOnlyFlow() throws Exception {
    // 1. Tải template CTĐT (ADMIN bị 403)
    mockMvc
        .perform(getAs(adminToken, "/api/v1/program-versions/import-template"))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(getAs(pmToken, "/api/v1/program-versions/import-template"))
        .andExpect(status().isOk())
        .andExpect(
            header()
                .string(
                    HttpHeaders.CONTENT_DISPOSITION,
                    org.hamcrest.Matchers.containsString("program-version-template.csv")));

    // 2. Import CTĐT CSV (LECTURER và ADMIN bị 403)
    String programCsv =
        "ploCode,ploName,ploPassThreshold,ploDisplayOrder,piCode,piName,piWeight,piPassThreshold,courseCodes\n"
            + "PLO1,Kiến thức nền tảng,0.6,1,PI1.1,Phân tích yêu cầu,1.0,0.5,"
            + course.getCode()
            + "\n";

    MockMultipartFile programFile =
        new MockMultipartFile(
            "file", "ctdt.csv", "text/csv", programCsv.getBytes(StandardCharsets.UTF_8));

    assertProgramImportForbidden(lecturerToken, programFile);
    assertProgramImportForbidden(adminToken, programFile);

    MvcResult pvImportRes =
        mockMvc
            .perform(
                multipart("/api/v1/program-versions/import")
                    .file(programFile)
                    .param("programId", program.getId())
                    .param("version", "2026.1")
                    .header(HttpHeaders.AUTHORIZATION, pmToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"))
            .andExpect(jsonPath("$.ploCount").value(1))
            .andExpect(jsonPath("$.piCount").value(1))
            .andExpect(jsonPath("$.courseCount").value(1))
            .andReturn();

    String programVersionId =
        JsonPath.read(pvImportRes.getResponse().getContentAsString(), "$.programVersionId");

    // Import đè lại vào phiên bản DRAFT vẫn hợp lệ để chỉnh sửa bằng CSV
    mockMvc
        .perform(
            multipart("/api/v1/program-versions/import")
                .file(programFile)
                .param("programId", program.getId())
                .param("version", "2026.1")
                .header(HttpHeaders.AUTHORIZATION, pmToken))
        .andExpect(status().isOk());

    // 3. Xác nhận Import CTĐT (ACTIVE & VERIFIED) — ADMIN bị 403
    mockMvc
        .perform(
            postAs(
                adminToken, "/api/v1/program-versions/{id}/confirm-import", null, programVersionId))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(putAs(adminToken, "/api/v1/program-versions/{id}/lock", null, programVersionId))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            putAs(
                adminToken,
                "/api/v1/program-versions/{id}/status",
                java.util.Map.of("status", "ARCHIVED"),
                programVersionId))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            postAs(pmToken, "/api/v1/program-versions/{id}/confirm-import", null, programVersionId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"));

    // Phiên bản đang ACTIVE vẫn cho phép import đè (chỉ ARCHIVED mới bị khóa 422)
    mockMvc
        .perform(
            multipart("/api/v1/program-versions/import")
                .file(programFile)
                .param("programId", program.getId())
                .param("version", "2026.1")
                .header(HttpHeaders.AUTHORIZATION, pmToken))
        .andExpect(status().isOk());

    // 4. Đọc dữ liệu đã import qua GET API
    mockMvc
        .perform(getAs(lecturerToken, "/api/v1/program-versions/{id}/plos", programVersionId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)));
    mockMvc
        .perform(getAs(lecturerToken, "/api/v1/program-versions/{id}/pis", programVersionId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)));
    mockMvc
        .perform(getAs(lecturerToken, "/api/v1/program-versions/{id}/courses", programVersionId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.totalElements").value(1));

    // 5. Tải template Đề cương
    mockMvc
        .perform(
            getAs(pmToken, "/api/v1/courses/{courseId}/syllabi/import-template", course.getId()))
        .andExpect(status().isOk())
        .andExpect(
            header()
                .string(
                    HttpHeaders.CONTENT_DISPOSITION,
                    org.hamcrest.Matchers.containsString("syllabus-clo-template.csv")));

    // 6. Import Đề cương CSV — quyền của PROGRAM_MANAGER; LECTURER bị 403
    String syllabusCsv =
        "cloCode,cloDescription,bloomLevel,defaultPassThreshold,piCode,linkWeight\n"
            + "CLO1,Phân tích bài toán,3,0.5,PI1.1,1.0\n";

    MockMultipartFile syllabusFile =
        new MockMultipartFile(
            "file", "de-cuong.csv", "text/csv", syllabusCsv.getBytes(StandardCharsets.UTF_8));

    mockMvc
        .perform(
            multipart("/api/v1/courses/{courseId}/syllabi/import", course.getId())
                .file(syllabusFile)
                .param("version", "2026.1")
                .param("programVersionId", programVersionId)
                .header(HttpHeaders.AUTHORIZATION, lecturerToken))
        .andExpect(status().isForbidden());

    MvcResult sylImportRes =
        mockMvc
            .perform(
                multipart("/api/v1/courses/{courseId}/syllabi/import", course.getId())
                    .file(syllabusFile)
                    .param("version", "2026.1")
                    .param("programVersionId", programVersionId)
                    .header(HttpHeaders.AUTHORIZATION, pmToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"))
            .andExpect(jsonPath("$.cloCount").value(1))
            .andExpect(jsonPath("$.linkCount").value(1))
            .andReturn();

    String syllabusId =
        JsonPath.read(sylImportRes.getResponse().getContentAsString(), "$.syllabusId");

    // 7. Xác nhận Import Đề cương (VERIFIED)
    mockMvc
        .perform(postAs(pmToken, "/api/v1/syllabi/{id}/confirm-import", null, syllabusId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"));

    // 8. Đọc CLO, liên kết CLO-PI, ma trận và duyệt CONFIRMED + QA verify liên kết
    mockMvc
        .perform(getAs(lecturerToken, "/api/v1/syllabi/{id}/clos", syllabusId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)));

    mockMvc
        .perform(getAs(lecturerToken, "/api/v1/syllabi/{id}/clo-pi-links", syllabusId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].piCode").value("PI1.1"));

    mockMvc
        .perform(getAs(lecturerToken, "/api/v1/syllabi/{id}", syllabusId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.courseCode").value(course.getCode()))
        .andExpect(jsonPath("$.courseName").value(course.getName()));

    mockMvc
        .perform(
            getAs(
                lecturerToken,
                "/api/v1/syllabi/{id}/clo-pi-matrix?programVersionId=" + programVersionId,
                syllabusId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.clos", hasSize(1)))
        .andExpect(jsonPath("$.pis", hasSize(1)));

    mockMvc
        .perform(
            putAs(
                pmToken,
                "/api/v1/syllabi/{id}/status",
                SyllabusStatusUpdateRequest.builder().status(SyllabusStatus.CONFIRMED).build(),
                syllabusId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CONFIRMED"));
  }

  @Test
  @DisplayName(
      "Import PLO/PI kèm trình độ năng lực & category + Import khung học phần theo học kỳ/tiên quyết")
  void importEnrichedPloPiAndProgramCoursesCurriculum() throws Exception {
    Course prereqCourse = fixtures.course();
    Course prevCourse = fixtures.course();

    // 1. Import PLO/PI có ploCapabilityLevel, ploCategory, piCapabilityLevel
    String enrichedProgramCsv =
        "ploCode,ploName,ploPassThreshold,ploDisplayOrder,ploCapabilityLevel,ploCategory,piCode,piName,piWeight,piPassThreshold,piCapabilityLevel,courseCodes\n"
            + "PLO1,Kiến thức nền tảng,0.6,1,4,SPECIALIZED,PI1.1,Phân tích hệ thống,1.0,0.6,4,"
            + course.getCode()
            + "\n";
    MockMultipartFile programFile =
        new MockMultipartFile(
            "file",
            "ctdt-enriched.csv",
            "text/csv",
            enrichedProgramCsv.getBytes(StandardCharsets.UTF_8));

    MvcResult pvRes =
        mockMvc
            .perform(
                multipart("/api/v1/program-versions/import")
                    .file(programFile)
                    .param("programId", program.getId())
                    .param("version", "2026.2")
                    .param("totalCredits", "126")
                    .param("durationYears", "4.0")
                    .param("applicableCohort", "K2024")
                    .header(HttpHeaders.AUTHORIZATION, pmToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalCredits").value(126))
            .andExpect(jsonPath("$.durationYears").value(4.0))
            .andExpect(jsonPath("$.applicableCohort").value("K2024"))
            .andExpect(jsonPath("$.plos[0].capabilityLevelRaw").value(4))
            .andExpect(jsonPath("$.plos[0].category").value("SPECIALIZED"))
            .andExpect(jsonPath("$.pis[0].capabilityLevelRaw").value(4))
            .andReturn();

    String pvId = JsonPath.read(pvRes.getResponse().getContentAsString(), "$.programVersionId");

    // 2. Tải template khung học phần (ADMIN bị 403)
    mockMvc
        .perform(getAs(adminToken, "/api/v1/program-versions/{id}/courses/import-template", pvId))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(getAs(pmToken, "/api/v1/program-versions/{id}/courses/import-template", pvId))
        .andExpect(status().isOk())
        .andExpect(
            header()
                .string(
                    HttpHeaders.CONTENT_DISPOSITION,
                    org.hamcrest.Matchers.containsString("program-courses-template.csv")));

    // 3. Import khung học phần theo học kỳ, môn học trước & tiên quyết
    String coursesCsv =
        "courseCode,semester,credits,type,category,previousCourseCodes,prerequisiteCourseCodes\n"
            + prevCourse.getCode()
            + ",1,3,COMPULSORY,FOUNDATION,,\n"
            + prereqCourse.getCode()
            + ",2,3,COMPULSORY,MAJOR_FOUNDATION,,\n"
            + course.getCode()
            + ",4,4,ELECTIVE,SPECIALIZED,"
            + prevCourse.getCode()
            + ","
            + prereqCourse.getCode()
            + "\n";
    MockMultipartFile coursesFile =
        new MockMultipartFile(
            "file", "program-courses.csv", "text/csv", coursesCsv.getBytes(StandardCharsets.UTF_8));

    // LECTURER và ADMIN bị 403
    assertCourseImportForbidden(lecturerToken, pvId, coursesFile);
    assertCourseImportForbidden(adminToken, pvId, coursesFile);

    mockMvc
        .perform(
            multipart("/api/v1/program-versions/{id}/courses/import", pvId)
                .file(coursesFile)
                .header(HttpHeaders.AUTHORIZATION, pmToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.importedCount").value(2))
        .andExpect(jsonPath("$.updatedCount").value(1))
        .andExpect(jsonPath("$.courses", hasSize(3)));

    // Kiểm tra chi tiết học phần đã cập nhật semester, type, category, previous, prerequisite
    mockMvc
        .perform(
            getAs(
                lecturerToken,
                "/api/v1/program-versions/{pvId}/courses/{courseId}",
                pvId,
                course.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.semester").value(4))
        .andExpect(jsonPath("$.credits").value(4))
        .andExpect(jsonPath("$.type").value("ELECTIVE"))
        .andExpect(jsonPath("$.category").value("SPECIALIZED"))
        .andExpect(jsonPath("$.previousCourseIds[0]").value(prevCourse.getId()))
        .andExpect(jsonPath("$.prerequisiteCourseIds[0]").value(prereqCourse.getId()));
  }

  private void assertProgramImportForbidden(String token, MockMultipartFile file) throws Exception {
    mockMvc
        .perform(
            multipart("/api/v1/program-versions/import")
                .file(file)
                .param("programId", program.getId())
                .param("version", "2026.1")
                .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isForbidden());
  }

  private void assertCourseImportForbidden(String token, String pvId, MockMultipartFile file)
      throws Exception {
    mockMvc
        .perform(
            multipart("/api/v1/program-versions/{id}/courses/import", pvId)
                .file(file)
                .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isForbidden());
  }
}
