package com.github.kushaal.scim_service.controller;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.github.kushaal.scim_service.JwtTestHelper;
import com.github.kushaal.scim_service.TestcontainersConfiguration;
import com.github.kushaal.scim_service.model.ScimConstants;
import com.github.kushaal.scim_service.repository.AuditLogRepository;
import com.github.kushaal.scim_service.repository.ScimUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ScimSecurityIT {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ScimUserRepository userRepository;
    @Autowired AuditLogRepository auditLogRepository;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ── Unauthenticated requests ──────────────────────────────────────────────

    @Test
    void noToken_returns401WithScimErrorBody() {
        ResponseEntity<Map<String, Object>> response = exchange(
                "/scim/v2/Users", HttpMethod.POST, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE))
                .contains(ScimConstants.SCIM_CONTENT_TYPE);
        assertScimError(response.getBody(), 401);
    }

    // ── Discovery endpoints — permit-all ─────────────────────────────────────

    // RFC 7644 §4: ServiceProviderConfig, Schemas, and ResourceTypes must be
    // accessible without authentication so IdPs can discover capabilities before
    // presenting credentials.

    @Test
    void serviceProviderConfig_noToken_returns200() {
        ResponseEntity<Map<String, Object>> response = exchange(
                "/scim/v2/ServiceProviderConfig", HttpMethod.GET, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void schemas_noToken_returns200() {
        ResponseEntity<Map<String, Object>> response = exchange(
                "/scim/v2/Schemas", HttpMethod.GET, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void resourceTypes_noToken_returns200() {
        ResponseEntity<Map<String, Object>> response = exchange(
                "/scim/v2/ResourceTypes", HttpMethod.GET, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ── Valid token ───────────────────────────────────────────────────────────

    @Test
    void validToken_returns200() {
        ResponseEntity<Map<String, Object>> response = exchange(
                "/scim/v2/Users", HttpMethod.GET, null, JwtTestHelper.validToken());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ── Token validation failures ─────────────────────────────────────────────

    @Test
    void expiredToken_returns401() {
        ResponseEntity<Map<String, Object>> response = exchange(
                "/scim/v2/Users", HttpMethod.GET, null, JwtTestHelper.expiredToken());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertScimError(response.getBody(), 401);
    }

    @Test
    void wrongIssuer_returns401() {
        ResponseEntity<Map<String, Object>> response = exchange(
                "/scim/v2/Users", HttpMethod.GET, null, JwtTestHelper.wrongIssuerToken());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertScimError(response.getBody(), 401);
    }

    // ── Scope failures ────────────────────────────────────────────────────────

    // Token is valid (correct signature, issuer, expiry) but carries the wrong
    // scope → 403 Forbidden, not 401. The distinction matters: 401 means "I don't
    // know who you are", 403 means "I know who you are but you can't do this".

    @Test
    void wrongScope_returns403() {
        ResponseEntity<Map<String, Object>> response = exchange(
                "/scim/v2/Users", HttpMethod.GET, null, JwtTestHelper.wrongScopeToken());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertScimError(response.getBody(), 403);
    }

    @Test
    void noScope_returns403() {
        ResponseEntity<Map<String, Object>> response = exchange(
                "/scim/v2/Users", HttpMethod.GET, null, JwtTestHelper.noScopeToken());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertScimError(response.getBody(), 403);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    // token=null → unauthenticated request (no Authorization header)
    private ResponseEntity<Map<String, Object>> exchange(
            String url, HttpMethod method, Object body, String token) {
        try {
            MockHttpServletRequestBuilder builder = MockMvcRequestBuilders
                    .request(method, url)
                    .contentType(ScimConstants.SCIM_CONTENT_TYPE)
                    .accept(ScimConstants.SCIM_CONTENT_TYPE);

            if (token != null) {
                builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            }
            if (body != null) {
                builder.content(objectMapper.writeValueAsString(body));
            }

            MvcResult result = mockMvc.perform(builder).andReturn();
            MockHttpServletResponse servletResponse = result.getResponse();

            HttpHeaders headers = new HttpHeaders();
            servletResponse.getHeaderNames().forEach(name ->
                    servletResponse.getHeaders(name).forEach(value -> headers.add(name, value)));

            Map<String, Object> responseBody = null;
            String content = servletResponse.getContentAsString();
            if (!content.isBlank()) {
                responseBody = objectMapper.readValue(content, new TypeReference<>() {});
            }

            return ResponseEntity.status(servletResponse.getStatus())
                    .headers(headers)
                    .body(responseBody);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void assertScimError(Map<String, Object> body, int expectedStatus) {
        assertThat(body).isNotNull();
        assertThat(body.get("status")).isEqualTo(expectedStatus);
        assertThat(body.get("schemas")).isEqualTo(List.of(ScimConstants.SCHEMA_ERROR));
    }
}
