package com.wallaceespindola.reportcomposer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wallaceespindola.reportcomposer.TestFixtures;
import com.wallaceespindola.reportcomposer.api.exception.BadRequestException;
import com.wallaceespindola.reportcomposer.api.exception.ConflictException;
import com.wallaceespindola.reportcomposer.api.exception.NotFoundException;
import com.wallaceespindola.reportcomposer.domain.ReportJob;
import com.wallaceespindola.reportcomposer.domain.ReportJobStatus;
import com.wallaceespindola.reportcomposer.domain.ReportWorkUnit;
import com.wallaceespindola.reportcomposer.domain.Tenant;
import com.wallaceespindola.reportcomposer.domain.WorkUnitStatus;
import com.wallaceespindola.reportcomposer.launcher.MasterLauncher;
import com.wallaceespindola.reportcomposer.repository.ReportArtifactRepository;
import com.wallaceespindola.reportcomposer.repository.ReportJobRepository;
import com.wallaceespindola.reportcomposer.repository.ReportWorkUnitRepository;
import com.wallaceespindola.reportcomposer.repository.TenantReportContractRepository;
import com.wallaceespindola.reportcomposer.repository.TenantRepository;
import com.wallaceespindola.reportcomposer.strategy.ReportStrategyResolver;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    private static final LocalDate DATE = TestFixtures.BUSINESS_DATE;

    @Mock private TenantRepository tenantRepository;
    @Mock private TenantReportContractRepository contractRepository;
    @Mock private ReportStrategyResolver strategyResolver;
    @Mock private ReportJobRepository reportJobRepository;
    @Mock private ReportWorkUnitRepository workUnitRepository;
    @Mock private ReportArtifactRepository artifactRepository;
    @Mock private ObjectProvider<MasterLauncher> launcherProvider;
    @Mock private MasterLauncher launcher;

    private JobService service;

    @BeforeEach
    void setUp() {
        service = new JobService(
                tenantRepository,
                contractRepository,
                strategyResolver,
                reportJobRepository,
                workUnitRepository,
                artifactRepository,
                launcherProvider);
    }

    private void stubValidTenantAndContract() {
        when(tenantRepository.findById("BE")).thenReturn(Optional.of(TestFixtures.tenantBE()));
        when(strategyResolver.isRegistered("ACCOUNT_STATEMENT")).thenReturn(true);
        when(contractRepository.findByTenantIdAndReportType("BE", "ACCOUNT_STATEMENT"))
                .thenReturn(Optional.of(TestFixtures.contract("BE", "ACCOUNT_STATEMENT")));
    }

    @Test
    void startRejectsUnknownTenant() {
        when(tenantRepository.findById("XX")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.start("XX", "ACCOUNT_STATEMENT", DATE))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("XX");
    }

    @Test
    void startRejectsDisabledTenant() {
        Tenant disabled = TestFixtures.tenantBE();
        disabled.setEnabled(false);
        when(tenantRepository.findById("BE")).thenReturn(Optional.of(disabled));
        assertThatThrownBy(() -> service.start("BE", "ACCOUNT_STATEMENT", DATE))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void startRejectsUnregisteredReportType() {
        when(tenantRepository.findById("BE")).thenReturn(Optional.of(TestFixtures.tenantBE()));
        when(strategyResolver.isRegistered("NOPE")).thenReturn(false);
        assertThatThrownBy(() -> service.start("BE", "NOPE", DATE))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("no registered strategy");
    }

    @Test
    void startRejectsMissingContract() {
        when(tenantRepository.findById("BE")).thenReturn(Optional.of(TestFixtures.tenantBE()));
        when(strategyResolver.isRegistered("TAX_SUMMARY")).thenReturn(true);
        when(contractRepository.findByTenantIdAndReportType("BE", "TAX_SUMMARY")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.start("BE", "TAX_SUMMARY", DATE))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("no contract");
    }

    @Test
    void startRejectsInactiveContract() {
        when(tenantRepository.findById("BE")).thenReturn(Optional.of(TestFixtures.tenantBE()));
        when(strategyResolver.isRegistered("ACCOUNT_STATEMENT")).thenReturn(true);
        var contract = TestFixtures.contract("BE", "ACCOUNT_STATEMENT");
        contract.setEffectiveFrom(DATE.plusDays(1));
        when(contractRepository.findByTenantIdAndReportType("BE", "ACCOUNT_STATEMENT"))
                .thenReturn(Optional.of(contract));
        assertThatThrownBy(() -> service.start("BE", "ACCOUNT_STATEMENT", DATE))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not active");
    }

    @Test
    void startRejectsInFlightDuplicate() {
        stubValidTenantAndContract();
        when(reportJobRepository.findByTenantIdAndReportTypeAndBusinessDate("BE", "ACCOUNT_STATEMENT", DATE))
                .thenReturn(Optional.of(job(ReportJobStatus.STARTED)));
        assertThatThrownBy(() -> service.start("BE", "ACCOUNT_STATEMENT", DATE))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("in flight");
    }

    @Test
    void startRejectsAlreadyCompleted() {
        stubValidTenantAndContract();
        when(reportJobRepository.findByTenantIdAndReportTypeAndBusinessDate("BE", "ACCOUNT_STATEMENT", DATE))
                .thenReturn(Optional.of(job(ReportJobStatus.COMPLETED)));
        assertThatThrownBy(() -> service.start("BE", "ACCOUNT_STATEMENT", DATE))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already completed");
    }

    @Test
    void startLaunchesAndStoresExecutionId() {
        stubValidTenantAndContract();
        when(reportJobRepository.findByTenantIdAndReportTypeAndBusinessDate("BE", "ACCOUNT_STATEMENT", DATE))
                .thenReturn(Optional.empty());
        when(reportJobRepository.save(any(ReportJob.class))).thenAnswer(inv -> inv.getArgument(0));
        when(launcherProvider.getIfAvailable()).thenReturn(launcher);
        when(launcher.launch("BE", "ACCOUNT_STATEMENT", DATE)).thenReturn(42L);

        assertThat(service.start("BE", "ACCOUNT_STATEMENT", DATE)).isEqualTo(42L);
        verify(launcher).launch("BE", "ACCOUNT_STATEMENT", DATE);
    }

    @Test
    void startMarksJobFailedWhenLauncherBlowsUp() {
        stubValidTenantAndContract();
        when(reportJobRepository.findByTenantIdAndReportTypeAndBusinessDate("BE", "ACCOUNT_STATEMENT", DATE))
                .thenReturn(Optional.empty());
        when(reportJobRepository.save(any(ReportJob.class))).thenAnswer(inv -> inv.getArgument(0));
        when(launcherProvider.getIfAvailable()).thenReturn(launcher);
        when(launcher.launch("BE", "ACCOUNT_STATEMENT", DATE)).thenThrow(new IllegalStateException("kafka down"));

        assertThatThrownBy(() -> service.start("BE", "ACCOUNT_STATEMENT", DATE))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void restartOnlyAllowedForFailedJobs() {
        when(reportJobRepository.findByJobExecutionId(7L)).thenReturn(Optional.of(job(ReportJobStatus.STARTED)));
        assertThatThrownBy(() -> service.restart(7L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Only FAILED");
    }

    @Test
    void restartRelaunchesFailedJob() {
        ReportJob failed = job(ReportJobStatus.FAILED);
        when(reportJobRepository.findByJobExecutionId(7L)).thenReturn(Optional.of(failed));
        when(reportJobRepository.save(any(ReportJob.class))).thenAnswer(inv -> inv.getArgument(0));
        when(launcherProvider.getIfAvailable()).thenReturn(launcher);
        when(launcher.restart(7L)).thenReturn(8L);

        assertThat(service.restart(7L)).isEqualTo(8L);
        assertThat(failed.getJobExecutionId()).isEqualTo(8L);
    }

    @Test
    void getReturnsSummaryWithPartitionCounts() {
        ReportJob job = job(ReportJobStatus.COMPLETED);
        when(reportJobRepository.findByJobExecutionId(7L)).thenReturn(Optional.of(job));
        when(workUnitRepository.countByReportJobId(1L)).thenReturn(50L);
        when(workUnitRepository.countByReportJobIdAndStatus(1L, WorkUnitStatus.COMPLETED)).thenReturn(48L);
        when(workUnitRepository.countByReportJobIdAndStatus(1L, WorkUnitStatus.FAILED)).thenReturn(2L);

        var summary = service.get(7L);
        assertThat(summary.partitionsTotal()).isEqualTo(50);
        assertThat(summary.partitionsCompleted()).isEqualTo(48);
        assertThat(summary.partitionsFailed()).isEqualTo(2);
        assertThat(summary.endTime()).isNotNull();
    }

    @Test
    void getUnknownExecutionIs404() {
        when(reportJobRepository.findByJobExecutionId(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(99L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void listFiltersByTenantAndStatus() {
        ReportJob beJob = job(ReportJobStatus.COMPLETED);
        ReportJob frJob = job(ReportJobStatus.FAILED);
        frJob.setId(2L);
        frJob.setTenantId("FR");
        when(reportJobRepository.findAll()).thenReturn(List.of(beJob, frJob));
        when(workUnitRepository.countByReportJobId(anyLong())).thenReturn(0L);
        when(workUnitRepository.countByReportJobIdAndStatus(anyLong(), any())).thenReturn(0L);

        assertThat(service.list("BE", null, null, null)).hasSize(1);
        assertThat(service.list(null, null, null, "failed")).hasSize(1);
        assertThat(service.list(null, null, null, null)).hasSize(2);
        assertThat(service.list(null, null, DATE.plusDays(1), null)).isEmpty();
    }

    @Test
    void partitionsMapsWorkUnits() {
        ReportJob job = job(ReportJobStatus.STARTED);
        when(reportJobRepository.findByJobExecutionId(7L)).thenReturn(Optional.of(job));
        ReportWorkUnit unit = ReportWorkUnit.builder()
                .id(11L)
                .reportJobId(1L)
                .tenantId("BE")
                .accountId("BE-ACC-0001")
                .reportType("ACCOUNT_STATEMENT")
                .businessDate(DATE)
                .status(WorkUnitStatus.COMPLETED)
                .attemptCount(1)
                .build();
        when(workUnitRepository.findByReportJobIdOrderByAccountId(1L)).thenReturn(List.of(unit));
        when(artifactRepository.findByWorkUnitId(11L)).thenReturn(Optional.empty());

        var partitions = service.partitions(7L);
        assertThat(partitions).hasSize(1);
        assertThat(partitions.get(0).accountId()).isEqualTo("BE-ACC-0001");
        assertThat(partitions.get(0).status()).isEqualTo("COMPLETED");
    }

    private ReportJob job(ReportJobStatus status) {
        ReportJob job = ReportJob.builder()
                .id(1L)
                .tenantId("BE")
                .reportType("ACCOUNT_STATEMENT")
                .businessDate(DATE)
                .jobExecutionId(7L)
                .status(status)
                .build();
        job.setCreatedAt(Instant.now());
        job.setUpdatedAt(Instant.now());
        return job;
    }
}
