package com.hcmute.lms.lmsapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hcmute.lms.lmsapi.document.RoleAssignment;
import com.hcmute.lms.lmsapi.document.User;
import com.hcmute.lms.lmsapi.dto.CreateUserRequest;
import com.hcmute.lms.lmsapi.dto.LoginRequest;
import com.hcmute.lms.lmsapi.dto.UpdateUserRequest;
import com.hcmute.lms.lmsapi.enums.Role;
import com.hcmute.lms.lmsapi.enums.UserStatus;
import com.hcmute.lms.lmsapi.testsupport.ApiTestFixtures;
import com.hcmute.lms.lmsapi.testsupport.IntegrationTestBase;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * F01: ca âm/biên cho US-01/US-02/US-03 mà các test hiện có chưa phủ (đối chiếu AC trong {@code
 * doc/plan/F01-nguoi-dung-phan-quyen.md} mục 5): vai trò không phải ADMIN trên mọi endpoint quản
 * lý, ranh giới username/mật khẩu, chuẩn hóa chữ thường, trường thừa, body rỗng/sai, ObjectId sai
 * định dạng, và nhật ký không chứa mật khẩu/băm.
 */
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("F01: ca âm và biên của API xác thực và quản lý người dùng")
class UserApiBoundaryIntegrationTest extends IntegrationTestBase {

  private static final String USERS = "/api/v1/users";
  private static final String USER_BY_ID = "/api/v1/users/{id}";
  private static final String USER_STATUS = "/api/v1/users/{id}/status";
  private static final String VALID_PASSWORD = "Mat-khau-thu-nghiem-1";

  private String admin;

  @BeforeEach
  void setUp() {
    admin = fixtures.bearerFor(Role.ADMIN, null);
  }

  private CreateUserRequest createRequest(String username) {
    return CreateUserRequest.builder()
        .username(username)
        .email(username + "@itest.invalid")
        .password(VALID_PASSWORD)
        .fullName(ApiTestFixtures.LABEL + " " + username)
        .roleAssignments(List.of(RoleAssignment.builder().role(Role.QA).build()))
        .build();
  }

  private MockHttpServletRequestBuilder request(
      String method, String url, String bearer, Object body, Object... vars) {
    return switch (method) {
      case "GET" -> getAs(bearer, url, vars);
      case "POST" -> postAs(bearer, url, body, vars);
      case "PUT" -> putAs(bearer, url, body, vars);
      default -> throw new IllegalArgumentException(method);
    };
  }

  // ------------------------------------------------------------------ US-03/AC-2, US-02/AC-4

