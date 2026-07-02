package com.wallaceespindola.reportcomposer.repository;

import com.wallaceespindola.reportcomposer.domain.ReportJob;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportJobRepository extends JpaRepository<ReportJob, Long> {

    Optional<ReportJob> findByTenantIdAndReportTypeAndBusinessDate(
            String tenantId, String reportType, LocalDate businessDate);

    Optional<ReportJob> findByJobExecutionId(Long jobExecutionId);
}
