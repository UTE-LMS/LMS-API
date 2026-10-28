package com.hcmute.lms.lmsapi.controller;

import com.hcmute.lms.lmsapi.enums.AuditAction;
import com.hcmute.lms.lmsapi.document.AuditLog;
import com.hcmute.lms.lmsapi.enums.Role;
import com.hcmute.lms.lmsapi.document.RoleAssignment;
import com.hcmute.lms.lmsapi.document.User;
import com.hcmute.lms.lmsapi.document.UserScope;
import com.hcmute.lms.lmsapi.enums.UserStatus;
import com.hcmute.lms.lmsapi.dto.LoginRequest;
import com.hcmute.lms.lmsapi.repository.AuditLogRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm thử tích hợp toàn diện phân hệ Xác thực US-01 (F01 / ADR 0003).
 * Bao phủ các luồng:
 * - Đăng nhập thành công, ghi nhận kiểm toán LOGIN
 * - Chống Timing Attack và kiểm tra tài khoản LOCKED
 * - Bỏ qua Bearer token hết hạn trên permitAll endpoints
 * - Đăng xuất vô hiệu hóa tức thời qua tokenVersion
 * - Lấy thông tin cá nhân /api/v1/auth/me
 * - Cấu hình CORS và các endpoint bảo vệ
 */
