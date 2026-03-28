package com.github.kushaal.scim_service.exception;

public class ScimConflictException extends RuntimeException {
    public ScimConflictException(String message) {
        super(message);
    }
}