package com.wallaceespindola.reportcomposer.api;

import com.wallaceespindola.reportcomposer.api.dto.ApiDtos.ContractCreateRequest;
import com.wallaceespindola.reportcomposer.api.dto.ApiDtos.ContractCreateResponse;
import com.wallaceespindola.reportcomposer.api.dto.ApiDtos.TenantCreateRequest;
import com.wallaceespindola.reportcomposer.api.dto.ApiDtos.TenantCreateResponse;
import com.wallaceespindola.reportcomposer.api.dto.ApiDtos.TenantDto;
import com.wallaceespindola.reportcomposer.api.dto.ApiDtos.TenantListResponse;
import com.wallaceespindola.reportcomposer.api.dto.ApiDtos.TransactionGenRequest;
import com.wallaceespindola.reportcomposer.api.dto.ApiDtos.TransactionGenResponse;
import com.wallaceespindola.reportcomposer.repository.TenantReportContractRepository;
import com.wallaceespindola.reportcomposer.repository.TenantRepository;
import com.wallaceespindola.reportcomposer.service.AdminService;
import com.wallaceespindola.reportcomposer.strategy.ReportStrategyResolver;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantRepository tenantRepository;
    private final TenantReportContractRepository contractRepository;
    private final ReportStrategyResolver strategyResolver;
    private final AdminService adminService;

    /** Tenants with the report types they may run — contracted AND backed by a registered strategy. */
    @GetMapping
    public TenantListResponse tenants() {
        List<TenantDto> tenants = tenantRepository.findAll().stream()
                .map(tenant -> new TenantDto(
                        tenant.getTenantId(),
                        tenant.getCountryCode(),
                        tenant.getLocale(),
                        tenant.getCurrency(),
                        tenant.isEnabled(),
                        contractRepository.findByTenantIdAndEnabledTrue(tenant.getTenantId()).stream()
                                .map(c -> c.getReportType())
                                .filter(strategyResolver::isRegistered)
                                .sorted()
                                .toList()))
                .toList();
        return new TenantListResponse(Instant.now(), tenants);
    }

    /** Onboard a new tenant (country), optionally seeding mock accounts + transactions. */
    @PostMapping
    public ResponseEntity<TenantCreateResponse> createTenant(@Valid @RequestBody TenantCreateRequest request) {
        var created = adminService.createTenant(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new TenantCreateResponse(
                Instant.now(), created.tenantId(), created.accountsCreated(), created.transactionsCreated()));
    }

    /** Contract the tenant for a report type from the agreed catalog (PRD §3.7). */
    @PostMapping("/{tenantId}/contracts")
    public ResponseEntity<ContractCreateResponse> createContract(
            @PathVariable String tenantId, @Valid @RequestBody ContractCreateRequest request) {
        var contract = adminService.createContract(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ContractCreateResponse(
                Instant.now(), contract.getTenantId(), contract.getReportType(), contract.isEnabled()));
    }

    /** Generate random mock transactions for the tenant's existing accounts. */
    @PostMapping("/{tenantId}/transactions")
    public ResponseEntity<TransactionGenResponse> generateTransactions(
            @PathVariable String tenantId, @Valid @RequestBody TransactionGenRequest request) {
        var generated = adminService.generateTransactions(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new TransactionGenResponse(
                Instant.now(), tenantId, request.businessDate(),
                generated.accounts(), generated.transactionsCreated()));
    }
}
