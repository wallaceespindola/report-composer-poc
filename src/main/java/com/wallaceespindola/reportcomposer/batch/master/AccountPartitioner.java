package com.wallaceespindola.reportcomposer.batch.master;

import com.wallaceespindola.reportcomposer.domain.Account;
import com.wallaceespindola.reportcomposer.domain.ReportJob;
import com.wallaceespindola.reportcomposer.domain.ReportJobStatus;
import com.wallaceespindola.reportcomposer.domain.ReportWorkUnit;
import com.wallaceespindola.reportcomposer.domain.WorkUnitStatus;
import com.wallaceespindola.reportcomposer.repository.AccountRepository;
import com.wallaceespindola.reportcomposer.repository.ReportJobRepository;
import com.wallaceespindola.reportcomposer.repository.ReportWorkUnitRepository;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.transaction.annotation.Transactional;

/**
 * Discovers eligible accounts for the job key and emits exactly one partition per
 * account (FR-2/FR-3). Also materializes the report_job / report_work_unit tracking
 * rows idempotently, so a restart never duplicates work units.
 */
@Slf4j
@RequiredArgsConstructor
public class AccountPartitioner implements Partitioner {

    public static final String CTX_TENANT = "tenantId";
    public static final String CTX_ACCOUNT = "accountId";
    public static final String CTX_REPORT_TYPE = "reportType";
    public static final String CTX_BUSINESS_DATE = "businessDate";

    private final AccountRepository accountRepository;
    private final ReportJobRepository reportJobRepository;
    private final ReportWorkUnitRepository workUnitRepository;

    private final String tenantId;
    private final String reportType;
    private final String businessDate;

    @Override
    @Transactional
    public Map<String, ExecutionContext> partition(int gridSize) {
        LocalDate date = LocalDate.parse(businessDate);
        ReportJob job = reportJobRepository
                .findByTenantIdAndReportTypeAndBusinessDate(tenantId, reportType, date)
                .orElseGet(() -> reportJobRepository.save(ReportJob.builder()
                        .tenantId(tenantId)
                        .reportType(reportType)
                        .businessDate(date)
                        .status(ReportJobStatus.STARTED)
                        .build()));

        List<Account> accounts = accountRepository.findByTenantIdAndEligibleTrueOrderByAccountId(tenantId);
        Map<String, ExecutionContext> partitions = new HashMap<>();
        for (Account account : accounts) {
            workUnitRepository
                    .findByTenantIdAndAccountIdAndReportTypeAndBusinessDate(
                            tenantId, account.getAccountId(), reportType, date)
                    .orElseGet(() -> workUnitRepository.save(ReportWorkUnit.builder()
                            .reportJobId(job.getId())
                            .tenantId(tenantId)
                            .accountId(account.getAccountId())
                            .reportType(reportType)
                            .businessDate(date)
                            .status(WorkUnitStatus.PENDING)
                            .build()));

            ExecutionContext context = new ExecutionContext();
            context.putString(CTX_TENANT, tenantId);
            context.putString(CTX_ACCOUNT, account.getAccountId());
            context.putString(CTX_REPORT_TYPE, reportType);
            context.putString(CTX_BUSINESS_DATE, businessDate);
            // stable partition name per account so Spring Batch restart matches
            // completed step executions and skips them
            partitions.put("partition-" + account.getAccountId(), context);
        }

        log.info("Partitioned job tenant={} type={} date={} into {} account partitions",
                tenantId, reportType, businessDate, partitions.size());
        return partitions;
    }
}
