package com.wallaceespindola.reportcomposer;

import com.wallaceespindola.reportcomposer.config.AppProperties;
import com.wallaceespindola.reportcomposer.domain.Account;
import com.wallaceespindola.reportcomposer.domain.Tenant;
import com.wallaceespindola.reportcomposer.domain.TenantReportContract;
import com.wallaceespindola.reportcomposer.domain.TransactionEntity;
import java.math.BigDecimal;
import java.time.LocalDate;

public final class TestFixtures {

    public static final LocalDate BUSINESS_DATE = LocalDate.parse("2026-06-30");

    private TestFixtures() {}

    public static Tenant tenantBE() {
        return Tenant.builder()
                .tenantId("BE")
                .countryCode("BE")
                .locale("nl-BE")
                .currency("EUR")
                .enabled(true)
                .build();
    }

    public static TenantReportContract contract(String tenantId, String reportType) {
        return TenantReportContract.builder()
                .tenantId(tenantId)
                .reportType(reportType)
                .enabled(true)
                .effectiveFrom(LocalDate.parse("2026-01-01"))
                .paramsJson("{\"outputFormat\":\"TXT\"}")
                .build();
    }

    public static Account account(String tenantId, String accountId) {
        return Account.builder()
                .accountId(accountId)
                .tenantId(tenantId)
                .customerName("Test Customer")
                .eligible(true)
                .build();
    }

    public static TransactionEntity txn(String accountId, String type, String amount) {
        return TransactionEntity.builder()
                .tenantId("BE")
                .accountId(accountId)
                .businessDate(BUSINESS_DATE)
                .amount(new BigDecimal(amount))
                .currency("EUR")
                .txnType(type)
                .description(type + " test")
                .build();
    }

    public static AppProperties appProperties(String role, String launcher) {
        return new AppProperties(
                role,
                launcher,
                new AppProperties.Kafka("report.partitions", "report.replies", 10, 2, "report-workers"),
                new AppProperties.Seed(true, 5, BUSINESS_DATE),
                new AppProperties.Minio("http://localhost:9000", "reports", "minioadmin", "minioadmin"),
                new AppProperties.K8s("report-composer", "k8s/master-job-template.yaml"),
                new AppProperties.Master("BE", "ACCOUNT_STATEMENT", "2026-06-30", ""));
    }
}
