package com.wallaceespindola.reportcomposer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.wallaceespindola.reportcomposer.TestFixtures;
import com.wallaceespindola.reportcomposer.api.exception.ConflictException;
import com.wallaceespindola.reportcomposer.api.exception.NotFoundException;
import com.wallaceespindola.reportcomposer.domain.ReportArtifact;
import com.wallaceespindola.reportcomposer.domain.ReportWorkUnit;
import com.wallaceespindola.reportcomposer.domain.WorkUnitStatus;
import com.wallaceespindola.reportcomposer.repository.ReportArtifactRepository;
import com.wallaceespindola.reportcomposer.repository.ReportWorkUnitRepository;
import com.wallaceespindola.reportcomposer.storage.ArtifactStorage;
import java.io.ByteArrayInputStream;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReportDownloadServiceTest {

    @Mock private ReportArtifactRepository artifactRepository;
    @Mock private ReportWorkUnitRepository workUnitRepository;
    @Mock private ArtifactStorage artifactStorage;

    private ReportDownloadService service;

    @BeforeEach
    void setUp() {
        service = new ReportDownloadService(artifactRepository, workUnitRepository, artifactStorage);
    }

    private ReportWorkUnit unit(WorkUnitStatus status) {
        return ReportWorkUnit.builder()
                .id(11L)
                .tenantId("BE")
                .accountId("BE-ACC-0001")
                .reportType("ACCOUNT_STATEMENT")
                .businessDate(TestFixtures.BUSINESS_DATE)
                .status(status)
                .build();
    }

    @Test
    void downloadsCompletedWorkUnitArtifact() {
        when(workUnitRepository.findById(11L)).thenReturn(Optional.of(unit(WorkUnitStatus.COMPLETED)));
        ReportArtifact artifact =
                ReportArtifact.builder().workUnitId(11L).objectKey("key").contentType("text/plain").build();
        when(artifactRepository.findByWorkUnitId(11L)).thenReturn(Optional.of(artifact));
        when(artifactStorage.get("key")).thenReturn(new ByteArrayInputStream("body".getBytes()));

        var download = service.download(11L);
        assertThat(download.artifact().getObjectKey()).isEqualTo("key");
        assertThat(download.content()).isNotNull();
    }

    @Test
    void incompleteWorkUnitIsConflict() {
        when(workUnitRepository.findById(11L)).thenReturn(Optional.of(unit(WorkUnitStatus.RUNNING)));
        assertThatThrownBy(() -> service.download(11L)).isInstanceOf(ConflictException.class);
    }

    @Test
    void unknownWorkUnitIs404() {
        when(workUnitRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.download(99L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void listJoinsWorkUnitsAndFilters() {
        ReportArtifact beArtifact = ReportArtifact.builder()
                .id(1L).workUnitId(11L)
                .objectKey("BE/ACCOUNT_STATEMENT/2026-06-30/be.txt")
                .contentType("text/plain").sizeBytes(10).checksum("c1")
                .createdAt(java.time.Instant.parse("2026-07-02T10:00:00Z"))
                .build();
        ReportArtifact frArtifact = ReportArtifact.builder()
                .id(2L).workUnitId(12L)
                .objectKey("FR/TAX_SUMMARY/2026-06-30/fr.txt")
                .contentType("text/plain").sizeBytes(20).checksum("c2")
                .createdAt(java.time.Instant.parse("2026-07-02T11:00:00Z"))
                .build();
        when(artifactRepository.findAll()).thenReturn(java.util.List.of(beArtifact, frArtifact));
        ReportWorkUnit beUnit = unit(WorkUnitStatus.COMPLETED);
        beUnit.setId(11L);
        ReportWorkUnit frUnit = unit(WorkUnitStatus.COMPLETED);
        frUnit.setId(12L);
        frUnit.setTenantId("FR");
        frUnit.setReportType("TAX_SUMMARY");
        when(workUnitRepository.findById(11L)).thenReturn(Optional.of(beUnit));
        when(workUnitRepository.findById(12L)).thenReturn(Optional.of(frUnit));

        var all = service.list(null, null, null);
        assertThat(all).hasSize(2);
        assertThat(all.get(0).fileName()).isEqualTo("fr.txt"); // newest first

        var onlyBe = service.list("BE", null, null);
        assertThat(onlyBe).hasSize(1);
        assertThat(onlyBe.get(0).reportType()).isEqualTo("ACCOUNT_STATEMENT");

        assertThat(service.list(null, "TAX_SUMMARY", null)).hasSize(1);
        assertThat(service.list(null, null, TestFixtures.BUSINESS_DATE.plusDays(1))).isEmpty();
    }
}
