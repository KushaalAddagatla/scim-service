package com.github.kushaal.scim_service.controller;

import com.github.kushaal.scim_service.dto.request.ScimGroupRequest;
import com.github.kushaal.scim_service.dto.request.ScimPatchRequest;
import com.github.kushaal.scim_service.dto.response.ScimGroupDto;
import com.github.kushaal.scim_service.dto.response.ScimListResponse;
import com.github.kushaal.scim_service.model.ScimConstants;
import com.github.kushaal.scim_service.service.ScimGroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping(value = "/scim/v2/Groups", produces = ScimConstants.SCIM_CONTENT_TYPE)
@RequiredArgsConstructor
@Tag(name = "Groups", description = "SCIM 2.0 Group management (RFC 7643/7644)")
public class ScimGroupController {

    private final ScimGroupService groupService;

    @PostMapping
    @Operation(summary = "Create a new group")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Group created"),
            @ApiResponse(responseCode = "409", description = "displayName already exists")
    })
    public ResponseEntity<ScimGroupDto> create(@Valid @RequestBody ScimGroupRequest request) {
        ScimGroupDto created = groupService.create(request);
        URI location = URI.create(created.getMeta().getLocation());
        return ResponseEntity.created(location)
                .eTag(created.getMeta().getVersion())
                .body(created);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get group by ID")
    @ApiResponse(responseCode = "404", description = "Group not found")
    public ResponseEntity<ScimGroupDto> getById(@PathVariable UUID id) {
        ScimGroupDto dto = groupService.findById(id);
        return ResponseEntity.ok()
                .eTag(dto.getMeta().getVersion())
                .body(dto);
    }

    @GetMapping
    @Operation(summary = "List groups with optional filter and pagination")
    public ResponseEntity<ScimListResponse<ScimGroupDto>> list(
            @Parameter(description = "1-based start index") @RequestParam(defaultValue = "1") int startIndex,
            @Parameter(description = "Max results per page") @RequestParam(defaultValue = "100") int count,
            @Parameter(description = "SCIM filter expression") @RequestParam(required = false) String filter) {
        return ResponseEntity.ok(groupService.findAll(startIndex, count, filter));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Add or remove group members via JSON Patch")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Group patched"),
            @ApiResponse(responseCode = "412", description = "If-Match version mismatch")
    })
    public ResponseEntity<ScimGroupDto> patch(
            @PathVariable UUID id,
            @RequestBody ScimPatchRequest request,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        ScimGroupDto patched = groupService.patch(id, request, ifMatch);
        return ResponseEntity.ok()
                .eTag(patched.getMeta().getVersion())
                .body(patched);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete group")
    @ApiResponse(responseCode = "204", description = "Group deleted")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        groupService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
