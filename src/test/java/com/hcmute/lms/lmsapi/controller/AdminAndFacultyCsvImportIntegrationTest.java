package com.hcmute.lms.lmsapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hcmute.lms.lmsapi.document.Course;
import com.hcmute.lms.lmsapi.document.Faculty;
import com.hcmute.lms.lmsapi.document.User;
import com.hcmute.lms.lmsapi.enums.Role;
import com.hcmute.lms.lmsapi.repository.CourseRepository;
import com.hcmute.lms.lmsapi.repository.FacultyRepository;
import com.hcmute.lms.lmsapi.repository.UserRepository;
import com.hcmute.lms.lmsapi.testsupport.IntegrationTestBase;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;

@DisplayName("Batch CSV Import: Faculties, Users, and Courses with Faculty Assignment")
class AdminAndFacultyCsvImportIntegrationTest extends IntegrationTestBase {

  @Autowired private FacultyRepository facultyRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private CourseRepository courseRepository;

  private String adminToken;
  private String pmToken;
  private Faculty pmFaculty;
  private Faculty otherFaculty;

  @BeforeEach
  void setUp() {
    adminToken = fixtures.bearerFor(Role.ADMIN);
    pmFaculty = fixtures.faculty();
    otherFaculty = fixtures.faculty();

    User pmUser = fixtures.user(Role.PROGRAM_MANAGER);
    pmUser.setFacultyId(pmFaculty.getId());
    pmUser = mongo().save(pmUser);
    pmToken = fixtures.bearer(pmUser);
  }

  @AfterEach
  void cleanImportedRecords() {
    mongo().remove(Query.query(Criteria.where("code").regex("^ITEST_IMP_")), Faculty.class);
    mongo().remove(Query.query(Criteria.where("username").regex("^itest_imp_")), User.class);
    mongo().remove(Query.query(Criteria.where("code").regex("^ITEST_C_")), Course.class);
  }

