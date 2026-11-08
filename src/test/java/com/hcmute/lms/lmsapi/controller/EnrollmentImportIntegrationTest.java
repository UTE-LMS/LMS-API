package com.hcmute.lms.lmsapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hcmute.lms.lmsapi.document.Course;
import com.hcmute.lms.lmsapi.document.CourseSection;
import com.hcmute.lms.lmsapi.document.Student;
import com.hcmute.lms.lmsapi.document.Term;
import com.hcmute.lms.lmsapi.document.User;
import com.hcmute.lms.lmsapi.document.UserScope;
import com.hcmute.lms.lmsapi.enums.EnrollmentStatus;
import com.hcmute.lms.lmsapi.enums.Role;
import com.hcmute.lms.lmsapi.enums.SectionStatus;
import com.hcmute.lms.lmsapi.enums.TermStatus;
import com.hcmute.lms.lmsapi.testsupport.ApiTestFixtures;
import com.hcmute.lms.lmsapi.testsupport.IntegrationTestBase;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

@DisplayName("F05a: Enrollment CSV Import Integration Tests (US-20)")
class EnrollmentImportIntegrationTest extends IntegrationTestBase {

  private Course course;
  private Term term;
  private CourseSection section;
  private String adminToken;
  private String lecturerToken;
  private String otherLecturerToken;

  @BeforeEach
  void setUpFixtures() {
    course = fixtures.course();
    term = fixtures.term();
    User lecturer = fixtures.user(Role.LECTURER, UserScope.builder().build());
    User otherLecturer = fixtures.user(Role.LECTURER, UserScope.builder().build());
    section = newSection(lecturer);

    adminToken = fixtures.bearerFor(Role.ADMIN, UserScope.builder().build());
    lecturerToken = fixtures.bearer(lecturer);
    otherLecturerToken = fixtures.bearer(otherLecturer);
  }

  private CourseSection newSection(User lecturer) {
    CourseSection created = fixtures.section(course.getId(), List.of(lecturer.getId()));
    created.setTermId(term.getId());
    return mongo().save(created);
  }

