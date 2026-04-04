package com.github.kushaal.scim_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.SesClientBuilder;

import java.net.URI;

/**
 * Creates the {@link SesClient} bean used by {@link com.github.kushaal.scim_service.service.CertificationEmailService}
 * to send certification review emails via AWS SES.
 *
 * <p>Only activated when {@code scim.ses.from-address} is present in the environment.
 * When absent (tests, local dev without LocalStack, CI), the bean is skipped and
 * {@code CertificationEmailService} falls back to stub logging.
 *
 * <p>When {@code aws.ses.endpoint} is set (local profile), the client points at LocalStack.
 * In prod the property is absent and the SDK resolves the real regional endpoint via the
 * task IAM role.
 */
@Configuration
@ConditionalOnProperty(name = "scim.ses.from-address")
public class SesConfig {

    @Value("${aws.ses.endpoint:}")
    private String endpointOverride;

    @Value("${aws.region:us-east-1}")
    private String region;

    @Bean
    public SesClient sesClient() {
        SesClientBuilder builder = SesClient.builder()
                .region(Region.of(region));

        if (!endpointOverride.isBlank()) {
            builder.endpointOverride(URI.create(endpointOverride));
        }

        return builder.build();
    }
}
