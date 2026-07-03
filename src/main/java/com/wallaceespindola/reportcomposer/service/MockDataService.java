package com.wallaceespindola.reportcomposer.service;

import com.wallaceespindola.reportcomposer.domain.Account;
import com.wallaceespindola.reportcomposer.domain.Tenant;
import com.wallaceespindola.reportcomposer.domain.TransactionEntity;
import com.wallaceespindola.reportcomposer.repository.AccountRepository;
import com.wallaceespindola.reportcomposer.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Random mock-data generation, shared by the startup seeder and the admin API. */
@Service
@RequiredArgsConstructor
public class MockDataService {

    private static final String[] TXN_TYPES = {"CREDIT", "DEBIT", "FEE", "INTEREST"};
    private static final String[] FIRST_NAMES = {"Ana", "Bram", "Chloe", "Diego", "Emma", "Felix", "Greta", "Hugo"};
    private static final String[] LAST_NAMES = {"Peeters", "Dubois", "Garcia", "Janssens", "Moreau", "Lopez"};

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    /** Creates {@code count} eligible accounts, numbering after the tenant's existing ones. */
    @Transactional
    public List<Account> createAccounts(Tenant tenant, int count, Random random) {
        long existing = accountRepository.countByTenantId(tenant.getTenantId());
        List<Account> accounts = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            accounts.add(Account.builder()
                    .accountId("%s-ACC-%04d".formatted(tenant.getTenantId(), existing + i))
                    .tenantId(tenant.getTenantId())
                    .customerName(FIRST_NAMES[random.nextInt(FIRST_NAMES.length)] + " "
                            + LAST_NAMES[random.nextInt(LAST_NAMES.length)])
                    .eligible(true)
                    .build());
        }
        return accountRepository.saveAll(accounts);
    }

    /**
     * Random transactions on {@code businessDate} for the given accounts.
     * {@code perAccount} null means 3..8 random per account.
     */
    @Transactional
    public int generateTransactions(Tenant tenant, List<Account> accounts, LocalDate businessDate,
            Integer perAccount, Random random) {
        List<TransactionEntity> transactions = new ArrayList<>();
        for (Account account : accounts) {
            int txnCount = perAccount != null ? perAccount : 3 + random.nextInt(6);
            for (int t = 0; t < txnCount; t++) {
                String type = TXN_TYPES[random.nextInt(TXN_TYPES.length)];
                BigDecimal amount = BigDecimal.valueOf(random.nextInt(500_000), 2);
                if (type.equals("DEBIT") || type.equals("FEE")) {
                    amount = amount.negate();
                }
                transactions.add(TransactionEntity.builder()
                        .tenantId(tenant.getTenantId())
                        .accountId(account.getAccountId())
                        .businessDate(businessDate)
                        .amount(amount)
                        .currency(tenant.getCurrency())
                        .txnType(type)
                        .description(type + " " + (t + 1))
                        .build());
            }
        }
        transactionRepository.saveAll(transactions);
        return transactions.size();
    }
}
