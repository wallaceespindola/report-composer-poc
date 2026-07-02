# Diagrams

Main diagrams for the Report Composer POC, each in two formats:

- **Mermaid** (`.mmd`) — rendered inline below (GitHub renders Mermaid natively).
- **PlantUML** (`.puml`) — full UML notation; render with `plantuml <file>.puml`
  or paste into https://www.plantuml.com/plantuml/uml/.

| Diagram | Mermaid | PlantUML |
|---------|---------|----------|
| Architecture / deployment | [architecture.mmd](architecture.mmd) | [architecture.puml](architecture.puml) |
| Components | [components.mmd](components.mmd) | [components.puml](components.puml) |
| Class (Strategy + batch core) | [class.mmd](class.mmd) | [class.puml](class.puml) |
| Job lifecycle sequence | [sequence.mmd](sequence.mmd) | [sequence.puml](sequence.puml) |
| Data model ERD | [erd.mmd](erd.mmd) | [erd.puml](erd.puml) |
| Data model classes (JPA entities) | [data-model-class.mmd](data-model-class.mmd) | [data-model-class.puml](data-model-class.puml) |

## Architecture / deployment (PRD §10)

Master and workers are the same image in different roles (`APP_ROLE`). In Compose mode
(`LAUNCHER_MODE=local`) the master step runs in-process in the API container instead of
a Kubernetes Job.

```mermaid
graph TB
    User[User / Scheduler]
    FE[Frontend<br/>static SPA - nginx :3000]

    subgraph K8S["Kubernetes namespace: report-composer"]
        API["API Deployment<br/>APP_ROLE=api<br/>REST + Swagger :8080"]
        MASTER["Master Job (per request)<br/>APP_ROLE=master<br/>manager step: discover + partition"]
        subgraph WORKERS["Worker Deployment (HPA 2..N, optional KEDA on lag)"]
            W1["Worker pod<br/>APP_ROLE=worker"]
            W2["Worker pod<br/>APP_ROLE=worker"]
        end
        KAFKA["Kafka (KRaft)<br/>report.partitions - 10 partitions<br/>consumer group report-workers"]
        H2[("H2 TCP server<br/>MODE=Oracle<br/>app tables + Spring Batch metadata")]
        MINIO[("MinIO<br/>bucket: reports")]
    end

    User --> FE
    FE -->|"/api proxy"| API
    API -->|"Fabric8: create Job<br/>(LAUNCHER_MODE=k8s)"| MASTER
    MASTER -->|"StepExecutionRequest<br/>per account"| KAFKA
    KAFKA --> W1
    KAFKA --> W2
    MASTER -->|"partitions + poll completion"| H2
    W1 --> H2
    W2 --> H2
    W1 -->|report artifact| MINIO
    W2 -->|report artifact| MINIO
    API -->|status, downloads| H2
    API -->|artifact stream| MINIO

    classDef app fill:#d4e8f0,stroke:#0277bd,color:#000
    classDef infra fill:#f0e8d8,stroke:#b8860b,color:#000
    classDef edge fill:#e8f5e9,stroke:#2f9e44,color:#000
    class API,MASTER,W1,W2 app
    class KAFKA,H2,MINIO infra
    class User,FE edge
```

Legend: blue = application (one image, role via `APP_ROLE`), gold = infrastructure,
green = edge/UI.

## Components

Package = responsibility. Hexagons are interfaces (extension points): `MasterLauncher`
(local vs k8s), `ReportTypeStrategy` (the agreed report-type catalog), `ArtifactStorage`.

