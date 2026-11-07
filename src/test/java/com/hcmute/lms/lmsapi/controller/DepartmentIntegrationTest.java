package com.hcmute.lms.lmsapi.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hcmute.lms.lmsapi.document.Course;
import com.hcmute.lms.lmsapi.document.Program;
import com.hcmute.lms.lmsapi.document.RoleAssignment;
import com.hcmute.lms.lmsapi.document.User;
import com.hcmute.lms.lmsapi.document.UserScope;
import com.hcmute.lms.lmsapi.dto.AssignCourseRequest;
import com.hcmute.lms.lmsapi.enums.Role;
import com.hcmute.lms.lmsapi.enums.UserStatus;
import com.hcmute.lms.lmsapi.repository.UserRepository;
import com.hcmute.lms.lmsapi.testsupport.IntegrationTestBase;
import java.util.ArrayList;
import java.util.List;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("F03 / ADR 0010: Department Course Assignment & Scoped Lecturer Lookup Tests")
class DepartmentIntegrationTest extends IntegrationTestBase {

  @Autowired private UserRepository userRepository;

  private Program program1;
  private Program program2;
  private Course course1;
  private Course course2;

  private String adminToken;
  private String deptHeadInScopeToken;
  private String deptHeadOutOfScopeToken;
  private String deptHeadProgram1Token;
  private String lecturerToken;
  private String pmToken;

  private User targetLecturer;
  private User lecturerProgram1;
  private User lecturerProgram2;
  private User studentUser;

  @BeforeEach
  void setUpFixtures() {
    program1 = fixtures.program();
    program2 = fixtures.program();
    course1 = fixtures.course();
    course2 = fixtures.course();

    adminToken = fixtures.bearerFor(Role.ADMIN, UserScope.builder().build());

    deptHeadInScopeToken =
        fixtures.bearerFor(
            Role.DEPARTMENT_HEAD, UserScope.builder().courseIds(List.of(course1.getId())).build());

    deptHeadOutOfScopeToken =
        fixtures.bearerFor(
            Role.DEPARTMENT_HEAD, UserScope.builder().courseIds(List.of(course2.getId())).build());

    deptHeadProgram1Token =
        fixtures.bearerFor(
            Role.DEPARTMENT_HEAD,
            UserScope.builder()
                .programIds(List.of(program1.getId()))
                .courseIds(List.of(course1.getId()))
                .build());

    lecturerToken = fixtures.bearerFor(Role.LECTURER, UserScope.builder().build());
    pmToken = fixtures.bearerFor(Role.PROGRAM_MANAGER, UserScope.builder().build());

    targetLecturer =
        userRepository.save(
            User.builder()
                .username("lecturer_" + fixtures.code("LEC").toLowerCase())
                .email("lecturer_" + fixtures.code("LEC").toLowerCase() + "@hcmute.edu.vn")
                .fullName("Giảng viên bộ môn thử nghiệm")
                .passwordHash("$2a$10$abcdefghijklmnopqrstuv")
                .status(UserStatus.ACTIVE)
                .roleAssignments(
                    new ArrayList<>(
                        List.of(
                            RoleAssignment.builder()
                                .role(Role.LECTURER)
                                .scope(UserScope.builder().courseIds(new ArrayList<>()).build())
                                .build())))
                .build());

    lecturerProgram1 =
        userRepository.save(
            User.builder()
                .username("lec_p1_" + fixtures.code("LP1").toLowerCase())
                .email("lec_p1_" + fixtures.code("LP1").toLowerCase() + "@hcmute.edu.vn")
                .fullName("Giảng viên Khoa CNTT (Program 1)")
                .passwordHash("$2a$10$abcdefghijklmnopqrstuv")
                .status(UserStatus.ACTIVE)
                .roleAssignments(
                    new ArrayList<>(
                        List.of(
                            RoleAssignment.builder()
                                .role(Role.LECTURER)
                                .scope(
                                    UserScope.builder()
                                        .programIds(new ArrayList<>(List.of(program1.getId())))
                                        .courseIds(new ArrayList<>())
                                        .build())
                                .build())))
                .build());

    lecturerProgram2 =
        userRepository.save(
            User.builder()
                .username("lec_p2_" + fixtures.code("LP2").toLowerCase())
                .email("lec_p2_" + fixtures.code("LP2").toLowerCase() + "@hcmute.edu.vn")
                .fullName("Giảng viên Khoa Kinh tế (Program 2)")
                .passwordHash("$2a$10$abcdefghijklmnopqrstuv")
                .status(UserStatus.ACTIVE)
                .roleAssignments(
                    new ArrayList<>(
                        List.of(
                            RoleAssignment.builder()
                                .role(Role.LECTURER)
                                .scope(
                                    UserScope.builder()
                                        .programIds(new ArrayList<>(List.of(program2.getId())))
                                        .courseIds(new ArrayList<>())
                                        .build())
                                .build())))
                .build());

    studentUser =
        userRepository.save(
            User.builder()
                .username("student_" + fixtures.code("STU").toLowerCase())
                .email("student_" + fixtures.code("STU").toLowerCase() + "@student.hcmute.edu.vn")
                .fullName("Sinh viên thử nghiệm")
                .passwordHash("$2a$10$abcdefghijklmnopqrstuv")
                .status(UserStatus.ACTIVE)
                .roleAssignments(
                    new ArrayList<>(
                        List.of(RoleAssignment.builder().role(Role.STUDENT).scope(null).build())))
                .build());
  }

