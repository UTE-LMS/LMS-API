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
import com.hcmute.lms.lmsapi.service.AuthSessionService;
import com.jayway.jsonpath.JsonPath;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Empirical Adversarial Challenge: User Immutability, Scope Reference Validation & Session Revocation (PR 4).
 * Stress-tests three critical dimensions:
 * 1. Username and Sensitive Fields Immutability:
 *    - Malicious PUT payloads injecting new username, password, passwordHash, tokenVersion, status, or id.
 *    - Direct DB assertions and authentication verification to prove state integrity.
 * 2. Scope Reference Validation (Nonexistent IDs -> HTTP 422):
 *    - Arbitrary nonexistent string and ObjectId identifiers in programIds, courseIds, and sectionIds.
 *    - Verification on both POST /api/v1/users and PUT /api/v1/users/{id}.
 *    - Positive control: valid existent records in MongoDB collections.
 * 3. Account Lock & Token Revocation Life-Cycle:
 *    - Immediate 401 rejection of active tokens upon lock (PUT .../status -> LOCKED).
 *    - Permanent invalidation: unlocking back to ACTIVE never reinstates old tokens.
 *    - Re-authentication lifecycle and monotonic tokenVersion tracking.
 */
@SpringBootTest
@DisplayName("Empirical Challenge: User Immutability, Scope Reference Validation & Session Revocation")
public class UserSecurityAndIntegrityEmpiricalChallengeTest {

    private static final String PREFIX = "ch_pr4_2_";
    private static final String ADMIN_USERNAME = PREFIX + "admin";
    private static final String TARGET_USERNAME = PREFIX + "target";
    private static final String CREATED_USERNAME = PREFIX + "created";
    private static final String RAW_PASSWORD = "ChallengePassword123!";

    private static final String TEST_PROG_ID = "671dcba5f123456789000011";
    private static final String TEST_COURSE_ID = "671dcba5f123456789000012";
    private static final String TEST_SECTION_ID = "671dcba5f123456789000013";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthSessionService authSessionService;

    private MockMvc mockMvc;

    private User adminUser;
    private User targetUser;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        cleanupData();

        // 1. Tạo ADMIN test user
        adminUser = userRepository.save(User.builder()
                .username(ADMIN_USERNAME)
                .email(PREFIX + "admin@hcmute.edu.vn")
                .passwordHash(passwordEncoder.encode(RAW_PASSWORD))
                .fullName("[DỮ LIỆU THỬ NGHIỆM] Challenger Admin")
                .status(UserStatus.ACTIVE)
                .tokenVersion(0L)
                .roleAssignments(List.of(RoleAssignment.builder().role(Role.ADMIN).build()))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());

        // 2. Tạo TARGET test user
        targetUser = userRepository.save(User.builder()
                .username(TARGET_USERNAME)
                .email(PREFIX + "target@hcmute.edu.vn")
                .passwordHash(passwordEncoder.encode(RAW_PASSWORD))
                .fullName("[DỮ LIỆU THỬ NGHIỆM] Challenger Target")
                .status(UserStatus.ACTIVE)
                .tokenVersion(0L)
                .roleAssignments(List.of(RoleAssignment.builder().role(Role.LECTURER).build()))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());

