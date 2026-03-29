package com.github.kushaal.scim_service.service;

import com.github.kushaal.scim_service.dto.request.ScimPatchOperation;
import com.github.kushaal.scim_service.exception.ScimInvalidValueException;
import com.github.kushaal.scim_service.model.entity.ScimUser;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Set;

/**
 * Applies a list of SCIM PATCH operations (RFC 7644 §3.5.2 / RFC 6902) to a
 * {@link ScimUser} entity in place.
 *
 * <p>This class is intentionally separate from {@link ScimUserService} because
 * the apply logic is substantial and will grow (multi-valued paths in 3.3). Keeping
 * it isolated also makes unit-testing the patch semantics straightforward without
 * needing a full Spring context.
 *
 * <p><b>Subtask 3.2 scope:</b> scalar paths and the path-less form.
 * Multi-valued paths ({@code emails[...]}, {@code phoneNumbers[...]}) are added in 3.3.
 */
@Component
public class PatchApplier {

    private static final Set<String> VALID_OPS = Set.of("add", "remove", "replace");

    public void apply(ScimUser user, List<ScimPatchOperation> operations) {
        if (operations == null || operations.isEmpty()) {
            return;
        }
        for (ScimPatchOperation operation : operations) {
            applyOperation(user, operation);
        }
    }

    // ── Operation dispatch ────────────────────────────────────────────────────

    private void applyOperation(ScimUser user, ScimPatchOperation operation) {
        String op = operation.getOp();
        if (op == null || op.isBlank()) {
            throw new ScimInvalidValueException("Each PATCH operation must include an 'op' field");
        }
        op = op.toLowerCase();
        if (!VALID_OPS.contains(op)) {
            throw new ScimInvalidValueException(
                    "Invalid op '" + operation.getOp() + "'. Must be one of: add, remove, replace");
        }

        String path = operation.getPath();

        if (path == null || path.isBlank()) {
            applyPathless(user, op, operation.getValue());
        } else {
            applyWithPath(user, op, path, operation.getValue());
        }
    }

    // ── Path-less form ────────────────────────────────────────────────────────
    // { "op": "replace", "value": { "active": false, "displayName": "Johnny" } }
    //
    // No path — the value is an object whose keys are attribute names.
    // Okta uses this form for multi-attribute updates in a single operation.
    // Each key is delegated to applyAddOrReplace as if it were a path.

    private void applyPathless(ScimUser user, String op, JsonNode value) {
        if (!"replace".equals(op) && !"add".equals(op)) {
            throw new ScimInvalidValueException(
                    "Path-less operations only support 'add' and 'replace', not '" + op + "'");
        }
        if (value == null || !value.isObject()) {
            throw new ScimInvalidValueException(
                    "Path-less '" + op + "' requires an object value with attribute keys");
        }
        // Jackson 3.x renamed fields() → properties(), which returns a Set not an Iterator
        for (var entry : value.properties()) {
            applyAddOrReplace(user, entry.getKey().toLowerCase(), entry.getKey(), entry.getValue());
        }
    }

    // ── With-path form ────────────────────────────────────────────────────────

    private void applyWithPath(ScimUser user, String op, String path, JsonNode value) {
        if ("remove".equals(op)) {
            applyRemove(user, path.toLowerCase(), path);
        } else {
            if (value == null) {
                throw new ScimInvalidValueException("'value' is required for op '" + op + "'");
            }
            applyAddOrReplace(user, path.toLowerCase(), path, value);
        }
    }

    // ── add / replace ─────────────────────────────────────────────────────────
    // For scalar attributes, add and replace are identical — both set the field.
    // The distinction only matters for multi-valued attributes (3.3): add appends
    // to the collection, replace overwrites it.

    void applyAddOrReplace(ScimUser user, String pathLower, String originalPath, JsonNode value) {
        switch (pathLower) {
            case "active"          -> user.setActive(nodeToBoolean(value, originalPath));
            case "displayname"     -> user.setDisplayName(nodeToText(value));
            case "username"        -> user.setUserName(nodeToText(value));
            case "externalid"      -> user.setExternalId(nodeToText(value));
            case "title"           -> user.setTitle(nodeToText(value));
            case "locale"          -> user.setLocale(nodeToText(value));
            case "timezone"        -> user.setTimezone(nodeToText(value));
            case "profileurl"      -> user.setProfileUrl(nodeToText(value));
            case "name.givenname"  -> user.setGivenName(nodeToText(value));
            case "name.familyname" -> user.setFamilyName(nodeToText(value));
            case "name.middlename" -> user.setMiddleName(nodeToText(value));

            // Path-less form may send "name": { "givenName": "...", "familyName": "..." }
            // as a nested object rather than using dot-notation paths.
            case "name" -> applyNameObject(user, value, originalPath);

            default -> throw new ScimInvalidValueException(
                    "Unsupported PATCH path: '" + originalPath + "'");
        }
    }

    private void applyNameObject(ScimUser user, JsonNode value, String originalPath) {
        if (!value.isObject()) {
            throw new ScimInvalidValueException(
                    "Expected object value for '" + originalPath + "'");
        }
        if (value.has("givenName"))  user.setGivenName(nodeToText(value.get("givenName")));
        if (value.has("familyName")) user.setFamilyName(nodeToText(value.get("familyName")));
        if (value.has("middleName")) user.setMiddleName(nodeToText(value.get("middleName")));
    }

    // ── remove ────────────────────────────────────────────────────────────────
    // Nullifies the field. Required attributes (active, userName) cannot be removed —
    // they would leave the resource in an invalid state and violate RFC 7643 §4.1.

    void applyRemove(ScimUser user, String pathLower, String originalPath) {
        switch (pathLower) {
            case "displayname"     -> user.setDisplayName(null);
            case "externalid"      -> user.setExternalId(null);
            case "title"           -> user.setTitle(null);
            case "locale"          -> user.setLocale(null);
            case "timezone"        -> user.setTimezone(null);
            case "profileurl"      -> user.setProfileUrl(null);
            case "name.givenname"  -> user.setGivenName(null);
            case "name.familyname" -> user.setFamilyName(null);
            case "name.middlename" -> user.setMiddleName(null);
            case "active", "username" -> throw new ScimInvalidValueException(
                    "Cannot remove required attribute: '" + originalPath + "'");
            default -> throw new ScimInvalidValueException(
                    "Unsupported PATCH path: '" + originalPath + "'");
        }
    }

    // ── Value extraction helpers ──────────────────────────────────────────────

    // active arrives as JSON boolean (false) or JSON string ("false") depending on the IdP.
    // Okta sends true JSON booleans; some clients stringify them. Handle both.
    private boolean nodeToBoolean(JsonNode node, String path) {
        if (node.isBoolean())  return node.booleanValue();
        if (node.isTextual())  return Boolean.parseBoolean(node.textValue());
        throw new ScimInvalidValueException(
                "Expected boolean value for '" + path + "', got: " + node.getNodeType());
    }

    private String nodeToText(JsonNode node) {
        if (node == null || node.isNull()) return null;
        return node.asText();
    }
}
