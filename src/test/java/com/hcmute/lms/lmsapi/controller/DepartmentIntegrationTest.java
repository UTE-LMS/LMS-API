package com.hcmute.lms.lmsapi.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hcmute.lms.lmsapi.document.Course;
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

@DisplayName("F03 / ADR 0010: Department Course Assignment Integration Tests")
class DepartmentIntegrationTest extends IntegrationTestBase {

  @Autowired private UserRepository userRepository;

  private Course course1;
  private Course course2;

  private String adminToken;
  private String deptHeadInScopeToken;
  private String deptHeadOutOfScopeToken;
  private String lecturerToken;
  private String pmToken;

  private User targetLecturer;
  private User studentUser;

  @BeforeEach
  void setUpFixtures() {
    course1 = fixtures.course();
    course2 = fixtures.course();

    adminToken = fixtures.bearerFor(Role.ADMIN, UserScope.builder().build());

    deptHeadInScopeToken =
        fixtures.bearerFor(
            Role.DEPARTMENT_HEAD, UserScope.builder().courseIds(List.of(course1.getId())).build());

    deptHeadOutOfScopeToken =
        fixtures.bearerFor(
            Role.DEPARTMENT_HEAD, UserScope.builder().courseIds(List.of(course2.getId())).build());

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
    if (targetLecturer != null) {
      userRepository.deleteById(targetLecturer.getId());
    }
    if (studentUser != null) {
      userRepository.deleteById(studentUser.getId());
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
  @DisplayName("ADMIN có thể phân công bất kỳ môn học nào -> 200 OK")
  void admin_assignCourse_success() throws Exception {
    AssignCourseRequest req =
        AssignCourseRequest.builder()
            .courseId(course2.getId())
            .lecturerId(targetLecturer.getId())
            .build();

    mockMvc
        .perform(postAs(adminToken, "/api/v1/departments/assign-course", req))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$.roleAssignments[?(@.role == 'LECTURER')].scope.courseIds[*]",
                hasItem(course2.getId())));
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
  @DisplayName("Phân công môn học không tồn tại -> 404 Not Found")
  void assignCourse_courseNotFound_404() throws Exception {
    AssignCourseRequest req =
        AssignCourseRequest.builder()
            .courseId(new ObjectId().toHexString())
            .lecturerId(targetLecturer.getId())
            .build();

    mockMvc
        .perform(postAs(adminToken, "/api/v1/departments/assign-course", req))
        .andExpect(status().isNotFound());
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
        .perform(postAs(adminToken, "/api/v1/departments/assign-course", req))
        .andExpect(status().isUnprocessableEntity());
  }
}
