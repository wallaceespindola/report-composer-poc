package com.wallaceespindola.reportcomposer.api;

import com.wallaceespindola.reportcomposer.api.dto.ApiDtos.TenantDto;
import com.wallaceespindola.reportcomposer.api.dto.ApiDtos.TenantListResponse;
import com.wallaceespindola.reportcomposer.repository.TenantReportContractRepository;
import com.wallaceespindola.reportcomposer.repository.TenantRepository;
import com.wallaceespindola.reportcomposer.strategy.ReportStrategyResolver;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantRepository tenantRepository;
    private final TenantReportContractRepository contractRepository;
    private final ReportStrategyResolver strategyResolver;

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
}
