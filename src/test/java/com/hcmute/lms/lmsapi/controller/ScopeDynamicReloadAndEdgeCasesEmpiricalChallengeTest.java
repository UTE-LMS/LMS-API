package com.hcmute.lms.lmsapi.controller;

import com.hcmute.lms.lmsapi.document.CourseSection;
import com.hcmute.lms.lmsapi.document.Role;
import com.hcmute.lms.lmsapi.document.RoleAssignment;
import com.hcmute.lms.lmsapi.document.User;
import com.hcmute.lms.lmsapi.document.UserScope;
import com.hcmute.lms.lmsapi.document.UserStatus;
import com.hcmute.lms.lmsapi.dto.LoginRequest;
import com.hcmute.lms.lmsapi.repository.CourseSectionRepository;
import com.hcmute.lms.lmsapi.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Empirical Challenge Tests — Challenger 2 (PR 5: Dynamic Permission Reload & Edge Cases).
 * Adversarially challenges:
 * 1. Dynamic DB permission update:
 *    - Issue valid JWT token for a user with Role LECTURER on Section A.
 *    - Call READ Section A -> 200 OK.
 *    - In DB, remove Section A from user's scope.
 *    - Using the SAME token, immediately call READ Section A -> must get 403 FORBIDDEN.
 *    - Then in DB, add user to CourseSection.lecturerIds of Section A.
 *    - Using the SAME token, call READ Section A -> must get 200 OK.
 * 2. Demoting user from ADMIN to VIEWER in DB:
 *    - With existing token, write access must immediately fail with 403,
 *      while read access succeeds according to VIEWER rules.
 * 3. Removing all roles from user in DB:
 *    - With existing token, all protected access must immediately fail with 403.
 * 4. Malformed/invalid ObjectId format in sectionId:
 *    - Ensure no unhandled 500 exceptions, graceful rejection or false return.
 */
@SpringBootTest
@DisplayName("Empirical Challenge: Dynamic Permission Reload & Edge Cases (Challenger 2 - PR 5)")
public class ScopeDynamicReloadAndEdgeCasesEmpiricalChallengeTest {

    private static final String FIXTURE_PREFIX = "challenger_p5_2_";
    private static final String FIXTURE_LABEL = "[DỮ LIỆU THỬ NGHIỆM] ";
    private static final String RAW_PASSWORD = "ChallengePassword123!";

    private static final String SECTION_A_ID = "671dcba5f123456789abc0a1";
    private static final String SECTION_B_ID = "671dcba5f123456789abc0b2";
    private static final String COURSE_ID = "671dcba5f123456789abc101";
    private static final String SYLLABUS_ID = "671dcba5f123456789abc201";
    private static final String TERM_ID = "671dcba5f123456789abc301";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CourseSectionRepository courseSectionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    private final List<String> createdUsernames = Collections.synchronizedList(new ArrayList<>());
    private final List<String> createdSectionIds = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        cleanupData();

