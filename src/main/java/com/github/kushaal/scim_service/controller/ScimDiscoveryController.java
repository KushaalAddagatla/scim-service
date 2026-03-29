package com.github.kushaal.scim_service.controller;

import com.github.kushaal.scim_service.model.ScimConstants;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(produces = ScimConstants.SCIM_CONTENT_TYPE)
public class ScimDiscoveryController {

    // ── /scim/v2/ServiceProviderConfig ────────────────────────────────────────

    @GetMapping("/scim/v2/ServiceProviderConfig")
    public ResponseEntity<Map<String, Object>> serviceProviderConfig() {
        return ResponseEntity.ok(SERVICE_PROVIDER_CONFIG);
    }

    // ── /scim/v2/ResourceTypes ────────────────────────────────────────────────

    @GetMapping("/scim/v2/ResourceTypes")
    public ResponseEntity<Map<String, Object>> resourceTypes() {
        return ResponseEntity.ok(RESOURCE_TYPES);
    }

    // ── /scim/v2/Schemas ──────────────────────────────────────────────────────

    @GetMapping("/scim/v2/Schemas")
    public ResponseEntity<Map<String, Object>> schemas() {
        return ResponseEntity.ok(SCHEMAS);
    }

    // ── Static response payloads ──────────────────────────────────────────────
    //
    // These never change at runtime, so they're built once at class load.
    // Rebuilding them on every request would be wasteful and introduces no value.

    private static final Map<String, Object> SERVICE_PROVIDER_CONFIG = Map.of(
            "schemas", List.of("urn:ietf:params:scim:schemas:core:2.0:ServiceProviderConfig"),
            // patch/filter/etag advertise planned Week 2 state — these will all be working
            // by Week 3 when the Okta dev tenant connects. Advertising false would cause
            // Okta to skip sending those operations entirely, which defeats the integration test.
            "patch",          Map.of("supported", true),
            "bulk",           Map.of("supported", false, "maxOperations", 0, "maxPayloadSize", 0),
            "filter",         Map.of("supported", true, "maxResults", 200),
            "changePassword", Map.of("supported", false),
            "sort",           Map.of("supported", false),
            "etag",           Map.of("supported", true),
            "authenticationSchemes", List.of(Map.of(
                    "type", "oauthbearertoken",
                    "name", "OAuth Bearer Token",
                    "description", "Authentication using the OAuth 2.0 Bearer Token standard",
                    "specUri", "https://www.rfc-editor.org/info/rfc6750",
                    "primary", true
            )),
            "meta", Map.of(
                    "resourceType", "ServiceProviderConfig",
                    "location", "/scim/v2/ServiceProviderConfig"
            )
    );

    private static final Map<String, Object> RESOURCE_TYPES = Map.of(
            "schemas",      List.of(ScimConstants.SCHEMA_LIST_RESPONSE),
            "totalResults", 2,
            "Resources",    List.of(
                    Map.of(
                            "schemas",   List.of("urn:ietf:params:scim:schemas:core:2.0:ResourceType"),
                            "id",        "User",
                            "name",      "User",
                            "endpoint",  "/scim/v2/Users",
                            "schema",    ScimConstants.SCHEMA_USER,
                            "meta",      Map.of(
                                    "resourceType", "ResourceType",
                                    "location",     "/scim/v2/ResourceTypes/User"
                            )
                    ),
                    Map.of(
                            "schemas",   List.of("urn:ietf:params:scim:schemas:core:2.0:ResourceType"),
                            "id",        "Group",
                            "name",      "Group",
                            "endpoint",  "/scim/v2/Groups",
                            "schema",    ScimConstants.SCHEMA_GROUP,
                            "meta",      Map.of(
                                    "resourceType", "ResourceType",
                                    "location",     "/scim/v2/ResourceTypes/Group"
                            )
                    )
            )
    );

    private static final Map<String, Object> SCHEMAS = Map.of(
            "schemas",      List.of(ScimConstants.SCHEMA_LIST_RESPONSE),
            "totalResults", 2,
            "Resources",    List.of(USER_SCHEMA, GROUP_SCHEMA)
    );

