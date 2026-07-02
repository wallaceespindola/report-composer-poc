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

/**
 * One row per partition/account. The unique key (tenant, account, type, date) enforces
 * idempotency; status + attempt_count drive restartability (PRD §15).
 */
@Entity
@Table(name = "report_work_unit")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportWorkUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_job_id", nullable = false)
    private Long reportJobId;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(name = "report_type", nullable = false)
    private String reportType;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkUnitStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
