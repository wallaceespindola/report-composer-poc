package com.wallaceespindola.reportcomposer.strategy;

import com.wallaceespindola.reportcomposer.domain.TransactionEntity;
import java.util.List;

/**
 * Strategy per report type (PRD §7). Adding a new report type = one new @Component
 * implementing this interface; no changes to orchestration, partitioning, API, or
 * persistence. The set of registered strategies is the agreed catalog that
 * tenant_report_contract rows may reference.
 */
public interface ReportTypeStrategy {

    /** The reportType code this strategy handles (e.g. ACCOUNT_STATEMENT). */
    String supports();

    /** Human-readable description, surfaced by GET /api/v1/report-types. */
    String description();

    GeneratedReport generate(ReportContext ctx, List<TransactionEntity> transactions);
}
