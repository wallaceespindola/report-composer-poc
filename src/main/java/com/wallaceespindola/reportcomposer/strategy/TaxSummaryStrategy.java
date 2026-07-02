package com.wallaceespindola.reportcomposer.strategy;

import com.wallaceespindola.reportcomposer.domain.TransactionEntity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Tax summary: totals per transaction type plus an indicative withholding amount. */
@Component
public class TaxSummaryStrategy implements ReportTypeStrategy {

    public static final String TYPE = "TAX_SUMMARY";

    // ponytail: flat indicative rate; per-tenant rates belong in tenant_report_contract params
    private static final BigDecimal TAX_RATE = new BigDecimal("0.30");

    @Override
    public String supports() {
        return TYPE;
    }

    @Override
    public String description() {
        return "Per-account tax summary with totals by transaction type";
    }

    @Override
    public GeneratedReport generate(ReportContext ctx, List<TransactionEntity> transactions) {
        Map<String, BigDecimal> totalsByType = transactions.stream()
                .collect(Collectors.groupingBy(
                        TransactionEntity::getTxnType,
                        TreeMap::new,
                        Collectors.reducing(BigDecimal.ZERO, TransactionEntity::getAmount, BigDecimal::add)));

        BigDecimal credits = transactions.stream()
                .map(TransactionEntity::getAmount)
                .filter(a -> a.signum() > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal withholding = credits.multiply(TAX_RATE).setScale(2, RoundingMode.HALF_UP);

        StringBuilder sb = new StringBuilder();
        sb.append("TAX SUMMARY\n");
        sb.append("Tenant: ").append(ctx.tenant().getTenantId()).append('\n');
        sb.append("Account: ").append(ctx.accountId()).append('\n');
        sb.append("Business date: ").append(ctx.businessDate()).append('\n');
        sb.append("Currency: ").append(ctx.tenant().getCurrency()).append('\n');
        sb.append("----------------------------------------\n");
        totalsByType.forEach((type, total) ->
                sb.append(String.format("%-10s %15s%n", type, total.setScale(2, RoundingMode.HALF_UP))));
        sb.append("----------------------------------------\n");
        sb.append("Taxable credits: ").append(credits.setScale(2, RoundingMode.HALF_UP)).append('\n');
        sb.append("Indicative withholding (30%): ").append(withholding).append('\n');

        String fileName = "%s_%s_%s_tax-summary.txt"
                .formatted(ctx.tenant().getTenantId(), ctx.accountId(), ctx.businessDate());
        return GeneratedReport.text(fileName, sb.toString());
    }
}
