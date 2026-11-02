package com.hcmute.lms.lmsapi.controller;

import com.hcmute.lms.lmsapi.document.Course;
import com.hcmute.lms.lmsapi.document.Pi;
import com.hcmute.lms.lmsapi.document.Plo;
import com.hcmute.lms.lmsapi.document.Program;
import com.hcmute.lms.lmsapi.document.ProgramVersion;
import com.hcmute.lms.lmsapi.document.RoleAssignment;
import com.hcmute.lms.lmsapi.document.User;
import com.hcmute.lms.lmsapi.document.UserScope;
import com.hcmute.lms.lmsapi.document.Verification;
import com.hcmute.lms.lmsapi.dto.CoursePiContributionRequest;
import com.hcmute.lms.lmsapi.dto.LoginRequest;
import com.hcmute.lms.lmsapi.dto.PiRequest;
import com.hcmute.lms.lmsapi.dto.PloRequest;
import com.hcmute.lms.lmsapi.dto.ProgramRequest;
import com.hcmute.lms.lmsapi.dto.ProgramVersionRequest;
import com.hcmute.lms.lmsapi.dto.ProgramVersionUpdateRequest;
import com.hcmute.lms.lmsapi.dto.VerificationRequest;
import com.hcmute.lms.lmsapi.enums.PiContributionType;
import com.hcmute.lms.lmsapi.enums.ProgramVersionStatus;
import com.hcmute.lms.lmsapi.enums.Role;
import com.hcmute.lms.lmsapi.enums.UserStatus;
import com.hcmute.lms.lmsapi.enums.VerificationStatus;
import com.hcmute.lms.lmsapi.repository.CoursePiContributionRepository;
import com.hcmute.lms.lmsapi.repository.CourseRepository;
import com.hcmute.lms.lmsapi.repository.PiRepository;
import com.hcmute.lms.lmsapi.repository.PloRepository;
import com.hcmute.lms.lmsapi.repository.ProgramRepository;
import com.hcmute.lms.lmsapi.repository.ProgramVersionRepository;
import com.hcmute.lms.lmsapi.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@DisplayName("F02: Program, PLO, PI and I/R/M Matrix Integration Tests")
class ProgramIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProgramRepository programRepository;

    @Autowired
    private ProgramVersionRepository programVersionRepository;

    @Autowired
    private PloRepository ploRepository;

    @Autowired
    private PiRepository piRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CoursePiContributionRepository contributionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    private static final String RAW_PASSWORD = "Password123!";
    private static final String SUFFIX = String.valueOf(System.nanoTime());

    private Program program;
    private ProgramVersion draftVersion;
    private ProgramVersion activeVersion;
    private Course course;
    private User managerUser;
    private User qaUser;
    private User qaOutOfScopeUser;
    private User adminUser;
    private User lecturerUser;

    private String managerToken;
    private String qaToken;
    private String qaOutOfScopeToken;
    private String adminToken;
    private String lecturerToken;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        program = programRepository.save(Program.builder()
                .code("F02_TEST_" + SUFFIX)
                .name("Chương trình kiểm thử F02")
                .build());

        draftVersion = programVersionRepository.save(ProgramVersion.builder()
                .programId(program.getId())
                .version("DRAFT-" + SUFFIX)
                .status(ProgramVersionStatus.DRAFT)
                .build());

        activeVersion = programVersionRepository.save(ProgramVersion.builder()
                .programId(program.getId())
                .version("ACTIVE-" + SUFFIX)
                .status(ProgramVersionStatus.ACTIVE)
                .build());

        course = courseRepository.save(Course.builder()
                .code("F02_COURSE_" + SUFFIX)
                .name("Học phần kiểm thử F02")
                .credits(3)
                .build());

        managerUser = createUser("f02_manager_" + SUFFIX, Role.PROGRAM_MANAGER,
                UserScope.builder().build());
        qaUser = createUser("f02_qa_" + SUFFIX, Role.QA_OFFICER,
                UserScope.builder().programIds(List.of(program.getId())).build());
        qaOutOfScopeUser = createUser("f02_qa_out_" + SUFFIX, Role.QA_OFFICER,
                UserScope.builder().build());
        adminUser = createUser("f02_admin_" + SUFFIX, Role.ADMIN,
                UserScope.builder().build());
        lecturerUser = createUser("f02_lecturer_" + SUFFIX, Role.LECTURER,
                UserScope.builder().build());

        managerToken = obtainAccessToken(managerUser.getUsername());
        qaToken = obtainAccessToken(qaUser.getUsername());
        qaOutOfScopeToken = obtainAccessToken(qaOutOfScopeUser.getUsername());
        adminToken = obtainAccessToken(adminUser.getUsername());
        lecturerToken = obtainAccessToken(lecturerUser.getUsername());
    }

    @AfterEach
    void tearDown() {
        contributionRepository.findByProgramVersionIdOrderByCourseIdAscPiIdAsc(draftVersion.getId())
                .forEach(contributionRepository::delete);
        contributionRepository.findByProgramVersionIdOrderByCourseIdAscPiIdAsc(activeVersion.getId())
                .forEach(contributionRepository::delete);
        ploRepository.deleteAll(ploRepository.findByProgramVersionIdOrderByDisplayOrderAsc(draftVersion.getId()));
        ploRepository.deleteAll(ploRepository.findByProgramVersionIdOrderByDisplayOrderAsc(activeVersion.getId()));
        piRepository.deleteAll(piRepository.findByProgramVersionIdOrderByDisplayOrderAsc(draftVersion.getId()));
        piRepository.deleteAll(piRepository.findByProgramVersionIdOrderByDisplayOrderAsc(activeVersion.getId()));
        programVersionRepository.deleteAll(List.of(draftVersion, activeVersion));
        programRepository.findAll().stream()
                .filter(item -> item.getCode() != null && item.getCode().equals("F02_NEW_" + SUFFIX))
                .forEach(programRepository::delete);
        programRepository.delete(program);
        courseRepository.delete(course);
        userRepository.deleteAll(List.of(managerUser, qaUser, qaOutOfScopeUser, adminUser, lecturerUser));
    }

    private User createUser(String username, Role role, UserScope scope) {
        return userRepository.save(User.builder()
                .username(username)
                .email(username + "@hcmute.edu.vn")
                .passwordHash(passwordEncoder.encode(RAW_PASSWORD))
                .fullName("[F02 TEST] " + role.name())
                .status(UserStatus.ACTIVE)
                .tokenVersion(0L)
                .roleAssignments(List.of(RoleAssignment.builder().role(role).scope(scope).build()))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());
    }

    private String obtainAccessToken(String username) throws Exception {
        LoginRequest request = new LoginRequest(username, RAW_PASSWORD);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    @Test
    @DisplayName("Đọc CTĐT yêu cầu đăng nhập, ghi cần PROGRAM_MANAGER, kiểm tra trùng mã và chuyển trạng thái")
    void programAndVersionFlow() throws Exception {
        mockMvc.perform(get("/api/v1/programs"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/programs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + lecturerToken))
                .andExpect(status().isOk());

        ProgramRequest request = ProgramRequest.builder()
                .code("F02_NEW_" + SUFFIX)
                .name("Chương trình mới")
                .build();

        mockMvc.perform(post("/api/v1/programs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + lecturerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/programs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("F02_NEW_" + SUFFIX));

        mockMvc.perform(post("/api/v1/programs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/program-versions")
                        .param("programId", program.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)));

        ProgramVersionRequest missingProgram = ProgramVersionRequest.builder()
                .programId("671dcba5f123456789abc777")
                .version("X")
                .build();
        mockMvc.perform(post("/api/v1/program-versions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(missingProgram)))
                .andExpect(status().isUnprocessableEntity());

        ProgramVersionUpdateRequest backward = ProgramVersionUpdateRequest.builder()
                .version(activeVersion.getVersion())
                .status(ProgramVersionStatus.DRAFT)
                .build();
        mockMvc.perform(put("/api/v1/program-versions/" + activeVersion.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(backward)))
                .andExpect(status().isUnprocessableEntity());

        ProgramVersionUpdateRequest forward = ProgramVersionUpdateRequest.builder()
                .version(draftVersion.getVersion())
                .status(ProgramVersionStatus.ACTIVE)
                .build();
        mockMvc.perform(put("/api/v1/program-versions/" + draftVersion.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(forward)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("PLO/PI/I-R-M: validate mã, ràng buộc PLO-PI, trùng lặp và chặn xóa PLO còn PI")
    void ploPiAndContributionFlow() throws Exception {
        PloRequest invalid = PloRequest.builder().code("PLO0").displayOrder(1).build();
        mockMvc.perform(post("/api/v1/program-versions/" + draftVersion.getId() + "/plos")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());

        PloRequest ploRequest = PloRequest.builder()
                .code("PLO1")
                .displayOrder(1)
                .contentSummary("Nội dung PLO")
                .capabilityLevelRaw(3)
                .build();

        MvcResult ploResult = mockMvc.perform(post("/api/v1/program-versions/" + draftVersion.getId() + "/plos")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ploRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.verification.status").value("NEEDS_CLARIFICATION"))
                .andReturn();
        String ploId = JsonPath.read(ploResult.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/api/v1/program-versions/" + draftVersion.getId() + "/plos")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ploRequest)))
                .andExpect(status().isConflict());

        PiRequest wrongPlo = PiRequest.builder()
                .ploId("671dcba5f123456789abc778")
                .code("PI1.1")
                .displayOrder(1)
                .build();
        mockMvc.perform(post("/api/v1/program-versions/" + draftVersion.getId() + "/pis")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongPlo)))
                .andExpect(status().isUnprocessableEntity());

        PiRequest piRequest = PiRequest.builder()
                .ploId(ploId)
                .code("PI1.1")
                .displayOrder(1)
                .capabilityLevelRaw(3)
                .targetPassRate(0.8)
                .build();
        MvcResult piResult = mockMvc.perform(post("/api/v1/program-versions/" + draftVersion.getId() + "/pis")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(piRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        String piId = JsonPath.read(piResult.getResponse().getContentAsString(), "$.id");

        CoursePiContributionRequest contributionRequest = CoursePiContributionRequest.builder()
                .courseId(course.getId())
                .piId(piId)
                .contribution(PiContributionType.R)
                .build();
        mockMvc.perform(post("/api/v1/program-versions/" + draftVersion.getId() + "/pi-contributions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(contributionRequest)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/program-versions/" + draftVersion.getId() + "/pi-contributions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(contributionRequest)))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/v1/program-versions/" + draftVersion.getId() + "/plos/" + ploId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(get("/api/v1/program-versions/" + draftVersion.getId() + "/pis")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @DisplayName("Xác nhận chỉ QA_OFFICER đúng phạm vi chương trình; ADMIN và PROGRAM_MANAGER bị 403")
    void verificationRequiresQaOfficerInProgramScope() throws Exception {
        VerificationRequest request = VerificationRequest.builder()
                .status(VerificationStatus.VERIFIED)
                .note("Đã kiểm tra")
                .build();
        String body = objectMapper.writeValueAsString(request);

        mockMvc.perform(put("/api/v1/program-versions/" + draftVersion.getId() + "/verification")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/program-versions/" + draftVersion.getId() + "/verification")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/program-versions/" + draftVersion.getId() + "/verification")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + qaOutOfScopeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/program-versions/" + draftVersion.getId() + "/verification")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + qaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verification.status").value("VERIFIED"))
                .andExpect(jsonPath("$.verification.verifiedBy").value(qaUser.getId()));

        Plo plo = ploRepository.save(Plo.builder()
                .programVersionId(draftVersion.getId())
                .code("PLO2")
                .displayOrder(2)
                .verification(Verification.builder().status(VerificationStatus.NEEDS_CLARIFICATION).build())
                .build());

        mockMvc.perform(put("/api/v1/program-versions/" + draftVersion.getId() + "/plos/" + plo.getId() + "/verification")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + qaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verification.status").value("VERIFIED"));

        Pi pi = piRepository.save(Pi.builder()
                .programVersionId(draftVersion.getId())
                .ploId(plo.getId())
                .code("PI2.1")
                .displayOrder(3)
                .verification(Verification.builder().status(VerificationStatus.NEEDS_CLARIFICATION).build())
                .build());

        mockMvc.perform(put("/api/v1/program-versions/" + draftVersion.getId() + "/pis/" + pi.getId() + "/verification")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + qaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verification.status").value("VERIFIED"));
    }
}
