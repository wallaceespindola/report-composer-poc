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

The POC is fully self-contained and runnable on a **local developer machine**: H2 in
Oracle-compatible mode stands in for Oracle, a minimal **frontend** triggers and
monitors jobs through the **backend API**, **MinIO** stands in for object storage, and
all infrastructure/config files, scaling manifests, and start/stop scripts ship in the
repo — runnable both via **Docker Compose** and on a **local Kubernetes** cluster
(minikube or kind).

### 1.1 Goals

- Demonstrate a single global job that partitions work per account and executes those
  partitions concurrently across autoscaled workers.
- Make onboarding a **new tenant (country)** a configuration-only operation.
- Make adding a **new report type** a single new Strategy implementation.
- Be **idempotent** and **restartable**: re-running never duplicates reports; failed
  partitions can be retried/restarted.
- Be **autoscalable** on Kubernetes via **HPA** (and optionally KEDA on Kafka lag).
- **Run end-to-end on a laptop** with one command, both in Compose and local k8s.

### 1.2 Non-Goals

- Production-grade auth, multi-region, or real Oracle/object-storage integration
  (H2 Oracle mode + MinIO are sufficient for the POC).
- Rich reporting UI — the frontend only needs to trigger jobs and show status.
- Exactly-once Kafka delivery semantics — idempotency is enforced at the data layer,
  not by the broker.

---

## 2. Technology Stack & Prerequisites

| Concern            | Choice                                                        |
|--------------------|---------------------------------------------------------------|
| Language           | **Java 21** (LTS)                                             |
| Build              | **Maven** (multi-module), Maven Wrapper (`./mvnw`)           |
| Framework          | **Spring Boot 3.4.x**                                         |
| Batch              | **Spring Batch** with **Remote Partitioning**                |
| Messaging          | **Apache Kafka** (KRaft mode, no ZooKeeper)                  |
| K8s job control    | **Fabric8 Kubernetes Client** (API spawns the master `Job`) |
| Database           | **H2 in Oracle compatibility mode** (`MODE=Oracle`), TCP server |
| Persistence        | Spring Data JPA / JDBC; **Flyway** migrations                |
| Object storage     | **MinIO** (S3-compatible) — report artifacts                 |
| Frontend           | Lightweight SPA (React or static HTML+JS) calling the API    |
| Orchestration      | **Kubernetes** — master Job + worker Deployment              |
| Autoscaling        | **HPA** (CPU/memory); optional **KEDA** on Kafka consumer lag|
| Observability      | Spring Boot Actuator + Micrometer/Prometheus                 |
| Tests              | **JUnit 5 + Mockito**; `spring-batch-test`; Testcontainers (optional) |
| Containers         | Dockerfile per deployable; `docker-compose.yml` for local    |
| CI                 | **GitHub Actions** (build, test, image build)                |

### 2.1 Local prerequisites

Pin and document concrete versions in the repo (`.tool-versions` / README):

- **JDK 21**, Maven (or `./mvnw`).
- **Docker** + **Docker Compose v2**.
- For local Kubernetes: **kubectl**, and one of **minikube** or **kind**, plus
  **Helm** (for Kafka/MinIO charts) and **metrics-server** (HPA dependency).

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
5. Persists the report (DB row + MinIO artifact) **idempotently**.
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
| FR-13 | The whole POC starts locally with one command (Compose **and** local k8s).        |

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
| GET    | `/api/v1/reports/{workUnitId}`    | Fetch/download a produced report artifact (from MinIO).|
| GET    | `/api/v1/tenants`                 | List configured tenants (countries) for the UI.      |
| GET    | `/api/v1/report-types`           | List available report types (registered strategies). |
| GET    | `/health`, `/actuator/*`          | Health, metrics, Prometheus scrape.                  |

Validation at the boundary: reject unknown `tenantId`/`reportType`, malformed
`businessDate`, and duplicate in-flight job keys. CORS is enabled for the frontend
origin.

