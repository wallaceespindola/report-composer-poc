package com.wallaceespindola.reportcomposer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wallaceespindola.reportcomposer.TestFixtures;
import com.wallaceespindola.reportcomposer.domain.Account;
import com.wallaceespindola.reportcomposer.domain.TransactionEntity;
import com.wallaceespindola.reportcomposer.repository.AccountRepository;
import com.wallaceespindola.reportcomposer.repository.TenantRepository;
import com.wallaceespindola.reportcomposer.repository.TransactionRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DataSeederTest {

    @Mock private AccountRepository accountRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private TenantRepository tenantRepository;

    @Test
    void skipsWhenAccountsExist() {
        when(accountRepository.count()).thenReturn(10L);
        new DataSeeder(accountRepository, transactionRepository, tenantRepository,
                TestFixtures.appProperties("api", "local"))
                .run(null);
        verify(accountRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void seedsConfiguredAccountsPerTenantWithTransactionsOnSeedDate() {
        when(accountRepository.count()).thenReturn(0L);
        when(tenantRepository.findByEnabledTrue()).thenReturn(List.of(TestFixtures.tenantBE()));

        new DataSeeder(accountRepository, transactionRepository, tenantRepository,
                TestFixtures.appProperties("api", "local")) // 5 accounts per tenant
                .run(null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Account>> accounts = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TransactionEntity>> txns = ArgumentCaptor.forClass(List.class);
        verify(accountRepository).saveAll(accounts.capture());
        verify(transactionRepository).saveAll(txns.capture());

        assertThat(accounts.getValue()).hasSize(5);
        assertThat(accounts.getValue()).allMatch(a -> a.getTenantId().equals("BE") && a.isEligible());
        assertThat(accounts.getValue().get(0).getAccountId()).isEqualTo("BE-ACC-0001");
        assertThat(txns.getValue()).isNotEmpty();
        assertThat(txns.getValue()).allMatch(t -> t.getBusinessDate().equals(TestFixtures.BUSINESS_DATE));
    }
}
