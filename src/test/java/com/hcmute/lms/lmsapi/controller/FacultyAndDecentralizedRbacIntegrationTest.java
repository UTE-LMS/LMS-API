package com.hcmute.lms.lmsapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hcmute.lms.lmsapi.document.Assessment;
import com.hcmute.lms.lmsapi.document.AssessmentItem;
import com.hcmute.lms.lmsapi.document.Clo;
import com.hcmute.lms.lmsapi.document.Course;
import com.hcmute.lms.lmsapi.document.CourseSection;
import com.hcmute.lms.lmsapi.document.Faculty;
import com.hcmute.lms.lmsapi.document.OutcomeMapping;
import com.hcmute.lms.lmsapi.document.Program;
import com.hcmute.lms.lmsapi.document.ProgramCourse;
import com.hcmute.lms.lmsapi.document.ProgramVersion;
import com.hcmute.lms.lmsapi.document.Syllabus;
import com.hcmute.lms.lmsapi.document.Term;
import com.hcmute.lms.lmsapi.document.User;
import com.hcmute.lms.lmsapi.dto.CourseRequest;
import com.hcmute.lms.lmsapi.dto.CourseUpdateRequest;
import com.hcmute.lms.lmsapi.dto.FacultyAssignPmRequest;
import com.hcmute.lms.lmsapi.dto.FacultyRequest;
import com.hcmute.lms.lmsapi.dto.FacultyStatusUpdateRequest;
import com.hcmute.lms.lmsapi.dto.FacultyTransferPmRequest;
import com.hcmute.lms.lmsapi.dto.ProgramRequest;
import com.hcmute.lms.lmsapi.enums.AssessmentScope;
import com.hcmute.lms.lmsapi.enums.AssessmentStatus;
import com.hcmute.lms.lmsapi.enums.AssessmentType;
import com.hcmute.lms.lmsapi.enums.ProgramVersionStatus;
import com.hcmute.lms.lmsapi.enums.Role;
import com.hcmute.lms.lmsapi.enums.SectionStatus;
import com.hcmute.lms.lmsapi.enums.SyllabusStatus;
import com.hcmute.lms.lmsapi.enums.TermStatus;
import com.hcmute.lms.lmsapi.repository.FacultyRepository;
import com.hcmute.lms.lmsapi.repository.UserRepository;
import com.hcmute.lms.lmsapi.testsupport.IntegrationTestBase;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

class FacultyAndDecentralizedRbacIntegrationTest extends IntegrationTestBase {

  @Autowired private FacultyRepository facultyRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private MongoTemplate mongoTemplate;

  private String createdFacultyId1;
  private String createdFacultyId2;

  @AfterEach
  void cleanCreatedFaculties() {
    if (createdFacultyId1 != null) {
      facultyRepository.deleteById(createdFacultyId1);
      createdFacultyId1 = null;
    }
    if (createdFacultyId2 != null) {
      facultyRepository.deleteById(createdFacultyId2);
      createdFacultyId2 = null;
    }
  }

