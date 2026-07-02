package com.wallaceespindola.reportcomposer.api.dto;

import java.time.Instant;
import java.time.LocalDate;

public record JobSummaryDto(
        Long jobExecutionId,
        String tenantId,
        String reportType,
        LocalDate businessDate,
        String status,
        Instant startTime,
        Instant endTime,
        long partitionsTotal,
        long partitionsCompleted,
        long partitionsFailed) {}
