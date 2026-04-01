package com.github.kushaal.scim_service.controller;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.github.kushaal.scim_service.TestcontainersConfiguration;
import com.github.kushaal.scim_service.dto.request.ScimGroupRequest;
import com.github.kushaal.scim_service.dto.request.ScimUserRequest;
import com.github.kushaal.scim_service.model.ScimConstants;
import com.github.kushaal.scim_service.repository.AuditLogRepository;
import com.github.kushaal.scim_service.repository.ScimGroupRepository;
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
class ScimGroupControllerIT {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ScimGroupRepository groupRepository;
    @Autowired ScimUserRepository userRepository;
    @Autowired AuditLogRepository auditLogRepository;

    @BeforeEach
    void setUp() {
        // Groups (and their cascaded memberships) must be deleted before users
        // because scim_group_memberships has a FK pointing at scim_users.
        auditLogRepository.deleteAll();
        groupRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ── POST /scim/v2/Groups ──────────────────────────────────────────────────

    @Test
    void post_createsGroup_returns201WithLocationAndETag() {
        ScimGroupRequest request = ScimGroupRequest.builder()
                .displayName("Engineering")
                .build();

        ResponseEntity<Map<String, Object>> response = exchange(
                "/scim/v2/Groups", HttpMethod.POST, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE))
                .contains(ScimConstants.SCIM_CONTENT_TYPE);
        assertThat(response.getHeaders().getLocation()).isNotNull();
        assertThat(response.getHeaders().getFirst(HttpHeaders.ETAG)).isEqualTo("W/\"1\"");

        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("displayName")).isEqualTo("Engineering");
        assertThat(body.get("id")).isNotNull();
        assertThat(body.get("schemas")).isEqualTo(List.of(ScimConstants.SCHEMA_GROUP));

        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) body.get("meta");
        assertThat(meta.get("resourceType")).isEqualTo("Group");
        assertThat(meta.get("location").toString()).contains("/scim/v2/Groups/");
    }

    @Test
    void post_createsGroupWithMembers_returnsMembersArray() {
        String userId = createUser("alice@example.com");

        ScimGroupRequest request = ScimGroupRequest.builder()
                .displayName("Admins")
                .members(List.of(
                        ScimGroupRequest.MemberRequest.builder().value(userId).build()
                ))
                .build();

        ResponseEntity<Map<String, Object>> response = exchange(
                "/scim/v2/Groups", HttpMethod.POST, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> members = (List<Map<String, Object>>) response.getBody().get("members");
        assertThat(members).hasSize(1);
        assertThat(members.get(0).get("value")).isEqualTo(userId);
    }

    @Test
    void post_missingDisplayName_returns400() {
        ScimGroupRequest request = ScimGroupRequest.builder().build();

        ResponseEntity<Map<String, Object>> response = exchange(
                "/scim/v2/Groups", HttpMethod.POST, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertScimError(response.getBody(), 400, "invalidValue");
    }

    // ── GET /scim/v2/Groups/{id} ──────────────────────────────────────────────

    @Test
    void getById_returnsGroupWithMembers() {
        String userId = createUser("bob@example.com");
        String groupId = createGroup("Platform", userId);

        ResponseEntity<Map<String, Object>> response = exchange(
                "/scim/v2/Groups/" + groupId, HttpMethod.GET, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("displayName")).isEqualTo("Platform");
        assertThat(response.getBody().get("id")).isEqualTo(groupId);
        assertThat(response.getHeaders().getFirst(HttpHeaders.ETAG)).isEqualTo("W/\"1\"");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> members = (List<Map<String, Object>>) response.getBody().get("members");
        assertThat(members).hasSize(1);
        assertThat(members.get(0).get("value")).isEqualTo(userId);
    }

    @Test
    void getById_whenNotFound_returns404() {
        ResponseEntity<Map<String, Object>> response = exchange(
                "/scim/v2/Groups/" + UUID.randomUUID(), HttpMethod.GET, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertScimError(response.getBody(), 404, null);
    }

    // ── GET /scim/v2/Groups ───────────────────────────────────────────────────

    @Test
    void list_returnsAllGroupsWithPagination() {
        createGroup("Team A");
        createGroup("Team B");

        ResponseEntity<Map<String, Object>> response = exchange(
                "/scim/v2/Groups", HttpMethod.GET, null);

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
    void list_filterByDisplayName_returnsMatchingGroup() {
        createGroup("Engineering");
        createGroup("Marketing");

        ResponseEntity<Map<String, Object>> response = exchange(
                "/scim/v2/Groups?filter=displayName eq \"Engineering\"", HttpMethod.GET, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body.get("totalResults")).isEqualTo(1);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> resources = (List<Map<String, Object>>) body.get("Resources");
        assertThat(resources).hasSize(1);
        assertThat(resources.get(0).get("displayName")).isEqualTo("Engineering");
    }

    @Test
    void list_startIndexBeyondTotal_returnsEmptyResourcesWithCorrectTotal() {
        createGroup("OnlyGroup");

        ResponseEntity<Map<String, Object>> response = exchange(
                "/scim/v2/Groups?startIndex=100&count=10", HttpMethod.GET, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("totalResults")).isEqualTo(1);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> resources = (List<Map<String, Object>>) response.getBody().get("Resources");
        assertThat(resources).isEmpty();
    }

    // ── PATCH /scim/v2/Groups/{id} ────────────────────────────────────────────

    @Test
    void patch_addMember_appendsMemberToGroup() {
        String userId = createUser("charlie@example.com");
        String groupId = createGroup("DevOps");

        // PATCH Operations value must be a JSON array of member objects, matching
        // the addMembersFromNode() expectation in ScimGroupService.
        Map<String, Object> patchBody = Map.of(
                "schemas", List.of("urn:ietf:params:scim:api:messages:2.0:PatchOp"),
                "Operations", List.of(Map.of(
                        "op", "add",
                        "path", "members",
                        "value", List.of(Map.of("value", userId))
                ))
        );

        ResponseEntity<Map<String, Object>> response = exchange(
                "/scim/v2/Groups/" + groupId, HttpMethod.PATCH, patchBody);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.ETAG)).isEqualTo("W/\"2\"");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> members = (List<Map<String, Object>>) response.getBody().get("members");
        assertThat(members).hasSize(1);
        assertThat(members.get(0).get("value")).isEqualTo(userId);
    }

    @Test
    void patch_removeMemberByFilter_removesMemberFromGroup() {
        String userId = createUser("diana@example.com");
        String groupId = createGroup("Security", userId);

        // The filter path form members[value eq "uuid"] targets a specific member for removal.
        Map<String, Object> patchBody = Map.of(
                "schemas", List.of("urn:ietf:params:scim:api:messages:2.0:PatchOp"),
                "Operations", List.of(Map.of(
                        "op", "remove",
                        "path", "members[value eq \"" + userId + "\"]"
                ))
        );

        ResponseEntity<Map<String, Object>> response = exchange(
                "/scim/v2/Groups/" + groupId, HttpMethod.PATCH, patchBody);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.ETAG)).isEqualTo("W/\"2\"");

        // mapMembers() returns emptyList() when memberships are absent, and
        // @JsonInclude(NON_NULL) serialises it as [] (not null), so we assert empty.
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> members = (List<Map<String, Object>>) response.getBody().get("members");
        assertThat(members).isEmpty();
    }

    @Test
    void patch_unsupportedPath_returns400ScimError() {
        String groupId = createGroup("QA");

        // Group PATCH only supports the 'members' path — displayName changes
        // must go through PUT. Service throws ScimInvalidValueException (400).
        Map<String, Object> patchBody = Map.of(
                "schemas", List.of("urn:ietf:params:scim:api:messages:2.0:PatchOp"),
                "Operations", List.of(Map.of(
                        "op", "replace",
                        "path", "displayName",
                        "value", "NewName"
                ))
        );

        ResponseEntity<Map<String, Object>> response = exchange(
                "/scim/v2/Groups/" + groupId, HttpMethod.PATCH, patchBody);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertScimError(response.getBody(), 400, "invalidValue");
    }

    // ── DELETE /scim/v2/Groups/{id} ───────────────────────────────────────────

    @Test
    void delete_returns204AndSubsequentGetReturns404() {
        String groupId = createGroup("Temporary");

        ResponseEntity<Map<String, Object>> deleteResponse = exchange(
                "/scim/v2/Groups/" + groupId, HttpMethod.DELETE, null);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map<String, Object>> getResponse = exchange(
                "/scim/v2/Groups/" + groupId, HttpMethod.GET, null);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void delete_whenNotFound_returns404() {
        ResponseEntity<Map<String, Object>> response = exchange(
                "/scim/v2/Groups/" + UUID.randomUUID(), HttpMethod.DELETE, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertScimError(response.getBody(), 404, null);
    }

    @Test
    void delete_doesNotDeactivateMemberUsers() {
        // Deleting a group is a hard delete on an organizational container only —
        // the user accounts it contained must remain active. This is the key
        // behavioral contract: groups don't own users.
        String userId = createUser("eve@example.com");
        String groupId = createGroup("Temp", userId);

        exchange("/scim/v2/Groups/" + groupId, HttpMethod.DELETE, null);

        ResponseEntity<Map<String, Object>> userResponse = exchange(
                "/scim/v2/Users/" + userId, HttpMethod.GET, null);
        assertThat(userResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(userResponse.getBody().get("active")).isEqualTo(true);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String createUser(String userName) {
        ScimUserRequest request = ScimUserRequest.builder()
                .userName(userName)
                .name(ScimUserRequest.NameRequest.builder()
                        .givenName("Test")
                        .familyName("User")
                        .build())
                .emails(List.of(ScimUserRequest.EmailRequest.builder()
                        .value(userName)
                        .type("work")
                        .primary(true)
                        .build()))
                .build();
        ResponseEntity<Map<String, Object>> response = exchange(
                "/scim/v2/Users", HttpMethod.POST, request);
        return (String) response.getBody().get("id");
    }

    private String createGroup(String displayName) {
        ScimGroupRequest request = ScimGroupRequest.builder()
                .displayName(displayName)
                .build();
        ResponseEntity<Map<String, Object>> response = exchange(
                "/scim/v2/Groups", HttpMethod.POST, request);
        return (String) response.getBody().get("id");
    }

    private String createGroup(String displayName, String userId) {
        ScimGroupRequest request = ScimGroupRequest.builder()
                .displayName(displayName)
                .members(List.of(
                        ScimGroupRequest.MemberRequest.builder().value(userId).build()
                ))
                .build();
        ResponseEntity<Map<String, Object>> response = exchange(
                "/scim/v2/Groups", HttpMethod.POST, request);
        return (String) response.getBody().get("id");
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
