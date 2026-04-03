package com.github.kushaal.scim_service.service;

import com.github.kushaal.scim_service.dto.request.ScimGroupRequest;
import com.github.kushaal.scim_service.dto.request.ScimPatchOperation;
import com.github.kushaal.scim_service.dto.request.ScimPatchRequest;
import com.github.kushaal.scim_service.dto.response.ScimGroupDto;
import com.github.kushaal.scim_service.dto.response.ScimListResponse;
import com.github.kushaal.scim_service.exception.ScimInvalidValueException;
import com.github.kushaal.scim_service.exception.ScimPreconditionFailedException;
import com.github.kushaal.scim_service.exception.ScimResourceNotFoundException;
import com.github.kushaal.scim_service.filter.ScimFilterParser;
import com.github.kushaal.scim_service.filter.ScimGroupSpecification;
import com.github.kushaal.scim_service.mapper.ScimGroupMapper;
import com.github.kushaal.scim_service.model.entity.AuditLog;
import com.github.kushaal.scim_service.model.entity.ScimGroup;
import com.github.kushaal.scim_service.model.entity.ScimGroupMembership;
import com.github.kushaal.scim_service.model.entity.ScimGroupMembershipId;
import com.github.kushaal.scim_service.model.entity.ScimUser;
import com.github.kushaal.scim_service.repository.AuditLogRepository;
import com.github.kushaal.scim_service.repository.ScimGroupRepository;
import com.github.kushaal.scim_service.repository.ScimUserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScimGroupService {

    private final ScimGroupRepository groupRepository;
    private final ScimUserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final ScimGroupMapper mapper;

    // Matches PATCH paths like: members[value eq "some-uuid"]
    private static final Pattern MEMBER_FILTER_PATTERN =
            Pattern.compile("members\\[value\\s+eq\\s+\"([^\"]+)\"\\]", Pattern.CASE_INSENSITIVE);

    // ── Create ────────────────────────────────────────────────────────────────

    @Transactional
    public ScimGroupDto create(ScimGroupRequest request) {
        // Save the group first to get its generated UUID, which is needed to
        // build the composite key for any membership rows we add next.
        ScimGroup group = mapper.toEntity(request);
        ScimGroup saved = groupRepository.save(group);

        if (request.getMembers() != null && !request.getMembers().isEmpty()) {
            for (ScimGroupRequest.MemberRequest memberReq : request.getMembers()) {
                addMemberToGroup(saved, memberReq.getValue(), memberReq.getDisplay());
            }
            // Cascade persists the membership rows; explicit save keeps intent clear.
            groupRepository.save(saved);
        }

        writeAuditLog("PROVISION", saved.getId(), "SUCCESS");
        return mapper.toDto(saved);
    }

    // ── Read (single) ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ScimGroupDto findById(UUID id) {
        ScimGroup group = groupRepository.findById(id)
                .orElseThrow(() -> new ScimResourceNotFoundException("Group not found: " + id));
        return mapper.toDto(group);
    }

    // ── Read (list) ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ScimListResponse<ScimGroupDto> findAll(int startIndex, int count, String filter) {
        PageRequest pageable = PageRequest.of(Math.max(0, startIndex - 1), count);

        Page<ScimGroup> page;
        if (filter != null && !filter.isBlank()) {
            ScimFilterParser.ParsedFilter parsed = ScimFilterParser.parse(filter);
            Specification<ScimGroup> spec = ScimGroupSpecification.fromFilter(parsed);
            page = groupRepository.findAll(spec, pageable);
        } else {
            page = groupRepository.findAll(pageable);
        }

        List<ScimGroupDto> dtos = page.getContent().stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());

        return ScimListResponse.<ScimGroupDto>builder()
                .totalResults((int) page.getTotalElements())
                .startIndex(startIndex)
                .itemsPerPage(dtos.size())
                .resources(dtos)
                .build();
    }

    // ── Patch ─────────────────────────────────────────────────────────────────

    // Groups only support patching the members collection — no scalar field patches
    // (displayName changes go through PUT). This keeps group PATCH simple and aligned
    // with what Okta actually sends: add/remove members, nothing else.
    @Transactional
    public ScimGroupDto patch(UUID id, ScimPatchRequest request, String ifMatch) {
        ScimGroup group = groupRepository.findById(id)
                .orElseThrow(() -> new ScimResourceNotFoundException("Group not found: " + id));

        if (ifMatch != null) {
            int requestedVersion = parseETagVersion(ifMatch);
            if (requestedVersion != group.getMetaVersion()) {
                throw new ScimPreconditionFailedException(
                        "Version mismatch: client has W/\"" + requestedVersion +
                        "\", server has W/\"" + group.getMetaVersion() + "\"");
            }
        }

        if (request.getOperations() == null || request.getOperations().isEmpty()) {
            return mapper.toDto(group);
        }

        for (ScimPatchOperation op : request.getOperations()) {
            applyGroupPatch(group, op);
        }

        group.setMetaVersion(group.getMetaVersion() + 1);
        ScimGroup saved = groupRepository.save(group);
        writeAuditLog("SCIM_PATCH", saved.getId(), "SUCCESS");
        return mapper.toDto(saved);
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    // Hard delete — unlike users (which soft-delete to preserve audit trail),
    // groups are just organizational containers. Deleting a group does NOT
    // deactivate its members; user accounts are untouched. The DB cascades
    // delete membership rows automatically (ON DELETE CASCADE + CascadeType.ALL).
    @Transactional
    public void delete(UUID id) {
        ScimGroup group = groupRepository.findById(id)
                .orElseThrow(() -> new ScimResourceNotFoundException("Group not found: " + id));
        groupRepository.delete(group);
        writeAuditLog("DEPROVISION", id, "SUCCESS");
    }

    // ── Group PATCH helpers ───────────────────────────────────────────────────

    private void applyGroupPatch(ScimGroup group, ScimPatchOperation op) {
        if (op.getOp() == null) {
            throw new ScimInvalidValueException("PATCH operation is missing required 'op' field");
        }

        String opLower = op.getOp().toLowerCase();
        String path = op.getPath();
        String pathLower = path != null ? path.toLowerCase() : null;

        // Path-less form: { "op": "replace", "value": { "displayName": "...", "members": [...] } }
        // Okta sends this to sync the full group state — e.g. after removing a member it sends
        // a path-less replace carrying the updated displayName (and sometimes the full members
        // list). We must handle it rather than reject it with 400.
        if (pathLower == null) {
            if (!"replace".equals(opLower) && !"add".equals(opLower)) {
                throw new ScimInvalidValueException(
                        "Path-less PATCH op '" + op.getOp() + "' is not supported for Groups");
            }
            applyGroupPathless(group, op.getValue());
            return;
        }

        if (!pathLower.startsWith("members")) {
            throw new ScimInvalidValueException(
                    "PATCH path '" + path + "' is not supported for Groups. Only 'members' is supported.");
        }

        // Path filter form: members[value eq "userId"] — only valid with op=remove
        if (pathLower.startsWith("members[")) {
            if (!"remove".equals(opLower)) {
                throw new ScimInvalidValueException(
                        "Filtered path '" + path + "' is only valid with op 'remove'");
            }
            removeMemberByFilter(group, path);
            return;
        }

        switch (opLower) {
            case "add"     -> addMembersFromNode(group, op.getValue());
            case "remove"  -> removeMembersFromNode(group, op.getValue());
            // replace wipes the existing member list and installs the new one —
            // same semantics as PUT on the members sub-resource
            case "replace" -> {
                group.getMemberships().clear();
                addMembersFromNode(group, op.getValue());
            }
            default -> throw new ScimInvalidValueException("Unknown op: '" + op.getOp() + "'");
        }
    }

    // Path-less group patch: value is an object whose keys are group attribute names.
    // Read-only fields (id, schemas) are silently ignored — Okta includes them for
    // context but they must not overwrite server-assigned values.
    // If a "members" key is present, it is treated as a full replacement of the
    // members list (same semantics as op=replace path=members).
    private void applyGroupPathless(ScimGroup group, JsonNode value) {
        if (value == null || !value.isObject()) {
            throw new ScimInvalidValueException("Path-less group PATCH requires an object value");
        }
        if (value.has("displayName")) {
            group.setDisplayName(value.get("displayName").asText());
        }
        if (value.has("externalId")) {
            group.setExternalId(value.get("externalId").asText());
        }
        // Full members replacement — Okta may send the current member list here
        // when syncing after a membership change.
        if (value.has("members") && value.get("members").isArray()) {
            group.getMemberships().clear();
            addMembersFromNode(group, value.get("members"));
        }
        // id and schemas are intentionally ignored — they are read-only server fields
    }

    private void addMembersFromNode(ScimGroup group, JsonNode value) {
        if (value == null || !value.isArray()) {
            throw new ScimInvalidValueException("'members' add/replace value must be a JSON array");
        }
        for (JsonNode node : value) {
            String userId = node.path("value").asText(null);
            if (userId == null) {
                throw new ScimInvalidValueException("Each member entry must have a 'value' field (user UUID)");
            }
            addMemberToGroup(group, userId, node.path("display").asText(null));
        }
    }

    private void removeMembersFromNode(ScimGroup group, JsonNode value) {
        if (value == null || !value.isArray()) {
            throw new ScimInvalidValueException("'members' remove value must be a JSON array");
        }
        for (JsonNode node : value) {
            String userIdStr = node.path("value").asText(null);
            if (userIdStr == null) continue;
            UUID userId = parseUserId(userIdStr);
            group.getMemberships().removeIf(m -> m.getId().getUserId().equals(userId));
        }
    }

    private void removeMemberByFilter(ScimGroup group, String path) {
        Matcher matcher = MEMBER_FILTER_PATTERN.matcher(path);
        if (!matcher.matches()) {
            throw new ScimInvalidValueException("Malformed members filter path: '" + path + "'");
        }
        UUID userId = parseUserId(matcher.group(1));
        group.getMemberships().removeIf(m -> m.getId().getUserId().equals(userId));
    }

    // Shared helper: resolves userId string → user entity, guards against duplicates,
    // builds the membership with the composite key fully populated.
    private void addMemberToGroup(ScimGroup group, String userIdStr, String display) {
        UUID userId = parseUserId(userIdStr);

        // Idempotent — skip if the user is already a member of this group
        boolean alreadyMember = group.getMemberships().stream()
                .anyMatch(m -> m.getId().getUserId().equals(userId));
        if (alreadyMember) return;

        ScimUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ScimResourceNotFoundException("User not found: " + userId));

        ScimGroupMembership membership = ScimGroupMembership.builder()
                .id(new ScimGroupMembershipId(group.getId(), userId))
                .group(group)
                .user(user)
                .display(display != null ? display : user.getDisplayName())
                .build();
        group.getMemberships().add(membership);
    }

    private UUID parseUserId(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new ScimInvalidValueException("Invalid user UUID: '" + raw + "'");
        }
    }

    // ── ETag helpers ─────────────────────────────────────────────────────────

    int parseETagVersion(String etag) {
        String stripped = etag.trim().replaceFirst("^W/", "").replace("\"", "");
        try {
            return Integer.parseInt(stripped);
        } catch (NumberFormatException e) {
            throw new ScimPreconditionFailedException("Malformed If-Match value: " + etag);
        }
    }

    // ── Audit log ─────────────────────────────────────────────────────────────

    private void writeAuditLog(String eventType, UUID resourceId, String outcome) {
        UUID correlationId = parseCorrelationId(MDC.get("correlationId"));
        AuditLog entry = AuditLog.builder()
                .eventType(eventType)
                .actor("system")
                .resourceId(resourceId != null ? resourceId.toString() : null)
                .outcome(outcome)
                .correlationId(correlationId)
                .build();
        auditLogRepository.save(entry);
    }

    private UUID parseCorrelationId(String raw) {
        if (raw == null) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
