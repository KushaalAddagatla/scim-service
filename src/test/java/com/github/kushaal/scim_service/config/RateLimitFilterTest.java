package com.github.kushaal.scim_service.config;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.github.kushaal.scim_service.model.ScimConstants;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RateLimitFilter}.
 *
 * <p>Uses a capacity of 3 so the test runs in milliseconds without waiting
 * for real-time refill. Each test gets a fresh filter instance, so buckets
 * never bleed between test methods.
 */
class RateLimitFilterTest {

    private static final long TEST_CAPACITY = 3;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private RateLimitFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter(objectMapper, TEST_CAPACITY, TEST_CAPACITY);
        chain = mock(FilterChain.class);
    }

    @Test
    void requestUnderLimit_delegatesToFilterChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain, times(1)).doFilter(request, response);
        // No 429 — default MockHttpServletResponse status is 200
        assertThat(response.getStatus()).isNotEqualTo(429);
    }

    @Test
    void requestOverLimit_returns429WithScimErrorBody() throws Exception {
        // Exhaust all tokens from 127.0.0.1
        for (int i = 0; i < TEST_CAPACITY; i++) {
            filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);
        }

        // Next request should be rate-limited
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);

        // Filter chain must NOT be invoked for the rate-limited request
        verify(chain, times((int) TEST_CAPACITY)).doFilter(any(), any());

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentType()).contains(ScimConstants.SCIM_CONTENT_TYPE);

        Map<String, Object> body = objectMapper.readValue(
                response.getContentAsString(), new TypeReference<>() {});
        assertThat(body.get("status")).isEqualTo(429);
        assertThat(body.get("scimType")).isEqualTo("tooMany");
        assertThat(body.get("schemas")).isEqualTo(List.of(ScimConstants.SCHEMA_ERROR));
        assertThat((String) body.get("detail")).contains("Rate limit exceeded");
    }

    // ── X-Forwarded-For (proxy / ngrok) tests ─────────────────────────────────

    @Test
    void xForwardedFor_usedInsteadOfRemoteAddr() throws Exception {
        // Exhaust the bucket for the real client IP sent via X-Forwarded-For
        for (int i = 0; i < TEST_CAPACITY; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest();
            req.addHeader("X-Forwarded-For", "203.0.113.5");
            filter.doFilter(req, new MockHttpServletResponse(), chain);
        }

        // Same XFF IP — should now be rate-limited even though remoteAddr differs
        MockHttpServletRequest rateLimited = new MockHttpServletRequest();
        rateLimited.setRemoteAddr("127.0.0.1");      // proxy address
        rateLimited.addHeader("X-Forwarded-For", "203.0.113.5");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(rateLimited, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
    }

    @Test
    void xForwardedFor_multipleProxies_usesLeftmostIp() throws Exception {
        // "client, proxy1, proxy2" — leftmost is the real client
        assertThat(filter.resolveClientIp(requestWithXff("203.0.113.5, 10.0.0.1, 10.0.0.2")))
                .isEqualTo("203.0.113.5");
    }

    @Test
    void noXForwardedFor_fallsBackToRemoteAddr() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("198.51.100.7");
        assertThat(filter.resolveClientIp(req)).isEqualTo("198.51.100.7");
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private MockHttpServletRequest requestWithXff(String headerValue) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Forwarded-For", headerValue);
        return req;
    }

    @Test
    void differentSourceIps_haveSeparateBuckets() throws Exception {
        // Exhaust the bucket for IP 10.0.0.1
        for (int i = 0; i < TEST_CAPACITY; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest();
            req.setRemoteAddr("10.0.0.1");
            filter.doFilter(req, new MockHttpServletResponse(), chain);
        }

        // IP 10.0.0.2 should still have a full bucket — must not get 429
        MockHttpServletRequest ip2Request = new MockHttpServletRequest();
        ip2Request.setRemoteAddr("10.0.0.2");
        MockHttpServletResponse ip2Response = new MockHttpServletResponse();
        filter.doFilter(ip2Request, ip2Response, chain);

        assertThat(ip2Response.getStatus()).isNotEqualTo(429);

        // IP 10.0.0.1 should now be rate-limited
        MockHttpServletRequest ip1Overflow = new MockHttpServletRequest();
        ip1Overflow.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse ip1Response = new MockHttpServletResponse();
        filter.doFilter(ip1Overflow, ip1Response, chain);

        assertThat(ip1Response.getStatus()).isEqualTo(429);
    }
}
