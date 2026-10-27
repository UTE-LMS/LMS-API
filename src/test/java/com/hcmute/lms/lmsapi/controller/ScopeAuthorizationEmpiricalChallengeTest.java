package com.hcmute.lms.lmsapi.controller;

import com.hcmute.lms.lmsapi.document.CourseSection;
import com.hcmute.lms.lmsapi.document.Role;
import com.hcmute.lms.lmsapi.document.RoleAssignment;
import com.hcmute.lms.lmsapi.document.User;
import com.hcmute.lms.lmsapi.document.UserScope;
import com.hcmute.lms.lmsapi.document.UserStatus;
import com.hcmute.lms.lmsapi.dto.AccessType;
import com.hcmute.lms.lmsapi.dto.LoginRequest;
import com.hcmute.lms.lmsapi.dto.UserPrincipal;
import com.hcmute.lms.lmsapi.repository.CourseSectionRepository;
import com.hcmute.lms.lmsapi.repository.UserRepository;
import com.hcmute.lms.lmsapi.service.ScopeAuthorizationService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Empirical Adversarial Challenge Test Suite for US-03 Scope Authorization (ADR 0003).
 * Authored by Challenger 1 to stress-test and empirically challenge:
 * 1. Nonexistent sectionId -> 403 FORBIDDEN (Fail-Closed).
 * 2. Empty/null roleAssignments -> 403 FORBIDDEN.
 * 3. STUDENT role attempting READ or WRITE -> 403 FORBIDDEN.
 * 4. VIEWER attempting WRITE -> 403 FORBIDDEN.
 * 5. VIEWER with non-empty scope containing only programIds -> 403 FORBIDDEN (does NOT fall into system-wide read!).
 * 6. LECTURER attempting WRITE to section NOT in scope and NOT in lecturerIds -> 403 FORBIDDEN.
 * 7. LECTURER with empty scope but in lecturerIds -> 200 OK for both READ and WRITE.
 * 8. User appearing in CourseSection.lecturerIds who DOES NOT hold the LECTURER role (e.g. VIEWER or QA_OFFICER) -> MUST NOT get LECTURER write access!
 * 9. Multi-role user: User has VIEWER (read-only) AND LECTURER on section 1 -> can READ anywhere, can WRITE section 1, cannot WRITE section 2.
 * 10. Direct ScopeAuthorizationService edge cases and fail-closed defenses.
 */
