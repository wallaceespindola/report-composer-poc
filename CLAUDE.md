# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project status

This is a **greenfield, spec-driven POC**. As of now the repo contains only the
product spec (`specs/Report_Composer_POC_PRD.md`), `README.md`, `LICENSE`, and a
Java-oriented `.gitignore`. There is **no build system, source code, or tests yet**.

The single source of truth for what to build is `specs/Report_Composer_POC_PRD.md`.
Read it before implementing anything. Follow Spec-Driven Development: changes to
behavior should be reflected in the spec first, then implemented.

When scaffolding the project, default to the stack the spec and architecture imply
(see below) and the Java standards in the global preferences: Java 21+, Spring Boot
3.4.x, Maven, JUnit 5 + Mockito.

## What this system is

A distributed **Report Composer**: it fans out report generation across many worker
pods using **Spring Batch Remote Partitioning** over **Apache Kafka**, running on
**OpenShift**, reading/writing **Oracle** plus object storage.

## Architecture (from the PRD)

```
API/Scheduler
  -> OpenShift Master Job        (the Spring Batch "manager")
  -> Spring Batch Manager Step   (discovers accounts, creates partitions)
  -> Kafka                       (carries StepExecutionRequests to workers)
  -> Worker Deployment           (scaled 2-20 replicas via KEDA)
  -> Remote Worker Step          (one StepExecution per partition)
  -> Oracle + Object Storage     (persisted report output)
```

Key design invariants — preserve these when implementing:

- **One global `JobExecution` per `(tenantId, reportType, businessDate)`.**
- **One partition per `accountId`.** Each worker thread executes exactly one remote
  `StepExecution`. ExecutionContext carries `tenantId`, `accountId`, `reportType`,
  `businessDate`.
- **Manager is an OpenShift Job; workers are a Deployment + KEDA** (autoscaled on
  Kafka lag). Manager and workers are the same app in different roles, not two
  codebases.
- **Idempotent outputs** — re-running a partition must not duplicate reports
  (one report per account/day). Restart of failed partitions must be supported.
- **Strategy Pattern for report types**: a `ReportTypeStrategy` is resolved by
  `reportType`. Adding a new report type = a new Strategy implementation, no changes
  to the partitioning/orchestration core.
- **Multi-tenant via DB config**: onboarding a new tenant must require database
  configuration only (no code change).

## Data model (from the PRD)

Tables: `tenant`, `report_job`, `report_work_unit`. `report_work_unit` is the
per-account/partition unit of work and is where idempotency and restartability are
tracked.

## Recommended runtime defaults (from the PRD)

- Worker replicas: 2–20
- Kafka partitions: 100
- Kafka consumer concurrency per pod: 2

## Build/run commands

None defined yet. Once the project is scaffolded as a Maven Spring Boot app, this
section should be updated with the real commands (e.g. `./mvnw test`,
`./mvnw spring-boot:run`, single-test invocation, and how to start the app in
manager vs. worker role).
