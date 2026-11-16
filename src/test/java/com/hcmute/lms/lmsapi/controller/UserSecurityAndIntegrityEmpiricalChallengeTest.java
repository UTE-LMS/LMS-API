package com.hcmute.lms.lmsapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hcmute.lms.lmsapi.document.User;
import com.hcmute.lms.lmsapi.dto.LoginRequest;
import com.hcmute.lms.lmsapi.dto.UpdateUserStatusRequest;
import com.hcmute.lms.lmsapi.enums.Role;
import com.hcmute.lms.lmsapi.enums.UserStatus;
import com.hcmute.lms.lmsapi.repository.UserRepository;
import com.hcmute.lms.lmsapi.service.AuthSessionService;
import com.hcmute.lms.lmsapi.support.ApiTestSupport;
import java.time.Instant;
import java.util.List;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

/**
 * Empirical Adversarial Challenge: User Immutability, Scope Reference Validation & Session
 * Revocation (PR 4). Stress-tests three critical dimensions: 1. Username and Sensitive Fields
 * Immutability: - Malicious PUT payloads injecting new username, password, passwordHash,
 * tokenVersion, status, or id. - Direct DB assertions and authentication verification to prove
 * state integrity. 2. Scope Reference Validation (Nonexistent IDs -> HTTP 422): - Arbitrary
 * nonexistent string and ObjectId identifiers in programIds, courseIds, and sectionIds. -
 * Verification on both POST /api/v1/users and PUT /api/v1/users/{id}. - Positive control: valid
 * existent records in MongoDB collections. 3. Account Lock & Token Revocation Life-Cycle: -
 * Immediate 401 rejection of active tokens upon lock (PUT .../status -> LOCKED). - Permanent
 * invalidation: unlocking back to ACTIVE never reinstates old tokens. - Re-authentication lifecycle
 * and monotonic tokenVersion tracking.
 */
@SpringBootTest
@DisplayName(
    "Empirical Challenge: User Immutability, Scope Reference Validation & Session Revocation")
public class UserSecurityAndIntegrityEmpiricalChallengeTest {

  private static final String PREFIX = "ch_pr4_2_";
  private static final String ADMIN_USERNAME = PREFIX + "admin";
  private static final String TARGET_USERNAME = PREFIX + "target";
  private static final String CREATED_USERNAME = PREFIX + "created";
  private static final String RAW_PASSWORD = "ChallengePassword123!";

  @Autowired private WebApplicationContext context;

  @Autowired private UserRepository userRepository;

  @Autowired private MongoTemplate mongoTemplate;

  @Autowired private PasswordEncoder passwordEncoder;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private AuthSessionService authSessionService;

  private MockMvc mockMvc;

  private User targetUser;
  private String adminToken;

