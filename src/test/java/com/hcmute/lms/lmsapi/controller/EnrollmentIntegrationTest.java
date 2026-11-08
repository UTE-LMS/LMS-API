package com.hcmute.lms.lmsapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hcmute.lms.lmsapi.document.Course;
import com.hcmute.lms.lmsapi.document.CourseSection;
import com.hcmute.lms.lmsapi.document.Enrollment;
import com.hcmute.lms.lmsapi.document.Student;
import com.hcmute.lms.lmsapi.document.Term;
import com.hcmute.lms.lmsapi.document.User;
import com.hcmute.lms.lmsapi.document.UserScope;
import com.hcmute.lms.lmsapi.dto.EnrollmentStatusUpdateRequest;
import com.hcmute.lms.lmsapi.enums.EnrollmentStatus;
import com.hcmute.lms.lmsapi.enums.Role;
import com.hcmute.lms.lmsapi.enums.SectionStatus;
import com.hcmute.lms.lmsapi.enums.TermStatus;
import com.hcmute.lms.lmsapi.testsupport.ApiTestFixtures;
import com.hcmute.lms.lmsapi.testsupport.IntegrationTestBase;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("F05a: Enrollment Integration Tests (US-20)")
class EnrollmentIntegrationTest extends IntegrationTestBase {

  private Term term;
  private CourseSection section;
  private User lecturer;
  private User otherLecturer;
  private String adminToken;
  private String lecturerToken;
  private String otherLecturerToken;

  @BeforeEach
  void setUpFixtures() {
    Course course = fixtures.course();
    term = fixtures.term();
    lecturer = fixtures.user(Role.LECTURER, UserScope.builder().build());
    otherLecturer = fixtures.user(Role.LECTURER, UserScope.builder().build());
    section = fixtures.section(course.getId(), List.of(lecturer.getId()));
    section.setTermId(term.getId());
    section = mongo().save(section);

    adminToken = fixtures.bearerFor(Role.ADMIN, UserScope.builder().build());
    lecturerToken = fixtures.bearer(lecturer);
    otherLecturerToken = fixtures.bearer(otherLecturer);
  }

  private CourseSection reloadSection() {
    return mongo().findById(section.getId(), CourseSection.class);
  }

  private EnrollmentStatusUpdateRequest statusRequest(EnrollmentStatus status) {
    return EnrollmentStatusUpdateRequest.builder().status(status).build();
  }

