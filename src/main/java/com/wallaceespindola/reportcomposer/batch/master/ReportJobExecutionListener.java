package com.wallaceespindola.reportcomposer.batch.master;

import com.wallaceespindola.reportcomposer.domain.ReportJobStatus;
import com.wallaceespindola.reportcomposer.repository.ReportJobRepository;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;

/** Keeps the report_job tracking row in sync with the Spring Batch JobExecution. */
@Slf4j
@RequiredArgsConstructor
public class ReportJobExecutionListener implements JobExecutionListener {

    private final ReportJobRepository reportJobRepository;

    @Override
    public void beforeJob(JobExecution jobExecution) {
        update(jobExecution, ReportJobStatus.STARTED);
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        ReportJobStatus status = jobExecution.getStatus() == BatchStatus.COMPLETED
                ? ReportJobStatus.COMPLETED
                : ReportJobStatus.FAILED;
        update(jobExecution, status);
    }

    private void update(JobExecution jobExecution, ReportJobStatus status) {
        String tenantId = jobExecution.getJobParameters().getString("tenantId");
        String reportType = jobExecution.getJobParameters().getString("reportType");
        LocalDate date = LocalDate.parse(jobExecution.getJobParameters().getString("businessDate"));
        reportJobRepository
                .findByTenantIdAndReportTypeAndBusinessDate(tenantId, reportType, date)
                .ifPresent(job -> {
                    job.setJobExecutionId(jobExecution.getId());
                    job.setStatus(status);
                    reportJobRepository.save(job);
                });
        log.info("report_job tenant={} type={} date={} -> {} (executionId={})",
                tenantId, reportType, date, status, jobExecution.getId());
    }
}
