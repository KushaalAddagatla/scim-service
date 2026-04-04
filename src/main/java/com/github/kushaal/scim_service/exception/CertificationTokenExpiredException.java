package com.github.kushaal.scim_service.exception;

/**
 * Thrown when a certification review token's JWT has passed its {@code exp} claim.
 *
 * <p>Maps to HTTP 410 Gone — the link was valid at some point but is no longer usable.
 * 410 is semantically stronger than 404 (which implies the resource never existed):
 * the reviewer navigated to a link that existed and has since expired.
 *
 * <p>The {@link ScimExceptionHandler} renders an HTML error page for this exception
 * rather than a SCIM JSON body, because the action endpoint is browser-facing.
 */
public class CertificationTokenExpiredException extends RuntimeException {

    public CertificationTokenExpiredException(String message) {
        super(message);
    }
}