```mermaid
graph LR
    subgraph API["api — REST boundary"]
        JC[JobController]
        TC[TenantController]
        RTC[ReportTypeController]
        RC[ReportController]
    end

    subgraph SVC["service"]
        JS["JobService<br/>contract validation + orchestration"]
        DS["DataSeeder<br/>idempotent mock data (api role)"]
        RDS[ReportDownloadService]
    end

    subgraph LAUNCH["launcher"]
        ML{{MasterLauncher}}
        LML["LocalMasterLauncher<br/>in-process, async"]
        KML["K8sMasterLauncher<br/>Fabric8 creates master Job"]
        MR["MasterRunner<br/>APP_ROLE=master entrypoint"]
    end

    subgraph MASTER["batch.master"]
        AP["AccountPartitioner<br/>1 partition per account"]
        MS["managerStep<br/>polling aggregation"]
    end

    subgraph WORKER["batch.worker"]
        WS[workerStep]
        WT["ReportWorkerTasklet<br/>generate + persist idempotently"]
    end

    subgraph STRAT["strategy — agreed catalog"]
        RSR[ReportStrategyResolver]
        RTS{{ReportTypeStrategy}}
        AS[AccountStatementStrategy]
        TS[TaxSummaryStrategy]
    end

    subgraph DATA["repository / storage"]
        REPO["Spring Data JPA repositories"]
        AST{{ArtifactStorage}}
        MAS[MinioArtifactStorage]
    end

    KAFKA[("Kafka")]
    H2[("H2 Oracle mode")]
    MINIO[("MinIO")]
    K8SAPI["Kubernetes API"]

    JC --> JS
    RC --> RDS
    JS --> ML
    ML -.-> LML
    ML -.-> KML
    KML --> K8SAPI
    LML --> MS
    MR --> MS
    MS --> AP
    MS -->|StepExecutionRequests| KAFKA
    KAFKA --> WS
    WS --> WT
    WT --> RSR
    RSR -.-> RTS
    RTS -.-> AS
    RTS -.-> TS
    WT --> AST
    AST -.-> MAS
    MAS --> MINIO
    JS --> REPO
    DS --> REPO
    WT --> REPO
    AP --> REPO
    RDS --> AST
    REPO --> H2

    classDef iface fill:#a6e3a1,stroke:#2f9e44,color:#000
    class ML,RTS,AST iface
```

## Class diagram — Strategy pattern + batch core

Adding a report type = one new `@Component` implementing `ReportTypeStrategy`; nothing
else changes (PRD §7). Idempotency lives in `ReportWorkUnit`'s unique key.

```mermaid
classDiagram
    direction LR

    class ReportTypeStrategy {
        <<interface>>
        +supports() String
        +description() String
        +generate(ReportContext, List~TransactionEntity~) GeneratedReport
    }
    class AccountStatementStrategy {
        supports = ACCOUNT_STATEMENT
    }
    class TaxSummaryStrategy {
        supports = TAX_SUMMARY
    }
    class ReportStrategyResolver {
        -Map~String,ReportTypeStrategy~ byType
        +resolve(reportType) ReportTypeStrategy
        +isRegistered(reportType) boolean
        +catalog() List~ReportTypeStrategy~
    }
    class ReportContext {
        <<record>>
        +Tenant tenant
        +String accountId
        +LocalDate businessDate
        +String contractParamsJson
    }
    class GeneratedReport {
        <<record>>
        +String fileName
        +String contentType
        +bytes content
    }

    class JobService {
        +start(tenantId, reportType, businessDate) Long
        +restart(jobExecutionId) Long
        +get(jobExecutionId) JobSummaryDto
        +partitions(jobExecutionId) List~PartitionDto~
        -validate() contractAndCatalogChecks
    }
    class MasterLauncher {
        <<interface>>
        +launch(tenantId, reportType, businessDate) Long
        +restart(jobExecutionId) Long
    }
    class LocalMasterLauncher {
        in-process async JobLauncher
    }
    class K8sMasterLauncher {
        Fabric8: create master Job from template
    }

    class AccountPartitioner {
        +partition(gridSize) Map~String,ExecutionContext~
        one partition per eligible account
    }
    class ReportWorkerTasklet {
        +execute(contribution, chunkContext)
        skip COMPLETED, retry, persist idempotently
    }
    class WorkUnitStateService {
        +markRunning(id) REQUIRES_NEW
        +markCompleted(id)
        +markFailed(id, error)
    }
    class ArtifactStorage {
        <<interface>>
        +put(objectKey, content, contentType)
        +get(objectKey) InputStream
    }

    class ReportJob {
        <<entity>>
        tenantId + reportType + businessDate (unique)
        jobExecutionId, status
    }
    class ReportWorkUnit {
        <<entity>>
        idempotency key: tenant+account+type+date (unique)
        status, attemptCount
    }

    ReportTypeStrategy <|.. AccountStatementStrategy
    ReportTypeStrategy <|.. TaxSummaryStrategy
    ReportStrategyResolver o-- ReportTypeStrategy : registry
    ReportTypeStrategy ..> ReportContext
    ReportTypeStrategy ..> GeneratedReport
    MasterLauncher <|.. LocalMasterLauncher
    MasterLauncher <|.. K8sMasterLauncher
    JobService --> MasterLauncher
    JobService --> ReportJob
    AccountPartitioner --> ReportJob : find-or-create
    AccountPartitioner --> ReportWorkUnit : insert-if-absent
    ReportWorkerTasklet --> ReportStrategyResolver
    ReportWorkerTasklet --> WorkUnitStateService
    ReportWorkerTasklet --> ArtifactStorage
    ReportWorkerTasklet --> ReportWorkUnit
    ReportJob "1" --> "*" ReportWorkUnit
```

