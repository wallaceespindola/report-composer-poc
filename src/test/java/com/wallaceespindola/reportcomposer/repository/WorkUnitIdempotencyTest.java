package com.wallaceespindola.reportcomposer.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wallaceespindola.reportcomposer.TestFixtures;
import com.wallaceespindola.reportcomposer.domain.ReportJob;
import com.wallaceespindola.reportcomposer.domain.ReportJobStatus;
import com.wallaceespindola.reportcomposer.domain.ReportWorkUnit;
import com.wallaceespindola.reportcomposer.domain.WorkUnitStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;

/** Idempotency unique-key enforcement against H2 in Oracle mode + Flyway schema (PRD §16). */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:idempotency;MODE=Oracle;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password="
})
class WorkUnitIdempotencyTest {

    @Autowired private ReportJobRepository reportJobRepository;
    @Autowired private ReportWorkUnitRepository workUnitRepository;
    @Autowired private TenantRepository tenantRepository;

    @Test
    void flywaySeedsTenantsAndContracts() {
        assertThat(tenantRepository.findAll())
                .extracting(t -> t.getTenantId())
                .containsExactlyInAnyOrder("BE", "FR", "ES");
    }

    @Test
    void duplicateWorkUnitForSameKeyIsRejectedByUniqueConstraint() {
        ReportJob job = reportJobRepository.saveAndFlush(ReportJob.builder()
                .tenantId("BE")
                .reportType("ACCOUNT_STATEMENT")
                .businessDate(TestFixtures.BUSINESS_DATE)
                .status(ReportJobStatus.STARTED)
                .build());

        workUnitRepository.saveAndFlush(unit(job.getId()));

        assertThatThrownBy(() -> workUnitRepository.saveAndFlush(unit(job.getId())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static ReportWorkUnit unit(Long jobId) {
        return ReportWorkUnit.builder()
                .reportJobId(jobId)
                .tenantId("BE")
                .accountId("BE-ACC-0001")
                .reportType("ACCOUNT_STATEMENT")
                .businessDate(TestFixtures.BUSINESS_DATE)
                .status(WorkUnitStatus.PENDING)
                .build();
    }
}
