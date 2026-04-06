package com.github.kushaal.scim_service.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

/**
 * SCIM Bulk response body (RFC 7644 §3.7.3).
 *
 * <p>The {@code Operations} array mirrors the request array — one result entry
 * per input operation, in the same order, unless the server stopped early due
 * to {@code failOnErrors} being exceeded (in which case trailing results are absent).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScimBulkResponse {

    private List<String> schemas;

    @JsonProperty("Operations")
    private List<ScimBulkOperationResult> operations;
}