---

## 6. Frontend

A lightweight UI (React SPA or static HTML+JS) that consumes the backend API. It is
intentionally minimal:

- Form to start a job: select **tenant (country)** and **report type** (populated from
  `/api/v1/tenants` and `/api/v1/report-types`), pick a **business date**.
- Jobs list with live status (running / completed / failed) and partition
  counts (completed / total / failed).
- Drill-down to per-account partition status.
- Restart button for failed jobs.
- Link to download a generated report.

Served either as static assets by the backend or as a separate `nginx` container /
pod.

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

H2 in **Oracle compatibility mode** (`MODE=Oracle`), run as a **TCP server** so master
and workers share one database (Spring Batch metadata + app tables must be shared).
Schema and seed data via **Flyway** (Oracle-dialect DDL).

### Application tables

| Table              | Purpose / key columns                                                                 |
|--------------------|---------------------------------------------------------------------------------------|
| `tenant`           | Country tenant. `tenant_id` (PK), `country_code`, `locale`, `currency`, `enabled`, config columns / config JSON. |
| `account`          | Accounts per tenant. `account_id` (PK), `tenant_id` (FK), `eligible` flag, attributes. |
| `transaction`      | Source transactions. `tenant_id`, `account_id`, `business_date`, amount, type, etc.   |
| `report_job`       | One row per `(tenant_id, report_type, business_date)`. Maps to a `JobExecution`; status, counts, timestamps. |
| `report_work_unit` | One row per partition/account. `(report_job_id, account_id)`; status, attempt count, output ref, **unique idempotency key** `(tenant_id, account_id, report_type, business_date)`. |
| `report_artifact`  | Stored report metadata: `work_unit_id`, MinIO object key/URI, content type, checksum.  |

`report_work_unit` is where **idempotency** (unique key) and **restartability**
(per-partition status + attempt count) are enforced.

Seed data (Flyway): several tenants (e.g. `BR`, `US`, `PT`), enough accounts and
transactions per tenant to make partitioning/scaling observable (hundreds of accounts).

---

## 9. Messaging (Kafka)

- Spring Batch Remote Partitioning over Kafka via `spring-batch-integration`.
- Master publishes `StepExecutionRequest` messages to a **request topic**; workers
  form a **consumer group** so each partition is processed by exactly one worker.
- Workers send replies on a **reply topic** (or use the polling/aggregation variant)
  so the master can aggregate completion.
- Topics are **explicitly provisioned** (not relying on auto-create) so partition count
  is deterministic.

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

| Component | K8s object     | Role     | Lifecycle                                              |
|-----------|----------------|----------|--------------------------------------------------------|
| API       | Deployment     | `api`    | Always-on; receives REST calls, creates master Jobs.   |
| Master    | **Job**        | `master` | Created per job request; runs the partition manager step, then completes. |
| Worker    | **Deployment** | `worker` | Long-lived consumers of the Kafka request topic.       |

### 10.1 How the API starts the master (gap closed)

The API does **not** run the manager step in-process. On `POST /api/v1/jobs` it uses
the **Fabric8 Kubernetes client** to create a Kubernetes **`Job`** (the master) from a
templated manifest, injecting `tenantId`, `reportType`, `businessDate` as env/args.
This requires:

- A dedicated **ServiceAccount** for the API.
- A **Role** granting `create/get/list/watch` on `jobs` (and `pods` for status) in the
  namespace, bound via **RoleBinding**.
- Job template carries `APP_ROLE=master`, the job key params, `ttlSecondsAfterFinished`
  for cleanup, and `backoffLimit`/restart policy.

For **Docker Compose** (no K8s API), the API instead launches the manager step
in-process or signals a master service — selected by a profile (`launcher=k8s|local`).

### 10.2 Object storage