  @Test
  @DisplayName(
      "US-20 (mẫu số): lớp có 3 ghi danh WITHDRAWN thì ENROLLED chỉ đếm số còn lại (ví dụ minh họa: 45 ghi danh, 3 rút -> 42)")
  void enrolledIsTheDenominatorWithdrawnIsExcluded() throws Exception {
    // Ví dụ minh họa lấy từ plan F05a §8: 45 ghi danh, trong đó 3 người WITHDRAWN
    for (int i = 0; i < 45; i++) {
      Student student = fixtures.student();
      fixtures.enrollment(
          section, student, i < 3 ? EnrollmentStatus.WITHDRAWN : EnrollmentStatus.ENROLLED);
    }

    mockMvc
        .perform(getAs(lecturerToken, "/api/v1/sections/{id}/enrollments", section.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sectionId").value(section.getId()))
        .andExpect(jsonPath("$.enrolled").value(42))
        .andExpect(jsonPath("$.withdrawn").value(3))
        .andExpect(jsonPath("$.enrollments", hasSize(45)));

    mockMvc
        .perform(
            getAs(
                lecturerToken,
                "/api/v1/sections/{id}/enrollments?status=WITHDRAWN",
                section.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enrollments", hasSize(3)))
        .andExpect(jsonPath("$.enrolled").value(42));
  }

  @Test
  @DisplayName("US-20: danh sách ghi danh trả mã thay thế và sắp theo studentRef")
  void listIsSortedAndExposesOnlySurrogateRef() throws Exception {
    Student studentB = fixtures.student();
    Student studentA = fixtures.student();
    fixtures.enrollment(section, studentB, EnrollmentStatus.ENROLLED);
    fixtures.enrollment(section, studentA, EnrollmentStatus.ENROLLED);
    List<String> sorted =
        List.of(studentA.getStudentRef(), studentB.getStudentRef()).stream().sorted().toList();

    mockMvc
        .perform(getAs(adminToken, "/api/v1/sections/{id}/enrollments", section.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enrollments[0].studentRef").value(sorted.get(0)))
        .andExpect(jsonPath("$.enrollments[1].studentRef").value(sorted.get(1)))
        .andExpect(jsonPath("$.enrollments[0].fullName").doesNotExist())
        .andExpect(jsonPath("$.enrollments[0].email").doesNotExist());
  }

  @Test
  @DisplayName(
      "US-19/AC-4 & RBAC: giảng viên ngoài lớp -> 403; chưa đăng nhập -> 401; lớp lạ -> 403")
  void readAccessIsScopedToTheSection() throws Exception {
    mockMvc
        .perform(getAs(otherLecturerToken, "/api/v1/sections/{id}/enrollments", section.getId()))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(getAs(null, "/api/v1/sections/{id}/enrollments", section.getId()))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            getAs(lecturerToken, "/api/v1/sections/{id}/enrollments", ApiTestFixtures.missingId()))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            getAs(adminToken, "/api/v1/sections/{id}/enrollments?status=BOGUS", section.getId()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName(
      "US-20/AC-4: rút khỏi lớp giảm sĩ số; ghi danh lại tăng lại; đổi cùng trạng thái là no-op")
  void withdrawAndReEnrollRecountsTheDenominator() throws Exception {
    Enrollment first = fixtures.enrollment(section, fixtures.student(), EnrollmentStatus.ENROLLED);
    fixtures.enrollment(section, fixtures.student(), EnrollmentStatus.ENROLLED);

    mockMvc
        .perform(
            putAs(
                lecturerToken,
                "/api/v1/enrollments/{id}/status",
                statusRequest(EnrollmentStatus.WITHDRAWN),
                first.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("WITHDRAWN"));
    assertThat(reloadSection().getEnrolledCount()).isEqualTo(1);

    // Đổi lại đúng trạng thái hiện tại: không lỗi, không đổi sĩ số
    mockMvc
        .perform(
            putAs(
                lecturerToken,
                "/api/v1/enrollments/{id}/status",
                statusRequest(EnrollmentStatus.WITHDRAWN),
                first.getId()))
        .andExpect(status().isOk());
    assertThat(reloadSection().getEnrolledCount()).isEqualTo(1);

    mockMvc
        .perform(
            putAs(
                adminToken,
                "/api/v1/enrollments/{id}/status",
                statusRequest(EnrollmentStatus.ENROLLED),
                first.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ENROLLED"));
    assertThat(reloadSection().getEnrolledCount()).isEqualTo(2);
  }

  @Test
  @DisplayName(
      "US-20 (khóa mẫu số): lớp CLOSED/LOCKED hoặc học kỳ khóa sổ điểm -> không đổi được ghi danh (422)")
  void frozenSectionOrTermRejectsStatusChange() throws Exception {
    Enrollment enrollment =
        fixtures.enrollment(section, fixtures.student(), EnrollmentStatus.ENROLLED);

    for (SectionStatus sectionStatus : List.of(SectionStatus.CLOSED, SectionStatus.LOCKED)) {
      section.setStatus(sectionStatus);
      section = mongo().save(section);
      mockMvc
          .perform(
              putAs(
                  lecturerToken,
                  "/api/v1/enrollments/{id}/status",
                  statusRequest(EnrollmentStatus.WITHDRAWN),
                  enrollment.getId()))
          .andExpect(status().isUnprocessableEntity())
          .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    }

    section.setStatus(SectionStatus.OPEN);
    section = mongo().save(section);
    for (TermStatus termStatus : List.of(TermStatus.SCORE_LOCKED, TermStatus.CLOSED)) {
      term.setStatus(termStatus);
      term = mongo().save(term);
      mockMvc
          .perform(
              putAs(
                  lecturerToken,
                  "/api/v1/enrollments/{id}/status",
                  statusRequest(EnrollmentStatus.WITHDRAWN),
                  enrollment.getId()))
          .andExpect(status().isUnprocessableEntity());
    }

    Enrollment stored = mongo().findById(enrollment.getId(), Enrollment.class);
    assertThat(stored).isNotNull();
    assertThat(stored.getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
  }

  @Test
  @DisplayName(
      "US-20 RBAC: giảng viên ngoài lớp không đổi được ghi danh; ID lạ/sai định dạng/thiếu body")
  void statusChangeAuthorizationAndValidation() throws Exception {
    Enrollment enrollment =
        fixtures.enrollment(section, fixtures.student(), EnrollmentStatus.ENROLLED);

    mockMvc
        .perform(
            putAs(
                otherLecturerToken,
                "/api/v1/enrollments/{id}/status",
                statusRequest(EnrollmentStatus.WITHDRAWN),
                enrollment.getId()))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            putAs(
                null,
                "/api/v1/enrollments/{id}/status",
                statusRequest(EnrollmentStatus.WITHDRAWN),
                enrollment.getId()))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            putAs(
                adminToken,
                "/api/v1/enrollments/{id}/status",
                statusRequest(EnrollmentStatus.WITHDRAWN),
                ApiTestFixtures.missingId()))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            putAs(
                adminToken,
                "/api/v1/enrollments/{id}/status",
                statusRequest(EnrollmentStatus.WITHDRAWN),
                "bad-id"))
        // Kiểm quyền (fail-closed) chạy trước validate @Pattern ở path nên ID rác bị 403, không
        // phải
        // 400; cùng hành vi với các controller khác (doc/tech-debt.md TD-22)
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            putAs(
                adminToken,
                "/api/v1/enrollments/{id}/status",
                EnrollmentStatusUpdateRequest.builder().build(),
                enrollment.getId()))
        .andExpect(status().isBadRequest());
  }
}