  @Test
  @DisplayName("ADMIN import Khoa (template + CSV best-effort); PM bị 403")
  void adminImportFaculties_SuccessAndPmForbidden() throws Exception {
    mockMvc
        .perform(getAs(adminToken, "/api/v1/faculties/import-template"))
        .andExpect(status().isOk())
        .andExpect(
            header()
                .string(HttpHeaders.CONTENT_DISPOSITION, containsString("faculties-template.csv")));

    String csv =
        "code,name,description,isActive\n"
            + "ITEST_IMP_FIT,Khoa CNTT Import,Mo ta FIT,true\n"
            + "ITEST_IMP_FE,Khoa Kinh te Import,Mo ta FE,false\n"
            + ",Khoa Thieu Ma,Mo ta,true\n";
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "faculties.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

    mockMvc
        .perform(
            multipart("/api/v1/faculties/import")
                .file(file)
                .header(HttpHeaders.AUTHORIZATION, pmToken))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            multipart("/api/v1/faculties/import")
                .file(file)
                .header(HttpHeaders.AUTHORIZATION, adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalRows").value(3))
        .andExpect(jsonPath("$.successCount").value(2))
        .andExpect(jsonPath("$.failureCount").value(1));

    Faculty fit = facultyRepository.findByCode("ITEST_IMP_FIT").orElseThrow();
    assertThat(fit.isActive()).isTrue();
    Faculty fe = facultyRepository.findByCode("ITEST_IMP_FE").orElseThrow();
    assertThat(fe.isActive()).isFalse();
  }

  @Test
  @DisplayName("ADMIN import Người dùng (template + CSV kèm roles và facultyCode); PM bị 403")
  void adminImportUsers_SuccessAndPmForbidden() throws Exception {
    mockMvc
        .perform(getAs(adminToken, "/api/v1/users/import-template"))
        .andExpect(status().isOk())
        .andExpect(
            header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("users-template.csv")));

    String csv =
        "username,email,password,fullName,roles,facultyCode,status\n"
            + "itest_imp_pm,itest_imp_pm@hcmute.edu.vn,Password123!,PM Import,PROGRAM_MANAGER,"
            + pmFaculty.getCode()
            + ",ACTIVE\n"
            + "itest_imp_gv,itest_imp_gv@hcmute.edu.vn,Password123!,GV Import,LECTURER;QA,,ACTIVE\n"
            + "itest_imp_bad,bad-email,Password123!,Bad Email,LECTURER,,ACTIVE\n";
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "users.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

    mockMvc
        .perform(
            multipart("/api/v1/users/import").file(file).header(HttpHeaders.AUTHORIZATION, pmToken))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            multipart("/api/v1/users/import")
                .file(file)
                .header(HttpHeaders.AUTHORIZATION, adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalRows").value(3))
        .andExpect(jsonPath("$.successCount").value(2))
        .andExpect(jsonPath("$.failureCount").value(1));

    User pm = userRepository.findByUsername("itest_imp_pm").orElseThrow();
    assertThat(pm.getRoles()).containsExactly(Role.PROGRAM_MANAGER);
    assertThat(pm.getFacultyId()).isEqualTo(pmFaculty.getId());

    User gv = userRepository.findByUsername("itest_imp_gv").orElseThrow();
    assertThat(gv.getRoles()).containsExactlyInAnyOrder(Role.LECTURER, Role.QA);
    assertThat(gv.getFacultyId()).isNull();
  }

  @Test
  @DisplayName(
      "Import học phần: ADMIN gán theo facultyCode/facultyId; PM tự động gán vào Khoa của PM")
  void importCourses_AssignsFacultyForAdminAndAutoAssignsForPm() throws Exception {
    mockMvc
        .perform(getAs(pmToken, "/api/v1/courses/import-template"))
        .andExpect(status().isOk())
        .andExpect(
            header()
                .string(HttpHeaders.CONTENT_DISPOSITION, containsString("courses-template.csv")));

    // 1. ADMIN import với cột facultyCode và fallback facultyId
    String adminCsv =
        "code,name,credits,facultyCode\n"
            + "ITEST_C_01,Mon Khoa Khac,3,"
            + otherFaculty.getCode()
            + "\n"
            + "ITEST_C_02,Mon Fallback Khoa PM,3,\n";
    MockMultipartFile adminFile =
        new MockMultipartFile(
            "file", "admin-courses.csv", "text/csv", adminCsv.getBytes(StandardCharsets.UTF_8));

    mockMvc
        .perform(
            multipart("/api/v1/courses/import")
                .file(adminFile)
                .param("facultyId", pmFaculty.getId())
                .header(HttpHeaders.AUTHORIZATION, adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.successCount").value(2))
        .andExpect(jsonPath("$.failureCount").value(0));

    assertThat(courseRepository.findByCode("ITEST_C_01").orElseThrow().getFacultyId())
        .isEqualTo(otherFaculty.getId());
    assertThat(courseRepository.findByCode("ITEST_C_02").orElseThrow().getFacultyId())
        .isEqualTo(pmFaculty.getId());

    // 2. PM import: dù CSV có ghi facultyCode của Khoa khác vẫn tự động ép về Khoa của PM
    String pmCsv =
        "code,name,credits,facultyCode\n"
            + "ITEST_C_03,Mon PM Import,4,"
            + otherFaculty.getCode()
            + "\n";
    MockMultipartFile pmFile =
        new MockMultipartFile(
            "file", "pm-courses.csv", "text/csv", pmCsv.getBytes(StandardCharsets.UTF_8));

    mockMvc
        .perform(
            multipart("/api/v1/courses/import")
                .file(pmFile)
                .header(HttpHeaders.AUTHORIZATION, pmToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.successCount").value(1))
        .andExpect(jsonPath("$.failureCount").value(0));

    assertThat(courseRepository.findByCode("ITEST_C_03").orElseThrow().getFacultyId())
        .isEqualTo(pmFaculty.getId());
  }
}
