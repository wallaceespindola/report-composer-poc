package com.wallaceespindola.reportcomposer.api.dto;

public record PartitionDto(Long workUnitId, String accountId, String status, int attemptCount, String artifactKey) {}
