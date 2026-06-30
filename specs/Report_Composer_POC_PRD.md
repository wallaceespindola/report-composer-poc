# PRD — Report Composer POC

> **Spec-Driven Development.** This document is the single source of truth for the
> Report Composer POC. Behavior is specified here first, then implemented. Any change
> in scope, data model, API, or runtime topology updates this file in the same change.

---

## 1. Overview

Build a Proof of Concept for a **distributed Report Composer** that generates one
report per **account**, per **business date**, per **report type**, per **tenant
(country)**.

The system uses **Spring Batch Remote Partitioning** to fan a single logical job out
across many **worker pods**, coordinated over **Apache Kafka**, running on
**Kubernetes**. A **master** pod, triggered by a REST API, discovers eligible accounts,
creates one partition per account, and distributes partitions to a Kafka consumer
group. **Worker** pods consume partitions, query the account's transactions, and
produce a report whose content is determined by a **Strategy** selected per
`reportType`.

The POC is fully self-contained and runnable: **H2 in Oracle-compatible mode** stands
in for Oracle, a minimal **frontend** triggers and monitors jobs through the
**backend API**, and all infrastructure/config files, scaling manifests, and
start/stop scripts ship in the repo.

### 1.1 Goals

- Demonstrate a single global job that partitions work per account and executes those
  partitions concurrently across autoscaled workers.
- Make onboarding a **new tenant (country)** a configuration-only operation.
- Make adding a **new report type** a single new Strategy implementation.
- Be **idempotent** and **restartable**: re-running never duplicates reports; failed
  partitions can be retried/restarted.
- Be **autoscalable** on Kubernetes via **HPA** (and optionally KEDA on Kafka lag).
- Ship runnable: config, manifests, scripts, and tests included.

### 1.2 Non-Goals

- Production-grade auth, multi-region, or real Oracle/object-storage integration
  (H2 Oracle mode and local/MinIO-style storage are sufficient for the POC).
- Rich reporting UI — the frontend only needs to trigger jobs and show status.
- Exactly-once Kafka delivery semantics — idempotency is enforced at the data layer,
  not by the broker.

---

## 2. Technology Stack

| Concern            | Choice                                                        |
|--------------------|---------------------------------------------------------------|
| Language           | **Java 21** (LTS)                                             |
| Build              | **Maven** (multi-module), Maven Wrapper (`./mvnw`)           |
| Framework          | **Spring Boot 3.4.x**                                         |
| Batch              | **Spring Batch** with **Remote Partitioning**                |
| Messaging          | **Apache Kafka** (`spring-kafka`, `spring-batch-integration`)|
| Database           | **H2 in Oracle compatibility mode** (`MODE=Oracle`)          |
| Persistence        | Spring Data JPA / JDBC                                        |
| Object storage     | Local filesystem volume for the POC (S3/MinIO-compatible later)|
| Frontend           | Lightweight SPA (React or static HTML+JS) calling the API    |
| Orchestration      | **Kubernetes** — master Job + worker Deployment              |
| Autoscaling        | **HPA** (CPU/memory); optional **KEDA** on Kafka consumer lag|
| Tests              | **JUnit 5 + Mockito**; Spring Batch test utilities           |
| Containers         | Dockerfile per deployable; `docker-compose.yml` for local    |

Conventions: Java 21 language level, Lombok + Java Records for boilerplate/DTOs,
max line length 120, `>80%` test coverage target.

---

## 3. Domain & Execution Model

### 3.1 Job identity

**One global `JobExecution` per `(tenantId, reportType, businessDate)`.**

These three values are the unique job key. Triggering the same key while a job is
running is rejected (or returns the in-flight execution); triggering a completed key
restarts/reuses per Spring Batch restart semantics.

### 3.2 Partitioning

- **One partition per `accountId`.**
- The master's partition step discovers eligible accounts for the
  `(tenantId, reportType, businessDate)` and emits one partition each.
