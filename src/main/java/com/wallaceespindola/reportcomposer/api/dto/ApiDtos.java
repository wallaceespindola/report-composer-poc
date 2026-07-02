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
}
