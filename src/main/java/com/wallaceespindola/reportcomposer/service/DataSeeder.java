package com.wallaceespindola.reportcomposer.service;

import com.wallaceespindola.reportcomposer.config.AppProperties;
import com.wallaceespindola.reportcomposer.domain.Account;
import com.wallaceespindola.reportcomposer.domain.TransactionEntity;
import com.wallaceespindola.reportcomposer.repository.AccountRepository;
import com.wallaceespindola.reportcomposer.repository.TenantRepository;
import com.wallaceespindola.reportcomposer.repository.TransactionRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Auto-loads mock accounts and transactions on startup (PRD §3.5, FR-14) so a report
 * can be generated with a single API call. Idempotent: only seeds when the account
 * table is empty, and only in the api role so concurrently starting workers don't race.
 * Tenants and contracts are seeded by Flyway (V3); volume comes from
 * SEED_ACCOUNTS_PER_TENANT.
 */
@Slf4j
@Component
@ConditionalOnExpression("'${app.role}' == 'api' && ${app.seed.enabled}")
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    private static final String[] TXN_TYPES = {"CREDIT", "DEBIT", "FEE", "INTEREST"};
    private static final String[] FIRST_NAMES = {"Ana", "Bram", "Chloe", "Diego", "Emma", "Felix", "Greta", "Hugo"};
    private static final String[] LAST_NAMES = {"Peeters", "Dubois", "Garcia", "Janssens", "Moreau", "Lopez"};

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final TenantRepository tenantRepository;
    private final AppProperties props;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (accountRepository.count() > 0) {
            log.info("Seed skipped — accounts already present");
            return;
        }
        Random random = new Random(42); // deterministic demo data
        List<Account> accounts = new ArrayList<>();
        List<TransactionEntity> transactions = new ArrayList<>();

        tenantRepository.findByEnabledTrue().forEach(tenant -> {
            for (int i = 1; i <= props.seed().accountsPerTenant(); i++) {
                String accountId = "%s-ACC-%04d".formatted(tenant.getTenantId(), i);
                accounts.add(Account.builder()
                        .accountId(accountId)
                        .tenantId(tenant.getTenantId())
                        .customerName(FIRST_NAMES[random.nextInt(FIRST_NAMES.length)] + " "
                                + LAST_NAMES[random.nextInt(LAST_NAMES.length)])
                        .eligible(true)
                        .build());

                int txnCount = 3 + random.nextInt(6);
                for (int t = 0; t < txnCount; t++) {
                    String type = TXN_TYPES[random.nextInt(TXN_TYPES.length)];
                    BigDecimal amount = BigDecimal.valueOf(random.nextInt(500_000), 2);
                    if (type.equals("DEBIT") || type.equals("FEE")) {
                        amount = amount.negate();
                    }
                    transactions.add(TransactionEntity.builder()
                            .tenantId(tenant.getTenantId())
                            .accountId(accountId)
                            .businessDate(props.seed().businessDate())
                            .amount(amount)
                            .currency(tenant.getCurrency())
                            .txnType(type)
                            .description(type + " " + (t + 1))
                            .build());
                }
            }
        });

        accountRepository.saveAll(accounts);
        transactionRepository.saveAll(transactions);
        log.info("Seeded {} accounts and {} transactions for business date {}",
                accounts.size(), transactions.size(), props.seed().businessDate());
    }
}
