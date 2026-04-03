package com.github.kushaal.scim_service.service;

import com.github.kushaal.scim_service.JwtTestHelper;
import com.github.kushaal.scim_service.exception.ScimInvalidValueException;
import com.github.kushaal.scim_service.model.entity.Certification;
import com.github.kushaal.scim_service.model.entity.ScimUser;
import com.github.kushaal.scim_service.repository.CertificationRepository;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CertificationTokenServiceTest {

    @Mock
    private CertificationRepository certificationRepository;

    private CertificationTokenService tokenService;

    @BeforeEach
    void setUp() {
        tokenService = new CertificationTokenService(JwtTestHelper.TEST_KEY, certificationRepository);
    }

    @Test
    void mintAndValidate_roundTrip_succeeds() {
        Certification cert = buildCert();

        String rawToken = tokenService.mintToken(cert);

        assertThat(cert.getTokenHash()).isNotNull().hasSize(64);
        assertThat(cert.getExpiresAt()).isAfter(Instant.now());

        when(certificationRepository.findByTokenHash(cert.getTokenHash()))
                .thenReturn(Optional.of(cert));

        Certification result = tokenService.validateToken(rawToken);
        assertThat(result).isSameAs(cert);
    }

    @Test
    void validateToken_expiredJwt_throws() {
        String expiredToken = buildExpiredToken();

        assertThatThrownBy(() -> tokenService.validateToken(expiredToken))
                .isInstanceOf(ScimInvalidValueException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void validateToken_usedToken_throws() {
        Certification cert = buildCert();
        String rawToken = tokenService.mintToken(cert);
        cert.setTokenUsed(true);

        when(certificationRepository.findByTokenHash(cert.getTokenHash()))
                .thenReturn(Optional.of(cert));

        assertThatThrownBy(() -> tokenService.validateToken(rawToken))
                .isInstanceOf(ScimInvalidValueException.class)
                .hasMessageContaining("already been used");
    }

    @Test
    void validateToken_tamperedSignature_throws() {
        Certification cert = buildCert();
        String rawToken = tokenService.mintToken(cert);

        // Replace the signature segment (third JWT part) with a wrong-but-valid-base64url value
        String[] parts = rawToken.split("\\.");
        String tampered = parts[0] + "." + parts[1] + ".AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

        assertThatThrownBy(() -> tokenService.validateToken(tampered))
                .isInstanceOf(ScimInvalidValueException.class);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Certification buildCert() {
        ScimUser user = ScimUser.builder()
                .id(UUID.randomUUID())
                .userName("test@example.com")
                .build();
        return Certification.builder()
                .id(UUID.randomUUID())
                .user(user)
                .resourceId("res-github")
                .build();
    }

    private String buildExpiredToken() {
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
