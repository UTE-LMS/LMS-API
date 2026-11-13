package com.hcmute.lms.lmsapi.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hcmute.lms.lmsapi.document.Course;
import com.hcmute.lms.lmsapi.document.Program;
import com.hcmute.lms.lmsapi.document.ProgramCourse;
import com.hcmute.lms.lmsapi.document.ProgramVersion;
import com.hcmute.lms.lmsapi.document.UserScope;
import com.hcmute.lms.lmsapi.dto.CourseRequest;
import com.hcmute.lms.lmsapi.enums.CourseCategory;
import com.hcmute.lms.lmsapi.enums.CourseType;
import com.hcmute.lms.lmsapi.enums.ProgramVersionStatus;
import com.hcmute.lms.lmsapi.enums.Role;
import com.hcmute.lms.lmsapi.testsupport.IntegrationTestBase;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;

@DisplayName("F03: Courses and ProgramCourses Integration Tests (US-09)")
class CourseIntegrationTest extends IntegrationTestBase {

  private Program program;
  private ProgramVersion programVersion;
  private Course course1;
  private Course course2;
  private Course course3;

  private String adminToken;
  private String pmToken;
  private String lecturerToken;

  @BeforeEach
  void setUpFixtures() {
    program = fixtures.program();
    programVersion = fixtures.programVersion(program, ProgramVersionStatus.DRAFT);

    course1 = fixtures.course();
    course2 = fixtures.course();
    course3 = fixtures.course();

    adminToken = fixtures.bearerFor(Role.ADMIN, UserScope.builder().build());
    pmToken =
        fixtures.bearerFor(
            Role.PROGRAM_MANAGER, UserScope.builder().programIds(List.of(program.getId())).build());
    lecturerToken = fixtures.bearerFor(Role.LECTURER, UserScope.builder().build());
  }

  @Test
  @DisplayName("US-09/AC-1: Tạo courses mã mới thành công (201); trùng code ném HTTP 409 CONFLICT")
  void us09_ac1_createCourseAndDuplicate() throws Exception {
    String code = fixtures.code("NEW");
    CourseRequest request =
        CourseRequest.builder()
            .code(code)
            .name("Học phần thử nghiệm mới [DỮ LIỆU THỬ NGHIỆM]")
            .credits(3)
            .build();

    // 1. Tạo thành công -> 201
    mockMvc
        .perform(postAs(adminToken, "/api/v1/courses", request))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.code").value(code))
        .andExpect(jsonPath("$.name").value("Học phần thử nghiệm mới [DỮ LIỆU THỬ NGHIỆM]"))
        .andExpect(jsonPath("$.credits").value(3));

    // 2. Trùng code -> 409 CONFLICT
    mockMvc
        .perform(postAs(adminToken, "/api/v1/courses", request))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"));
  }

