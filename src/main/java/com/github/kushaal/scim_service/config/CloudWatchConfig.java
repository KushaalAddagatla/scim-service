package com.github.kushaal.scim_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClientBuilder;

import java.net.URI;

/**
 * Creates the {@link CloudWatchClient} bean used by
 * {@link com.github.kushaal.scim_service.service.CloudWatchMetricsService} to publish
 * custom certification metrics.
 *
 * <p>Only active when {@code scim.cloudwatch.namespace} is present (set in local and prod
 * profiles). When absent — tests, plain {@code ./mvnw spring-boot:run} without a profile —
 * the bean is skipped and {@code CloudWatchMetricsService} falls back to stub logging.
 *
 * <p>When {@code aws.cloudwatch.endpoint} is set (local profile), the client points at
 * LocalStack. In prod the property is absent and the SDK resolves the real CloudWatch
 * endpoint via the ECS task IAM role.
 */
@Configuration
@ConditionalOnProperty(name = "scim.cloudwatch.namespace")
public class CloudWatchConfig {

    @Value("${aws.cloudwatch.endpoint:}")
    private String endpointOverride;

    @Value("${aws.region:us-east-1}")
    private String region;

    @Bean
    public CloudWatchClient cloudWatchClient() {
        CloudWatchClientBuilder builder = CloudWatchClient.builder()
                .region(Region.of(region));

        if (!endpointOverride.isBlank()) {
            builder.endpointOverride(URI.create(endpointOverride));
        }

        return builder.build();
    }
}
