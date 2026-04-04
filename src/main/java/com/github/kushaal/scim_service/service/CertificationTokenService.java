package com.github.kushaal.scim_service.service;

import com.github.kushaal.scim_service.exception.CertificationTokenAlreadyUsedException;
import com.github.kushaal.scim_service.exception.CertificationTokenExpiredException;
import com.github.kushaal.scim_service.exception.ScimInvalidValueException;
import com.github.kushaal.scim_service.model.entity.Certification;
import com.github.kushaal.scim_service.repository.CertificationRepository;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Service;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * Mints and validates single-use HS256 JWTs for certification review links.
 *
 * <p>The raw token is embedded in the email link sent to the reviewer. It is never
 * stored in the database. Instead, {@code SHA-256(rawToken)} is stored as
 * {@code token_hash} so that even if the DB is read by an attacker, they cannot
 * reconstruct a valid link.
 *
 * <p>Re-uses the same {@link SecretKeySpec} bean already wired for SCIM Bearer JWT
 * verification — no second secret is needed.
 */
@Service
public class CertificationTokenService {

    static final long REVIEW_WINDOW_DAYS = 7;
    private static final String ISSUER = "scim-service";

    private final SecretKeySpec signingKey;
    private final CertificationRepository certificationRepository;

    public CertificationTokenService(SecretKeySpec signingKey,
                                     CertificationRepository certificationRepository) {
        this.signingKey = signingKey;
        this.certificationRepository = certificationRepository;
    }

    /**
     * Builds a signed review JWT, sets {@code cert.tokenHash} and {@code cert.expiresAt},
     * and returns the raw token for embedding in the email link.
     *
     * <p>The certification is NOT saved here — the caller (scheduler) owns the transaction
     * and persists the entity after this method returns.
     *
     * @param cert a detached or managed Certification with a non-null id and user
     * @return the serialized JWT to embed in the email link
     */
    public String mintToken(Certification cert) {
        Instant expiry = Instant.now().plus(REVIEW_WINDOW_DAYS, ChronoUnit.DAYS);

        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(ISSUER)
                    .subject(cert.getUser().getId().toString())
                    .issueTime(Date.from(Instant.now()))
                    .expirationTime(Date.from(expiry))
                    .claim("cert_id", cert.getId().toString())
                    .claim("user_id", cert.getUser().getId().toString())
                    .claim("action", "review")
                    .build();

            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(signingKey.getEncoded()));
            String rawToken = jwt.serialize();

            cert.setTokenHash(sha256(rawToken));
            cert.setExpiresAt(expiry);

            return rawToken;

        } catch (JOSEException e) {
            throw new RuntimeException("Failed to mint certification token", e);
        }
    }

    /**
     * Validates a raw review token. Checks (in order): signature, expiry, DB existence,
     * and single-use flag. Throws {@link ScimInvalidValueException} (400) on any failure.
     *
     * @param rawToken the serialized JWT from the review link
     * @return the matching {@link Certification} if all checks pass
     */
    public Certification validateToken(String rawToken) {
        SignedJWT jwt = parseAndVerifySignature(rawToken);
        checkExpiry(jwt);

        String hash = sha256(rawToken);
        Certification cert = certificationRepository.findByTokenHash(hash)
                .orElseThrow(() -> new ScimInvalidValueException("Review token not found"));

        if (Boolean.TRUE.equals(cert.getTokenUsed())) {
            throw new CertificationTokenAlreadyUsedException("Review token has already been used");
        }

        return cert;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private SignedJWT parseAndVerifySignature(String rawToken) {
        try {
            SignedJWT jwt = SignedJWT.parse(rawToken);
            if (!jwt.verify(new MACVerifier(signingKey.getEncoded()))) {
                throw new ScimInvalidValueException("Invalid token signature");
            }
            return jwt;
        } catch (ParseException | JOSEException e) {
            throw new ScimInvalidValueException("Malformed review token");
        }
    }

    private void checkExpiry(SignedJWT jwt) {
        try {
            Date exp = jwt.getJWTClaimsSet().getExpirationTime();
            if (exp == null || exp.toInstant().isBefore(Instant.now())) {
                throw new CertificationTokenExpiredException("Review token has expired");
            }
        } catch (ParseException e) {
            throw new ScimInvalidValueException("Could not parse token claims");
        }
    }

    /**
     * Returns the lowercase hex-encoded SHA-256 hash of the input string.
     * 64 characters — matches the {@code VARCHAR(64)} column in the DB.
     */
    static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
