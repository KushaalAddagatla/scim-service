package com.github.kushaal.scim_service.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import tools.jackson.databind.JsonNode;

/**
 * A single JSON Patch operation per RFC 6902.
 *
 * <p>Three fields:
 * <ul>
 *   <li>{@code op}    — required. One of: {@code add}, {@code remove}, {@code replace}.</li>
 *   <li>{@code path}  — optional. SCIM attribute path, e.g. {@code "active"},
 *                       {@code "name.givenName"}, {@code "emails[type eq \"work\"].value"}.
 *                       Absent in the path-less form where {@code value} is an object.</li>
 *   <li>{@code value} — required for {@code add}/{@code replace}; absent for {@code remove}.
 *                       Typed as {@link JsonNode} because the value can be a primitive
 *                       (string, boolean), an object (path-less form), or an array
 *                       (adding multiple emails). A fixed Java type can't represent all
 *                       three — JsonNode defers the interpretation to PatchApplier.</li>
 * </ul>
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} — IdPs sometimes include
 * extra vendor fields on operations. Fail-fast on unknown fields would break
 * compatibility with real Okta traffic.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScimPatchOperation {
    private String op;
    private String path;
    private JsonNode value;
}
