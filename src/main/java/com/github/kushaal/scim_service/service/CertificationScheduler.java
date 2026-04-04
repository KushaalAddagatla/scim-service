package com.github.kushaal.scim_service.service;

import com.github.kushaal.scim_service.model.entity.AccessHistory;
import com.github.kushaal.scim_service.model.entity.AuditLog;
import com.github.kushaal.scim_service.model.entity.Certification;
import com.github.kushaal.scim_service.repository.AccessHistoryRepository;
import com.github.kushaal.scim_service.repository.AuditLogRepository;
import com.github.kushaal.scim_service.repository.CertificationRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Weekly certification campaign scheduler.
 *
 * <p>Runs every Monday at 1am. For each {@code access_history} row where
 * {@code last_accessed_at} is older than {@code scim.certification.stale-days}
 * (default 90 days), it opens a {@code PENDING} certification and emails the
 * user's manager a tokenized approve/revoke link.
 *
 * <p>The entire run is wrapped in one {@code @Transactional} — if anything fails
 * mid-run the whole batch rolls back, leaving no half-written certifications or
 * audit rows. For a production system with millions of rows you'd chunk this, but
 * one transaction is appropriate here.
 *
 * <p>Idempotent: if a {@code PENDING} certification already exists for a
 * {@code (user_id, resource_id)} pair (e.g. from a previous run that crashed before
 * the email was sent), the row is skipped rather than duplicated.
 *
 * <p>A run-level correlation ID is generated at the start of each run and placed in
 * MDC so every log line and audit log entry for that run shares the same trace ID.
 */
@Component
@RequiredArgsConstructor
public class CertificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(CertificationScheduler.class);

    private final AccessHistoryRepository accessHistoryRepository;
    private final CertificationRepository certificationRepository;
    private final CertificationTokenService tokenService;
    private final CertificationEmailService emailService;
    private final AuditLogRepository auditLogRepository;
    private final CloudWatchMetricsService metricsService;

    @Value("${scim.certification.stale-days:90}")
    private int staleDays;

    @Scheduled(cron = "0 0 1 * * MON")
    @Transactional
    public void runCertificationCampaign() {
        // Run-level correlation ID — ties every log line and audit entry for this
        // batch together, even though there's no HTTP request driving it.
        MDC.put("correlationId", UUID.randomUUID().toString());
        try {
            runInternal();
        } finally {
            MDC.remove("correlationId");
        }
    }

    private void runInternal() {
        Instant cutoff = Instant.now().minus(staleDays, ChronoUnit.DAYS);
        List<AccessHistory> staleEntries = accessHistoryRepository.findStaleActive(cutoff);

        log.info("Certification campaign started: {} stale access entries found (cutoff={})",
                staleEntries.size(), cutoff);

        int opened = 0;
        int skipped = 0;

        for (AccessHistory entry : staleEntries) {
            UUID userId = entry.getUser().getId();
            String resourceId = entry.getResourceId();

            // Idempotency guard — a PENDING cert already open for this (user, resource) pair
            if (certificationRepository.existsPendingFor(userId, resourceId)) {
                log.debug("Skipping stale entry — PENDING cert already exists: user={}, resource={}",
                        userId, resourceId);
                skipped++;
                continue;
            }

            // Pre-generate the UUID so mintToken can embed cert_id in the JWT
            // without requiring a save-then-update round trip.
            Certification cert = Certification.builder()
                    .id(UUID.randomUUID())
                    .user(entry.getUser())
                    .resourceId(resourceId)
                    .reviewer(entry.getUser().getManager())
                    .status(Certification.CertStatus.PENDING)
                    .build();

            // mintToken sets cert.tokenHash and cert.expiresAt; returns raw JWT for the email link
            String rawToken = tokenService.mintToken(cert);
            certificationRepository.save(cert);

            emailService.sendReviewEmail(cert, entry.getUser(), entry.getUser().getManager(), rawToken);

            writeAuditLog(userId, cert.getId());
            opened++;
        }

        log.info("Certification campaign complete: opened={}, skipped={}", opened, skipped);

        // Publish CloudWatch metrics so dashboards and alarms can track campaign volume
        metricsService.publishCount("CertificationsOpened", opened);
        metricsService.publishCount("StaleAccessViolations", staleEntries.size());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void writeAuditLog(UUID targetUserId, UUID certId) {
        auditLogRepository.save(AuditLog.builder()
                .eventType("CERTIFY_OPEN")
                .actor("certification-scheduler")
                .targetUserId(targetUserId)
                .resourceId(certId.toString())
                .outcome("SUCCESS")
                .correlationId(parseCorrelationId(MDC.get("correlationId")))
                .build());
    }

    private static UUID parseCorrelationId(String raw) {
        if (raw == null) return null;
        try { return UUID.fromString(raw); }
        catch (IllegalArgumentException e) { return null; }
    }
}
