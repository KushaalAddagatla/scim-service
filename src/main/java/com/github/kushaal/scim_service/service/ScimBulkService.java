package com.github.kushaal.scim_service.service;

import com.github.kushaal.scim_service.dto.request.ScimBulkOperation;
import com.github.kushaal.scim_service.dto.request.ScimBulkRequest;
import com.github.kushaal.scim_service.dto.request.ScimGroupRequest;
import com.github.kushaal.scim_service.dto.request.ScimPatchRequest;
import com.github.kushaal.scim_service.dto.request.ScimUserRequest;
import com.github.kushaal.scim_service.dto.response.ScimBulkOperationResult;
import com.github.kushaal.scim_service.dto.response.ScimBulkResponse;
import com.github.kushaal.scim_service.dto.response.ScimError;
import com.github.kushaal.scim_service.dto.response.ScimGroupDto;
import com.github.kushaal.scim_service.dto.response.ScimUserDto;
import com.github.kushaal.scim_service.exception.ScimConflictException;
import com.github.kushaal.scim_service.exception.ScimInvalidValueException;
import com.github.kushaal.scim_service.exception.ScimPreconditionFailedException;
import com.github.kushaal.scim_service.exception.ScimResourceNotFoundException;
import com.github.kushaal.scim_service.model.ScimConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Processes a SCIM Bulk request (RFC 7644 §3.7) by dispatching each sub-operation
 * to the appropriate User or Group service method.
 *
 * <h2>Why this service exists as a separate layer</h2>
 * Bulk is essentially a multiplexer: it deserialises each operation's {@code data}
 * field into the right DTO type (unknown until the path is parsed), then delegates
 * to the same service methods that the individual endpoints call. Keeping that
 * routing logic here — rather than in the controller — keeps the controller thin
 * and makes the routing testable in isolation.
 *
 * <h2>Atomicity</h2>
 * Each operation commits in its own transaction (the existing service methods are
 * {@code @Transactional}). There is no rollback of earlier operations when a later
 * one fails. This is best-effort mode, which the spec does not prohibit — RFC 7644
 * §3.7.1 requires only that the server honour {@code failOnErrors} as a stop signal,
 * not that it undo already-committed work.
 *
 * <h2>bulkId resolution</h2>
 * When a POST operation succeeds, its {@code bulkId} → created resource location is
 * stored in a session-scoped map. Subsequent operations can reference the new resource
 * before knowing its UUID by writing {@code "path": "/Users/bulkId:ref"} or embedding
 * {@code "bulkId:ref"} inside the {@code data} JSON (e.g. a group member {@code value}).
 * Both forms are resolved by string substitution before the path is parsed or the data
 * is deserialised.
 */
@Service
@RequiredArgsConstructor
public class ScimBulkService {

    static final int MAX_OPERATIONS = 100;

    private final ScimUserService userService;
    private final ScimGroupService groupService;
    private final ObjectMapper objectMapper;

    // Matches /Users, /Users/{id}, /Groups, /Groups/{id}
    private static final Pattern PATH_PATTERN =
            Pattern.compile("^/(Users|Groups)(?:/([^/]+))?$");

    // Matches the literal token "bulkId:someRef" anywhere in a string
    private static final Pattern BULK_ID_REF =
            Pattern.compile("bulkId:([\\w-]+)");

    public ScimBulkResponse process(ScimBulkRequest request) {
        List<ScimBulkOperation> ops = request.getOperations() != null
                ? request.getOperations() : List.of();

        // Guard: reject payloads that exceed the advertised maximum so a single
        // request cannot loop the server for an unbounded amount of work.
        if (ops.size() > MAX_OPERATIONS) {
            throw new ScimInvalidValueException(
                    "Bulk request exceeds maximum of " + MAX_OPERATIONS + " operations");
        }

        int failOnErrors = (request.getFailOnErrors() != null && request.getFailOnErrors() > 0)
                ? request.getFailOnErrors() : 0;

        List<ScimBulkOperationResult> results = new ArrayList<>();
        // bulkId → meta.location of the created resource, e.g. "/scim/v2/Users/uuid"
        Map<String, String> bulkIdToLocation = new HashMap<>();
        int errorCount = 0;

        for (ScimBulkOperation op : ops) {
            try {
                ScimBulkOperationResult result = executeOperation(op, bulkIdToLocation);

                // Register a successful POST so later operations can reference it by bulkId.
                if ("POST".equalsIgnoreCase(op.getMethod())
                        && op.getBulkId() != null
                        && result.getLocation() != null) {
                    bulkIdToLocation.put(op.getBulkId(), result.getLocation());
                }

                results.add(result);
            } catch (Exception e) {
                results.add(buildErrorResult(op, e));
                errorCount++;
                // failOnErrors = 0 means "never stop early".
                // failOnErrors = n means "stop after the nth error".
                if (failOnErrors > 0 && errorCount >= failOnErrors) {
                    break;
                }
            }
        }

        return ScimBulkResponse.builder()
                .schemas(List.of(ScimConstants.SCHEMA_BULK_RESPONSE))
                .operations(results)
                .build();
    }

