package com.github.kushaal.scim_service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.MetricDatum;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit;

import java.time.Instant;

/**
 * Publishes custom COUNT metrics to CloudWatch under the configured namespace.
 *
 * <p>This service is always available as a Spring bean — schedulers inject it
 * unconditionally. When {@code CloudWatchConfig} is not loaded (tests, no-profile local
 * run), {@code cloudWatchClient} is {@code null} and every publish call stubs to a log
 * statement instead, keeping the schedulers operational without CloudWatch configured.
 *
 * <p>Metrics published by the certification engine:
 * <ul>
 *   <li>{@code CertificationsOpened} — number of new PENDING certifications opened by the weekly scheduler</li>
 *   <li>{@code StaleAccessViolations} — number of access_history rows that were stale (found by the scheduler,
 *       including any skipped due to idempotency guard)</li>
 *   <li>{@code AutoSuspensions} — number of users auto-suspended by the nightly escalation sweep</li>
 * </ul>
 */
@Service
public class CloudWatchMetricsService {

    private static final Logger log = LoggerFactory.getLogger(CloudWatchMetricsService.class);

    // Null when CloudWatchConfig is not loaded — graceful stub fallback
    @Autowired(required = false)
    private CloudWatchClient cloudWatchClient;

    @Value("${scim.cloudwatch.namespace:ScimService/Certifications}")
    private String namespace;

    /**
     * Publishes a single COUNT metric to CloudWatch.
     *
     * <p>CloudWatch aggregates metrics by namespace + metric name. Each call emits one
     * data point with the current timestamp. For the certification scheduler this is
     * called once per run — downstream dashboards and alarms can graph it over time.
     *
     * @param metricName CloudWatch metric name (e.g. {@code "CertificationsOpened"})
     * @param count      the value to emit
     */
    public void publishCount(String metricName, long count) {
        if (cloudWatchClient == null) {
            log.info("[METRICS STUB] {} / {} = {}", namespace, metricName, count);
            return;
        }

        cloudWatchClient.putMetricData(PutMetricDataRequest.builder()
                .namespace(namespace)
                .metricData(MetricDatum.builder()
                        .metricName(metricName)
                        .value((double) count)
                        .unit(StandardUnit.COUNT)
                        .timestamp(Instant.now())
                        .build())
                .build());

        log.debug("Published CloudWatch metric: {}/{} = {}", namespace, metricName, count);
    }
}