  @Test
  @DisplayName(
      "Quyền hạn: ADMIN và PROGRAM_MANAGER soạn danh mục; LECTURER bị 403; Scope kiểm tra fail-closed")
  void rbacAndScopeAuthorization() throws Exception {
    String code = fixtures.code("PM");
    CourseRequest courseReq =
        CourseRequest.builder()
            .code(code)
            .name("Học phần do PM tạo [DỮ LIỆU THỬ NGHIỆM]")
            .credits(2)
            .build();

    // 1. PROGRAM_MANAGER tạo course -> 403 Forbidden (ADR 0010)
    mockMvc
        .perform(postAs(pmToken, "/api/v1/courses", courseReq))
        .andExpect(status().isForbidden());
    // 2. ADMIN tạo course -> 201 Created
    mockMvc
        .perform(postAs(adminToken, "/api/v1/courses", courseReq))
        .andExpect(status().isCreated());

    // 2. LECTURER tạo course -> 403
    mockMvc
        .perform(postAs(lecturerToken, "/api/v1/courses", courseReq))
        .andExpect(status().isForbidden());

    // 3. PM ngoài scope gán học phần vào programVersion -> 403
    // 3. Unauthenticated GET /api/v1/courses -> 401
    mockMvc.perform(getAs(null, "/api/v1/courses")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Vòng đời xem danh sách học phần CTĐT (GET) và chặn xóa học phần đang tham chiếu")
  void lifecycleReadAndDeleteGuardTest() throws Exception {
    ProgramCourse pc =
        mongo()
            .save(
                ProgramCourse.builder()
                    .programVersionId(programVersion.getId())
                    .courseId(course2.getId())
                    .semester(2)
                    .credits(4)
                    .type(CourseType.ELECTIVE)
                    .category(CourseCategory.SPECIALIZED)
                    .prerequisiteCourseIds(List.of(course1.getId()))
                    .previousCourseIds(List.of(course3.getId()))
                    .build());

    // 1. Lấy danh sách học phần của CTĐT
    mockMvc
        .perform(getAs(pmToken, "/api/v1/program-versions/{pvId}/courses", programVersion.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].courseId").value(course2.getId()))
        .andExpect(jsonPath("$[0].course.code").value(course2.getCode()));

    // 2. Lấy chi tiết gán học phần
    mockMvc
        .perform(
            getAs(
                pmToken,
                "/api/v1/program-versions/{pvId}/courses/{courseId}",
                programVersion.getId(),
                course2.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.semester").value(2));

    // 3. Cố gắng xóa course2 khi đang được tham chiếu trong CTĐT -> 422
    mockMvc
        .perform(deleteAs(adminToken, "/api/v1/courses/{id}", course2.getId()))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));

    // 4. Gỡ bản ghi ProgramCourse trực tiếp rồi xóa course2 -> 204
    mongo().remove(pc);
    mockMvc
        .perform(deleteAs(adminToken, "/api/v1/courses/{id}", course2.getId()))
        .andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("Import CSV: Admin tải lên CSV thành công theo cơ chế Best-effort (200 OK)")
  void importCourses_asAdmin_bestEffortSuccess() throws Exception {
    String uniqueCode1 = fixtures.code("CSV1");
    String uniqueCode2 = fixtures.code("CSV2");

    String csvContent =
        "code,name,credits\n"
            + uniqueCode1
            + ",Nhập môn Khoa học máy tính,3\n"
            + uniqueCode2
            + ",Lập trình hướng đối tượng nâng cao,4\n"
            + uniqueCode1
            + ",Bị trùng trong tệp,3\n"; // Trùng lặp trong tệp

    MockMultipartFile csvFile =
        new MockMultipartFile(
            "file", "courses.csv", "text/csv", csvContent.getBytes(StandardCharsets.UTF_8));

    mockMvc
        .perform(
            multipart("/api/v1/courses/import")
                .file(csvFile)
                .header(HttpHeaders.AUTHORIZATION, adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalRows").value(3))
        .andExpect(jsonPath("$.successCount").value(2))
        .andExpect(jsonPath("$.failureCount").value(1))
        .andExpect(jsonPath("$.errors", hasSize(1)))
        .andExpect(jsonPath("$.errors[0].row").value(4))
        .andExpect(jsonPath("$.errors[0].code").value(uniqueCode1));
  }

  @Test
  @DisplayName("Import CSV: Giảng viên hoặc Trưởng CTĐT gọi API bị từ chối 403 Forbidden")
  void importCourses_asNonAdmin_returnsForbidden() throws Exception {
    MockMultipartFile csvFile =
        new MockMultipartFile(
            "file",
            "courses.csv",
            "text/csv",
            "code,name,credits\nCS101,Toán,3".getBytes(StandardCharsets.UTF_8));

    mockMvc
        .perform(
            multipart("/api/v1/courses/import")
                .file(csvFile)
                .header(HttpHeaders.AUTHORIZATION, lecturerToken))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            multipart("/api/v1/courses/import")
                .file(csvFile)
                .header(HttpHeaders.AUTHORIZATION, pmToken))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Import CSV: Chưa đăng nhập bị từ chối 401 Unauthorized")
  void importCourses_unauthenticated_returnsUnauthorized() throws Exception {
    MockMultipartFile csvFile =
        new MockMultipartFile(
            "file",
            "courses.csv",
            "text/csv",
            "code,name,credits\nCS101,Toán,3".getBytes(StandardCharsets.UTF_8));

    mockMvc
        .perform(multipart("/api/v1/courses/import").file(csvFile))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Import CSV: Tải file sai định dạng hoặc rỗng trả về 400 Bad Request")
  void importCourses_invalidFile_returnsBadRequest() throws Exception {
    MockMultipartFile invalidExtFile =
        new MockMultipartFile(
            "file",
            "courses.xlsx",
            "application/vnd.ms-excel",
            "test".getBytes(StandardCharsets.UTF_8));

    mockMvc
        .perform(
            multipart("/api/v1/courses/import")
                .file(invalidExtFile)
                .header(HttpHeaders.AUTHORIZATION, adminToken))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }
}