Report artifacts are written to **MinIO** (S3-compatible). On K8s, MinIO runs as a
Deployment + Service + PVC; in Compose it is a `minio` service with a bucket created at
startup. Using object storage (not a shared filesystem) avoids `ReadWriteMany` PVC
requirements across workers.

### 10.3 Autoscaling

- **HPA** on the worker Deployment (CPU and/or memory targets) — **required**. Workers
  **must declare resource `requests`/`limits`**, or HPA cannot compute utilization.
- **Optional KEDA** `ScaledObject` scaling workers on **Kafka consumer-group lag** for
  responsiveness to backlog (scale-to-many on burst, down when drained).
- `min` / `max` replica bounds align with the 2–20 recommendation.
- Workers handle **graceful shutdown** (finish the in-flight partition, commit offset)
  so scale-down does not orphan work; restart re-runs anything left incomplete.

### 10.4 Required manifests (shipped under `k8s/`)

namespace, ConfigMap, Secret, API `Deployment` + `Service`, RBAC (ServiceAccount/Role/
RoleBinding), master `Job` template, worker `Deployment` + `Service`, `HPA`, optional
KEDA `ScaledObject`, Kafka (Helm/Strimzi or manifest), MinIO (Deployment/Service/PVC),
H2 (Deployment/Service) or Postgres alternative, and `Ingress` for API + frontend.

---

## 11. Local Development & Runtime

The POC must run on a laptop two ways. Both paths are documented in the README and
wrapped by the start/stop scripts (§12).

### 11.1 Docker Compose (default, fastest)

`docker-compose.yml` defines the full stack:

| Service     | Image / build            | Purpose                                  | Host port |
|-------------|--------------------------|------------------------------------------|-----------|
| `kafka`     | Kafka in **KRaft** mode  | Messaging (no ZooKeeper)                  | 9092      |
| `kafka-ui`  | kafka-ui (optional)      | Inspect topics/consumer groups           | 8082      |
| `h2`        | H2 TCP server (Oracle mode) | Shared DB for master + workers        | 9093      |
| `minio`     | minio + `mc` init        | Object storage + bucket bootstrap        | 9000/9001 |
| `api`       | app image (`APP_ROLE=api`) | REST API + master launcher (local mode) | 8080      |
| `worker`    | app image (`APP_ROLE=worker`) | Batch workers; `--scale worker=N`     | —         |
| `frontend`  | nginx (or served by api) | UI                                       | 3000      |

- Start: `docker compose up -d --build --scale worker=3`.
- Health checks + `depends_on` ordering so the app waits for Kafka/H2/MinIO.
- Note the H2 TCP port is mapped to **9093** to avoid colliding with Kafka's 9092.
- Demonstrate scaling by changing `--scale worker=N` and re-triggering a job.

### 11.2 Local Kubernetes (minikube or kind)

Documented end-to-end so the K8s topology (incl. HPA and the API-spawns-master flow)
can be exercised locally:

1. **Create cluster**: `minikube start` (or `kind create cluster`).
2. **Enable metrics**: `minikube addons enable metrics-server` (HPA needs it; for kind
   install metrics-server manually).
3. **Build & load image** into the cluster: `minikube image load <img>` /
   `kind load docker-image <img>` (or `eval $(minikube docker-env)` then build).
4. **Install dependencies**: Kafka (Strimzi operator or Bitnami Helm chart), MinIO and
   H2 via the manifests in `k8s/`.
5. **Apply app manifests**: `kubectl apply -f k8s/` (namespace, RBAC, ConfigMap/Secret,
   API Deployment/Service, worker Deployment/Service, HPA, Ingress).
6. **Access**: `kubectl port-forward svc/report-composer-api 8080:8080` (and the
   frontend), or enable `minikube tunnel` / ingress.
7. **Trigger a job**: `POST /api/v1/jobs` → API creates the master `Job` via the K8s
   API; watch with `kubectl get jobs,pods -w`.
