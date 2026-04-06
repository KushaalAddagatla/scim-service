package com.github.kushaal.scim_service.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

/**
 * SCIM Bulk request body (RFC 7644 §3.7.2).
 *
 * <p>The {@code Operations} array (capital-O, per spec) contains one entry for
 * each sub-request. The {@code failOnErrors} integer controls how many operation
 * failures the server should tolerate before aborting the entire batch:
 * <ul>
 *   <li>{@code 0} (or absent) — best-effort: process all operations and collect
 *       every error into the response. Nothing stops on the first failure.</li>
 *   <li>{@code n > 0} — stop after {@code n} errors. Operations processed before
 *       the limit are not rolled back; this is an abort-forward, not a rollback.</li>
 * </ul>
 *
 * <p>The {@code Operations} field uses {@code @JsonProperty("Operations")} for
 * the same reason as {@link ScimPatchRequest}: the SCIM spec capitalises the
 * array field name and real IdP payloads will match that casing exactly.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScimBulkRequest {

    private List<String> schemas;

    /**
     * Max error count before the server stops processing.
     * 0 or null means "process all, never stop early".
     */
    private Integer failOnErrors;

    @JsonProperty("Operations")
    private List<ScimBulkOperation> operations;
}
