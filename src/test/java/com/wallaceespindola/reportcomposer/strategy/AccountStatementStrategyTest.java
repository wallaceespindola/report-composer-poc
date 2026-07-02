package com.wallaceespindola.reportcomposer.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import com.wallaceespindola.reportcomposer.TestFixtures;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class AccountStatementStrategyTest {

    private final AccountStatementStrategy strategy = new AccountStatementStrategy();

    @Test
    void supportsAccountStatement() {
        assertThat(strategy.supports()).isEqualTo("ACCOUNT_STATEMENT");
        assertThat(strategy.description()).isNotBlank();
    }

    @Test
    void generatesStatementWithBalanceAndAllTransactions() {
        var ctx = new ReportContext(TestFixtures.tenantBE(), "BE-ACC-0001", TestFixtures.BUSINESS_DATE, null);
        var report = strategy.generate(ctx, List.of(
                TestFixtures.txn("BE-ACC-0001", "CREDIT", "100.00"),
                TestFixtures.txn("BE-ACC-0001", "DEBIT", "-40.00")));

        String body = new String(report.content(), StandardCharsets.UTF_8);
        assertThat(report.fileName()).isEqualTo("BE_BE-ACC-0001_2026-06-30_statement.txt");
        assertThat(report.contentType()).startsWith("text/plain");
        assertThat(body)
                .contains("ACCOUNT STATEMENT")
                .contains("Account: BE-ACC-0001")
                .contains("CREDIT")
                .contains("DEBIT")
                .contains("Transactions: 2")
                .contains("Closing balance:")
                .contains("60"); // 100 - 40
    }

    @Test
    void emptyTransactionsProduceZeroBalance() {
        var ctx = new ReportContext(TestFixtures.tenantBE(), "BE-ACC-0002", TestFixtures.BUSINESS_DATE, null);
        var report = strategy.generate(ctx, List.of());
        assertThat(new String(report.content(), StandardCharsets.UTF_8)).contains("Transactions: 0");
    }
}
