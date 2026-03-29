package com.github.kushaal.scim_service.service;

import com.github.kushaal.scim_service.dto.request.ScimPatchOperation;
import com.github.kushaal.scim_service.exception.ScimInvalidValueException;
import com.github.kushaal.scim_service.filter.ScimFilterParser;
import com.github.kushaal.scim_service.model.entity.ScimUser;
import com.github.kushaal.scim_service.model.entity.ScimUserEmail;
import com.github.kushaal.scim_service.model.entity.ScimUserPhoneNumber;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Applies a list of SCIM PATCH operations (RFC 7644 §3.5.2 / RFC 6902) to a
 * {@link ScimUser} entity in place.
 *
 * <p>Two PATCH forms are supported:
 * <ul>
 *   <li><b>With path:</b> {@code { "op": "replace", "path": "active", "value": false }}</li>
 *   <li><b>Path-less:</b> {@code { "op": "replace", "value": { "active": false } }}</li>
 * </ul>
 *
 * <p>Multi-valued paths follow the SCIM attribute path syntax (RFC 7644 §3.10):
 * <pre>
 *   emails                           → whole collection
 *   emails[type eq "work"]           → filtered item(s)
 *   emails[type eq "work"].value     → specific field on filtered item(s)
 * </pre>
 */
@Component
public class PatchApplier {

    private static final Set<String> VALID_OPS = Set.of("add", "remove", "replace");

    // Parses: attrName  [filter]  .subAttr
    // Groups:    1         2          3
    // All groups after 1 are optional.
    private static final Pattern MULTI_VALUED_PATH =
            Pattern.compile("^(\\w+)(?:\\[([^\\]]+)\\])?(?:\\.(\\w+))?$", Pattern.CASE_INSENSITIVE);

    private record ParsedMultiValuedPath(String attrName, String filter, String subAttr) {}

    // ── Public entry point ────────────────────────────────────────────────────

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
        // Multi-valued paths (emails, phoneNumbers) are handled separately — the filter
        // and sub-attribute parsing logic is distinct from scalar path handling.
        if (isMultiValuedPath(path)) {
            applyMultiValued(user, op, path, value);
            return;
        }

