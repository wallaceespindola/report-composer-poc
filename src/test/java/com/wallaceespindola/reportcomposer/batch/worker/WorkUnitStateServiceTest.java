package com.wallaceespindola.reportcomposer.batch.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wallaceespindola.reportcomposer.domain.ReportWorkUnit;
import com.wallaceespindola.reportcomposer.domain.WorkUnitStatus;
import com.wallaceespindola.reportcomposer.repository.ReportWorkUnitRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkUnitStateServiceTest {

    @Mock private ReportWorkUnitRepository repository;

    private WorkUnitStateService service;
    private ReportWorkUnit unit;

    @BeforeEach
    void setUp() {
        service = new WorkUnitStateService(repository);
        unit = ReportWorkUnit.builder().id(1L).status(WorkUnitStatus.PENDING).attemptCount(0).build();
        when(repository.findById(1L)).thenReturn(Optional.of(unit));
    }

    @Test
    void markRunningIncrementsAttemptAndClearsError() {
        unit.setErrorMessage("old error");
        service.markRunning(1L);
        assertThat(unit.getStatus()).isEqualTo(WorkUnitStatus.RUNNING);
        assertThat(unit.getAttemptCount()).isEqualTo(1);
        assertThat(unit.getErrorMessage()).isNull();
        verify(repository).save(unit);
    }

    @Test
    void markCompleted() {
        service.markCompleted(1L);
        assertThat(unit.getStatus()).isEqualTo(WorkUnitStatus.COMPLETED);
    }

    @Test
    void markFailedTruncatesLongMessages() {
        service.markFailed(1L, "x".repeat(5000));
        assertThat(unit.getStatus()).isEqualTo(WorkUnitStatus.FAILED);
        assertThat(unit.getErrorMessage()).hasSize(2000);
    }
}