8. **Observe autoscaling**: generate backlog and watch `kubectl get hpa -w` scale the
   worker Deployment.

All of the above is captured as scripted steps (§12) and `Makefile` targets so it is
one command per path.

---

## 12. Operational Scripts

Cross-platform start/stop scripts that bring the POC up and down. Each supports a target
(`compose` default, or `k8s`):

- `scripts/start.sh` / `scripts/stop.sh` (Linux/macOS)
- `scripts/start.ps1` / `scripts/stop.ps1` (Windows PowerShell)
- `scripts/start.bat` / `scripts/stop.bat` (Windows cmd)

Behavior:

- `start … compose`: build image(s), `docker compose up` with the worker scale, wait for
  health, print the API/frontend/MinIO/kafka-ui URLs.
- `start … k8s`: ensure cluster + metrics-server, build/load image, install Kafka/MinIO,
  `kubectl apply -f k8s/`, port-forward, print URLs.
- `stop …`: `docker compose down -v` or `kubectl delete -f k8s/` (and optionally tear
  down the local cluster).

A `Makefile` provides equivalent targets (`make up`, `make up-k8s`, `make down`,
`make test`, `make image`).

---

## 13. Configuration Reference

All configuration is externalized (env vars / ConfigMap / Spring profiles). Core keys:

| Key                              | Example                          | Used by        |
|----------------------------------|----------------------------------|----------------|
| `APP_ROLE`                       | `api` \| `master` \| `worker`    | all            |
| `SPRING_PROFILES_ACTIVE`         | `compose` \| `k8s`               | all            |
| `LAUNCHER_MODE`                  | `local` \| `k8s`                 | api            |
| `DB_URL`                         | `jdbc:h2:tcp://h2:9092/report;MODE=Oracle` | all   |
| `KAFKA_BOOTSTRAP_SERVERS`        | `kafka:9092`                     | master, worker |
| `KAFKA_REQUEST_TOPIC`            | `report.partitions`              | master, worker |
| `KAFKA_REPLY_TOPIC`              | `report.replies`                 | master, worker |
| `KAFKA_REQUEST_PARTITIONS`       | `100`                            | provisioning   |
| `KAFKA_CONCURRENCY`              | `2`                              | worker         |
| `WORKER_MIN_REPLICAS` / `_MAX`   | `2` / `20`                       | HPA/KEDA       |
| `MINIO_ENDPOINT` / `_BUCKET`     | `http://minio:9000` / `reports`  | worker, api    |
| `MINIO_ACCESS_KEY` / `_SECRET`   | (Secret)                         | worker, api    |
| `K8S_NAMESPACE` / `MASTER_JOB_TEMPLATE` | `report-composer` / path  | api            |

Secrets (DB creds, MinIO keys) come from a K8s `Secret` / `.env` (never committed).

---

## 14. Observability

- **Actuator** endpoints: `/health` (liveness/readiness probes), `/actuator/prometheus`
  for metrics.
- **Micrometer/Prometheus** metrics: job duration, partitions total/completed/failed,
  worker throughput, retry counts, Kafka consumer lag.
- **Structured logging** with the job key `(tenant, reportType, businessDate)` and
  `accountId` in MDC for traceability across master/worker.
- Kafka consumer-group lag is the scaling signal for the optional KEDA path.

---

## 15. Reliability: Retry, Restart, Idempotency

- **Retry**: worker step retries transient failures (DB blip, transient query error)
  with bounded backoff; non-transient failures fail the partition.
- **Restart**: a failed `JobExecution` is restartable; completed partitions are skipped,
  only failed/incomplete `report_work_unit`s re-run.
- **Idempotency**: the unique key on `report_work_unit`
  `(tenant_id, account_id, report_type, business_date)` plus an upsert on
  `report_artifact` (and overwrite-by-key in MinIO) guarantees re-processing never
  produces a duplicate report.

---