  @Test
  @DisplayName("US-03/AC-2: không đăng nhập -> 401 UNAUTHORIZED trên mọi endpoint trừ login")
  void everyProtectedEndpointRejectsAnonymous() throws Exception {
    String id = ApiTestFixtures.missingId();
    String[][] endpoints = {
      {"GET", "/api/v1/auth/me"},
      {"POST", "/api/v1/auth/logout"},
      {"GET", USERS},
      {"POST", USERS},
      {"GET", USER_BY_ID},
      {"PUT", USER_BY_ID},
      {"PUT", USER_STATUS},
    };
    for (String[] endpoint : endpoints) {
      mockMvc
          .perform(request(endpoint[0], endpoint[1], null, "{}", id))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
  }

  @ParameterizedTest(name = "{0} -> 403 trên mọi endpoint /users")
  @EnumSource(
      value = Role.class,
      names = {"ADMIN"},
      mode = EnumSource.Mode.EXCLUDE)
  @DisplayName(
      "US-02/AC-4: mọi vai trò khác ADMIN gọi API quản lý người dùng -> 403, dữ liệu không đổi")
  void nonAdminRolesAreForbiddenOnUserManagement(Role role) throws Exception {
    String bearer = fixtures.bearerFor(role, null);
    User target = fixtures.user(Role.LECTURER, null);
    UpdateUserRequest update =
        UpdateUserRequest.builder()
            .email("changed@itest.invalid")
            .fullName("changed")
            .roleAssignments(List.of(RoleAssignment.builder().role(Role.ADMIN).build()))
            .build();

    mockMvc.perform(getAs(bearer, USERS)).andExpect(status().isForbidden());
    mockMvc.perform(getAs(bearer, USER_BY_ID, target.getId())).andExpect(status().isForbidden());
    mockMvc
        .perform(postAs(bearer, USERS, createRequest(fixtures.username("x"))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    mockMvc
        .perform(putAs(bearer, USER_BY_ID, update, target.getId()))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(putAs(bearer, USER_STATUS, "{\"status\":\"LOCKED\"}", target.getId()))
        .andExpect(status().isForbidden());

    User stored = mongo().findById(target.getId(), User.class);
    assertThat(stored.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(stored.getTokenVersion()).isZero();
    assertThat(stored.getRoleAssignments())
        .extracting(RoleAssignment::getRole)
        .containsExactly(Role.LECTURER);
  }

  // ------------------------------------------------------------------ US-02/AC-1: ranh giới

  @Test
  @DisplayName("US-02/AC-1 (biên): username 3 và 50 ký tự hợp lệ; 2 và 51 ký tự bị 400")
  void usernameLengthBoundaries() throws Exception {
    String three = fixtures.trackUsername(UUID.randomUUID().toString().substring(0, 3));
    String fifty = ("itest_" + fixtures.runId() + "_" + "x".repeat(50)).substring(0, 50);
    assertThat(fifty).hasSize(50);

    mockMvc.perform(postAs(admin, USERS, createRequest(three))).andExpect(status().isCreated());
    mockMvc.perform(postAs(admin, USERS, createRequest(fifty))).andExpect(status().isCreated());
    mockMvc
        .perform(postAs(admin, USERS, createRequest("ab")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("username")));
    mockMvc
        .perform(postAs(admin, USERS, createRequest(fifty + "z")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("username")));
  }

  @ParameterizedTest(name = "username \"{0}\" -> 400")
  @ValueSource(strings = {"a b c", "ab@cd", "tên_có_dấu", "user/name", "user*", "  "})
  @DisplayName("US-02/AC-1 (âm): username chứa ký tự ngoài [a-zA-Z0-9._-] -> 400")
  void usernameIllegalCharacters(String username) throws Exception {
    mockMvc
        .perform(postAs(admin, USERS, createRequest(username)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  @Test
  @DisplayName("US-02/AC-1 (biên): mật khẩu 8 và 72 byte hợp lệ; 7 và 73 byte bị 400")
  void passwordByteBoundaries() throws Exception {
    for (int bytes : new int[] {8, 72}) {
      CreateUserRequest ok = createRequest(fixtures.username("pw"));
      ok.setPassword("a".repeat(bytes));
      mockMvc.perform(postAs(admin, USERS, ok)).andExpect(status().isCreated());
    }
    for (int bytes : new int[] {7, 73}) {
      CreateUserRequest bad = createRequest(fixtures.username("pw"));
      bad.setPassword("a".repeat(bytes));
      mockMvc
          .perform(postAs(admin, USERS, bad))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("password")));
    }
  }

  @Test
  @DisplayName(
      "US-02/AC-1 (biên): mật khẩu tiếng Việt đo bằng byte UTF-8: 24 chữ 'ế' = 72 byte hợp lệ, 25 chữ = 75 byte bị 400")
  void passwordIsMeasuredInUtf8Bytes() throws Exception {
    // 'ế' (U+1EBF) chiếm 3 byte UTF-8: 24 x 3 = 72, 25 x 3 = 75.
    CreateUserRequest atLimit = createRequest(fixtures.username("vn"));
    atLimit.setPassword("ế".repeat(24));
    mockMvc.perform(postAs(admin, USERS, atLimit)).andExpect(status().isCreated());

    CreateUserRequest over = createRequest(fixtures.username("vn"));
    over.setPassword("ế".repeat(25));
    mockMvc.perform(postAs(admin, USERS, over)).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName(
      "US-02/AC-1: username và email được lưu chữ thường; trùng khác hoa/thường vẫn là 409 (US-02/AC-2)")
  void usernameAndEmailAreLowercased() throws Exception {
    String lower = fixtures.username("case");
    CreateUserRequest mixed = createRequest(lower.toUpperCase());
    mixed.setEmail(lower.toUpperCase() + "@Itest.Invalid");

    mockMvc
        .perform(postAs(admin, USERS, mixed))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.username").value(lower))
        .andExpect(jsonPath("$.email").value(lower + "@itest.invalid"));
    assertThat(mongo().findOne(Query.query(Criteria.where("username").is(lower)), User.class))
        .as("username lưu chữ thường")
        .isNotNull();

    mockMvc
        .perform(postAs(admin, USERS, createRequest(lower)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"));

    CreateUserRequest sameEmail = createRequest(fixtures.username("case"));
    sameEmail.setEmail(lower + "@ITEST.invalid");
    mockMvc.perform(postAs(admin, USERS, sameEmail)).andExpect(status().isConflict());
  }

  @Test
  @DisplayName(
      "US-02/AC-1 (âm): trường thừa status, tokenVersion, passwordHash, id trong body bị bỏ qua")
  void createUserIgnoresPrivilegedExtraFields() throws Exception {
    String username = fixtures.username("mass");
    String forgedId = ApiTestFixtures.missingId();
    String body =
        "{\"username\":\""
            + username
            + "\",\"email\":\""
            + username
            + "@itest.invalid\",\"password\":\""
            + VALID_PASSWORD
            + "\",\"fullName\":\"x\",\"roleAssignments\":[{\"role\":\"QA\"}],"
            + "\"status\":\"LOCKED\",\"tokenVersion\":99,\"passwordHash\":\"chu-nghia-gia\","
            + "\"id\":\""
            + forgedId
            + "\"}";

    MvcResult result =
        mockMvc
            .perform(postAs(admin, USERS, body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andReturn();

    User stored = mongo().findById(read(result, "$.id").toString(), User.class);
    assertThat(stored.getId()).isNotEqualTo(forgedId);
    assertThat(stored.getTokenVersion()).isZero();
    assertThat(stored.getPasswordHash()).startsWith("$2").isNotEqualTo("chu-nghia-gia");
    // Mật khẩu thật (không phải "chu-nghia-gia") phải đăng nhập được.
    mockMvc
        .perform(postAs(null, "/api/v1/auth/login", new LoginRequest(username, VALID_PASSWORD)))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName(
      "US-02/AC-1 (âm): roleAssignments rỗng/null, vai trò lạ, email sai, thiếu họ tên -> 400")
  void createUserValidation() throws Exception {
    CreateUserRequest noRoles = createRequest(fixtures.username("v"));
    noRoles.setRoleAssignments(List.of());
    mockMvc
        .perform(postAs(admin, USERS, noRoles))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("roleAssignments")));

    String name = fixtures.username("v");
    String unknownRole =
        "{\"username\":\""
            + name
            + "\",\"email\":\""
            + name
            + "@itest.invalid\","
            + "\"password\":\""
            + VALID_PASSWORD
            + "\",\"fullName\":\"x\","
            + "\"roleAssignments\":[{\"role\":\"SUPERUSER\"}]}";
    mockMvc.perform(postAs(admin, USERS, unknownRole)).andExpect(status().isBadRequest());

    for (String badEmail : new String[] {"khong-co-a-cong", "a b@c.d", "a@"}) {
      CreateUserRequest badMail = createRequest(fixtures.username("v"));
      badMail.setEmail(badEmail);
      mockMvc
          .perform(postAs(admin, USERS, badMail))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("email")));
    }

    CreateUserRequest noName = createRequest(fixtures.username("v"));
    noName.setFullName("   ");
    mockMvc
        .perform(postAs(admin, USERS, noName))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("fullName")));
  }

  // ------------------------------------------------------------------ US-40: body, ObjectId

  @Test
  @DisplayName("US-40/AC-1: body rỗng, {} và JSON hỏng trên POST /users và POST /auth/login -> 400")
  void emptyAndMalformedBodies() throws Exception {
    for (String url : new String[] {USERS, "/api/v1/auth/login"}) {
      String bearer = url.equals(USERS) ? admin : null;
      mockMvc
          .perform(postAs(bearer, url, null))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
      mockMvc.perform(postAs(bearer, url, "{")).andExpect(status().isBadRequest());
      mockMvc.perform(postAs(bearer, url, "[]")).andExpect(status().isBadRequest());
      mockMvc
          .perform(postAs(bearer, url, "{}"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("username")));
    }
  }

  @ParameterizedTest(name = "{0} -> 400")
  @ValueSource(strings = {"abc", "12345", "zzzzzzzzzzzzzzzzzzzzzzzz", "671dcba5f123456789abc7777"})
  @DisplayName(
      "US-40 (biên): ObjectId sai định dạng ở path của GET/PUT /users/{id}[/status] -> 400, không 500")
  void malformedUserIds(String id) throws Exception {
    UpdateUserRequest update =
        UpdateUserRequest.builder()
            .email("a@itest.invalid")
            .fullName("x")
            .roleAssignments(List.of(RoleAssignment.builder().role(Role.QA).build()))
            .build();
    mockMvc
        .perform(getAs(admin, USER_BY_ID, id))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    mockMvc.perform(putAs(admin, USER_BY_ID, update, id)).andExpect(status().isBadRequest());
    mockMvc
        .perform(putAs(admin, USER_STATUS, "{\"status\":\"LOCKED\"}", id))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName(
      "US-40: ObjectId hợp lệ nhưng không có user -> 404; trạng thái ngoài enum hoặc thiếu -> 400")
  void unknownUserAndInvalidStatusBodies() throws Exception {
    String missing = ApiTestFixtures.missingId();
    mockMvc
        .perform(putAs(admin, USER_STATUS, "{\"status\":\"LOCKED\"}", missing))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));

    User target = fixtures.user(Role.LECTURER, null);
    mockMvc
        .perform(putAs(admin, USER_STATUS, "{\"status\":\"DELETED\"}", target.getId()))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(putAs(admin, USER_STATUS, "{}", target.getId()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("status")));
    mockMvc
        .perform(putAs(admin, USER_STATUS, null, target.getId()))
        .andExpect(status().isBadRequest());
    assertThat(mongo().findById(target.getId(), User.class).getStatus())
        .isEqualTo(UserStatus.ACTIVE);
  }

  // ------------------------------------------------------------------ US-01/AC-4: nhật ký

  @Test
  @DisplayName(
      "US-01/AC-4: log của đăng nhập đúng, sai mật khẩu và tài khoản khóa không chứa mật khẩu hay băm")
  void logsNeverContainPasswordOrHash(CapturedOutput output) throws Exception {
    String secret = "Bi-mat-chi-co-trong-test-77";
    User user = fixtures.userWithPassword(Role.LECTURER, null, secret);

    mockMvc
        .perform(postAs(null, "/api/v1/auth/login", new LoginRequest(user.getUsername(), secret)))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            postAs(
                null,
                "/api/v1/auth/login",
                new LoginRequest(user.getUsername(), secret + "-sai-mat-khau")))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(postAs(null, "/api/v1/auth/login", new LoginRequest("khong_ton_tai_x", secret)))
        .andExpect(status().isUnauthorized());

    String logs = output.getAll();
    assertThat(logs).contains(user.getUsername()); // log có ghi nhận lần đăng nhập
    assertThat(logs).doesNotContain(secret).doesNotContain("passwordHash");
    assertThat(Pattern.compile("\\$2[aby]\\$\\d{2}\\$").matcher(logs).find())
        .as("băm BCrypt trong log")
        .isFalse();
  }
}
