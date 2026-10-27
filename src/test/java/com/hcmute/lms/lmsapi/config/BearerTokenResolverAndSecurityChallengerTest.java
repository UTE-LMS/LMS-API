package com.hcmute.lms.lmsapi.config;

import com.hcmute.lms.lmsapi.document.Role;
import com.hcmute.lms.lmsapi.document.RoleAssignment;
import com.hcmute.lms.lmsapi.document.User;
import com.hcmute.lms.lmsapi.document.UserScope;
import com.hcmute.lms.lmsapi.document.UserStatus;
import com.hcmute.lms.lmsapi.dto.ErrorResponse;
import com.hcmute.lms.lmsapi.dto.LoginRequest;
import com.hcmute.lms.lmsapi.exception.ErrorCode;
import com.hcmute.lms.lmsapi.filter.CorrelationIdFilter;
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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Empirical Challenge Suite (Challenger 2 - PR 3):
 * 1. BearerTokenResolver stress testing with expired, malformed, malicious and boundary tokens.
 * 2. PermitAll Actuator (/actuator/health, /actuator/info) vs Non-PermitAll Actuator endpoints.
 * 3. AuthenticationEntryPoint ErrorResponse contract compliance and X-Correlation-Id tracking.
 */
@SpringBootTest
@DisplayName("Empirical Challenge: BearerTokenResolver, Actuator Security & AuthenticationEntryPoint")
public class BearerTokenResolverAndSecurityChallengerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private BearerTokenResolver bearerTokenResolver;

    @Autowired
    private AuthenticationEntryPoint authenticationEntryPoint;

    @Autowired
    private CorrelationIdFilter correlationIdFilter;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    private static final String CHALLENGE_ACTIVE_USER = "challenger_sec_active";
    private static final String CHALLENGE_LOCKED_USER = "challenger_sec_locked";
    private static final String CHALLENGE_PASSWORD = "ChallengePassword123!";

    private User activeUser;
    private User lockedUser;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters(correlationIdFilter)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        cleanup();

        activeUser = userRepository.save(User.builder()
                .username(CHALLENGE_ACTIVE_USER)
                .email("challenger_active@hcmute.edu.vn")
                .passwordHash(passwordEncoder.encode(CHALLENGE_PASSWORD))
                .fullName("[DỮ LIỆU THỬ NGHIỆM] Challenger Active User")
                .status(UserStatus.ACTIVE)
                .tokenVersion(0L)
                .roleAssignments(List.of(
                        RoleAssignment.builder()
                                .role(Role.LECTURER)
                                .scope(UserScope.builder().build())
                                .build()
                ))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());

        lockedUser = userRepository.save(User.builder()
                .username(CHALLENGE_LOCKED_USER)
                .email("challenger_locked@hcmute.edu.vn")
                .passwordHash(passwordEncoder.encode(CHALLENGE_PASSWORD))
                .fullName("[DỮ LIỆU THỬ NGHIỆM] Challenger Locked User")
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
        cleanup();
    }

    private void cleanup() {
        userRepository.findByUsername(CHALLENGE_ACTIVE_USER).ifPresent(userRepository::delete);
        userRepository.findByUsername(CHALLENGE_LOCKED_USER).ifPresent(userRepository::delete);
    }

    // =========================================================================
    // SUITE 1: DIRECT UNIT CHALLENGE OF BearerTokenResolver
    // =========================================================================
    @Nested
    @DisplayName("1. Direct Unit Challenge on BearerTokenResolver Bean")
    class BearerTokenResolverDirectUnitChallenge {

        @Test
        @DisplayName("OPTIONS preflight requests on ANY path always resolve to null (bypassing token resolution)")
        void optionsMethod_AlwaysResolvesToNull() {
            MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/auth/me");
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer some_dummy_token");

            String resolved = bearerTokenResolver.resolve(request);
            assertThat(resolved).isNull();

            MockHttpServletRequest protectedPostOption = new MockHttpServletRequest("options", "/actuator/beans");
            protectedPostOption.addHeader(HttpHeaders.AUTHORIZATION, "Bearer another_token");
            assertThat(bearerTokenResolver.resolve(protectedPostOption)).isNull();
        }

        @ParameterizedTest(name = "permitAll URI: {0}")
        @ValueSource(strings = {
                "/api/v1/auth/login",
                "/api/v1/auth/login/",
                "/actuator/info",
                "/actuator/info/",
                "/actuator/health",
                "/actuator/health/",
                "/actuator/health/liveness",
                "/actuator/health/readiness",
                "/actuator/health/custom/nested/probe"
        })
        @DisplayName("PermitAll URIs resolve to null even when Authorization header contains Bearer token")
        void permitAllUris_ResolveToNull(String uri) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer invalid_or_expired_jwt_token");

            String resolved = bearerTokenResolver.resolve(request);
            assertThat(resolved)
                    .as("Expected URI '%s' to resolve to null in BearerTokenResolver", uri)
                    .isNull();
        }

        @ParameterizedTest(name = "Protected URI: {0}")
        @ValueSource(strings = {
                "/api/v1/auth/me",
                "/api/v1/auth/logout",
                "/api/v1/users",
                "/actuator",
                "/actuator/beans",
                "/actuator/env",
                "/actuator/healthcheck",
                "/actuator/metrics",
                "/api/v1/auth/login_attempt"
        })
        @DisplayName("Protected URIs correctly resolve Bearer token from header")
        void protectedUris_ResolveToken(String uri) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
            String rawToken = "sample.jwt.token.value";
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + rawToken);

            String resolved = bearerTokenResolver.resolve(request);
            assertThat(resolved)
                    .as("Expected protected URI '%s' to resolve raw token", uri)
                    .isEqualTo(rawToken);
        }

        @Test
        @DisplayName("Request with null URI does not throw exception and delegates gracefully")
        void nullUri_DoesNotThrow() {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", null);
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer sample_token");

            String resolved = bearerTokenResolver.resolve(request);
            assertThat(resolved).isEqualTo("sample_token");
        }
    }

    // =========================================================================
    // SUITE 2: LOGIN WITH MALFORMED & EXPIRED TOKENS (FILTER CHAIN STRESS)
    // =========================================================================
    @Nested
    @DisplayName("2. Login with Malformed, Expired and Adversarial Tokens")
    class LoginWithMalformedAndExpiredTokensMockMvcChallenge {

        private static final String EXPIRED_JWT =
                "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTYiLCJleHAiOjE1MTYyMzkwMjJ9.4dc...invalid";

        private static final String MALFORMED_JWT_RANDOM_CHARS = "random.unparseable.jwt-structure#@!";
        private static final String MALFORMED_JWT_NO_DOTS = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9";
        private static final String MALFORMED_JWT_FOUR_PARTS = "part1.part2.part3.part4";
        private static final String MALFORMED_JWT_INVALID_BASE64 = "!!!.@@@.###";
        private static final String SQLI_INJECTION_TOKEN = "' OR '1'='1' -- ";
        private static final String UNICODE_EMOJI_TOKEN = "🔒_secret_🔑_token_✨";

        @ParameterizedTest(name = "Adversarial Bearer token: {0}")
        @ValueSource(strings = {
                EXPIRED_JWT,
                MALFORMED_JWT_RANDOM_CHARS,
                MALFORMED_JWT_NO_DOTS,
                MALFORMED_JWT_FOUR_PARTS,
                MALFORMED_JWT_INVALID_BASE64,
                SQLI_INJECTION_TOKEN,
                UNICODE_EMOJI_TOKEN
        })
        @DisplayName("POST /api/v1/auth/login ignores adversarial/malformed Bearer tokens and processes login successfully")
        void login_WithAdversarialTokens_Succeeds(String adversarialToken) throws Exception {
            LoginRequest loginRequest = new LoginRequest(CHALLENGE_ACTIVE_USER, CHALLENGE_PASSWORD);

            mockMvc.perform(post("/api/v1/auth/login")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adversarialToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.expiresIn").value(3600))
                    .andExpect(jsonPath("$.user.username").value(CHALLENGE_ACTIVE_USER));
        }

        @Test
        @DisplayName("POST /api/v1/auth/login with massive 16KB token header still succeeds (does not trigger JWT parsing 401)")
        void login_WithOversizedToken_Succeeds() throws Exception {
            String massiveToken = "A".repeat(16384);
            LoginRequest loginRequest = new LoginRequest(CHALLENGE_ACTIVE_USER, CHALLENGE_PASSWORD);

            mockMvc.perform(post("/api/v1/auth/login")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + massiveToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty());
        }

        @Test
        @DisplayName("POST /api/v1/auth/login with empty Bearer header ('Bearer ') still succeeds")
        void login_WithEmptyBearer_Succeeds() throws Exception {
            LoginRequest loginRequest = new LoginRequest(CHALLENGE_ACTIVE_USER, CHALLENGE_PASSWORD);

            mockMvc.perform(post("/api/v1/auth/login")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer ")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty());
        }

        @Test
        @DisplayName("POST /api/v1/auth/login with query params and expired token still succeeds")
        void login_WithQueryParamsAndExpiredToken_Succeeds() throws Exception {
            LoginRequest loginRequest = new LoginRequest(CHALLENGE_ACTIVE_USER, CHALLENGE_PASSWORD);

            mockMvc.perform(post("/api/v1/auth/login?source=mobile_client&ref=homepage")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + EXPIRED_JWT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty());
        }

        @Test
        @DisplayName("POST /api/v1/auth/login with expired token but WRONG credentials returns 401 from AuthController (not JWT filter)")
        void login_WithExpiredTokenAndWrongCredentials_ReturnsBusiness401() throws Exception {
            LoginRequest wrongRequest = new LoginRequest(CHALLENGE_ACTIVE_USER, "WrongPassword999!");

            mockMvc.perform(post("/api/v1/auth/login")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + EXPIRED_JWT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(wrongRequest)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.message").value("Tên đăng nhập hoặc mật khẩu không chính xác"))
                    .andExpect(jsonPath("$.path").value("/api/v1/auth/login"));
        }

        @Test
        @DisplayName("POST /api/v1/auth/login with expired token and LOCKED user returns 401 with generic message")
        void login_WithExpiredTokenAndLockedUser_ReturnsBusiness401() throws Exception {
            LoginRequest lockedRequest = new LoginRequest(CHALLENGE_LOCKED_USER, CHALLENGE_PASSWORD);

            mockMvc.perform(post("/api/v1/auth/login")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + EXPIRED_JWT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(lockedRequest)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.message").value("Tên đăng nhập hoặc mật khẩu không chính xác"));
        }
    }

    // =========================================================================
    // SUITE 3: PERMIT-ALL ACTUATOR VS NON-PERMIT-ALL ACTUATOR ENDPOINTS
    // =========================================================================
    @Nested
    @DisplayName("3. PermitAll Actuator vs Non-PermitAll Endpoints")
    class PermitAllVsNonPermitAllActuatorChallenge {

        @Test
        @DisplayName("/actuator/health is accessible unauthenticated and returns 200 OK")
        void actuatorHealth_Unauthenticated_Returns200() throws Exception {
            mockMvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("/actuator/health with malformed Bearer token is accessible and returns 200 OK")
        void actuatorHealth_WithMalformedToken_Returns200() throws Exception {
            mockMvc.perform(get("/actuator/health")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer malformed.token.value"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("/actuator/info is accessible unauthenticated and returns 200 OK")
        void actuatorInfo_Unauthenticated_Returns200() throws Exception {
            mockMvc.perform(get("/actuator/info"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("/actuator/info with malformed Bearer token is accessible and returns 200 OK")
        void actuatorInfo_WithMalformedToken_Returns200() throws Exception {
            mockMvc.perform(get("/actuator/info")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer malformed.token.value"))
                    .andExpect(status().isOk());
        }

        @ParameterizedTest(name = "Non-permitAll endpoint: {0}")
        @ValueSource(strings = {
                "/actuator",
                "/actuator/beans",
                "/actuator/env",
                "/actuator/metrics",
                "/actuator/mappings",
                "/actuator/configprops",
                "/actuator/loggers",
                "/actuator/nonexistent"
        })
        @DisplayName("Non-permitAll Actuator endpoints reject unauthenticated requests with HTTP 401 UNAUTHORIZED")
        void nonPermitAllActuatorEndpoints_Unauthenticated_Return401(String endpoint) throws Exception {
            mockMvc.perform(get(endpoint))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.path").value(endpoint));
        }

        @Test
        @DisplayName("/actuator/beans with malformed token is rejected with HTTP 401 UNAUTHORIZED")
        void protectedActuator_WithMalformedToken_Returns401() throws Exception {
            mockMvc.perform(get("/actuator/beans")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer invalid_signature_token"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.path").value("/actuator/beans"));
        }
    }

    // =========================================================================
    // SUITE 4: AuthenticationEntryPoint ErrorResponse & CORRELATION ID
    // =========================================================================
    @Nested
    @DisplayName("4. AuthenticationEntryPoint ErrorResponse & X-Correlation-Id Challenge")
    class AuthenticationEntryPointErrorResponseChallenge {

        @Test
        @DisplayName("Unauthenticated GET /api/v1/auth/me returns standard ErrorResponse structure with status=401, code=UNAUTHORIZED")
        void unauthenticatedMe_ReturnsStandardErrorResponseStructure() throws Exception {
            MvcResult result = mockMvc.perform(get("/api/v1/auth/me"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().exists(CorrelationIdFilter.CORRELATION_ID_HEADER))
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.message").value("Yêu cầu xác thực để truy cập tài nguyên này"))
                    .andExpect(jsonPath("$.path").value("/api/v1/auth/me"))
                    .andExpect(jsonPath("$.timestamp").isNotEmpty())
                    .andExpect(jsonPath("$.fieldErrors").doesNotExist())
                    .andReturn();

            String correlationId = result.getResponse().getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
            assertThat(correlationId).isNotBlank();
            // Validate correlationId is a valid UUID
            assertThat(UUID.fromString(correlationId)).isNotNull();

            // Validate timestamp parses as ISO-8601 Instant within recent 10 seconds
            String timestampStr = JsonPath.read(result.getResponse().getContentAsString(), "$.timestamp");
            Instant parsedTimestamp = Instant.parse(timestampStr);
            assertThat(parsedTimestamp).isBetween(Instant.now().minusSeconds(10), Instant.now().plusSeconds(2));
        }

        @Test
        @DisplayName("Client-provided X-Correlation-Id is preserved in response header on 401 Unauthorized")
        void customCorrelationId_PreservedOnUnauthorized() throws Exception {
            String customCorrId = UUID.randomUUID().toString();

            mockMvc.perform(get("/api/v1/auth/me")
                            .header(CorrelationIdFilter.CORRELATION_ID_HEADER, customCorrId))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string(CorrelationIdFilter.CORRELATION_ID_HEADER, customCorrId))
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("Unauthenticated POST /api/v1/auth/logout returns standard ErrorResponse with path=/api/v1/auth/logout")
        void unauthenticatedLogout_ReturnsStandardErrorResponse() throws Exception {
            mockMvc.perform(post("/api/v1/auth/logout"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().exists(CorrelationIdFilter.CORRELATION_ID_HEADER))
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.path").value("/api/v1/auth/logout"));
        }

        @Test
        @DisplayName("Protected endpoint with malformed Bearer token returns 401 ErrorResponse")
        void protectedEndpoint_WithMalformedToken_Returns401ErrorResponse() throws Exception {
            mockMvc.perform(get("/api/v1/auth/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer not.a.real.jwt"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().exists(CorrelationIdFilter.CORRELATION_ID_HEADER))
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.path").value("/api/v1/auth/me"));
        }

        @Test
        @DisplayName("Protected endpoint with Basic Auth header returns 401 ErrorResponse")
        void protectedEndpoint_WithBasicAuthHeader_Returns401ErrorResponse() throws Exception {
            mockMvc.perform(get("/api/v1/auth/me")
                            .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNzd29yZA=="))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().exists(CorrelationIdFilter.CORRELATION_ID_HEADER))
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.path").value("/api/v1/auth/me"));
        }

        @Test
        @DisplayName("Direct unit verification of AuthenticationEntryPoint bean: builds and writes compliant ErrorResponse")
        void directAuthenticationEntryPoint_UnitChallenge() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
            MockHttpServletResponse response = new MockHttpServletResponse();
            AuthenticationException exception = new BadCredentialsException("Token invalid or expired");

            authenticationEntryPoint.commence(request, response, exception);

            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
            assertThat(response.getCharacterEncoding()).isEqualTo("UTF-8");

            String body = response.getContentAsString();
            ErrorResponse errorResponse = objectMapper.readValue(body, ErrorResponse.class);

            assertThat(errorResponse).isNotNull();
            assertThat(errorResponse.getStatus()).isEqualTo(401);
            assertThat(errorResponse.getCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
            assertThat(errorResponse.getMessage()).isEqualTo("Yêu cầu xác thực để truy cập tài nguyên này");
            assertThat(errorResponse.getPath()).isEqualTo("/api/v1/auth/me");
            assertThat(errorResponse.getTimestamp()).isNotNull();
            assertThat(errorResponse.getFieldErrors()).isNull();
        }
    }
}