    // ── Operation dispatcher ──────────────────────────────────────────────────

    private ScimBulkOperationResult executeOperation(
            ScimBulkOperation op, Map<String, String> bulkIdToLocation) {

        if (op.getMethod() == null) {
            throw new ScimInvalidValueException("Bulk operation missing 'method'");
        }
        if (op.getPath() == null) {
            throw new ScimInvalidValueException("Bulk operation missing 'path'");
        }

        // Substitute any "bulkId:ref" tokens in the path with the real UUID portion
        // of the previously-created resource's location.
        String resolvedPath = resolveBulkIdRefs(op.getPath(), bulkIdToLocation);

        Matcher m = PATH_PATTERN.matcher(resolvedPath);
        if (!m.matches()) {
            throw new ScimInvalidValueException(
                    "Unrecognised bulk operation path: " + op.getPath());
        }

        String resourceType = m.group(1);   // "Users" or "Groups"
        String rawId = m.group(2);          // UUID string or null
        UUID resourceId = rawId != null ? parseUuid(rawId) : null;

        // Substitute bulkId refs in the data JSON too (e.g. a group that adds a member
        // whose user was just created in the same bulk batch).
        JsonNode data = resolveDataBulkIdRefs(op.getData(), bulkIdToLocation);

        // If-Match comes from the bulk operation's version field, mirroring the
        // header semantics on individual PATCH/PUT endpoints.
        String ifMatch = op.getVersion();

        return switch (resourceType) {
            case "Users"  -> executeUserOp(op.getMethod().toUpperCase(), resourceId, data, op.getBulkId(), ifMatch);
            case "Groups" -> executeGroupOp(op.getMethod().toUpperCase(), resourceId, data, op.getBulkId(), ifMatch);
            default -> throw new ScimInvalidValueException(
                    "Unknown resource type in bulk path: " + resourceType);
        };
    }

    // ── User operations ───────────────────────────────────────────────────────

    private ScimBulkOperationResult executeUserOp(
            String method, UUID id, JsonNode data, String bulkId, String ifMatch) {
        return switch (method) {
            case "POST" -> {
                ScimUserDto created = userService.create(
                        objectMapper.treeToValue(data, ScimUserRequest.class));
                yield ScimBulkOperationResult.builder()
                        .method("POST")
                        .bulkId(bulkId)
                        .location(created.getMeta().getLocation())
                        .version(created.getMeta().getVersion())
                        .status(Map.of("code", "201"))
                        .response(created)
                        .build();
            }
            case "PUT" -> {
                ScimUserDto updated = userService.update(id,
                        objectMapper.treeToValue(data, ScimUserRequest.class), ifMatch);
                yield ScimBulkOperationResult.builder()
                        .method("PUT")
                        .location(updated.getMeta().getLocation())
                        .version(updated.getMeta().getVersion())
                        .status(Map.of("code", "200"))
                        .response(updated)
                        .build();
            }
            case "PATCH" -> {
                ScimUserDto patched = userService.patch(id,
                        objectMapper.treeToValue(data, ScimPatchRequest.class), ifMatch);
                yield ScimBulkOperationResult.builder()
                        .method("PATCH")
                        .location(patched.getMeta().getLocation())
                        .version(patched.getMeta().getVersion())
                        .status(Map.of("code", "200"))
                        .response(patched)
                        .build();
            }
            case "DELETE" -> {
                userService.delete(id);
                yield ScimBulkOperationResult.builder()
                        .method("DELETE")
                        .status(Map.of("code", "204"))
                        .build();
            }
            default -> throw new ScimInvalidValueException(
                    "Unsupported method '" + method + "' for /Users");
        };
    }

    // ── Group operations ──────────────────────────────────────────────────────

