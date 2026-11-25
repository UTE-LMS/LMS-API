package com.hcmute.lms.lmsapi.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hcmute.lms.lmsapi.document.Clo;
import com.hcmute.lms.lmsapi.document.CloPiLink;
import com.hcmute.lms.lmsapi.document.Course;
import com.hcmute.lms.lmsapi.document.Faculty;
import com.hcmute.lms.lmsapi.document.Pi;
import com.hcmute.lms.lmsapi.document.Plo;
import com.hcmute.lms.lmsapi.document.Program;
import com.hcmute.lms.lmsapi.document.ProgramCourse;
import com.hcmute.lms.lmsapi.document.ProgramVersion;
import com.hcmute.lms.lmsapi.document.Syllabus;
import com.hcmute.lms.lmsapi.enums.CourseCategory;
import com.hcmute.lms.lmsapi.enums.CourseType;
import com.hcmute.lms.lmsapi.enums.ProgramVersionStatus;
import com.hcmute.lms.lmsapi.enums.SyllabusStatus;
import com.hcmute.lms.lmsapi.testsupport.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

@DisplayName("Public Homepage Catalog & Filtering Integration Tests")
class PublicHomepageCatalogIntegrationTest extends IntegrationTestBase {

  @Test
  @DisplayName("Người dùng chưa đăng nhập xem được danh mục công khai và lọc đa chiều chính xác")
  void anonymousUserCanBrowseAndFilterPublicCatalog() throws Exception {
    Faculty fit = fixtures.faculty();
    Faculty math = fixtures.faculty();

    Program itProg = fixtures.program();
    itProg.setFacultyId(fit.getId());
    itProg.setLevel("Đại học");
    itProg.setDegree("Kỹ sư");
    itProg.setName("Kỹ thuật Phần mềm");
    itProg = mongo().save(itProg);

    Program mathProg = fixtures.program();
    mathProg.setFacultyId(math.getId());
    mathProg.setLevel("Thạc sĩ");
    mathProg.setDegree("Thạc sĩ");
    mongo().save(mathProg);

    ProgramVersion itV2025 = fixtures.programVersion(itProg, ProgramVersionStatus.ACTIVE);
    itV2025.setVersion("2025.1");
    itV2025.setApplicableCohort("K2025");
    itV2025 = mongo().save(itV2025);

    ProgramVersion itV2020 = fixtures.programVersion(itProg, ProgramVersionStatus.ARCHIVED);
    itV2020.setVersion("2020.1");
    itV2020.setApplicableCohort("K2020");
    mongo().save(itV2020);

    Course javaCourse = fixtures.course();
    javaCourse.setFacultyId(fit.getId());
    javaCourse.setName("Lập trình Java");
    javaCourse.setActive(true);
    javaCourse = mongo().save(javaCourse);

    Course inactiveCourse = fixtures.course();
    inactiveCourse.setFacultyId(fit.getId());
    inactiveCourse.setActive(false);
    inactiveCourse = mongo().save(inactiveCourse);

    mongo()
        .save(
            ProgramCourse.builder()
                .programVersionId(itV2025.getId())
                .courseId(javaCourse.getId())
                .semester(3)
                .credits(3)
                .type(CourseType.COMPULSORY)
                .category(CourseCategory.FOUNDATION)
                .build());

    Plo plo = fixtures.plo(itV2025, "PLO1", 1);
    Pi pi = fixtures.pi(itV2025, plo, "PI1.1", 1);

    Syllabus syllabus = fixtures.syllabus(javaCourse, SyllabusStatus.CONFIRMED);
    syllabus.setVersion("2025.1");
    syllabus = mongo().save(syllabus);

    Clo clo = fixtures.clo(syllabus, "CLO1");
    mongo()
        .save(
            CloPiLink.builder()
                .cloId(clo.getId())
                .piId(pi.getId())
                .programVersionId(itV2025.getId())
                .syllabusId(syllabus.getId())
                .weight(1.0)
                .build());

    // 1. Public GET faculties (no token)
    mockMvc
        .perform(get("/api/v1/faculties").param("isActive", "true").param("search", fit.getCode()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id").value(fit.getId()));

    // 2. Public GET programs filtered by facultyId, level, degree, search
    mockMvc
        .perform(
            get("/api/v1/programs")
                .param("facultyId", fit.getId())
                .param("level", "Đại học")
                .param("degree", "Kỹ sư")
                .param("search", "Phần mềm"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].id").value(itProg.getId()));

    // 3. Public GET program-versions filtered by facultyId, cohort, status, search
    mockMvc
        .perform(
            get("/api/v1/program-versions")
                .param("facultyId", fit.getId())
                .param("cohort", "K2025")
                .param("status", "ACTIVE")
                .param("search", "2025"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].id").value(itV2025.getId()));

    // 4. Public GET program-versions/{id}/courses filtered by semester, type, category, search +
    // pagination
    mockMvc
        .perform(
            get("/api/v1/program-versions/{id}/courses", itV2025.getId())
                .param("semester", "3")
                .param("type", "COMPULSORY")
                .param("category", "FOUNDATION")
                .param("search", "Java")
                .param("page", "0")
                .param("size", "20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.last").value(true))
        .andExpect(jsonPath("$.content[0].courseId").value(javaCourse.getId()));

    mockMvc
        .perform(
            get("/api/v1/program-versions/{id}/courses", itV2025.getId())
                .param("page", "0")
                .param("size", "101"))
        .andExpect(status().isBadRequest());

    // 5. Public GET program-versions/{id}/syllabi filtered by status & search
    mockMvc
        .perform(
            get("/api/v1/program-versions/{id}/syllabi", itV2025.getId())
                .param("status", "CONFIRMED")
                .param("search", "2025"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].id").value(syllabus.getId()));

    // 6. Public GET courses filtered by facultyId & isActive
    mockMvc
        .perform(get("/api/v1/courses").param("facultyId", fit.getId()).param("isActive", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[*].id", hasItem(javaCourse.getId())))
        .andExpect(jsonPath("$.content[?(@.id=='" + inactiveCourse.getId() + "')]").isEmpty());

    // 7. Public GET PLOs, PIs, Syllabus detail, CLOs, CLO-PI links & matrix (no token)
    mockMvc
        .perform(get("/api/v1/program-versions/{id}/plos", itV2025.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)));
    mockMvc
        .perform(get("/api/v1/program-versions/{id}/pis", itV2025.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)));
    mockMvc
        .perform(get("/api/v1/courses/{courseId}/syllabi", javaCourse.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)));
    mockMvc
        .perform(get("/api/v1/syllabi/{id}", syllabus.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(syllabus.getId()));
    mockMvc
        .perform(get("/api/v1/syllabi/{id}/clos", syllabus.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)));
    mockMvc
        .perform(
            get("/api/v1/syllabi/{id}/clo-pi-matrix", syllabus.getId())
                .param("programVersionId", itV2025.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.clos", hasSize(1)))
        .andExpect(jsonPath("$.pis", hasSize(1)));
  }

  @Test
  @DisplayName("Endpoint nhạy cảm và thao tác ghi vẫn yêu cầu xác thực (401)")
  void protectedEndpointsStillRequireAuthentication() throws Exception {
    Faculty faculty = fixtures.faculty();
    Course course = fixtures.course();

    mockMvc
        .perform(get("/api/v1/faculties/{id}/program-managers", faculty.getId()))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(get("/api/v1/program-versions/import-template"))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(get("/api/v1/courses/{id}/syllabi/import-template", course.getId()))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(post("/api/v1/programs").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());
  }
}
