package com.wallaceespindola.reportcomposer.service;

import com.wallaceespindola.reportcomposer.config.AppProperties;
import com.wallaceespindola.reportcomposer.domain.Account;
import com.wallaceespindola.reportcomposer.repository.AccountRepository;
import com.wallaceespindola.reportcomposer.repository.TenantRepository;
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

    private final AccountRepository accountRepository;
    private final TenantRepository tenantRepository;
    private final MockDataService mockDataService;
    private final AppProperties props;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (accountRepository.count() > 0) {
            log.info("Seed skipped — accounts already present");
            return;
        }
        Random random = new Random(42); // deterministic demo data
        int[] totals = {0, 0};
        tenantRepository.findByEnabledTrue().forEach(tenant -> {
            List<Account> accounts =
                    mockDataService.createAccounts(tenant, props.seed().accountsPerTenant(), random);
            int transactions = mockDataService.generateTransactions(
                    tenant, accounts, props.seed().businessDate(), null, random);
            totals[0] += accounts.size();
            totals[1] += transactions;
        });
        log.info("Seeded {} accounts and {} transactions for business date {}",
                totals[0], totals[1], props.seed().businessDate());
    }
}
