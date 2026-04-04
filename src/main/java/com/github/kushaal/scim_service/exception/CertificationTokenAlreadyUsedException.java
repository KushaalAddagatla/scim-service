package com.github.kushaal.scim_service.exception;

/**
 * Thrown when a certification review token has already been consumed (single-use
 * enforcement) or when the underlying certification is no longer in {@code PENDING}
 * status (already approved or revoked).
 *
 * <p>Maps to HTTP 409 Conflict — the request cannot be completed because the resource
 * state has already moved on.
 *
 * <p>The {@link ScimExceptionHandler} renders an HTML error page for this exception
 * rather than a SCIM JSON body, because the action endpoint is browser-facing.
 */
public class CertificationTokenAlreadyUsedException extends RuntimeException {

    public CertificationTokenAlreadyUsedException(String message) {
        super(message);
    }
}
