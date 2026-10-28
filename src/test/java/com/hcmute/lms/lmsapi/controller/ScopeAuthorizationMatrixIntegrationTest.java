package com.hcmute.lms.lmsapi.controller;

import com.hcmute.lms.lmsapi.document.CourseSection;
import com.hcmute.lms.lmsapi.enums.Role;
import com.hcmute.lms.lmsapi.document.RoleAssignment;
import com.hcmute.lms.lmsapi.document.User;
import com.hcmute.lms.lmsapi.document.UserScope;
import com.hcmute.lms.lmsapi.enums.UserStatus;
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
import org.junit.jupiter.params.provider.CsvSource;
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
 * Kiểm thử tích hợp toàn diện ma trận phân quyền vai trò × phạm vi × READ/WRITE (F01 / US-03 / ADR 0003).
 * Sử dụng MockMvc và controller giả lập ScopeAuthorizationTestController với @PreAuthorize,
 * kết hợp kiểm chứng nạp quyền động từ CSDL MongoDB mỗi request (Dynamic Permission Reload).
 */
@SpringBootTest
@DisplayName("US-03: Scope Authorization Controller Matrix & Dynamic Reload Integration Tests (ADR 0003)")
class ScopeAuthorizationMatrixIntegrationTest {

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

    private static final String TARGET_SECTION_ID = "671dcba5f123456789abc001";
    private static final String TARGET_COURSE_ID = "671dcba5f123456789abc100";
    private static final String OTHER_SECTION_ID = "671dcba5f123456789abc999";
    private static final String COMMON_PASSWORD = "Password123!";

    private CourseSection savedCourseSection;
    private String lecturerAssignedUserId;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        cleanupData();

        // 1. Tạo người dùng giảng viên thử nghiệm có ID cố định để đưa vào lecturerIds của lớp
        User assignedLecturer = userRepository.save(User.builder()
                .username("test_sec_assigned_lecturer")
                .email("test_sec_assigned_lecturer@hcmute.edu.vn")
                .passwordHash(passwordEncoder.encode(COMMON_PASSWORD))
                .fullName("[DỮ LIỆU THỬ NGHIỆM] Giảng viên phụ trách lớp")
                .status(UserStatus.ACTIVE)
                .tokenVersion(0L)
                .roleAssignments(List.of(
                        RoleAssignment.builder().role(Role.LECTURER).scope(null).build()
                ))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());
        this.lecturerAssignedUserId = assignedLecturer.getId();