  @BeforeEach
  void setUp() throws Exception {
    this.mockMvc =
        MockMvcBuilders.webAppContextSetup(context)
            .apply(SecurityMockMvcConfigurers.springSecurity())
            .build();

    cleanupData();

    // 1. Tạo ADMIN test user
    userRepository.save(
        User.builder()
            .username(ADMIN_USERNAME)
            .email(PREFIX + "admin@hcmute.edu.vn")
            .passwordHash(passwordEncoder.encode(RAW_PASSWORD))
            .fullName("[DỮ LIỆU THỬ NGHIỆM] Challenger Admin")
            .status(UserStatus.ACTIVE)
            .tokenVersion(0L)
            .roles(List.of(Role.ADMIN))
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build());

    // 2. Tạo TARGET test user
    targetUser =
        userRepository.save(
            User.builder()
                .username(TARGET_USERNAME)
                .email(PREFIX + "target@hcmute.edu.vn")
                .passwordHash(passwordEncoder.encode(RAW_PASSWORD))
                .fullName("[DỮ LIỆU THỬ NGHIỆM] Challenger Target")
                .status(UserStatus.ACTIVE)
                .tokenVersion(0L)
                .roles(List.of(Role.LECTURER))
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
  }

  private String obtainAccessToken(String username, String password) throws Exception {
    return ApiTestSupport.obtainAccessToken(mockMvc, objectMapper, username, password);
  }

  // =========================================================================
  // 1. THỬ THÁCH TÍNH BẤT BIẾN USERNAME & CÁC TRƯỜNG NHẠY CẢM
  // =========================================================================

  @Nested
  @DisplayName("1. Username and Sensitive Fields Immutability Adversarial Challenge")
  class UsernameAndFieldImmutabilityAdversarialChallengeTests {

    @Test
    @DisplayName(
        "Cố tình truyền username mới trong PUT /api/v1/users/{id}: Username trong DB tuyệt đối không đổi")
    void whenAttackerSendsNewUsernameInPutBody_usernameInDbRemainsStrictlyUnchanged()
        throws Exception {
      String tamperedUsername = PREFIX + "tampered_username";
      String rawJsonWithTamperedUsername =
          """
                    {\
                        "username": "%s",\
                        "email": "%starget_updated@hcmute.edu.vn",\
                        "fullName": "Victim Updated Name",\
                        "roles": [\
                            "LECTURER"\
                        ]\
                    }\
                    """
              .formatted(tamperedUsername, PREFIX);

      mockMvc
          .perform(
              put("/api/v1/users/" + targetUser.getId())
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
      mockMvc
          .perform(
              post("/api/v1/auth/login")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(badLogin)))
          .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName(
        "Cố tình truyền password hoặc passwordHash trong PUT /api/v1/users/{id}: Mật khẩu không bị ghi đè")
    void whenAttackerSendsPasswordAndHashInPutBody_passwordInDbRemainsStrictlyUnchanged()
        throws Exception {
      String originalHash = targetUser.getPasswordHash();
      String maliciousPassword = "MaliciousHackedPassword999!";
      String rawJsonWithPassword =
          """
                    {\
                        "password": "%s",\
                        "passwordHash": "$2a$10$maliciousFakeHashValueHere1234567890abcdef",\
                        "email": "%starget_pw@hcmute.edu.vn",\
                        "fullName": "Tamper PW Name",\
                        "roles": [\
                            "LECTURER"\
                        ]\
                    }\
                    """
              .formatted(maliciousPassword, PREFIX);

      mockMvc
          .perform(
              put("/api/v1/users/" + targetUser.getId())
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
      mockMvc
          .perform(
              post("/api/v1/auth/login")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(badLogin)))
          .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName(
        "Cố tình truyền status hoặc tokenVersion trong PUT /api/v1/users/{id}: Giá trị không bị sửa")
    void whenAttackerSendsStatusAndTokenVersionInPutBody_fieldsInDbRemainStrictlyUnchanged()
        throws Exception {
      String rawJsonWithStatusAndVersion =
          """
                    {\
                        "status": "LOCKED",\
                        "tokenVersion": 9999,\
                        "email": "%starget_ver@hcmute.edu.vn",\
                        "fullName": "Tamper Version Name",\
                        "roles": [\
                            "LECTURER"\
                        ]\
                    }\
                    """
              .formatted(PREFIX);

      mockMvc
          .perform(
              put("/api/v1/users/" + targetUser.getId())
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
    @DisplayName(
        "Cố tình truyền id mới trong PUT /api/v1/users/{id}: Entity ID trong DB giữ nguyên")
    void whenAttackerSendsIdInPutBody_entityIdRemainsStrictlyUnchanged() throws Exception {
      String fakeId = "671dcba5f123456789999999";
      String rawJsonWithFakeId =
          """
                    {\
                        "id": "%s",\
                        "email": "%starget_id@hcmute.edu.vn",\
                        "fullName": "Tamper ID Name",\
                        "roles": [\
                            "LECTURER"\
                        ]\
                    }\
                    """
              .formatted(fakeId, PREFIX);

      mockMvc
          .perform(
              put("/api/v1/users/" + targetUser.getId())
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
  // 3. THỬ THÁCH KHÓA & THU HỒI PHIÊN LÀM VIỆC (LOCKED -> 401, UNLOCK -> VẪN 401)
  // =========================================================================

  @Nested
  @DisplayName("3. Account Lock and Session Revocation Adversarial Challenge")
  class AccountLockAndSessionRevocationAdversarialChallengeTests {

    @Test
    @DisplayName(
        "Vòng đời Khóa -> Thu hồi -> Mở khóa -> Token cũ VĨNH VIỄN bị 401; Đăng nhập mới nhận token mới hoạt động tốt")
    void fullLifecycle_lock_revokesToken_unlock_preservesRevocation_relogin_works()
        throws Exception {
      // Bước 1: Target user đăng nhập ban đầu -> nhận tokenV0
      String tokenV0 = obtainAccessToken(TARGET_USERNAME, RAW_PASSWORD);
      assertThat(tokenV0).isNotEmpty();

      // Bước 2: TokenV0 truy cập hợp lệ /api/v1/auth/me -> HTTP 200 OK
      mockMvc
          .perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenV0))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.username").value(TARGET_USERNAME));

      // Bước 3: Admin khóa tài khoản target user -> PUT /api/v1/users/{id}/status sang LOCKED
      UpdateUserStatusRequest lockRequest = new UpdateUserStatusRequest(UserStatus.LOCKED);
      ApiTestSupport.performJson(
              mockMvc,
              objectMapper,
              put("/api/v1/users/" + targetUser.getId() + "/status"),
              adminToken,
              lockRequest)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("LOCKED"));

      // Bước 4: Kiểm tra trạng thái trong MongoDB
      User lockedUserInDb = userRepository.findById(targetUser.getId()).orElseThrow();
      assertThat(lockedUserInDb.getStatus()).isEqualTo(UserStatus.LOCKED);
      assertThat(lockedUserInDb.getTokenVersion()).isEqualTo(1L);

      // Bước 5: Gửi request với token cũ tokenV0 -> lập tức nhận HTTP 401 UNAUTHORIZED!
      mockMvc
          .perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenV0))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.status").value(401))
          .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

      // Bước 6: Admin mở khóa lại tài khoản target user sang ACTIVE
      UpdateUserStatusRequest unlockRequest = new UpdateUserStatusRequest(UserStatus.ACTIVE);
      ApiTestSupport.performJson(
              mockMvc,
              objectMapper,
              put("/api/v1/users/" + targetUser.getId() + "/status"),
              adminToken,
              unlockRequest)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("ACTIVE"));

      // Bước 7: Kiểm tra MongoDB: status đã là ACTIVE, nhưng tokenVersion VẪN LÀ 1 (không bao giờ
      // giảm về 0)
      User unlockedUserInDb = userRepository.findById(targetUser.getId()).orElseThrow();
      assertThat(unlockedUserInDb.getStatus()).isEqualTo(UserStatus.ACTIVE);
      assertThat(unlockedUserInDb.getTokenVersion()).isEqualTo(1L);

      // Bước 8: Gửi LẠI request với token cũ tokenV0 -> VẪN VĨNH VIỄN NHẬN HTTP 401 UNAUTHORIZED!
      mockMvc
          .perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenV0))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.status").value(401))
          .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

      // Bước 9: Target user đăng nhập lại sau khi mở khóa -> nhận token mới tokenV1
      String tokenV1 = obtainAccessToken(TARGET_USERNAME, RAW_PASSWORD);
      assertThat(tokenV1).isNotEmpty();
      assertThat(tokenV1).isNotEqualTo(tokenV0);

      // Bước 10: Token mới tokenV1 truy cập thành công /api/v1/auth/me -> HTTP 200 OK!
      mockMvc
          .perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenV1))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.username").value(TARGET_USERNAME));

