package com.github.kushaal.scim_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClientBuilder;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;

import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.util.Base64;

/**
 * Fetches the HMAC-SHA256 JWT signing key from AWS Secrets Manager on startup
 * and exposes it as a {@link SecretKeySpec} bean consumed by {@code SecurityConfig}.
 *
 * <p>Only active when {@code scim.jwt.secret-name} is set in the environment.
 * This means the bean is skipped entirely during tests (which supply their own
 * hardcoded key via {@code TestSecurityConfig}) and in any context where the
 * property is absent, preventing spurious AWS SDK calls.
 *
 * <p>When {@code aws.secretsmanager.endpoint} is provided (local profile only),
 * the client points at LocalStack instead of real AWS. In prod the property is
 * absent and the SDK uses its default endpoint resolution (IAM role / env creds).
 */
@Configuration
@ConditionalOnProperty(name = "scim.jwt.secret-name")
public class AwsSecretsManagerConfig {

    @Value("${scim.jwt.secret-name}")
    private String secretName;

    // Empty string default — blank check below determines whether to override
    @Value("${aws.secretsmanager.endpoint:}")
    private String endpointOverride;

    @Value("${aws.region:us-east-1}")
    private String region;

    @Bean
    public SecretKeySpec jwtSigningKey() {
        SecretsManagerClient client = buildClient();

        String secretValue = client
                .getSecretValue(GetSecretValueRequest.builder().secretId(secretName).build())
                .secretString();

        // The secret is stored as a Base64-encoded 256-bit key (32 raw bytes).
        // Base64 decode gives the raw bytes; SecretKeySpec wraps them for HMAC-SHA256.
        byte[] keyBytes = Base64.getDecoder().decode(secretValue);
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    private SecretsManagerClient buildClient() {
        SecretsManagerClientBuilder builder = SecretsManagerClient.builder()
                .region(Region.of(region));

        // endpointOverride is only set in the local profile to point at LocalStack.
        // In prod the SDK resolves the real regional endpoint automatically.
        if (!endpointOverride.isBlank()) {
            builder.endpointOverride(URI.create(endpointOverride));
        }

        return builder.build();
    }
}
