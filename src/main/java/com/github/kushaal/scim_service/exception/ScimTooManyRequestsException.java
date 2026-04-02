package com.github.kushaal.scim_service.exception;

public class ScimTooManyRequestsException extends RuntimeException {

    public ScimTooManyRequestsException(String message) {
        super(message);
    }
}
