package com.github.kushaal.scim_service.service;

import com.github.kushaal.scim_service.dto.request.ScimPatchOperation;
import com.github.kushaal.scim_service.dto.request.ScimPatchRequest;
import com.github.kushaal.scim_service.model.entity.AuditLog;
import com.github.kushaal.scim_service.model.entity.Certification;
import com.github.kushaal.scim_service.model.entity.ScimUser;
import com.github.kushaal.scim_service.repository.AuditLogRepository;
import com.github.kushaal.scim_service.repository.CertificationRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.node.BooleanNode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Nightly fail-secure escalation sweep.
 *
 * <p>Any {@code PENDING} certification that has passed its {@code expires_at} without a
 * manager response is treated as an implicit revocation. Access is suspended immediately.
 * This is the fail-secure default: silence == revoke, not silence == approve.
 *
 * <p><strong>SOC2 CC6.3 mapping:</strong> "The entity implements logical access security
 * software, infrastructure, and architectures over protected information assets to protect
 * them from security events to meet the entity's objectives." Periodic access reviews with
 * automated escalation on non-response satisfy the "regularly review and certify user
 * access rights" control.
 *
 * <p>The entire sweep runs in one {@code @Transactional} — if anything fails mid-run the
 * whole batch rolls back, leaving no partial suspensions. For production at scale this
 * would be chunked, but single-transaction is appropriate for this portfolio scale.
 */
@Component
@RequiredArgsConstructor
public class EscalationScheduler {

    private static final Logger log = LoggerFactory.getLogger(EscalationScheduler.class);

    private final CertificationRepository certificationRepository;
    private final CertificationEmailService emailService;
    private final ScimUserService userService;
    private final AuditLogRepository auditLogRepository;

    /**
     * Runs nightly at 2am. Finds all certifications that are still {@code PENDING} but
     * whose {@code expires_at} has passed, then auto-suspends each affected user.
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void runEscalationSweep() {
        List<Certification> expired = certificationRepository.findExpiredPending(Instant.now());

        log.info("Escalation sweep started: {} expired PENDING certifications found", expired.size());

        int suspended = 0;

        for (Certification cert : expired) {
            ScimUser user = cert.getUser();

            // Mark EXPIRED and record the decision timestamp
            cert.setStatus(Certification.CertStatus.EXPIRED);
            cert.setDecidedAt(Instant.now());
            certificationRepository.save(cert);

            // Deactivate the user — same internal PATCH path used by the revoke action handler
            deactivateUser(user.getId());

            // Notify the manager so they are aware of the auto-suspension
            emailService.sendEscalationEmail(cert, user, user.getManager());

            // Write audit log — include expires_at for forensic auditability
            writeAuditLog(user.getId(), cert.getId(), cert.getExpiresAt());

            log.info("Auto-suspended user {} (cert={}, expiredAt={})",
                    user.getUserName(), cert.getId(), cert.getExpiresAt());
            suspended++;
        }

        log.info("Escalation sweep complete: {} users auto-suspended", suspended);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Builds and fires an internal PATCH to set {@code active = false} on the user.
     *
     * <p>Calling the service method directly keeps both writes in the same transaction
     * and avoids the need for a self-issued Bearer token on an internal HTTP call.
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

    /**
     * Writes a {@code CERTIFY_ESCALATE} audit log entry.
     *
     * <p>{@code expires_at} is recorded in the {@code scim_operation} JSONB column so
     * auditors can verify the exact deadline that was missed — important for SOC2 evidence.
     */
    private void writeAuditLog(UUID targetUserId, UUID certId, Instant expiresAt) {
        String operation = String.format(
                "{\"cert_id\":\"%s\",\"expires_at\":\"%s\",\"auto_suspended\":true}",
                certId, expiresAt);

        auditLogRepository.save(AuditLog.builder()
                .eventType("CERTIFY_ESCALATE")
                .actor("escalation-scheduler")
                .targetUserId(targetUserId)
                .resourceId(certId.toString())
                .scimOperation(operation)
                .outcome("SUCCESS")
                .build());
    }
}
