package com.hcmute.lms.lmsapi.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hcmute.lms.lmsapi.document.Course;
import com.hcmute.lms.lmsapi.document.Program;
import com.hcmute.lms.lmsapi.document.UserScope;
import com.hcmute.lms.lmsapi.dto.SyllabusStatusUpdateRequest;
import com.hcmute.lms.lmsapi.enums.Role;
import com.hcmute.lms.lmsapi.enums.SyllabusStatus;
import com.hcmute.lms.lmsapi.testsupport.IntegrationTestBase;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
  private String lecturerToken;

  @BeforeEach
  void setUpUsersAndFixtures() {
    program = fixtures.program();
    course = fixtures.course();

    UserScope pmScope = UserScope.builder().programIds(List.of(program.getId())).build();
    pmToken = fixtures.bearerFor(Role.PROGRAM_MANAGER, pmScope);

    UserScope lecturerScope = UserScope.builder().courseIds(List.of(course.getId())).build();
    lecturerToken = fixtures.bearerFor(Role.LECTURER, lecturerScope);
  }

  @Test
  @DisplayName(
      "Import CTĐT & Import Đề cương bởi PROGRAM_MANAGER -> xác nhận -> chỉ đọc GET -> QA verification")
  void fullImportConfirmAndReadOnlyFlow() throws Exception {
    // 1. Tải template CTĐT
    mockMvc
        .perform(getAs(pmToken, "/api/v1/program-versions/import-template"))
        .andExpect(status().isOk())
        .andExpect(
            header()
                .string(
                    HttpHeaders.CONTENT_DISPOSITION,
                    org.hamcrest.Matchers.containsString("program-version-template.csv")));

    // 2. Import CTĐT CSV (LECTURER bị 403)
    String programCsv =
        "ploCode,ploName,ploPassThreshold,ploDisplayOrder,piCode,piName,piWeight,piPassThreshold,courseCodes\n"
            + "PLO1,Kiến thức nền tảng,0.6,1,PI1.1,Phân tích yêu cầu,1.0,0.5,"
            + course.getCode()
            + "\n";

    MockMultipartFile programFile =
        new MockMultipartFile(
            "file", "ctdt.csv", "text/csv", programCsv.getBytes(StandardCharsets.UTF_8));

    mockMvc
        .perform(
            multipart("/api/v1/program-versions/import")
                .file(programFile)
                .param("programId", program.getId())
                .param("version", "2026.1")
                .header(HttpHeaders.AUTHORIZATION, lecturerToken))
        .andExpect(status().isForbidden());

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

    // 3. Xác nhận Import CTĐT (ACTIVE & VERIFIED)
    mockMvc
        .perform(
            postAs(pmToken, "/api/v1/program-versions/{id}/confirm-import", null, programVersionId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"));

    // Phiên bản đã ACTIVE thì không được import đè nữa (422)
    mockMvc
        .perform(
            multipart("/api/v1/program-versions/import")
                .file(programFile)
                .param("programId", program.getId())
                .param("version", "2026.1")
                .header(HttpHeaders.AUTHORIZATION, pmToken))
        .andExpect(status().isUnprocessableEntity());

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
        .andExpect(jsonPath("$", hasSize(1)));

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
        .andExpect(jsonPath("$", hasSize(1)));

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
}
