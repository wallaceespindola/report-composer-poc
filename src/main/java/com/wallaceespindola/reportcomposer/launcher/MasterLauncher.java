package com.wallaceespindola.reportcomposer.launcher;

import java.time.LocalDate;

/**
 * Starts the partitioning master for a job key. LAUNCHER_MODE=local runs the manager
 * step in-process (Docker Compose / single machine); LAUNCHER_MODE=k8s creates a
 * Kubernetes Job through the K8s API (PRD §10.1).
 */
public interface MasterLauncher {

    /** @return the JobExecution id, or null when it is created asynchronously (k8s mode). */
    Long launch(String tenantId, String reportType, LocalDate businessDate);

    /** Restart a failed/stopped execution. @return the new JobExecution id, or null (k8s mode). */
    Long restart(long jobExecutionId);
}
