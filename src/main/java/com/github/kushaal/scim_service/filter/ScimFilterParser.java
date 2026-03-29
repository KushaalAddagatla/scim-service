package com.github.kushaal.scim_service.filter;

import com.github.kushaal.scim_service.exception.ScimInvalidValueException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses SCIM filter expressions (RFC 7644 §3.4.2.2) into a structured form.
 *
 * <p>SCIM filter grammar (simplified):
 * <pre>
 *   ATTRNAME SP compareOp SP compValue
 *   compValue = quoted-string / "true" / "false"
 * </pre>
 *
 * <p>Only {@code eq} is supported — sufficient for Okta SCIM compatibility,
 * which uses {@code userName eq "..."} before every provisioning event to check
 * whether the user already exists.
 *
 * <p>Attribute names are normalised to lowercase so the switch in
 * {@link ScimUserSpecification} can use simple string matching regardless of
 * what case the client sent.
 */
public class ScimFilterParser {

    // Accepts both quoted and unquoted values:
    //   userName eq "john@example.com"   ← standard
    //   active eq true                   ← boolean without quotes (Okta sends this)
    private static final Pattern FILTER_PATTERN =
            Pattern.compile("^(\\S+)\\s+(eq)\\s+\"?([^\"]+?)\"?\\s*$", Pattern.CASE_INSENSITIVE);

    public record ParsedFilter(String attribute, String operator, String value) {}

    public static ParsedFilter parse(String filter) {
        Matcher m = FILTER_PATTERN.matcher(filter.trim());
        if (!m.matches()) {
            throw new ScimInvalidValueException("Unsupported or malformed filter expression: " + filter);
        }
        return new ParsedFilter(
                m.group(1).toLowerCase(),
                m.group(2).toLowerCase(),
                m.group(3)
        );
    }
}
