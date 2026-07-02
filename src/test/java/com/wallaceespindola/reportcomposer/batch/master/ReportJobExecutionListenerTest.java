package com.wallaceespindola.reportcomposer.batch.master;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.wallaceespindola.reportcomposer.TestFixtures;
import com.wallaceespindola.reportcomposer.domain.ReportJob;
import com.wallaceespindola.reportcomposer.domain.ReportJobStatus;
import com.wallaceespindola.reportcomposer.repository.ReportJobRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;

@ExtendWith(MockitoExtension.class)
class ReportJobExecutionListenerTest {

    @Mock private ReportJobRepository reportJobRepository;

    private ReportJobExecutionListener listener;
    private ReportJob job;
    private JobExecution execution;

    @BeforeEach
    void setUp() {
        listener = new ReportJobExecutionListener(reportJobRepository);
        job = ReportJob.builder()
                .id(1L)
                .tenantId("BE")
                .reportType("ACCOUNT_STATEMENT")
                .businessDate(TestFixtures.BUSINESS_DATE)
                .status(ReportJobStatus.REQUESTED)
                .build();
        execution = new JobExecution(42L, new JobParametersBuilder()
                .addString("tenantId", "BE")
                .addString("reportType", "ACCOUNT_STATEMENT")
                .addString("businessDate", "2026-06-30")
                .toJobParameters());
        when(reportJobRepository.findByTenantIdAndReportTypeAndBusinessDate(
                        "BE", "ACCOUNT_STATEMENT", TestFixtures.BUSINESS_DATE))
                .thenReturn(Optional.of(job));
    }

    @Test
    void beforeJobLinksExecutionIdAndMarksStarted() {
        listener.beforeJob(execution);
        assertThat(job.getJobExecutionId()).isEqualTo(42L);
        assertThat(job.getStatus()).isEqualTo(ReportJobStatus.STARTED);
    }

    @Test
    void afterJobMapsCompletedStatus() {
        execution.setStatus(BatchStatus.COMPLETED);
        listener.afterJob(execution);
        assertThat(job.getStatus()).isEqualTo(ReportJobStatus.COMPLETED);
    }

    @Test
    void afterJobMapsFailedStatus() {
        execution.setStatus(BatchStatus.FAILED);
        listener.afterJob(execution);
        assertThat(job.getStatus()).isEqualTo(ReportJobStatus.FAILED);
    }
}