    private ScimBulkOperationResult executeGroupOp(
            String method, UUID id, JsonNode data, String bulkId, String ifMatch) {
        return switch (method) {
            case "POST" -> {
                ScimGroupDto created = groupService.create(
                        objectMapper.treeToValue(data, ScimGroupRequest.class));
                yield ScimBulkOperationResult.builder()
                        .method("POST")
                        .bulkId(bulkId)
                        .location(created.getMeta().getLocation())
                        .version(created.getMeta().getVersion())
                        .status(Map.of("code", "201"))
                        .response(created)
                        .build();
            }
            case "PATCH" -> {
                ScimGroupDto patched = groupService.patch(id,
                        objectMapper.treeToValue(data, ScimPatchRequest.class), ifMatch);
                yield ScimBulkOperationResult.builder()
                        .method("PATCH")
                        .location(patched.getMeta().getLocation())
                        .version(patched.getMeta().getVersion())
                        .status(Map.of("code", "200"))
                        .response(patched)
                        .build();
            }
            case "DELETE" -> {
                groupService.delete(id);
                yield ScimBulkOperationResult.builder()
                        .method("DELETE")
                        .status(Map.of("code", "204"))
                        .build();
            }
            default -> throw new ScimInvalidValueException(
                    "Unsupported method '" + method + "' for /Groups");
        };
    }

    // ── bulkId resolution helpers ─────────────────────────────────────────────

    /**
     * Replaces {@code bulkId:ref} tokens in the path with the UUID portion of
     * the previously-created resource's location.
     *
     * <p>Example: {@code "/Users/bulkId:abc"} where {@code abc → "/scim/v2/Users/some-uuid"}
     * becomes {@code "/Users/some-uuid"}.
     */
    private String resolveBulkIdRefs(String path, Map<String, String> bulkIdToLocation) {
        if (!path.contains("bulkId:")) {
            return path;
        }
        Matcher m = BULK_ID_REF.matcher(path);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String ref = m.group(1);
            String location = bulkIdToLocation.get(ref);
            if (location == null) {
                throw new ScimInvalidValueException(
                        "Unresolved bulkId reference: bulkId:" + ref);
            }
            // Extract just the last path segment (the UUID) from the location URL
            String resolvedId = location.substring(location.lastIndexOf('/') + 1);
            m.appendReplacement(sb, Matcher.quoteReplacement(resolvedId));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Substitutes {@code bulkId:ref} values inside the {@code data} JSON node.
     *
     * <p>The common case is a group POST that adds members whose users were
     * just created in the same bulk batch:
     * <pre>
     * { "members": [{"value": "bulkId:userRef"}] }
     * </pre>
     * The substitution replaces the string with the created user's UUID so the
     * group service receives a normal UUID member reference.
     */
    private JsonNode resolveDataBulkIdRefs(JsonNode data, Map<String, String> bulkIdToLocation) {
        if (data == null || bulkIdToLocation.isEmpty()) {
            return data;
        }
        String json = data.toString();
        if (!json.contains("bulkId:")) {
            return data;
        }
        for (Map.Entry<String, String> entry : bulkIdToLocation.entrySet()) {
            String uuid = entry.getValue().substring(entry.getValue().lastIndexOf('/') + 1);
            json = json.replace("bulkId:" + entry.getKey(), uuid);
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            // If reparsing fails (shouldn't happen — we only did string substitution),
            // return the original unmodified node and let the service fail naturally.
            return data;
        }
    }

    // ── Error handling ────────────────────────────────────────────────────────

    /**
     * Converts a service exception into a bulk operation result with the right
     * HTTP status code and SCIM error body, instead of letting the exception
     * propagate and abort the whole batch.
     */
    private ScimBulkOperationResult buildErrorResult(ScimBulkOperation op, Exception e) {
        int statusCode = httpStatusFor(e);
        ScimError error = ScimError.builder()
                .status(statusCode)
                .scimType(scimTypeFor(e))
                .detail(e.getMessage())
                .build();
        return ScimBulkOperationResult.builder()
                .method(op.getMethod())
                .bulkId(op.getBulkId())
                .status(Map.of("code", String.valueOf(statusCode)))
                .response(error)
                .build();
    }

    private int httpStatusFor(Exception e) {
        if (e instanceof ScimResourceNotFoundException) return 404;
        if (e instanceof ScimConflictException)         return 409;
        if (e instanceof ScimInvalidValueException)     return 400;
        if (e instanceof ScimPreconditionFailedException) return 412;
        return 500;
    }

    private String scimTypeFor(Exception e) {
        if (e instanceof ScimConflictException)     return "uniqueness";
        if (e instanceof ScimInvalidValueException) return "invalidValue";
        return null;
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new ScimInvalidValueException("Invalid resource ID in bulk path: " + raw);
        }
    }
}
