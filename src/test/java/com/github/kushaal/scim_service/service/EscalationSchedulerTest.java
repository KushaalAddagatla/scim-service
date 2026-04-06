package com.github.kushaal.scim_service.service;

import com.github.kushaal.scim_service.dto.request.ScimPatchRequest;
import com.github.kushaal.scim_service.model.entity.AuditLog;
import com.github.kushaal.scim_service.model.entity.Certification;
import com.github.kushaal.scim_service.model.entity.ScimUser;
import com.github.kushaal.scim_service.repository.AuditLogRepository;
import com.github.kushaal.scim_service.repository.CertificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EscalationSchedulerTest {

    @Mock private CertificationRepository certificationRepository;
    @Mock private CertificationEmailService emailService;
    @Mock private ScimUserService userService;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private CloudWatchMetricsService metricsService;

    @InjectMocks
    private EscalationScheduler escalationScheduler;

    @Test
    void runEscalationSweep_twoExpiredCerts_patchAndAuditCalledTwice() {
        Certification cert1 = buildExpiredCert();
        Certification cert2 = buildExpiredCert();
        when(certificationRepository.findExpiredPending(any(Instant.class)))
                .thenReturn(List.of(cert1, cert2));

        escalationScheduler.runEscalationSweep();

        // patch fires once per cert to deactivate the user
        verify(userService, times(2)).patch(any(UUID.class), any(ScimPatchRequest.class), isNull());

        // one audit log entry per cert
        verify(auditLogRepository, times(2)).save(any(AuditLog.class));
    }

    @Test
    void runEscalationSweep_auditLogContainsExpiresAt() {
        Certification cert = buildExpiredCert();
        when(certificationRepository.findExpiredPending(any(Instant.class)))
                .thenReturn(List.of(cert));

        escalationScheduler.runEscalationSweep();

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog log = captor.getValue();
        assertThat(log.getEventType()).isEqualTo("CERTIFY_ESCALATE");
        assertThat(log.getActor()).isEqualTo("escalation-scheduler");
        assertThat(log.getTargetUserId()).isEqualTo(cert.getUser().getId());
        // expires_at must appear in the JSONB payload for audit trail auditability
        assertThat(log.getScimOperation())
                .contains("expires_at")
                .contains("auto_suspended")
                .contains(cert.getId().toString());
    }

    @Test
    void runEscalationSweep_noExpiredCerts_noSideEffects() {
        when(certificationRepository.findExpiredPending(any(Instant.class)))
                .thenReturn(List.of());

        escalationScheduler.runEscalationSweep();

        verify(userService, never()).patch(any(), any(), any());
        verify(auditLogRepository, never()).save(any());
        verify(emailService, never()).sendEscalationEmail(any(), any(), any());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Certification buildExpiredCert() {
        ScimUser manager = ScimUser.builder()
                .id(UUID.randomUUID())
                .userName("manager@example.com")
                .displayName("Alice Manager")
                .build();

        ScimUser user = ScimUser.builder()
                .id(UUID.randomUUID())
                .userName("bob@example.com")
                .displayName("Bob User")
                .manager(manager)
                .build();

        return Certification.builder()
                .id(UUID.randomUUID())
                .user(user)
                .reviewer(manager)
                .resourceId("res-github-access")
                .status(Certification.CertStatus.PENDING)
                // Expired 1 day ago
                .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();
    }
}
