package com.github.kushaal.scim_service.mapper;

import com.github.kushaal.scim_service.dto.request.ScimGroupRequest;
import com.github.kushaal.scim_service.dto.response.ScimGroupDto;
import com.github.kushaal.scim_service.dto.response.ScimMeta;
import com.github.kushaal.scim_service.model.entity.ScimGroup;
import com.github.kushaal.scim_service.model.entity.ScimGroupMembership;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class ScimGroupMapper {

    // ── Entity → DTO ──────────────────────────────────────────────────────────

    public ScimGroupDto toDto(ScimGroup group) {
        return ScimGroupDto.builder()
                .id(group.getId().toString())
                .externalId(group.getExternalId())
                .displayName(group.getDisplayName())
                .members(mapMembers(group.getMemberships()))
                .meta(mapMeta(group))
                .build();
    }

    // ── Request → Entity (create) ─────────────────────────────────────────────
    // Memberships are NOT set here — the service resolves user UUIDs from
    // the request and attaches ScimGroupMembership objects to the saved group.

    public ScimGroup toEntity(ScimGroupRequest request) {
        return ScimGroup.builder()
                .externalId(request.getExternalId())
                .displayName(request.getDisplayName())
                .build();
    }

    // ── Request → Entity (update / PUT) ───────────────────────────────────────

    public void updateEntity(ScimGroup existing, ScimGroupRequest request) {
        existing.setExternalId(request.getExternalId());
        existing.setDisplayName(request.getDisplayName());
        // Memberships are replaced separately in the service (clear + re-add)
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private List<ScimGroupDto.MemberDto> mapMembers(List<ScimGroupMembership> memberships) {
        if (memberships == null || memberships.isEmpty()) return Collections.emptyList();
        return memberships.stream()
                .map(m -> {
                    String ref = ServletUriComponentsBuilder.fromCurrentContextPath()
                            .path("/scim/v2/Users/{id}")
                            .buildAndExpand(m.getId().getUserId())
                            .toUriString();
                    return ScimGroupDto.MemberDto.builder()
                            .value(m.getId().getUserId().toString())
                            .display(m.getDisplay())
                            .ref(ref)
                            .build();
                })
                .collect(Collectors.toList());
    }

    private ScimMeta mapMeta(ScimGroup group) {
        String location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/scim/v2/Groups/{id}")
                .buildAndExpand(group.getId())
                .toUriString();

        return ScimMeta.builder()
                .resourceType("Group")
                .created(group.getCreatedAt())
                .lastModified(group.getUpdatedAt())
                .location(location)
                .version("W/\"" + group.getMetaVersion() + "\"")
                .build();
    }
}
