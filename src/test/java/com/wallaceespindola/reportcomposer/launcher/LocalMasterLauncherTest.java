package com.wallaceespindola.reportcomposer.launcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.wallaceespindola.reportcomposer.TestFixtures;
import com.wallaceespindola.reportcomposer.api.exception.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;

@ExtendWith(MockitoExtension.class)
class LocalMasterLauncherTest {

    @Mock private JobLauncher jobLauncher;
    @Mock private Job reportJob;
    @Mock private JobExplorer jobExplorer;

    private LocalMasterLauncher launcher;

    @BeforeEach
    void setUp() {
        launcher = new LocalMasterLauncher(jobLauncher, reportJob, jobExplorer);
    }

    private static JobParameters params() {
        return new JobParametersBuilder()
                .addString("tenantId", "BE")
                .addString("reportType", "ACCOUNT_STATEMENT")
                .addString("businessDate", "2026-06-30")
                .toJobParameters();
    }

    @Test
    void launchReturnsExecutionId() throws Exception {
        JobExecution execution = new JobExecution(42L, params());
        when(jobLauncher.run(eq(reportJob), eq(params()))).thenReturn(execution);

        assertThat(launcher.launch("BE", "ACCOUNT_STATEMENT", TestFixtures.BUSINESS_DATE)).isEqualTo(42L);
    }

    @Test
    void alreadyRunningMapsToConflict() throws Exception {
        when(jobLauncher.run(any(), any()))
                .thenThrow(new JobExecutionAlreadyRunningException("running"));
        assertThatThrownBy(() -> launcher.launch("BE", "ACCOUNT_STATEMENT", TestFixtures.BUSINESS_DATE))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already running");
    }

    @Test
    void alreadyCompleteMapsToConflict() throws Exception {
        when(jobLauncher.run(any(), any()))
                .thenThrow(new JobInstanceAlreadyCompleteException("done"));
        assertThatThrownBy(() -> launcher.launch("BE", "ACCOUNT_STATEMENT", TestFixtures.BUSINESS_DATE))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already completed");
    }

    @Test
    void restartReusesOriginalJobParameters() throws Exception {
        JobExecution failed = new JobExecution(7L, params());
        when(jobExplorer.getJobExecution(7L)).thenReturn(failed);
        JobExecution restarted = new JobExecution(8L, params());
        when(jobLauncher.run(eq(reportJob), eq(params()))).thenReturn(restarted);

        assertThat(launcher.restart(7L)).isEqualTo(8L);
    }

    @Test
    void restartOfUnknownExecutionIsConflict() {
        when(jobExplorer.getJobExecution(99L)).thenReturn(null);
        assertThatThrownBy(() -> launcher.restart(99L)).isInstanceOf(ConflictException.class);
    }
}
