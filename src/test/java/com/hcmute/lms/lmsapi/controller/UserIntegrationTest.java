package com.hcmute.lms.lmsapi.controller;

import com.hcmute.lms.lmsapi.document.Role;
import com.hcmute.lms.lmsapi.document.RoleAssignment;
import com.hcmute.lms.lmsapi.document.User;
import com.hcmute.lms.lmsapi.document.UserScope;
import com.hcmute.lms.lmsapi.document.UserStatus;
import com.hcmute.lms.lmsapi.dto.CreateUserRequest;
import com.hcmute.lms.lmsapi.dto.LoginRequest;
import com.hcmute.lms.lmsapi.dto.UpdateUserRequest;
import com.hcmute.lms.lmsapi.dto.UpdateUserStatusRequest;
import com.hcmute.lms.lmsapi.repository.UserRepository;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm thử tích hợp toàn diện phân hệ Quản lý Người dùng US-02 (F01 / ADR 0003).
 * Bao phủ các luồng:
 * - Phân quyền vai trò: chỉ ADMIN được truy cập, người dùng khác nhận HTTP 403, chưa xác thực nhận 401
 * - Kiểm tra phân trang chuẩn (400 VALIDATION_ERROR khi page < 0 hoặc size ngoài [1, 100])
 * - CRUD người dùng: tạo mới, trùng username/email nhận 409 CONFLICT, username bất biến
 * - Kiểm tra tham chiếu scope (HTTP 422 khi ID không tồn tại)
 * - ADMIN Guards: Chặn tự khóa và tự hạ quyền (HTTP 422)
 * - Thu hồi tức thời và vĩnh viễn token khi khóa tài khoản (LOCKED -> 401, mở khóa lại -> vẫn 401)
 */
