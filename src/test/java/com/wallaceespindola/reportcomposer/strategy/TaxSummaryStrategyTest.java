package com.wallaceespindola.reportcomposer.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import com.wallaceespindola.reportcomposer.TestFixtures;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class TaxSummaryStrategyTest {

    private final TaxSummaryStrategy strategy = new TaxSummaryStrategy();

    @Test
    void supportsTaxSummary() {
        assertThat(strategy.supports()).isEqualTo("TAX_SUMMARY");
        assertThat(strategy.description()).isNotBlank();
    }

    @Test
    void computesTotalsByTypeAndWithholdingOnCreditsOnly() {
        var ctx = new ReportContext(TestFixtures.tenantBE(), "BE-ACC-0001", TestFixtures.BUSINESS_DATE, null);
        var report = strategy.generate(ctx, List.of(
                TestFixtures.txn("BE-ACC-0001", "CREDIT", "200.00"),
                TestFixtures.txn("BE-ACC-0001", "CREDIT", "100.00"),
                TestFixtures.txn("BE-ACC-0001", "DEBIT", "-50.00")));

        String body = new String(report.content(), StandardCharsets.UTF_8);
        assertThat(report.fileName()).endsWith("tax-summary.txt");
        assertThat(body)
                .contains("TAX SUMMARY")
                .contains("Taxable credits: 300.00")
                .contains("Indicative withholding (30%): 90.00") // 300 * 0.30
                .contains("CREDIT")
                .contains("DEBIT");
    }
}
