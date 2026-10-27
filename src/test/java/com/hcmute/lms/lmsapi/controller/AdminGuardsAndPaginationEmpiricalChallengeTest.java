package com.hcmute.lms.lmsapi.controller;

import com.hcmute.lms.lmsapi.document.Role;
import com.hcmute.lms.lmsapi.document.RoleAssignment;
import com.hcmute.lms.lmsapi.document.User;
import com.hcmute.lms.lmsapi.document.UserStatus;
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
import java.util.Collections;
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
 * Empirical Challenge: Admin Guards and Pagination Boundaries (Challenger 1 - PR 4).
 * Rigorously challenges:
 * 1. Admin Guards via HTTP API:
 *    - Self-lock attempt -> HTTP 422 BUSINESS_RULE_VIOLATION.
 *    - Self-demotion attempt -> HTTP 422 BUSINESS_RULE_VIOLATION.
 *    - Non-admin attempts -> HTTP 403 FORBIDDEN.
 *    - Post-rejection state verification (user remains ACTIVE, token remains valid).
 * 2. Pagination Boundaries (GET /api/v1/users):
 *    - page = -1, page = -100 -> HTTP 400 VALIDATION_ERROR.
 *    - size = 0, size = -5 -> HTTP 400 VALIDATION_ERROR.
 *    - size = 101, size = 1000 -> HTTP 400 VALIDATION_ERROR.
 *    - Dual violations (e.g. page=-1 & size=0, page=-100 & size=1000) -> HTTP 400 VALIDATION_ERROR.
 *    - page = 0, size = 1 -> HTTP 200 OK.
 *    - page = 0, size = 100 -> HTTP 200 OK.
 *    - Defaults (no params) -> HTTP 200 OK (page=0, size=20).
 *    - Valid large page (page=9999, size=50) -> HTTP 200 OK with empty content.
 */
@SpringBootTest
@DisplayName("Empirical Challenge: Admin Guards & Pagination Boundaries Integration (PR 4 / US-02)")
public class AdminGuardsAndPaginationEmpiricalChallengeTest {

    private static final String FIXTURE_PREFIX = "challenger_p4_";
    private static final String FIXTURE_LABEL = "[DỮ LIỆU THỬ NGHIỆM] ";
    private static final String RAW_PASSWORD = "ChallengePassword123!";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    private final List<String> createdUsernames = Collections.synchronizedList(new ArrayList<>());

    private User primaryAdmin;
    private User secondaryAdmin;
    private User targetLecturer;

    private String primaryAdminToken;
    private String lecturerToken;

    @BeforeEach
    void setUp() throws Exception {
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        cleanupData();

        primaryAdmin = createTestUser("admin1", Role.ADMIN, UserStatus.ACTIVE);
        secondaryAdmin = createTestUser("admin2", Role.ADMIN, UserStatus.ACTIVE);
        targetLecturer = createTestUser("lecturer", Role.LECTURER, UserStatus.ACTIVE);

        primaryAdminToken = obtainAccessToken(primaryAdmin.getUsername(), RAW_PASSWORD);
        lecturerToken = obtainAccessToken(targetLecturer.getUsername(), RAW_PASSWORD);
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
    }