        // 2. Tạo CourseSection thử nghiệm trong CSDL
        savedCourseSection = courseSectionRepository.save(CourseSection.builder()
                .id(TARGET_SECTION_ID)
                .code("SE401.T01")
                .courseId(TARGET_COURSE_ID)
                .syllabusId("671dcba5f123456789abc222")
                .termId("671dcba5f123456789abc333")
                .lecturerIds(List.of(lecturerAssignedUserId))
                .enrolledCount(40)
                .status("OPEN")
                .build());
    }

    @AfterEach
    void tearDown() {
        cleanupData();
    }

    private void cleanupData() {
        // Xóa các user thử nghiệm
        List<String> testUsernames = List.of(
                "test_sec_assigned_lecturer",
                "test_sec_admin",
                "test_sec_lecturer_scoped",
                "test_sec_lecturer_unassigned",
                "test_sec_pm",
                "test_sec_dh",
                "test_sec_qa",
                "test_sec_viewer_empty",
                "test_sec_viewer_scoped",
                "test_sec_viewer_prog_only",
                "test_sec_student",
                "test_sec_dynamic_user"
        );
        for (String uname : testUsernames) {
            userRepository.findByUsername(uname).ifPresent(userRepository::delete);
        }

        // Xóa section thử nghiệm
        courseSectionRepository.deleteById(TARGET_SECTION_ID);
    }

    /**
     * Tiện ích đăng nhập và lấy JWT Access Token cho một tài khoản.
     */
    private String loginAndGetToken(String username, String password) throws Exception {
        LoginRequest request = new LoginRequest(username, password);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    /**
     * Tiện ích tạo nhanh User thử nghiệm và trả về Access Token.
     */
    private String createUserAndGetToken(String username, Role role, UserScope scope) throws Exception {
        userRepository.save(User.builder()
                .username(username)
                .email(username + "@hcmute.edu.vn")
                .passwordHash(passwordEncoder.encode(COMMON_PASSWORD))
                .fullName("[DỮ LIỆU THỬ NGHIỆM] " + username)
                .status(UserStatus.ACTIVE)
                .tokenVersion(0L)
                .roleAssignments(List.of(
                        RoleAssignment.builder().role(role).scope(scope).build()
                ))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());

        return loginAndGetToken(username, COMMON_PASSWORD);
    }

    // =========================================================================
    // 1. KIỂM THỬ XÁC THỰC CƠ BẢN VÀ FAIL-CLOSED
    // =========================================================================

    @Nested
    @DisplayName("1. Authentication & Fail-Closed Endpoint Tests")
    class AuthAndFailClosedTests {

        @Test
        @DisplayName("Không có Bearer token -> trả về HTTP 401 UNAUTHORIZED")
        void noToken_Returns401() throws Exception {
            mockMvc.perform(get("/api/v1/test/sections/" + TARGET_SECTION_ID))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

            mockMvc.perform(post("/api/v1/test/sections/" + TARGET_SECTION_ID + "/grades"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("Lớp học phần không tồn tại trên CSDL -> trả về HTTP 403 FORBIDDEN (Fail-Closed)")
        void sectionNotFound_Returns403() throws Exception {
            String adminToken = createUserAndGetToken("test_sec_admin", Role.ADMIN, null);

            mockMvc.perform(get("/api/v1/test/sections/" + OTHER_SECTION_ID)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
    }

    // =========================================================================
    // 2. MA TRẬN PHÂN QUYỀN VAI TRÒ × PHẠM VI × READ/WRITE
    // =========================================================================

    @Nested
    @DisplayName("2. Role x Scope x READ/WRITE Matrix Tests")
    class MatrixTests {

        @Test
        @DisplayName("ADMIN: Toàn quyền READ (200 OK) và WRITE (200 OK) trên toàn bộ lớp")
        void admin_ReadAndWrite_Success() throws Exception {
            String token = createUserAndGetToken("test_sec_admin", Role.ADMIN, null);

            mockMvc.perform(get("/api/v1/test/sections/" + TARGET_SECTION_ID)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SUCCESS"))
                    .andExpect(jsonPath("$.message").value("READ access granted"));

            mockMvc.perform(post("/api/v1/test/sections/" + TARGET_SECTION_ID + "/grades")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SUCCESS"))
                    .andExpect(jsonPath("$.message").value("WRITE access granted"));
        }

        @Test
        @DisplayName("LECTURER (Scope khớp sectionId): READ (200 OK) và WRITE (200 OK)")
        void lecturer_ScopedSection_Success() throws Exception {
            UserScope scope = UserScope.builder().sectionIds(List.of(TARGET_SECTION_ID)).build();
            String token = createUserAndGetToken("test_sec_lecturer_scoped", Role.LECTURER, scope);

            mockMvc.perform(get("/api/v1/test/sections/" + TARGET_SECTION_ID)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/api/v1/test/sections/" + TARGET_SECTION_ID + "/grades")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("LECTURER (Quy tắc độc lập lecturerIds): Scope rỗng nhưng có tên trong lecturerIds -> READ (200 OK) và WRITE (200 OK)")
        void lecturer_IndependentLecturerIdsRule_Success() throws Exception {
            // Tài khoản test_sec_assigned_lecturer đã được tạo trong setUp() với scope null và nằm trong lecturerIds
            String token = loginAndGetToken("test_sec_assigned_lecturer", COMMON_PASSWORD);

            mockMvc.perform(get("/api/v1/test/sections/" + TARGET_SECTION_ID)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SUCCESS"));

            mockMvc.perform(post("/api/v1/test/sections/" + TARGET_SECTION_ID + "/grades")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SUCCESS"));
        }

        @Test
        @DisplayName("LECTURER ngoài phạm vi (Scope rỗng và không thuộc lecturerIds) -> READ (403) và WRITE (403)")
        void lecturer_Unassigned_Forbidden() throws Exception {
            String token = createUserAndGetToken("test_sec_lecturer_unassigned", Role.LECTURER, new UserScope());

            mockMvc.perform(get("/api/v1/test/sections/" + TARGET_SECTION_ID)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));

            mockMvc.perform(post("/api/v1/test/sections/" + TARGET_SECTION_ID + "/grades")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }

        @ParameterizedTest(name = "Vai trò quản lý {0}: READ trong phạm vi (200 OK), WRITE bị cấm (403 FORBIDDEN)")
        @CsvSource({
                "PROGRAM_MANAGER, test_sec_pm",
                "DEPARTMENT_HEAD, test_sec_dh",
                "QA_OFFICER, test_sec_qa"
        })
        void managementRoles_ReadOk_WriteForbidden(Role role, String username) throws Exception {
            UserScope scope = UserScope.builder().courseIds(List.of(TARGET_COURSE_ID)).build();
            String token = createUserAndGetToken(username, role, scope);

            // READ: Cho phép 200 OK vì courseId khớp scope
            mockMvc.perform(get("/api/v1/test/sections/" + TARGET_SECTION_ID)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SUCCESS"));

            // WRITE: Tuyệt đối bị cấm (403 FORBIDDEN)
            mockMvc.perform(post("/api/v1/test/sections/" + TARGET_SECTION_ID + "/grades")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("VIEWER (Scope rỗng nghiêm ngặt): READ toàn hệ thống (200 OK), WRITE bị cấm (403 FORBIDDEN)")
        void viewer_StrictEmptyScope_ReadOk_WriteForbidden() throws Exception {
            String token = createUserAndGetToken("test_sec_viewer_empty", Role.VIEWER, null);

            mockMvc.perform(get("/api/v1/test/sections/" + TARGET_SECTION_ID)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SUCCESS"));

            mockMvc.perform(post("/api/v1/test/sections/" + TARGET_SECTION_ID + "/grades")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("VIEWER (Có scope khớp): READ cho phép (200 OK), WRITE bị cấm (403 FORBIDDEN)")
        void viewer_ScopedMatching_ReadOk_WriteForbidden() throws Exception {
            UserScope scope = UserScope.builder().sectionIds(List.of(TARGET_SECTION_ID)).build();
            String token = createUserAndGetToken("test_sec_viewer_scoped", Role.VIEWER, scope);

            mockMvc.perform(get("/api/v1/test/sections/" + TARGET_SECTION_ID)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/api/v1/test/sections/" + TARGET_SECTION_ID + "/grades")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("ĐIỂM BIÊN: VIEWER chỉ có programIds -> Scope KHÔNG rỗng nhưng ko khớp -> READ bị 403, WRITE bị 403")
        void viewer_ProgramOnlyScope_Forbidden() throws Exception {
            UserScope scope = UserScope.builder().programIds(List.of("671dcba5f123456789abc777")).build();
            String token = createUserAndGetToken("test_sec_viewer_prog_only", Role.VIEWER, scope);

            mockMvc.perform(get("/api/v1/test/sections/" + TARGET_SECTION_ID)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));

            mockMvc.perform(post("/api/v1/test/sections/" + TARGET_SECTION_ID + "/grades")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("STUDENT: Bị từ chối 403 trên cả READ và WRITE (chờ story NEW-03)")
        void student_ReadAndWrite_Forbidden() throws Exception {
            String token = createUserAndGetToken("test_sec_student", Role.STUDENT, null);

            mockMvc.perform(get("/api/v1/test/sections/" + TARGET_SECTION_ID)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));

            mockMvc.perform(post("/api/v1/test/sections/" + TARGET_SECTION_ID + "/grades")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
    }

    // =========================================================================
    // 3. NẠP LẠI QUYỀN ĐỘNG TỪ CSDL MỖI REQUEST (DYNAMIC PERMISSION RELOAD)
    // =========================================================================

    @Nested
    @DisplayName("3. Dynamic Permission Reload Tests (ADR 0003 / QĐ-1)")
    class DynamicPermissionReloadTests {

        @Test
        @DisplayName("Cập nhật vai trò/scope trong MongoDB -> Request kế tiếp với JWT token cũ sử dụng ngay quyền mới mà không cần cấp lại token")
        void dynamicPermissionReload_ImmediateEffectWithSameToken() throws Exception {
            String dynamicUsername = "test_sec_dynamic_user";

            // Bước 1: Tạo tài khoản với vai trò ban đầu là STUDENT (không có quyền xem lớp)
            User user = userRepository.save(User.builder()
                    .username(dynamicUsername)
                    .email("dynamic_user@hcmute.edu.vn")
                    .passwordHash(passwordEncoder.encode(COMMON_PASSWORD))
                    .fullName("[DỮ LIỆU THỬ NGHIỆM] Người dùng kiểm thử quyền động")
                    .status(UserStatus.ACTIVE)
                    .tokenVersion(0L)
                    .roleAssignments(List.of(
                            RoleAssignment.builder().role(Role.STUDENT).scope(null).build()
                    ))
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build());

            // Đăng nhập một lần duy nhất để lấy Access Token
            String token = loginAndGetToken(dynamicUsername, COMMON_PASSWORD);

            // Giai đoạn 1: Với vai trò STUDENT, cả READ và WRITE đều bị từ chối 403
            mockMvc.perform(get("/api/v1/test/sections/" + TARGET_SECTION_ID)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));

            mockMvc.perform(post("/api/v1/test/sections/" + TARGET_SECTION_ID + "/grades")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));

            // Bước 2: Quản trị viên cập nhật CSDL gán thêm vai trò LECTURER phụ trách TARGET_SECTION_ID
            user.setRoleAssignments(List.of(
                    RoleAssignment.builder()
                            .role(Role.LECTURER)
                            .scope(UserScope.builder().sectionIds(List.of(TARGET_SECTION_ID)).build())
                            .build()
            ));
            user.setUpdatedAt(Instant.now());
            userRepository.save(user);

            // Giai đoạn 2: Gọi lại API bằng CHÍNH TOKEN CŨ (không login lại), hệ thống nạp lại UserPrincipal từ DB
            // -> Lập tức có quyền READ (200 OK) và WRITE (200 OK)
            mockMvc.perform(get("/api/v1/test/sections/" + TARGET_SECTION_ID)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SUCCESS"))
                    .andExpect(jsonPath("$.message").value("READ access granted"));

            mockMvc.perform(post("/api/v1/test/sections/" + TARGET_SECTION_ID + "/grades")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SUCCESS"))
                    .andExpect(jsonPath("$.message").value("WRITE access granted"));

            // Bước 3: Quản trị viên hạ quyền người dùng xuống VIEWER (scope rỗng)
            user.setRoleAssignments(List.of(
                    RoleAssignment.builder().role(Role.VIEWER).scope(null).build()
            ));
            user.setUpdatedAt(Instant.now());
            userRepository.save(user);

            // Giai đoạn 3: Với CÙNG TOKEN ĐÓ:
            // - WRITE lập tức bị từ chối 403
            mockMvc.perform(post("/api/v1/test/sections/" + TARGET_SECTION_ID + "/grades")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));

            // - READ vẫn được phép 200 OK (vì VIEWER có scope rỗng đọc toàn hệ thống)
            mockMvc.perform(get("/api/v1/test/sections/" + TARGET_SECTION_ID)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SUCCESS"));
        }
    }
}
