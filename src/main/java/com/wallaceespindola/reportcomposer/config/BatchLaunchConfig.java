package com.wallaceespindola.reportcomposer.config;

import com.wallaceespindola.reportcomposer.strategy.UnknownReportTypeException;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.retry.support.RetryTemplate;

@Configuration
public class BatchLaunchConfig {

    /** Synchronous launcher — used by the master runner (k8s Job waits for completion). */
    @Bean
    @Primary
    public JobLauncher syncJobLauncher(JobRepository jobRepository) throws Exception {
        TaskExecutorJobLauncher launcher = new TaskExecutorJobLauncher();
        launcher.setJobRepository(jobRepository);
        launcher.setTaskExecutor(new SyncTaskExecutor());
        launcher.afterPropertiesSet();
        return launcher;
    }

    /** Async launcher — the API returns the JobExecution id immediately (LAUNCHER_MODE=local). */
    @Bean
    public JobLauncher asyncJobLauncher(JobRepository jobRepository) throws Exception {
        TaskExecutorJobLauncher launcher = new TaskExecutorJobLauncher();
        launcher.setJobRepository(jobRepository);
        launcher.setTaskExecutor(new SimpleAsyncTaskExecutor("job-launcher-"));
        launcher.afterPropertiesSet();
        return launcher;
    }

    /** Bounded retry with backoff for transient worker failures (FR-8, PRD §15). */
    @Bean
    public RetryTemplate reportRetryTemplate() {
        return RetryTemplate.builder()
                .maxAttempts(3)
                .exponentialBackoff(500, 2.0, 5000)
                .notRetryOn(UnknownReportTypeException.class)
                .build();
    }
}
