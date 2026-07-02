package com.wallaceespindola.reportcomposer.launcher;

import com.wallaceespindola.reportcomposer.config.AppProperties;
import java.util.function.IntConsumer;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * APP_ROLE=master entrypoint (k8s mode): runs the manager step synchronously for the
 * job key passed via env vars, then exits so the Kubernetes Job completes.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.role", havingValue = "master")
@RequiredArgsConstructor
public class MasterRunner implements ApplicationRunner {

    private final JobLauncher syncJobLauncher;
    private final Job reportJob;
    private final JobExplorer jobExplorer;
    private final AppProperties props;
    private final ConfigurableApplicationContext context;

    /** Overridable in tests — production exits the JVM so the k8s Job completes. */
    @Setter(AccessLevel.PACKAGE)
    private IntConsumer exiter;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        JobExecution execution;
        String restartId = props.master().restartExecutionId();
        if (StringUtils.hasText(restartId)) {
            JobExecution failed = jobExplorer.getJobExecution(Long.parseLong(restartId));
            if (failed == null) {
                throw new IllegalArgumentException("No JobExecution " + restartId + " to restart");
            }
            log.info("Restarting job execution {}", restartId);
            execution = syncJobLauncher.run(reportJob, failed.getJobParameters());
        } else {
            JobParameters params = new JobParametersBuilder()
                    .addString("tenantId", props.master().tenantId())
                    .addString("reportType", props.master().reportType())
                    .addString("businessDate", props.master().businessDate())
                    .toJobParameters();
            log.info("Master launching reportJob with {}", params);
            execution = syncJobLauncher.run(reportJob, params);
        }

        int exitCode = execution.getStatus() == BatchStatus.COMPLETED ? 0 : 1;
        log.info("Master finished with batch status {} -> exit {}", execution.getStatus(), exitCode);
        IntConsumer exit = exiter != null
                ? exiter
                : code -> System.exit(SpringApplication.exit(context, () -> code));
        exit.accept(exitCode);
    }
}
