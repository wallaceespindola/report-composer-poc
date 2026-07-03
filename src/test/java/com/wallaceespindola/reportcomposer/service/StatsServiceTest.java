package com.wallaceespindola.reportcomposer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.wallaceespindola.reportcomposer.TestFixtures;
import com.wallaceespindola.reportcomposer.domain.ReportArtifact;
import com.wallaceespindola.reportcomposer.domain.ReportJobStatus;
import com.wallaceespindola.reportcomposer.domain.WorkUnitStatus;
import com.wallaceespindola.reportcomposer.repository.AccountRepository;
import com.wallaceespindola.reportcomposer.repository.ReportArtifactRepository;
import com.wallaceespindola.reportcomposer.repository.ReportJobRepository;
import com.wallaceespindola.reportcomposer.repository.ReportWorkUnitRepository;
import com.wallaceespindola.reportcomposer.repository.TenantReportContractRepository;
import com.wallaceespindola.reportcomposer.repository.TenantRepository;
import com.wallaceespindola.reportcomposer.repository.TransactionRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaAdmin;

@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private TenantReportContractRepository contractRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private ReportJobRepository reportJobRepository;
    @Mock private ReportWorkUnitRepository workUnitRepository;
    @Mock private ReportArtifactRepository artifactRepository;
    @Mock private KafkaAdmin kafkaAdmin;

    @Test
    void aggregatesCountsAndSurvivesUnreachableKafka() {
        when(tenantRepository.count()).thenReturn(3L);
        when(contractRepository.count()).thenReturn(6L);
        when(accountRepository.count()).thenReturn(150L);
        when(transactionRepository.count()).thenReturn(800L);
        when(reportJobRepository.countByStatus(org.mockito.ArgumentMatchers.any())).thenReturn(0L);
        when(reportJobRepository.countByStatus(ReportJobStatus.COMPLETED)).thenReturn(2L);
        when(reportJobRepository.countByStatus(ReportJobStatus.FAILED)).thenReturn(1L);
        when(workUnitRepository.countByStatus(org.mockito.ArgumentMatchers.any())).thenReturn(0L);
        when(workUnitRepository.countByStatus(WorkUnitStatus.COMPLETED)).thenReturn(100L);
        when(artifactRepository.count()).thenReturn(100L);
        when(artifactRepository.findAll()).thenReturn(List.of(
                ReportArtifact.builder().workUnitId(1L).objectKey("k").contentType("t").sizeBytes(300).build(),
                ReportArtifact.builder().workUnitId(2L).objectKey("k").contentType("t").sizeBytes(700).build()));
        // unreachable broker with tiny timeouts -> worker count degrades to 0/0
        when(kafkaAdmin.getConfigurationProperties()).thenReturn(Map.of(
                "bootstrap.servers", "localhost:65535",
                "request.timeout.ms", 200,
                "default.api.timeout.ms", 200));

        var stats = new StatsService(
                        tenantRepository, contractRepository, accountRepository, transactionRepository,
                        reportJobRepository, workUnitRepository, artifactRepository, kafkaAdmin,
                        TestFixtures.appProperties("api", "local"))
                .stats();

        assertThat(stats.tenants()).isEqualTo(3);
        assertThat(stats.contracts()).isEqualTo(6);
        assertThat(stats.accounts()).isEqualTo(150);
        assertThat(stats.transactions()).isEqualTo(800);
        assertThat(stats.jobsByStatus()).containsEntry("COMPLETED", 2L).containsEntry("FAILED", 1L)
                .doesNotContainKey("STARTED");
        assertThat(stats.workUnitsByStatus()).containsEntry("COMPLETED", 100L);
        assertThat(stats.artifacts()).isEqualTo(100);
        assertThat(stats.artifactBytes()).isEqualTo(1000);
        assertThat(stats.activeWorkerPods()).isZero();
        assertThat(stats.workerConsumerThreads()).isZero();
        assertThat(stats.timestamp()).isNotNull();
    }
}
