package com.wallaceespindola.reportcomposer.batch.worker;

import com.wallaceespindola.reportcomposer.batch.master.AccountPartitioner;
import com.wallaceespindola.reportcomposer.domain.ReportArtifact;
import com.wallaceespindola.reportcomposer.domain.ReportWorkUnit;
import com.wallaceespindola.reportcomposer.domain.Tenant;
import com.wallaceespindola.reportcomposer.domain.TransactionEntity;
import com.wallaceespindola.reportcomposer.domain.WorkUnitStatus;
import com.wallaceespindola.reportcomposer.repository.ReportArtifactRepository;
import com.wallaceespindola.reportcomposer.repository.ReportWorkUnitRepository;
import com.wallaceespindola.reportcomposer.repository.TenantReportContractRepository;
import com.wallaceespindola.reportcomposer.repository.TenantRepository;
import com.wallaceespindola.reportcomposer.repository.TransactionRepository;
import com.wallaceespindola.reportcomposer.storage.ArtifactStorage;
import com.wallaceespindola.reportcomposer.strategy.GeneratedReport;
import com.wallaceespindola.reportcomposer.strategy.ReportContext;
import com.wallaceespindola.reportcomposer.strategy.ReportStrategyResolver;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

/**
 * Executes one partition = one account (PRD §3.3): loads the account's transactions,
 * resolves the strategy by reportType, renders the report, persists it idempotently
 * (DB row + MinIO object, both keyed) and marks the work unit complete.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportWorkerTasklet implements Tasklet {

    private final ReportWorkUnitRepository workUnitRepository;
    private final ReportArtifactRepository artifactRepository;
    private final TenantRepository tenantRepository;
    private final TenantReportContractRepository contractRepository;
    private final TransactionRepository transactionRepository;
    private final ReportStrategyResolver strategyResolver;
    private final ArtifactStorage artifactStorage;
    private final WorkUnitStateService stateService;
    private final RetryTemplate reportRetryTemplate;

    @Override
    public RepeatStatus execute(StepContribution contribution, org.springframework.batch.core.scope.context.ChunkContext chunkContext) {
        ExecutionContext ctx = chunkContext.getStepContext().getStepExecution().getExecutionContext();
        String tenantId = ctx.getString(AccountPartitioner.CTX_TENANT);
        String accountId = ctx.getString(AccountPartitioner.CTX_ACCOUNT);
        String reportType = ctx.getString(AccountPartitioner.CTX_REPORT_TYPE);
        LocalDate businessDate = LocalDate.parse(ctx.getString(AccountPartitioner.CTX_BUSINESS_DATE));

        MDC.put("tenantId", tenantId);
        MDC.put("accountId", accountId);
        MDC.put("reportType", reportType);
        MDC.put("businessDate", businessDate.toString());
        try {
            ReportWorkUnit unit = workUnitRepository
                    .findByTenantIdAndAccountIdAndReportTypeAndBusinessDate(
                            tenantId, accountId, reportType, businessDate)
                    .orElseThrow(() -> new IllegalStateException(
                            "No report_work_unit for %s/%s/%s/%s".formatted(
                                    tenantId, accountId, reportType, businessDate)));

            if (unit.getStatus() == WorkUnitStatus.COMPLETED) {
                log.info("Work unit {} already COMPLETED — skipping (idempotent re-run)", unit.getId());
                return RepeatStatus.FINISHED;
            }

            stateService.markRunning(unit.getId());
            try {
                reportRetryTemplate.execute(retryCtx -> {
                    generateAndPersist(unit, tenantId, accountId, reportType, businessDate);
                    return null;
                });
                stateService.markCompleted(unit.getId());
                log.info("Work unit {} COMPLETED", unit.getId());
            } catch (Exception e) {
                stateService.markFailed(unit.getId(), e.getMessage());
                log.error("Work unit {} FAILED: {}", unit.getId(), e.getMessage());
                throw e instanceof RuntimeException re ? re : new IllegalStateException(e);
            }
            return RepeatStatus.FINISHED;
        } finally {
            MDC.remove("tenantId");
            MDC.remove("accountId");
            MDC.remove("reportType");
            MDC.remove("businessDate");
        }
    }

    private void generateAndPersist(
            ReportWorkUnit unit, String tenantId, String accountId, String reportType, LocalDate businessDate) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalStateException("Unknown tenant " + tenantId));
        String contractParams = contractRepository
                .findByTenantIdAndReportType(tenantId, reportType)
                .map(c -> c.getParamsJson())
                .orElse(null);
        List<TransactionEntity> transactions =
                transactionRepository.findByAccountIdAndBusinessDateOrderById(accountId, businessDate);

        GeneratedReport report = strategyResolver
                .resolve(reportType)
                .generate(new ReportContext(tenant, accountId, businessDate, contractParams), transactions);

        // deterministic object key -> re-runs overwrite the same object, never duplicate
        String objectKey = "%s/%s/%s/%s".formatted(tenantId, reportType, businessDate, report.fileName());
        artifactStorage.put(objectKey, report.content(), report.contentType());

        ReportArtifact artifact = artifactRepository.findByWorkUnitId(unit.getId())
                .orElseGet(() -> ReportArtifact.builder().workUnitId(unit.getId()).build());
        artifact.setObjectKey(objectKey);
        artifact.setContentType(report.contentType());
        artifact.setSizeBytes(report.content().length);
        artifact.setChecksum(sha256(report.content()));
        artifactRepository.save(artifact);
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
