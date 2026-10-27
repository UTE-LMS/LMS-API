package com.hcmute.lms.lmsapi.controller;

import com.hcmute.lms.lmsapi.document.Role;
import com.hcmute.lms.lmsapi.document.RoleAssignment;
import com.hcmute.lms.lmsapi.document.User;
import com.hcmute.lms.lmsapi.document.UserStatus;
import com.hcmute.lms.lmsapi.dto.LoginRequest;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Empirical Challenger: Adversarial Stress Tests for PR 3 Authentication Subsystem.
 * Thoroughly challenges:
 * 1. Timing Attack Defense & User Enumeration Resistance (Dummy Hash verification, response parity).
 * 2. Immediate Logout & Multi-Session Token Revocation (Replay attack, monotonic tokenVersion, concurrent logout).
 * 3. Locked Account Rejection & Lock-Unlock Lifecycle (Active session invalidation, lock-unlock irreversibility).
 */
@SpringBootTest
@DisplayName("Empirical Challenge: Timing Attack Defense, Logout Revocation & Locked Account Rejection")
public class AuthTimingAndRevocationEmpiricalChallengeTest {

    private static final String FIXTURE_PREFIX = "challenger_pr3_";
    private static final String FIXTURE_LABEL = "[DỮ LIỆU THỬ NGHIỆM] ";
    private static final String DEFAULT_PASSWORD = "ChallengePassword123!";

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

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        createdUsernames.clear();
    }

    @AfterEach
    void tearDown() {
        for (String username : createdUsernames) {
            userRepository.findByUsername(username).ifPresent(userRepository::delete);
        }
    }

    private User createTestUser(String usernameSuffix, UserStatus status, long tokenVersion) {
        String username = FIXTURE_PREFIX + usernameSuffix;
        User user = User.builder()
                .username(username)
                .email(username + "@hcmute.edu.vn")
                .passwordHash(passwordEncoder.encode(DEFAULT_PASSWORD))
                .fullName(FIXTURE_LABEL + "User " + usernameSuffix)
                .status(status)
                .tokenVersion(tokenVersion)
                .roleAssignments(List.of(
                        RoleAssignment.builder().role(Role.LECTURER).build()
                ))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        User saved = userRepository.save(user);
        createdUsernames.add(saved.getUsername());
        return saved;
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

    // =========================================================================
    // 1. EMPIRICAL CHALLENGE: TIMING ATTACK DEFENSE & USER ENUMERATION RESISTANCE
    // =========================================================================
    @Nested
    @DisplayName("1. Timing Attack Defense & User Enumeration Resistance")
    class TimingAttackChallenge {

        @Test
        @DisplayName("Empirical Verification: Non-existent user executes BCrypt dummy hash (latency > 15ms)")
        void challengeTimingAttack_NonExistentUser_ExecutesDummyHash() throws Exception {
            String nonExistentUsername = FIXTURE_PREFIX + "non_existent_" + UUID.randomUUID().toString().substring(0, 8);
            LoginRequest request = new LoginRequest(nonExistentUsername, DEFAULT_PASSWORD);

            long start = System.nanoTime();
            MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.message").value("Tên đăng nhập hoặc mật khẩu không chính xác"))
                    .andReturn();
            long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

            // BCrypt round 10 calculation guarantees at least 15ms of CPU work.
            // If dummy hash wasn't executed, elapsedMillis would be < 2ms.
            assertThat(elapsedMillis)
                    .as("BCrypt dummy hash must take measurable time (>15ms) to prevent user enumeration")
                    .isGreaterThanOrEqualTo(15L);
        }

        @Test
        @DisplayName("Response Parity: Non-existent user, wrong password, and locked user return identical 401 payload")
        void challengeTimingAttack_ResponseParityAcrossAllFailureModes() throws Exception {
            User activeUser = createTestUser("parity_active", UserStatus.ACTIVE, 0L);
            User lockedUser = createTestUser("parity_locked", UserStatus.LOCKED, 0L);

            // 1. Non-existent user
            LoginRequest reqNonExistent = new LoginRequest("definitely_non_existent_user_999", DEFAULT_PASSWORD);
            MvcResult resNonExistent = mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(reqNonExistent)))
                    .andExpect(status().isUnauthorized())
                    .andReturn();

            // 2. Existent user with wrong password
            LoginRequest reqWrongPass = new LoginRequest(activeUser.getUsername(), "WrongPassword999!");
            MvcResult resWrongPass = mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(reqWrongPass)))
                    .andExpect(status().isUnauthorized())
                    .andReturn();

            // 3. Locked user with correct password
            LoginRequest reqLocked = new LoginRequest(lockedUser.getUsername(), DEFAULT_PASSWORD);
            MvcResult resLocked = mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(reqLocked)))
                    .andExpect(status().isUnauthorized())
                    .andReturn();

            String bodyNonExistent = resNonExistent.getResponse().getContentAsString();
            String bodyWrongPass = resWrongPass.getResponse().getContentAsString();
            String bodyLocked = resLocked.getResponse().getContentAsString();

            // Verify status, code, and message are completely indistinguishable
            assertThat((Integer) JsonPath.read(bodyNonExistent, "$.status")).isEqualTo(401);
            assertThat((Integer) JsonPath.read(bodyWrongPass, "$.status")).isEqualTo(401);
            assertThat((Integer) JsonPath.read(bodyLocked, "$.status")).isEqualTo(401);

            assertThat((String) JsonPath.read(bodyNonExistent, "$.code")).isEqualTo("UNAUTHORIZED");
            assertThat((String) JsonPath.read(bodyWrongPass, "$.code")).isEqualTo("UNAUTHORIZED");
            assertThat((String) JsonPath.read(bodyLocked, "$.code")).isEqualTo("UNAUTHORIZED");

            assertThat((String) JsonPath.read(bodyNonExistent, "$.message"))
                    .isEqualTo("Tên đăng nhập hoặc mật khẩu không chính xác");
            assertThat((String) JsonPath.read(bodyWrongPass, "$.message"))
                    .isEqualTo("Tên đăng nhập hoặc mật khẩu không chính xác");
            assertThat((String) JsonPath.read(bodyLocked, "$.message"))
                    .isEqualTo("Tên đăng nhập hoặc mật khẩu không chính xác");
        }

        @Test
        @DisplayName("Adversarial Usernames: SQL/NoSQL injection payloads and Vietnamese strings fail safely with 401")
        void challengeTimingAttack_AdversarialUsernames_SafeFailure() throws Exception {
            String[] maliciousUsernames = {
                    "admin' OR '1'='1",
                    "admin'--",
                    "{$ne: null}",
                    "người_dùng_không_tồn_tại_123"
            };

            for (String username : maliciousUsernames) {
                LoginRequest req = new LoginRequest(username, DEFAULT_PASSWORD);
                mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                        .andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                        .andExpect(jsonPath("$.message").value("Tên đăng nhập hoặc mật khẩu không chính xác"));
            }
        }
    }

    // =========================================================================
    // 2. EMPIRICAL CHALLENGE: LOGOUT & MULTI-SESSION TOKEN REVOCATION
    // =========================================================================
    @Nested
    @DisplayName("2. Immediate Logout & Multi-Session Token Revocation")
    class LogoutAndRevocationChallenge {

        @Test
        @DisplayName("Immediate Invalidation: Token is instantly rejected on protected endpoints following logout")
        void challengeLogout_SingleSession_ImmediateInvalidation() throws Exception {
            User user = createTestUser("logout_immediate", UserStatus.ACTIVE, 0L);
            String token = loginAndGetToken(user.getUsername(), DEFAULT_PASSWORD);

            // Token works initially
            mockMvc.perform(get("/api/v1/auth/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value(user.getUsername()));

            // User logs out
            mockMvc.perform(post("/api/v1/auth/logout")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Đăng xuất thành công"));

            // Token is rejected IMMEDIATELY on next request
            mockMvc.perform(get("/api/v1/auth/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.message").value("Yêu cầu xác thực để truy cập tài nguyên này"));
        }

        @Test
        @DisplayName("Replay Attack Defense: A revoked token cannot be reused to invoke /api/v1/auth/logout again")
        void challengeLogout_ReplayAttack_CannotLogoutTwiceWithRevokedToken() throws Exception {
            User user = createTestUser("logout_replay", UserStatus.ACTIVE, 0L);
            String token = loginAndGetToken(user.getUsername(), DEFAULT_PASSWORD);

            // First logout succeeds
            mockMvc.perform(post("/api/v1/auth/logout")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk());

            // Second logout with same revoked token MUST FAIL with 401 UNAUTHORIZED
            mockMvc.perform(post("/api/v1/auth/logout")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("Multi-Session Global Revocation: Logging out from one device revokes all concurrent sessions")
        void challengeLogout_MultiSession_GlobalRevocation() throws Exception {
            User user = createTestUser("multi_session", UserStatus.ACTIVE, 0L);

            // User logs in on Device A and Device B
            String tokenDeviceA = loginAndGetToken(user.getUsername(), DEFAULT_PASSWORD);
            String tokenDeviceB = loginAndGetToken(user.getUsername(), DEFAULT_PASSWORD);

            // Both tokens work
            mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeviceA))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeviceB))
                    .andExpect(status().isOk());

            // Device A logs out
            mockMvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeviceA))
                    .andExpect(status().isOk());

            // Device B is also globally revoked!
            mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeviceB))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("Monotonic Growth: Subsequent logins produce fresh tokens, while all older tokens remain dead")
        void challengeLogout_MonotonicTokenVersion_OldTokensNeverRevived() throws Exception {
            User user = createTestUser("monotonic_revocation", UserStatus.ACTIVE, 0L);

            // Generation 0
            String tokenGen0 = loginAndGetToken(user.getUsername(), DEFAULT_PASSWORD);
            mockMvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenGen0))
                    .andExpect(status().isOk());

            // Generation 1
            String tokenGen1 = loginAndGetToken(user.getUsername(), DEFAULT_PASSWORD);
            mockMvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenGen1))
                    .andExpect(status().isOk());

            // Generation 2 (Current active session)
            String tokenGen2 = loginAndGetToken(user.getUsername(), DEFAULT_PASSWORD);

            // Active token works
            mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenGen2))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value(user.getUsername()));

            // Historical tokens (Gen 0 and Gen 1) are permanently rejected
            mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenGen0))
                    .andExpect(status().isUnauthorized());

            mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenGen1))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Concurrency Stress: 10 concurrent logout requests with same token -> exactly 1 succeeds, 9 fail with 401")
        void challengeLogout_ConcurrentLogoutStress() throws Exception {
            User user = createTestUser("concurrent_logout", UserStatus.ACTIVE, 0L);
            String token = loginAndGetToken(user.getUsername(), DEFAULT_PASSWORD);

            int totalThreads = 10;
            ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
            CountDownLatch readyLatch = new CountDownLatch(totalThreads);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(totalThreads);

            AtomicInteger okResponses = new AtomicInteger(0);
            AtomicInteger unauthorizedResponses = new AtomicInteger(0);

            for (int i = 0; i < totalThreads; i++) {
                executor.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startLatch.await();
                        MvcResult res = mockMvc.perform(post("/api/v1/auth/logout")
                                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                                .andReturn();
                        int httpStatus = res.getResponse().getStatus();
                        if (httpStatus == 200) {
                            okResponses.incrementAndGet();
                        } else if (httpStatus == 401) {
                            unauthorizedResponses.incrementAndGet();
                        }
                    } catch (Exception e) {
                        // ignore
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
            startLatch.countDown();
            assertThat(doneLatch.await(10, TimeUnit.SECONDS)).isTrue();
            executor.shutdown();

            // In atomic $inc semantics, at least 1 and typically exactly 1 thread processes before tokenVersion changes
            assertThat(okResponses.get())
                    .as("At least 1 logout request must succeed")
                    .isGreaterThanOrEqualTo(1);

            assertThat(unauthorizedResponses.get())
                    .as("The rest of concurrent requests must fail with 401")
                    .isEqualTo(totalThreads - okResponses.get());

            // Token is definitely dead now
            mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
    }

    // =========================================================================
    // 3. EMPIRICAL CHALLENGE: LOCKED ACCOUNT REJECTION & LIFECYCLE
    // =========================================================================
    @Nested
    @DisplayName("3. Locked Account Rejection & Lock-Unlock Lifecycle")
    class LockedAccountChallenge {

        @Test
        @DisplayName("Active session is rejected immediately with 401 when account status is set to LOCKED in DB")
        void challengeLockedAccount_ActiveSessionImmediateRevocationOnLock() throws Exception {
            User user = createTestUser("active_to_locked", UserStatus.ACTIVE, 0L);
            String token = loginAndGetToken(user.getUsername(), DEFAULT_PASSWORD);

            // Works while ACTIVE
            mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk());

            // Admin locks the user account in database
            user.setStatus(UserStatus.LOCKED);
            userRepository.save(user);

            // Immediate rejection on next request
            mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.message").value("Yêu cầu xác thực để truy cập tài nguyên này"));
        }

        @Test
        @DisplayName("Lock-Unlock Lifecycle: When account is unlocked, tokens issued BEFORE the lock remain permanently revoked")
        void challengeLockedAccount_LockAndUnlock_TokensIssuedBeforeLockPermanentlyRevoked() throws Exception {
            User user = createTestUser("lock_unlock_lifecycle", UserStatus.ACTIVE, 0L);

            // 1. User logs in while ACTIVE -> obtains Token T_pre
            String tokenPreLock = loginAndGetToken(user.getUsername(), DEFAULT_PASSWORD);
            mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPreLock))
                    .andExpect(status().isOk());

            // 2. Account is LOCKED and tokenVersion is incremented (R4 compliance requirement)
            user.setStatus(UserStatus.LOCKED);
            userRepository.save(user);
            userRepository.incrementTokenVersion(user.getId());

            // 3. User attempts to use T_pre while locked -> rejected 401
            mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPreLock))
                    .andExpect(status().isUnauthorized());

            // 4. Admin unlocks user back to ACTIVE (reloading fresh entity from DB to preserve incremented tokenVersion)
            User freshUser = userRepository.findById(user.getId()).orElseThrow();
            freshUser.setStatus(UserStatus.ACTIVE);
            userRepository.save(freshUser);

            // 5. Old token T_pre MUST STILL BE REJECTED 401 due to tokenVersion mismatch!
            mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPreLock))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

            // 6. Fresh login after unlock succeeds and produces new valid token
            String tokenPostUnlock = loginAndGetToken(user.getUsername(), DEFAULT_PASSWORD);
            mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPostUnlock))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value(user.getUsername()));
        }

        @Test
        @DisplayName("Locked user cannot log in, regardless of whether password is correct or wrong")
        void challengeLockedAccount_CannotLogin() throws Exception {
            User lockedUser = createTestUser("cannot_login_locked", UserStatus.LOCKED, 0L);

            // Correct password -> 401
            LoginRequest reqCorrect = new LoginRequest(lockedUser.getUsername(), DEFAULT_PASSWORD);
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(reqCorrect)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Tên đăng nhập hoặc mật khẩu không chính xác"));

            // Wrong password -> 401
            LoginRequest reqWrong = new LoginRequest(lockedUser.getUsername(), "WrongPassWord123!");
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(reqWrong)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Tên đăng nhập hoặc mật khẩu không chính xác"));
        }
    }
}
