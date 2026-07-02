package com.wallaceespindola.reportcomposer.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ReportStrategyResolverTest {

    private final ReportStrategyResolver resolver =
            new ReportStrategyResolver(List.of(new AccountStatementStrategy(), new TaxSummaryStrategy()));

    @Test
    void resolvesRegisteredStrategies() {
        assertThat(resolver.resolve("ACCOUNT_STATEMENT")).isInstanceOf(AccountStatementStrategy.class);
        assertThat(resolver.resolve("TAX_SUMMARY")).isInstanceOf(TaxSummaryStrategy.class);
        assertThat(resolver.isRegistered("ACCOUNT_STATEMENT")).isTrue();
    }

    @Test
    void failsFastOnUnknownType() {
        assertThat(resolver.isRegistered("PORTFOLIO_VALUATION")).isFalse();
        assertThatThrownBy(() -> resolver.resolve("PORTFOLIO_VALUATION"))
                .isInstanceOf(UnknownReportTypeException.class)
                .hasMessageContaining("PORTFOLIO_VALUATION");
    }

    @Test
    void catalogListsAllStrategiesSorted() {
        assertThat(resolver.catalog())
                .extracting(ReportTypeStrategy::supports)
                .containsExactly("ACCOUNT_STATEMENT", "TAX_SUMMARY");
    }
}