  @AfterEach
  void cleanUp() {
    for (User u : List.of(targetLecturer, lecturerProgram1, lecturerProgram2, studentUser)) {
      if (u != null && u.getId() != null) {
        userRepository.deleteById(u.getId());
      }
    }
  }

  @Test
  @DisplayName("TBM phân công môn trong scope cho Giảng viên -> 200 OK (ADR 0010)")
  void deptHead_assignCourseInScope_success() throws Exception {
    AssignCourseRequest req =
        AssignCourseRequest.builder()
            .courseId(course1.getId())
            .lecturerId(targetLecturer.getId())
            .build();

    mockMvc
        .perform(postAs(deptHeadInScopeToken, "/api/v1/departments/assign-course", req))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$.roleAssignments[?(@.role == 'LECTURER')].scope.courseIds[*]",
                hasItem(course1.getId())));

    // Unassign course -> 200 OK
    mockMvc
        .perform(postAs(deptHeadInScopeToken, "/api/v1/departments/unassign-course", req))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$.roleAssignments[?(@.role == 'LECTURER')].scope.courseIds[*]",
                not(hasItem(course1.getId()))));
  }

  @Test
  @DisplayName(
      "TBM có scope.programIds chỉ nhìn thấy và phân công được Giảng viên cùng Khoa/Ngành (Phương án A)")
  void deptHead_onlySeesAndAssignsLecturersInSameProgramScope() throws Exception {
    // 1. Tra cứu danh sách giảng viên: chỉ thấy lecturerProgram1, KHÔNG thấy lecturerProgram2
    mockMvc
        .perform(
            getAs(
                deptHeadProgram1Token, "/api/v1/departments/lecturers?courseId=" + course1.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].id", hasItem(lecturerProgram1.getId())))
        .andExpect(jsonPath("$[*].id", not(hasItem(lecturerProgram2.getId()))));

    // 2. Phân công cho giảng viên cùng Khoa/Ngành (Program 1) -> 200 OK
    AssignCourseRequest validReq =
        AssignCourseRequest.builder()
            .courseId(course1.getId())
            .lecturerId(lecturerProgram1.getId())
            .build();
    mockMvc
        .perform(postAs(deptHeadProgram1Token, "/api/v1/departments/assign-course", validReq))
        .andExpect(status().isOk());

    // 3. Cố tình phân công cho giảng viên khác Khoa/Ngành (Program 2) -> 422 Unprocessable Entity
    AssignCourseRequest crossDeptReq =
        AssignCourseRequest.builder()
            .courseId(course1.getId())
            .lecturerId(lecturerProgram2.getId())
            .build();
    mockMvc
        .perform(postAs(deptHeadProgram1Token, "/api/v1/departments/assign-course", crossDeptReq))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  @DisplayName("TBM gọi GET /lecturers với courseId ngoài scope -> 403 Forbidden")
  void deptHead_getLecturersForOutOfScopeCourse_forbidden() throws Exception {
    mockMvc
        .perform(
            getAs(
                deptHeadOutOfScopeToken,
                "/api/v1/departments/lecturers?courseId=" + course1.getId()))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("TBM phân công môn ngoài scope -> 403 Forbidden (ADR 0010)")
  void deptHead_assignCourseOutOfScope_forbidden() throws Exception {
    AssignCourseRequest req =
        AssignCourseRequest.builder()
            .courseId(course1.getId())
            .lecturerId(targetLecturer.getId())
            .build();

    mockMvc
        .perform(postAs(deptHeadOutOfScopeToken, "/api/v1/departments/assign-course", req))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName(
      "ADMIN không có thẩm quyền Bộ môn: gọi /departments/lecturers hoặc assign-course -> 403 Forbidden")
  void admin_cannotAccessDepartmentEndpoints_returns403() throws Exception {
    mockMvc
        .perform(getAs(adminToken, "/api/v1/departments/lecturers"))
        .andExpect(status().isForbidden());

    AssignCourseRequest req =
        AssignCourseRequest.builder()
            .courseId(course2.getId())
            .lecturerId(targetLecturer.getId())
            .build();

    mockMvc
        .perform(postAs(adminToken, "/api/v1/departments/assign-course", req))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("LECTURER hoặc PM cố tình gọi API phân công môn -> 403 Forbidden")
  void lecturerOrPm_cannotAssignCourse() throws Exception {
    AssignCourseRequest req =
        AssignCourseRequest.builder()
            .courseId(course1.getId())
            .lecturerId(targetLecturer.getId())
            .build();

    mockMvc
        .perform(postAs(lecturerToken, "/api/v1/departments/assign-course", req))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(postAs(pmToken, "/api/v1/departments/assign-course", req))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName(
      "Phân công môn học không tồn tại -> 403 Forbidden (fail-closed bảo vệ trước khi vào service)")
  void assignCourse_courseNotFound_403() throws Exception {
    AssignCourseRequest req =
        AssignCourseRequest.builder()
            .courseId(new ObjectId().toHexString())
            .lecturerId(targetLecturer.getId())
            .build();

    mockMvc
        .perform(postAs(deptHeadInScopeToken, "/api/v1/departments/assign-course", req))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Phân công cho người dùng không phải LECTURER -> 422 Business Rule Violation")
  void assignCourse_targetNotLecturer_422() throws Exception {
    AssignCourseRequest req =
        AssignCourseRequest.builder()
            .courseId(course1.getId())
            .lecturerId(studentUser.getId())
            .build();

    mockMvc
        .perform(postAs(deptHeadInScopeToken, "/api/v1/departments/assign-course", req))
        .andExpect(status().isUnprocessableEntity());
  }
}
