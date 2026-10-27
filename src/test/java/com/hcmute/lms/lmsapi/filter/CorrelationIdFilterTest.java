package com.hcmute.lms.lmsapi.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CorrelationIdFilter: Distributed Tracing & MDC Filter Unit Tests")
public class CorrelationIdFilterTest {

    private CorrelationIdFilter filter;

    @BeforeEach
    void setUp() {
        filter = new CorrelationIdFilter();
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("Filter generates new UUID and sets MDC and response header when header is missing")
    void testMissingHeaderGeneratesCorrelationId() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> mdcDuringChain = new AtomicReference<>();

        FilterChain chain = (req, res) -> {
            mdcDuringChain.set(MDC.get(CorrelationIdFilter.MDC_KEY));
        };

        filter.doFilter(request, response, chain);

        String generatedId = response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
        assertThat(generatedId).isNotBlank();
        assertThat(mdcDuringChain.get()).isEqualTo(generatedId);

        // Verifies MDC is cleared after request processing finishes
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("Filter preserves client-provided X-Correlation-Id header")
    void testExistingHeaderPreserved() throws ServletException, IOException {
        String existingId = "client-trace-id-12345";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, existingId);
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> mdcDuringChain = new AtomicReference<>();

        FilterChain chain = (req, res) -> {
            mdcDuringChain.set(MDC.get(CorrelationIdFilter.MDC_KEY));
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).isEqualTo(existingId);
        assertThat(mdcDuringChain.get()).isEqualTo(existingId);

        // Verifies MDC is cleared after request finishes
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("Filter generates new UUID when incoming header is blank or whitespace")
    void testWhitespaceHeaderReplacedWithGeneratedId() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "   \t   ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> mdcDuringChain = new AtomicReference<>();

        FilterChain chain = (req, res) -> {
            mdcDuringChain.set(MDC.get(CorrelationIdFilter.MDC_KEY));
        };

        filter.doFilter(request, response, chain);

        String headerValue = response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
        assertThat(headerValue).isNotBlank();
        assertThat(headerValue).isNotEqualTo("   \t   ");
        assertThat(mdcDuringChain.get()).isEqualTo(headerValue);
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("Adversarial: When filter chain throws exception, MDC is guaranteed cleaned up in finally block")
    void testMdcCleanedUpEvenWhenChainThrowsException() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "stress-test-id");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> mdcDuringChain = new AtomicReference<>();

        FilterChain chain = (req, res) -> {
            mdcDuringChain.set(MDC.get(CorrelationIdFilter.MDC_KEY));
            throw new RuntimeException("Simulated catastrophic failure in downstream filter/controller");
        };

        assertThatThrownBy(() -> filter.doFilter(request, response, chain))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Simulated catastrophic failure");

        // MDC was set during the chain
        assertThat(mdcDuringChain.get()).isEqualTo("stress-test-id");

        // MDC must be completely cleared despite unhandled exception
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }
}
