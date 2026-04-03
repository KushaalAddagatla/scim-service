package com.github.kushaal.scim_service.service;

import com.github.kushaal.scim_service.model.entity.Certification;
import com.github.kushaal.scim_service.model.entity.ScimUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Sends certification review emails to managers via AWS SES.
 *
 * <p>Phase 4 stub — logs the review request instead of sending a real email.
 * Phase 5 will wire the SesClient and render the HTML template.
 */
@Service
public class CertificationEmailService {

    private static final Logger log = LoggerFactory.getLogger(CertificationEmailService.class);

    public void sendReviewEmail(Certification cert, ScimUser user, ScimUser reviewer, String rawToken) {
        String reviewerEmail = reviewer != null ? reviewer.getUserName() : "(no manager)";
        log.info("[STUB] Would send review email: cert={}, user={}, reviewer={}, tokenPresent={}",
                cert.getId(), user.getUserName(), reviewerEmail, rawToken != null);
    }
}
