package com.github.kushaal.scim_service.controller;

import com.github.kushaal.scim_service.dto.request.ScimGroupRequest;
import com.github.kushaal.scim_service.dto.request.ScimPatchRequest;
import com.github.kushaal.scim_service.dto.response.ScimGroupDto;
import com.github.kushaal.scim_service.dto.response.ScimListResponse;
import com.github.kushaal.scim_service.model.ScimConstants;
import com.github.kushaal.scim_service.service.ScimGroupService;
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
public class ScimGroupController {

    private final ScimGroupService groupService;

    @PostMapping
    public ResponseEntity<ScimGroupDto> create(@Valid @RequestBody ScimGroupRequest request) {
        ScimGroupDto created = groupService.create(request);
        URI location = URI.create(created.getMeta().getLocation());
        return ResponseEntity.created(location)
                .eTag(created.getMeta().getVersion())
                .body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ScimGroupDto> getById(@PathVariable UUID id) {
        ScimGroupDto dto = groupService.findById(id);
        return ResponseEntity.ok()
                .eTag(dto.getMeta().getVersion())
                .body(dto);
    }

    @GetMapping
    public ResponseEntity<ScimListResponse<ScimGroupDto>> list(
            @RequestParam(defaultValue = "1") int startIndex,
            @RequestParam(defaultValue = "100") int count,
            @RequestParam(required = false) String filter) {
        return ResponseEntity.ok(groupService.findAll(startIndex, count, filter));
    }

    @PatchMapping("/{id}")
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
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        groupService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
