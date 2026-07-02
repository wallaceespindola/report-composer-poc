package com.wallaceespindola.reportcomposer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One row per (tenantId, reportType, businessDate) job key; maps to a Spring Batch JobExecution. */
@Entity
@Table(name = "report_job")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "report_type", nullable = false)
    private String reportType;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "job_execution_id")
    private Long jobExecutionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportJobStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = createdAt == null ? Instant.now() : createdAt;
        updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
