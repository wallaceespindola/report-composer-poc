package com.wallaceespindola.reportcomposer.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wallaceespindola.reportcomposer.TestFixtures;
import com.wallaceespindola.reportcomposer.repository.AccountRepository;
import com.wallaceespindola.reportcomposer.repository.TenantRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DataSeederTest {

    @Mock private AccountRepository accountRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private MockDataService mockDataService;

    @Test
    void skipsWhenAccountsExist() {
        when(accountRepository.count()).thenReturn(10L);
        new DataSeeder(accountRepository, tenantRepository, mockDataService,
                TestFixtures.appProperties("api", "local"))
                .run(null);
        verify(mockDataService, never()).createAccounts(any(), anyInt(), any());
    }

    @Test
    void seedsAccountsAndTransactionsPerEnabledTenant() {
        when(accountRepository.count()).thenReturn(0L);
        var tenant = TestFixtures.tenantBE();
        when(tenantRepository.findByEnabledTrue()).thenReturn(List.of(tenant));
        var accounts = List.of(TestFixtures.account("BE", "BE-ACC-0001"));
        when(mockDataService.createAccounts(eq(tenant), eq(5), any())).thenReturn(accounts); // 5 per TestFixtures
        when(mockDataService.generateTransactions(
                        eq(tenant), eq(accounts), eq(TestFixtures.BUSINESS_DATE), eq(null), any()))
                .thenReturn(20);

        new DataSeeder(accountRepository, tenantRepository, mockDataService,
                TestFixtures.appProperties("api", "local"))
                .run(null);

        verify(mockDataService).createAccounts(eq(tenant), eq(5), any());
        verify(mockDataService).generateTransactions(
                eq(tenant), eq(accounts), eq(TestFixtures.BUSINESS_DATE), eq(null), any());
    }
}
