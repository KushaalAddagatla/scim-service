package com.github.kushaal.scim_service.controller;

import com.github.kushaal.scim_service.dto.request.ScimBulkRequest;
import com.github.kushaal.scim_service.dto.response.ScimBulkResponse;
import com.github.kushaal.scim_service.model.ScimConstants;
import com.github.kushaal.scim_service.service.ScimBulkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/scim/v2/Bulk",
        consumes = ScimConstants.SCIM_CONTENT_TYPE,
        produces = ScimConstants.SCIM_CONTENT_TYPE)
@RequiredArgsConstructor
@Tag(name = "Bulk", description = "RFC 7644 §3.7 — batch provisioning endpoint")
public class ScimBulkController {

    private final ScimBulkService bulkService;

    @PostMapping
    @Operation(
            summary = "Execute a bulk operation batch",
            description = """
                    Accepts an array of SCIM sub-operations (POST, PUT, PATCH, DELETE) \
                    and executes them in order. Each operation is processed independently; \
                    earlier operations are not rolled back if a later one fails. \
                    Use `failOnErrors` to control how many failures to tolerate before \
                    aborting the remaining operations. Supports `bulkId` forward-references \
                    so a POST result can be referenced by a later operation in the same batch."""
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Batch processed; inspect each operation result for individual status codes"),
            @ApiResponse(responseCode = "400", description = "Malformed request or operation count exceeds server maximum"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid Bearer token"),
            @ApiResponse(responseCode = "403", description = "Token lacks scim:provision scope")
    })
    public ResponseEntity<ScimBulkResponse> bulk(@RequestBody ScimBulkRequest request) {
        return ResponseEntity.ok(bulkService.process(request));
    }
}
