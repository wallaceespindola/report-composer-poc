package com.wallaceespindola.reportcomposer.strategy;

import com.wallaceespindola.reportcomposer.domain.TransactionEntity;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Daily account statement: one line per transaction plus a closing balance. */
@Component
public class AccountStatementStrategy implements ReportTypeStrategy {

    public static final String TYPE = "ACCOUNT_STATEMENT";

    @Override
    public String supports() {
        return TYPE;
    }

    @Override
    public String description() {
        return "Daily account statement listing all transactions with a closing balance";
    }

    @Override
    public GeneratedReport generate(ReportContext ctx, List<TransactionEntity> transactions) {
        Locale locale = Locale.forLanguageTag(ctx.tenant().getLocale());
        NumberFormat money = NumberFormat.getCurrencyInstance(locale);
        money.setCurrency(Currency.getInstance(ctx.tenant().getCurrency()));

        StringBuilder sb = new StringBuilder();
        sb.append("ACCOUNT STATEMENT\n");
        sb.append("Tenant: ").append(ctx.tenant().getTenantId())
                .append(" (").append(ctx.tenant().getCountryCode()).append(")\n");
        sb.append("Account: ").append(ctx.accountId()).append('\n');
        sb.append("Business date: ").append(ctx.businessDate()).append('\n');
        sb.append("----------------------------------------\n");

        BigDecimal balance = BigDecimal.ZERO;
        for (TransactionEntity txn : transactions) {
            balance = balance.add(txn.getAmount());
            sb.append(String.format("%-10s %15s  %s%n",
                    txn.getTxnType(), money.format(txn.getAmount()),
                    txn.getDescription() == null ? "" : txn.getDescription()));
        }

        sb.append("----------------------------------------\n");
        sb.append("Transactions: ").append(transactions.size()).append('\n');
        sb.append("Closing balance: ").append(money.format(balance)).append('\n');

        String fileName = "%s_%s_%s_statement.txt"
                .formatted(ctx.tenant().getTenantId(), ctx.accountId(), ctx.businessDate());
        return GeneratedReport.text(fileName, sb.toString());
    }
}
