package com.wallaceespindola.reportcomposer.config;

import java.time.LocalDate;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** All externalized configuration (PRD §13), bound from env vars via application.yml. */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String role,
        String launcher,
        Kafka kafka,
        Seed seed,
        Minio minio,
        K8s k8s,
        Master master) {

    public record Kafka(
            String requestTopic,
            String replyTopic,
            int requestPartitions,
            int concurrency,
            String consumerGroup) {}

    public record Seed(boolean enabled, int accountsPerTenant, LocalDate businessDate) {}

    public record Minio(String endpoint, String bucket, String accessKey, String secretKey) {}

    public record K8s(String namespace, String masterJobTemplate) {}

    /** Job-key parameters injected into the master pod (k8s mode). */
    public record Master(String tenantId, String reportType, String businessDate, String restartExecutionId) {}
}
