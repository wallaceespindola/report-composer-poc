package com.wallaceespindola.reportcomposer.service;

import com.wallaceespindola.reportcomposer.api.dto.ApiDtos.ContractCreateRequest;
import com.wallaceespindola.reportcomposer.api.dto.ApiDtos.TenantCreateRequest;
import com.wallaceespindola.reportcomposer.api.dto.ApiDtos.TransactionGenRequest;
import com.wallaceespindola.reportcomposer.api.exception.BadRequestException;
import com.wallaceespindola.reportcomposer.api.exception.ConflictException;
import com.wallaceespindola.reportcomposer.api.exception.NotFoundException;
import com.wallaceespindola.reportcomposer.config.AppProperties;
import com.wallaceespindola.reportcomposer.domain.Account;
import com.wallaceespindola.reportcomposer.domain.Tenant;
import com.wallaceespindola.reportcomposer.domain.TenantReportContract;
import com.wallaceespindola.reportcomposer.repository.AccountRepository;
import com.wallaceespindola.reportcomposer.repository.TenantReportContractRepository;
import com.wallaceespindola.reportcomposer.repository.TenantRepository;
import com.wallaceespindola.reportcomposer.strategy.ReportStrategyResolver;
import java.time.LocalDate;
import java.util.List;
import java.util.Random;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * POC admin operations: onboard tenants and contracts (PRD §3.7 — DB config only)
 * and generate random mock transactions for existing tenants.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final TenantRepository tenantRepository;
    private final TenantReportContractRepository contractRepository;
    private final AccountRepository accountRepository;
    private final ReportStrategyResolver strategyResolver;
    private final MockDataService mockDataService;
    private final AppProperties props;

    private final Random random = new Random();

    public record TenantCreated(String tenantId, int accountsCreated, int transactionsCreated) {}

    @Transactional
    public TenantCreated createTenant(TenantCreateRequest request) {
        String tenantId = request.tenantId().toUpperCase();
        if (tenantRepository.existsById(tenantId)) {
            throw new ConflictException("Tenant '" + tenantId + "' already exists");
        }
        Tenant tenant = tenantRepository.save(Tenant.builder()
                .tenantId(tenantId)
                .countryCode(request.countryCode().toUpperCase())
                .locale(request.locale())
                .currency(request.currency().toUpperCase())
                .enabled(true)
                .configJson("{}")
                .build());

        int accounts = request.seedAccounts() == null ? 0 : request.seedAccounts();
        int transactions = 0;
        if (accounts > 0) {
            LocalDate date = request.businessDate() != null ? request.businessDate() : props.seed().businessDate();
            List<Account> created = mockDataService.createAccounts(tenant, accounts, random);
            transactions = mockDataService.generateTransactions(tenant, created, date, null, random);
        }
        log.info("Onboarded tenant {} with {} accounts / {} transactions",
                tenant.getTenantId(), accounts, transactions);
        return new TenantCreated(tenant.getTenantId(), accounts, transactions);
    }

    @Transactional
    public TenantReportContract createContract(String tenantId, ContractCreateRequest request) {
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new NotFoundException("Unknown tenant '" + tenantId + "'"));
        if (!strategyResolver.isRegistered(request.reportType())) {
            throw new BadRequestException("Report type '" + request.reportType()
                    + "' is not in the agreed catalog (no registered strategy)");
        }
        contractRepository.findByTenantIdAndReportType(tenantId, request.reportType()).ifPresent(c -> {
            throw new ConflictException(
                    "Tenant '" + tenantId + "' already has a contract for '" + request.reportType() + "'");
        });
        TenantReportContract contract = contractRepository.save(TenantReportContract.builder()
                .tenantId(tenantId)
                .reportType(request.reportType())
                .enabled(true)
                .effectiveFrom(request.effectiveFrom())
                .paramsJson("{\"outputFormat\":\"TXT\"}")
                .build());
        log.info("Contracted tenant {} for report type {}", tenantId, request.reportType());
        return contract;
    }

    public record TransactionsGenerated(int accounts, int transactionsCreated) {}

    @Transactional
    public TransactionsGenerated generateTransactions(String tenantId, TransactionGenRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new NotFoundException("Unknown tenant '" + tenantId + "'"));
        List<Account> accounts = accountRepository.findByTenantIdAndEligibleTrueOrderByAccountId(tenantId);
        if (accounts.isEmpty()) {
            throw new ConflictException("Tenant '" + tenantId + "' has no eligible accounts to transact on");
        }
        if (request.perAccount() != null && (request.perAccount() < 1 || request.perAccount() > 100)) {
            throw new BadRequestException("perAccount must be between 1 and 100");
        }
        int created = mockDataService.generateTransactions(
                tenant, accounts, request.businessDate(), request.perAccount(), random);
        log.info("Generated {} transactions for tenant {} on {}", created, tenantId, request.businessDate());
        return new TransactionsGenerated(accounts.size(), created);
    }
}
