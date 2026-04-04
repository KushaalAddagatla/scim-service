package com.github.kushaal.scim_service.service;

import com.github.kushaal.scim_service.model.entity.Certification;
import com.github.kushaal.scim_service.model.entity.ScimUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link CertificationEmailService}.
 *
 * <p>The SesClient is mocked — no real AWS calls. {@code @InjectMocks} wires the
 * mock SesClient into the service via field injection (matching the field name).
 * {@code @Value} fields don't participate in Mockito injection, so we set them
 * directly with {@code ReflectionTestUtils.setField} in {@code setUp}.
 */
@ExtendWith(MockitoExtension.class)
class CertificationEmailServiceTest {

    private static final String FROM_ADDRESS = "noreply@test.scim.local";
    private static final String BASE_URL     = "http://localhost:8080";
    private static final String RAW_TOKEN    = "header.payload.signature";

    @Mock
    private SesClient sesClient;

    @InjectMocks
    private CertificationEmailService emailService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "fromAddress", FROM_ADDRESS);
        ReflectionTestUtils.setField(emailService, "baseUrl", BASE_URL);
    }

    @Test
    void sendReviewEmail_callsSesWithCorrectToFromAndSubject() {
        ScimUser user     = buildUser("alice@example.com", "Alice Smith");
        ScimUser reviewer = buildUser("manager@example.com", "Bob Manager");
        Certification cert = buildCert(user, reviewer);

        emailService.sendReviewEmail(cert, user, reviewer, RAW_TOKEN);

        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(sesClient).sendEmail(captor.capture());

        SendEmailRequest req = captor.getValue();
        assertThat(req.source()).isEqualTo(FROM_ADDRESS);
        assertThat(req.destination().toAddresses()).containsExactly("manager@example.com");
        assertThat(req.message().subject().data()).contains("Access Review Required");
        assertThat(req.message().subject().data()).contains("Alice Smith");
        assertThat(req.message().subject().data()).contains(cert.getResourceId());
    }

    @Test
    void sendReviewEmail_htmlBodyContainsBothActionUrls() {
        ScimUser user     = buildUser("bob@example.com", "Bob User");
        ScimUser reviewer = buildUser("manager@example.com", "Alice Manager");
        Certification cert = buildCert(user, reviewer);

        emailService.sendReviewEmail(cert, user, reviewer, RAW_TOKEN);

        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(sesClient).sendEmail(captor.capture());

        String htmlBody = captor.getValue().message().body().html().data();
        String textBody = captor.getValue().message().body().text().data();

        String expectedApprove = BASE_URL + "/certifications/action?token=" + RAW_TOKEN + "&decision=approve";
        String expectedRevoke  = BASE_URL + "/certifications/action?token=" + RAW_TOKEN + "&decision=revoke";

        // Both URLs must appear in the HTML (the clickable buttons)
        assertThat(htmlBody).contains(expectedApprove);
        assertThat(htmlBody).contains(expectedRevoke);

        // Both URLs must also appear in the plain-text fallback
        assertThat(textBody).contains(expectedApprove);
        assertThat(textBody).contains(expectedRevoke);
    }

    @Test
    void sendReviewEmail_nullReviewer_doesNotSendEmail() {
        ScimUser user = buildUser("carol@example.com", "Carol User");
        Certification cert = buildCert(user, null);

        // reviewer = null → email skipped, no SES call
        emailService.sendReviewEmail(cert, user, null, RAW_TOKEN);

        verify(sesClient, never()).sendEmail(org.mockito.ArgumentMatchers.any(SendEmailRequest.class));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ScimUser buildUser(String userName, String displayName) {
        return ScimUser.builder()
                .id(UUID.randomUUID())
                .userName(userName)
                .displayName(displayName)
                .build();
    }

    private Certification buildCert(ScimUser user, ScimUser reviewer) {
        return Certification.builder()
                .id(UUID.randomUUID())
                .user(user)
                .reviewer(reviewer)
                .resourceId("res-github-access")
                .status(Certification.CertStatus.PENDING)
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .build();
    }
}