- Each partition's `ExecutionContext` carries: `tenantId`, `accountId`, `reportType`,
  `businessDate`.
- Each worker thread executes exactly **one remote `StepExecution`** at a time.

### 3.3 Worker step

For each partition a worker:

1. Reads the partition parameters from the `ExecutionContext`.
2. Queries the account's transactions for `businessDate`.
3. Resolves the `ReportTypeStrategy` bean for `reportType`.
4. Generates the report via the strategy.
5. Persists the report (DB row + object-storage artifact) **idempotently**.
6. Marks the `report_work_unit` complete.

### 3.4 Tenant = Country

`tenantId` represents a **country**. Tenant-specific behavior (locale, currency,
calendar/business-date rules, eligible-account query, output formatting) is driven by
**tenant configuration in the database**, not by code. Onboarding a new country =
insert a `tenant` row plus its configuration; **no code change**.

---

## 4. Functional Requirements

| ID    | Requirement                                                                       |
|-------|-----------------------------------------------------------------------------------|
| FR-1  | Trigger a job via REST API for a `(tenantId, reportType, businessDate)`.           |
| FR-2  | Master discovers eligible accounts for the job key.                               |
| FR-3  | Master creates exactly one partition per eligible account.                        |
| FR-4  | Partitions are distributed to workers via a Kafka consumer group.                 |
| FR-5  | Worker reads the account's transactions for the business date.                    |
| FR-6  | Worker generates a report using the Strategy bound to `reportType`.               |
| FR-7  | Worker persists report output idempotently (one report per account/day/type/tenant).|
| FR-8  | Transient failures are retried with backoff inside the worker step.               |
| FR-9  | Failed partitions can be restarted without reprocessing completed ones.           |
| FR-10 | Job status and per-partition progress are queryable via API and visible in the UI.|
| FR-11 | Onboarding a new tenant requires DB configuration only.                           |
| FR-12 | Adding a new report type requires only a new `ReportTypeStrategy` implementation. |

---

## 5. Backend API

Backend triggers and monitors Spring Batch jobs. All responses include a `timestamp`
field; expose Swagger/OpenAPI docs and a `/health` endpoint (Actuator).

| Method | Path                              | Purpose                                              |
|--------|-----------------------------------|------------------------------------------------------|
| POST   | `/api/v1/jobs`                    | Start a job. Body: `tenantId`, `reportType`, `businessDate`. Returns `jobExecutionId`. |
| GET    | `/api/v1/jobs/{id}`               | Job status + aggregate partition progress.           |
| GET    | `/api/v1/jobs/{id}/partitions`    | Per-account (`report_work_unit`) status list.        |
| POST   | `/api/v1/jobs/{id}/restart`       | Restart a failed/stopped job execution.              |
| GET    | `/api/v1/jobs`                    | List/filter executions by tenant/type/date/status.   |
| GET    | `/api/v1/reports/{workUnitId}`    | Fetch/download a produced report artifact.           |
| GET    | `/health`                         | Liveness/readiness (Actuator).                        |

Validation at the boundary: reject unknown `tenantId`/`reportType`, malformed
`businessDate`, and duplicate in-flight job keys.

---

## 6. Frontend

A lightweight UI (React SPA or static HTML+JS) that consumes the backend API. It is
intentionally minimal:

- Form to start a job: select **tenant (country)**, **report type**, **business date**.
- Jobs list with live status (running / completed / failed) and partition
  counts (completed / total / failed).
- Drill-down to per-account partition status.
- Restart button for failed jobs.
- Link to download a generated report.

---

## 7. Strategy Pattern (Report Types)

Report content is produced by a `ReportTypeStrategy` resolved by `reportType`.

```java
public interface ReportTypeStrategy {
    ReportType supports();                          // the reportType this handles
    Report generate(ReportContext ctx,              // tenant, account, businessDate
                    List<Transaction> transactions);
}
```