  @Test
  void adminManagesFacultiesAndEnforcesOnePmPerFacultyAndTokenInvalidation() throws Exception {
    User admin = fixtures.user(Role.ADMIN, null);
    User pm1 = fixtures.user(Role.PROGRAM_MANAGER, null);
    User pm2 = fixtures.user(Role.PROGRAM_MANAGER, null);
    String adminBearer = fixtures.bearer(admin);
    String pm1OldBearer = fixtures.bearer(pm1);

    String f1Code = ("F1_" + fixtures.code("FIT")).toUpperCase();
    String f2Code = ("F2_" + fixtures.code("FAS")).toUpperCase();
    if (f1Code.length() > 20) f1Code = f1Code.substring(0, 20);
    if (f2Code.length() > 20) f2Code = f2Code.substring(0, 20);

    MvcResult f1Res =
        mockMvc
            .perform(
                postAs(
                    adminBearer,
                    "/api/v1/faculties",
                    FacultyRequest.builder().code(f1Code).name("Khoa CNTT " + f1Code).build()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value(f1Code))
            .andReturn();
    createdFacultyId1 = JsonPath.read(f1Res.getResponse().getContentAsString(), "$.id");

    MvcResult f2Res =
        mockMvc
            .perform(
                postAs(
                    adminBearer,
                    "/api/v1/faculties",
                    FacultyRequest.builder().code(f2Code).name("Khoa KHCB " + f2Code).build()))
            .andExpect(status().isCreated())
            .andReturn();
    createdFacultyId2 = JsonPath.read(f2Res.getResponse().getContentAsString(), "$.id");

    // Gán PM1 và PM2 vào Khoa 1 (1 Khoa có nhiều PM)
    mockMvc
        .perform(
            postAs(
                adminBearer,
                "/api/v1/faculties/" + createdFacultyId1 + "/program-managers",
                FacultyAssignPmRequest.builder().userId(pm1.getId()).build()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.facultyId").value(createdFacultyId1));

    mockMvc
        .perform(
            postAs(
                adminBearer,
                "/api/v1/faculties/" + createdFacultyId1 + "/program-managers",
                FacultyAssignPmRequest.builder().userId(pm2.getId()).build()))
        .andExpect(status().isOk());

    // Vi phạm bất biến 1 PM - 1 Khoa: gán PM1 vào Khoa 2 -> 409 Conflict
    mockMvc
        .perform(
            postAs(
                adminBearer,
                "/api/v1/faculties/" + createdFacultyId2 + "/program-managers",
                FacultyAssignPmRequest.builder().userId(pm1.getId()).build()))
        .andExpect(status().isConflict());

    // Điều chuyển PM1 sang Khoa 2 -> token cũ của PM1 bị vô hiệu hóa (401)
    mockMvc
        .perform(
            postAs(
                adminBearer,
                "/api/v1/faculties/transfer-program-manager",
                FacultyTransferPmRequest.builder()
                    .userId(pm1.getId())
                    .targetFacultyId(createdFacultyId2)
                    .transferReason("Điều chuyển công tác")
                    .build()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.facultyId").value(createdFacultyId2));

    mockMvc.perform(getAs(pm1OldBearer, "/api/v1/faculties")).andExpect(status().isUnauthorized());

    // Hủy gán PM2 khỏi Khoa 1 -> token cũ của PM2 bị vô hiệu hóa
    String pm2OldBearer = fixtures.bearer(pm2);
    mockMvc
        .perform(
            deleteAs(
                adminBearer,
                "/api/v1/faculties/" + createdFacultyId1 + "/program-managers/" + pm2.getId()))
        .andExpect(status().isNoContent());

    mockMvc.perform(getAs(pm2OldBearer, "/api/v1/faculties")).andExpect(status().isUnauthorized());
  }

  @Test
  void pmCreatesOwnProgramAndCourseAndCannotTouchOtherFacultyOrTd29Syllabus() throws Exception {
    String f1Code = ("F1_" + fixtures.code("IT")).toUpperCase();
    String f2Code = ("F2_" + fixtures.code("SC")).toUpperCase();
    if (f1Code.length() > 20) f1Code = f1Code.substring(0, 20);
    if (f2Code.length() > 20) f2Code = f2Code.substring(0, 20);

    Faculty f1 =
        facultyRepository.save(
            Faculty.builder().code(f1Code).name("Khoa 1 " + f1Code).isActive(true).build());
    Faculty f2 =
        facultyRepository.save(
            Faculty.builder().code(f2Code).name("Khoa 2 " + f2Code).isActive(true).build());
    createdFacultyId1 = f1.getId();
    createdFacultyId2 = f2.getId();

    User pm1 = fixtures.user(Role.PROGRAM_MANAGER, null);
    pm1.setFacultyId(f1.getId());
    pm1 = userRepository.save(pm1);
    String pm1Bearer = fixtures.bearer(pm1);

    User pm2 = fixtures.user(Role.PROGRAM_MANAGER, null);
    pm2.setFacultyId(f2.getId());
    pm2 = userRepository.save(pm2);
    String pm2Bearer = fixtures.bearer(pm2);

    // PM1 tạo CTĐT bỏ trống facultyId -> tự gán f1.getId()
    MvcResult progRes =
        mockMvc
            .perform(
                postAs(
                    pm1Bearer,
                    "/api/v1/programs",
                    ProgramRequest.builder().code(fixtures.code("PRG")).name("Ngành CNTT").build()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.facultyId").value(f1.getId()))
            .andReturn();
    String prog1Id = JsonPath.read(progRes.getResponse().getContentAsString(), "$.id");

    // PM1 cố tạo CTĐT cho Khoa 2 -> 403
    mockMvc
        .perform(
            postAs(
                pm1Bearer,
                "/api/v1/programs",
                ProgramRequest.builder()
                    .facultyId(f2.getId())
                    .code(fixtures.code("PRG2"))
                    .name("Ngành Toán")
                    .build()))
        .andExpect(status().isForbidden());

    // PM2 tạo môn Toán thuộc Khoa 2
    MvcResult course2Res =
        mockMvc
            .perform(
                postAs(
                    pm2Bearer,
                    "/api/v1/courses",
                    CourseRequest.builder()
                        .code(fixtures.code("MATH"))
                        .name("Toán cao cấp")
                        .credits(3)
                        .build()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.facultyId").value(f2.getId()))
            .andReturn();
    String course2Id = JsonPath.read(course2Res.getResponse().getContentAsString(), "$.id");

    // Liên kết môn Toán (Khoa 2) vào Phiên bản CTĐT ACTIVE của Khoa 1
    Program prog1 = mongoTemplate.findById(prog1Id, Program.class);
    ProgramVersion pv1 = fixtures.programVersion(prog1, ProgramVersionStatus.ACTIVE);
    mongoTemplate.save(
        ProgramCourse.builder()
            .programVersionId(pv1.getId())
            .courseId(course2Id)
            .semester(1)
            .credits(3)
            .build());

    // Chống TD-29: PM1 tuy có dùng môn Toán trong CTĐT nhưng không được preview/import đề cương môn
    // Toán
    MockMultipartFile csv =
        new MockMultipartFile(
            "file",
            "syllabus.csv",
            "text/csv",
            "code,contentOriginal,bloomLevel,defaultPassThreshold\nCLO1,Noi dung,2,0.5\n"
                .getBytes(StandardCharsets.UTF_8));
    mockMvc
        .perform(
            multipart("/api/v1/courses/" + course2Id + "/syllabi/import/preview")
                .file(csv)
                .param("version", "2026")
                .param("programVersionId", pv1.getId())
                .header(HttpHeaders.AUTHORIZATION, pm1Bearer))
        .andExpect(status().isForbidden());

    // Toàn vẹn liên khoa: PM2 cố vô hiệu hóa hoặc xóa môn Toán đang nằm trong CTĐT ACTIVE của Khoa
    // 1 -> 422
    mockMvc
        .perform(
            putAs(
                pm2Bearer,
                "/api/v1/courses/" + course2Id,
                CourseUpdateRequest.builder()
                    .name("Toán cao cấp")
                    .credits(3)
                    .isActive(false)
                    .build()))
        .andExpect(status().isUnprocessableEntity());

    mockMvc
        .perform(deleteAs(pm2Bearer, "/api/v1/courses/" + course2Id))
        .andExpect(status().isUnprocessableEntity());

    // Admin không được khóa Khoa 1 khi Khoa 1 vẫn còn CTĐT ACTIVE
    User admin = fixtures.user(Role.ADMIN, null);
    mockMvc
        .perform(
            putAs(
                fixtures.bearer(admin),
                "/api/v1/faculties/" + f1.getId() + "/status",
                FacultyStatusUpdateRequest.builder().isActive(false).reason("Test").build()))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void lockingSyllabusOrProgramVersionBlocksLecturerScoreImport() throws Exception {
    String fCode = ("FL_" + fixtures.code("LC")).toUpperCase();
    if (fCode.length() > 20) fCode = fCode.substring(0, 20);
    Faculty f1 =
        facultyRepository.save(
            Faculty.builder().code(fCode).name("Khoa Lock " + fCode).isActive(true).build());
    createdFacultyId1 = f1.getId();

    User pm = fixtures.user(Role.PROGRAM_MANAGER, null);
    pm.setFacultyId(f1.getId());
    pm = userRepository.save(pm);
    String pmBearer = fixtures.bearer(pm);

    User lecturer = fixtures.user(Role.LECTURER, null);
    String lecturerBearer = fixtures.bearer(lecturer);

    Program program = fixtures.program();
    program.setFacultyId(f1.getId());
    program = mongoTemplate.save(program);

    ProgramVersion pv = fixtures.programVersion(program, ProgramVersionStatus.ACTIVE);

    Course course = fixtures.course();
    course.setFacultyId(f1.getId());
    course = mongoTemplate.save(course);

    Syllabus syllabus = fixtures.syllabus(course, SyllabusStatus.CONFIRMED);
    Clo clo = fixtures.clo(syllabus, "CLO1");
    Term term = fixtures.term(TermStatus.OPEN);
    CourseSection section =
        mongoTemplate.save(
            CourseSection.builder()
                .code(fixtures.code("SEC"))
                .courseId(course.getId())
                .syllabusId(syllabus.getId())
                .termId(term.getId())
                .lecturerIds(List.of(lecturer.getId()))
                .status(SectionStatus.OPEN)
                .build());
    Assessment assessment =
        mongoTemplate.save(
            Assessment.builder()
                .scope(AssessmentScope.SECTION)
                .sectionId(section.getId())
                .syllabusId(syllabus.getId())
                .code(fixtures.code("ASM"))
                .name("Giữa kỳ")
                .type(AssessmentType.MIDTERM)
                .status(AssessmentStatus.LOCKED)
                .locked(true)
                .items(
                    List.of(
                        AssessmentItem.builder()
                            .id(new org.bson.types.ObjectId().toHexString())
                            .code("Q1")
                            .orderIndex(1)
                            .outcomeMappings(
                                List.of(
                                    OutcomeMapping.builder()
                                        .cloId(clo.getId())
                                        .maxScore(10.0)
                                        .build()))
                            .build()))
                .build());

    // PM khóa đề cương (/lock)
    mockMvc
        .perform(putAs(pmBearer, "/api/v1/syllabi/" + syllabus.getId() + "/lock", "{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ARCHIVED"));

    // Giảng viên cố import điểm vào lớp thuộc đề cương đã khóa -> bị chặn 422
    MockMultipartFile scoreFile =
        new MockMultipartFile(
            "file", "scores.csv", "text/csv", "studentRef,Q1\n20110001,8\n".getBytes());
    mockMvc
        .perform(
            multipart(
                    "/api/v1/sections/"
                        + section.getId()
                        + "/assessments/"
                        + assessment.getId()
                        + "/import-batches")
                .file(scoreFile)
                .header(HttpHeaders.AUTHORIZATION, lecturerBearer))
        .andExpect(status().isUnprocessableEntity());

    // PM khóa luôn Phiên bản CTĐT (/lock)
    mockMvc
        .perform(putAs(pmBearer, "/api/v1/program-versions/" + pv.getId() + "/lock", "{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ARCHIVED"));

    assertThat(facultyRepository.findById(f1.getId())).isPresent();
  }
}
