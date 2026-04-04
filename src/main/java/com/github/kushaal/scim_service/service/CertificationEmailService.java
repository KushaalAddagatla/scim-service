package com.github.kushaal.scim_service.service;

import com.github.kushaal.scim_service.model.entity.Certification;
import com.github.kushaal.scim_service.model.entity.ScimUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Sends certification review emails to managers via AWS SES.
 *
 * <p>The email contains tokenized approve/revoke links. The raw JWT is embedded
 * directly in the URL query param — never stored in the DB. Clicking either link
 * hits {@code GET /certifications/action?token=<jwt>&decision=<approve|revoke>}.
 *
 * <p>{@code sesClient} is {@code null} when {@code SesConfig} is not loaded
 * (i.e., {@code scim.ses.from-address} is absent — tests, CI, plain local run
 * without LocalStack). In that case the service falls back to stub logging so
 * the scheduler continues to operate without email support.
 *
 * <p><strong>Why store the hash, not the raw token?</strong> A DB breach would
 * expose {@code token_hash} values, but an attacker cannot reverse SHA-256 to
 * reconstruct the raw JWT needed to call the action endpoint. This satisfies the
 * "stored credential ≠ usable credential" principle (analogous to password hashing).
 */
@Service
public class CertificationEmailService {

    private static final Logger log = LoggerFactory.getLogger(CertificationEmailService.class);

    private static final DateTimeFormatter EXPIRES_FMT =
            DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    // Null when scim.ses.from-address is absent — graceful stub fallback below
    @Autowired(required = false)
    private SesClient sesClient;

    @Value("${scim.ses.from-address:}")
    private String fromAddress;

    // Base URL for the approve/revoke links embedded in the email
    @Value("${scim.base-url:http://localhost:8080}")
    private String baseUrl;

