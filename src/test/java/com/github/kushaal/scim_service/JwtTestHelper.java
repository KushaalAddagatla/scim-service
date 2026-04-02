package com.github.kushaal.scim_service;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Builds signed JWTs for integration tests.
 *
 * <p>Uses a fixed 32-byte (256-bit) key so tokens are deterministic and verifiable
 * against the {@link SecretKeySpec} bean registered in {@link TestcontainersConfiguration}.
 * Both must use the same key — any change here must be reflected there and vice versa.
 *
 * <p>Nimbus ({@code nimbus-jose-jwt}) is already on the test classpath as a transitive
 * dependency of {@code spring-boot-starter-oauth2-resource-server}, so no extra
 * dependency is needed.
 */
public class JwtTestHelper {

    // 32 ASCII characters = 32 bytes = 256-bit key. Fixed so tests are reproducible.
    // Never used outside the test context.
    static final SecretKeySpec TEST_KEY = new SecretKeySpec(
            "scim-service-test-signing-key-32".getBytes(StandardCharsets.UTF_8),
            "HmacSHA256");

    private static final String ISSUER = "scim-service";
    private static final String SCOPE  = "scim:provision";

    /** A fully valid token — correct issuer, correct scope, expires in 1 hour. */
    public static String validToken() {
        return build(ISSUER, SCOPE, Instant.now().plusSeconds(3600));
    }

    /** Correct issuer and scope but expiry is 1 hour in the past → should 401. */
    public static String expiredToken() {
        return build(ISSUER, SCOPE, Instant.now().minusSeconds(3600));
    }

    /** Valid signature and expiry but issuer is wrong → should 401. */
    public static String wrongIssuerToken() {
        return build("not-scim-service", SCOPE, Instant.now().plusSeconds(3600));
    }

    /** Valid signature, expiry, and issuer but scope is insufficient → should 403. */
    public static String wrongScopeToken() {
        return build(ISSUER, "scim:read", Instant.now().plusSeconds(3600));
    }

    /** Valid signature, expiry, and issuer but no scope claim at all → should 403. */
    public static String noScopeToken() {
        return build(ISSUER, null, Instant.now().plusSeconds(3600));
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    private static String build(String issuer, String scope, Instant expiry) {
        try {
            JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                    .issuer(issuer)
                    .subject("test-client")
                    .issueTime(Date.from(Instant.now()))
                    .expirationTime(Date.from(expiry));

            if (scope != null) {
                claims.claim("scope", scope);
            }

            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims.build());
            jwt.sign(new MACSigner(TEST_KEY.getEncoded()));
            return jwt.serialize();

        } catch (JOSEException e) {
            throw new RuntimeException("Failed to build test JWT", e);
        }
    }
}
