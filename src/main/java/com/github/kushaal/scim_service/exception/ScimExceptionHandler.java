package com.github.kushaal.scim_service.exception;

import com.github.kushaal.scim_service.dto.response.ScimError;
import com.github.kushaal.scim_service.model.ScimConstants;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ScimExceptionHandler {

    @ExceptionHandler(ScimResourceNotFoundException.class)
    public ResponseEntity<ScimError> handleNotFound(ScimResourceNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .contentType(org.springframework.http.MediaType
                        .parseMediaType(ScimConstants.SCIM_CONTENT_TYPE))
                .body(ScimError.builder()
                        .status(404)
                        .detail(ex.getMessage())
                        .build());
    }

    @ExceptionHandler(ScimConflictException.class)
    public ResponseEntity<ScimError> handleConflict(ScimConflictException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .contentType(org.springframework.http.MediaType
                        .parseMediaType(ScimConstants.SCIM_CONTENT_TYPE))
                .body(ScimError.builder()
                        .status(409)
                        .scimType("uniqueness")
                        .detail(ex.getMessage())
                        .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ScimError> handleGeneric(Exception ex) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(org.springframework.http.MediaType
                        .parseMediaType(ScimConstants.SCIM_CONTENT_TYPE))
                .body(ScimError.builder()
                        .status(500)
                        .detail("An unexpected error occurred")
                        .build());
    }
}