package com.github.kushaal.scim_service.controller;

import com.github.kushaal.scim_service.JwtTestHelper;
import com.github.kushaal.scim_service.TestcontainersConfiguration;
import com.github.kushaal.scim_service.model.entity.AccessHistory;
import com.github.kushaal.scim_service.model.entity.Certification;
import com.github.kushaal.scim_service.model.entity.ScimUser;
import com.github.kushaal.scim_service.repository.AccessHistoryRepository;
import com.github.kushaal.scim_service.repository.AuditLogRepository;
import com.github.kushaal.scim_service.repository.CertificationRepository;
import com.github.kushaal.scim_service.repository.ScimUserRepository;
import com.github.kushaal.scim_service.service.CertificationScheduler;
import com.github.kushaal.scim_service.service.CertificationTokenService;
import com.github.kushaal.scim_service.service.EscalationScheduler;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the Access Certification engine.
 *
 * <p>Runs against a real PostgreSQL container (Testcontainers). No mocks — the full
 * Spring context loads, including JPA, security, and scheduler beans.
 *
 * <p>SES and CloudWatch are absent from the test context (their beans are conditional
 * on properties not set here), so {@link com.github.kushaal.scim_service.service.CertificationEmailService}
 * falls back to stub logging. All other components — token service, scheduler, action
 * handler — run unmodified against the real database.
 *
 * <p>The {@code /certifications/action} endpoint is {@code permitAll} in
 * {@link com.github.kushaal.scim_service.config.SecurityConfig} — no Bearer token
 * is needed for these requests. The single-use JWT in the {@code token} param is the
 * only authentication mechanism, which is exactly what these tests exercise.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CertificationIT {

    @Autowired MockMvc mockMvc;
    @Autowired ScimUserRepository userRepository;
    @Autowired AccessHistoryRepository accessHistoryRepository;
    @Autowired CertificationRepository certificationRepository;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired CertificationScheduler certificationScheduler;
    @Autowired EscalationScheduler escalationScheduler;
    @Autowired CertificationTokenService certificationTokenService;

    @BeforeEach
    void cleanUp() {
        // Order matters: certifications and access_history both have FKs to scim_users
        auditLogRepository.deleteAll();
        certificationRepository.deleteAll();
        accessHistoryRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ── 1. Scheduler ─────────────────────────────────────────────────────────

    /**
     * Seeds a stale access_history row (last_accessed_at = 100 days ago), then triggers
     * the certification scheduler directly (bypassing the cron expression). Asserts that
     * a PENDING certification row is created with a token hash set, and that a
     * CERTIFY_OPEN audit log entry is written.
     *
     * <p>This verifies the scheduler is idempotent on re-run because the second call
     * would find an existing PENDING cert and skip it.
     */
    @Test
    void scheduler_staleAccess_createsPendingCertificationAndAuditLog() {
        ScimUser user = saveUser("stale@example.com", "Stale User");
        saveStaleAccessHistory(user, "res-github");

        certificationScheduler.runCertificationCampaign();

        List<Certification> certs = certificationRepository.findAll();
        assertThat(certs).hasSize(1);
        assertThat(certs.get(0).getStatus()).isEqualTo(Certification.CertStatus.PENDING);
        assertThat(certs.get(0).getUser().getId()).isEqualTo(user.getId());
        // tokenHash is written by CertificationTokenService.mintToken — confirms the
        // token was minted and embedded (even though we can't recover the raw JWT here)
        assertThat(certs.get(0).getTokenHash()).isNotNull().hasSize(64);

        assertThat(auditLogRepository.findAll())
                .anyMatch(l -> "CERTIFY_OPEN".equals(l.getEventType()));

        // Idempotency: second run skips because PENDING cert already exists
        certificationScheduler.runCertificationCampaign();
        assertThat(certificationRepository.findAll()).hasSize(1);
    }

    // ── 2. Approve ───────────────────────────────────────────────────────────

    /**
     * A valid approve decision: returns 200 text/html with "Approved" in the body,
     * sets certifications.status = APPROVED with a decided_at timestamp,
     * and writes a CERTIFY_APPROVE audit log row.
     */
    @Test
    void approve_validToken_certApproved_auditLogged() throws Exception {
        ScimUser user = saveUser("alice@example.com", "Alice Smith");
        String rawToken = mintAndSaveCert(user);

        mockMvc.perform(get("/certifications/action")
                        .param("token", rawToken)
                        .param("decision", "approve"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Approved")));

        Certification cert = certificationRepository.findAll().get(0);
        assertThat(cert.getStatus()).isEqualTo(Certification.CertStatus.APPROVED);
        assertThat(cert.getDecidedAt()).isNotNull();

        assertThat(auditLogRepository.findAll())
                .anyMatch(l -> "CERTIFY_APPROVE".equals(l.getEventType())
                            && user.getId().equals(l.getTargetUserId()));
    }

    // ── 3 + 6. Revoke — verifies HTML response AND internal PATCH active=false ──

    /**
     * A valid revoke decision: returns 200 text/html with "Revoked" in the body,
     * sets certifications.status = REVOKED, fires the internal PATCH that sets
     * scim_users.active = false (confirmed in DB), and writes a CERTIFY_REVOKE
     * audit log row.
     *
     * <p>The internal PATCH runs via {@link com.github.kushaal.scim_service.service.CertificationService#processAction}
     * → {@code deactivateUser()} → {@link com.github.kushaal.scim_service.service.ScimUserService#patch}.
     * Both the cert update and the user patch share the same transaction so the
     * DB read after the call sees the final committed state.
     */
    @Test
    void revoke_validToken_userDeactivated_auditLogged() throws Exception {
        ScimUser user = saveUser("bob@example.com", "Bob User");
        String rawToken = mintAndSaveCert(user);

        mockMvc.perform(get("/certifications/action")
                        .param("token", rawToken)
                        .param("decision", "revoke"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Revoked")));

        Certification cert = certificationRepository.findAll().get(0);
        assertThat(cert.getStatus()).isEqualTo(Certification.CertStatus.REVOKED);

        // Internal PATCH via CertificationService.deactivateUser() must persist active = false
        ScimUser refreshed = userRepository.findById(user.getId()).orElseThrow();
        assertThat(refreshed.getActive()).isFalse();

        assertThat(auditLogRepository.findAll())
                .anyMatch(l -> "CERTIFY_REVOKE".equals(l.getEventType())
                            && user.getId().equals(l.getTargetUserId()));
    }

    // ── 4. Token replay → 409 ────────────────────────────────────────────────

    /**
     * Replaying the same token after a successful approve click returns 409 Conflict
     * as HTML — the token is burned on first use (fail-secure: token_used = true is
     * written before any outcome is applied).
     */
    @Test
    void tokenReplay_after_approve_returns409Html() throws Exception {
        ScimUser user = saveUser("carol@example.com", "Carol");
        String rawToken = mintAndSaveCert(user);

        // First click succeeds
        mockMvc.perform(get("/certifications/action")
                        .param("token", rawToken)
                        .param("decision", "approve"))
                .andExpect(status().isOk());

        // Replay: token is already burned → 409 Conflict, HTML body
        mockMvc.perform(get("/certifications/action")
                        .param("token", rawToken)
                        .param("decision", "approve"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }

    // ── 5. Expired token → 410 ───────────────────────────────────────────────

    /**
     * A token whose JWT expiry is in the past returns 410 Gone as HTML.
     * The token is signed with the same test key so it passes signature verification —
     * the expiry check in {@link CertificationTokenService#validateToken} must catch it.
     */
    @Test
    void expiredToken_returns410Html() throws Exception {
        String expiredToken = buildExpiredReviewToken();

        mockMvc.perform(get("/certifications/action")
                        .param("token", expiredToken)
                        .param("decision", "approve"))
                .andExpect(status().isGone())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }

    // ── 7. Auto-suspend escalation ───────────────────────────────────────────

    /**
     * Inserts a PENDING certification whose expiresAt is in the past, triggers the
     * escalation sweep directly, and asserts that the cert transitions to EXPIRED
     * and the user is deactivated (scim_users.active = false). A CERTIFY_ESCALATE
     * audit log entry must also be written.
     *
     * <p>No manager is set on the user, so the escalation email is skipped (stub log
     * path). This is intentional — the fail-secure suspension happens regardless of
     * whether an email can be sent.
     */
    @Test
    void escalationSweep_expiredPendingCert_userSuspended_statusExpired() {
        ScimUser user = saveUser("dave@example.com", "Dave User");
        Certification cert = saveExpiredPendingCert(user);

        escalationScheduler.runEscalationSweep();

        Certification updated = certificationRepository.findById(cert.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(Certification.CertStatus.EXPIRED);
        assertThat(updated.getDecidedAt()).isNotNull();

        ScimUser updatedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(updatedUser.getActive()).isFalse();

        assertThat(auditLogRepository.findAll())
                .anyMatch(l -> "CERTIFY_ESCALATE".equals(l.getEventType())
                            && user.getId().equals(l.getTargetUserId()));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Persists a minimal active user with no manager. */
    private ScimUser saveUser(String userName, String displayName) {
        return userRepository.save(ScimUser.builder()
                .userName(userName)
                .displayName(displayName)
                .active(true)
                .build());
    }

    /**
     * Inserts an access_history row with last_accessed_at = 100 days ago,
     * beyond the 90-day stale threshold configured in application.yml.
     */
    private void saveStaleAccessHistory(ScimUser user, String resourceId) {
        accessHistoryRepository.save(AccessHistory.builder()
                .user(user)
                .resourceId(resourceId)
                .resourceName(resourceId)
                .accessGrantedAt(Instant.now().minus(200, ChronoUnit.DAYS))
                .lastAccessedAt(Instant.now().minus(100, ChronoUnit.DAYS))
                .accessStatus(AccessHistory.AccessStatus.ACTIVE)
                .build());
    }

    /**
     * Creates a PENDING certification and returns the raw JWT for use in HTTP requests.
     *
     * <p>Two-step save: persist first (JPA generates UUID) → mint token (embeds cert ID
     * in JWT claims, sets tokenHash + expiresAt) → save again (UPDATE with tokenHash).
     * Pre-setting the UUID and calling save() once would fail: Hibernate 6 calls merge()
     * for non-null IDs, but INSERT-on-miss is not guaranteed — it treats a missing row as
     * a concurrent delete and throws ObjectOptimisticLockingFailureException.
     */
    private String mintAndSaveCert(ScimUser user) {
        Certification cert = certificationRepository.save(Certification.builder()
                .user(user)
                .resourceId("res-github")
                .status(Certification.CertStatus.PENDING)
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS)) // placeholder, overwritten by mintToken
                .build());
        String rawToken = certificationTokenService.mintToken(cert); // sets tokenHash + expiresAt
        certificationRepository.save(cert); // UPDATE: persist tokenHash + final expiresAt
        return rawToken;
    }

    /**
     * Inserts a PENDING certification with expiresAt in the past so the escalation
     * scheduler picks it up on the next sweep.
     */
    private Certification saveExpiredPendingCert(ScimUser user) {
        return certificationRepository.save(Certification.builder()
                .user(user)
                .resourceId("res-github")
                .status(Certification.CertStatus.PENDING)
                .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .build());
    }

    /**
     * Builds an HS256 JWT with expiry 1 hour in the past, signed with the test key.
     * The signature check passes; the expiry check throws
     * {@link com.github.kushaal.scim_service.exception.CertificationTokenExpiredException} (410).
     */
    private String buildExpiredReviewToken() {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer("scim-service")
                    .subject(UUID.randomUUID().toString())
                    .expirationTime(Date.from(Instant.now().minusSeconds(3600)))
                    .claim("cert_id", UUID.randomUUID().toString())
                    .claim("user_id", UUID.randomUUID().toString())
                    .claim("action", "review")
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(JwtTestHelper.TEST_KEY.getEncoded()));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new RuntimeException(e);
        }
    }
}