@SpringBootTest
@DisplayName("CHALLENGE 1: Empirical Adversarial Testing - Scope Authorization Matrix & SpEL (ADR 0003)")
class ScopeAuthorizationEmpiricalChallengeTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CourseSectionRepository courseSectionRepository;

    @Autowired
    private ScopeAuthorizationService scopeAuthorizationService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    // 24-character hexadecimal ObjectId strings for MongoDB compliance
    private static final String SECTION_1_ID = "671dcba5f123456789abc011";
    private static final String SECTION_2_ID = "671dcba5f123456789abc022";
    private static final String NONEXISTENT_SECTION_ID = "671dcba5f123456789abc099";

    private static final String COURSE_1_ID = "671dcba5f123456789abc111";
    private static final String COURSE_2_ID = "671dcba5f123456789abc222";

    private static final String PROGRAM_1_ID = "671dcba5f123456789abc777";

    private static final String COMMON_PASSWORD = "ChallengePass123!";

    private User targetLecturerUser;
    private User targetNonLecturerUser;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        cleanupData();

        // 1. Giảng viên hợp lệ được phân công vào SECTION_1
        targetLecturerUser = userRepository.save(User.builder()
                .username("challenge_lecturer_assigned")
                .email("challenge_lecturer_assigned@hcmute.edu.vn")
                .passwordHash(passwordEncoder.encode(COMMON_PASSWORD))
                .fullName("[DỮ LIỆU THỬ NGHIỆM] Giảng viên phân công lớp 1")
                .status(UserStatus.ACTIVE)
                .tokenVersion(0L)
                .roleAssignments(List.of(
                        RoleAssignment.builder().role(Role.LECTURER).scope(null).build()
                ))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());

        // 2. Người dùng KHÔNG có vai trò LECTURER (chỉ là VIEWER) nhưng ID xuất hiện trong lecturerIds của SECTION_1
        targetNonLecturerUser = userRepository.save(User.builder()
                .username("challenge_viewer_in_lecturer_ids")
                .email("challenge_viewer_in_lecturer_ids@hcmute.edu.vn")
                .passwordHash(passwordEncoder.encode(COMMON_PASSWORD))
                .fullName("[DỮ LIỆU THỬ NGHIỆM] Viewer có ID trong lecturerIds")
                .status(UserStatus.ACTIVE)
                .tokenVersion(0L)
                .roleAssignments(List.of(
                        RoleAssignment.builder().role(Role.VIEWER).scope(null).build()
                ))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());

        // 3. Khởi tạo Section 1: chứa cả targetLecturerUser và targetNonLecturerUser trong lecturerIds
        courseSectionRepository.save(CourseSection.builder()
                .id(SECTION_1_ID)
                .code("CHAL401.T01")
                .courseId(COURSE_1_ID)
                .syllabusId("671dcba5f123456789abc331")
                .termId("671dcba5f123456789abc441")
                .lecturerIds(List.of(targetLecturerUser.getId(), targetNonLecturerUser.getId()))
                .enrolledCount(35)
                .status("OPEN")
                .build());

        // 4. Khởi tạo Section 2: KHÔNG chứa bất kỳ user nào ở trên trong lecturerIds
        courseSectionRepository.save(CourseSection.builder()
                .id(SECTION_2_ID)
                .code("CHAL402.T02")
                .courseId(COURSE_2_ID)
                .syllabusId("671dcba5f123456789abc332")
                .termId("671dcba5f123456789abc442")
                .lecturerIds(List.of("671dcba5f123456789abc987"))
                .enrolledCount(28)
                .status("OPEN")
                .build());
    }

    @AfterEach
    void tearDown() {
        cleanupData();
    }

    private void cleanupData() {
        List<String> usernames = List.of(
                "challenge_lecturer_assigned",
                "challenge_viewer_in_lecturer_ids",
                "challenge_admin",
                "challenge_student",
                "challenge_viewer_scoped",
                "challenge_viewer_prog_only",
                "challenge_lecturer_other_sec",
                "challenge_qa_in_lecturer_ids",
                "challenge_student_in_lecturer_ids",
                "challenge_multi_viewer_lecturer",
                "challenge_no_roles"
        );
        for (String uname : usernames) {
            userRepository.findByUsername(uname).ifPresent(userRepository::delete);
        }

        courseSectionRepository.deleteById(SECTION_1_ID);
        courseSectionRepository.deleteById(SECTION_2_ID);
        courseSectionRepository.deleteById(NONEXISTENT_SECTION_ID);
    }

    private String loginAndGetToken(String username, String password) throws Exception {
        LoginRequest request = new LoginRequest(username, password);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private String createUserAndGetToken(String username, Role role, UserScope scope) throws Exception {
        List<RoleAssignment> assignments = (role != null)
                ? List.of(RoleAssignment.builder().role(role).scope(scope).build())
                : List.of();

        userRepository.save(User.builder()
                .username(username)
                .email(username + "@hcmute.edu.vn")
                .passwordHash(passwordEncoder.encode(COMMON_PASSWORD))
                .fullName("[DỮ LIỆU THỬ NGHIỆM] " + username)
                .status(UserStatus.ACTIVE)
                .tokenVersion(0L)
                .roleAssignments(assignments)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());

        return loginAndGetToken(username, COMMON_PASSWORD);
    }

    // =========================================================================
    // 1. NONEXISTENT SECTION ID TESTS (SCENARIO 1)
    // =========================================================================

    @Nested
    @DisplayName("Challenge 1: Nonexistent Section ID -> 403 Forbidden (Fail-Closed)")
    class NonexistentSectionTests {

        @Test
        @DisplayName("ADMIN truy cập lớp học phần không tồn tại trên CSDL -> Trả về HTTP 403 FORBIDDEN")
        void adminAccessNonexistentSection_Returns403() throws Exception {
            String adminToken = createUserAndGetToken("challenge_admin", Role.ADMIN, null);

            // READ endpoint
            mockMvc.perform(get("/api/v1/test/sections/" + NONEXISTENT_SECTION_ID)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));

            // WRITE endpoint
            mockMvc.perform(post("/api/v1/test/sections/" + NONEXISTENT_SECTION_ID + "/grades")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("Direct Service: canAccessSection trả về false khi sectionId không tồn tại")
        void directService_NonexistentSection_ReturnsFalse() {
            UserPrincipal adminPrincipal = UserPrincipal.builder()
                    .id("admin-id")
                    .username("admin")
                    .roleAssignments(List.of(RoleAssignment.builder().role(Role.ADMIN).build()))
                    .build();

            assertThat(scopeAuthorizationService.canAccessSection(adminPrincipal, NONEXISTENT_SECTION_ID, AccessType.READ)).isFalse();
            assertThat(scopeAuthorizationService.canAccessSection(adminPrincipal, NONEXISTENT_SECTION_ID, AccessType.WRITE)).isFalse();
        }
    }

    // =========================================================================
    // 2. EMPTY OR NULL ROLE ASSIGNMENTS TESTS (SCENARIO 2)
    // =========================================================================

    @Nested
    @DisplayName("Challenge 2: Empty or Null Role Assignments -> 403 Forbidden")
    class EmptyRoleAssignmentsTests {

        @Test
        @DisplayName("Người dùng hợp lệ nhưng roleAssignments rỗng -> Bị từ chối HTTP 403 trên cả READ và WRITE")
        void emptyRoleAssignments_Returns403() throws Exception {
            String token = createUserAndGetToken("challenge_no_roles", null, null);

            mockMvc.perform(get("/api/v1/test/sections/" + SECTION_1_ID)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));

            mockMvc.perform(post("/api/v1/test/sections/" + SECTION_1_ID + "/grades")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("Direct Service: principal có roleAssignments null hoặc rỗng -> trả về false")
        void directService_NullOrEmptyAssignments_ReturnsFalse() {
            UserPrincipal principalNull = UserPrincipal.builder().id("u1").username("u1").roleAssignments(null).build();
            UserPrincipal principalEmpty = UserPrincipal.builder().id("u2").username("u2").roleAssignments(List.of()).build();

            assertThat(scopeAuthorizationService.canAccessSection(principalNull, SECTION_1_ID, AccessType.READ)).isFalse();
            assertThat(scopeAuthorizationService.canAccessSection(principalNull, SECTION_1_ID, AccessType.WRITE)).isFalse();
            assertThat(scopeAuthorizationService.canAccessSection(principalEmpty, SECTION_1_ID, AccessType.READ)).isFalse();
            assertThat(scopeAuthorizationService.canAccessSection(principalEmpty, SECTION_1_ID, AccessType.WRITE)).isFalse();
        }
    }

    // =========================================================================
    // 3. STUDENT ROLE ATTEMPTING READ OR WRITE (SCENARIO 3)
    // =========================================================================

    @Nested
    @DisplayName("Challenge 3: STUDENT Role Attempting READ or WRITE -> 403 Forbidden")
    class StudentRoleTests {

        @Test
        @DisplayName("STUDENT gọi READ hoặc WRITE trên tài nguyên lớp học phần -> Trả về HTTP 403 FORBIDDEN")
        void studentAttemptingReadOrWrite_Returns403() throws Exception {
            String studentToken = createUserAndGetToken("challenge_student", Role.STUDENT, null);

            mockMvc.perform(get("/api/v1/test/sections/" + SECTION_1_ID)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + studentToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));

            mockMvc.perform(post("/api/v1/test/sections/" + SECTION_1_ID + "/grades")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + studentToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
    }

    // =========================================================================
    // 4. VIEWER ATTEMPTING WRITE (SCENARIO 4)
    // =========================================================================

    @Nested
    @DisplayName("Challenge 4: VIEWER Attempting WRITE -> 403 Forbidden")
    class ViewerWriteTests {

        @Test
        @DisplayName("VIEWER có scope khớp sectionId gọi WRITE -> Tuyệt đối bị từ chối HTTP 403 FORBIDDEN")
        void viewerWithMatchingScope_AttemptingWrite_Returns403() throws Exception {
            UserScope scope = UserScope.builder().sectionIds(List.of(SECTION_1_ID)).build();
            String viewerToken = createUserAndGetToken("challenge_viewer_scoped", Role.VIEWER, scope);

            // READ cho phép vì scope khớp
            mockMvc.perform(get("/api/v1/test/sections/" + SECTION_1_ID)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + viewerToken))
                    .andExpect(status().isOk());

            // WRITE tuyệt đối bị cấm (403)
            mockMvc.perform(post("/api/v1/test/sections/" + SECTION_1_ID + "/grades")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + viewerToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
    }

    // =========================================================================
    // 5. VIEWER WITH PROGRAM-ONLY SCOPE (SCENARIO 5 - BOUNDARY CHECK)
    // =========================================================================

    @Nested
    @DisplayName("Challenge 5: VIEWER with Non-Empty Scope (programIds only) -> 403 Forbidden")
    class ViewerProgramOnlyScopeTests {

        @Test
        @DisplayName("VIEWER chỉ có programIds thì scope KHÔNG rỗng -> Không được rơi vào system-wide read -> Bị 403 FORBIDDEN")
        void viewerWithProgramOnlyScope_DoesNotFallIntoSystemWideRead_Returns403() throws Exception {
            UserScope progScope = UserScope.builder().programIds(List.of(PROGRAM_1_ID)).build();
            String token = createUserAndGetToken("challenge_viewer_prog_only", Role.VIEWER, progScope);

            // READ bị 403 (chứng minh không rơi vào nhánh scope rỗng đọc toàn hệ thống!)
            mockMvc.perform(get("/api/v1/test/sections/" + SECTION_1_ID)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));

            // WRITE bị 403
            mockMvc.perform(post("/api/v1/test/sections/" + SECTION_1_ID + "/grades")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
    }

    // =========================================================================
    // 6. LECTURER OUT OF SCOPE AND NOT IN lecturerIds (SCENARIO 6)
    // =========================================================================

    @Nested
    @DisplayName("Challenge 6: LECTURER Out of Scope and Not in lecturerIds -> 403 Forbidden")
    class LecturerOutOfScopeTests {

        @Test
        @DisplayName("LECTURER phụ trách SECTION_1 gọi WRITE lên SECTION_2 (không thuộc scope và không thuộc lecturerIds) -> 403 FORBIDDEN")
        void lecturerWritingToUnassignedSection_Returns403() throws Exception {
            UserScope scope = UserScope.builder().sectionIds(List.of(SECTION_1_ID)).build();
            String token = createUserAndGetToken("challenge_lecturer_other_sec", Role.LECTURER, scope);

            // Gọi vào SECTION_2
            mockMvc.perform(post("/api/v1/test/sections/" + SECTION_2_ID + "/grades")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));

            mockMvc.perform(get("/api/v1/test/sections/" + SECTION_2_ID)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
    }

    // =========================================================================
    // 7. LECTURER WITH EMPTY SCOPE BUT IN lecturerIds (SCENARIO 7 - INDEPENDENT RULE)
    // =========================================================================

    @Nested
    @DisplayName("Challenge 7: LECTURER with Empty Scope but in lecturerIds -> 200 OK for READ & WRITE")
    class LecturerIndependentRuleTests {

        @Test
        @DisplayName("LECTURER có scope null nhưng nằm trong CourseSection.lecturerIds -> READ (200 OK) và WRITE (200 OK)")
        void lecturerInLecturerIds_ReadAndWrite_Success() throws Exception {
            String token = loginAndGetToken(targetLecturerUser.getUsername(), COMMON_PASSWORD);

            // READ: 200 OK
            mockMvc.perform(get("/api/v1/test/sections/" + SECTION_1_ID)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SUCCESS"))
                    .andExpect(jsonPath("$.message").value("READ access granted"));

            // WRITE: 200 OK
            mockMvc.perform(post("/api/v1/test/sections/" + SECTION_1_ID + "/grades")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SUCCESS"))
                    .andExpect(jsonPath("$.message").value("WRITE access granted"));
        }
    }

    // =========================================================================
    // 8. NON-LECTURER ROLE IN lecturerIds (SCENARIO 8 - PRIVILEGE ESCALATION ATTACK)
    // =========================================================================

    @Nested
    @DisplayName("Challenge 8: Non-LECTURER Role Appearing in CourseSection.lecturerIds -> MUST NOT Get Lecturer Access")
    class NonLecturerInLecturerIdsTests {

        @Test
        @DisplayName("TẤN CÔNG LEO THANG ĐẶC QUYỀN: User giữ vai trò VIEWER nhưng ID có trong lecturerIds -> Tuyệt đối KHÔNG có WRITE access (403 FORBIDDEN)")
        void viewerAppearingInLecturerIds_MustNotGetWriteAccess() throws Exception {
            String token = loginAndGetToken(targetNonLecturerUser.getUsername(), COMMON_PASSWORD);

            // WRITE: Bắt buộc phải bị từ chối 403! (Quy tắc lecturerIds chỉ áp dụng khi user CÓ vai trò LECTURER)
            mockMvc.perform(post("/api/v1/test/sections/" + SECTION_1_ID + "/grades")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));

            // READ: Được phép 200 OK NHƯNG là thông qua quyền VIEWER (scope rỗng), KHÔNG PHẢI qua LECTURER!
            mockMvc.perform(get("/api/v1/test/sections/" + SECTION_1_ID)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("QA_OFFICER có ID trong lecturerIds của lớp nhưng scope rỗng -> WRITE bị 403, READ bị 403 (không được thừa hưởng quyền LECTURER)")
        void qaOfficerInLecturerIds_WithoutLecturerRole_MustNotGetAccess() throws Exception {
            // Tạo user QA_OFFICER có ID trùng với targetLecturerUser (được liệt kê trong lecturerIds của SECTION_1)
            // Thay vì tạo mới, ta đổi role của targetLecturerUser sang QA_OFFICER với scope rỗng
            User qaUser = userRepository.save(User.builder()
                    .username("challenge_qa_in_lecturer_ids")
                    .email("challenge_qa_in_lecturer_ids@hcmute.edu.vn")
                    .passwordHash(passwordEncoder.encode(COMMON_PASSWORD))
                    .fullName("[DỮ LIỆU THỬ NGHIỆM] QA Officer in lecturerIds")
                    .status(UserStatus.ACTIVE)
                    .tokenVersion(0L)
                    .roleAssignments(List.of(
                            RoleAssignment.builder().role(Role.QA_OFFICER).scope(new UserScope()).build()
                    ))
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build());

            // Thêm QA user vào lecturerIds của SECTION_1
            CourseSection sec1 = courseSectionRepository.findById(SECTION_1_ID).orElseThrow();
            List<String> updatedLecturerIds = new ArrayList<>(sec1.getLecturerIds());
            updatedLecturerIds.add(qaUser.getId());
            sec1.setLecturerIds(updatedLecturerIds);
            courseSectionRepository.save(sec1);

            String qaToken = loginAndGetToken("challenge_qa_in_lecturer_ids", COMMON_PASSWORD);

            // WRITE: 403 FORBIDDEN
            mockMvc.perform(post("/api/v1/test/sections/" + SECTION_1_ID + "/grades")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + qaToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));

            // READ: 403 FORBIDDEN (QA_OFFICER có scope rỗng -> fail closed; không được kế thừa lecturerIds)
            mockMvc.perform(get("/api/v1/test/sections/" + SECTION_1_ID)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + qaToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("STUDENT có ID trong lecturerIds của lớp -> WRITE bị 403, READ bị 403")
        void studentInLecturerIds_MustNotGetAccess() throws Exception {
            User studentUser = userRepository.save(User.builder()
                    .username("challenge_student_in_lecturer_ids")
                    .email("challenge_student_in_lecturer_ids@hcmute.edu.vn")
                    .passwordHash(passwordEncoder.encode(COMMON_PASSWORD))
                    .fullName("[DỮ LIỆU THỬ NGHIỆM] Student in lecturerIds")
                    .status(UserStatus.ACTIVE)
                    .tokenVersion(0L)
                    .roleAssignments(List.of(
                            RoleAssignment.builder().role(Role.STUDENT).scope(null).build()
                    ))
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build());

            CourseSection sec1 = courseSectionRepository.findById(SECTION_1_ID).orElseThrow();
            List<String> updatedLecturerIds = new ArrayList<>(sec1.getLecturerIds());
            updatedLecturerIds.add(studentUser.getId());
            sec1.setLecturerIds(updatedLecturerIds);
            courseSectionRepository.save(sec1);

            String studentToken = loginAndGetToken("challenge_student_in_lecturer_ids", COMMON_PASSWORD);

            mockMvc.perform(post("/api/v1/test/sections/" + SECTION_1_ID + "/grades")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + studentToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));

            mockMvc.perform(get("/api/v1/test/sections/" + SECTION_1_ID)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + studentToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
    }

    // =========================================================================
    // 9. MULTI-ROLE UNION TESTS: VIEWER + LECTURER (SCENARIO 9)
    // =========================================================================

    @Nested
    @DisplayName("Challenge 9: Multi-Role Union Tests (VIEWER + LECTURER)")
    class MultiRoleUnionTests {

        @Test
        @DisplayName("Người dùng có VIEWER (scope rỗng) VÀ LECTURER (phụ trách SECTION_1): có thể READ bất kỳ đâu, chỉ WRITE được SECTION_1, không WRITE được SECTION_2")
        void viewerAndLecturerUnion_PermissionsScopedCorrectly() throws Exception {
            // Tạo user có 2 vai trò:
            // 1. VIEWER: scope null (đọc toàn hệ thống)
            // 2. LECTURER: scope chỉ chứa SECTION_1_ID
            userRepository.save(User.builder()
                    .username("challenge_multi_viewer_lecturer")
                    .email("challenge_multi_viewer_lecturer@hcmute.edu.vn")
                    .passwordHash(passwordEncoder.encode(COMMON_PASSWORD))
                    .fullName("[DỮ LIỆU THỬ NGHIỆM] Đa vai trò VIEWER + LECTURER")
                    .status(UserStatus.ACTIVE)
                    .tokenVersion(0L)
                    .roleAssignments(List.of(
                            RoleAssignment.builder().role(Role.VIEWER).scope(null).build(),
                            RoleAssignment.builder()
                                    .role(Role.LECTURER)
                                    .scope(UserScope.builder().sectionIds(List.of(SECTION_1_ID)).build())
                                    .build()
                    ))
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build());

            String multiToken = loginAndGetToken("challenge_multi_viewer_lecturer", COMMON_PASSWORD);

            // 1. Trên SECTION_1:
            // READ: 200 OK (cả VIEWER và LECTURER đều cho phép)
            mockMvc.perform(get("/api/v1/test/sections/" + SECTION_1_ID)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + multiToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("READ access granted"));

            // WRITE: 200 OK (do LECTURER cấp quyền)
            mockMvc.perform(post("/api/v1/test/sections/" + SECTION_1_ID + "/grades")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + multiToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("WRITE access granted"));

            // 2. Trên SECTION_2:
            // READ: 200 OK (do VIEWER có scope rỗng đọc toàn hệ thống)
            mockMvc.perform(get("/api/v1/test/sections/" + SECTION_2_ID)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + multiToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("READ access granted"));

            // WRITE: Tuyệt đối bị cấm 403 FORBIDDEN! (VIEWER ko có quyền WRITE, LECTURER ko phụ trách SECTION_2)
            mockMvc.perform(post("/api/v1/test/sections/" + SECTION_2_ID + "/grades")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + multiToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
    }

    // =========================================================================
    // 10. DIRECT SERVICE FAIL-CLOSED & BOUNDARY TESTS
    // =========================================================================

    @Nested
    @DisplayName("Challenge 10: Direct Service Boundary & Fail-Closed Assertions")
    class DirectServiceEdgeCaseTests {

        @Test
        @DisplayName("Tham số null hoặc chuỗi trắng -> fail-closed trả về false")
        void nullAndBlankParameters_ReturnFalse() {
            UserPrincipal principal = UserPrincipal.builder()
                    .id("test-id")
                    .username("test_user")
                    .roleAssignments(List.of(RoleAssignment.builder().role(Role.ADMIN).build()))
                    .build();

            assertThat(scopeAuthorizationService.canAccessSection(null, SECTION_1_ID, AccessType.READ)).isFalse();
            assertThat(scopeAuthorizationService.canAccessSection(principal, null, AccessType.READ)).isFalse();
            assertThat(scopeAuthorizationService.canAccessSection(principal, "", AccessType.READ)).isFalse();
            assertThat(scopeAuthorizationService.canAccessSection(principal, "   ", AccessType.READ)).isFalse();
            assertThat(scopeAuthorizationService.canAccessSection(principal, SECTION_1_ID, null)).isFalse();
        }

        @Test
        @DisplayName("CourseSection có lecturerIds rỗng hoặc null -> xử lý an toàn không phát sinh NullPointerException")
        void sectionWithNullLecturerIds_SafeExecution() {
            CourseSection noLecturersSec = courseSectionRepository.save(CourseSection.builder()
                    .id("671dcba5f123456789abc033")
                    .code("CHAL403.T03")
                    .courseId(COURSE_1_ID)
                    .syllabusId("671dcba5f123456789abc333")
                    .termId("671dcba5f123456789abc443")
                    .lecturerIds(null) // null list
                    .status("OPEN")
                    .build());

            UserPrincipal lecturer = UserPrincipal.builder()
                    .id("any-lecturer-id")
                    .username("lecturer_test")
                    .roleAssignments(List.of(RoleAssignment.builder().role(Role.LECTURER).scope(new UserScope()).build()))
                    .build();

            assertThat(scopeAuthorizationService.canAccessSection(lecturer, noLecturersSec.getId(), AccessType.READ)).isFalse();
            assertThat(scopeAuthorizationService.canAccessSection(lecturer, noLecturersSec.getId(), AccessType.WRITE)).isFalse();

            courseSectionRepository.deleteById(noLecturersSec.getId());
        }
    }
}
