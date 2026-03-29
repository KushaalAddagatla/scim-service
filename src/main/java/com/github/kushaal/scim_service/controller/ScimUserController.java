package com.github.kushaal.scim_service.controller;

import com.github.kushaal.scim_service.dto.request.ScimUserRequest;
import com.github.kushaal.scim_service.dto.response.ScimListResponse;
import com.github.kushaal.scim_service.dto.response.ScimUserDto;
import com.github.kushaal.scim_service.model.ScimConstants;
import com.github.kushaal.scim_service.service.ScimUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping(value = "/scim/v2/Users", produces = ScimConstants.SCIM_CONTENT_TYPE)
@RequiredArgsConstructor
public class ScimUserController {

    private final ScimUserService userService;

    @PostMapping
    public ResponseEntity<ScimUserDto> create(@Valid @RequestBody ScimUserRequest request) {
        ScimUserDto created = userService.create(request);
        URI location = URI.create(created.getMeta().getLocation());
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ScimUserDto> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.findById(id));
    }

    @GetMapping
    public ResponseEntity<ScimListResponse<ScimUserDto>> list(
            @RequestParam(defaultValue = "1") int startIndex,
            @RequestParam(defaultValue = "100") int count,
            @RequestParam(required = false) String filter) {
        return ResponseEntity.ok(userService.findAll(startIndex, count, filter));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ScimUserDto> update(
            @PathVariable UUID id,
            @Valid @RequestBody ScimUserRequest request) {
        return ResponseEntity.ok(userService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