    private static final Map<String, Object> USER_SCHEMA = Map.ofEntries(
            Map.entry("id",          ScimConstants.SCHEMA_USER),
            Map.entry("name",        "User"),
            Map.entry("description", "User Account"),
            Map.entry("schemas",     List.of("urn:ietf:params:scim:schemas:core:2.0:Schema")),
            Map.entry("meta", Map.of(
                    "resourceType", "Schema",
                    "location",     "/scim/v2/Schemas/" + ScimConstants.SCHEMA_USER
            )),
            Map.entry("attributes", List.of(
                    attr("id",          "string",  false, false, "readOnly",  "always",   "server"),
                    attr("externalId",  "string",  false, false, "readWrite", "default",  "none"),
                    attr("userName",    "string",  false, true,  "readWrite", "always",   "server"),
                    attr("displayName", "string",  false, false, "readWrite", "default",  "none"),
                    attr("active",      "boolean", false, false, "readWrite", "default",  "none"),
                    Map.of(
                            "name",          "name",
                            "type",          "complex",
                            "multiValued",   false,
                            "required",      false,
                            "mutability",    "readWrite",
                            "returned",      "default",
                            "subAttributes", List.of(
                                    attr("givenName",  "string", false, false, "readWrite", "default", "none"),
                                    attr("familyName", "string", false, false, "readWrite", "default", "none"),
                                    attr("middleName", "string", false, false, "readWrite", "default", "none"),
                                    attr("formatted",  "string", false, false, "readWrite", "default", "none")
                            )
                    ),
                    Map.of(
                            "name",          "emails",
                            "type",          "complex",
                            "multiValued",   true,
                            "required",      false,
                            "mutability",    "readWrite",
                            "returned",      "default",
                            "subAttributes", List.of(
                                    attr("value",   "string",  false, false, "readWrite", "default", "none"),
                                    attr("type",    "string",  false, false, "readWrite", "default", "none"),
                                    attr("primary", "boolean", false, false, "readWrite", "default", "none"),
                                    attr("display", "string",  false, false, "readWrite", "default", "none")
                            )
                    ),
                    Map.of(
                            "name",          "phoneNumbers",
                            "type",          "complex",
                            "multiValued",   true,
                            "required",      false,
                            "mutability",    "readWrite",
                            "returned",      "default",
                            "subAttributes", List.of(
                                    attr("value",   "string",  false, false, "readWrite", "default", "none"),
                                    attr("type",    "string",  false, false, "readWrite", "default", "none"),
                                    attr("primary", "boolean", false, false, "readWrite", "default", "none")
                            )
                    )
            ))
    );

    private static final Map<String, Object> GROUP_SCHEMA = Map.ofEntries(
            Map.entry("id",          ScimConstants.SCHEMA_GROUP),
            Map.entry("name",        "Group"),
            Map.entry("description", "Group"),
            Map.entry("schemas",     List.of("urn:ietf:params:scim:schemas:core:2.0:Schema")),
            Map.entry("meta", Map.of(
                    "resourceType", "Schema",
                    "location",     "/scim/v2/Schemas/" + ScimConstants.SCHEMA_GROUP
            )),
            Map.entry("attributes", List.of(
                    attr("id",          "string", false, false, "readOnly",  "always",  "server"),
                    attr("displayName", "string", false, true,  "readWrite", "always",  "none"),
                    Map.of(
                            "name",          "members",
                            "type",          "complex",
                            "multiValued",   true,
                            "required",      false,
                            "mutability",    "readWrite",
                            "returned",      "default",
                            "subAttributes", List.of(
                                    attr("value",   "string", false, false, "immutable", "default", "none"),
                                    attr("display", "string", false, false, "readOnly",  "default", "none")
                            )
                    )
            ))
    );

    // Builds a simple scalar attribute definition map (no sub-attributes).
    // All SCIM attribute definitions share these same fields per RFC 7643 §7.
    private static Map<String, Object> attr(
            String name, String type, boolean multiValued, boolean required,
            String mutability, String returned, String uniqueness) {
        return Map.of(
                "name",        name,
                "type",        type,
                "multiValued", multiValued,
                "required",    required,
                "mutability",  mutability,
                "returned",    returned,
                "uniqueness",  uniqueness
        );
    }
}
