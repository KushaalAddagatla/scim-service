package com.github.kushaal.scim_service.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.Map;

/**
 * The result of a single operation inside a SCIM Bulk response (RFC 7644 §3.7.3).
 *
 * <p>Fields map directly to what the spec requires in each response operation:
 * <ul>
 *   <li>{@code method} — echoed from the request so the client can correlate
 *       results without relying on array position.</li>
 *   <li>{@code bulkId} — echoed from the request; absent for DELETE and for
 *       operations that had no bulkId.</li>
 *   <li>{@code location} — the {@code /scim/v2/...} URL of the affected resource.
 *       Set for POST (newly created), PUT, and PATCH. Absent for DELETE.</li>
 *   <li>{@code version} — the resource ETag after the operation. Matches the
 *       {@code ETag} header a direct request would have returned.</li>
 *   <li>{@code status} — a single-key map {@code {"code": "201"}} where the code
 *       is a string per the spec. Using a map rather than a nested object keeps the
 *       DTO simple while matching the exact wire format the spec shows.</li>
 *   <li>{@code response} — the full resource DTO on success, a {@link ScimError}
 *       on failure. {@code null} for 204 No Content (DELETE).</li>
 * </ul>
 *
 * <p>{@code @JsonInclude(NON_NULL)} suppresses absent fields so a successful DELETE
 * result only contains {@code method} and {@code status}, not null-valued
 * {@code location}, {@code bulkId}, and {@code response} fields.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScimBulkOperationResult {

    private String method;
    private String bulkId;
    private String location;
    private String version;

    /** {@code {"code": "201"}} — code is a string per RFC 7644 §3.7.3. */
    private Map<String, String> status;

    /** Resource DTO on success; {@link ScimError} on failure; null for 204. */
    private Object response;
}