    private User createTestUser(String suffix, Role role, UserStatus status) {
        String username = FIXTURE_PREFIX + suffix;
        createdUsernames.add(username);
        userRepository.findByUsername(username).ifPresent(userRepository::delete);

        User user = User.builder()
                .username(username)
                .email(username + "@hcmute.edu.vn")
                .passwordHash(passwordEncoder.encode(RAW_PASSWORD))
                .fullName(FIXTURE_LABEL + "User " + suffix)
                .status(status)
                .tokenVersion(0L)
                .roleAssignments(List.of(RoleAssignment.builder().role(role).build()))
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
    // 1. EMPIRICAL CHALLENGE: ADMIN GUARDS (HTTP 422)
    // =========================================================================
    @Nested
    @DisplayName("1. Admin Guards Empirical Challenge")
    class AdminGuardsChallenge {

        @Test
        @DisplayName("Challenge 1.1: Admin self-lock attempt returns HTTP 422 BUSINESS_RULE_VIOLATION")
        void adminSelfLock_MustReturn422() throws Exception {
            UpdateUserStatusRequest lockRequest = new UpdateUserStatusRequest(UserStatus.LOCKED);

            mockMvc.perform(put("/api/v1/users/" + primaryAdmin.getId() + "/status")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + primaryAdminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(lockRequest)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.status").value(422))
                    .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                    .andExpect(jsonPath("$.message").value(containsString("tự khóa tài khoản của chính mình")));

            // Kiểm chứng thực nghiệm: Trạng thái trong DB vẫn là ACTIVE
            User reloadedAdmin = userRepository.findById(primaryAdmin.getId()).orElseThrow();
            assertThat(reloadedAdmin.getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(reloadedAdmin.getTokenVersion()).isEqualTo(0L);

            // Kiểm chứng thực nghiệm: Phiên làm việc của Admin vẫn hợp lệ
            mockMvc.perform(get("/api/v1/auth/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + primaryAdminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value(primaryAdmin.getUsername()));
        }

        @Test
        @DisplayName("Challenge 1.2: Admin self-demotion attempt returns HTTP 422 BUSINESS_RULE_VIOLATION")
        void adminSelfDemotion_MustReturn422() throws Exception {
            UpdateUserRequest demotionRequest = UpdateUserRequest.builder()
                    .email(primaryAdmin.getEmail())
                    .fullName("Admin Self Demoted")
                    .roleAssignments(List.of(RoleAssignment.builder().role(Role.LECTURER).build()))
                    .build();

            mockMvc.perform(put("/api/v1/users/" + primaryAdmin.getId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + primaryAdminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(demotionRequest)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.status").value(422))
                    .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                    .andExpect(jsonPath("$.message").value(containsString("tự gỡ bỏ vai trò ADMIN của chính mình")));

            // Kiểm chứng thực nghiệm: Vai trò trong DB vẫn giữ nguyên Role.ADMIN
            User reloadedAdmin = userRepository.findById(primaryAdmin.getId()).orElseThrow();
            boolean stillAdmin = reloadedAdmin.getRoleAssignments().stream()
                    .anyMatch(ra -> Role.ADMIN.equals(ra.getRole()));
            assertThat(stillAdmin).isTrue();
        }

        @Test
        @DisplayName("Challenge 1.3: Admin updates self while retaining ADMIN role returns HTTP 200 OK")
        void adminSelfUpdate_RetainingAdminRole_Returns200() throws Exception {
            UpdateUserRequest updateRequest = UpdateUserRequest.builder()
                    .email(primaryAdmin.getEmail())
                    .fullName("Updated Admin FullName")
                    .roleAssignments(List.of(
                            RoleAssignment.builder().role(Role.ADMIN).build(),
                            RoleAssignment.builder().role(Role.VIEWER).build()
                    ))
                    .build();

            mockMvc.perform(put("/api/v1/users/" + primaryAdmin.getId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + primaryAdminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.fullName").value("Updated Admin FullName"))
                    .andExpect(jsonPath("$.roleAssignments", hasSize(2)));

            User reloadedAdmin = userRepository.findById(primaryAdmin.getId()).orElseThrow();
            assertThat(reloadedAdmin.getFullName()).isEqualTo("Updated Admin FullName");
        }

        @Test
        @DisplayName("Challenge 1.4: Non-admin calling lock on target returns HTTP 403 FORBIDDEN")
        void nonAdmin_AttemptLock_Returns403() throws Exception {
            UpdateUserStatusRequest lockRequest = new UpdateUserStatusRequest(UserStatus.LOCKED);

            mockMvc.perform(put("/api/v1/users/" + targetLecturer.getId() + "/status")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + lecturerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(lockRequest)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
    }

    // =========================================================================
    // 2. EMPIRICAL CHALLENGE: PAGINATION BOUNDARIES (HTTP 400 & HTTP 200)
    // =========================================================================
    @Nested
    @DisplayName("2. Pagination Boundaries Empirical Challenge")
    class PaginationBoundariesChallenge {

        @ParameterizedTest(name = "GET /api/v1/users?page={0}&size=20 must return HTTP 400 VALIDATION_ERROR")
        @CsvSource({
                "-1",
                "-100"
        })
        @DisplayName("Challenge 2.1: page = -1, page = -100 out of bounds -> HTTP 400 VALIDATION_ERROR")
        void pageNegative_MustReturn400(String page) throws Exception {
            mockMvc.perform(get("/api/v1/users?page=" + page + "&size=20")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + primaryAdminToken))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("page"));
        }

        @ParameterizedTest(name = "GET /api/v1/users?page=0&size={0} must return HTTP 400 VALIDATION_ERROR")
        @CsvSource({
                "0",
                "-5"
        })
        @DisplayName("Challenge 2.2: size = 0, size = -5 out of lower bound -> HTTP 400 VALIDATION_ERROR")
        void sizeLowerOutOfBounds_MustReturn400(String size) throws Exception {
            mockMvc.perform(get("/api/v1/users?page=0&size=" + size)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + primaryAdminToken))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("size"));
        }

        @ParameterizedTest(name = "GET /api/v1/users?page=0&size={0} must return HTTP 400 VALIDATION_ERROR")
        @CsvSource({
                "101",
                "1000"
        })
        @DisplayName("Challenge 2.3: size = 101, size = 1000 out of upper bound -> HTTP 400 VALIDATION_ERROR")
        void sizeUpperOutOfBounds_MustReturn400(String size) throws Exception {
            mockMvc.perform(get("/api/v1/users?page=0&size=" + size)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + primaryAdminToken))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("size"));
        }

        @ParameterizedTest(name = "GET /api/v1/users?page={0}&size={1} must return HTTP 400 with 2 field errors")
        @CsvSource({
                "-1, 0",
                "-100, 1000"
        })
        @DisplayName("Challenge 2.4: Both page and size invalid -> HTTP 400 VALIDATION_ERROR reporting both fields")
        void dualBoundaryViolation_MustReturn400WithBothFields(String page, String size) throws Exception {
            mockMvc.perform(get("/api/v1/users?page=" + page + "&size=" + size)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + primaryAdminToken))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.fieldErrors", hasSize(2)));
        }

        @Test
        @DisplayName("Challenge 2.5: page = 0, size = 1 lower boundary -> HTTP 200 OK with valid envelope")
        void boundarySize1_MustReturn200() throws Exception {
            mockMvc.perform(get("/api/v1/users?page=0&size=1")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + primaryAdminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(1))
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.totalElements").isNumber())
                    .andExpect(jsonPath("$.totalPages").isNumber())
                    .andExpect(jsonPath("$.last").isBoolean());
        }

        @Test
        @DisplayName("Challenge 2.6: page = 0, size = 100 upper boundary -> HTTP 200 OK with valid envelope")
        void boundarySize100_MustReturn200() throws Exception {
            mockMvc.perform(get("/api/v1/users?page=0&size=100")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + primaryAdminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(100))
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.totalElements").isNumber())
                    .andExpect(jsonPath("$.totalPages").isNumber())
                    .andExpect(jsonPath("$.last").isBoolean());
        }

        @Test
        @DisplayName("Challenge 2.7: Default pagination parameters (no query params) -> HTTP 200 OK (page=0, size=20)")
        void defaultPagination_MustReturn200() throws Exception {
            mockMvc.perform(get("/api/v1/users")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + primaryAdminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(20))
                    .andExpect(jsonPath("$.content").isArray());
        }

        @Test
        @DisplayName("Challenge 2.8: High page number within size bound (page = 9999, size = 50) -> HTTP 200 OK, empty content, last=true")
        void highPageNumber_MustReturn200WithEmptyContent() throws Exception {
            mockMvc.perform(get("/api/v1/users?page=9999&size=50")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + primaryAdminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page").value(9999))
                    .andExpect(jsonPath("$.size").value(50))
                    .andExpect(jsonPath("$.content", hasSize(0)))
                    .andExpect(jsonPath("$.last").value(true));
        }
    }
}
