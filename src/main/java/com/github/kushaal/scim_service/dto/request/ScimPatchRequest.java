package com.github.kushaal.scim_service.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

/**
 * The SCIM PATCH request body (RFC 7644 §3.5.2).
 *
 * <p>The field name {@code Operations} is capitalised — that is how it appears
 * in the SCIM spec and in every real IdP payload. {@code @JsonProperty("Operations")}
 * maps the JSON key to the Java field; without it, Jackson would look for
 * {@code "operations"} (lowercase) and silently leave the list null.
 *
 * <p>Example request body Okta sends to deactivate a user:
 * <pre>
 * {
 *   "schemas": ["urn:ietf:params:scim:api:messages:2.0:PatchOp"],
 *   "Operations": [
 *     { "op": "replace", "path": "active", "value": false }
 *   ]
 * }
 * </pre>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScimPatchRequest {

    private List<String> schemas;

    @JsonProperty("Operations")
    private List<ScimPatchOperation> operations;
}
