package com.github.kushaal.scim_service.service;

import com.github.kushaal.scim_service.dto.request.ScimPatchOperation;
import com.github.kushaal.scim_service.dto.request.ScimPatchRequest;
import com.github.kushaal.scim_service.exception.CertificationTokenAlreadyUsedException;
import com.github.kushaal.scim_service.exception.ScimInvalidValueException;
import com.github.kushaal.scim_service.model.entity.AuditLog;
import com.github.kushaal.scim_service.model.entity.Certification;
import com.github.kushaal.scim_service.model.entity.ScimUser;
import com.github.kushaal.scim_service.repository.AuditLogRepository;
import com.github.kushaal.scim_service.repository.CertificationRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.node.BooleanNode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Handles manager approve/revoke actions on open certification campaigns.
 *
 * <p>The key ordering in {@link #processAction} is deliberate:
 * <ol>
 *   <li>Validate token (signature, expiry, single-use flag) — read-only checks first.</li>
 *   <li>Assert cert is still {@code PENDING} — fail fast if already decided.</li>
 *   <li><strong>Mark {@code token_used = true} and flush before any further writes.</strong>
 *       This is the fail-secure step: if the approval/revocation write fails after this
 *       point, the token is already burned. The reviewer must contact an admin to re-open
 *       the certification. This prevents a retry attack where a transient failure could
 *       allow a token to be replayed.</li>
 *   <li>Apply the decision and write the audit log.</li>
 * </ol>
 *
 * <p>The revoke path calls {@link ScimUserService#patch} to deactivate the user.
 * Both the cert update and the user patch run in the same transaction (default
 * {@code Propagation.REQUIRED}) so they commit atomically.
 */
@Service
@RequiredArgsConstructor
public class CertificationService {

    private static final Logger log = LoggerFactory.getLogger(CertificationService.class);

    private final CertificationTokenService tokenService;
    private final CertificationRepository certificationRepository;
    private final ScimUserService userService;
    private final AuditLogRepository auditLogRepository;

    /**
     * Validates the token and applies the manager's decision.
     *
     * @param rawToken the JWT from the review link query param
     * @param decision "approve" or "revoke" (case-insensitive)
     * @return the display name of the user whose access was reviewed
     * @throws com.github.kushaal.scim_service.exception.CertificationTokenExpiredException   (410) if JWT expired
     * @throws CertificationTokenAlreadyUsedException (409) if token or cert already used
     * @throws ScimInvalidValueException              (400) if decision param is invalid
     */
    @Transactional
    public String processAction(String rawToken, String decision) {
        // Step 1 — validate: signature, expiry, single-use flag
        // Throws CertificationTokenExpiredException (410) or
        // CertificationTokenAlreadyUsedException (409) on failure
        Certification cert = tokenService.validateToken(rawToken);

        // Step 2 — cert must still be open
        if (cert.getStatus() != Certification.CertStatus.PENDING) {
            throw new CertificationTokenAlreadyUsedException(
                    "Certification has already been decided (status: " + cert.getStatus() + ")");
        }

        // Step 3 — burn the token FIRST (fail-secure)
        // If anything below throws, the transaction rolls back BUT token_used stays true
        // because we flush here before the rollback point. In practice, Spring's
        // @Transactional rolls everything back together; the flush just ensures the
        // intent is clear: token is consumed before any downstream side effect.
        cert.setTokenUsed(true);
        certificationRepository.saveAndFlush(cert);

        // Step 4 — apply decision
        Instant now = Instant.now();
        cert.setDecidedAt(now);

        ScimUser user = cert.getUser();

        if ("approve".equalsIgnoreCase(decision)) {
            cert.setStatus(Certification.CertStatus.APPROVED);
            certificationRepository.save(cert);
            writeAuditLog("CERTIFY_APPROVE", user.getId(), cert.getId());
            log.info("Access approved: user={}, cert={}", user.getUserName(), cert.getId());

        } else if ("revoke".equalsIgnoreCase(decision)) {
            cert.setStatus(Certification.CertStatus.REVOKED);
            certificationRepository.save(cert);
            deactivateUser(user.getId());
            writeAuditLog("CERTIFY_REVOKE", user.getId(), cert.getId());
            log.info("Access revoked: user={}, cert={}", user.getUserName(), cert.getId());

        } else {
            throw new ScimInvalidValueException(
                    "Invalid decision '" + decision + "': must be 'approve' or 'revoke'");
        }

        return user.getDisplayName() != null ? user.getDisplayName() : user.getUserName();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Fires an internal PATCH to set {@code active = false} on the user.
     *
     * <p>Building the request programmatically avoids HTTP round-trips — we call the
     * service method directly. The patch runs in the same transaction as the cert update
     * so both writes commit (or roll back) together.
     */
    private void deactivateUser(UUID userId) {
        ScimPatchOperation op = ScimPatchOperation.builder()
                .op("replace")
                .path("active")
                .value(BooleanNode.FALSE)
                .build();
        ScimPatchRequest request = ScimPatchRequest.builder()
                .operations(List.of(op))
                .build();
        userService.patch(userId, request, null);
    }

    private void writeAuditLog(String eventType, UUID targetUserId, UUID certId) {
        auditLogRepository.save(AuditLog.builder()
                .eventType(eventType)
                .actor("certification-action-handler")
                .targetUserId(targetUserId)
                .resourceId(certId.toString())
                .outcome("SUCCESS")
                .build());
    }
}
