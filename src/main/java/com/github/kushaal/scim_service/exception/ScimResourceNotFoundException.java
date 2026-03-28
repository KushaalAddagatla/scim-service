package com.github.kushaal.scim_service.exception;

public class ScimResourceNotFoundException extends RuntimeException {
    public ScimResourceNotFoundException(String message) {
        super(message);
    }
}