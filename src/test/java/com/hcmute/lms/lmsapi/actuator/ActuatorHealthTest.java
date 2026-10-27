package com.hcmute.lms.lmsapi.actuator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@TestPropertySource(properties = {
        "management.endpoints.web.exposure.include=health,info",
        "management.endpoint.health.show-details=always",
        "management.info.env.enabled=true",
        "info.app.name=LMS-API",
        "info.app.description=Testing Actuator Endpoints",
        "info.app.version=0.0.1-SNAPSHOT"
})
@DisplayName("US-39: Actuator Health & Info Security Verification")
class ActuatorHealthTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    // =========================================================================
    // 1. PUBLIC ACTUATOR ENDPOINTS (US-39/AC-1: PERMITALL)
    // =========================================================================

    @Nested
    @DisplayName("Permitted Actuator Endpoints (Unauthenticated Access)")
    class PermittedEndpointsTests {

        @Test
        @DisplayName("GET /actuator/health is accessible without authentication (HTTP 200, status UP)")
        void healthEndpointShouldBePermittedWithoutAuthentication() throws Exception {
            mockMvc.perform(get("/actuator/health")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", anyOf(is("UP"), is("DOWN"))))
                    .andExpect(jsonPath("$.components").exists());
        }

        @Test
        @DisplayName("GET /actuator/health/** subpaths are accessible without authentication")
        void healthSubpathsShouldBePermittedWithoutAuthentication() throws Exception {
            mockMvc.perform(get("/actuator/health/mongo")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        // Either 200 or 503 or 404, but MUST NOT be 401 Unauthorized
                        assertThat(status).isNotEqualTo(401);
                    });
        }

        @Test
        @DisplayName("GET /actuator/info is accessible without authentication (HTTP 200, info fields present)")
        void infoEndpointShouldBePermittedWithoutAuthentication() throws Exception {
            mockMvc.perform(get("/actuator/info")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.app.name").value("LMS-API"))
                    .andExpect(jsonPath("$.app.version").value("0.0.1-SNAPSHOT"));
        }
    }

    // =========================================================================
    // 2. ADVERSARIAL SECURITY: PROTECTED ENDPOINTS (FAIL-SECURE HTTP 401)
    // =========================================================================

    @Nested
    @DisplayName("Adversarial Access: Protected Endpoints Must Reject Unauthenticated Calls (HTTP 401)")
    class ProtectedEndpointsTests {

        @Test
        @DisplayName("GET /actuator/beans unauthenticated -> HTTP 401 Unauthorized with standard ErrorResponse")
        void beansEndpointMustRequireAuthentication() throws Exception {
            mockMvc.perform(get("/actuator/beans")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.message").value("Yêu cầu xác thực để truy cập tài nguyên này"))
                    .andExpect(jsonPath("$.path").value("/actuator/beans"));
        }

        @Test
        @DisplayName("GET /actuator/env unauthenticated -> HTTP 401 Unauthorized with standard ErrorResponse")
        void envEndpointMustRequireAuthentication() throws Exception {
            mockMvc.perform(get("/actuator/env")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.message").value("Yêu cầu xác thực để truy cập tài nguyên này"))
                    .andExpect(jsonPath("$.path").value("/actuator/env"));
        }

        @Test
        @DisplayName("GET /api/v1/protected unauthenticated -> HTTP 401 Unauthorized with standard ErrorResponse")
        void arbitraryApiEndpointMustRequireAuthentication() throws Exception {
            mockMvc.perform(get("/api/v1/protected")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.message").value("Yêu cầu xác thực để truy cập tài nguyên này"))
                    .andExpect(jsonPath("$.path").value("/api/v1/protected"))
                    .andExpect(jsonPath("$.timestamp").isNotEmpty());
        }
    }
}
