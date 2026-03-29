package com.github.kushaal.scim_service.controller;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.github.kushaal.scim_service.TestcontainersConfiguration;
import com.github.kushaal.scim_service.dto.request.ScimUserRequest;
import com.github.kushaal.scim_service.model.ScimConstants;
import com.github.kushaal.scim_service.repository.AuditLogRepository;
import com.github.kushaal.scim_service.repository.ScimUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ScimUserControllerIT {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ScimUserRepository userRepository;
    @Autowired AuditLogRepository auditLogRepository;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ── POST /scim/v2/Users ───────────────────────────────────────────────────

    @Test
    void post_createsUser_returns201WithLocationAndScimBody() {
        ScimUserRequest request = buildRequest("john@example.com", "John", "Doe");

        ResponseEntity<Map<String, Object>> response = exchange(
                "/scim/v2/Users", HttpMethod.POST, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE))
                .contains(ScimConstants.SCIM_CONTENT_TYPE);
        assertThat(response.getHeaders().getLocation()).isNotNull();

        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("userName")).isEqualTo("john@example.com");
        assertThat(body.get("id")).isNotNull();
        assertThat(body.get("active")).isEqualTo(true);
        assertThat(body.get("schemas")).isEqualTo(List.of(ScimConstants.SCHEMA_USER));

        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) body.get("meta");
        assertThat(meta.get("resourceType")).isEqualTo("User");
        assertThat(meta.get("location").toString()).contains("/scim/v2/Users/");
    }

    @Test
    void post_missingUserName_returns400ScimError() {
        ScimUserRequest request = ScimUserRequest.builder().build();

        ResponseEntity<Map<String, Object>> response = exchange(
                "/scim/v2/Users", HttpMethod.POST, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertScimError(response.getBody(), 400, "invalidValue");
    }

    @Test
    void post_duplicateUserName_returns409ScimError() {
        ScimUserRequest request = buildRequest("john@example.com", "John", "Doe");
        exchange("/scim/v2/Users", HttpMethod.POST, request);

        ResponseEntity<Map<String, Object>> response = exchange(
                "/scim/v2/Users", HttpMethod.POST, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertScimError(response.getBody(), 409, "uniqueness");
    }

    // ── GET /scim/v2/Users/{id} ───────────────────────────────────────────────

    @Test
    void getById_returnsUser() {
        String id = createUser("john@example.com");

        ResponseEntity<Map<String, Object>> response = exchange(
                "/scim/v2/Users/" + id, HttpMethod.GET, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("userName")).isEqualTo("john@example.com");
        assertThat(response.getBody().get("id")).isEqualTo(id);
    }

    @Test
    void getById_whenNotFound_returns404ScimError() {
        ResponseEntity<Map<String, Object>> response = exchange(
                "/scim/v2/Users/" + UUID.randomUUID(), HttpMethod.GET, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertScimError(response.getBody(), 404, null);
    }

    // ── GET /scim/v2/Users ────────────────────────────────────────────────────

    @Test
    void list_returnsScimListResponse() {
        createUser("alice@example.com");
        createUser("bob@example.com");

        ResponseEntity<Map<String, Object>> response = exchange(
                "/scim/v2/Users", HttpMethod.GET, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body.get("totalResults")).isEqualTo(2);
        assertThat(body.get("startIndex")).isEqualTo(1);
        assertThat(body.get("schemas")).isEqualTo(List.of(ScimConstants.SCHEMA_LIST_RESPONSE));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> resources = (List<Map<String, Object>>) body.get("Resources");
        assertThat(resources).hasSize(2);
    }

    @Test
    void list_emptyDb_returnsTotalResultsZero() {
        ResponseEntity<Map<String, Object>> response = exchange(
                "/scim/v2/Users", HttpMethod.GET, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("totalResults")).isEqualTo(0);
    }

    @Test
    void list_startIndexBeyondTotalResults_returnsEmptyResourcesButCorrectTotal() {
        createUser("alice@example.com");
        createUser("bob@example.com");

        ResponseEntity<Map<String, Object>> response = exchange(
                "/scim/v2/Users?startIndex=100&count=10", HttpMethod.GET, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // totalResults reflects the full count, not the page size
        assertThat(response.getBody().get("totalResults")).isEqualTo(2);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> resources = (List<Map<String, Object>>) response.getBody().get("Resources");
        assertThat(resources).isEmpty();
    }

    // ── GET /scim/v2/Users?filter= ────────────────────────────────────────────

    @Test
    void filter_byUserName_returnsMatchingUser() {
        createUser("alice@example.com");
        createUser("bob@example.com");

        ResponseEntity<Map<String, Object>> response = exchange(
                "/scim/v2/Users?filter=userName eq \"alice@example.com\"", HttpMethod.GET, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body.get("totalResults")).isEqualTo(1);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> resources = (List<Map<String, Object>>) body.get("Resources");
        assertThat(resources).hasSize(1);
        assertThat(resources.get(0).get("userName")).isEqualTo("alice@example.com");
    }

    @Test
    void filter_byUserName_noMatch_returnsEmptyListNotError() {
        createUser("alice@example.com");

        ResponseEntity<Map<String, Object>> response = exchange(
                "/scim/v2/Users?filter=userName eq \"nobody@example.com\"", HttpMethod.GET, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("totalResults")).isEqualTo(0);
    }

    @Test
    void filter_byEmailValue_returnsMatchingUser() {
        createUser("carol@example.com");
        createUser("dave@example.com");

        ResponseEntity<Map<String, Object>> response = exchange(
                "/scim/v2/Users?filter=emails.value eq \"carol@example.com\"", HttpMethod.GET, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("totalResults")).isEqualTo(1);
    }

    @Test
    void filter_malformed_returns400ScimError() {
        ResponseEntity<Map<String, Object>> response = exchange(
                "/scim/v2/Users?filter=INVALID_FILTER_SYNTAX", HttpMethod.GET, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertScimError(response.getBody(), 400, "invalidValue");
    }

    @Test
    void filter_unsupportedAttribute_returns400ScimError() {
        ResponseEntity<Map<String, Object>> response = exchange(
                "/scim/v2/Users?filter=nickname eq \"foo\"", HttpMethod.GET, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertScimError(response.getBody(), 400, "invalidValue");
    }

    // ── PUT /scim/v2/Users/{id} ───────────────────────────────────────────────

    @Test
    void put_updatesUserAndIncrementsVersion() {
        String id = createUser("john@example.com");
        ScimUserRequest updateRequest = buildRequest("john@example.com", "Johnny", "Updated");

        ResponseEntity<Map<String, Object>> response = exchange(
                "/scim/v2/Users/" + id, HttpMethod.PUT, updateRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        @SuppressWarnings("unchecked")
        Map<String, Object> name = (Map<String, Object>) response.getBody().get("name");
        assertThat(name.get("givenName")).isEqualTo("Johnny");

        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) response.getBody().get("meta");
        assertThat(meta.get("version")).isEqualTo("W/\"2\"");
    }

    @Test
    void put_whenNotFound_returns404() {
        ResponseEntity<Map<String, Object>> response = exchange(
                "/scim/v2/Users/" + UUID.randomUUID(),
                HttpMethod.PUT,
                buildRequest("john@example.com", "John", "Doe"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── ETag / If-Match ───────────────────────────────────────────────────────

    @Test
    void post_responseIncludesETagHeader() {
        ScimUserRequest request = buildRequest("etag@example.com", "E", "Tag");

        ResponseEntity<Map<String, Object>> response = exchange(
                "/scim/v2/Users", HttpMethod.POST, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getFirst(HttpHeaders.ETAG)).isEqualTo("W/\"1\"");
    }

    @Test
    void getById_responseIncludesETagHeader() {
        String id = createUser("etag-get@example.com");

        ResponseEntity<Map<String, Object>> response = exchange(
                "/scim/v2/Users/" + id, HttpMethod.GET, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.ETAG)).isEqualTo("W/\"1\"");
    }

    @Test
    void put_withCorrectIfMatch_succeeds() {
        String id = createUser("ifmatch@example.com");

        ResponseEntity<Map<String, Object>> response = exchangeWithHeader(
                "/scim/v2/Users/" + id, HttpMethod.PUT,
                buildRequest("ifmatch@example.com", "Updated", "User"),
                HttpHeaders.IF_MATCH, "W/\"1\"");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.ETAG)).isEqualTo("W/\"2\"");
    }

    @Test
    void put_withStaleIfMatch_returns412() {
        String id = createUser("stale@example.com");

        // PUT once to advance version to 2
        exchange("/scim/v2/Users/" + id, HttpMethod.PUT,
                buildRequest("stale@example.com", "First", "Update"));

        // Now send If-Match: W/"1" — stale
        ResponseEntity<Map<String, Object>> response = exchangeWithHeader(
                "/scim/v2/Users/" + id, HttpMethod.PUT,
                buildRequest("stale@example.com", "Stale", "Write"),
                HttpHeaders.IF_MATCH, "W/\"1\"");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PRECONDITION_FAILED);
        assertScimError(response.getBody(), 412, null);
    }

    @Test
    void put_withMalformedIfMatch_returns412() {
        String id = createUser("malformed@example.com");

        ResponseEntity<Map<String, Object>> response = exchangeWithHeader(
                "/scim/v2/Users/" + id, HttpMethod.PUT,
                buildRequest("malformed@example.com", "Bad", "Etag"),
                HttpHeaders.IF_MATCH, "not-an-etag");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PRECONDITION_FAILED);
        assertScimError(response.getBody(), 412, null);
    }

    // ── DELETE /scim/v2/Users/{id} ────────────────────────────────────────────

    @Test
    void delete_returns204AndSoftDeletesUser() {
        String id = createUser("john@example.com");

        ResponseEntity<Map<String, Object>> deleteResponse = exchange(
                "/scim/v2/Users/" + id, HttpMethod.DELETE, null);

        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map<String, Object>> getResponse = exchange(
                "/scim/v2/Users/" + id, HttpMethod.GET, null);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody().get("active")).isEqualTo(false);
    }

    @Test
    void delete_whenNotFound_returns404() {
        ResponseEntity<Map<String, Object>> response = exchange(
                "/scim/v2/Users/" + UUID.randomUUID(), HttpMethod.DELETE, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ResponseEntity<Map<String, Object>> exchangeWithHeader(
            String url, HttpMethod method, Object body, String headerName, String headerValue) {
        try {
            MockHttpServletRequestBuilder builder = MockMvcRequestBuilders
                    .request(method, url)
                    .contentType(ScimConstants.SCIM_CONTENT_TYPE)
                    .accept(ScimConstants.SCIM_CONTENT_TYPE)
                    .header(headerName, headerValue);

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

    private String createUser(String userName) {
        ScimUserRequest request = buildRequest(userName, "John", "Doe");
        ResponseEntity<Map<String, Object>> response = exchange(
                "/scim/v2/Users", HttpMethod.POST, request);
        return (String) response.getBody().get("id");
    }

    private ScimUserRequest buildRequest(String userName, String givenName, String familyName) {
        return ScimUserRequest.builder()
                .userName(userName)
                .name(ScimUserRequest.NameRequest.builder()
                        .givenName(givenName)
                        .familyName(familyName)
                        .build())
                .emails(List.of(ScimUserRequest.EmailRequest.builder()
                        .value(userName)
                        .type("work")
                        .primary(true)
                        .build()))
                .build();
    }

    private ResponseEntity<Map<String, Object>> exchange(
            String url, HttpMethod method, Object body) {
        try {
            MockHttpServletRequestBuilder builder = MockMvcRequestBuilders
                    .request(method, url)
                    .contentType(ScimConstants.SCIM_CONTENT_TYPE)
                    .accept(ScimConstants.SCIM_CONTENT_TYPE);

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

    private void assertScimError(Map<String, Object> body, int expectedStatus, String expectedScimType) {
        assertThat(body).isNotNull();
        assertThat(body.get("status")).isEqualTo(expectedStatus);
        assertThat(body.get("schemas")).isEqualTo(List.of(ScimConstants.SCHEMA_ERROR));
        if (expectedScimType != null) {
            assertThat(body.get("scimType")).isEqualTo(expectedScimType);
        }
    }
}