      // Bước 11: Concurrently: Token cũ tokenV0 vẫn tiếp tục bị từ chối 401!
      mockMvc
          .perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenV0))
          .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName(
        "Khi tài khoản đang bị LOCKED: Thử đăng nhập nhận về HTTP 401 UNAUTHORIZED (chống user enumeration)")
    void loginAttempt_whileAccountIsLocked_failsWith401Unauthorized() throws Exception {
      // Khóa tài khoản
      UpdateUserStatusRequest lockRequest = new UpdateUserStatusRequest(UserStatus.LOCKED);
      ApiTestSupport.performJson(
              mockMvc,
              objectMapper,
              put("/api/v1/users/" + targetUser.getId() + "/status"),
              adminToken,
              lockRequest)
          .andExpect(status().isOk());

      // Đăng nhập với mật khẩu đúng khi tài khoản đang LOCKED -> từ chối 401 với thông điệp chung
      // tránh lộ trạng thái tài khoản
      LoginRequest loginRequest = new LoginRequest(TARGET_USERNAME, RAW_PASSWORD);
      mockMvc
          .perform(
              post("/api/v1/auth/login")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(loginRequest)))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.status").value(401))
          .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
          .andExpect(jsonPath("$.message").value("Tên đăng nhập hoặc mật khẩu không chính xác"));
    }

    @Test
    @DisplayName(
        "Nhiều lần khóa liên tiếp: tokenVersion tăng đơn điệu và vô hiệu hóa toàn bộ token thế hệ trước")
    void repeatedLockCalls_incrementTokenVersionMonotonically() throws Exception {
      // Khóa lần 1 -> tokenVersion = 1
      mockMvc
          .perform(
              put("/api/v1/users/" + targetUser.getId() + "/status")
                  .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      objectMapper.writeValueAsString(
                          new UpdateUserStatusRequest(UserStatus.LOCKED))))
          .andExpect(status().isOk());

      assertThat(userRepository.findById(targetUser.getId()).orElseThrow().getTokenVersion())
          .isEqualTo(1L);

      // Mở khóa -> tokenVersion vẫn = 1
      mockMvc
          .perform(
              put("/api/v1/users/" + targetUser.getId() + "/status")
                  .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      objectMapper.writeValueAsString(
                          new UpdateUserStatusRequest(UserStatus.ACTIVE))))
          .andExpect(status().isOk());

      String tokenGen1 = obtainAccessToken(TARGET_USERNAME, RAW_PASSWORD);

      // Khóa lần 2 -> tokenVersion = 2
      mockMvc
          .perform(
              put("/api/v1/users/" + targetUser.getId() + "/status")
                  .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      objectMapper.writeValueAsString(
                          new UpdateUserStatusRequest(UserStatus.LOCKED))))
          .andExpect(status().isOk());

      assertThat(userRepository.findById(targetUser.getId()).orElseThrow().getTokenVersion())
          .isEqualTo(2L);

      // Mở khóa lần 2 -> tokenVersion vẫn = 2
      mockMvc
          .perform(
              put("/api/v1/users/" + targetUser.getId() + "/status")
                  .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      objectMapper.writeValueAsString(
                          new UpdateUserStatusRequest(UserStatus.ACTIVE))))
          .andExpect(status().isOk());

      // Token Gen 1 cấp ở version 1 lập tức bị từ chối 401
      mockMvc
          .perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenGen1))
          .andExpect(status().isUnauthorized());

      // Đăng nhập lần 3 -> nhận Token Gen 2 (version 2) -> hoạt động bình thường
      String tokenGen2 = obtainAccessToken(TARGET_USERNAME, RAW_PASSWORD);
      mockMvc
          .perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenGen2))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Token đã bị thu hồi do khóa: Bị từ chối HTTP 401 trên TẤT CẢ các endpoint bảo vệ")
    void revokedToken_rejectedAcrossAllProtectedEndpoints() throws Exception {
      String token = obtainAccessToken(TARGET_USERNAME, RAW_PASSWORD);

      // Khóa tài khoản
      mockMvc
          .perform(
              put("/api/v1/users/" + targetUser.getId() + "/status")
                  .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      objectMapper.writeValueAsString(
                          new UpdateUserStatusRequest(UserStatus.LOCKED))))
          .andExpect(status().isOk());

      // 1. Thử gọi /api/v1/auth/me -> 401
      mockMvc
          .perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

      // 2. Thử gọi /api/v1/auth/logout -> 401
      mockMvc
          .perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

      // 3. Thử gọi /api/v1/users -> 401
      mockMvc
          .perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName(
        "Kiểm tra trực tiếp AuthSessionService: Ném InvalidBearerTokenException khi lệch tokenVersion hoặc bị LOCKED")
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
