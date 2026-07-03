package com.wallaceespindola.reportcomposer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wallaceespindola.reportcomposer.TestFixtures;
import com.wallaceespindola.reportcomposer.api.dto.ApiDtos.ContractCreateRequest;
import com.wallaceespindola.reportcomposer.api.dto.ApiDtos.TenantCreateRequest;
import com.wallaceespindola.reportcomposer.api.dto.ApiDtos.TransactionGenRequest;
import com.wallaceespindola.reportcomposer.api.exception.BadRequestException;
import com.wallaceespindola.reportcomposer.api.exception.ConflictException;
import com.wallaceespindola.reportcomposer.api.exception.NotFoundException;
import com.wallaceespindola.reportcomposer.domain.Tenant;
import com.wallaceespindola.reportcomposer.domain.TenantReportContract;
import com.wallaceespindola.reportcomposer.repository.AccountRepository;
import com.wallaceespindola.reportcomposer.repository.TenantReportContractRepository;
import com.wallaceespindola.reportcomposer.repository.TenantRepository;
import com.wallaceespindola.reportcomposer.strategy.ReportStrategyResolver;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private TenantReportContractRepository contractRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private ReportStrategyResolver strategyResolver;
    @Mock private MockDataService mockDataService;

    private AdminService service;

    @BeforeEach
    void setUp() {
        service = new AdminService(
                tenantRepository,
                contractRepository,
                accountRepository,
                strategyResolver,
                mockDataService,
                TestFixtures.appProperties("api", "local"));
    }

    @Test
    void createTenantRejectsDuplicates() {
        when(tenantRepository.existsById("BE")).thenReturn(true);
        assertThatThrownBy(() -> service.createTenant(
                        new TenantCreateRequest("BE", "BE", "nl-BE", "EUR", null, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createTenantSeedsAccountsAndTransactionsWhenRequested() {
        when(tenantRepository.existsById("NL")).thenReturn(false);
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mockDataService.createAccounts(any(), eq(10), any()))
                .thenReturn(List.of(TestFixtures.account("NL", "NL-ACC-0001")));
        when(mockDataService.generateTransactions(any(), anyList(), any(), any(), any())).thenReturn(55);

        var created = service.createTenant(new TenantCreateRequest("nl", "nl", "nl-NL", "eur", 10, null));

        assertThat(created.tenantId()).isEqualTo("NL");
        assertThat(created.accountsCreated()).isEqualTo(10);
        assertThat(created.transactionsCreated()).isEqualTo(55);
    }

    @Test
    void createTenantWithoutSeedingSkipsMockData() {
        when(tenantRepository.existsById("NL")).thenReturn(false);
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        var created = service.createTenant(new TenantCreateRequest("NL", "NL", "nl-NL", "EUR", null, null));

        assertThat(created.accountsCreated()).isZero();
        verify(mockDataService, never()).createAccounts(any(), anyInt(), any());
    }

    @Test
    void createContractRejectsUnknownTenant() {
        when(tenantRepository.findById("XX")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createContract("XX", new ContractCreateRequest("TAX_SUMMARY", null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void createContractRejectsUncataloguedReportType() {
        when(tenantRepository.findById("BE")).thenReturn(Optional.of(TestFixtures.tenantBE()));
        when(strategyResolver.isRegistered("NOPE")).thenReturn(false);
        assertThatThrownBy(() -> service.createContract("BE", new ContractCreateRequest("NOPE", null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("agreed catalog");
    }

    @Test
    void createContractRejectsDuplicates() {
        when(tenantRepository.findById("BE")).thenReturn(Optional.of(TestFixtures.tenantBE()));
        when(strategyResolver.isRegistered("TAX_SUMMARY")).thenReturn(true);
        when(contractRepository.findByTenantIdAndReportType("BE", "TAX_SUMMARY"))
                .thenReturn(Optional.of(TestFixtures.contract("BE", "TAX_SUMMARY")));
        assertThatThrownBy(() -> service.createContract("BE", new ContractCreateRequest("TAX_SUMMARY", null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createContractSavesEnabledContract() {
        when(tenantRepository.findById("BE")).thenReturn(Optional.of(TestFixtures.tenantBE()));
        when(strategyResolver.isRegistered("TAX_SUMMARY")).thenReturn(true);
        when(contractRepository.findByTenantIdAndReportType("BE", "TAX_SUMMARY")).thenReturn(Optional.empty());
        when(contractRepository.save(any(TenantReportContract.class))).thenAnswer(inv -> inv.getArgument(0));

        TenantReportContract contract = service.createContract("BE", new ContractCreateRequest("TAX_SUMMARY", null));

        assertThat(contract.isEnabled()).isTrue();
        assertThat(contract.getReportType()).isEqualTo("TAX_SUMMARY");
    }

    @Test
    void generateTransactionsRequiresAccounts() {
        when(tenantRepository.findById("BE")).thenReturn(Optional.of(TestFixtures.tenantBE()));
        when(accountRepository.findByTenantIdAndEligibleTrueOrderByAccountId("BE")).thenReturn(List.of());
        assertThatThrownBy(() -> service.generateTransactions(
                        "BE", new TransactionGenRequest(TestFixtures.BUSINESS_DATE, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("no eligible accounts");
    }

    @Test
    void generateTransactionsValidatesPerAccountBounds() {
        when(tenantRepository.findById("BE")).thenReturn(Optional.of(TestFixtures.tenantBE()));
        when(accountRepository.findByTenantIdAndEligibleTrueOrderByAccountId("BE"))
                .thenReturn(List.of(TestFixtures.account("BE", "BE-ACC-0001")));
        assertThatThrownBy(() -> service.generateTransactions(
                        "BE", new TransactionGenRequest(TestFixtures.BUSINESS_DATE, 500)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void generateTransactionsDelegatesToMockData() {
        when(tenantRepository.findById("BE")).thenReturn(Optional.of(TestFixtures.tenantBE()));
        when(accountRepository.findByTenantIdAndEligibleTrueOrderByAccountId("BE"))
                .thenReturn(List.of(TestFixtures.account("BE", "BE-ACC-0001")));
        when(mockDataService.generateTransactions(any(), anyList(), eq(TestFixtures.BUSINESS_DATE), eq(5), any()))
                .thenReturn(5);

        var generated = service.generateTransactions(
                "BE", new TransactionGenRequest(TestFixtures.BUSINESS_DATE, 5));

        assertThat(generated.accounts()).isEqualTo(1);
        assertThat(generated.transactionsCreated()).isEqualTo(5);
    }
}
