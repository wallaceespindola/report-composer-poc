package com.wallaceespindola.reportcomposer.repository;

import com.wallaceespindola.reportcomposer.domain.ReportWorkUnit;
import com.wallaceespindola.reportcomposer.domain.WorkUnitStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportWorkUnitRepository extends JpaRepository<ReportWorkUnit, Long> {

    Optional<ReportWorkUnit> findByTenantIdAndAccountIdAndReportTypeAndBusinessDate(
            String tenantId, String accountId, String reportType, LocalDate businessDate);

    List<ReportWorkUnit> findByReportJobIdOrderByAccountId(Long reportJobId);

    long countByReportJobId(Long reportJobId);

    long countByReportJobIdAndStatus(Long reportJobId, WorkUnitStatus status);
}