## 16. Testing

JUnit 5 + Mockito; `>80%` coverage target.

- **Unit**: each `ReportTypeStrategy`, the `ReportStrategyResolver`, tenant config
  resolution, eligible-account discovery, idempotency key logic, the K8s master-launcher
  (Fabric8 client mocked) (Mockito for repositories/collaborators).
- **Batch slice**: partition handler creates one partition per account; worker step
  reads transactions and writes a report; restart skips completed work units
  (`spring-batch-test`: `JobLauncherTestUtils`, `StepScopeTestUtils`).
- **API**: controller validation, duplicate-job rejection, status endpoints
  (`@WebMvcTest` + MockMvc, Mockito services).
- **Persistence**: idempotency unique-constraint enforcement against H2 Oracle mode.
- **Integration (optional)**: end-to-end trigger→partition→report with **embedded
  Kafka** / Testcontainers (Kafka + MinIO).

---

## 17. CI/CD

GitHub Actions workflow under `.github/workflows/`:

- On push/PR: `./mvnw -B clean verify` (compile, test, coverage gate), cache Maven deps.
- Build the container image (and optionally push to a registry / load for e2e).
- Lint/format check (Spotless or equivalent) and report coverage.

---

## 18. Project Structure (target)

```
report-composer-poc/
├── pom.xml                      # parent (or single module for POC)
├── src/main/java/...            # api, batch (master/worker), strategy, domain, k8s-launcher, config
├── src/main/resources/
│   ├── application.yml          # profiles: api/master/worker, compose/k8s; H2 Oracle; Kafka
│   └── db/migration/            # Flyway: schema + seed tenants/accounts/transactions
├── src/test/java/...            # JUnit 5 + Mockito + spring-batch-test
├── frontend/                    # minimal SPA or static UI
├── k8s/                         # namespace, RBAC, Job, Deployment, Service, ConfigMap, Secret, HPA, KEDA, MinIO, H2, Ingress
├── scripts/                     # start/stop .sh/.ps1/.bat (compose + k8s targets)
├── .github/workflows/           # CI
├── Dockerfile
├── docker-compose.yml
└── Makefile
```

---

## 19. Acceptance Criteria

- [ ] One global `JobExecution` per `(tenantId, reportType, businessDate)`.
- [ ] Remote partitioning over Kafka: one partition per account.
- [ ] Concurrent processing across multiple worker pods (master Job + worker Deployment).
- [ ] API spawns the master as a Kubernetes `Job` via the K8s API (RBAC in place).
- [ ] Restart support: failed partitions re-run; completed ones are skipped.
- [ ] Idempotent reports: one report per account/day/type/tenant, never duplicated.
- [ ] New tenant (country) onboarded via **DB configuration only**.
- [ ] New report type added via a **single new Strategy implementation**.
- [ ] Autoscaling: worker **HPA** present and functional (resource requests set);
      optional KEDA on Kafka lag.
- [ ] Backend API starts/monitors/restarts jobs; frontend triggers and shows status.
- [ ] H2 in Oracle-compatible mode (TCP server) with Flyway migrations and seed data.
- [ ] **Runs locally via Docker Compose** with `--scale worker=N`.
- [ ] **Runs on local Kubernetes** (minikube/kind) with documented, scripted steps.
- [ ] MinIO object storage for report artifacts.
- [ ] Start/stop scripts for `.sh`, `.ps1`, and `.bat`, covering compose **and** k8s.
- [ ] JUnit 5 + Mockito tests, `>80%` coverage; build is green via `./mvnw verify`.
- [ ] GitHub Actions CI builds, tests, and packages the image.
- [ ] All config/manifest files for start and scaling present in the repo.

---

## Author

- **Wallace Espindola** — wallace.espindola@gmail.com
- LinkedIn: https://www.linkedin.com/in/wallaceespindola/
- GitHub: https://github.com/wallaceespindola/