## Job lifecycle sequence

Trigger → validate → launch master (k8s Job or in-process) → one partition per account
over Kafka → workers generate + persist idempotently → master polls completion → status
and download via the API. Restart re-runs only failed partitions (FR-9).

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant API as API (JobController/JobService)
    participant K8s as Kubernetes API
    participant Master as Master (manager step)
    participant Kafka as Kafka report.partitions
    participant Worker as Worker (consumer group)
    participant DB as H2 (shared job repo + app tables)
    participant MinIO

    User->>API: POST /api/v1/jobs {tenantId, reportType, businessDate}
    activate API
    API->>DB: validate tenant enabled + active contract<br/>+ registered strategy (FR-18)
    alt invalid
        API-->>User: 400/404/409 {timestamp, error}
    else valid
        API->>DB: upsert report_job (REQUESTED)
        alt LAUNCHER_MODE=k8s
            API->>K8s: create master Job (Fabric8, env = job key)
            K8s->>Master: schedule pod (APP_ROLE=master)
        else LAUNCHER_MODE=local
            API->>Master: run manager step in-process (async)
        end
        API-->>User: 202 {jobExecutionId}
    end
    deactivate API

    activate Master
    Master->>DB: discover eligible accounts,<br/>insert report_work_unit per account (idempotent)
    Master->>Kafka: StepExecutionRequest per partition<br/>(partition-{accountId})
    loop until all partitions terminal
        Master->>DB: poll step executions (polling aggregation)
    end

    Kafka->>Worker: request (each partition -> exactly one worker)
    activate Worker
    Worker->>DB: load ExecutionContext, work unit<br/>(skip if already COMPLETED)
    Worker->>DB: markRunning (attempt+1)
    Worker->>Worker: resolve strategy by reportType,<br/>generate report (retry w/ backoff on transient)
    Worker->>MinIO: put artifact (overwrite-by-key)
    Worker->>DB: upsert report_artifact, markCompleted
    deactivate Worker

    Master->>DB: all partitions done -> JobExecution + report_job COMPLETED
    deactivate Master

    User->>API: GET /api/v1/jobs/{id} / partitions
    API-->>User: status + completed/total/failed
    User->>API: GET /api/v1/reports/{workUnitId}
    API->>MinIO: get(objectKey)
    API-->>User: report file download
