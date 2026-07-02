package com.wallaceespindola.reportcomposer.batch.master;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wallaceespindola.reportcomposer.TestFixtures;
import com.wallaceespindola.reportcomposer.domain.ReportJob;
import com.wallaceespindola.reportcomposer.domain.ReportJobStatus;
import com.wallaceespindola.reportcomposer.domain.ReportWorkUnit;
import com.wallaceespindola.reportcomposer.domain.WorkUnitStatus;
import com.wallaceespindola.reportcomposer.repository.AccountRepository;
import com.wallaceespindola.reportcomposer.repository.ReportJobRepository;
import com.wallaceespindola.reportcomposer.repository.ReportWorkUnitRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountPartitionerTest {

    @Mock private AccountRepository accountRepository;
    @Mock private ReportJobRepository reportJobRepository;
    @Mock private ReportWorkUnitRepository workUnitRepository;

    private AccountPartitioner partitioner() {
        return new AccountPartitioner(
                accountRepository, reportJobRepository, workUnitRepository, "BE", "ACCOUNT_STATEMENT", "2026-06-30");
    }

    @Test
    void createsOnePartitionPerEligibleAccountWithFullContext() {
        ReportJob job = ReportJob.builder().id(1L).tenantId("BE").reportType("ACCOUNT_STATEMENT")
                .businessDate(TestFixtures.BUSINESS_DATE).status(ReportJobStatus.STARTED).build();
        when(reportJobRepository.findByTenantIdAndReportTypeAndBusinessDate(
                        "BE", "ACCOUNT_STATEMENT", TestFixtures.BUSINESS_DATE))
                .thenReturn(Optional.of(job));
        when(accountRepository.findByTenantIdAndEligibleTrueOrderByAccountId("BE"))
                .thenReturn(List.of(
                        TestFixtures.account("BE", "BE-ACC-0001"),
                        TestFixtures.account("BE", "BE-ACC-0002")));
        when(workUnitRepository.findByTenantIdAndAccountIdAndReportTypeAndBusinessDate(
                        any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(workUnitRepository.save(any(ReportWorkUnit.class))).thenAnswer(inv -> inv.getArgument(0));

        var partitions = partitioner().partition(10);

        assertThat(partitions).hasSize(2).containsKeys("partition-BE-ACC-0001", "partition-BE-ACC-0002");
        var ctx = partitions.get("partition-BE-ACC-0001");
        assertThat(ctx.getString(AccountPartitioner.CTX_TENANT)).isEqualTo("BE");
        assertThat(ctx.getString(AccountPartitioner.CTX_ACCOUNT)).isEqualTo("BE-ACC-0001");
        assertThat(ctx.getString(AccountPartitioner.CTX_REPORT_TYPE)).isEqualTo("ACCOUNT_STATEMENT");
        assertThat(ctx.getString(AccountPartitioner.CTX_BUSINESS_DATE)).isEqualTo("2026-06-30");
    }

    @Test
    void createsReportJobRowWhenMissing() {
        when(reportJobRepository.findByTenantIdAndReportTypeAndBusinessDate(
                        "BE", "ACCOUNT_STATEMENT", TestFixtures.BUSINESS_DATE))
                .thenReturn(Optional.empty());
        when(reportJobRepository.save(any(ReportJob.class))).thenAnswer(inv -> {
            ReportJob j = inv.getArgument(0);
            j.setId(9L);
            return j;
        });
        when(accountRepository.findByTenantIdAndEligibleTrueOrderByAccountId("BE")).thenReturn(List.of());

        assertThat(partitioner().partition(10)).isEmpty();
        verify(reportJobRepository).save(any(ReportJob.class));
    }

    @Test
    void doesNotDuplicateExistingWorkUnitsOnRestart() {
        ReportJob job = ReportJob.builder().id(1L).tenantId("BE").reportType("ACCOUNT_STATEMENT")
                .businessDate(TestFixtures.BUSINESS_DATE).status(ReportJobStatus.STARTED).build();
        when(reportJobRepository.findByTenantIdAndReportTypeAndBusinessDate(
                        "BE", "ACCOUNT_STATEMENT", TestFixtures.BUSINESS_DATE))
                .thenReturn(Optional.of(job));
        when(accountRepository.findByTenantIdAndEligibleTrueOrderByAccountId("BE"))
                .thenReturn(List.of(TestFixtures.account("BE", "BE-ACC-0001")));
        when(workUnitRepository.findByTenantIdAndAccountIdAndReportTypeAndBusinessDate(
                        "BE", "BE-ACC-0001", "ACCOUNT_STATEMENT", TestFixtures.BUSINESS_DATE))
                .thenReturn(Optional.of(ReportWorkUnit.builder()
                        .id(5L)
                        .status(WorkUnitStatus.COMPLETED)
                        .build()));

        var partitions = partitioner().partition(10);

        assertThat(partitions).hasSize(1); // partition still emitted; Spring Batch skips completed step executions
        verify(workUnitRepository, never()).save(any());
    }
}