- Strategies are Spring beans discovered into a registry keyed by `reportType`.
- A `ReportStrategyResolver` returns the strategy for a given `reportType` (fails fast
  if none registered).
- **Adding a new report type = add one `@Component` implementing the interface.** No
  changes to orchestration, partitioning, API, or persistence.

The POC ships at least two strategies (e.g. `ACCOUNT_STATEMENT`, `TAX_SUMMARY`) to
prove the pattern.

---

## 8. Data Model

H2 in **Oracle compatibility mode** (`MODE=Oracle`); schema migrations via
Flyway/Liquibase (Flyway preferred). Spring Batch's own metadata tables are created
from the Oracle DDL variant.

### Application tables

| Table              | Purpose / key columns                                                                 |
|--------------------|---------------------------------------------------------------------------------------|
| `tenant`           | Country tenant. `tenant_id` (PK), `country_code`, `locale`, `currency`, `enabled`, config columns / config JSON. |
| `account`          | Accounts per tenant. `account_id` (PK), `tenant_id` (FK), `eligible` flag, attributes. |
| `transaction`      | Source transactions. `tenant_id`, `account_id`, `business_date`, amount, type, etc.   |
| `report_job`       | One row per `(tenant_id, report_type, business_date)`. Maps to a `JobExecution`; status, counts, timestamps. |
| `report_work_unit` | One row per partition/account. `(report_job_id, account_id)`; status, attempt count, output ref, **unique idempotency key** `(tenant_id, account_id, report_type, business_date)`. |
| `report_artifact`  | Stored report metadata: `work_unit_id`, location/URI, content type, checksum.          |

`report_work_unit` is where **idempotency** (unique key) and **restartability**
(per-partition status + attempt count) are enforced.

---

## 9. Messaging (Kafka)

- Spring Batch Remote Partitioning over Kafka via `spring-batch-integration`.
- Master publishes `StepExecutionRequest` messages to a **request topic**; workers
  form a **consumer group** so each partition is processed by exactly one worker.
- Workers send replies on a **reply topic** (or use the polling/aggregation variant)
  so the master can aggregate completion.

Recommended POC defaults:

| Parameter                     | Value   |
|-------------------------------|---------|
| Kafka partitions (request)    | 100     |
| Worker replicas               | 2–20    |
| Kafka consumer concurrency/pod| 2       |
| Reports per account/day/type  | 1       |

Topic partition count (100) bounds effective worker parallelism; replicas scale within
that bound.

---

## 10. Kubernetes Topology & Scaling

Master and workers are the **same application image** started in different **roles**
via a Spring profile / env var (`APP_ROLE=master|worker`).

| Component | K8s object   | Role     | Lifecycle                                              |
|-----------|--------------|----------|--------------------------------------------------------|
| Master    | **Job**      | `master` | Triggered by the API; runs the partition manager step, then completes. |
| Worker    | **Deployment** | `worker` | Long-lived consumers of the Kafka request topic.     |

### Autoscaling

- **HPA** on the worker Deployment (CPU and/or memory targets) — required for the POC.
- **Optional KEDA** `ScaledObject` scaling workers on **Kafka consumer-group lag** for
  responsiveness to backlog (scale-to-many on burst, down when drained).
- `min` / `max` replica bounds align with the 2–20 recommendation.

### Required config/manifest files (shipped in repo)

- `Dockerfile` (app image), `docker-compose.yml` (local: app + Kafka + H2/console).
- Kubernetes manifests under `k8s/` (or `deploy/`): namespace, ConfigMap, Secret,
  master `Job`, worker `Deployment`, `Service`, `HPA`, optional KEDA `ScaledObject`,
  Kafka (or reference to a Kafka operator/Strimzi), and ingress for API + frontend.
- Externalized config for tenants, topics, replica bounds, and DB connection.

---

## 11. Operational Scripts

