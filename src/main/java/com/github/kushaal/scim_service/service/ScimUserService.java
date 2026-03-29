package com.github.kushaal.scim_service.service;

import com.github.kushaal.scim_service.dto.request.ScimUserRequest;
import com.github.kushaal.scim_service.dto.response.ScimListResponse;
import com.github.kushaal.scim_service.dto.response.ScimUserDto;
import com.github.kushaal.scim_service.exception.ScimConflictException;
import com.github.kushaal.scim_service.exception.ScimPreconditionFailedException;
import com.github.kushaal.scim_service.exception.ScimResourceNotFoundException;
import com.github.kushaal.scim_service.filter.ScimFilterParser;
import com.github.kushaal.scim_service.filter.ScimUserSpecification;
import com.github.kushaal.scim_service.mapper.ScimUserMapper;
import com.github.kushaal.scim_service.model.entity.AuditLog;
import com.github.kushaal.scim_service.model.entity.ScimUser;
import com.github.kushaal.scim_service.repository.AuditLogRepository;
import com.github.kushaal.scim_service.repository.ScimUserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScimUserService {

    private final ScimUserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final ScimUserMapper mapper;

    // ── Create ────────────────────────────────────────────────────────────────

    @Transactional
    public ScimUserDto create(ScimUserRequest request) {
        if (userRepository.existsByUserName(request.getUserName())) {
            throw new ScimConflictException(
                    "User with userName '" + request.getUserName() + "' already exists");
        }

        ScimUser user = mapper.toEntity(request);

        // Default active to true if the client omitted it.
        // Defaulting here in the service (not the entity) keeps the rule explicit
        // and makes it easy to find if the business logic ever changes.
        if (user.getActive() == null) {
            user.setActive(true);
        }

        ScimUser saved = userRepository.save(user);
        writeAuditLog("PROVISION", saved.getId(), "SUCCESS", null);

        return mapper.toDto(saved);
    }

    // ── Read (single) ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ScimUserDto findById(UUID id) {
        ScimUser user = userRepository.findById(id)
                .orElseThrow(() -> new ScimResourceNotFoundException("User not found: " + id));
        return mapper.toDto(user);
    }

    // ── Read (list) ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ScimListResponse<ScimUserDto> findAll(int startIndex, int count, String filter) {
        // SCIM startIndex is 1-based. Spring's PageRequest is 0-based.
        // Math.max guards against a client sending startIndex=0, which is technically
        // invalid per the spec but shouldn't cause an exception.
        PageRequest pageable = PageRequest.of(Math.max(0, startIndex - 1), count);

        Page<ScimUser> page;
        if (filter != null && !filter.isBlank()) {
            ScimFilterParser.ParsedFilter parsed = ScimFilterParser.parse(filter);
            Specification<ScimUser> spec = ScimUserSpecification.fromFilter(parsed);
            page = userRepository.findAll(spec, pageable);
        } else {
            page = userRepository.findAll(pageable);
        }

        List<ScimUserDto> dtos = page.getContent().stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());

        return ScimListResponse.<ScimUserDto>builder()
                .totalResults((int) page.getTotalElements())
                .startIndex(startIndex)
                .itemsPerPage(dtos.size())
                .resources(dtos)
                .build();
    }

    // ── Update (PUT — full replace) ───────────────────────────────────────────

    @Transactional
    public ScimUserDto update(UUID id, ScimUserRequest request, String ifMatch) {
        ScimUser existing = userRepository.findById(id)
                .orElseThrow(() -> new ScimResourceNotFoundException("User not found: " + id));

        // If-Match is optional — we don't require it because Okta omits it on many
        // PUT/PATCH calls. When it IS present, the client's stated version must equal
        // the stored version; a mismatch means the client is operating on stale data
        // and we reject with 412 rather than silently overwriting the newer state.
        // This satisfies RFC 7644 §3.14 without breaking IdPs that skip the header.
        if (ifMatch != null) {
            int requestedVersion = parseETagVersion(ifMatch);
            if (requestedVersion != existing.getMetaVersion()) {
                throw new ScimPreconditionFailedException(
                        "Version mismatch: client has W/\"" + requestedVersion +
                        "\", server has W/\"" + existing.getMetaVersion() + "\"");
            }
        }

        // Only check uniqueness if the userName is actually changing.
        // Without this guard, a PUT that sends the same userName would conflict with itself.
        if (!existing.getUserName().equals(request.getUserName())
                && userRepository.existsByUserName(request.getUserName())) {
            throw new ScimConflictException(
                    "User with userName '" + request.getUserName() + "' already exists");
        }

        mapper.updateEntity(existing, request);
        if (existing.getActive() == null) {
            existing.setActive(true);
        }
        existing.setMetaVersion(existing.getMetaVersion() + 1);

        ScimUser saved = userRepository.save(existing);
        writeAuditLog("SCIM_PUT", saved.getId(), "SUCCESS", null);

        return mapper.toDto(saved);
    }

    // ── Delete (soft) ─────────────────────────────────────────────────────────

    @Transactional
    public void delete(UUID id) {
        ScimUser user = userRepository.findById(id)
                .orElseThrow(() -> new ScimResourceNotFoundException("User not found: " + id));

        // Soft delete: mark inactive, keep the row.
        // This is the correct enterprise pattern — hard-deleting a provisioned identity
        // breaks audit trails and makes it impossible to detect replay attacks
        // (a deleted user's ID could be reused). The row stays, active becomes false.
        user.setActive(false);
        user.setMetaVersion(user.getMetaVersion() + 1);
        userRepository.save(user);

        writeAuditLog("DEPROVISION", user.getId(), "SUCCESS", null);
    }

    // ── ETag helpers ─────────────────────────────────────────────────────────

    // Extracts the integer version from a weak ETag string.
    // Accepts both W/"1" (spec-compliant) and "1" (some clients omit the W/ prefix).
    int parseETagVersion(String etag) {
        String stripped = etag.trim().replaceFirst("^W/", "").replace("\"", "");
        try {
            return Integer.parseInt(stripped);
        } catch (NumberFormatException e) {
            throw new ScimPreconditionFailedException("Malformed If-Match value: " + etag);
        }
    }

    // ── Audit log ─────────────────────────────────────────────────────────────

    private void writeAuditLog(String eventType, UUID targetUserId, String outcome, String scimOperation) {
        // MDC (Mapped Diagnostic Context) is a thread-local map that the correlation ID
        // filter (added in a later step) will populate per-request. If the filter isn't
        // installed yet, this returns null — the audit log entry is still written,
        // just without a correlation ID.
        UUID correlationId = parseCorrelationId(MDC.get("correlationId"));

        AuditLog entry = AuditLog.builder()
                .eventType(eventType)
                .actor("system")
                .targetUserId(targetUserId)
                .resourceId(targetUserId != null ? targetUserId.toString() : null)
                .scimOperation(scimOperation)
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
