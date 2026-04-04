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

// Certification-specific HTML responses — these are browser-facing errors from the
// /certifications/action endpoint, not machine-to-machine SCIM errors.

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

    /**
     * 410 Gone — the review JWT has passed its expiry date.
     *
     * <p>410 is preferred over 404 because the resource did exist (the link was valid
     * when emailed) but has since expired. An automated fail-secure sweep will have
     * already suspended the access. Returns HTML because this hits a browser.
     */
    @ExceptionHandler(CertificationTokenExpiredException.class)
    public ResponseEntity<String> handleTokenExpired(CertificationTokenExpiredException ex) {
        return ResponseEntity
                .status(HttpStatus.GONE)
                .contentType(MediaType.TEXT_HTML)
                .body(buildErrorHtml(
                        "Review Link Expired",
                        "This review link has expired.",
                        "Expired access is automatically suspended per fail-secure policy. "
                      + "Contact your IT administrator if you have questions."));
    }

    /**
     * 409 Conflict — the token was already clicked, or the certification was already
     * decided by another path (e.g. auto-suspend escalation ran first).
     *
     * <p>Returns HTML because this hits a browser.
     */
    @ExceptionHandler(CertificationTokenAlreadyUsedException.class)
    public ResponseEntity<String> handleTokenAlreadyUsed(CertificationTokenAlreadyUsedException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .contentType(MediaType.TEXT_HTML)
                .body(buildErrorHtml(
                        "Already Reviewed",
                        "This review has already been completed.",
                        ex.getMessage()));
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

    // ── HTML error page builder ───────────────────────────────────────────────

    private static String buildErrorHtml(String title, String heading, String detail) {
        return "<!DOCTYPE html>"
             + "<html lang=\"en\"><head><meta charset=\"UTF-8\">"
             + "<title>" + title + "</title></head>"
             + "<body style=\"margin:0;padding:0;background-color:#f4f4f4;font-family:Arial,sans-serif;\">"
             + "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\""
             + "  style=\"background-color:#f4f4f4;padding:60px 0;\">"
             + "<tr><td align=\"center\">"
             + "<table width=\"520\" cellpadding=\"0\" cellspacing=\"0\""
             + "  style=\"background-color:#ffffff;border-radius:8px;"
             + "         box-shadow:0 2px 8px rgba(0,0,0,0.1);padding:48px;text-align:center;\">"
             + "<tr><td>"
             + "<div style=\"width:64px;height:64px;border-radius:50%;background-color:#dc2626;"
             + "     display:inline-flex;align-items:center;justify-content:center;"
             + "     font-size:32px;color:#ffffff;margin-bottom:24px;\">&#33;</div>"
             + "<h1 style=\"margin:0 0 12px;color:#111827;font-size:22px;font-weight:600;\">"
             + escapeHtml(heading) + "</h1>"
             + "<p style=\"margin:0 0 32px;color:#6b7280;font-size:14px;line-height:1.6;\">"
             + escapeHtml(detail) + "</p>"
             + "<p style=\"margin:0;font-size:12px;color:#9ca3af;\">You may close this window.</p>"
             + "</td></tr></table>"
             + "</td></tr></table>"
             + "</body></html>";
    }

    private static String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;");
    }
}