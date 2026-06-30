# Report Composer POC

A Proof of Concept for a **distributed Report Composer** that generates one report per
**account**, per **business date**, per **report type**, per **tenant (country)** — using
**Spring Batch Remote Partitioning** over **Apache Kafka**, running on **Kubernetes**
with autoscaling.

**Simple but functional:** the whole POC — **master and workers together** — runs on a
**single machine** with one command. The application **manages its own infrastructure**
(owns the Spring Batch lifecycle, provisions its Kafka topics, and creates the master
Job in k8s mode) and **auto-loads mock data on startup**, so you can generate a report
with a single API call — no manual data or topic setup.

> 📄 Full specification: [`specs/Report_Composer_POC_PRD.md`](specs/Report_Composer_POC_PRD.md).
> This project follows **Spec-Driven Development** — the PRD is the source of truth.

---

## What it does

A REST API triggers a **master** pod that discovers eligible accounts for a
`(tenant, reportType, businessDate)` job, creates **one partition per account**, and
distributes those partitions over a **Kafka** consumer group to a pool of **worker**
pods. Each worker queries the account's transactions, builds a report using a
**Strategy** selected by `reportType`, and persists it **idempotently**. A minimal
frontend triggers jobs and shows live status.

```
API/Scheduler
  -> Kubernetes Master Job        (Spring Batch manager step: discover + partition)
  -> Kafka                        (one partition per account -> consumer group)
  -> Worker Deployment            (autoscaled via HPA / optional KEDA)
  -> Remote Worker Step           (query transactions -> Strategy -> report)
  -> H2 (Oracle mode) + storage   (idempotent, restartable output)
```

## Key properties

- **One global `JobExecution`** per `(tenantId, reportType, businessDate)`.
- **One partition per `accountId`**; each worker thread runs exactly one remote step.
- **Idempotent & restartable** — re-running never duplicates a report; failed
  partitions restart without reprocessing completed ones.
- **New country = DB configuration only** (no code change).
- **New report type = one new `ReportTypeStrategy` implementation** (Strategy pattern).
- **Autoscalable** worker pods via **HPA** (optional **KEDA** on Kafka consumer lag).
- **Self-managed infra** — provisions Kafka topics, owns the batch lifecycle, creates
  the master Job (k8s mode); **auto-seeds mock data** on startup.
- **Single-machine** run: master + N workers via Docker Compose, one command.

## Technology stack

| Concern        | Choice                                                |
|----------------|-------------------------------------------------------|
| Language        | Java 21 (LTS)                                         |
| Build           | Maven (`./mvnw`)                                      |
| Framework       | Spring Boot 3.4.x                                     |
| Batch           | Spring Batch — Remote Partitioning                    |
| Messaging       | Apache Kafka (`spring-kafka`, `spring-batch-integration`) |
| Database        | H2 in Oracle compatibility mode (`MODE=Oracle`), Flyway |
| Frontend        | Lightweight SPA / static HTML+JS                      |
| Orchestration   | Kubernetes — master `Job` + worker `Deployment`       |
| Autoscaling     | HPA (CPU/memory); optional KEDA on Kafka lag          |
| Tests           | JUnit 5 + Mockito (`>80%` coverage target)            |

## Project layout (target)

```
report-composer-poc/
├── src/main/java/...      # api, batch (master/worker), strategy, domain, config
├── src/main/resources/    # application.yml (master/worker profiles), Flyway migrations
├── src/test/java/...       # JUnit 5 + Mockito
├── frontend/              # minimal UI to trigger + monitor jobs
├── k8s/                   # Job, Deployment, Service, ConfigMap, Secret, HPA, KEDA
├── scripts/               # start/stop .sh / .ps1 / .bat
├── Dockerfile
├── docker-compose.yml
└── Makefile
```

> The application code is being built against the PRD. The structure above is the
> target shape; see the spec for the authoritative breakdown.

## Getting started

> Scaffolding is in progress. Once the app is in place, the workflow will be:

```bash
# Build and test
./mvnw clean verify

# Run the full POC (master + N workers) on one machine via Docker Compose.
# On startup the app auto-creates Kafka topics, runs Flyway, and seeds mock data.
./scripts/start.sh                 # Linux/macOS  (wraps: docker compose up --scale worker=3)
.\scripts\start.ps1                # Windows PowerShell
scripts\start.bat                  # Windows cmd

# Trigger a report job (uses the auto-seeded tenant + business date)
curl -X POST http://localhost:8080/api/v1/jobs \
  -H 'Content-Type: application/json' \
  -d '{"tenantId":"BR","reportType":"ACCOUNT_STATEMENT","businessDate":"2026-06-30"}'

# Watch status, then stop
curl http://localhost:8080/api/v1/jobs        # list executions
./scripts/stop.sh
```

The same image runs as **api/master** or **worker** via `APP_ROLE`. In local/Compose
mode the **master step runs in-process and workers run as separate scaled containers on
the same machine** (`launcher=local`). On Kubernetes the master runs as a `Job` that the
API creates through the Kubernetes API, and workers run as a `Deployment` behind an
HPA — see `k8s/` and the local-Kubernetes walkthrough in the PRD.

## API (summary)

| Method | Path                           | Purpose                                  |
|--------|--------------------------------|------------------------------------------|
| POST   | `/api/v1/jobs`                 | Start a job (`tenantId`, `reportType`, `businessDate`) |
| GET    | `/api/v1/jobs/{id}`            | Job status + partition progress          |
| GET    | `/api/v1/jobs/{id}/partitions` | Per-account partition status             |
| POST   | `/api/v1/jobs/{id}/restart`    | Restart a failed job                     |
| GET    | `/api/v1/reports/{workUnitId}` | Download a generated report              |
| GET    | `/health`                      | Liveness/readiness (Actuator)            |

Swagger/OpenAPI docs are exposed by the backend.

## License

See [LICENSE](LICENSE).

## Author

**Wallace Espindola**
- Email: wallace.espindola@gmail.com
- LinkedIn: https://www.linkedin.com/in/wallaceespindola/
- GitHub: https://github.com/wallaceespindola/
