package com.wallaceespindola.reportcomposer.batch.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wallaceespindola.reportcomposer.TestFixtures;
import com.wallaceespindola.reportcomposer.batch.master.AccountPartitioner;
import com.wallaceespindola.reportcomposer.domain.ReportArtifact;
import com.wallaceespindola.reportcomposer.domain.ReportWorkUnit;
import com.wallaceespindola.reportcomposer.domain.WorkUnitStatus;
import com.wallaceespindola.reportcomposer.repository.ReportArtifactRepository;
import com.wallaceespindola.reportcomposer.repository.ReportWorkUnitRepository;
import com.wallaceespindola.reportcomposer.repository.TenantReportContractRepository;
import com.wallaceespindola.reportcomposer.repository.TenantRepository;
import com.wallaceespindola.reportcomposer.repository.TransactionRepository;
import com.wallaceespindola.reportcomposer.storage.ArtifactStorage;
import com.wallaceespindola.reportcomposer.storage.StorageException;
import com.wallaceespindola.reportcomposer.strategy.AccountStatementStrategy;
import com.wallaceespindola.reportcomposer.strategy.ReportStrategyResolver;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.batch.test.MetaDataInstanceFactory;
import org.springframework.retry.support.RetryTemplate;

@ExtendWith(MockitoExtension.class)
class ReportWorkerTaskletTest {

    @Mock private ReportWorkUnitRepository workUnitRepository;
    @Mock private ReportArtifactRepository artifactRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private TenantReportContractRepository contractRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private ArtifactStorage artifactStorage;
    @Mock private WorkUnitStateService stateService;

    private ReportWorkerTasklet tasklet;

    @BeforeEach
    void setUp() {
        tasklet = new ReportWorkerTasklet(
                workUnitRepository,
                artifactRepository,
                tenantRepository,
                contractRepository,
                transactionRepository,
                new ReportStrategyResolver(List.of(new AccountStatementStrategy())),
                artifactStorage,
                stateService,
                RetryTemplate.builder().maxAttempts(3).fixedBackoff(1).build());
    }

    private ChunkContext chunkContext() {
        StepExecution stepExecution = MetaDataInstanceFactory.createStepExecution();
        var ctx = stepExecution.getExecutionContext();
        ctx.putString(AccountPartitioner.CTX_TENANT, "BE");
        ctx.putString(AccountPartitioner.CTX_ACCOUNT, "BE-ACC-0001");
        ctx.putString(AccountPartitioner.CTX_REPORT_TYPE, "ACCOUNT_STATEMENT");
        ctx.putString(AccountPartitioner.CTX_BUSINESS_DATE, "2026-06-30");
        return new ChunkContext(new StepContext(stepExecution));
    }

    private ReportWorkUnit unit(WorkUnitStatus status) {
        return ReportWorkUnit.builder()
                .id(11L)
                .reportJobId(1L)
                .tenantId("BE")
                .accountId("BE-ACC-0001")
                .reportType("ACCOUNT_STATEMENT")
                .businessDate(TestFixtures.BUSINESS_DATE)
                .status(status)
                .build();
    }

    @Test
    void generatesUploadsAndMarksCompleted() {
        when(workUnitRepository.findByTenantIdAndAccountIdAndReportTypeAndBusinessDate(
                        "BE", "BE-ACC-0001", "ACCOUNT_STATEMENT", TestFixtures.BUSINESS_DATE))
                .thenReturn(Optional.of(unit(WorkUnitStatus.PENDING)));
        when(tenantRepository.findById("BE")).thenReturn(Optional.of(TestFixtures.tenantBE()));
        when(contractRepository.findByTenantIdAndReportType("BE", "ACCOUNT_STATEMENT"))
                .thenReturn(Optional.of(TestFixtures.contract("BE", "ACCOUNT_STATEMENT")));
        when(transactionRepository.findByAccountIdAndBusinessDateOrderById("BE-ACC-0001", TestFixtures.BUSINESS_DATE))
                .thenReturn(List.of(TestFixtures.txn("BE-ACC-0001", "CREDIT", "100.00")));
        when(artifactRepository.findByWorkUnitId(11L)).thenReturn(Optional.empty());
        when(artifactRepository.save(any(ReportArtifact.class))).thenAnswer(inv -> inv.getArgument(0));

        RepeatStatus status = tasklet.execute(new StepContribution(MetaDataInstanceFactory.createStepExecution()),
                chunkContext());

        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
        verify(stateService).markRunning(11L);
        verify(stateService).markCompleted(11L);
        verify(artifactStorage).put(eq("BE/ACCOUNT_STATEMENT/2026-06-30/BE_BE-ACC-0001_2026-06-30_statement.txt"),
                any(byte[].class), anyString());

        ArgumentCaptor<ReportArtifact> artifact = ArgumentCaptor.forClass(ReportArtifact.class);
        verify(artifactRepository).save(artifact.capture());
        assertThat(artifact.getValue().getChecksum()).hasSize(64);
        assertThat(artifact.getValue().getSizeBytes()).isPositive();
    }

    @Test
    void skipsAlreadyCompletedWorkUnitIdempotently() {
        when(workUnitRepository.findByTenantIdAndAccountIdAndReportTypeAndBusinessDate(
                        any(), any(), any(), any()))
                .thenReturn(Optional.of(unit(WorkUnitStatus.COMPLETED)));

        RepeatStatus status = tasklet.execute(new StepContribution(MetaDataInstanceFactory.createStepExecution()),
                chunkContext());

        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
        verify(stateService, never()).markRunning(any());
        verify(artifactStorage, never()).put(any(), any(), any());
    }

    @Test
    void retriesTransientFailuresThenMarksFailed() {
        when(workUnitRepository.findByTenantIdAndAccountIdAndReportTypeAndBusinessDate(
                        any(), any(), any(), any()))
                .thenReturn(Optional.of(unit(WorkUnitStatus.PENDING)));
        when(tenantRepository.findById("BE")).thenReturn(Optional.of(TestFixtures.tenantBE()));
        when(contractRepository.findByTenantIdAndReportType(any(), any())).thenReturn(Optional.empty());
        when(transactionRepository.findByAccountIdAndBusinessDateOrderById(any(), any())).thenReturn(List.of());
        doThrow(new StorageException("minio down", new RuntimeException()))
                .when(artifactStorage)
                .put(any(), any(), any());

        assertThatThrownBy(() -> tasklet.execute(
                        new StepContribution(MetaDataInstanceFactory.createStepExecution()), chunkContext()))
                .isInstanceOf(StorageException.class);

        verify(artifactStorage, times(3)).put(any(), any(), any()); // bounded retry (FR-8)
        verify(stateService).markFailed(eq(11L), anyString());
        verify(stateService, never()).markCompleted(any());
    }

    @Test
    void missingWorkUnitFailsThePartition() {
        when(workUnitRepository.findByTenantIdAndAccountIdAndReportTypeAndBusinessDate(
                        any(), any(), any(), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> tasklet.execute(
                        new StepContribution(MetaDataInstanceFactory.createStepExecution()), chunkContext()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No report_work_unit");
    }
}
