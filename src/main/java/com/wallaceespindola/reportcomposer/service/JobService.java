package com.wallaceespindola.reportcomposer.service;

import com.wallaceespindola.reportcomposer.api.dto.JobSummaryDto;
import com.wallaceespindola.reportcomposer.api.dto.PartitionDto;
import com.wallaceespindola.reportcomposer.api.exception.BadRequestException;
import com.wallaceespindola.reportcomposer.api.exception.ConflictException;
import com.wallaceespindola.reportcomposer.api.exception.NotFoundException;
import com.wallaceespindola.reportcomposer.domain.ReportJob;
import com.wallaceespindola.reportcomposer.domain.ReportJobStatus;
import com.wallaceespindola.reportcomposer.domain.Tenant;
import com.wallaceespindola.reportcomposer.domain.TenantReportContract;
import com.wallaceespindola.reportcomposer.domain.WorkUnitStatus;
import com.wallaceespindola.reportcomposer.launcher.MasterLauncher;
import com.wallaceespindola.reportcomposer.repository.ReportArtifactRepository;
import com.wallaceespindola.reportcomposer.repository.ReportJobRepository;
import com.wallaceespindola.reportcomposer.repository.ReportWorkUnitRepository;
import com.wallaceespindola.reportcomposer.repository.TenantReportContractRepository;
import com.wallaceespindola.reportcomposer.repository.TenantRepository;
import com.wallaceespindola.reportcomposer.strategy.ReportStrategyResolver;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Validates job requests at the boundary (FR-18, PRD §3.7/§5) and orchestrates
 * launch/restart through the configured {@link MasterLauncher}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobService {

    private static final Set<ReportJobStatus> IN_FLIGHT =
            EnumSet.of(ReportJobStatus.REQUESTED, ReportJobStatus.STARTED, ReportJobStatus.RESTARTING);

    private final TenantRepository tenantRepository;
    private final TenantReportContractRepository contractRepository;
    private final ReportStrategyResolver strategyResolver;
    private final ReportJobRepository reportJobRepository;
    private final ReportWorkUnitRepository workUnitRepository;
    private final ReportArtifactRepository artifactRepository;
    private final ObjectProvider<MasterLauncher> masterLauncher;

    public Long start(String tenantId, String reportType, LocalDate businessDate) {
        validate(tenantId, reportType, businessDate);

        ReportJob job = reportJobRepository
                .findByTenantIdAndReportTypeAndBusinessDate(tenantId, reportType, businessDate)
                .orElse(null);
        if (job != null && IN_FLIGHT.contains(job.getStatus())) {
            throw new ConflictException("A job for this (tenant, reportType, businessDate) is already in flight");
        }
        if (job != null && job.getStatus() == ReportJobStatus.COMPLETED) {
            throw new ConflictException("This (tenant, reportType, businessDate) already completed successfully");
        }
        if (job == null) {
            job = ReportJob.builder()
                    .tenantId(tenantId)
                    .reportType(reportType)
                    .businessDate(businessDate)
                    .status(ReportJobStatus.REQUESTED)
                    .build();
        } else {
            job.setStatus(ReportJobStatus.REQUESTED);
        }
        job = reportJobRepository.save(job);

        try {
            Long executionId = launcher().launch(tenantId, reportType, businessDate);
            if (executionId != null) {
                job.setJobExecutionId(executionId);
                reportJobRepository.save(job);
            }
            return executionId;
        } catch (RuntimeException e) {
            job.setStatus(ReportJobStatus.FAILED);
            reportJobRepository.save(job);
            throw e;
        }
    }

    public Long restart(long jobExecutionId) {
        ReportJob job = findByExecutionId(jobExecutionId);
        if (job.getStatus() != ReportJobStatus.FAILED) {
            throw new ConflictException("Only FAILED jobs can be restarted (current: " + job.getStatus() + ")");
        }
        job.setStatus(ReportJobStatus.RESTARTING);
        reportJobRepository.save(job);
        try {
            Long newExecutionId = launcher().restart(jobExecutionId);
            if (newExecutionId != null) {
                job.setJobExecutionId(newExecutionId);
                reportJobRepository.save(job);
            }
            return newExecutionId != null ? newExecutionId : jobExecutionId;
        } catch (RuntimeException e) {
            job.setStatus(ReportJobStatus.FAILED);
            reportJobRepository.save(job);
            throw e;
        }
    }

    public JobSummaryDto get(long jobExecutionId) {
        return toSummary(findByExecutionId(jobExecutionId));
    }

    public List<JobSummaryDto> list(String tenantId, String reportType, LocalDate businessDate, String status) {
        return reportJobRepository.findAll().stream()
                .filter(j -> tenantId == null || j.getTenantId().equals(tenantId))
                .filter(j -> reportType == null || j.getReportType().equals(reportType))
                .filter(j -> businessDate == null || j.getBusinessDate().equals(businessDate))
                .filter(j -> status == null || j.getStatus().name().equalsIgnoreCase(status))
                .sorted(Comparator.comparing(ReportJob::getId).reversed())
                .map(this::toSummary)
                .toList();
    }

    public List<PartitionDto> partitions(long jobExecutionId) {
        ReportJob job = findByExecutionId(jobExecutionId);
        return workUnitRepository.findByReportJobIdOrderByAccountId(job.getId()).stream()
                .map(unit -> new PartitionDto(
                        unit.getId(),
                        unit.getAccountId(),
                        unit.getStatus().name(),
                        unit.getAttemptCount(),
                        artifactRepository.findByWorkUnitId(unit.getId())
                                .map(a -> a.getObjectKey())
                                .orElse(null)))
                .toList();
    }

    private void validate(String tenantId, String reportType, LocalDate businessDate) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new NotFoundException("Unknown tenant '" + tenantId + "'"));
        if (!tenant.isEnabled()) {
            throw new BadRequestException("Tenant '" + tenantId + "' is disabled");
        }
        if (!strategyResolver.isRegistered(reportType)) {
            throw new BadRequestException(
                    "Report type '" + reportType + "' has no registered strategy (not in the agreed catalog)");
        }
        TenantReportContract contract = contractRepository
                .findByTenantIdAndReportType(tenantId, reportType)
                .orElseThrow(() -> new BadRequestException(
                        "Tenant '" + tenantId + "' has no contract for report type '" + reportType + "'"));
        if (!contract.isActiveOn(businessDate)) {
            throw new BadRequestException(
                    "Contract for '" + tenantId + "/" + reportType + "' is not active on " + businessDate);
        }
    }

    private ReportJob findByExecutionId(long jobExecutionId) {
        return reportJobRepository
                .findByJobExecutionId(jobExecutionId)
                .orElseThrow(() -> new NotFoundException("No job with execution id " + jobExecutionId));
    }

    private MasterLauncher launcher() {
        MasterLauncher launcher = masterLauncher.getIfAvailable();
        if (launcher == null) {
            throw new IllegalStateException("No MasterLauncher available in role/launcher mode");
        }
        return launcher;
    }

    private JobSummaryDto toSummary(ReportJob job) {
        long total = workUnitRepository.countByReportJobId(job.getId());
        long completed = workUnitRepository.countByReportJobIdAndStatus(job.getId(), WorkUnitStatus.COMPLETED);
        long failed = workUnitRepository.countByReportJobIdAndStatus(job.getId(), WorkUnitStatus.FAILED);
        boolean finished = job.getStatus() == ReportJobStatus.COMPLETED || job.getStatus() == ReportJobStatus.FAILED;
        return new JobSummaryDto(
                job.getJobExecutionId(),
                job.getTenantId(),
                job.getReportType(),
                job.getBusinessDate(),
                job.getStatus().name(),
                job.getCreatedAt(),
                finished ? job.getUpdatedAt() : null,
                total,
                completed,
                failed);
    }
}
