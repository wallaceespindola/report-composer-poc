package com.wallaceespindola.reportcomposer.launcher;

import com.wallaceespindola.reportcomposer.api.exception.ConflictException;
import java.time.LocalDate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/** Runs the manager step in-process in the API service — the Compose / single-machine path. */
@Slf4j
@Component
@ConditionalOnExpression("'${app.role}' == 'api' && '${app.launcher}' == 'local'")
public class LocalMasterLauncher implements MasterLauncher {

    private final JobLauncher asyncJobLauncher;
    private final Job reportJob;
    private final JobExplorer jobExplorer;

    public LocalMasterLauncher(
            @Qualifier("asyncJobLauncher") JobLauncher asyncJobLauncher, Job reportJob, JobExplorer jobExplorer) {
        this.asyncJobLauncher = asyncJobLauncher;
        this.reportJob = reportJob;
        this.jobExplorer = jobExplorer;
    }

    @Override
    public Long launch(String tenantId, String reportType, LocalDate businessDate) {
        JobParameters params = new JobParametersBuilder()
                .addString("tenantId", tenantId)
                .addString("reportType", reportType)
                .addString("businessDate", businessDate.toString())
                .toJobParameters();
        return run(params);
    }

    @Override
    public Long restart(long jobExecutionId) {
        JobExecution execution = jobExplorer.getJobExecution(jobExecutionId);
        if (execution == null) {
            throw new ConflictException("No JobExecution " + jobExecutionId + " to restart");
        }
        // launching with the same identifying parameters after a failure IS a Spring
        // Batch restart: completed partitions are skipped, failed ones re-run
        return run(execution.getJobParameters());
    }

    private Long run(JobParameters params) {
        try {
            JobExecution execution = asyncJobLauncher.run(reportJob, params);
            log.info("Launched reportJob execution {} with params {}", execution.getId(), params);
            return execution.getId();
        } catch (JobExecutionAlreadyRunningException e) {
            throw new ConflictException("A job for this (tenant, reportType, businessDate) is already running");
        } catch (JobInstanceAlreadyCompleteException e) {
            throw new ConflictException("This (tenant, reportType, businessDate) already completed successfully");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to launch job", e);
        }
    }
}