Cross-platform start/stop scripts that bring the POC up and down locally (compose) and
optionally apply/delete the K8s manifests:

- `scripts/start.sh` / `scripts/stop.sh` (Linux/macOS)
- `scripts/start.ps1` / `scripts/stop.ps1` (Windows PowerShell)
- `scripts/start.bat` / `scripts/stop.bat` (Windows cmd)

Scripts should: build the image(s), start dependencies (Kafka, H2), launch master/worker
(compose) or `kubectl apply`/`delete` the manifests, and surface the API/frontend URLs.

---

## 12. Reliability: Retry, Restart, Idempotency

- **Retry**: worker step retries transient failures (DB blip, transient query error)
  with bounded backoff; non-transient failures fail the partition.
- **Restart**: a failed `JobExecution` is restartable; completed partitions are skipped,
  only failed/incomplete `report_work_unit`s re-run.
- **Idempotency**: the unique key on `report_work_unit`
  `(tenant_id, account_id, report_type, business_date)` plus an upsert on
  `report_artifact` guarantees re-processing never produces a duplicate report.

---

## 13. Testing

JUnit 5 + Mockito; `>80%` coverage target.

- **Unit**: each `ReportTypeStrategy`, the `ReportStrategyResolver`, tenant config
  resolution, eligible-account discovery, idempotency key logic (Mockito for
  repositories/collaborators).
- **Batch slice**: partition handler creates one partition per account; worker step
  reads transactions and writes a report; restart skips completed work units
  (`spring-batch-test`: `JobLauncherTestUtils`, `StepScopeTestUtils`).
- **API**: controller validation, duplicate-job rejection, status endpoints
  (`@WebMvcTest` + MockMvc, Mockito services).
- **Persistence**: idempotency unique-constraint enforcement against H2 Oracle mode.
- **Integration (optional)**: end-to-end trigger→partition→report with embedded Kafka.

---

## 14. Project Structure (target)

```
report-composer-poc/
├── pom.xml                      # parent (or single module for POC)
├── src/main/java/...            # api, batch (master/worker), strategy, domain, config
├── src/main/resources/
│   ├── application.yml          # profiles: master, worker; H2 Oracle mode; Kafka
│   └── db/migration/            # Flyway: schema + seed tenants/accounts/transactions
├── src/test/java/...            # JUnit 5 + Mockito
├── frontend/                    # minimal SPA or static UI
├── k8s/ (or deploy/)            # Job, Deployment, Service, ConfigMap, Secret, HPA, KEDA
├── scripts/                     # start/stop .sh/.ps1/.bat
├── Dockerfile
├── docker-compose.yml
└── Makefile
```

---

## 15. Acceptance Criteria

- [ ] One global `JobExecution` per `(tenantId, reportType, businessDate)`.
- [ ] Remote partitioning over Kafka: one partition per account.
- [ ] Concurrent processing across multiple worker pods (master Job + worker Deployment).
- [ ] Restart support: failed partitions re-run; completed ones are skipped.
- [ ] Idempotent reports: one report per account/day/type/tenant, never duplicated.
- [ ] New tenant (country) onboarded via **DB configuration only**.
- [ ] New report type added via a **single new Strategy implementation**.
- [ ] Autoscaling: worker **HPA** present (optional KEDA on Kafka lag).
- [ ] Backend API starts/monitors/restarts jobs; frontend triggers and shows status.
- [ ] H2 in Oracle-compatible mode with Flyway migrations and seed data.
- [ ] Start/stop scripts for `.sh`, `.ps1`, and `.bat`.
- [ ] JUnit 5 + Mockito tests, `>80%` coverage; build is green via `./mvnw test`.
- [ ] All config/manifest files for start and scaling present in the repo.

---

## Author

- **Wallace Espindola** — wallace.espindola@gmail.com
- LinkedIn: https://www.linkedin.com/in/wallaceespindola/
- GitHub: https://github.com/wallaceespindola/