  private MockMultipartHttpServletRequestBuilder importAs(
      String bearer, String sectionId, String csv) {
    MockMultipartHttpServletRequestBuilder builder =
        multipart("/api/v1/sections/{id}/enrollments/import", sectionId)
            .file(
                new MockMultipartFile(
                    "file", "list.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)));
    return bearer == null ? builder : builder.header(HttpHeaders.AUTHORIZATION, bearer);
  }

  private long countEnrollments(String sectionId, EnrollmentStatus status) {
    return mongo()
        .count(
            Query.query(
                Criteria.where("sectionId")
                    .is(new org.bson.types.ObjectId(sectionId))
                    .and("status")
                    .is(status.name())),
            "enrollments");
  }

  private long countStudents(String... refs) {
    return mongo().count(Query.query(Criteria.where("studentRef").in((Object[]) refs)), "students");
  }

  private int enrolledCountOf(CourseSection target) {
    return Objects.requireNonNull(mongo().findById(target.getId(), CourseSection.class))
        .getEnrolledCount();
  }

  @Test
  @DisplayName(
      "US-20/AC-1 & AC-4: nhập CSV tạo sinh viên mới, ghi danh ENROLLED, sĩ số = số dòng hợp lệ")
  void us20_ac1_importCreatesStudentsAndEnrollments() throws Exception {
    String refA = fixtures.code("STU");
    String refB = fixtures.code("STU");
    String refC = fixtures.code("STU");

    mockMvc
        .perform(
            importAs(
                lecturerToken,
                section.getId(),
                "studentRef,cohort,majorCode\n"
                    + refA
                    + ",K22,FINTECH\n"
                    + refB
                    + ",K22,\n"
                    + refC
                    + "\n"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sectionId").value(section.getId()))
        .andExpect(jsonPath("$.totalRows").value(3))
        .andExpect(jsonPath("$.newStudents").value(3))
        .andExpect(jsonPath("$.reusedStudents").value(0))
        .andExpect(jsonPath("$.sectionEnrolledCount").value(3))
        .andExpect(jsonPath("$.ignoredColumns", hasSize(0)));

    assertThat(countEnrollments(section.getId(), EnrollmentStatus.ENROLLED)).isEqualTo(3);
    assertThat(enrolledCountOf(section)).isEqualTo(3);
    Student stored =
        mongo()
            .findOne(Query.query(Criteria.where("studentRef").is(refA)), Student.class, "students");
    assertThat(stored).isNotNull();
    assertThat(stored.getCohort()).isEqualTo("K22");
    assertThat(stored.getMajorCode()).isEqualTo("FINTECH");

    mockMvc
        .perform(getAs(lecturerToken, "/api/v1/sections/{id}/enrollments", section.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enrolled").value(3))
        .andExpect(jsonPath("$.enrollments", hasSize(3)));
  }

  @Test
  @DisplayName(
      "US-20/AC-1 (Q09): cột họ tên/email bị bỏ qua, không được lưu, được báo trong phản hồi")
  void us20_personalColumnsAreNotStored() throws Exception {
    String ref = fixtures.code("STU");

    mockMvc
        .perform(
            importAs(
                lecturerToken,
                section.getId(),
                "studentRef,fullName,email\n" + ref + ",Ten Gia,gia@example.invalid\n"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ignoredColumns[0]").value("fullName"))
        .andExpect(jsonPath("$.ignoredColumns[1]").value("email"));

    org.bson.Document raw =
        mongo()
            .findOne(
                Query.query(Criteria.where("studentRef").is(ref)),
                org.bson.Document.class,
                "students");
    assertThat(raw).isNotNull();
    assertThat(raw.containsKey("fullName")).isFalse();
    assertThat(raw.containsKey("email")).isFalse();
  }

  @Test
  @DisplayName(
      "US-20/AC-1: sinh viên đã có ở lớp khác được dùng lại, không tạo trùng (studentRef trùng giữa hai tệp)")
  void us20_ac1_existingStudentIsReusedAcrossSections() throws Exception {
    String shared = fixtures.code("STU");
    String onlyInSecond = fixtures.code("STU");
    User lecturer2 = fixtures.user(Role.LECTURER, UserScope.builder().build());
    CourseSection second = newSection(lecturer2);

    mockMvc
        .perform(importAs(lecturerToken, section.getId(), "studentRef\n" + shared + "\n"))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            importAs(
                fixtures.bearer(lecturer2),
                second.getId(),
                "studentRef\n" + shared + "\n" + onlyInSecond + "\n"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.newStudents").value(1))
        .andExpect(jsonPath("$.reusedStudents").value(1))
        .andExpect(jsonPath("$.sectionEnrolledCount").value(2));

    assertThat(countStudents(shared, onlyInSecond)).isEqualTo(2);
  }

  @Test
  @DisplayName(
      "US-20/AC-2: có dòng lỗi (thiếu, sai định dạng, trùng trong tệp) -> 422 theo dòng và không ghi gì")
  void us20_ac2_rowErrorsMeanNothingIsWritten() throws Exception {
    String good = fixtures.code("STU");
    String dup = fixtures.code("STU");

    mockMvc
        .perform(
            importAs(
                lecturerToken,
                section.getId(),
                "studentRef\n"
                    + good
                    + "\n"
                    + "\n"
                    + dup
                    + "\n"
                    + "BAD REF!\n"
                    + dup
                    + "\n"
                    + "\"\"\n"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
        .andExpect(jsonPath("$.fieldErrors", hasSize(3)))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("line 5"))
        .andExpect(jsonPath("$.fieldErrors[1].field").value("line 6"))
        .andExpect(
            jsonPath("$.fieldErrors[1].message").value("studentRef trùng với dòng 4 trong tệp"))
        .andExpect(jsonPath("$.fieldErrors[2].field").value("line 7"));

    // Dòng hợp lệ đứng trước cũng không được ghi (một thao tác thống nhất)
    assertThat(countStudents(good, dup)).isZero();
    assertThat(countEnrollments(section.getId(), EnrollmentStatus.ENROLLED)).isZero();
    assertThat(enrolledCountOf(section)).isZero();
  }

  @Test
  @DisplayName(
      "US-20/AC-2 & idempotent: nhập lại cùng tệp -> 422 'đã ghi danh', không nhân đôi sinh viên/ghi danh")
  void us20_ac2_reimportingTheSameFileIsRejectedWithoutDuplicates() throws Exception {
    String ref = fixtures.code("STU");
    String csv = "studentRef\n" + ref + "\n";

    mockMvc.perform(importAs(lecturerToken, section.getId(), csv)).andExpect(status().isOk());
    mockMvc
        .perform(importAs(lecturerToken, section.getId(), csv))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.fieldErrors[0].field").value("line 2"))
        .andExpect(jsonPath("$.fieldErrors[0].message").value("Sinh viên đã ghi danh lớp này"));

    assertThat(countStudents(ref)).isEqualTo(1);
    assertThat(countEnrollments(section.getId(), EnrollmentStatus.ENROLLED)).isEqualTo(1);
    assertThat(enrolledCountOf(section)).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "US-20/AC-2: sinh viên đã rút khỏi lớp xuất hiện lại trong tệp -> lỗi chỉ dẫn dùng API ghi danh lại")
  void us20_ac2_withdrawnStudentMustBeReEnrolledViaStatusApi() throws Exception {
    Student student = fixtures.student();
    fixtures.enrollment(section, student, EnrollmentStatus.WITHDRAWN);

    mockMvc
        .perform(
            importAs(
                lecturerToken, section.getId(), "studentRef\n" + student.getStudentRef() + "\n"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(
            jsonPath("$.fieldErrors[0].message")
                .value(org.hamcrest.Matchers.containsString("ghi danh lại")));
  }

  @Test
  @DisplayName("US-20/AC-3: tệp rỗng, sai cột, không có dòng dữ liệu, thiếu phần tệp -> 400")
  void us20_ac3_malformedFilesAreRejected() throws Exception {
    for (String csv : List.of("", "cohort\nK22\n", "studentRef\n")) {
      mockMvc
          .perform(importAs(lecturerToken, section.getId(), csv))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // Không gửi phần "file" của multipart
    mockMvc
        .perform(
            multipart("/api/v1/sections/{id}/enrollments/import", section.getId())
                .header(HttpHeaders.AUTHORIZATION, lecturerToken))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  @Test
  @DisplayName("US-20 RBAC: giảng viên ngoài lớp -> 403; chưa đăng nhập -> 401; ADMIN nhập được")
  void us20_rbac() throws Exception {
    String csv = "studentRef\n" + fixtures.code("STU") + "\n";

    mockMvc
        .perform(importAs(otherLecturerToken, section.getId(), csv))
        .andExpect(status().isForbidden());
    mockMvc.perform(importAs(null, section.getId(), csv)).andExpect(status().isUnauthorized());
    mockMvc
        .perform(importAs(lecturerToken, ApiTestFixtures.missingId(), csv))
        .andExpect(status().isForbidden());
    // PROGRAM_MANAGER chỉ xem, không nhập danh sách lớp
    mockMvc
        .perform(
            importAs(
                fixtures.bearerFor(
                    Role.PROGRAM_MANAGER,
                    UserScope.builder()
                        .sectionIds(new java.util.ArrayList<>(List.of(section.getId())))
                        .build()),
                section.getId(),
                csv))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(importAs(adminToken, section.getId(), csv))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalRows").value(1));
  }

  @Test
  @DisplayName(
      "US-20 (khóa mẫu số): lớp CLOSED/LOCKED hoặc học kỳ khóa sổ điểm -> 422 và không ghi gì")
  void us20_frozenSectionOrTermRejectsImport() throws Exception {
    String ref = fixtures.code("STU");
    String csv = "studentRef\n" + ref + "\n";

    for (SectionStatus frozen : List.of(SectionStatus.CLOSED, SectionStatus.LOCKED)) {
      section.setStatus(frozen);
      section = mongo().save(section);
      mockMvc
          .perform(importAs(lecturerToken, section.getId(), csv))
          .andExpect(status().isUnprocessableEntity());
    }
    section.setStatus(SectionStatus.OPEN);
    section = mongo().save(section);
    term.setStatus(TermStatus.SCORE_LOCKED);
    term = mongo().save(term);
    mockMvc
        .perform(importAs(lecturerToken, section.getId(), csv))
        .andExpect(status().isUnprocessableEntity());

    assertThat(countStudents(ref)).isZero();
    assertThat(enrolledCountOf(section)).isZero();
  }

  @Test
  @DisplayName(
      "US-20/AC-4: sau khi nhập 5 người rồi cho 1 người rút, sĩ số còn 4 và tệp nhập mới tính tiếp từ 4")
  void us20_ac4_denominatorFollowsWithdrawals() throws Exception {
    StringBuilder csv = new StringBuilder("studentRef\n");
    for (int i = 0; i < 5; i++) {
      csv.append(fixtures.code("STU")).append('\n');
    }
    mockMvc
        .perform(importAs(lecturerToken, section.getId(), csv.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sectionEnrolledCount").value(5));

    org.bson.Document firstEnrollment =
        Objects.requireNonNull(
            mongo()
                .findOne(
                    Query.query(
                        Criteria.where("sectionId")
                            .is(new org.bson.types.ObjectId(section.getId()))),
                    org.bson.Document.class,
                    "enrollments"));
    String enrollmentId = firstEnrollment.getObjectId("_id").toHexString();
    mockMvc
        .perform(
            putAs(
                lecturerToken,
                "/api/v1/enrollments/{id}/status",
                com.hcmute.lms.lmsapi.dto.EnrollmentStatusUpdateRequest.builder()
                    .status(EnrollmentStatus.WITHDRAWN)
                    .build(),
                enrollmentId))
        .andExpect(status().isOk());
    assertThat(enrolledCountOf(section)).isEqualTo(4);

    mockMvc
        .perform(
            importAs(lecturerToken, section.getId(), "studentRef\n" + fixtures.code("STU") + "\n"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sectionEnrolledCount").value(5));
  }
}