@SpringBootTest
@DisplayName("US-02: User Management Subsystem Integration Tests (ADR 0003)")
class UserIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    private User adminUser;
    private User lecturerUser;
    private User targetUser;

    private static final String ADMIN_USERNAME = "test_user_admin";
    private static final String LECTURER_USERNAME = "test_user_lecturer";
    private static final String TARGET_USERNAME = "test_user_target";
    private static final String RAW_PASSWORD = "Password123!";

    private String adminToken;
    private String lecturerToken;

    @BeforeEach
    void setUp() throws Exception {
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        cleanupData();

        // 1. Tạo tài khoản ADMIN
        adminUser = userRepository.save(User.builder()
                .username(ADMIN_USERNAME)
                .email("admin_user@hcmute.edu.vn")
                .passwordHash(passwordEncoder.encode(RAW_PASSWORD))
                .fullName("[DỮ LIỆU THỬ NGHIỆM] Quản Trị Viên")
                .status(UserStatus.ACTIVE)
                .tokenVersion(0L)
                .roleAssignments(List.of(RoleAssignment.builder().role(Role.ADMIN).build()))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());

        // 2. Tạo tài khoản LECTURER (non-admin)
        lecturerUser = userRepository.save(User.builder()
                .username(LECTURER_USERNAME)
                .email("lecturer_user@hcmute.edu.vn")
                .passwordHash(passwordEncoder.encode(RAW_PASSWORD))
                .fullName("[DỮ LIỆU THỬ NGHIỆM] Giảng Viên")
                .status(UserStatus.ACTIVE)
                .tokenVersion(0L)
                .roleAssignments(List.of(RoleAssignment.builder().role(Role.LECTURER).build()))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());

        // 3. Tạo tài khoản TARGET dùng cho kiểm thử cập nhật/khóa
        targetUser = userRepository.save(User.builder()
                .username(TARGET_USERNAME)
                .email("target_user@hcmute.edu.vn")
                .passwordHash(passwordEncoder.encode(RAW_PASSWORD))
                .fullName("[DỮ LIỆU THỬ NGHIỆM] Người Dùng Mục Tiêu")
                .status(UserStatus.ACTIVE)
                .tokenVersion(0L)
                .roleAssignments(List.of(RoleAssignment.builder().role(Role.LECTURER).build()))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());

        // Lấy access token cho ADMIN và LECTURER
        adminToken = obtainAccessToken(ADMIN_USERNAME, RAW_PASSWORD);
        lecturerToken = obtainAccessToken(LECTURER_USERNAME, RAW_PASSWORD);
    }

    @AfterEach
    void tearDown() {
        cleanupData();
    }

    private void cleanupData() {
        userRepository.findByUsername(ADMIN_USERNAME).ifPresent(userRepository::delete);
        userRepository.findByUsername(LECTURER_USERNAME).ifPresent(userRepository::delete);
        userRepository.findByUsername(TARGET_USERNAME).ifPresent(userRepository::delete);
        userRepository.findByUsername("created_test_user").ifPresent(userRepository::delete);
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
    // 1. KIỂM THỬ PHÂN QUYỀN VAI TRÒ (Role-based Authorization: HTTP 401 & 403)
    // =========================================================================

    @Nested
    @DisplayName("1. Role-Based Access Control (/api/v1/users/**)")
    class RoleAuthorizationTests {

        @Test
        @DisplayName("Chưa xác thực gọi /api/v1/users: trả về HTTP 401 UNAUTHORIZED")
        void unauthenticated_Returns401() throws Exception {
            mockMvc.perform(get("/api/v1/users"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("Người dùng không phải ADMIN (LECTURER) gọi GET /api/v1/users: trả về HTTP 403 FORBIDDEN")
        void nonAdmin_GetUsers_Returns403() throws Exception {
            mockMvc.perform(get("/api/v1/users")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + lecturerToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("Người dùng không phải ADMIN gọi POST /api/v1/users: trả về HTTP 403 FORBIDDEN")
        void nonAdmin_CreateUser_Returns403() throws Exception {
            CreateUserRequest request = CreateUserRequest.builder()
                    .username("new_test_user")
                    .email("new_test@hcmute.edu.vn")
                    .password(RAW_PASSWORD)
                    .fullName("New Test User")
                    .roleAssignments(List.of(RoleAssignment.builder().role(Role.STUDENT).build()))
                    .build();

            mockMvc.perform(post("/api/v1/users")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + lecturerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("Người dùng không phải ADMIN gọi PUT /api/v1/users/{id}: trả về HTTP 403 FORBIDDEN")
        void nonAdmin_UpdateUser_Returns403() throws Exception {
            UpdateUserRequest request = UpdateUserRequest.builder()
                    .email("updated@hcmute.edu.vn")
                    .fullName("Updated Name")
                    .roleAssignments(List.of(RoleAssignment.builder().role(Role.LECTURER).build()))
                    .build();

            mockMvc.perform(put("/api/v1/users/" + targetUser.getId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + lecturerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("Người dùng không phải ADMIN gọi PUT /api/v1/users/{id}/status: trả về HTTP 403 FORBIDDEN")
        void nonAdmin_UpdateStatus_Returns403() throws Exception {
            UpdateUserStatusRequest request = new UpdateUserStatusRequest(UserStatus.LOCKED);

            mockMvc.perform(put("/api/v1/users/" + targetUser.getId() + "/status")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + lecturerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
    }

    // =========================================================================
    // 2. KIỂM THỬ THAM SỐ PHÂN TRANG (Pagination Validation: HTTP 400 VALIDATION_ERROR)
    // =========================================================================

    @Nested
    @DisplayName("2. Pagination Parameters Validation (GET /api/v1/users)")
    class PaginationValidationTests {

        @Test
        @DisplayName("Tham số page < 0: trả về HTTP 400 VALIDATION_ERROR kèm fieldErrors cho trường page")
        void pageNegative_Returns400() throws Exception {
            mockMvc.perform(get("/api/v1/users?page=-1&size=20")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("page"));
        }

        @Test
        @DisplayName("Tham số size < 1: trả về HTTP 400 VALIDATION_ERROR kèm fieldErrors cho trường size")
        void sizeZero_Returns400() throws Exception {
            mockMvc.perform(get("/api/v1/users?page=0&size=0")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("size"));
        }

        @Test
        @DisplayName("Tham số size > 100: trả về HTTP 400 VALIDATION_ERROR kèm fieldErrors cho trường size")
        void sizeExceeding100_Returns400() throws Exception {
            mockMvc.perform(get("/api/v1/users?page=0&size=101")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("size"));
        }

        @Test
        @DisplayName("Tham số phân trang hợp lệ: trả về HTTP 200 OK với đúng envelope PageResponse")
        void validPagination_Returns200WithEnvelope() throws Exception {
            mockMvc.perform(get("/api/v1/users?page=0&size=20")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(20))
                    .andExpect(jsonPath("$.totalElements").isNumber())
                    .andExpect(jsonPath("$.totalPages").isNumber())
                    .andExpect(jsonPath("$.last").isBoolean());
        }
    }

    // =========================================================================
    // 3. KIỂM THỬ CRUD NGƯỜI DÙNG & TÍNH BẤT BIẾN USERNAME
    // =========================================================================

    @Nested
    @DisplayName("3. User CRUD Operations & Immutability Rules")
    class UserCrudTests {

        @Test
        @DisplayName("Tạo người dùng thành công: trả về HTTP 201 Created và không lộ passwordHash hay tokenVersion")
        void createUser_Success() throws Exception {
            CreateUserRequest request = CreateUserRequest.builder()
                    .username("created_test_user")
                    .email("created_test@hcmute.edu.vn")
                    .password(RAW_PASSWORD)
                    .fullName("[DỮ LIỆU THỬ NGHIỆM] Người dùng mới tạo")
                    .roleAssignments(List.of(RoleAssignment.builder().role(Role.LECTURER).build()))
                    .build();

            mockMvc.perform(post("/api/v1/users")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNotEmpty())
                    .andExpect(jsonPath("$.username").value("created_test_user"))
                    .andExpect(jsonPath("$.email").value("created_test@hcmute.edu.vn"))
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.passwordHash").doesNotExist())
                    .andExpect(jsonPath("$.tokenVersion").doesNotExist());
        }

        @Test
        @DisplayName("Tạo người dùng trùng username: trả về HTTP 409 CONFLICT")
        void createUser_DuplicateUsername_Returns409() throws Exception {
            CreateUserRequest request = CreateUserRequest.builder()
                    .username(TARGET_USERNAME) // đã tồn tại
                    .email("unique_email@hcmute.edu.vn")
                    .password(RAW_PASSWORD)
                    .fullName("Duplicate User")
                    .roleAssignments(List.of(RoleAssignment.builder().role(Role.STUDENT).build()))
                    .build();

            mockMvc.perform(post("/api/v1/users")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.code").value("CONFLICT"));
        }

        @Test
        @DisplayName("Tạo người dùng trùng email: trả về HTTP 409 CONFLICT")
        void createUser_DuplicateEmail_Returns409() throws Exception {
            CreateUserRequest request = CreateUserRequest.builder()
                    .username("unique_username")
                    .email("target_user@hcmute.edu.vn") // đã tồn tại
                    .password(RAW_PASSWORD)
                    .fullName("Duplicate Email User")
                    .roleAssignments(List.of(RoleAssignment.builder().role(Role.STUDENT).build()))
                    .build();

            mockMvc.perform(post("/api/v1/users")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.code").value("CONFLICT"));
        }

        @Test
        @DisplayName("Xem chi tiết người dùng: ID không tồn tại trả về HTTP 404 NOT_FOUND")
        void getUserById_NotFound_Returns404() throws Exception {
            mockMvc.perform(get("/api/v1/users/671dcba5f123456789ffffff")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }

        @Test
        @DisplayName("Xem chi tiết người dùng: ID sai định dạng hex trả về HTTP 400 VALIDATION_ERROR")
        void getUserById_InvalidId_Returns400() throws Exception {
            mockMvc.perform(get("/api/v1/users/invalid-id-123")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("Cập nhật thông tin: username không đổi (bất biến), email được cập nhật")
        void updateUser_EmailUpdated_UsernameUnchanged() throws Exception {
            UpdateUserRequest request = UpdateUserRequest.builder()
                    .email("target_new_email@hcmute.edu.vn")
                    .fullName("Tên Đã Cập Nhật")
                    .roleAssignments(List.of(RoleAssignment.builder().role(Role.LECTURER).build()))
                    .build();

            mockMvc.perform(put("/api/v1/users/" + targetUser.getId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value(TARGET_USERNAME)) // username giữ nguyên
                    .andExpect(jsonPath("$.email").value("target_new_email@hcmute.edu.vn"))
                    .andExpect(jsonPath("$.fullName").value("Tên Đã Cập Nhật"));
        }

        @Test
        @DisplayName("Scope chứa courseId không tồn tại trong CSDL: từ chối với HTTP 422 BUSINESS_RULE_VIOLATION")
        void createUser_NonExistentScopeId_Returns422() throws Exception {
            CreateUserRequest request = CreateUserRequest.builder()
                    .username("scope_test_user")
                    .email("scope_test@hcmute.edu.vn")
                    .password(RAW_PASSWORD)
                    .fullName("Scope User")
                    .roleAssignments(List.of(
                            RoleAssignment.builder()
                                    .role(Role.LECTURER)
                                    .scope(UserScope.builder()
                                            .courseIds(List.of("671dcba5f123456789abc999")) // ID không có trong collection courses
                                            .build())
                                    .build()
                    ))
                    .build();

            mockMvc.perform(post("/api/v1/users")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.status").value(422))
                    .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                    .andExpect(jsonPath("$.message").value(containsString("SCOPE_NOT_FOUND")));
        }
    }

    // =========================================================================
    // 4. KIỂM THỬ ADMIN GUARDS TRÊN ENDPOINT (HTTP 422)
    // =========================================================================

    @Nested
    @DisplayName("4. ADMIN Guards Protection Tests")
    class AdminGuardsTests {

        @Test
        @DisplayName("Guard 1: ADMIN tự khóa tài khoản của chính mình bị từ chối với HTTP 422")
        void adminSelfLock_Returns422() throws Exception {
            UpdateUserStatusRequest lockRequest = new UpdateUserStatusRequest(UserStatus.LOCKED);

            mockMvc.perform(put("/api/v1/users/" + adminUser.getId() + "/status")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(lockRequest)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.status").value(422))
                    .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                    .andExpect(jsonPath("$.message").value(containsString("tự khóa tài khoản của chính mình")));
        }

        @Test
        @DisplayName("Guard 2: ADMIN tự gỡ vai trò ADMIN của chính mình bị từ chối với HTTP 422")
        void adminSelfDemotion_Returns422() throws Exception {
            UpdateUserRequest demotionRequest = UpdateUserRequest.builder()
                    .email("admin_user@hcmute.edu.vn")
                    .fullName("Admin User")
                    .roleAssignments(List.of(RoleAssignment.builder().role(Role.LECTURER).build())) // gỡ ADMIN
                    .build();

            mockMvc.perform(put("/api/v1/users/" + adminUser.getId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(demotionRequest)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.status").value(422))
                    .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                    .andExpect(jsonPath("$.message").value(containsString("tự gỡ bỏ vai trò ADMIN của chính mình")));
        }
    }

    // =========================================================================
    // 5. KIỂM THỬ KHÓA TÀI KHOẢN VÀ VÔ HIỆU HÓA TOKEN TỨC THÌ (Atomic Invalidation)
    // =========================================================================

    @Nested
    @DisplayName("5. Account Lock & Immediate Token Revocation")
    class StatusLockAndTokenRevocationTests {

        @Test
        @DisplayName("Khóa tài khoản (LOCKED): token cũ lập tức bị từ chối 401; mở khóa lại ACTIVE token cũ vẫn bị 401 vĩnh viễn")
        void lockAccount_InvalidatesTokenImmediately_AndPermanently() throws Exception {
            // 1. Target user đăng nhập và nhận access token
            String targetToken = obtainAccessToken(TARGET_USERNAME, RAW_PASSWORD);

            // 2. Xác thực thành công với /api/v1/auth/me
            mockMvc.perform(get("/api/v1/auth/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + targetToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value(TARGET_USERNAME));

            // 3. ADMIN khóa tài khoản target user qua PUT /api/v1/users/{id}/status sang LOCKED
            UpdateUserStatusRequest lockRequest = new UpdateUserStatusRequest(UserStatus.LOCKED);
            mockMvc.perform(put("/api/v1/users/" + targetUser.getId() + "/status")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(lockRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("LOCKED"));

            // 4. Kiểm tra token cũ của target user lập tức bị từ chối với HTTP 401 UNAUTHORIZED!
            mockMvc.perform(get("/api/v1/auth/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + targetToken))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

            // 5. ADMIN mở khóa lại tài khoản target user sang ACTIVE
            UpdateUserStatusRequest unlockRequest = new UpdateUserStatusRequest(UserStatus.ACTIVE);
            mockMvc.perform(put("/api/v1/users/" + targetUser.getId() + "/status")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(unlockRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ACTIVE"));

            // 6. Token cũ cấp trước thời điểm khóa VẪN VĨNH VIỄN bị từ chối với HTTP 401 UNAUTHORIZED!
            mockMvc.perform(get("/api/v1/auth/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + targetToken))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
    }
}
