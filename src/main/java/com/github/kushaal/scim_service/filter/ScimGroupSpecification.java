package com.github.kushaal.scim_service.filter;

import com.github.kushaal.scim_service.exception.ScimInvalidValueException;
import com.github.kushaal.scim_service.model.entity.ScimGroup;
import org.springframework.data.jpa.domain.Specification;

/**
 * Converts a {@link ScimFilterParser.ParsedFilter} into a JPA {@link Specification}
 * for dynamic WHERE clause generation against {@link ScimGroup}.
 *
 * <p>Groups only need displayName and externalId filtering — Okta uses
 * {@code displayName eq "..."} before pushing a group to check if it already exists.
 */
public class ScimGroupSpecification {

    public static Specification<ScimGroup> fromFilter(ScimFilterParser.ParsedFilter filter) {
        return switch (filter.attribute()) {
            case "displayname" -> (root, query, cb) ->
                    cb.equal(cb.lower(root.get("displayName")), filter.value().toLowerCase());

            case "externalid" -> (root, query, cb) ->
                    cb.equal(root.get("externalId"), filter.value());

            default -> throw new ScimInvalidValueException(
                    "Unsupported filter attribute: '" + filter.attribute() +
                    "'. Supported attributes for Groups: displayName, externalId");
        };
    }
}
