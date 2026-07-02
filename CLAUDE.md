# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project status

Fully implemented, spec-driven POC. The single source of truth is
`docs/specs/Report_Composer_POC_PRD.md` — follow Spec-Driven Development: behavior changes
update the spec first, then the code.

Stack: Java 21, Spring Boot 3.4.x, Maven single module, Spring Batch Remote
Partitioning over Kafka (`spring-batch-integration`), H2 in Oracle mode + Flyway,
MinIO, Fabric8 (k8s master Job), JUnit 5 + Mockito.

## Build / test / run commands

```bash
./mvnw -B verify                       # build + tests + JaCoCo gate (>=80% line)
./mvnw test -Dtest=JobServiceTest      # single test class
./mvnw test -Dtest=JobServiceTest#startRejectsUnknownTenant   # single test method
./scripts/start.sh        # full POC via Docker Compose (api + 3 workers + kafka/h2/minio)
./scripts/start.sh k8s    # local Kubernetes path (minikube/kind)
make up | down | test | image | up-k8s | down-k8s
```

Manager/worker roles: same app, selected by `APP_ROLE=api|master|worker` env var.
`LAUNCHER_MODE=local` runs the manager step in-process in the api role (Compose path);
`LAUNCHER_MODE=k8s` makes the API create a master `Job` via Fabric8.

## Architecture map (package = responsibility)

Base package: `com.wallaceespindola.reportcomposer`

- `batch/master/` — `AccountPartitioner` (one partition per account + creates
  `report_job`/`report_work_unit` rows idempotently), `ManagerBatchConfig` (manager step,
  polling aggregation variant — workers report through the shared job repository, no
  reply-channel aggregation), `ReportJobExecutionListener` (syncs `report_job` row).
- `batch/worker/` — `WorkerBatchConfig` (Kafka consumer group -> worker step),
  `ReportWorkerTasklet` (one partition: load txns -> resolve strategy -> MinIO ->
  artifact row), `WorkUnitStateService` (REQUIRES_NEW status transitions so FAILED
  survives the step rollback).
- `batch/BatchMessageSerde` — Java serialization of `StepExecutionRequest` over Kafka;
  the ExecutionContext itself travels through the shared H2 job repository, not Kafka.
- `strategy/` — `ReportTypeStrategy` interface + `ReportStrategyResolver` registry.
  **Adding a report type = one new `@Component` implementing the interface. Nothing else.**
- `launcher/` — `LocalMasterLauncher` (async in-process), `K8sMasterLauncher` (Fabric8,
  loads `k8s/templates/master-job-template.yaml`), `MasterRunner` (APP_ROLE=master entrypoint,
  runs sync then exits).
- `service/JobService` — boundary validation (tenant enabled, active
  `tenant_report_contract`, registered strategy) + launch/restart; `DataSeeder` seeds
  accounts/transactions only in the api role, only when the account table is empty.
- `config/` — `AppProperties` (all env config, PRD §13), Kafka topic provisioning
  (`NewTopic` beans — the app owns topic creation), launchers, MinIO client.

Flyway migrations (`src/main/resources/db/migration/`): V1 = Spring Batch metadata
schema (do not let Boot initialize it — `spring.batch.jdbc.initialize-schema: never`),
V2 = app schema, V3 = seed tenants (BE, FR, ES) + contracts.

## Design invariants — preserve these

- One global `JobExecution` per `(tenantId, reportType, businessDate)` (identifying job
  parameters). One partition per `accountId`, named `partition-{accountId}` (stable
  names are what make Spring Batch restarts skip completed partitions).
- Idempotency lives in the DB unique key `report_work_unit(tenant, account, type, date)`
  + deterministic MinIO object keys (overwrite-by-key). Don't move it to Kafka semantics.
- Workers are generic: tenant onboarding must stay DB-config-only (`tenant` +
  `tenant_report_contract` rows), no code change, no worker redeploy.
- The API rejects jobs without an active contract or registered strategy (FR-18) —
  keep validation at the boundary in `JobService`.
- Role-conditional beans: manager config is active for roles `api` and `master`; worker
  config only for `worker`; seeder only for `api`. Watch the `@ConditionalOn*`
  expressions when adding beans so worker pods don't need api-only dependencies.

## Conventions

- Lombok + records; DTOs are records under `api/dto`. Max line length 120.
- Tests mirror main packages; Mockito for collaborators; `@WebMvcTest` for controllers;
  `@DataJpaTest` with `MODE=Oracle` URL for persistence. Coverage gate: 80% line
  (config/wiring classes are excluded in the jacoco plugin config).
- `frontend/` is plain HTML/JS/CSS with no build step; Maven copies it into
  `static/` so the API serves it too. Keep fetch URLs relative.
- All API responses include `timestamp`; errors use `{timestamp, status, error, message}`.