        // Khởi tạo Section A và Section B chuẩn cho các kịch bản kiểm thử
        createTestSection(SECTION_A_ID, "SEC_CHALLENGE_A", new ArrayList<>());
        createTestSection(SECTION_B_ID, "SEC_CHALLENGE_B", new ArrayList<>());
    }

    @AfterEach
    void tearDown() {
        cleanupData();
    }

    private void cleanupData() {
        for (String username : createdUsernames) {
            userRepository.findByUsername(username).ifPresent(userRepository::delete);
        }
        createdUsernames.clear();

        for (String sectionId : createdSectionIds) {
            courseSectionRepository.deleteById(sectionId);
        }
        createdSectionIds.clear();

        // Dọn dẹp cả các ID cố định phòng trường hợp còn sót
        courseSectionRepository.deleteById(SECTION_A_ID);
        courseSectionRepository.deleteById(SECTION_B_ID);
    }

    private CourseSection createTestSection(String id, String code, List<String> lecturerIds) {
        createdSectionIds.add(id);
        courseSectionRepository.deleteById(id);

        CourseSection section = CourseSection.builder()
                .id(id)
                .code(code)
                .courseId(COURSE_ID)
                .syllabusId(SYLLABUS_ID)
                .termId(TERM_ID)
                .lecturerIds(lecturerIds != null ? new ArrayList<>(lecturerIds) : new ArrayList<>())
                .enrolledCount(35)
                .status("OPEN")
                .build();

        return courseSectionRepository.save(section);
    }

    private User createTestUser(String suffix, Role role, UserScope scope) {
        String username = FIXTURE_PREFIX + suffix;
        createdUsernames.add(username);
        userRepository.findByUsername(username).ifPresent(userRepository::delete);

        User user = User.builder()
                .username(username)
                .email(username + "@hcmute.edu.vn")
                .passwordHash(passwordEncoder.encode(RAW_PASSWORD))
                .fullName(FIXTURE_LABEL + "User " + suffix)
                .status(UserStatus.ACTIVE)
                .tokenVersion(0L)
                .roleAssignments(List.of(
                        RoleAssignment.builder().role(role).scope(scope).build()
                ))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        return userRepository.save(user);
    }

    private String obtainAccessToken(String username, String password) throws Exception {
        LoginRequest request = new LoginRequest(username, password);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    // =========================================================================
    // TASK 1: DYNAMIC DB PERMISSION UPDATE (LECTURER SCOPE -> REMOVE -> ASSIGN)
    // =========================================================================

    @Nested
    @DisplayName("Task 1: Dynamic DB Permission Update Lifecycle")
    class DynamicDbPermissionUpdateTests {

        @Test
        @DisplayName("Lecturer: Scoped on Section A (200 OK) -> Remove Section A in DB (403) -> Add to lecturerIds in DB (200 OK) using SAME token")
        void dynamicPermissionUpdate_LecturerLifecycle_ScopeRemovalToForbidden_ThenAssignedLecturerToOk() throws Exception {
            // Bước 1: Khởi tạo người dùng có vai trò LECTURER với scope chứa Section A
            UserScope initialScope = UserScope.builder()
                    .sectionIds(List.of(SECTION_A_ID))
                    .build();
            User lecturer = createTestUser("dyn_lecturer", Role.LECTURER, initialScope);

            // Cấp JWT Access Token 1 lần duy nhất
            String token = obtainAccessToken(lecturer.getUsername(), RAW_PASSWORD);
            assertThat(token).isNotBlank();

            // Giai đoạn 1: Với Token này, kiểm tra READ Section A -> 200 OK
            mockMvc.perform(get("/api/v1/test/sections/" + SECTION_A_ID)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SUCCESS"))
                    .andExpect(jsonPath("$.message").value("READ access granted"))
                    .andExpect(jsonPath("$.sectionId").value(SECTION_A_ID));

            // Kiểm tra thêm WRITE Section A cũng thành công do Section A trong scope
            mockMvc.perform(post("/api/v1/test/sections/" + SECTION_A_ID + "/grades")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SUCCESS"))
                    .andExpect(jsonPath("$.message").value("WRITE access granted"));

            // Bước 2: CẬP NHẬT CSDL: Gỡ Section A khỏi scope của user (set scope rỗng)
            User userInDb = userRepository.findById(lecturer.getId()).orElseThrow();
            userInDb.setRoleAssignments(List.of(
                    RoleAssignment.builder()
                            .role(Role.LECTURER)
                            .scope(UserScope.builder().sectionIds(Collections.emptyList()).build())
                            .build()
            ));
            userInDb.setUpdatedAt(Instant.now());
            userRepository.save(userInDb);

            // Giai đoạn 2: Dùng LẠI CHÍNH TOKEN CŨ (không login lại), gọi READ Section A -> BẮT BUỘC NHẬN 403 FORBIDDEN
            mockMvc.perform(get("/api/v1/test/sections/" + SECTION_A_ID)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                    .andExpect(jsonPath("$.status").value(403));

            // WRITE Section A cũng phải bị từ chối 403
            mockMvc.perform(post("/api/v1/test/sections/" + SECTION_A_ID + "/grades")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));

            // Bước 3: CẬP NHẬT CSDL: Thêm user vào CourseSection.lecturerIds của Section A
            CourseSection sectionAInDb = courseSectionRepository.findById(SECTION_A_ID).orElseThrow();
            sectionAInDb.setLecturerIds(List.of(lecturer.getId()));
            courseSectionRepository.save(sectionAInDb);

            // Giai đoạn 3: Dùng LẠI CHÍNH TOKEN CŨ (vẫn token ban đầu), gọi READ Section A -> BẮT BUỘC 200 OK
            mockMvc.perform(get("/api/v1/test/sections/" + SECTION_A_ID)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SUCCESS"))
                    .andExpect(jsonPath("$.message").value("READ access granted"));

            // WRITE Section A cũng phải thành công 200 OK (theo quy tắc lecturerIds độc lập)
            mockMvc.perform(post("/api/v1/test/sections/" + SECTION_A_ID + "/grades")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SUCCESS"))
                    .andExpect(jsonPath("$.message").value("WRITE access granted"));
        }
    }

    // =========================================================================
    // TASK 2: DEMOTING USER FROM ADMIN TO VIEWER IN DB
    // =========================================================================

    @Nested
    @DisplayName("Task 2: Demoting User from ADMIN to VIEWER in DB")
    class DemoteAdminToViewerTests {

        @Test
        @DisplayName("ADMIN demoted to VIEWER (empty scope): Write immediately fails 403, Read succeeds 200 with SAME token")
        void demotingAdminToViewer_StrictEmptyScope_WriteFails403_ReadSucceeds200() throws Exception {
            // Bước 1: Khởi tạo tài khoản ADMIN
            User admin = createTestUser("admin_to_viewer", Role.ADMIN, null);
            String token = obtainAccessToken(admin.getUsername(), RAW_PASSWORD);

            // Giai đoạn 1: Với vai trò ADMIN, cả READ và WRITE đều 200 OK
            mockMvc.perform(get("/api/v1/test/sections/" + SECTION_A_ID)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SUCCESS"));

            mockMvc.perform(post("/api/v1/test/sections/" + SECTION_A_ID + "/grades")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SUCCESS"));

            // Bước 2: Hạ quyền user từ ADMIN xuống VIEWER với scope rỗng (null) trong CSDL
            User userInDb = userRepository.findById(admin.getId()).orElseThrow();
            userInDb.setRoleAssignments(List.of(
                    RoleAssignment.builder().role(Role.VIEWER).scope(null).build()
            ));
            userInDb.setUpdatedAt(Instant.now());
            userRepository.save(userInDb);

            // Giai đoạn 2: Dùng lại token cũ:
            // - WRITE lập tức thất bại với HTTP 403 FORBIDDEN
            mockMvc.perform(post("/api/v1/test/sections/" + SECTION_A_ID + "/grades")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                    .andExpect(jsonPath("$.status").value(403));

            // - READ tiếp tục thành công với HTTP 200 OK theo quy tắc VIEWER scope rỗng (đọc toàn hệ thống)
            mockMvc.perform(get("/api/v1/test/sections/" + SECTION_A_ID)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SUCCESS"))
                    .andExpect(jsonPath("$.message").value("READ access granted"));
        }

        @Test
        @DisplayName("ADMIN demoted to VIEWER with specific scope: Read inside scope 200 OK, Read outside scope 403, Write everywhere 403")
        void demotingAdminToViewer_Scoped_ReadScopedOnly_WriteForbidden() throws Exception {
            // Bước 1: Khởi tạo tài khoản ADMIN
            User admin = createTestUser("admin_to_scoped_viewer", Role.ADMIN, null);
            String token = obtainAccessToken(admin.getUsername(), RAW_PASSWORD);

            // Bước 2: Hạ quyền user từ ADMIN xuống VIEWER chỉ có Section B trong scope
            User userInDb = userRepository.findById(admin.getId()).orElseThrow();
            userInDb.setRoleAssignments(List.of(
                    RoleAssignment.builder()
                            .role(Role.VIEWER)
                            .scope(UserScope.builder().sectionIds(List.of(SECTION_B_ID)).build())
                            .build()
            ));
            userInDb.setUpdatedAt(Instant.now());
            userRepository.save(userInDb);

            // Giai đoạn 2: Dùng lại token cũ:
            // - READ Section A (ngoài scope) -> 403 FORBIDDEN
            mockMvc.perform(get("/api/v1/test/sections/" + SECTION_A_ID)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));

            // - READ Section B (trong scope) -> 200 OK
            mockMvc.perform(get("/api/v1/test/sections/" + SECTION_B_ID)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SUCCESS"));

            // - WRITE Section B (kể cả trong scope) -> 403 FORBIDDEN
            mockMvc.perform(post("/api/v1/test/sections/" + SECTION_B_ID + "/grades")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
    }

    // =========================================================================
    // TASK 3: REMOVING ALL ROLES FROM USER IN DB
    // =========================================================================

    @Nested
    @DisplayName("Task 3: Removing All Roles from User in DB")
    class RemoveAllRolesTests {

        @Test
        @DisplayName("User with active token: Empty roleAssignments list in DB -> All protected endpoints fail with 403")
        void removingAllRoles_EmptyList_AllAccessImmediatelyFails403() throws Exception {
            // Bước 1: Khởi tạo tài khoản có vai trò ADMIN
            User user = createTestUser("stripped_roles_empty", Role.ADMIN, null);
            String token = obtainAccessToken(user.getUsername(), RAW_PASSWORD);

            // Ban đầu: truy cập thành công
            mockMvc.perform(get("/api/v1/test/sections/" + SECTION_A_ID)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/v1/users")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk());

            // Bước 2: Xóa sạch toàn bộ vai trò của user trong CSDL (danh sách rỗng)
            User userInDb = userRepository.findById(user.getId()).orElseThrow();
            userInDb.setRoleAssignments(Collections.emptyList());
            userInDb.setUpdatedAt(Instant.now());
            userRepository.save(userInDb);

            // Bước 3: Dùng LẠI CHÍNH TOKEN CŨ, toàn bộ các truy cập được bảo vệ đều phải bị từ chối 403
            // 3.1. READ endpoint trên mock section controller
            mockMvc.perform(get("/api/v1/test/sections/" + SECTION_A_ID)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                    .andExpect(jsonPath("$.status").value(403));

            // 3.2. WRITE endpoint trên mock section controller
            mockMvc.perform(post("/api/v1/test/sections/" + SECTION_A_ID + "/grades")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                    .andExpect(jsonPath("$.status").value(403));

            // 3.3. Endpoint quản lý người dùng UserController (yêu cầu hasRole('ADMIN'))
            mockMvc.perform(get("/api/v1/users")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                    .andExpect(jsonPath("$.status").value(403));
        }

        @Test
        @DisplayName("User with multiple roles (ADMIN + LECTURER): Stripping all roles to empty list in DB -> All access immediately fails 403")
        void removingAllRoles_MultiRoleStripping_AllAccessImmediatelyFails403() throws Exception {
            // Bước 1: Khởi tạo tài khoản có CẢ 2 VAI TRÒ: ADMIN và LECTURER
            UserScope scope = UserScope.builder().sectionIds(List.of(SECTION_A_ID)).build();
            User user = createTestUser("stripped_multi_roles", Role.ADMIN, null);
            user.setRoleAssignments(List.of(
                    RoleAssignment.builder().role(Role.ADMIN).scope(null).build(),
                    RoleAssignment.builder().role(Role.LECTURER).scope(scope).build()
            ));
            user = userRepository.save(user);

            String token = obtainAccessToken(user.getUsername(), RAW_PASSWORD);

            // Ban đầu: Cả READ và WRITE đều 200 OK
            mockMvc.perform(get("/api/v1/test/sections/" + SECTION_A_ID)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/api/v1/test/sections/" + SECTION_A_ID + "/grades")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk());

            // Bước 2: Quản trị viên xóa sạch toàn bộ vai trò (set roleAssignments = empty list)
            // Lưu ý: roleAssignments là trường required trong MongoDB $jsonSchema, nên danh sách rỗng []
            // đại diện chuẩn xác cho trạng thái tài khoản không còn bất kỳ vai trò nào.
            User userInDb = userRepository.findById(user.getId()).orElseThrow();
            userInDb.setRoleAssignments(Collections.emptyList());
            userInDb.setUpdatedAt(Instant.now());
            userRepository.save(userInDb);

            // Bước 3: Với token cũ: cả READ và WRITE trên Section A, cùng GET /api/v1/users đều phải lập tức thất bại 403
            mockMvc.perform(get("/api/v1/test/sections/" + SECTION_A_ID)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                    .andExpect(jsonPath("$.status").value(403));

            mockMvc.perform(post("/api/v1/test/sections/" + SECTION_A_ID + "/grades")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                    .andExpect(jsonPath("$.status").value(403));

            mockMvc.perform(get("/api/v1/users")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                    .andExpect(jsonPath("$.status").value(403));
        }
    }

    // =========================================================================
    // TASK 4: MALFORMED / INVALID OBJECTID FORMAT IN SECTION_ID
    // =========================================================================

    @Nested
    @DisplayName("Task 4: Malformed / Invalid ObjectId Format in sectionId")
    class MalformedObjectIdTests {

        @ParameterizedTest(name = "Malformed sectionId: [{0}] -> gracefully handled without 500 error")
        @ValueSource(strings = {
                "not-an-object-id",
                "12345",
                "671dcba5f123456789abc",       // 21 chars (thiếu độ dài 24 hex)
                "671dcba5f123456789abc00z",     // ký tự không phải hex ('z')
                "-1",
                "null",
                "   ",                          // whitespace
                "671dcba5f123456789abc001'--",  // SQL/Mongo injection probe
                "undefined"
        })
        @DisplayName("Malformed/invalid sectionId on GET endpoint: Gracefully rejected (403/400/404), never 500")
        void malformedSectionId_GetSection_NeverThrows500(String malformedSectionId) throws Exception {
            User admin = createTestUser("admin_malformed_get", Role.ADMIN, null);
            String token = obtainAccessToken(admin.getUsername(), RAW_PASSWORD);

            MvcResult result = mockMvc.perform(get("/api/v1/test/sections/" + malformedSectionId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andReturn();

            int statusCode = result.getResponse().getStatus();

            // Khẳng định chắc chắn: Tuyệt đối không được phát sinh mã lỗi 500 INTERNAL_ERROR
            assertThat(statusCode)
                    .withFailMessage("Malformed sectionId [%s] caused unexpected HTTP %d", malformedSectionId, statusCode)
                    .isNotEqualTo(500);

            // Hệ thống phải từ chối an toàn (403 FORBIDDEN do không tìm thấy section trong DB hoặc 400/404)
            assertThat(statusCode).isIn(400, 403, 404);

            String responseBody = result.getResponse().getContentAsString();
            assertThat(responseBody)
                    .withFailMessage("Response body should not be empty")
                    .isNotBlank();

            // Khẳng định không bị rò rỉ stack trace hoặc tên ngoại lệ nội bộ
            assertThat(responseBody).doesNotContain("NullPointerException");
            assertThat(responseBody).doesNotContain("IllegalArgumentException");
            assertThat(responseBody).doesNotContain("InternalServerError");
        }

        @ParameterizedTest(name = "Malformed sectionId on POST /grades: [{0}] -> gracefully handled without 500 error")
        @ValueSource(strings = {
                "not-an-object-id",
                "12345",
                "671dcba5f123456789abc",
                "671dcba5f123456789abc00z",
                "-1",
                "null"
        })
        @DisplayName("Malformed/invalid sectionId on POST endpoint: Gracefully rejected (403/400/404), never 500")
        void malformedSectionId_PostGrades_NeverThrows500(String malformedSectionId) throws Exception {
            User admin = createTestUser("admin_malformed_post", Role.ADMIN, null);
            String token = obtainAccessToken(admin.getUsername(), RAW_PASSWORD);

            MvcResult result = mockMvc.perform(post("/api/v1/test/sections/" + malformedSectionId + "/grades")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andReturn();

            int statusCode = result.getResponse().getStatus();

            assertThat(statusCode)
                    .withFailMessage("Malformed sectionId [%s] on POST caused unexpected HTTP %d", malformedSectionId, statusCode)
                    .isNotEqualTo(500);

            assertThat(statusCode).isIn(400, 403, 404);

            String responseBody = result.getResponse().getContentAsString();
            assertThat(responseBody).isNotBlank();
            assertThat(responseBody).doesNotContain("InternalServerError");
        }

        @Test
        @DisplayName("Malformed ObjectId on GET /api/v1/users/{id} -> Returns HTTP 400 VALIDATION_ERROR, never 500")
        void malformedUserId_Returns400ValidationError_Never500() throws Exception {
            User admin = createTestUser("admin_malformed_user", Role.ADMIN, null);
            String token = obtainAccessToken(admin.getUsername(), RAW_PASSWORD);

            mockMvc.perform(get("/api/v1/users/not-a-valid-24-hex-id")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.fieldErrors").isArray());
        }
    }
}
