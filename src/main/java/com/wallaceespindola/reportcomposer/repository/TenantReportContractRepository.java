package com.wallaceespindola.reportcomposer.repository;

import com.wallaceespindola.reportcomposer.domain.TenantReportContract;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantReportContractRepository extends JpaRepository<TenantReportContract, Long> {

    Optional<TenantReportContract> findByTenantIdAndReportType(String tenantId, String reportType);

    List<TenantReportContract> findByTenantIdAndEnabledTrue(String tenantId);
}
