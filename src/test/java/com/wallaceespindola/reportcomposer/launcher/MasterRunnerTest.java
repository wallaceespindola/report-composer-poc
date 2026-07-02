package com.wallaceespindola.reportcomposer.launcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.wallaceespindola.reportcomposer.TestFixtures;
import com.wallaceespindola.reportcomposer.config.AppProperties;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.context.ConfigurableApplicationContext;

@ExtendWith(MockitoExtension.class)
class MasterRunnerTest {

    @Mock private JobLauncher jobLauncher;
    @Mock private Job reportJob;
    @Mock private JobExplorer jobExplorer;
    @Mock private ConfigurableApplicationContext context;

    private static JobParameters params() {
        return new JobParametersBuilder()
                .addString("tenantId", "BE")
                .addString("reportType", "ACCOUNT_STATEMENT")
                .addString("businessDate", "2026-06-30")
                .toJobParameters();
    }

    private MasterRunner runner(AppProperties props, AtomicInteger exitCode) {
        MasterRunner runner = new MasterRunner(jobLauncher, reportJob, jobExplorer, props, context);
        runner.setExiter(exitCode::set);
        return runner;
    }

    @Test
    void runsJobFromEnvParamsAndExitsZeroOnSuccess() throws Exception {
        JobExecution execution = new JobExecution(1L, params());
        execution.setStatus(BatchStatus.COMPLETED);
        when(jobLauncher.run(eq(reportJob), eq(params()))).thenReturn(execution);

        AtomicInteger exitCode = new AtomicInteger(-1);
        runner(TestFixtures.appProperties("master", "local"), exitCode).run(null);

        assertThat(exitCode.get()).isZero();
    }

    @Test
    void exitsNonZeroOnFailedJob() throws Exception {
        JobExecution execution = new JobExecution(1L, params());
        execution.setStatus(BatchStatus.FAILED);
        when(jobLauncher.run(eq(reportJob), eq(params()))).thenReturn(execution);

        AtomicInteger exitCode = new AtomicInteger(-1);
        runner(TestFixtures.appProperties("master", "local"), exitCode).run(null);

        assertThat(exitCode.get()).isEqualTo(1);
    }

    @Test
    void restartModeLooksUpOriginalParameters() throws Exception {
        AppProperties props = new AppProperties(
                "master", "local", null, null, null, null,
                new AppProperties.Master("", "", "", "7"));
        JobExecution failed = new JobExecution(7L, params());
        when(jobExplorer.getJobExecution(7L)).thenReturn(failed);
        JobExecution restarted = new JobExecution(8L, params());
        restarted.setStatus(BatchStatus.COMPLETED);
        when(jobLauncher.run(eq(reportJob), eq(params()))).thenReturn(restarted);

        AtomicInteger exitCode = new AtomicInteger(-1);
        runner(props, exitCode).run(null);

        assertThat(exitCode.get()).isZero();
    }
}
