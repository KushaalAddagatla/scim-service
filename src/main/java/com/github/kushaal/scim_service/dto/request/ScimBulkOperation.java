package com.github.kushaal.scim_service.dto.request;

import lombok.*;
import tools.jackson.databind.JsonNode;

/**
 * A single operation within a SCIM Bulk request (RFC 7644 §3.7.2).
 *
 * <p>Each operation mirrors a single SCIM HTTP call: {@code method} is the HTTP
 * verb, {@code path} is the resource path (e.g. {@code /Users} or
 * {@code /Users/{id}}), and {@code data} is the request body (absent for DELETE).
 *
 * <p>{@code bulkId} is a client-supplied opaque string. Two purposes:
 * <ol>
 *   <li>Forward reference — a later operation can target the resource created
 *       by a POST by writing {@code "path": "/Groups/bulkId:someRef"} before
 *       the server has assigned a real UUID.</li>
 *   <li>Correlation — the server echoes the bulkId in the response so the
 *       client can match results to the original operations by position-independent
 *       key rather than index.</li>
 * </ol>
 *
 * <p>{@code data} is kept as a {@link JsonNode} (raw JSON tree) because the
 * payload type varies by method and resource: POST/PUT bodies are
 * {@link ScimUserRequest} or {@link ScimGroupRequest}, PATCH bodies are
 * {@link ScimPatchRequest}, and DELETE has no body at all. Deserializing to
 * the correct DTO is deferred to {@code ScimBulkService} once the path is known.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScimBulkOperation {

    /** HTTP method: POST, PUT, PATCH, or DELETE. Required. */
    private String method;

    /**
     * Client-supplied identifier for this operation.
     * Required on POST so that later operations can reference the resource
     * before its server-assigned ID is known. Not used on DELETE.
     */
    private String bulkId;

    /**
     * ETag value for optimistic concurrency on PUT/PATCH.
     * When provided, the service treats it as an {@code If-Match} header.
     */
    private String version;

    /**
     * Resource path relative to the SCIM base (e.g. {@code /Users},
     * {@code /Users/some-uuid}, {@code /Groups/bulkId:ref}).
     * The {@code /scim/v2} prefix is NOT included — that is a server detail
     * not present in bulk payloads per RFC 7644 §3.7.2.
     */
    private String path;

    /**
     * Request body. Null for DELETE. Deserialized later based on path + method.
     */
    private JsonNode data;
}