        if ("remove".equals(op)) {
            applyRemove(user, path.toLowerCase(), path);
        } else {
            if (value == null) {
                throw new ScimInvalidValueException("'value' is required for op '" + op + "'");
            }
            applyAddOrReplace(user, path.toLowerCase(), path, value);
        }
    }

    private boolean isMultiValuedPath(String path) {
        String lower = path.toLowerCase();
        return lower.startsWith("emails") || lower.startsWith("phonenumbers");
    }

    // ── add / replace (scalars) ───────────────────────────────────────────────
    // For scalar attributes, add and replace are identical — both set the field.
    // The distinction only matters for multi-valued attributes: add appends
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
            case "name"            -> applyNameObject(user, value, originalPath);
            default -> throw new ScimInvalidValueException(
                    "Unsupported PATCH path: '" + originalPath + "'");
        }
    }

    private void applyNameObject(ScimUser user, JsonNode value, String originalPath) {
        if (!value.isObject()) {
            throw new ScimInvalidValueException("Expected object value for '" + originalPath + "'");
        }
        if (value.has("givenName"))  user.setGivenName(nodeToText(value.get("givenName")));
        if (value.has("familyName")) user.setFamilyName(nodeToText(value.get("familyName")));
        if (value.has("middleName")) user.setMiddleName(nodeToText(value.get("middleName")));
    }

    // ── remove (scalars) ──────────────────────────────────────────────────────
    // Nullifies the field. Required attributes (active, userName) cannot be removed —
    // they would leave the resource in an invalid state per RFC 7643 §4.1.

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

    // ── Multi-valued paths ────────────────────────────────────────────────────
    // Path formats handled:
    //   emails                           add → append, replace → clear+add, remove → clear all
    //   emails[type eq "work"]           remove → removeIf matching
    //   emails[type eq "work"].value     add/replace → update subAttr on matching item(s)

    private void applyMultiValued(ScimUser user, String op, String path, JsonNode value) {
        ParsedMultiValuedPath parsed = parseMultiValuedPath(path);
        switch (parsed.attrName()) {
            case "emails"        -> applyToEmails(user, op, parsed, value, path);
            case "phonenumbers"  -> applyToPhones(user, op, parsed, value, path);
            default -> throw new ScimInvalidValueException("Unsupported PATCH path: '" + path + "'");
        }
    }

    private ParsedMultiValuedPath parseMultiValuedPath(String path) {
        Matcher m = MULTI_VALUED_PATH.matcher(path.trim());
        if (!m.matches()) {
            throw new ScimInvalidValueException("Malformed multi-valued path: '" + path + "'");
        }
        return new ParsedMultiValuedPath(
                m.group(1).toLowerCase(),
                m.group(2),
                m.group(3) != null ? m.group(3).toLowerCase() : null
        );
    }

    // ── Email operations ──────────────────────────────────────────────────────

    private void applyToEmails(ScimUser user, String op, ParsedMultiValuedPath parsed,
                                JsonNode value, String originalPath) {
        if (parsed.filter() == null) {
            // No filter — operating on the whole collection
            if ("remove".equals(op)) {
                user.getEmails().clear();
            } else if ("add".equals(op)) {
                addEmailsFromNode(user, value, originalPath);
            } else {
                // replace: clear existing and add new
                user.getEmails().clear();
                addEmailsFromNode(user, value, originalPath);
            }
        } else {
            ScimFilterParser.ParsedFilter filter = ScimFilterParser.parse(parsed.filter());
            if ("remove".equals(op) && parsed.subAttr() == null) {
                // remove emails[type eq "work"]
                user.getEmails().removeIf(e -> matchesEmailFilter(e, filter));
            } else if (!"remove".equals(op) && parsed.subAttr() != null) {
                // replace emails[type eq "work"].value
                if (value == null) {
                    throw new ScimInvalidValueException("'value' is required for op '" + op + "'");
                }
                user.getEmails().stream()
                        .filter(e -> matchesEmailFilter(e, filter))
                        .forEach(e -> applyEmailSubAttr(e, parsed.subAttr(), value, originalPath));
            } else {
                throw new ScimInvalidValueException(
                        "Unsupported multi-valued operation for path: '" + originalPath + "'");
            }
        }
    }

    private boolean matchesEmailFilter(ScimUserEmail email, ScimFilterParser.ParsedFilter filter) {
        String filterValue = filter.value();
        return switch (filter.attribute()) {
            case "type"    -> filterValue.equalsIgnoreCase(email.getType());
            case "value"   -> filterValue.equalsIgnoreCase(email.getValue());
            case "primary" -> Boolean.parseBoolean(filterValue) == Boolean.TRUE.equals(email.getPrimary());
            default        -> false;
        };
    }

    private void applyEmailSubAttr(ScimUserEmail email, String subAttr, JsonNode value, String originalPath) {
        switch (subAttr) {
            case "value"   -> email.setValue(nodeToText(value));
            case "type"    -> email.setType(nodeToText(value));
            case "display" -> email.setDisplay(nodeToText(value));
            case "primary" -> email.setPrimary(nodeToBoolean(value, originalPath));
            default -> throw new ScimInvalidValueException(
                    "Unsupported email sub-attribute: '" + subAttr + "' in path: '" + originalPath + "'");
        }
    }

    private void addEmailsFromNode(ScimUser user, JsonNode value, String originalPath) {
        if (value == null) {
            throw new ScimInvalidValueException("'value' is required for emails add/replace");
        }
        if (value.isArray()) {
            for (JsonNode item : value) {
                user.getEmails().add(emailFromNode(item, user, originalPath));
            }
        } else if (value.isObject()) {
            user.getEmails().add(emailFromNode(value, user, originalPath));
        } else {
            throw new ScimInvalidValueException(
                    "Expected object or array for emails add/replace in path: '" + originalPath + "'");
        }
    }

    private ScimUserEmail emailFromNode(JsonNode node, ScimUser user, String originalPath) {
        if (!node.isObject()) {
            throw new ScimInvalidValueException(
                    "Each email entry must be an object in path: '" + originalPath + "'");
        }
        return ScimUserEmail.builder()
                .value(nodeToText(node.get("value")))
                .type(nodeToText(node.get("type")))
                .primary(node.has("primary") ? node.get("primary").booleanValue() : null)
                .display(nodeToText(node.get("display")))
                .user(user)
                .build();
    }

    // ── Phone number operations ───────────────────────────────────────────────

    private void applyToPhones(ScimUser user, String op, ParsedMultiValuedPath parsed,
                                JsonNode value, String originalPath) {
        if (parsed.filter() == null) {
            if ("remove".equals(op)) {
                user.getPhoneNumbers().clear();
            } else if ("add".equals(op)) {
                addPhonesFromNode(user, value, originalPath);
            } else {
                user.getPhoneNumbers().clear();
                addPhonesFromNode(user, value, originalPath);
            }
        } else {
            ScimFilterParser.ParsedFilter filter = ScimFilterParser.parse(parsed.filter());
            if ("remove".equals(op) && parsed.subAttr() == null) {
                user.getPhoneNumbers().removeIf(p -> matchesPhoneFilter(p, filter));
            } else if (!"remove".equals(op) && parsed.subAttr() != null) {
                if (value == null) {
                    throw new ScimInvalidValueException("'value' is required for op '" + op + "'");
                }
                user.getPhoneNumbers().stream()
                        .filter(p -> matchesPhoneFilter(p, filter))
                        .forEach(p -> applyPhoneSubAttr(p, parsed.subAttr(), value, originalPath));
            } else {
                throw new ScimInvalidValueException(
                        "Unsupported multi-valued operation for path: '" + originalPath + "'");
            }
        }
    }

    private boolean matchesPhoneFilter(ScimUserPhoneNumber phone, ScimFilterParser.ParsedFilter filter) {
        String filterValue = filter.value();
        return switch (filter.attribute()) {
            case "type"    -> filterValue.equalsIgnoreCase(phone.getType());
            case "value"   -> filterValue.equalsIgnoreCase(phone.getValue());
            case "primary" -> Boolean.parseBoolean(filterValue) == Boolean.TRUE.equals(phone.getPrimary());
            default        -> false;
        };
    }

    private void applyPhoneSubAttr(ScimUserPhoneNumber phone, String subAttr, JsonNode value, String originalPath) {
        switch (subAttr) {
            case "value"   -> phone.setValue(nodeToText(value));
            case "type"    -> phone.setType(nodeToText(value));
            case "primary" -> phone.setPrimary(nodeToBoolean(value, originalPath));
            default -> throw new ScimInvalidValueException(
                    "Unsupported phoneNumber sub-attribute: '" + subAttr + "' in path: '" + originalPath + "'");
        }
    }

    private void addPhonesFromNode(ScimUser user, JsonNode value, String originalPath) {
        if (value == null) {
            throw new ScimInvalidValueException("'value' is required for phoneNumbers add/replace");
        }
        if (value.isArray()) {
            for (JsonNode item : value) {
                user.getPhoneNumbers().add(phoneFromNode(item, user, originalPath));
            }
        } else if (value.isObject()) {
            user.getPhoneNumbers().add(phoneFromNode(value, user, originalPath));
        } else {
            throw new ScimInvalidValueException(
                    "Expected object or array for phoneNumbers add/replace in path: '" + originalPath + "'");
        }
    }

    private ScimUserPhoneNumber phoneFromNode(JsonNode node, ScimUser user, String originalPath) {
        if (!node.isObject()) {
            throw new ScimInvalidValueException(
                    "Each phoneNumber entry must be an object in path: '" + originalPath + "'");
        }
        return ScimUserPhoneNumber.builder()
                .value(nodeToText(node.get("value")))
                .type(nodeToText(node.get("type")))
                .primary(node.has("primary") ? node.get("primary").booleanValue() : null)
                .user(user)
                .build();
    }

    // ── Value extraction helpers ──────────────────────────────────────────────

    // active arrives as JSON boolean (false) or JSON string ("false") depending on the IdP.
    // Okta sends true JSON booleans; some clients stringify them. Handle both.
    private boolean nodeToBoolean(JsonNode node, String path) {
        if (node.isBoolean()) return node.booleanValue();
        if (node.isTextual()) return Boolean.parseBoolean(node.textValue());
        throw new ScimInvalidValueException(
                "Expected boolean value for '" + path + "', got: " + node.getNodeType());
    }

    private String nodeToText(JsonNode node) {
        if (node == null || node.isNull()) return null;
        return node.asText();
    }
}
