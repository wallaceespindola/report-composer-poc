package com.wallaceespindola.reportcomposer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Binds a tenant to an agreed report type (PRD §3.7). Inserting rows here onboards a
 * country for a report type with no code change.
 */
@Entity
@Table(name = "tenant_report_contract")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantReportContract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "report_type", nullable = false)
    private String reportType;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Lob
    @Column(name = "params_json")
    private String paramsJson;

    /** Whether this contract is active for the given business date. */
    public boolean isActiveOn(LocalDate businessDate) {
        return enabled
                && (effectiveFrom == null || !businessDate.isBefore(effectiveFrom))
                && (effectiveTo == null || !businessDate.isAfter(effectiveTo));
    }
}
