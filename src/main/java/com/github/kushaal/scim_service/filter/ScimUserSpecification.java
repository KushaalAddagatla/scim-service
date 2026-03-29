package com.github.kushaal.scim_service.filter;

import com.github.kushaal.scim_service.exception.ScimInvalidValueException;
import com.github.kushaal.scim_service.model.entity.ScimUser;
import com.github.kushaal.scim_service.model.entity.ScimUserEmail;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

/**
 * Converts a {@link ScimFilterParser.ParsedFilter} into a JPA {@link Specification}
 * for dynamic WHERE clause generation against {@link ScimUser}.
 *
 * <p>Why JPA Specifications instead of a custom @Query?
 * Specifications are composable — two Specifications can be AND-ed or OR-ed together
 * with {@code Specification.and()} / {@code Specification.or()}, which matters when
 * we add compound filter support (e.g. {@code userName eq "x" and active eq "true"}).
 * A hardcoded @Query can't compose. The repository only needs to extend
 * {@code JpaSpecificationExecutor<ScimUser>}, which it already does.
 *
 * <p>Supported filter attributes:
 * <ul>
 *   <li>{@code userName}    — case-insensitive equality on scim_users.user_name</li>
 *   <li>{@code externalId}  — exact match on scim_users.external_id</li>
 *   <li>{@code active}      — boolean match on scim_users.active</li>
 *   <li>{@code emails.value}— JOIN on scim_user_emails.value (dot-notation per RFC 7644)</li>
 * </ul>
 */
public class ScimUserSpecification {

    public static Specification<ScimUser> fromFilter(ScimFilterParser.ParsedFilter filter) {
        return switch (filter.attribute()) {
            // Case-insensitive: Okta sends the userName it knows, but the stored value may
            // differ in case. Lowercasing both sides avoids a mismatch without altering data.
            case "username" -> (root, query, cb) ->
                    cb.equal(cb.lower(root.get("userName")), filter.value().toLowerCase());

            case "externalid" -> (root, query, cb) ->
                    cb.equal(root.get("externalId"), filter.value());

            // Boolean parse: filter value arrives as the string "true" or "false".
            // Boolean.parseBoolean("true") → true, anything else → false (safe default).
            case "active" -> (root, query, cb) ->
                    cb.equal(root.get("active"), Boolean.parseBoolean(filter.value()));

            // emails.value requires a JOIN to the emails child table.
            // query.distinct(true) prevents duplicate ScimUser rows when a user has
            // multiple emails — without it, a user with 3 emails matching the filter
            // would appear 3 times in the result set.
            case "emails.value" -> (root, query, cb) -> {
                query.distinct(true);
                Join<ScimUser, ScimUserEmail> emailJoin = root.join("emails", JoinType.LEFT);
                return cb.equal(emailJoin.get("value"), filter.value());
            };

            default -> throw new ScimInvalidValueException(
                    "Unsupported filter attribute: '" + filter.attribute() + "'. " +
                    "Supported attributes: userName, externalId, active, emails.value");
        };
    }
}
