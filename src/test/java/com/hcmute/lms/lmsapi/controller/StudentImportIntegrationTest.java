package com.hcmute.lms.lmsapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hcmute.lms.lmsapi.document.Student;
import com.hcmute.lms.lmsapi.dto.StudentBatchStatusUpdateRequest;
import com.hcmute.lms.lmsapi.dto.StudentStatusUpdateRequest;
import com.hcmute.lms.lmsapi.enums.Role;
import com.hcmute.lms.lmsapi.enums.StudentStatus;
import com.hcmute.lms.lmsapi.repository.StudentRepository;
import com.hcmute.lms.lmsapi.testsupport.ApiTestFixtures;
import com.hcmute.lms.lmsapi.testsupport.IntegrationTestBase;
import com.hcmute.lms.lmsapi.util.XlsxCsvConverter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

@DisplayName("Student Management, Status Batch Update & CSV/XLSX Import Integration Tests (ADMIN)")
class StudentImportIntegrationTest extends IntegrationTestBase {

  @Autowired private StudentRepository studentRepository;

  private String adminToken;
  private String pmToken;
  private String lecturerToken;
  private String qaToken;

  @BeforeEach
  void setUp() {
    adminToken = fixtures.bearerFor(Role.ADMIN);
    pmToken = fixtures.bearerFor(Role.PROGRAM_MANAGER);
    lecturerToken = fixtures.bearerFor(Role.LECTURER);
    qaToken = fixtures.bearerFor(Role.QA);
  }

  @AfterEach
  void cleanImportedStudents() {
    mongo().remove(Query.query(Criteria.where("studentCode").regex("^ITEST_STU_")), Student.class);
  }

