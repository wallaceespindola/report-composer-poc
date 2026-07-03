package com.wallaceespindola.reportcomposer.service;

import com.wallaceespindola.reportcomposer.api.dto.ApiDtos.StatsResponse;
import com.wallaceespindola.reportcomposer.config.AppProperties;
import com.wallaceespindola.reportcomposer.domain.ReportJobStatus;
import com.wallaceespindola.reportcomposer.domain.WorkUnitStatus;
import com.wallaceespindola.reportcomposer.repository.AccountRepository;
import com.wallaceespindola.reportcomposer.repository.ReportArtifactRepository;
import com.wallaceespindola.reportcomposer.repository.ReportJobRepository;
import com.wallaceespindola.reportcomposer.repository.ReportWorkUnitRepository;
import com.wallaceespindola.reportcomposer.repository.TenantReportContractRepository;
import com.wallaceespindola.reportcomposer.repository.TenantRepository;
import com.wallaceespindola.reportcomposer.repository.TransactionRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.MemberDescription;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Service;

/** System stats for the UI, including live worker count from the Kafka consumer group. */
@Slf4j
@Service
public class StatsService {

    private final TenantRepository tenantRepository;
    private final TenantReportContractRepository contractRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final ReportJobRepository reportJobRepository;
    private final ReportWorkUnitRepository workUnitRepository;
    private final ReportArtifactRepository artifactRepository;
    private final KafkaAdmin kafkaAdmin;
    private final AppProperties props;

    public StatsService(
            TenantRepository tenantRepository,
            TenantReportContractRepository contractRepository,
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            ReportJobRepository reportJobRepository,
            ReportWorkUnitRepository workUnitRepository,
            ReportArtifactRepository artifactRepository,
            KafkaAdmin kafkaAdmin,
            AppProperties props) {
        this.tenantRepository = tenantRepository;
        this.contractRepository = contractRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.reportJobRepository = reportJobRepository;
        this.workUnitRepository = workUnitRepository;
        this.artifactRepository = artifactRepository;
        this.kafkaAdmin = kafkaAdmin;
        this.props = props;
    }

    public StatsResponse stats() {
        Map<String, Long> jobsByStatus = new LinkedHashMap<>();
        for (ReportJobStatus status : ReportJobStatus.values()) {
            long count = reportJobRepository.countByStatus(status);
            if (count > 0) {
                jobsByStatus.put(status.name(), count);
            }
        }
        Map<String, Long> workUnitsByStatus = new LinkedHashMap<>();
        for (WorkUnitStatus status : WorkUnitStatus.values()) {
            long count = workUnitRepository.countByStatus(status);
            if (count > 0) {
                workUnitsByStatus.put(status.name(), count);
            }
        }
        long artifactBytes = artifactRepository.findAll().stream()
                .mapToLong(a -> a.getSizeBytes())
                .sum();

        Workers workers = activeWorkers();

        return new StatsResponse(
                Instant.now(),
                tenantRepository.count(),
                contractRepository.count(),
                accountRepository.count(),
                transactionRepository.count(),
                jobsByStatus,
                workUnitsByStatus,
                artifactRepository.count(),
                artifactBytes,
                workers.pods(),
                workers.threads());
    }

    record Workers(int pods, int threads) {}

    /**
     * Live workers = members of the Kafka consumer group. Distinct client hosts =
     * worker pods/containers; members = consumer threads (KAFKA_CONCURRENCY each).
     * Works identically in compose and k8s mode; returns 0/0 if Kafka is unreachable.
     */
    private Workers activeWorkers() {
        try (Admin admin = Admin.create(kafkaAdmin.getConfigurationProperties())) {
            String group = props.kafka().consumerGroup();
            List<MemberDescription> members = admin.describeConsumerGroups(List.of(group))
                    .describedGroups()
                    .get(group)
                    .get(3, TimeUnit.SECONDS)
                    .members()
                    .stream()
                    .toList();
            int pods = (int) members.stream().map(MemberDescription::host).distinct().count();
            return new Workers(pods, members.size());
        } catch (Exception e) {
            log.debug("Could not read consumer group state: {}", e.getMessage());
            return new Workers(0, 0);
        }
    }
}
