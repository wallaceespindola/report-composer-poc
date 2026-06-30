# PRD — Report Composer POC

## 1. Overview
Build a Proof of Concept for a distributed Report Composer using Spring Batch Remote Partitioning, Apache Kafka, OpenShift, Oracle Database and the Strategy Pattern following Spec-Driven Development.

## Architecture
API/Scheduler
 -> OpenShift Master Job
 -> Spring Batch Manager Step
 -> Kafka
 -> Worker Deployment
 -> Remote Worker Step
 -> Oracle + Object Storage

## Execution Model
One global JobExecution per:
- tenantId
- reportType
- businessDate

One partition:
- accountId

ExecutionContext:
- tenantId
- accountId
- reportType
- businessDate

Each worker thread executes one remote StepExecution.

Recommended:
- Worker replicas: 2-20
- Kafka partitions: 100
- Kafka concurrency per pod: 2
- One report per account/day

## Functional Requirements
- Discover eligible accounts.
- Create one partition per account.
- Read transactions.
- Generate report.
- Persist report.
- Retry transient failures.
- Restart failed partitions.
- Idempotent outputs.

## Strategy
ReportTypeStrategy resolved by reportType.

## Database
tenant
report_job
report_work_unit

## Scaling
Master = OpenShift Job
Workers = Deployment + KEDA

## Acceptance
- One global JobExecution
- Remote partitioning
- Concurrent workers
- Restart support
- Idempotent reports
- New tenant requires DB configuration only
- New report type requires new Strategy implementation
