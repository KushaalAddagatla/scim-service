package com.github.kushaal.scim_service.exception;

public class ScimPreconditionFailedException extends RuntimeException {
    public ScimPreconditionFailedException(String message) {
        super(message);
    }
}
