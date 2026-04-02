package com.github.kushaal.scim_service.controller;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.github.kushaal.scim_service.JwtTestHelper;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ScimUserPatchIT {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ScimUserRepository userRepository;
    @Autowired AuditLogRepository auditLogRepository;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ── replace scalar ────────────────────────────────────────────────────────

    @Test
    void patch_replaceActive_deactivatesUser() {
        String id = createUser("john@example.com");

        ResponseEntity<Map<String, Object>> response = patch(id, patchBody(List.of(
                op("replace", "active", false)
        )));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("active")).isEqualTo(false);
        assertThat(response.getHeaders().getFirst(HttpHeaders.ETAG)).isEqualTo("W/\"2\"");
    }

    @Test
    void patch_replaceDisplayName_updatesField() {
        String id = createUserWithDisplayName("john@example.com", "Old Name");

        ResponseEntity<Map<String, Object>> response = patch(id, patchBody(List.of(
                op("replace", "displayName", "New Name")
        )));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("displayName")).isEqualTo("New Name");
    }

    @Test
    void patch_replaceNameGivenName_updatesNestedField() {
        String id = createUser("john@example.com");

        ResponseEntity<Map<String, Object>> response = patch(id, patchBody(List.of(
                op("replace", "name.givenName", "Johnny")
        )));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> name = (Map<String, Object>) response.getBody().get("name");
        assertThat(name.get("givenName")).isEqualTo("Johnny");
    }

    // ── add scalar ────────────────────────────────────────────────────────────

    @Test
    void patch_addScalar_behavesLikeReplace() {
        String id = createUser("john@example.com");

        ResponseEntity<Map<String, Object>> response = patch(id, patchBody(List.of(
                op("add", "displayName", "Added Name")
        )));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("displayName")).isEqualTo("Added Name");
    }

    // ── remove scalar ─────────────────────────────────────────────────────────

    @Test
    void patch_removeScalar_nullifiesField() {
        String id = createUserWithDisplayName("john@example.com", "Has A Name");

        ResponseEntity<Map<String, Object>> response = patch(id, patchBody(List.of(
                opRemove("displayName")
        )));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("displayName")).isNull();
    }

    // ── path-less form ────────────────────────────────────────────────────────

    @Test
    void patch_pathlessForm_replacesMultipleFields() {
        String id = createUser("john@example.com");

        ResponseEntity<Map<String, Object>> response = patch(id, patchBody(List.of(
                Map.of("op", "replace", "value", Map.of(
                        "active", false,
                        "displayName", "Pathless Update"
                ))
        )));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("active")).isEqualTo(false);
        assertThat(response.getBody().get("displayName")).isEqualTo("Pathless Update");
    }

    // ── multi-valued — emails ─────────────────────────────────────────────────

    @Test
    void patch_replaceEmailValue_updatesSpecificEmail() {
        String id = createUser("john@example.com");

        ResponseEntity<Map<String, Object>> response = patch(id, patchBody(List.of(
                op("replace", "emails[type eq \"work\"].value", "updated@example.com")
        )));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> emails = (List<Map<String, Object>>) response.getBody().get("emails");
        assertThat(emails).hasSize(1);
        assertThat(emails.get(0).get("value")).isEqualTo("updated@example.com");
    }

    @Test
    void patch_addEmails_appendsToCollection() {
        String id = createUser("john@example.com");

        ResponseEntity<Map<String, Object>> response = patch(id, patchBody(List.of(
                op("add", "emails", Map.of(
                        "value", "home@example.com",
                        "type", "home",
                        "primary", false
                ))
        )));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> emails = (List<Map<String, Object>>) response.getBody().get("emails");
        assertThat(emails).hasSize(2);
    }

    @Test
    void patch_removeEmail_removesFromCollection() {
        String id = createUser("john@example.com");

        ResponseEntity<Map<String, Object>> response = patch(id, patchBody(List.of(
                opRemove("emails[type eq \"work\"]")
        )));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> emails = (List<Map<String, Object>>) response.getBody().get("emails");
        assertThat(emails).isEmpty();
    }

    // ── error cases ───────────────────────────────────────────────────────────

    @Test
    void patch_missingOp_returns400ScimError() {
        String id = createUser("john@example.com");

        ResponseEntity<Map<String, Object>> response = patch(id, patchBody(List.of(
                Map.of("path", "active", "value", false)  // no "op" field
        )));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertScimError(response.getBody(), 400, "invalidValue");
    }

    @Test
    void patch_unknownPath_returns400ScimError() {
        String id = createUser("john@example.com");

        ResponseEntity<Map<String, Object>> response = patch(id, patchBody(List.of(
                op("replace", "nonExistentAttribute", "value")
        )));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertScimError(response.getBody(), 400, "invalidValue");
    }

    @Test
    void patch_whenUserNotFound_returns404() {
        ResponseEntity<Map<String, Object>> response = patch(
                UUID.randomUUID().toString(),
                patchBody(List.of(op("replace", "active", false)))
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void patch_withStaleIfMatch_returns412() {
        String id = createUser("john@example.com");

        // Advance version to 2
        patch(id, patchBody(List.of(op("replace", "displayName", "v2"))));

        // Send If-Match: W/"1" — stale
        ResponseEntity<Map<String, Object>> response = patchWithHeader(
                id,
                patchBody(List.of(op("replace", "displayName", "v3"))),
                HttpHeaders.IF_MATCH, "W/\"1\""
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PRECONDITION_FAILED);
        assertScimError(response.getBody(), 412, null);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Map<String, Object> patchBody(List<Map<String, Object>> operations) {
        return Map.of(
                "schemas", List.of(ScimConstants.SCHEMA_PATCH_OP),
                "Operations", operations
        );
    }

    // LinkedHashMap preserves insertion order so the serialised JSON is predictable in tests
    private Map<String, Object> op(String op, String path, Object value) {
        LinkedHashMap<String, Object> operation = new LinkedHashMap<>();
        operation.put("op", op);
        operation.put("path", path);
        operation.put("value", value);
        return operation;
    }

    private Map<String, Object> opRemove(String path) {
        return Map.of("op", "remove", "path", path);
    }

    private ResponseEntity<Map<String, Object>> patch(String id, Object body) {
        return exchange("/scim/v2/Users/" + id, HttpMethod.PATCH, body, null, null);
    }

    private ResponseEntity<Map<String, Object>> patchWithHeader(
            String id, Object body, String headerName, String headerValue) {
        return exchange("/scim/v2/Users/" + id, HttpMethod.PATCH, body, headerName, headerValue);
    }

    private ResponseEntity<Map<String, Object>> exchange(
            String url, HttpMethod method, Object body, String headerName, String headerValue) {
        try {
            MockHttpServletRequestBuilder builder = MockMvcRequestBuilders
                    .request(method, url)
                    .contentType(ScimConstants.SCIM_CONTENT_TYPE)
                    .accept(ScimConstants.SCIM_CONTENT_TYPE)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + JwtTestHelper.validToken());

            if (headerName != null) builder.header(headerName, headerValue);
            if (body != null) builder.content(objectMapper.writeValueAsString(body));

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
        ScimUserRequest request = ScimUserRequest.builder()
                .userName(userName)
                .name(ScimUserRequest.NameRequest.builder()
                        .givenName("John").familyName("Doe").build())
                .emails(List.of(ScimUserRequest.EmailRequest.builder()
                        .value(userName).type("work").primary(true).build()))
                .build();
        ResponseEntity<Map<String, Object>> response =
                exchange("/scim/v2/Users", HttpMethod.POST, request, null, null);
        return (String) response.getBody().get("id");
    }

    private String createUserWithDisplayName(String userName, String displayName) {
        ScimUserRequest request = ScimUserRequest.builder()
                .userName(userName)
                .displayName(displayName)
                .name(ScimUserRequest.NameRequest.builder()
                        .givenName("John").familyName("Doe").build())
                .emails(List.of(ScimUserRequest.EmailRequest.builder()
                        .value(userName).type("work").primary(true).build()))
                .build();
        ResponseEntity<Map<String, Object>> response =
                exchange("/scim/v2/Users", HttpMethod.POST, request, null, null);
        return (String) response.getBody().get("id");
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