    /**
     * Builds and sends an HTML review email to the manager.
     *
     * @param cert      the open certification record (provides cert ID, resource, expiry)
     * @param user      the user whose access is under review
     * @param reviewer  the manager who should respond; if null the email is skipped
     * @param rawToken  the raw JWT for the action links — never persisted, only lives in the email
     */
    public void sendReviewEmail(Certification cert, ScimUser user, ScimUser reviewer, String rawToken) {
        if (reviewer == null) {
            log.warn("No manager assigned for user {} — skipping review email for cert {}",
                    user.getUserName(), cert.getId());
            return;
        }

        String reviewerEmail = reviewer.getUserName();
        String approveUrl = baseUrl + "/certifications/action?token=" + rawToken + "&decision=approve";
        String revokeUrl  = baseUrl + "/certifications/action?token=" + rawToken + "&decision=revoke";

        String subject  = "Access Review Required: " + displayName(user) + " — " + cert.getResourceId();
        String htmlBody = buildHtml(cert, user, approveUrl, revokeUrl);
        String textBody = buildText(cert, user, approveUrl, revokeUrl);

        if (sesClient == null || fromAddress.isBlank()) {
            log.info("[STUB] Would send review email to {} for cert {} (SES not configured)",
                    reviewerEmail, cert.getId());
            log.info("[STUB] Approve: {}", approveUrl);
            log.info("[STUB]  Revoke: {}", revokeUrl);
            return;
        }

        sesClient.sendEmail(SendEmailRequest.builder()
                .source(fromAddress)
                .destination(Destination.builder()
                        .toAddresses(reviewerEmail)
                        .build())
                .message(Message.builder()
                        .subject(Content.builder()
                                .data(subject)
                                .charset("UTF-8")
                                .build())
                        .body(Body.builder()
                                .html(Content.builder()
                                        .data(htmlBody)
                                        .charset("UTF-8")
                                        .build())
                                .text(Content.builder()
                                        .data(textBody)
                                        .charset("UTF-8")
                                        .build())
                                .build())
                        .build())
                .build());

        log.info("Review email sent to {} for cert {}", reviewerEmail, cert.getId());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String displayName(ScimUser user) {
        return user.getDisplayName() != null ? user.getDisplayName() : user.getUserName();
    }

    /**
     * Builds the HTML email body.
     *
     * <p>Uses table-based layout for broad email-client compatibility (Gmail, Outlook,
     * Apple Mail). Inline styles only — external stylesheets are stripped by most clients.
     * Two CTA buttons (Approve / Revoke) are the primary call-to-action; all critical
     * info is also in the plain-text fallback built by {@link #buildText}.
     */
    private String buildHtml(Certification cert, ScimUser user, String approveUrl, String revokeUrl) {
        String expiresAt = cert.getExpiresAt() != null ? EXPIRES_FMT.format(cert.getExpiresAt()) : "N/A";
        return "<!DOCTYPE html>"
             + "<html lang=\"en\"><head><meta charset=\"UTF-8\">"
             + "<title>Access Review Required</title></head>"
             + "<body style=\"margin:0;padding:0;background-color:#f4f4f4;font-family:Arial,sans-serif;\">"
             + "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\""
             + "  style=\"background-color:#f4f4f4;padding:20px 0;\">"
             + "<tr><td align=\"center\">"
             + "<table width=\"600\" cellpadding=\"0\" cellspacing=\"0\""
             + "  style=\"background-color:#ffffff;border-radius:8px;"
             + "         box-shadow:0 2px 8px rgba(0,0,0,0.1);overflow:hidden;\">"
             // ── Header ──
             + "<tr><td style=\"background-color:#1a56db;padding:24px 32px;\">"
             + "<h1 style=\"margin:0;color:#ffffff;font-size:20px;font-weight:600;\">"
             + "Access Review Required</h1></td></tr>"
             // ── Body ──
             + "<tr><td style=\"padding:32px;\">"
             + "<p style=\"margin:0 0 16px;color:#374151;font-size:14px;line-height:1.6;\">"
             + "A periodic access review has been triggered for the following user. "
             + "Please review the access and take action before the expiry date. "
             + "<strong>If no action is taken, access will be automatically suspended.</strong></p>"
             // ── Details table ──
             + "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\""
             + "  style=\"border:1px solid #e5e7eb;border-radius:6px;margin-bottom:24px;\">"
             + "<tr style=\"background-color:#f9fafb;\">"
             + "  <td style=\"padding:10px 16px;font-size:13px;color:#6b7280;"
             + "      border-bottom:1px solid #e5e7eb;width:40%;\"><strong>User</strong></td>"
             + "  <td style=\"padding:10px 16px;font-size:13px;color:#111827;"
             + "      border-bottom:1px solid #e5e7eb;\">" + escapeHtml(displayName(user)) + "</td></tr>"
             + "<tr>"
             + "  <td style=\"padding:10px 16px;font-size:13px;color:#6b7280;"
             + "      border-bottom:1px solid #e5e7eb;\"><strong>Username</strong></td>"
             + "  <td style=\"padding:10px 16px;font-size:13px;color:#111827;"
             + "      border-bottom:1px solid #e5e7eb;\">" + escapeHtml(user.getUserName()) + "</td></tr>"
             + "<tr style=\"background-color:#f9fafb;\">"
             + "  <td style=\"padding:10px 16px;font-size:13px;color:#6b7280;"
             + "      border-bottom:1px solid #e5e7eb;\"><strong>Resource</strong></td>"
             + "  <td style=\"padding:10px 16px;font-size:13px;color:#111827;"
             + "      border-bottom:1px solid #e5e7eb;\">" + escapeHtml(cert.getResourceId()) + "</td></tr>"
             + "<tr>"
             + "  <td style=\"padding:10px 16px;font-size:13px;color:#6b7280;\"><strong>Review Expires</strong></td>"
             + "  <td style=\"padding:10px 16px;font-size:13px;color:#dc2626;\">" + expiresAt + "</td></tr>"
             + "</table>"
             // ── CTA buttons ──
             + "<table cellpadding=\"0\" cellspacing=\"0\"><tr>"
             + "  <td style=\"padding-right:12px;\">"
             + "    <a href=\"" + approveUrl + "\""
             + "      style=\"display:inline-block;background-color:#16a34a;color:#ffffff;"
             + "             padding:12px 24px;border-radius:6px;text-decoration:none;"
             + "             font-size:14px;font-weight:600;\">"
             + "      &#10003; Approve Access</a></td>"
             + "  <td>"
             + "    <a href=\"" + revokeUrl + "\""
             + "      style=\"display:inline-block;background-color:#dc2626;color:#ffffff;"
             + "             padding:12px 24px;border-radius:6px;text-decoration:none;"
             + "             font-size:14px;font-weight:600;\">"
             + "      &#10007; Revoke Access</a></td>"
             + "</tr></table>"
             + "<p style=\"margin:24px 0 0;font-size:12px;color:#9ca3af;line-height:1.5;\">"
             + "These links are <strong>single-use</strong> and expire on " + expiresAt + ". "
             + "If you believe you received this email in error, please contact your IT administrator. "
             + "This review supports SOC2 CC6.3 periodic access certification compliance.</p>"
             + "</td></tr>"
             // ── Footer ──
             + "<tr><td style=\"background-color:#f9fafb;padding:16px 32px;"
             + "               border-top:1px solid #e5e7eb;\">"
             + "<p style=\"margin:0;font-size:12px;color:#9ca3af;\">"
             + "SCIM Identity Provisioning Service — Automated Access Certification</p>"
             + "</td></tr>"
             + "</table></td></tr></table>"
             + "</body></html>";
    }

    /** Plain-text fallback — displayed by email clients that block HTML. */
    private String buildText(Certification cert, ScimUser user, String approveUrl, String revokeUrl) {
        String expiresAt = cert.getExpiresAt() != null ? EXPIRES_FMT.format(cert.getExpiresAt()) : "N/A";
        return "ACCESS REVIEW REQUIRED\n\n"
             + "A periodic access review has been triggered. "
             + "Please take action before the expiry date. "
             + "If no action is taken, access will be automatically suspended.\n\n"
             + "User:            " + displayName(user) + " (" + user.getUserName() + ")\n"
             + "Resource:        " + cert.getResourceId() + "\n"
             + "Review Expires:  " + expiresAt + "\n\n"
             + "APPROVE ACCESS:\n" + approveUrl + "\n\n"
             + "REVOKE ACCESS:\n"  + revokeUrl  + "\n\n"
             + "These links are single-use and expire on " + expiresAt + ".\n"
             + "This review supports SOC2 CC6.3 periodic access certification compliance.";
    }

    /** Minimal HTML entity escaping for user-supplied strings inserted into the email body. */
    private static String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;");
    }
}
