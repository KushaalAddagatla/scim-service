package com.github.kushaal.scim_service.exception;

import com.github.kushaal.scim_service.dto.response.ScimError;
import com.github.kushaal.scim_service.model.ScimConstants;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class ScimExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ScimError> handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.parseMediaType(ScimConstants.SCIM_CONTENT_TYPE))
                .body(ScimError.builder()
                        .status(400)
                        .scimType("invalidValue")
                        .detail(detail)
                        .build());
    }

    @ExceptionHandler(ScimInvalidValueException.class)
    public ResponseEntity<ScimError> handleInvalidValue(ScimInvalidValueException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.parseMediaType(ScimConstants.SCIM_CONTENT_TYPE))
                .body(ScimError.builder()
                        .status(400)
                        .scimType("invalidValue")
                        .detail(ex.getMessage())
                        .build());
    }

    @ExceptionHandler(ScimPreconditionFailedException.class)
    public ResponseEntity<ScimError> handlePreconditionFailed(ScimPreconditionFailedException ex) {
        return ResponseEntity
                .status(HttpStatus.PRECONDITION_FAILED)
                .contentType(MediaType.parseMediaType(ScimConstants.SCIM_CONTENT_TYPE))
                .body(ScimError.builder()
                        .status(412)
                        .detail(ex.getMessage())
                        .build());
    }

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

    @ExceptionHandler(ScimTooManyRequestsException.class)
    public ResponseEntity<ScimError> handleTooManyRequests(ScimTooManyRequestsException ex) {
        return ResponseEntity
                .status(429)
                .contentType(MediaType.parseMediaType(ScimConstants.SCIM_CONTENT_TYPE))
                .body(ScimError.builder()
                        .status(429)
                        .scimType("tooMany")
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