```

## Data model — ERD (PRD §8)

`report_work_unit` is where idempotency (unique key) and restartability
(status + attempt_count) are enforced. Spring Batch metadata tables share the same H2
database (Flyway V1) but are managed by Spring Batch and omitted here.

```mermaid
erDiagram
    TENANT {
        varchar2_10 tenant_id PK "country code, e.g. BE"
        varchar2_2 country_code
        varchar2_20 locale
        varchar2_3 currency
        boolean enabled
        clob config_json
        timestamp created_at
    }

    TENANT_REPORT_CONTRACT {
        bigint id PK
        varchar2_10 tenant_id FK
        varchar2_50 report_type "UK with tenant_id"
        boolean enabled
        date effective_from
        date effective_to
        clob params_json "output format, thresholds"
    }

    ACCOUNT {
        varchar2_40 account_id PK
        varchar2_10 tenant_id FK
        varchar2_100 customer_name
        boolean eligible "drives partition discovery"
    }

    TRANSACTION {
        bigint id PK
        varchar2_10 tenant_id
        varchar2_40 account_id FK
        date business_date
        number_18_2 amount
        varchar2_3 currency
        varchar2_20 txn_type
        varchar2_200 description
    }

    REPORT_JOB {
        bigint id PK
        varchar2_10 tenant_id "UK(tenant, type, date)"
        varchar2_50 report_type
        date business_date
        bigint job_execution_id "Spring Batch JobExecution"
        varchar2_20 status
        timestamp created_at
        timestamp updated_at
    }

    REPORT_WORK_UNIT {
        bigint id PK
        bigint report_job_id FK
        varchar2_10 tenant_id "idempotency UK(tenant, account, type, date)"
        varchar2_40 account_id
        varchar2_50 report_type
        date business_date
        varchar2_20 status "PENDING RUNNING COMPLETED FAILED"
        int attempt_count
        varchar2_2000 error_message
        timestamp updated_at
    }

    REPORT_ARTIFACT {
        bigint id PK
        bigint work_unit_id FK "unique - one artifact per work unit"
        varchar2_500 object_key "MinIO key, overwrite-by-key"
        varchar2_100 content_type
        bigint size_bytes
        varchar2_64 checksum "sha-256"
        timestamp created_at
    }

    TENANT ||--o{ TENANT_REPORT_CONTRACT : "contracted for (onboarding = insert rows)"
    TENANT ||--o{ ACCOUNT : owns
    ACCOUNT ||--o{ TRANSACTION : has
    REPORT_JOB ||--o{ REPORT_WORK_UNIT : "1 per account partition"
    REPORT_WORK_UNIT ||--o| REPORT_ARTIFACT : produces
```

## Data model — JPA entity classes

```mermaid
classDiagram
    direction LR

    class Tenant {
        <<entity tenant>>
        +String tenantId
        +String countryCode
        +String locale
        +String currency
        +boolean enabled
        +String configJson
        +Instant createdAt
    }

    class TenantReportContract {
        <<entity tenant_report_contract>>
        +Long id
        +String tenantId
        +String reportType
        +boolean enabled
        +LocalDate effectiveFrom
        +LocalDate effectiveTo
        +String paramsJson
        +isActiveOn(businessDate) boolean
    }

    class Account {
        <<entity account>>
        +String accountId
        +String tenantId
        +String customerName
        +boolean eligible
    }

    class TransactionEntity {
        <<entity transaction>>
        +Long id
        +String tenantId
        +String accountId
        +LocalDate businessDate
        +BigDecimal amount
        +String currency
        +String txnType
        +String description
    }

    class ReportJob {
        <<entity report_job>>
        +Long id
        +String tenantId
        +String reportType
        +LocalDate businessDate
        +Long jobExecutionId
        +ReportJobStatus status
        +Instant createdAt
        +Instant updatedAt
    }

    class ReportWorkUnit {
        <<entity report_work_unit>>
        +Long id
        +Long reportJobId
        +String tenantId
        +String accountId
        +String reportType
        +LocalDate businessDate
        +WorkUnitStatus status
        +int attemptCount
        +String errorMessage
        +Instant updatedAt
    }

    class ReportArtifact {
        <<entity report_artifact>>
        +Long id
        +Long workUnitId
        +String objectKey
        +String contentType
        +long sizeBytes
        +String checksum
        +Instant createdAt
    }

    class ReportJobStatus {
        <<enumeration>>
        REQUESTED
        STARTED
        COMPLETED
        FAILED
        RESTARTING
    }

    class WorkUnitStatus {
        <<enumeration>>
        PENDING
        RUNNING
        COMPLETED
        FAILED
    }

    Tenant "1" --> "*" TenantReportContract : contracts
    Tenant "1" --> "*" Account : owns
    Account "1" --> "*" TransactionEntity : transactions
    ReportJob "1" --> "*" ReportWorkUnit : partitions
    ReportWorkUnit "1" --> "0..1" ReportArtifact : artifact
    ReportJob --> ReportJobStatus
    ReportWorkUnit --> WorkUnitStatus

    note for ReportJob "unique (tenantId, reportType, businessDate) - one job key"
    note for ReportWorkUnit "idempotency unique key (tenantId, accountId, reportType, businessDate)"
```
