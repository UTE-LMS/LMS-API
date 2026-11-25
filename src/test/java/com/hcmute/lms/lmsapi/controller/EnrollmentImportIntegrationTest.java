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
    User lecturer = fixtures.user(Role.LECTURER);
    User otherLecturer = fixtures.user(Role.LECTURER);
    section = newSection(lecturer);

    adminToken = fixtures.bearerFor(Role.ADMIN);
    lecturerToken = fixtures.bearer(lecturer);
    otherLecturerToken = fixtures.bearer(otherLecturer);
  }

  private CourseSection newSection(User lecturer) {
    CourseSection created = fixtures.section(course.getId(), List.of(lecturer.getId()));
    created.setTermId(term.getId());
    return mongo().save(created);
  }

  private Student seedStudent(String cohort) {
    Student student = fixtures.student();
    if (cohort != null) {
      student.setCohort(cohort);
      student = mongo().save(student);
    }
    return student;
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
    return mongo()
        .count(Query.query(Criteria.where("studentCode").in((Object[]) refs)), "students");
  }

  private int enrolledCountOf(CourseSection target) {
    return Objects.requireNonNull(mongo().findById(target.getId(), CourseSection.class))
        .getEnrolledCount();
  }

  @Test
  @DisplayName(
      "US-20/AC-1 & AC-4: nhập CSV ghi danh ENROLLED sinh viên đã có, không tạo mới, sĩ số = số dòng hợp lệ")
  void us20_ac1_importEnrollsExistingStudents() throws Exception {
    String refA = seedStudent("K22").getStudentCode();
    String refB = seedStudent("K22").getStudentCode();
    String refC = seedStudent(null).getStudentCode();

    mockMvc
        .perform(
            importAs(
                lecturerToken,
                section.getId(),
                "studentCode,cohort\n" + refA + ",K22\n" + refB + ",K22\n" + refC + "\n"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sectionId").value(section.getId()))
        .andExpect(jsonPath("$.totalRows").value(3))
        .andExpect(jsonPath("$.newStudents").value(0))
        .andExpect(jsonPath("$.reusedStudents").value(3))
        .andExpect(jsonPath("$.sectionEnrolledCount").value(3))
        .andExpect(jsonPath("$.ignoredColumns", hasSize(0)));

    assertThat(countEnrollments(section.getId(), EnrollmentStatus.ENROLLED)).isEqualTo(3);
    assertThat(enrolledCountOf(section)).isEqualTo(3);
    Student stored =
        mongo()
            .findOne(
                Query.query(Criteria.where("studentCode").is(refA)), Student.class, "students");
    assertThat(stored).isNotNull();
    assertThat(stored.getCohort()).isEqualTo("K22");

    mockMvc
        .perform(getAs(lecturerToken, "/api/v1/sections/{id}/enrollments", section.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enrolled").value(3))
        .andExpect(jsonPath("$.enrollments", hasSize(3)));
  }

  @Test
  @DisplayName(
      "US-20/AC-1 (Q09): cột họ tên/email trong tệp ghi danh bị bỏ qua, không ghi đè dữ liệu sinh viên")
  void us20_personalColumnsAreNotStored() throws Exception {
    String ref = seedStudent("K22").getStudentCode();

    mockMvc
        .perform(
            importAs(
                lecturerToken,
                section.getId(),
                "studentCode,fullName,email\n" + ref + ",Ten Gia,gia@example.invalid\n"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ignoredColumns[0]").value("fullName"))
        .andExpect(jsonPath("$.ignoredColumns[1]").value("email"));

    org.bson.Document raw =
        mongo()
            .findOne(
                Query.query(Criteria.where("studentCode").is(ref)),
                org.bson.Document.class,
                "students");
    assertThat(raw).isNotNull();
    assertThat(raw.getString("fullName")).isEqualTo("Sinh viên " + ref);
    assertThat(raw.containsKey("email")).isFalse();
  }

  @Test
  @DisplayName("US-20/AC-1: cùng sinh viên đã có trong hệ thống ghi danh được vào nhiều lớp")
  void us20_ac1_existingStudentIsReusedAcrossSections() throws Exception {
    String shared = seedStudent("K22").getStudentCode();
    String onlyInSecond = seedStudent("K22").getStudentCode();
    User lecturer2 = fixtures.user(Role.LECTURER);
    CourseSection second = newSection(lecturer2);

    mockMvc
        .perform(importAs(lecturerToken, section.getId(), "studentCode\n" + shared + "\n"))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            importAs(
                fixtures.bearer(lecturer2),
                second.getId(),
                "studentCode\n" + shared + "\n" + onlyInSecond + "\n"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.newStudents").value(0))
        .andExpect(jsonPath("$.reusedStudents").value(2))
        .andExpect(jsonPath("$.sectionEnrolledCount").value(2));

    assertThat(countStudents(shared, onlyInSecond)).isEqualTo(2);
  }

  @Test
  @DisplayName(
      "US-20/AC-2: mã sinh viên chưa tồn tại trong hệ thống -> 422 và không tạo mới hay ghi danh")
  void us20_ac2_unknownStudentCodeIsRejectedAndNotCreated() throws Exception {
    String existingRef = seedStudent("K23").getStudentCode();
    String missingRef = fixtures.code("STU");

    mockMvc
        .perform(
            importAs(
                lecturerToken,
                section.getId(),
                "studentCode,cohort\n" + existingRef + ",K23\n" + missingRef + ",K23\n"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
        .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("line 3"))
        .andExpect(
            jsonPath("$.fieldErrors[0].message")
                .value(org.hamcrest.Matchers.containsString("không tồn tại")));

    assertThat(countStudents(missingRef)).isZero();
    assertThat(countEnrollments(section.getId(), EnrollmentStatus.ENROLLED)).isZero();
    assertThat(enrolledCountOf(section)).isZero();
  }

  @Test
  @DisplayName(
      "US-20/AC-2: có dòng lỗi (thiếu, sai định dạng, trùng trong tệp) -> 422 theo dòng và không ghi gì")
  void us20_ac2_rowErrorsMeanNothingIsWritten() throws Exception {
    String good = seedStudent("K22").getStudentCode();
    String dup = seedStudent("K22").getStudentCode();

    mockMvc
        .perform(
            importAs(
                lecturerToken,
                section.getId(),
                "studentCode\n"
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
            jsonPath("$.fieldErrors[1].message").value("studentCode trùng với dòng 4 trong tệp"))
        .andExpect(jsonPath("$.fieldErrors[2].field").value("line 7"));

    // Dòng hợp lệ đứng trước cũng không được ghi (một thao tác thống nhất)
    assertThat(countEnrollments(section.getId(), EnrollmentStatus.ENROLLED)).isZero();
    assertThat(enrolledCountOf(section)).isZero();
  }

  @Test
  @DisplayName(
      "US-20/AC-2 & idempotent: nhập lại cùng tệp -> 422 'đã ghi danh', không nhân đôi sinh viên/ghi danh")
  void us20_ac2_reimportingTheSameFileIsRejectedWithoutDuplicates() throws Exception {
    String ref = seedStudent("K22").getStudentCode();
    String csv = "studentCode\n" + ref + "\n";

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
                lecturerToken, section.getId(), "studentCode\n" + student.getStudentCode() + "\n"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(
            jsonPath("$.fieldErrors[0].message")
                .value(org.hamcrest.Matchers.containsString("ghi danh lại")));
  }

  @Test
  @DisplayName("US-20/AC-3: tệp rỗng, sai cột, không có dòng dữ liệu, thiếu phần tệp -> 400")
  void us20_ac3_malformedFilesAreRejected() throws Exception {
    for (String csv : List.of("", "cohort\nK22\n", "studentCode\n")) {
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
    String csv = "studentCode\n" + seedStudent("K23").getStudentCode() + "\n";

    mockMvc
        .perform(importAs(otherLecturerToken, section.getId(), csv))
        .andExpect(status().isForbidden());
    mockMvc.perform(importAs(null, section.getId(), csv)).andExpect(status().isUnauthorized());
    mockMvc
        .perform(importAs(lecturerToken, ApiTestFixtures.missingId(), csv))
        .andExpect(status().isForbidden());
    // PROGRAM_MANAGER chỉ xem, không nhập danh sách lớp
    mockMvc
        .perform(importAs(fixtures.bearerFor(Role.PROGRAM_MANAGER), section.getId(), csv))
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
    String ref = seedStudent("K23").getStudentCode();
    String csv = "studentCode\n" + ref + "\n";

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

    assertThat(countEnrollments(section.getId(), EnrollmentStatus.ENROLLED)).isZero();
    assertThat(enrolledCountOf(section)).isZero();
  }

  @Test
  @DisplayName(
      "US-20/AC-4: sau khi nhập 5 người rồi cho 1 người rút, sĩ số còn 4 và tệp nhập mới tính tiếp từ 4")
  void us20_ac4_denominatorFollowsWithdrawals() throws Exception {
    StringBuilder csv = new StringBuilder("studentCode\n");
    for (int i = 0; i < 5; i++) {
      csv.append(seedStudent("K23").getStudentCode()).append('\n');
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
            importAs(
                lecturerToken,
                section.getId(),
                "studentCode\n" + seedStudent("K23").getStudentCode() + "\n"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sectionEnrolledCount").value(5));
  }

  @Test
  @DisplayName("Tải template và import ghi danh nhiều lớp: từ chối sinh viên chưa tồn tại")
  void bulkEnrollmentTemplateAndImport() throws Exception {
    mockMvc
        .perform(
            getAs(
                lecturerToken,
                "/api/v1/sections/{id}/enrollments/import-template",
                section.getId()))
        .andExpect(status().isOk())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .string(org.hamcrest.Matchers.containsString("studentCode,cohort")));

    mockMvc
        .perform(getAs(lecturerToken, "/api/v1/enrollments/import-template"))
        .andExpect(status().isOk())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .string(org.hamcrest.Matchers.containsString("sectionCode,studentCode,cohort")));

    String mssv1 = seedStudent("K23").getStudentCode();
    String mssv2 = seedStudent("K23").getStudentCode();
    String missingMssv = fixtures.code("MSSV_MISS");
    var file =
        new org.springframework.mock.web.MockMultipartFile(
            "file",
            "bulk.csv",
            "text/csv",
            ("sectionCode,studentCode,cohort\n"
                    + section.getCode()
                    + ","
                    + mssv1
                    + ",K23\n"
                    + section.getCode()
                    + ","
                    + mssv2
                    + ",K23\n"
                    + section.getCode()
                    + ","
                    + missingMssv
                    + ",K23\n"
                    + "MISSING_SEC,"
                    + mssv2
                    + ",K23\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(
                    "/api/v1/enrollments/import")
                .file(file)
                .header(org.springframework.http.HttpHeaders.AUTHORIZATION, lecturerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalRows").value(4))
        .andExpect(jsonPath("$.successCount").value(2))
        .andExpect(jsonPath("$.failureCount").value(2))
        .andExpect(
            jsonPath("$.errors[0].message")
                .value(org.hamcrest.Matchers.containsString("không tồn tại")));
    assertThat(enrolledCountOf(section)).isEqualTo(2);
    assertThat(countStudents(missingMssv)).isZero();
  }

  @Test
  @DisplayName(
      "Luồng E2E: ADMIN import sinh viên trước, sau đó giảng viên ghi danh vào lớp thành công")
  void endToEnd_adminImportsStudentThenLecturerEnrolls() throws Exception {
    String studentCode = fixtures.code("STU");
    MockMultipartFile studentFile =
        new MockMultipartFile(
            "file",
            "students.csv",
            "text/csv",
            ("studentCode,fullName,cohort\n" + studentCode + ",Sinh Viên Mới,K24\n")
                .getBytes(StandardCharsets.UTF_8));

    mockMvc
        .perform(
            multipart("/api/v1/students/import")
                .file(studentFile)
                .header(HttpHeaders.AUTHORIZATION, adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.successCount").value(1));

    mockMvc
        .perform(importAs(lecturerToken, section.getId(), "studentCode\n" + studentCode + "\n"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalRows").value(1))
        .andExpect(jsonPath("$.newStudents").value(0))
        .andExpect(jsonPath("$.reusedStudents").value(1))
        .andExpect(jsonPath("$.sectionEnrolledCount").value(1));
  }

  @Test
  @DisplayName(
      "Chặn ghi danh sinh viên không ở trạng thái ACTIVE (qua import 1 lớp, nhiều lớp và ghi danh lại)")
  void nonActiveStudentsAreRejectedFromEnrollment() throws Exception {
    Student suspended = seedStudent("K23");
    Student graduated = seedStudent("K22");

    mockMvc
        .perform(
            putAs(
                adminToken,
                "/api/v1/students/{id}/status",
                com.hcmute.lms.lmsapi.dto.StudentStatusUpdateRequest.builder()
                    .status(com.hcmute.lms.lmsapi.enums.StudentStatus.SUSPENDED)
                    .build(),
                suspended.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUSPENDED"));

    MockMultipartFile statusCsv =
        new MockMultipartFile(
            "file",
            "status.csv",
            "text/csv",
            ("studentCode,status\n" + graduated.getStudentCode() + ",GRADUATED\n")
                .getBytes(StandardCharsets.UTF_8));
    mockMvc
        .perform(
            multipart("/api/v1/students/status-import")
                .file(statusCsv)
                .header(HttpHeaders.AUTHORIZATION, adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.successCount").value(1));

    // 1. Import 1 lớp phải trả 422 và không ghi danh
    mockMvc
        .perform(
            importAs(
                lecturerToken,
                section.getId(),
                "studentCode\n" + suspended.getStudentCode() + "\n"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.fieldErrors[0].field").value("line 2"))
        .andExpect(
            jsonPath("$.fieldErrors[0].message")
                .value(org.hamcrest.Matchers.containsString("ACTIVE")));
    assertThat(enrolledCountOf(section)).isZero();

    // 2. Import nhiều lớp báo lỗi dòng không ACTIVE
    MockMultipartFile bulkCsv =
        new MockMultipartFile(
            "file",
            "bulk.csv",
            "text/csv",
            ("sectionCode,studentCode,cohort\n"
                    + section.getCode()
                    + ","
                    + graduated.getStudentCode()
                    + ",K22\n")
                .getBytes(StandardCharsets.UTF_8));
    mockMvc
        .perform(
            multipart("/api/v1/enrollments/import")
                .file(bulkCsv)
                .header(HttpHeaders.AUTHORIZATION, lecturerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.successCount").value(0))
        .andExpect(jsonPath("$.failureCount").value(1))
        .andExpect(
            jsonPath("$.errors[0].message").value(org.hamcrest.Matchers.containsString("ACTIVE")));
    assertThat(enrolledCountOf(section)).isZero();

    // 3. Sinh viên đã rút lớp (WITHDRAWN) sau đó đổi sang DROPPED_OUT -> không thể ghi danh lại
    Student dropped = seedStudent("K23");
    var withdrawn = fixtures.enrollment(section, dropped, EnrollmentStatus.WITHDRAWN);
    mockMvc
        .perform(
            putAs(
                adminToken,
                "/api/v1/students/{id}/status",
                com.hcmute.lms.lmsapi.dto.StudentStatusUpdateRequest.builder()
                    .status(com.hcmute.lms.lmsapi.enums.StudentStatus.DROPPED_OUT)
                    .build(),
                dropped.getId()))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            putAs(
                lecturerToken,
                "/api/v1/enrollments/{id}/status",
                com.hcmute.lms.lmsapi.dto.EnrollmentStatusUpdateRequest.builder()
                    .status(EnrollmentStatus.ENROLLED)
                    .build(),
                withdrawn.getId()))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
        .andExpect(
            jsonPath("$.message").value(org.hamcrest.Matchers.containsString("DROPPED_OUT")));
  }
}