        // Lấy token quản trị
        adminToken = obtainAccessToken(ADMIN_USERNAME, RAW_PASSWORD);
    }

    @AfterEach
    void tearDown() {
        cleanupData();
    }

    private void cleanupData() {
        // Xóa sạch toàn bộ người dùng thử nghiệm theo tiền tố PREFIX
        mongoTemplate.remove(new Query(Criteria.where("username").regex("^" + PREFIX)), "users");
        mongoTemplate.remove(new Query(Criteria.where("email").regex("^" + PREFIX)), "users");

        // Xóa tài liệu scope thử nghiệm
        mongoTemplate.remove(new Query(new Criteria().orOperator(
                Criteria.where("_id").is(TEST_PROG_ID),
                Criteria.where("_id").is(new ObjectId(TEST_PROG_ID)),
                Criteria.where("code").is("PROG_CH_01")
        )), "programs");
        mongoTemplate.remove(new Query(new Criteria().orOperator(
                Criteria.where("_id").is(TEST_COURSE_ID),
                Criteria.where("_id").is(new ObjectId(TEST_COURSE_ID)),
                Criteria.where("code").is("COURSE_CH_01")
        )), "courses");
        mongoTemplate.remove(new Query(new Criteria().orOperator(
                Criteria.where("_id").is(TEST_SECTION_ID),
                Criteria.where("_id").is(new ObjectId(TEST_SECTION_ID)),
                Criteria.where("code").is("SEC_CH_01")
        )), "course_sections");
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
    // 1. THỬ THÁCH TÍNH BẤT BIẾN USERNAME & CÁC TRƯỜNG NHẠY CẢM
    // =========================================================================

    @Nested
    @DisplayName("1. Username and Sensitive Fields Immutability Adversarial Challenge")
    class UsernameAndFieldImmutabilityAdversarialChallengeTests {

        @Test
        @DisplayName("Cố tình truyền username mới trong PUT /api/v1/users/{id}: Username trong DB tuyệt đối không đổi")
        void whenAttackerSendsNewUsernameInPutBody_usernameInDbRemainsStrictlyUnchanged() throws Exception {
            String tamperedUsername = PREFIX + "tampered_username";
            String rawJsonWithTamperedUsername = """
                    {
                        "username": "%s",
                        "email": "%starget_updated@hcmute.edu.vn",
                        "fullName": "Victim Updated Name",
                        "roleAssignments": [
                            { "role": "LECTURER" }
                        ]
                    }
                    """.formatted(tamperedUsername, PREFIX);

            mockMvc.perform(put("/api/v1/users/" + targetUser.getId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(rawJsonWithTamperedUsername))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value(TARGET_USERNAME)) // Phải trả về username ban đầu
                    .andExpect(jsonPath("$.email").value(PREFIX + "target_updated@hcmute.edu.vn"))
                    .andExpect(jsonPath("$.fullName").value("Victim Updated Name"));

            // 1. Kiểm tra trực tiếp trong MongoDB: username vẫn là giá trị gốc
            User userInDb = userRepository.findById(targetUser.getId()).orElseThrow();
            assertThat(userInDb.getUsername()).isEqualTo(TARGET_USERNAME);
            assertThat(userInDb.getEmail()).isEqualTo(PREFIX + "target_updated@hcmute.edu.vn");

            // 2. Không có bất kỳ user nào trong DB mang username bị cố tình sửa
            assertThat(userRepository.findByUsername(tamperedUsername)).isEmpty();

            // 3. Đăng nhập bằng username ban đầu vẫn thành công
            String targetToken = obtainAccessToken(TARGET_USERNAME, RAW_PASSWORD);
            assertThat(targetToken).isNotEmpty();

            // 4. Cố tình đăng nhập bằng username đã thử sửa -> phải bị 401 UNAUTHORIZED
            LoginRequest badLogin = new LoginRequest(tamperedUsername, RAW_PASSWORD);
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(badLogin)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Cố tình truyền password hoặc passwordHash trong PUT /api/v1/users/{id}: Mật khẩu không bị ghi đè")
        void whenAttackerSendsPasswordAndHashInPutBody_passwordInDbRemainsStrictlyUnchanged() throws Exception {
            String originalHash = targetUser.getPasswordHash();
            String maliciousPassword = "MaliciousHackedPassword999!";
            String rawJsonWithPassword = """
                    {
                        "password": "%s",
                        "passwordHash": "$2a$10$maliciousFakeHashValueHere1234567890abcdef",
                        "email": "%starget_pw@hcmute.edu.vn",
                        "fullName": "Tamper PW Name",
                        "roleAssignments": [
                            { "role": "LECTURER" }
                        ]
                    }
                    """.formatted(maliciousPassword, PREFIX);

            mockMvc.perform(put("/api/v1/users/" + targetUser.getId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(rawJsonWithPassword))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.passwordHash").doesNotExist());

            // 1. Kiểm tra trong DB: passwordHash không bị thay đổi
            User userInDb = userRepository.findById(targetUser.getId()).orElseThrow();
            assertThat(userInDb.getPasswordHash()).isEqualTo(originalHash);

            // 2. Đăng nhập với mật khẩu cũ thành công
            String token = obtainAccessToken(TARGET_USERNAME, RAW_PASSWORD);
            assertThat(token).isNotEmpty();

            // 3. Đăng nhập với mật khẩu cố tình truyền vào body -> thất bại 401
            LoginRequest badLogin = new LoginRequest(TARGET_USERNAME, maliciousPassword);
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(badLogin)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Cố tình truyền status hoặc tokenVersion trong PUT /api/v1/users/{id}: Giá trị không bị sửa")
        void whenAttackerSendsStatusAndTokenVersionInPutBody_fieldsInDbRemainStrictlyUnchanged() throws Exception {
            String rawJsonWithStatusAndVersion = """
                    {
                        "status": "LOCKED",
                        "tokenVersion": 9999,
                        "email": "%starget_ver@hcmute.edu.vn",
                        "fullName": "Tamper Version Name",
                        "roleAssignments": [
                            { "role": "LECTURER" }
                        ]
                    }
                    """.formatted(PREFIX);

            mockMvc.perform(put("/api/v1/users/" + targetUser.getId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(rawJsonWithStatusAndVersion))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.tokenVersion").doesNotExist());

            User userInDb = userRepository.findById(targetUser.getId()).orElseThrow();
            assertThat(userInDb.getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(userInDb.getTokenVersion()).isEqualTo(0L);
        }

        @Test
        @DisplayName("Cố tình truyền id mới trong PUT /api/v1/users/{id}: Entity ID trong DB giữ nguyên")
        void whenAttackerSendsIdInPutBody_entityIdRemainsStrictlyUnchanged() throws Exception {
            String fakeId = "671dcba5f123456789999999";
            String rawJsonWithFakeId = """
                    {
                        "id": "%s",
                        "email": "%starget_id@hcmute.edu.vn",
                        "fullName": "Tamper ID Name",
                        "roleAssignments": [
                            { "role": "LECTURER" }
                        ]
                    }
                    """.formatted(fakeId, PREFIX);

            mockMvc.perform(put("/api/v1/users/" + targetUser.getId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(rawJsonWithFakeId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(targetUser.getId()));

            assertThat(userRepository.findById(fakeId)).isEmpty();
            assertThat(userRepository.findById(targetUser.getId())).isPresent();
        }
    }

    // =========================================================================
    // 2. THỬ THÁCH SCOPE REFERENCE VALIDATION (ID RÁC -> HTTP 422)
    // =========================================================================

    @Nested
    @DisplayName("2. Scope Reference Validation Adversarial Challenge (Nonexistent IDs -> HTTP 422)")
    class ScopeReferenceValidationAdversarialChallengeTests {

        @Test
        @DisplayName("POST /api/v1/users với programId rác không tồn tại: Trả về HTTP 422 SCOPE_NOT_FOUND")
        void postUser_withNonExistentProgramId_returns422ScopeNotFound() throws Exception {
            String nonExistentProgId = "garbage_prog_id_999";
            CreateUserRequest request = CreateUserRequest.builder()
                    .username(CREATED_USERNAME)
                    .email(PREFIX + "created_prog@hcmute.edu.vn")
                    .password(RAW_PASSWORD)
                    .fullName("Prog Scope User")
                    .roleAssignments(List.of(RoleAssignment.builder()
                            .role(Role.PROGRAM_MANAGER)
                            .scope(UserScope.builder().programIds(List.of(nonExistentProgId)).build())
                            .build()))
                    .build();

            mockMvc.perform(post("/api/v1/users")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.status").value(422))
                    .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                    .andExpect(jsonPath("$.message").value(containsString("SCOPE_NOT_FOUND")))
                    .andExpect(jsonPath("$.message").value(containsString(nonExistentProgId)));
        }

        @Test
        @DisplayName("POST /api/v1/users với courseId ObjectId rác không tồn tại: Trả về HTTP 422 SCOPE_NOT_FOUND")
        void postUser_withNonExistentCourseId_returns422ScopeNotFound() throws Exception {
            String nonExistentCourseId = "671dcba5f123456789beef01";
            CreateUserRequest request = CreateUserRequest.builder()
                    .username(CREATED_USERNAME)
                    .email(PREFIX + "created_course@hcmute.edu.vn")
                    .password(RAW_PASSWORD)
                    .fullName("Course Scope User")
                    .roleAssignments(List.of(RoleAssignment.builder()
                            .role(Role.LECTURER)
                            .scope(UserScope.builder().courseIds(List.of(nonExistentCourseId)).build())
                            .build()))
                    .build();

            mockMvc.perform(post("/api/v1/users")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.status").value(422))
                    .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                    .andExpect(jsonPath("$.message").value(containsString("SCOPE_NOT_FOUND")))
                    .andExpect(jsonPath("$.message").value(containsString(nonExistentCourseId)));
        }

        @Test
        @DisplayName("POST /api/v1/users với sectionId rác không tồn tại: Trả về HTTP 422 SCOPE_NOT_FOUND")
        void postUser_withNonExistentSectionId_returns422ScopeNotFound() throws Exception {
            String nonExistentSectionId = "nonexistent_section_id_xyz";
            CreateUserRequest request = CreateUserRequest.builder()
                    .username(CREATED_USERNAME)
                    .email(PREFIX + "created_sec@hcmute.edu.vn")
                    .password(RAW_PASSWORD)
                    .fullName("Section Scope User")
                    .roleAssignments(List.of(RoleAssignment.builder()
                            .role(Role.LECTURER)
                            .scope(UserScope.builder().sectionIds(List.of(nonExistentSectionId)).build())
                            .build()))
                    .build();

            mockMvc.perform(post("/api/v1/users")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.status").value(422))
                    .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                    .andExpect(jsonPath("$.message").value(containsString("SCOPE_NOT_FOUND")))
                    .andExpect(jsonPath("$.message").value(containsString(nonExistentSectionId)));
        }

        @Test
        @DisplayName("PUT /api/v1/users/{id} với programId rác không tồn tại: Trả về HTTP 422 SCOPE_NOT_FOUND")
        void putUser_withNonExistentProgramId_returns422ScopeNotFound() throws Exception {
            String nonExistentProgId = "671dcba5f123456789prog99";
            UpdateUserRequest request = UpdateUserRequest.builder()
                    .email(PREFIX + "target_put_prog@hcmute.edu.vn")
                    .fullName("Updated Prog User")
                    .roleAssignments(List.of(RoleAssignment.builder()
                            .role(Role.PROGRAM_MANAGER)
                            .scope(UserScope.builder().programIds(List.of(nonExistentProgId)).build())
                            .build()))
                    .build();

            mockMvc.perform(put("/api/v1/users/" + targetUser.getId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.status").value(422))
                    .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                    .andExpect(jsonPath("$.message").value(containsString("SCOPE_NOT_FOUND")))
                    .andExpect(jsonPath("$.message").value(containsString(nonExistentProgId)));
        }

        @Test
        @DisplayName("PUT /api/v1/users/{id} với courseId rác không tồn tại: Trả về HTTP 422 SCOPE_NOT_FOUND")
        void putUser_withNonExistentCourseId_returns422ScopeNotFound() throws Exception {
            String nonExistentCourseId = "garbage_course_id_abc";
            UpdateUserRequest request = UpdateUserRequest.builder()
                    .email(PREFIX + "target_put_course@hcmute.edu.vn")
                    .fullName("Updated Course User")
                    .roleAssignments(List.of(RoleAssignment.builder()
                            .role(Role.LECTURER)
                            .scope(UserScope.builder().courseIds(List.of(nonExistentCourseId)).build())
                            .build()))
                    .build();

            mockMvc.perform(put("/api/v1/users/" + targetUser.getId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.status").value(422))
                    .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                    .andExpect(jsonPath("$.message").value(containsString("SCOPE_NOT_FOUND")))
                    .andExpect(jsonPath("$.message").value(containsString(nonExistentCourseId)));
        }

        @Test
        @DisplayName("PUT /api/v1/users/{id} với sectionId rác không tồn tại: Trả về HTTP 422 SCOPE_NOT_FOUND")
        void putUser_withNonExistentSectionId_returns422ScopeNotFound() throws Exception {
            String nonExistentSecId = "garbage_section_id_123";
            UpdateUserRequest request = UpdateUserRequest.builder()
                    .email(PREFIX + "target_put_sec@hcmute.edu.vn")
                    .fullName("Updated Section User")
                    .roleAssignments(List.of(RoleAssignment.builder()
                            .role(Role.LECTURER)
                            .scope(UserScope.builder().sectionIds(List.of(nonExistentSecId)).build())
                            .build()))
                    .build();

            mockMvc.perform(put("/api/v1/users/" + targetUser.getId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.status").value(422))
                    .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                    .andExpect(jsonPath("$.message").value(containsString("SCOPE_NOT_FOUND")))
                    .andExpect(jsonPath("$.message").value(containsString(nonExistentSecId)));
        }

        @Test
        @DisplayName("Nhiều roleAssignments trong đó chỉ assignment thứ 2 có ID rác: Vẫn từ chối với HTTP 422")
        void multipleRoleAssignments_whereSecondAssignmentHasInvalidScope_returns422() throws Exception {
            String nonExistentProgId = "671dcba5f123456789prog88";
            UpdateUserRequest request = UpdateUserRequest.builder()
                    .email(PREFIX + "target_multi@hcmute.edu.vn")
                    .fullName("Multi Role User")
                    .roleAssignments(List.of(
                            RoleAssignment.builder().role(Role.LECTURER).build(), // hợp lệ (scope rỗng)
                            RoleAssignment.builder()
                                    .role(Role.PROGRAM_MANAGER)
                                    .scope(UserScope.builder().programIds(List.of(nonExistentProgId)).build()) // không hợp lệ
                                    .build()
                    ))
                    .build();

            mockMvc.perform(put("/api/v1/users/" + targetUser.getId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.status").value(422))
                    .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                    .andExpect(jsonPath("$.message").value(containsString("SCOPE_NOT_FOUND")));
        }

        @Test
        @DisplayName("Positive Control: ID thực sự tồn tại trong CSDL -> Chấp nhận thành công (HTTP 200/201)")
        void whenScopeIdsActuallyExistInDatabase_succeedsWithOkOrCreated() throws Exception {
            // Chèn bản ghi giả lập thỏa mãn validator schema của MongoDB vào đúng 3 collections
            mongoTemplate.insert(new Document("_id", new ObjectId(TEST_PROG_ID))
                    .append("code", "PROG_CH_01")
                    .append("name", "Challenger Program"), "programs");

            mongoTemplate.insert(new Document("_id", new ObjectId(TEST_COURSE_ID))
                    .append("code", "COURSE_CH_01")
                    .append("name", "Challenger Course"), "courses");

            mongoTemplate.insert(new Document("_id", new ObjectId(TEST_SECTION_ID))
                    .append("code", "SEC_CH_01")
                    .append("courseId", new ObjectId(TEST_COURSE_ID))
                    .append("syllabusId", new ObjectId("671dcba5f123456789000002"))
                    .append("termId", new ObjectId("671dcba5f123456789000003"))
                    .append("status", "OPEN"), "course_sections");

            // 1. Tạo mới user với scope hợp lệ chứa cả 3 ID có thật
            CreateUserRequest postRequest = CreateUserRequest.builder()
                    .username(CREATED_USERNAME)
                    .email(PREFIX + "created_valid@hcmute.edu.vn")
                    .password(RAW_PASSWORD)
                    .fullName("Valid Scope User")
                    .roleAssignments(List.of(RoleAssignment.builder()
                            .role(Role.LECTURER)
                            .scope(UserScope.builder()
                                    .programIds(List.of(TEST_PROG_ID))
                                    .courseIds(List.of(TEST_COURSE_ID))
                                    .sectionIds(List.of(TEST_SECTION_ID))
                                    .build())
                            .build()))
                    .build();

            mockMvc.perform(post("/api/v1/users")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(postRequest)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.username").value(CREATED_USERNAME));

            // 2. Cập nhật user với scope hợp lệ
            UpdateUserRequest putRequest = UpdateUserRequest.builder()
                    .email(PREFIX + "target_valid@hcmute.edu.vn")
                    .fullName("Valid Put Scope")
                    .roleAssignments(List.of(RoleAssignment.builder()
                            .role(Role.LECTURER)
                            .scope(UserScope.builder()
                                    .programIds(List.of(TEST_PROG_ID))
                                    .courseIds(List.of(TEST_COURSE_ID))
                                    .sectionIds(List.of(TEST_SECTION_ID))
                                    .build())
                            .build()))
                    .build();

            mockMvc.perform(put("/api/v1/users/" + targetUser.getId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(putRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value(PREFIX + "target_valid@hcmute.edu.vn"));
        }

        @Test
        @DisplayName("Scope rỗng hoặc null: Không thực hiện kiểm tra DB và chấp nhận thành công")
        void scopeEmptyOrNull_acceptedWithoutCheckingDb() throws Exception {
            UpdateUserRequest request = UpdateUserRequest.builder()
                    .email(PREFIX + "target_empty_scope@hcmute.edu.vn")
                    .fullName("Empty Scope User")
                    .roleAssignments(List.of(
                            RoleAssignment.builder().role(Role.VIEWER).scope(null).build(),
                            RoleAssignment.builder().role(Role.LECTURER).scope(UserScope.builder()
                                    .programIds(Collections.emptyList())
                                    .courseIds(Collections.emptyList())
                                    .sectionIds(Collections.emptyList())
                                    .build()).build()
                    ))
                    .build();

            mockMvc.perform(put("/api/v1/users/" + targetUser.getId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }
    }

    // =========================================================================
    // 3. THỬ THÁCH KHÓA & THU HỒI PHIÊN LÀM VIỆC (LOCKED -> 401, UNLOCK -> VẪN 401)
    // =========================================================================

    @Nested
    @DisplayName("3. Account Lock and Session Revocation Adversarial Challenge")
    class AccountLockAndSessionRevocationAdversarialChallengeTests {

        @Test
        @DisplayName("Vòng đời Khóa -> Thu hồi -> Mở khóa -> Token cũ VĨNH VIỄN bị 401; Đăng nhập mới nhận token mới hoạt động tốt")
        void fullLifecycle_lock_revokesToken_unlock_preservesRevocation_relogin_works() throws Exception {
            // Bước 1: Target user đăng nhập ban đầu -> nhận tokenV0
            String tokenV0 = obtainAccessToken(TARGET_USERNAME, RAW_PASSWORD);
            assertThat(tokenV0).isNotEmpty();

            // Bước 2: TokenV0 truy cập hợp lệ /api/v1/auth/me -> HTTP 200 OK
            mockMvc.perform(get("/api/v1/auth/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenV0))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value(TARGET_USERNAME));

            // Bước 3: Admin khóa tài khoản target user -> PUT /api/v1/users/{id}/status sang LOCKED
            UpdateUserStatusRequest lockRequest = new UpdateUserStatusRequest(UserStatus.LOCKED);
            mockMvc.perform(put("/api/v1/users/" + targetUser.getId() + "/status")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(lockRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("LOCKED"));

            // Bước 4: Kiểm tra trạng thái trong MongoDB
            User lockedUserInDb = userRepository.findById(targetUser.getId()).orElseThrow();
            assertThat(lockedUserInDb.getStatus()).isEqualTo(UserStatus.LOCKED);
            assertThat(lockedUserInDb.getTokenVersion()).isEqualTo(1L);

            // Bước 5: Gửi request với token cũ tokenV0 -> lập tức nhận HTTP 401 UNAUTHORIZED!
            mockMvc.perform(get("/api/v1/auth/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenV0))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

            // Bước 6: Admin mở khóa lại tài khoản target user sang ACTIVE
            UpdateUserStatusRequest unlockRequest = new UpdateUserStatusRequest(UserStatus.ACTIVE);
            mockMvc.perform(put("/api/v1/users/" + targetUser.getId() + "/status")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(unlockRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ACTIVE"));

            // Bước 7: Kiểm tra MongoDB: status đã là ACTIVE, nhưng tokenVersion VẪN LÀ 1 (không bao giờ giảm về 0)
            User unlockedUserInDb = userRepository.findById(targetUser.getId()).orElseThrow();
            assertThat(unlockedUserInDb.getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(unlockedUserInDb.getTokenVersion()).isEqualTo(1L);

            // Bước 8: Gửi LẠI request với token cũ tokenV0 -> VẪN VĨNH VIỄN NHẬN HTTP 401 UNAUTHORIZED!
            mockMvc.perform(get("/api/v1/auth/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenV0))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

            // Bước 9: Target user đăng nhập lại sau khi mở khóa -> nhận token mới tokenV1
            String tokenV1 = obtainAccessToken(TARGET_USERNAME, RAW_PASSWORD);
            assertThat(tokenV1).isNotEmpty();
            assertThat(tokenV1).isNotEqualTo(tokenV0);

            // Bước 10: Token mới tokenV1 truy cập thành công /api/v1/auth/me -> HTTP 200 OK!
            mockMvc.perform(get("/api/v1/auth/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenV1))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value(TARGET_USERNAME));

            // Bước 11: Concurrently: Token cũ tokenV0 vẫn tiếp tục bị từ chối 401!
            mockMvc.perform(get("/api/v1/auth/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenV0))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Khi tài khoản đang bị LOCKED: Thử đăng nhập nhận về HTTP 401 UNAUTHORIZED (chống user enumeration)")
        void loginAttempt_whileAccountIsLocked_failsWith401Unauthorized() throws Exception {
            // Khóa tài khoản
            UpdateUserStatusRequest lockRequest = new UpdateUserStatusRequest(UserStatus.LOCKED);
            mockMvc.perform(put("/api/v1/users/" + targetUser.getId() + "/status")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(lockRequest)))
                    .andExpect(status().isOk());

            // Đăng nhập với mật khẩu đúng khi tài khoản đang LOCKED -> từ chối 401 với thông điệp chung tránh lộ trạng thái tài khoản
            LoginRequest loginRequest = new LoginRequest(TARGET_USERNAME, RAW_PASSWORD);
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.message").value("Tên đăng nhập hoặc mật khẩu không chính xác"));
        }

        @Test
        @DisplayName("Nhiều lần khóa liên tiếp: tokenVersion tăng đơn điệu và vô hiệu hóa toàn bộ token thế hệ trước")
        void repeatedLockCalls_incrementTokenVersionMonotonically() throws Exception {
            // Khóa lần 1 -> tokenVersion = 1
            mockMvc.perform(put("/api/v1/users/" + targetUser.getId() + "/status")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new UpdateUserStatusRequest(UserStatus.LOCKED))))
                    .andExpect(status().isOk());

            assertThat(userRepository.findById(targetUser.getId()).orElseThrow().getTokenVersion()).isEqualTo(1L);

            // Mở khóa -> tokenVersion vẫn = 1
            mockMvc.perform(put("/api/v1/users/" + targetUser.getId() + "/status")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new UpdateUserStatusRequest(UserStatus.ACTIVE))))
                    .andExpect(status().isOk());

            String tokenGen1 = obtainAccessToken(TARGET_USERNAME, RAW_PASSWORD);

            // Khóa lần 2 -> tokenVersion = 2
            mockMvc.perform(put("/api/v1/users/" + targetUser.getId() + "/status")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new UpdateUserStatusRequest(UserStatus.LOCKED))))
                    .andExpect(status().isOk());

            assertThat(userRepository.findById(targetUser.getId()).orElseThrow().getTokenVersion()).isEqualTo(2L);

            // Mở khóa lần 2 -> tokenVersion vẫn = 2
            mockMvc.perform(put("/api/v1/users/" + targetUser.getId() + "/status")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new UpdateUserStatusRequest(UserStatus.ACTIVE))))
                    .andExpect(status().isOk());

            // Token Gen 1 cấp ở version 1 lập tức bị từ chối 401
            mockMvc.perform(get("/api/v1/auth/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenGen1))
                    .andExpect(status().isUnauthorized());

            // Đăng nhập lần 3 -> nhận Token Gen 2 (version 2) -> hoạt động bình thường
            String tokenGen2 = obtainAccessToken(TARGET_USERNAME, RAW_PASSWORD);
            mockMvc.perform(get("/api/v1/auth/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenGen2))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Token đã bị thu hồi do khóa: Bị từ chối HTTP 401 trên TẤT CẢ các endpoint bảo vệ")
        void revokedToken_rejectedAcrossAllProtectedEndpoints() throws Exception {
            String token = obtainAccessToken(TARGET_USERNAME, RAW_PASSWORD);

            // Khóa tài khoản
            mockMvc.perform(put("/api/v1/users/" + targetUser.getId() + "/status")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new UpdateUserStatusRequest(UserStatus.LOCKED))))
                    .andExpect(status().isOk());

            // 1. Thử gọi /api/v1/auth/me -> 401
            mockMvc.perform(get("/api/v1/auth/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

            // 2. Thử gọi /api/v1/auth/logout -> 401
            mockMvc.perform(post("/api/v1/auth/logout")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

            // 3. Thử gọi /api/v1/users -> 401
            mockMvc.perform(get("/api/v1/users")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("Kiểm tra trực tiếp AuthSessionService: Ném InvalidBearerTokenException khi lệch tokenVersion hoặc bị LOCKED")
        void authSessionService_directVerificationUnderStress() {
            String userId = targetUser.getId();

            // 1. Bình thường: user ACTIVE, tokenVersion = 0 -> thành công
            var principal = authSessionService.validateUserSession(userId, 0L);
            assertThat(principal).isNotNull();
            assertThat(principal.getUsername()).isEqualTo(TARGET_USERNAME);

            // 2. Lệch tokenVersion (truyền 1L khi DB là 0L) -> ném InvalidBearerTokenException
            assertThatThrownBy(() -> authSessionService.validateUserSession(userId, 1L))
                    .isInstanceOf(InvalidBearerTokenException.class)
                    .hasMessageContaining("tokenVersion mismatch");

            // 3. Khóa user trong DB
            targetUser.setStatus(UserStatus.LOCKED);
            userRepository.save(targetUser);

            // 4. Đúng tokenVersion nhưng user đang LOCKED -> ném InvalidBearerTokenException
            assertThatThrownBy(() -> authSessionService.validateUserSession(userId, 0L))
                    .isInstanceOf(InvalidBearerTokenException.class)
                    .hasMessageContaining("User account is locked or inactive");
        }
    }
}
