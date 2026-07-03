package com.wallaceespindola.reportcomposer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wallaceespindola.reportcomposer.TestFixtures;
import com.wallaceespindola.reportcomposer.domain.Account;
import com.wallaceespindola.reportcomposer.domain.TransactionEntity;
import com.wallaceespindola.reportcomposer.repository.AccountRepository;
import com.wallaceespindola.reportcomposer.repository.TransactionRepository;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MockDataServiceTest {

    @Mock private AccountRepository accountRepository;
    @Mock private TransactionRepository transactionRepository;

    private MockDataService service;

    @BeforeEach
    void setUp() {
        service = new MockDataService(accountRepository, transactionRepository);
    }

    @Test
    void createAccountsNumbersAfterExistingOnes() {
        when(accountRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(accountRepository.countByTenantId("BE")).thenReturn(50L);

        List<Account> accounts = service.createAccounts(TestFixtures.tenantBE(), 3, new Random(1));

        assertThat(accounts).extracting(Account::getAccountId)
                .containsExactly("BE-ACC-0051", "BE-ACC-0052", "BE-ACC-0053");
        assertThat(accounts).allMatch(Account::isEligible);
    }

    @Test
    void generateTransactionsHonorsFixedPerAccountCount() {
        List<Account> accounts = List.of(
                TestFixtures.account("BE", "BE-ACC-0001"),
                TestFixtures.account("BE", "BE-ACC-0002"));

        int created = service.generateTransactions(
                TestFixtures.tenantBE(), accounts, TestFixtures.BUSINESS_DATE, 4, new Random(1));

        assertThat(created).isEqualTo(8);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TransactionEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(transactionRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(8);
        assertThat(captor.getValue()).allMatch(t -> t.getBusinessDate().equals(TestFixtures.BUSINESS_DATE));
        assertThat(captor.getValue()).allMatch(t -> t.getCurrency().equals("EUR"));
    }

    @Test
    void generateTransactionsRandomCountIsBetween3And8PerAccount() {
        List<Account> accounts = List.of(TestFixtures.account("BE", "BE-ACC-0001"));

        int created = service.generateTransactions(
                TestFixtures.tenantBE(), accounts, TestFixtures.BUSINESS_DATE, null, new Random(7));

        assertThat(created).isBetween(3, 8);
    }
}
