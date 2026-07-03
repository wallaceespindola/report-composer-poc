package com.wallaceespindola.reportcomposer.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** API request/response records. Every response carries a timestamp (PRD §5). */
public final class ApiDtos {

    private ApiDtos() {}

    public record JobStartRequest(
            @NotBlank String tenantId, @NotBlank String reportType, @NotNull LocalDate businessDate) {}

    public record JobStartResponse(Instant timestamp, Long jobExecutionId, String status) {}

    public record JobRestartResponse(Instant timestamp, Long jobExecutionId, String status) {}

    public record JobDetailResponse(Instant timestamp, JobSummaryDto job) {}

    public record JobListResponse(Instant timestamp, List<JobSummaryDto> jobs) {}

    public record PartitionListResponse(Instant timestamp, List<PartitionDto> partitions) {}

    public record TenantDto(
            String tenantId,
            String countryCode,
            String locale,
            String currency,
            boolean enabled,
            List<String> reportTypes) {}

    public record TenantListResponse(Instant timestamp, List<TenantDto> tenants) {}

    public record ReportTypeDto(String code, String description) {}

    public record ReportTypeListResponse(Instant timestamp, List<ReportTypeDto> reportTypes) {}

    public record ErrorResponse(Instant timestamp, int status, String error, String message) {}

    // --- onboarding / mock data (POC admin operations) ---

    public record TenantCreateRequest(
            @NotBlank String tenantId,
            @NotBlank String countryCode,
            @NotBlank String locale,
            @NotBlank String currency,
            Integer seedAccounts,
            LocalDate businessDate) {}

    public record TenantCreateResponse(
            Instant timestamp, String tenantId, int accountsCreated, int transactionsCreated) {}

    public record ContractCreateRequest(@NotBlank String reportType, LocalDate effectiveFrom) {}

    public record ContractCreateResponse(Instant timestamp, String tenantId, String reportType, boolean enabled) {}

    public record TransactionGenRequest(@NotNull LocalDate businessDate, Integer perAccount) {}

    public record TransactionGenResponse(
            Instant timestamp, String tenantId, LocalDate businessDate, int accounts, int transactionsCreated) {}

    // --- generated documents / stats ---

    public record ArtifactDto(
            Long workUnitId,
            String tenantId,
            String accountId,
            String reportType,
            LocalDate businessDate,
            String fileName,
            String contentType,
            long sizeBytes,
            String checksum,
            Instant createdAt) {}

    public record ArtifactListResponse(Instant timestamp, List<ArtifactDto> artifacts) {}

    public record StatsResponse(
            Instant timestamp,
            long tenants,
            long contracts,
            long accounts,
            long transactions,
            java.util.Map<String, Long> jobsByStatus,
            java.util.Map<String, Long> workUnitsByStatus,
            long artifacts,
            long artifactBytes,
            int activeWorkerPods,
            int workerConsumerThreads) {}
}
