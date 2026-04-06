package com.github.kushaal.scim_service.controller;

import com.github.kushaal.scim_service.dto.request.ScimPatchRequest;
import com.github.kushaal.scim_service.dto.request.ScimUserRequest;
import com.github.kushaal.scim_service.dto.response.ScimListResponse;
import com.github.kushaal.scim_service.dto.response.ScimUserDto;
import com.github.kushaal.scim_service.model.ScimConstants;
import com.github.kushaal.scim_service.service.ScimUserService;
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
@RequestMapping(value = "/scim/v2/Users", produces = ScimConstants.SCIM_CONTENT_TYPE)
@RequiredArgsConstructor
@Tag(name = "Users", description = "SCIM 2.0 User provisioning (RFC 7643/7644)")
public class ScimUserController {

    private final ScimUserService userService;

    @PostMapping
    @Operation(summary = "Provision a new user")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User created"),
            @ApiResponse(responseCode = "409", description = "userName already exists")
    })
    public ResponseEntity<ScimUserDto> create(@Valid @RequestBody ScimUserRequest request) {
        ScimUserDto created = userService.create(request);
        URI location = URI.create(created.getMeta().getLocation());
        return ResponseEntity.created(location)
                .eTag(created.getMeta().getVersion())
                .body(created);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get user by ID")
    @ApiResponse(responseCode = "404", description = "User not found")
    public ResponseEntity<ScimUserDto> getById(@PathVariable UUID id) {
        ScimUserDto dto = userService.findById(id);
        return ResponseEntity.ok()
                .eTag(dto.getMeta().getVersion())
                .body(dto);
    }

    @GetMapping
    @Operation(summary = "List users with optional filter and pagination")
    public ResponseEntity<ScimListResponse<ScimUserDto>> list(
            @Parameter(description = "1-based start index") @RequestParam(defaultValue = "1") int startIndex,
            @Parameter(description = "Max results per page") @RequestParam(defaultValue = "100") int count,
            @Parameter(description = "SCIM filter expression, e.g. userName eq \"alice\"") @RequestParam(required = false) String filter) {
        return ResponseEntity.ok(userService.findAll(startIndex, count, filter));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Full replace (PUT)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User replaced"),
            @ApiResponse(responseCode = "412", description = "If-Match version mismatch")
    })
    public ResponseEntity<ScimUserDto> update(
            @PathVariable UUID id,
            @Valid @RequestBody ScimUserRequest request,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        ScimUserDto updated = userService.update(id, request, ifMatch);
        return ResponseEntity.ok()
                .eTag(updated.getMeta().getVersion())
                .body(updated);
    }

    @PatchMapping("/{id}")
    @Operation(
            summary = "Partial update via JSON Patch (RFC 6902)",
            description = "Operations array supports add, remove, replace on scalar and " +
                    "multi-valued attributes (e.g. emails[type eq \"work\"].value).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User patched"),
            @ApiResponse(responseCode = "400", description = "Malformed patch operation"),
            @ApiResponse(responseCode = "412", description = "If-Match version mismatch")
    })
    public ResponseEntity<ScimUserDto> patch(
            @PathVariable UUID id,
            @RequestBody ScimPatchRequest request,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        ScimUserDto patched = userService.patch(id, request, ifMatch);
        return ResponseEntity.ok()
                .eTag(patched.getMeta().getVersion())
                .body(patched);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Deprovision user (soft delete — sets active=false)")
    @ApiResponse(responseCode = "204", description = "User deprovisioned")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