@SpringBootTest
@DisplayName("US-01: Authentication Subsystem Integration Tests (ADR 0003)")
class AuthIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    private User testActiveUser;
    private User testLockedUser;

    private static final String ACTIVE_USERNAME = "test_auth_active";
    private static final String LOCKED_USERNAME = "test_auth_locked";
    private static final String RAW_PASSWORD = "Password123!";

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        cleanupData();

        // 1. Tạo người dùng ACTIVE thử nghiệm
        testActiveUser = userRepository.save(User.builder()
                .username(ACTIVE_USERNAME)
                .email("test_active@hcmute.edu.vn")
                .passwordHash(passwordEncoder.encode(RAW_PASSWORD))
                .fullName("[DỮ LIỆU THỬ NGHIỆM] Giảng viên kiểm thử")
                .status(UserStatus.ACTIVE)
                .tokenVersion(0L)
                .roleAssignments(List.of(
                        RoleAssignment.builder()
                                .role(Role.LECTURER)
                                .scope(UserScope.builder()
                                        .sectionIds(List.of("671dcba5f123456789abcdef"))
                                        .build())
                                .build()
                ))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());

        // 2. Tạo người dùng LOCKED thử nghiệm
        testLockedUser = userRepository.save(User.builder()
                .username(LOCKED_USERNAME)
                .email("test_locked@hcmute.edu.vn")
                .passwordHash(passwordEncoder.encode(RAW_PASSWORD))
                .fullName("[DỮ LIỆU THỬ NGHIỆM] Tài khoản bị khóa")
                .status(UserStatus.LOCKED)
                .tokenVersion(0L)
                .roleAssignments(List.of(
                        RoleAssignment.builder().role(Role.STUDENT).build()
                ))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());
    }

    @AfterEach
    void tearDown() {
        cleanupData();
    }

    private void cleanupData() {
        userRepository.findByUsername(ACTIVE_USERNAME).ifPresent(userRepository::delete);
        userRepository.findByUsername(LOCKED_USERNAME).ifPresent(userRepository::delete);
        userRepository.findByUsername("test_auth_vietnamese").ifPresent(userRepository::delete);
        if (testActiveUser != null && testActiveUser.getId() != null) {
            List<AuditLog> logs = auditLogRepository.findByEntityCollectionAndEntityIdOrderByAtDesc("users", testActiveUser.getId());
            auditLogRepository.deleteAll(logs);
        }
    }

    // =========================================================================
    // 1. KIỂM THỬ ĐĂNG NHẬP (POST /api/v1/auth/login)
    // =========================================================================

    @Nested
    @DisplayName("1. POST /api/v1/auth/login")
    class LoginTests {

        @Test
        @DisplayName("Đăng nhập thành công với tài khoản ACTIVE: trả về HTTP 200, JWT token và ghi nhận audit log LOGIN")
        void login_Success() throws Exception {
            LoginRequest request = new LoginRequest(ACTIVE_USERNAME, RAW_PASSWORD);

            MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.expiresIn").value(3600))
                    .andExpect(jsonPath("$.user.id").value(testActiveUser.getId()))
                    .andExpect(jsonPath("$.user.username").value(ACTIVE_USERNAME))
                    .andExpect(jsonPath("$.user.email").value("test_active@hcmute.edu.vn"))
                    .andExpect(jsonPath("$.user.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.user.roleAssignments[0].role").value("LECTURER"))
                    .andReturn();

            String token = JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
            assertThat(token).isNotBlank();

            // Xác minh bản ghi audit log LOGIN được ghi nhận vào CSDL
            List<AuditLog> auditLogs = auditLogRepository.findByEntityCollectionAndEntityIdOrderByAtDesc("users", testActiveUser.getId());
            assertThat(auditLogs).isNotEmpty();
            AuditLog loginAudit = auditLogs.stream()
                    .filter(a -> a.getAction() == AuditAction.LOGIN)
                    .findFirst()
                    .orElse(null);
            assertThat(loginAudit).isNotNull();
            assertThat(loginAudit.getEntity().getCollection()).isEqualTo("users");
            assertThat(loginAudit.getEntity().getId()).isEqualTo(testActiveUser.getId());
        }

        @Test
        @DisplayName("Bỏ qua kiểm tra Bearer token trên login: Client gửi kèm token cũ hết hạn/sai vẫn đăng nhập bình thường (HTTP 200)")
        void login_WithExpiredOrInvalidBearerTokenInHeader_ShouldSucceed() throws Exception {
            LoginRequest request = new LoginRequest(ACTIVE_USERNAME, RAW_PASSWORD);

            // Gửi kèm token giả định hoặc token đã hết hạn trên Authorization header
            String expiredToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE1MTYyMzkwMjIsImsubiI6IjEyMzQ1In0.invalid_signature";

            mockMvc.perform(post("/api/v1/auth/login")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.user.username").value(ACTIVE_USERNAME));
        }

        @Test
        @DisplayName("Đăng nhập với username không tồn tại: trả về HTTP 401 với thông điệp chung (chống timing attack)")
        void login_NonExistentUser_Returns401() throws Exception {
            LoginRequest request = new LoginRequest("user_not_exist_at_all", RAW_PASSWORD);

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.message").value("Tên đăng nhập hoặc mật khẩu không chính xác"))
                    .andExpect(jsonPath("$.path").value("/api/v1/auth/login"));
        }

        @Test
        @DisplayName("Đăng nhập sai mật khẩu: trả về HTTP 401 với thông điệp chung")
        void login_WrongPassword_Returns401() throws Exception {
            LoginRequest request = new LoginRequest(ACTIVE_USERNAME, "WrongPass123!");

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.message").value("Tên đăng nhập hoặc mật khẩu không chính xác"));
        }

        @Test
        @DisplayName("Tài khoản ở trạng thái LOCKED: trả về HTTP 401 với cùng thông điệp chung như sai mật khẩu")
        void login_LockedUser_Returns401WithGenericMessage() throws Exception {
            LoginRequest request = new LoginRequest(LOCKED_USERNAME, RAW_PASSWORD);

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.message").value("Tên đăng nhập hoặc mật khẩu không chính xác"));
        }

        @Test
        @DisplayName("Validation lỗi: Mật khẩu nhỏ hơn 8 bytes UTF-8 bị từ chối với HTTP 400 VALIDATION_ERROR")
        void login_PasswordShorterThan8Bytes_Returns400() throws Exception {
            LoginRequest request = new LoginRequest(ACTIVE_USERNAME, "short");

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("password"));
        }

        @Test
        @DisplayName("Validation lỗi: Mật khẩu vượt quá 72 bytes UTF-8 bị từ chối với HTTP 400 VALIDATION_ERROR")
        void login_PasswordExceeding72Bytes_Returns400() throws Exception {
            // Chuỗi 73 ký tự ASCII = 73 bytes
            String longPassword = "A".repeat(73);
            LoginRequest request = new LoginRequest(ACTIVE_USERNAME, longPassword);

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("password"));
        }

        @Test
        @DisplayName("Mật khẩu tiếng Việt có dấu trong khoảng 8-72 bytes UTF-8: Đăng nhập thành công bình thường")
        void login_VietnameseAccentedPassword_Success() throws Exception {
            String vietnamesePass = "MậtKhẩu123!"; // 15 bytes UTF-8
            userRepository.save(User.builder()
                    .username("test_auth_vietnamese")
                    .email("vietnamese@hcmute.edu.vn")
                    .passwordHash(passwordEncoder.encode(vietnamesePass))
                    .status(UserStatus.ACTIVE)
                    .build());

            LoginRequest request = new LoginRequest("test_auth_vietnamese", vietnamesePass);

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty());
        }

        @Test
        @DisplayName("Validation lỗi: Thiếu username hoặc password trả về HTTP 400")
        void login_MissingFields_Returns400() throws Exception {
            LoginRequest request = new LoginRequest("", "");

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
    }

    // =========================================================================
    // 2. KIỂM THỬ ĐĂNG XUẤT & THU HỒI PHIÊN (POST /api/v1/auth/logout)
    // =========================================================================

    @Nested
    @DisplayName("2. POST /api/v1/auth/logout & Token Invalidation")
    class LogoutTests {

        @Test
        @DisplayName("Đăng xuất không có Bearer token: trả về HTTP 401 UNAUTHORIZED")
        void logout_WithoutToken_Returns401() throws Exception {
            mockMvc.perform(post("/api/v1/auth/logout"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("Đăng xuất thành công: tăng tokenVersion trong CSDL và vô hiệu hóa tức thời token cũ ở request kế tiếp")
        void logout_Success_IncrementsTokenVersion_AndInvalidatesToken() throws Exception {
            // 1. Đăng nhập để lấy token hợp lệ
            String token = obtainAccessToken(ACTIVE_USERNAME, RAW_PASSWORD);

            // 2. Gọi GET /api/v1/auth/me với token này -> thành công 200 OK
            mockMvc.perform(get("/api/v1/auth/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value(ACTIVE_USERNAME));

            // 3. Thực hiện đăng xuất POST /api/v1/auth/logout
            mockMvc.perform(post("/api/v1/auth/logout")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Đăng xuất thành công"));

            // 4. Kiểm tra tokenVersion trong MongoDB đã được tăng nguyên tử lên 1
            User updatedUser = userRepository.findById(testActiveUser.getId()).orElseThrow();
            assertThat(updatedUser.getTokenVersion()).isEqualTo(1L);

            // 5. Thử gọi lại GET /api/v1/auth/me với token cũ -> Bị từ chối ngay lập tức với HTTP 401!
            mockMvc.perform(get("/api/v1/auth/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.message").value("Yêu cầu xác thực để truy cập tài nguyên này"));
        }
    }

    // =========================================================================
    // 3. KIỂM THỬ LẤY THÔNG TIN CÁ NHÂN (GET /api/v1/auth/me)
    // =========================================================================

    @Nested
    @DisplayName("3. GET /api/v1/auth/me & Dynamic Authorization")
    class MeEndpointTests {

        @Test
        @DisplayName("Lấy thông tin cá nhân thành công với Bearer token hợp lệ")
        void me_Success() throws Exception {
            String token = obtainAccessToken(ACTIVE_USERNAME, RAW_PASSWORD);

            mockMvc.perform(get("/api/v1/auth/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(testActiveUser.getId()))
                    .andExpect(jsonPath("$.username").value(ACTIVE_USERNAME))
                    .andExpect(jsonPath("$.email").value("test_active@hcmute.edu.vn"))
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.roleAssignments[0].role").value("LECTURER"))
                    .andExpect(jsonPath("$.roleAssignments[0].scope.sectionIds[0]").value("671dcba5f123456789abcdef"));
        }

        @Test
        @DisplayName("Tài khoản bị khóa sau khi đã cấp token: Token lập tức bị từ chối ở request tiếp theo (HTTP 401)")
        void me_AccountLockedAfterTokenIssued_Returns401() throws Exception {
            String token = obtainAccessToken(ACTIVE_USERNAME, RAW_PASSWORD);

            // Quản trị viên khóa tài khoản trong CSDL
            testActiveUser.setStatus(UserStatus.LOCKED);
            userRepository.save(testActiveUser);

            // Request tiếp theo mang token cũ bị từ chối 401 do AuthSessionService kiểm tra status tươi từ DB
            mockMvc.perform(get("/api/v1/auth/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.message").value("Yêu cầu xác thực để truy cập tài nguyên này"));
        }

        @Test
        @DisplayName("Token giả mạo hoặc sai chữ ký: trả về HTTP 401 UNAUTHORIZED")
        void me_ForgedToken_Returns401() throws Exception {
            String forgedToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwidG9rZW5WZXJzaW9uIjowfQ.invalid_signature_here";

            mockMvc.perform(get("/api/v1/auth/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + forgedToken))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
    }

    // =========================================================================
    // 4. KIỂM THỬ PERMIT-ALL VS PROTECTED ENDPOINTS & CORS
    // =========================================================================

    @Nested
    @DisplayName("4. PermitAll vs Protected Endpoints & CORS Configuration")
    class SecurityConfigurationTests {

        @Test
        @DisplayName("PermitAll endpoints: /actuator/health, /actuator/info, /api/v1/auth/login không yêu cầu token")
        void permitAllEndpoints_AccessibleWithoutToken() throws Exception {
            mockMvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/actuator/info"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Protected Actuator: /actuator/beans yêu cầu xác thực Bearer token (HTTP 401 khi không có token)")
        void protectedActuatorEndpoint_RequiresAuthentication() throws Exception {
            mockMvc.perform(get("/actuator/beans"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("CORS Preflight: Request OPTIONS /api/v1/auth/login trả về đúng CORS headers và HTTP 200")
        void corsPreflight_ReturnsAppropriateHeaders() throws Exception {
            mockMvc.perform(options("/api/v1/auth/login")
                            .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type,Authorization"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200"))
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "X-Correlation-Id"));
        }
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private String obtainAccessToken(String username, String password) throws Exception {
        LoginRequest request = new LoginRequest(username, password);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }
}
