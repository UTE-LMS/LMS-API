package com.hcmute.lms.lmsapi.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hcmute.lms.lmsapi.document.Course;
import com.hcmute.lms.lmsapi.document.Program;
import com.hcmute.lms.lmsapi.document.ProgramVersion;
import com.hcmute.lms.lmsapi.document.UserScope;
import com.hcmute.lms.lmsapi.dto.CourseRequest;
import com.hcmute.lms.lmsapi.dto.ProgramCourseRequest;
import com.hcmute.lms.lmsapi.dto.ProgramCourseUpdateRequest;
import com.hcmute.lms.lmsapi.enums.CourseCategory;
import com.hcmute.lms.lmsapi.enums.CourseType;
import com.hcmute.lms.lmsapi.enums.ProgramVersionStatus;
import com.hcmute.lms.lmsapi.enums.Role;
import com.hcmute.lms.lmsapi.testsupport.IntegrationTestBase;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("F03: Courses and ProgramCourses Integration Tests (US-09)")
class CourseIntegrationTest extends IntegrationTestBase {

  private Program program;
  private ProgramVersion programVersion;
  private Course course1;
  private Course course2;

  private String adminToken;
  private String pmToken;
  private String pmOutOfScopeToken;
  private String lecturerToken;

  @BeforeEach
  void setUpFixtures() {
    program = fixtures.program();
    programVersion = fixtures.programVersion(program, ProgramVersionStatus.DRAFT);

    course1 = fixtures.course();
    course2 = fixtures.course();

    adminToken = fixtures.bearerFor(Role.ADMIN, UserScope.builder().build());
    pmToken =
        fixtures.bearerFor(
            Role.PROGRAM_MANAGER, UserScope.builder().programIds(List.of(program.getId())).build());
    pmOutOfScopeToken =
        fixtures.bearerFor(
            Role.PROGRAM_MANAGER,
            UserScope.builder().programIds(List.of("671dcba5f123456789abc999")).build());
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
      "US-09/AC-2: Gán học phần vào programVersionId thành công (201); trùng ném HTTP 409 CONFLICT")
  void us09_ac2_assignCourseToProgramVersionAndDuplicate() throws Exception {
    ProgramCourseRequest request =
        ProgramCourseRequest.builder()
            .courseId(course1.getId())
            .semester(1)
            .credits(3)
            .type(CourseType.COMPULSORY)
            .category(CourseCategory.FOUNDATION)
            .build();

    // 1. Gán thành công -> 201
    mockMvc
        .perform(
            postAs(
                pmToken,
                "/api/v1/program-versions/{pvId}/courses",
                request,
                programVersion.getId()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.programVersionId").value(programVersion.getId()))
        .andExpect(jsonPath("$.courseId").value(course1.getId()))
        .andExpect(jsonPath("$.type").value("COMPULSORY"))
        .andExpect(jsonPath("$.category").value("FOUNDATION"));

    // 2. Trùng (programVersionId, courseId) -> 409 CONFLICT
    mockMvc
        .perform(
            postAs(
                pmToken,
                "/api/v1/program-versions/{pvId}/courses",
                request,
                programVersion.getId()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"));
  }

  @Test
  @DisplayName(
      "US-09/AC-3: prerequisiteCourseIds chứa học phần không tồn tại hoặc chính nó ném HTTP 422")
  void us09_ac3_invalidPrerequisites() throws Exception {
    // 1. Tiên quyết là chính nó -> 422
    ProgramCourseRequest selfPrereq =
        ProgramCourseRequest.builder()
            .courseId(course1.getId())
            .semester(1)
            .credits(3)
            .type(CourseType.COMPULSORY)
            .prerequisiteCourseIds(List.of(course1.getId()))
            .build();

    mockMvc
        .perform(
            postAs(
                pmToken,
                "/api/v1/program-versions/{pvId}/courses",
                selfPrereq,
                programVersion.getId()))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));

    // 2. Tiên quyết là học phần không tồn tại -> 422
    ProgramCourseRequest nonExistentPrereq =
        ProgramCourseRequest.builder()
            .courseId(course1.getId())
            .semester(1)
            .credits(3)
            .type(CourseType.COMPULSORY)
            .prerequisiteCourseIds(List.of("671dcba5f123456789ffffff"))
            .build();

    mockMvc
        .perform(
            postAs(
                pmToken,
                "/api/v1/program-versions/{pvId}/courses",
                nonExistentPrereq,
                programVersion.getId()))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
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

    // 1. PROGRAM_MANAGER tạo course -> 201
    mockMvc.perform(postAs(pmToken, "/api/v1/courses", courseReq)).andExpect(status().isCreated());

    // 2. LECTURER tạo course -> 403
    mockMvc
        .perform(postAs(lecturerToken, "/api/v1/courses", courseReq))
        .andExpect(status().isForbidden());

    // 3. PM ngoài scope gán học phần vào programVersion -> 403
    ProgramCourseRequest pcReq =
        ProgramCourseRequest.builder()
            .courseId(course1.getId())
            .semester(1)
            .credits(3)
            .type(CourseType.COMPULSORY)
            .build();

    mockMvc
        .perform(
            postAs(
                pmOutOfScopeToken,
                "/api/v1/program-versions/{pvId}/courses",
                pcReq,
                programVersion.getId()))
        .andExpect(status().isForbidden());

    // 4. LECTURER gán học phần vào programVersion -> 403
    mockMvc
        .perform(
            postAs(
                lecturerToken,
                "/api/v1/program-versions/{pvId}/courses",
                pcReq,
                programVersion.getId()))
        .andExpect(status().isForbidden());

    // 5. Unauthenticated -> 401
    mockMvc
        .perform(
            postAs(null, "/api/v1/program-versions/{pvId}/courses", pcReq, programVersion.getId()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("CRUD vòng đời: Gán học phần, cập nhật, lấy chi tiết, xóa khỏi CTĐT và xóa học phần")
  void lifecycleCrudTest() throws Exception {
    // 1. Gán course2 vào programVersion với tiên quyết là course1
    ProgramCourseRequest assignReq =
        ProgramCourseRequest.builder()
            .courseId(course2.getId())
            .semester(2)
            .credits(4)
            .type(CourseType.ELECTIVE)
            .category(CourseCategory.SPECIALIZED)
            .prerequisiteCourseIds(List.of(course1.getId()))
            .build();

    mockMvc
        .perform(
            postAs(
                pmToken,
                "/api/v1/program-versions/{pvId}/courses",
                assignReq,
                programVersion.getId()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.prerequisiteCourseIds", hasSize(1)));

    // 2. Lấy danh sách học phần của CTĐT
    mockMvc
        .perform(getAs(pmToken, "/api/v1/program-versions/{pvId}/courses", programVersion.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].courseId").value(course2.getId()))
        .andExpect(jsonPath("$[0].course.code").value(course2.getCode()));

    // 3. Lấy chi tiết gán học phần
    mockMvc
        .perform(
            getAs(
                pmToken,
                "/api/v1/program-versions/{pvId}/courses/{courseId}",
                programVersion.getId(),
                course2.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.semester").value(2));

    // 4. Cập nhật gán học phần (đổi semester = 3)
    ProgramCourseUpdateRequest updateReq =
        ProgramCourseUpdateRequest.builder()
            .semester(3)
            .credits(4)
            .type(CourseType.COMPULSORY)
            .category(CourseCategory.SPECIALIZED)
            .prerequisiteCourseIds(List.of(course1.getId()))
            .build();

    mockMvc
        .perform(
            putAs(
                pmToken,
                "/api/v1/program-versions/{pvId}/courses/{courseId}",
                updateReq,
                programVersion.getId(),
                course2.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.semester").value(3))
        .andExpect(jsonPath("$.type").value("COMPULSORY"));

    // 5. Cố gắng xóa course2 khi đang được tham chiếu trong CTĐT -> 422
    mockMvc
        .perform(deleteAs(adminToken, "/api/v1/courses/{id}", course2.getId()))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));

    // 6. Xóa gán course2 khỏi CTĐT -> 204
    mockMvc
        .perform(
            deleteAs(
                pmToken,
                "/api/v1/program-versions/{pvId}/courses/{courseId}",
                programVersion.getId(),
                course2.getId()))
        .andExpect(status().isNoContent());

    // 7. Xóa course2 khi đã không còn tham chiếu -> 204
    mockMvc
        .perform(deleteAs(adminToken, "/api/v1/courses/{id}", course2.getId()))
        .andExpect(status().isNoContent());
  }
}
