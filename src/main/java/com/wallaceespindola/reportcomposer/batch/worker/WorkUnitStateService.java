package com.wallaceespindola.reportcomposer.batch.worker;

import com.wallaceespindola.reportcomposer.domain.ReportWorkUnit;
import com.wallaceespindola.reportcomposer.domain.WorkUnitStatus;
import com.wallaceespindola.reportcomposer.repository.ReportWorkUnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Work-unit status transitions in REQUIRES_NEW transactions so a failing partition
 * still records its FAILED state after the step transaction rolls back.
 */
@Service
@RequiredArgsConstructor
public class WorkUnitStateService {

    private final ReportWorkUnitRepository workUnitRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRunning(Long workUnitId) {
        ReportWorkUnit unit = workUnitRepository.findById(workUnitId).orElseThrow();
        unit.setStatus(WorkUnitStatus.RUNNING);
        unit.setAttemptCount(unit.getAttemptCount() + 1);
        unit.setErrorMessage(null);
        workUnitRepository.save(unit);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompleted(Long workUnitId) {
        ReportWorkUnit unit = workUnitRepository.findById(workUnitId).orElseThrow();
        unit.setStatus(WorkUnitStatus.COMPLETED);
        workUnitRepository.save(unit);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long workUnitId, String errorMessage) {
        ReportWorkUnit unit = workUnitRepository.findById(workUnitId).orElseThrow();
        unit.setStatus(WorkUnitStatus.FAILED);
        unit.setErrorMessage(errorMessage != null && errorMessage.length() > 2000
                ? errorMessage.substring(0, 2000)
                : errorMessage);
        workUnitRepository.save(unit);
    }
}