  private MockMultipartFile csvPart(String content) {
    return new MockMultipartFile(
        "file", "students.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  @DisplayName(
      "RBAC: Chỉ ADMIN được tải template và import sinh viên; vai trò khác 403; chưa đăng nhập 401")
  void rbac_OnlyAdminAllowed() throws Exception {
    MockMultipartFile file = csvPart("studentCode,fullName,cohort\nITEST_STU_01,SV 1,K23\n");

    mockMvc
        .perform(getAs(null, "/api/v1/students/import-template"))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(multipart("/api/v1/students/import").file(file))
        .andExpect(status().isUnauthorized());

    for (String token : List.of(pmToken, lecturerToken, qaToken)) {
      mockMvc
          .perform(getAs(token, "/api/v1/students/import-template"))
          .andExpect(status().isForbidden());
      mockMvc
          .perform(
              multipart("/api/v1/students/import")
                  .file(file)
                  .header(HttpHeaders.AUTHORIZATION, token))
          .andExpect(status().isForbidden());
    }
  }

  @Test
  @DisplayName("ADMIN tải được template CSV và XLSX không chứa cột status")
  void adminDownloadTemplates_CsvAndXlsx() throws Exception {
    mockMvc
        .perform(getAs(adminToken, "/api/v1/students/import-template"))
        .andExpect(status().isOk())
        .andExpect(
            header()
                .string(HttpHeaders.CONTENT_DISPOSITION, containsString("students-template.csv")))
        .andExpect(content().string(containsString("studentCode,fullName,cohort")))
        .andExpect(content().string(not(containsString("status"))));

    MvcResult xlsxResult =
        mockMvc
            .perform(getAs(adminToken, "/api/v1/students/import-template?format=xlsx"))
            .andExpect(status().isOk())
            .andExpect(
                header()
                    .string(
                        HttpHeaders.CONTENT_DISPOSITION, containsString("students-template.xlsx")))
            .andReturn();

    String convertedCsv =
        new String(
            XlsxCsvConverter.toCsv(xlsxResult.getResponse().getContentAsByteArray()),
            StandardCharsets.UTF_8);
    assertThat(convertedCsv).contains("studentCode,fullName,cohort");
  }

  @Test
  @DisplayName(
      "ADMIN import CSV best-effort: lưu sinh viên đủ studentCode/fullName/cohort với ACTIVE, báo lỗi dòng thiếu/trùng/sai mẫu")
  void adminImportStudents_CsvBestEffort() throws Exception {
    mongo()
        .save(
            Student.builder()
                .studentCode("ITEST_STU_EXISTS")
                .fullName("Sinh Viên Tồn Tại")
                .cohort("K22")
                .build());

    String csv =
        "studentCode,fullName,cohort\n"
            + "ITEST_STU_01,Nguyễn Văn An,K23\n"
            + "ITEST_STU_02,,K23\n"
            + "ITEST_STU_03,Thiếu Khóa,\n"
            + ",Thiếu mã,K23\n"
            + "BAD CODE,Sai định dạng,K23\n"
            + "ITEST_STU_01,Trùng trong tệp,K23\n"
            + "ITEST_STU_EXISTS,Trùng DB,K23\n";

    mockMvc
        .perform(
            multipart("/api/v1/students/import")
                .file(csvPart(csv))
                .header(HttpHeaders.AUTHORIZATION, adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalRows").value(7))
        .andExpect(jsonPath("$.successCount").value(1))
        .andExpect(jsonPath("$.failureCount").value(6));

    Student s1 = studentRepository.findByStudentCode("ITEST_STU_01").orElseThrow();
    assertThat(s1.getFullName()).isEqualTo("Nguyễn Văn An");
    assertThat(s1.getCohort()).isEqualTo("K23");
    assertThat(s1.getStatus()).isEqualTo(StudentStatus.ACTIVE);
    assertThat(studentRepository.findByStudentCode("ITEST_STU_02")).isEmpty();
    assertThat(studentRepository.findByStudentCode("ITEST_STU_03")).isEmpty();
  }

  @Test
  @DisplayName("ADMIN import được danh sách sinh viên từ tệp Excel (.xlsx)")
  void adminImportStudents_Xlsx() throws Exception {
    byte[] xlsx =
        XlsxCsvConverter.fromCsv(
            "studentCode,fullName,cohort\nITEST_STU_XLSX1,Trần Văn Excel,K24\n"
                .getBytes(StandardCharsets.UTF_8));
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "students.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            xlsx);

    mockMvc
        .perform(
            multipart("/api/v1/students/import")
                .file(file)
                .header(HttpHeaders.AUTHORIZATION, adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalRows").value(1))
        .andExpect(jsonPath("$.successCount").value(1))
        .andExpect(jsonPath("$.failureCount").value(0));

    Student saved = studentRepository.findByStudentCode("ITEST_STU_XLSX1").orElseThrow();
    assertThat(saved.getFullName()).isEqualTo("Trần Văn Excel");
    assertThat(saved.getCohort()).isEqualTo("K24");
    assertThat(saved.getStatus()).isEqualTo(StudentStatus.ACTIVE);
  }

  @Test
  @DisplayName("RBAC: Chỉ ADMIN được xem danh sách và đổi trạng thái sinh viên (API JSON + import)")
  void rbac_StudentListingAndStatusEndpoints_OnlyAdminAllowed() throws Exception {
    MockMultipartFile statusFile = csvPart("studentCode,status\nITEST_STU_01,GRADUATED\n");
    String missingId = ApiTestFixtures.missingId();
    StudentStatusUpdateRequest singleReq =
        StudentStatusUpdateRequest.builder().status(StudentStatus.SUSPENDED).build();
    StudentBatchStatusUpdateRequest batchReq =
        StudentBatchStatusUpdateRequest.builder()
            .studentIds(List.of(missingId))
            .status(StudentStatus.GRADUATED)
            .build();

    mockMvc.perform(getAs(null, "/api/v1/students")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(putAs(null, "/api/v1/students/{id}/status", singleReq, missingId))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(putAs(null, "/api/v1/students/status", batchReq))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(getAs(null, "/api/v1/students/status-import-template"))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(multipart("/api/v1/students/status-import").file(statusFile))
        .andExpect(status().isUnauthorized());

    for (String token : List.of(pmToken, lecturerToken, qaToken)) {
      mockMvc.perform(getAs(token, "/api/v1/students")).andExpect(status().isForbidden());
      mockMvc
          .perform(putAs(token, "/api/v1/students/{id}/status", singleReq, missingId))
          .andExpect(status().isForbidden());
      mockMvc
          .perform(putAs(token, "/api/v1/students/status", batchReq))
          .andExpect(status().isForbidden());
      mockMvc
          .perform(getAs(token, "/api/v1/students/status-import-template"))
          .andExpect(status().isForbidden());
      mockMvc
          .perform(
              multipart("/api/v1/students/status-import")
                  .file(statusFile)
                  .header(HttpHeaders.AUTHORIZATION, token))
          .andExpect(status().isForbidden());
    }
  }

  @Test
  @DisplayName("ADMIN lọc danh sách sinh viên và cập nhật trạng thái đơn / hàng loạt (JSON API)")
  void adminSearchAndBatchStatusUpdate() throws Exception {
    Student s1 =
        mongo()
            .save(
                Student.builder()
                    .studentCode("ITEST_STU_L01")
                    .fullName("Nguyễn Bình An")
                    .cohort("K23")
                    .status(StudentStatus.ACTIVE)
                    .build());
    Student s2 =
        mongo()
            .save(
                Student.builder()
                    .studentCode("ITEST_STU_L02")
                    .fullName("Trần Thu Hà")
                    .cohort("K23")
                    .status(StudentStatus.ACTIVE)
                    .build());
    mongo()
        .save(
            Student.builder()
                .studentCode("ITEST_STU_L03")
                .fullName("Lê Hoàng")
                .cohort("K22")
                .status(StudentStatus.GRADUATED)
                .build());

    mockMvc
        .perform(getAs(adminToken, "/api/v1/students?search=Bình&cohort=K23&status=ACTIVE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].studentCode").value("ITEST_STU_L01"));

    mockMvc
        .perform(
            putAs(
                adminToken,
                "/api/v1/students/{id}/status",
                StudentStatusUpdateRequest.builder().status(StudentStatus.SUSPENDED).build(),
                s1.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUSPENDED"));

    mockMvc
        .perform(
            putAs(
                adminToken,
                "/api/v1/students/status",
                StudentBatchStatusUpdateRequest.builder()
                    .studentIds(List.of(s1.getId(), s2.getId()))
                    .status(StudentStatus.GRADUATED)
                    .build()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requestedCount").value(2))
        .andExpect(jsonPath("$.updatedCount").value(2))
        .andExpect(jsonPath("$.status").value("GRADUATED"));

    assertThat(studentRepository.findById(s1.getId()).orElseThrow().getStatus())
        .isEqualTo(StudentStatus.GRADUATED);
    assertThat(studentRepository.findById(s2.getId()).orElseThrow().getStatus())
        .isEqualTo(StudentStatus.GRADUATED);

    // Nếu danh sách batch có ID không tồn tại -> 404 và không cập nhật sinh viên nào
    mockMvc
        .perform(
            putAs(
                adminToken,
                "/api/v1/students/status",
                StudentBatchStatusUpdateRequest.builder()
                    .studentIds(List.of(s1.getId(), ApiTestFixtures.missingId()))
                    .status(StudentStatus.DROPPED_OUT)
                    .build()))
        .andExpect(status().isNotFound());
    assertThat(studentRepository.findById(s1.getId()).orElseThrow().getStatus())
        .isEqualTo(StudentStatus.GRADUATED);
  }

  @Test
  @DisplayName("ADMIN tải template và import CSV/XLSX cập nhật trạng thái sinh viên hàng loạt")
  void adminStatusImport_TemplateCsvAndXlsx() throws Exception {
    mongo()
        .save(
            Student.builder()
                .studentCode("ITEST_STU_ST1")
                .fullName("Sinh viên Status 1")
                .cohort("K22")
                .status(StudentStatus.ACTIVE)
                .build());
    mongo()
        .save(
            Student.builder()
                .studentCode("ITEST_STU_ST2")
                .fullName("Sinh viên Status 2")
                .cohort("K23")
                .status(StudentStatus.ACTIVE)
                .build());

    mockMvc
        .perform(getAs(adminToken, "/api/v1/students/status-import-template"))
        .andExpect(status().isOk())
        .andExpect(
            header()
                .string(
                    HttpHeaders.CONTENT_DISPOSITION,
                    containsString("students-status-template.csv")))
        .andExpect(content().string(containsString("studentCode,status")));

    MvcResult xlsxTemplate =
        mockMvc
            .perform(getAs(adminToken, "/api/v1/students/status-import-template?format=xlsx"))
            .andExpect(status().isOk())
            .andExpect(
                header()
                    .string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        containsString("students-status-template.xlsx")))
            .andReturn();
    assertThat(
            new String(
                XlsxCsvConverter.toCsv(xlsxTemplate.getResponse().getContentAsByteArray()),
                StandardCharsets.UTF_8))
        .contains("studentCode,status");

    String csv =
        "studentCode,status\n"
            + "ITEST_STU_ST1,GRADUATED\n"
            + "ITEST_STU_MISSING,SUSPENDED\n"
            + "ITEST_STU_ST2,INVALID\n";
    mockMvc
        .perform(
            multipart("/api/v1/students/status-import")
                .file(csvPart(csv))
                .header(HttpHeaders.AUTHORIZATION, adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalRows").value(3))
        .andExpect(jsonPath("$.successCount").value(1))
        .andExpect(jsonPath("$.failureCount").value(2));

    assertThat(studentRepository.findByStudentCode("ITEST_STU_ST1").orElseThrow().getStatus())
        .isEqualTo(StudentStatus.GRADUATED);
    assertThat(studentRepository.findByStudentCode("ITEST_STU_ST2").orElseThrow().getStatus())
        .isEqualTo(StudentStatus.ACTIVE);

    byte[] xlsx =
        XlsxCsvConverter.fromCsv(
            "studentCode,status\nITEST_STU_ST2,SUSPENDED\n".getBytes(StandardCharsets.UTF_8));
    MockMultipartFile xlsxPart =
        new MockMultipartFile(
            "file",
            "status.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            xlsx);
    mockMvc
        .perform(
            multipart("/api/v1/students/status-import")
                .file(xlsxPart)
                .header(HttpHeaders.AUTHORIZATION, adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.successCount").value(1))
        .andExpect(jsonPath("$.failureCount").value(0));

    assertThat(studentRepository.findByStudentCode("ITEST_STU_ST2").orElseThrow().getStatus())
        .isEqualTo(StudentStatus.SUSPENDED);
  }
}
