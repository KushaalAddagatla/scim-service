package com.github.kushaal.scim_service.controller;

import com.github.kushaal.scim_service.service.CertificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Handles manager clicks on certification review email links.
 *
 * <p>Intentionally mapped to {@code /certifications/action} — outside the
 * {@code /scim/v2/**} namespace so {@link com.github.kushaal.scim_service.config.SecurityConfig}
 * can {@code permitAll} it without a Bearer token. The JWT embedded in the
 * {@code token} query param is the only authentication mechanism here.
 *
 * <p>Returns {@code text/html} — this is a browser link, not a machine API.
 * Error cases (expired token, already used) are also rendered as HTML by
 * {@link com.github.kushaal.scim_service.exception.ScimExceptionHandler}.
 */
@RestController
@RequiredArgsConstructor
public class CertificationController {

    private final CertificationService certificationService;

    /**
     * Approve or revoke access based on the signed token in the email link.
     *
     * <p>Example links emailed to the manager:
     * <pre>
     * GET /certifications/action?token=eyJ...&decision=approve
     * GET /certifications/action?token=eyJ...&decision=revoke
     * </pre>
     *
     * @param token    the raw HS256 JWT from the email link
     * @param decision "approve" or "revoke"
     * @return 200 HTML confirmation page
     */
    @GetMapping(value = "/certifications/action", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> handleAction(
            @RequestParam String token,
            @RequestParam String decision) {

        String displayName = certificationService.processAction(token, decision);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(buildConfirmationHtml(displayName, decision));
    }

    // ── HTML builder ──────────────────────────────────────────────────────────

    private static String buildConfirmationHtml(String displayName, String decision) {
        boolean approved = "approve".equalsIgnoreCase(decision);
        String color  = approved ? "#16a34a" : "#dc2626";
        String symbol = approved ? "&#10003;" : "&#10007;";
        String action = approved ? "Approved"  : "Revoked";
        String detail = approved
                ? "Access for <strong>" + escapeHtml(displayName) + "</strong> has been approved. No further action is required."
                : "Access for <strong>" + escapeHtml(displayName) + "</strong> has been revoked. The account has been deactivated.";

        return "<!DOCTYPE html>"
             + "<html lang=\"en\"><head><meta charset=\"UTF-8\">"
             + "<title>Access " + action + "</title></head>"
             + "<body style=\"margin:0;padding:0;background-color:#f4f4f4;font-family:Arial,sans-serif;\">"
             + "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\""
             + "  style=\"background-color:#f4f4f4;padding:60px 0;\">"
             + "<tr><td align=\"center\">"
             + "<table width=\"520\" cellpadding=\"0\" cellspacing=\"0\""
             + "  style=\"background-color:#ffffff;border-radius:8px;"
             + "         box-shadow:0 2px 8px rgba(0,0,0,0.1);padding:48px;text-align:center;\">"
             + "<tr><td>"
             + "<div style=\"width:64px;height:64px;border-radius:50%;background-color:" + color + ";"
             + "     display:inline-flex;align-items:center;justify-content:center;"
             + "     font-size:32px;color:#ffffff;margin-bottom:24px;\">"
             + symbol + "</div>"
             + "<h1 style=\"margin:0 0 12px;color:#111827;font-size:22px;font-weight:600;\">"
             + "Access " + action + "</h1>"
             + "<p style=\"margin:0 0 32px;color:#6b7280;font-size:14px;line-height:1.6;\">"
             + detail + "</p>"
             + "<p style=\"margin:0;font-size:12px;color:#9ca3af;\">"
             + "You may close this window. This decision has been recorded in the audit log.</p>"
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
