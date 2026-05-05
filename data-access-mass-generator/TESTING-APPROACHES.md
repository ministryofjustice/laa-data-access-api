# Mass Generator - Async Web Mode

## What it does

Seeds the PostgreSQL database with realistic test data — applications, caseworkers, individuals,
proceedings, decisions, merits decisions, and certificates. Runs **asynchronously** so the HTTP
request returns immediately and generation happens in the background.

### Data generated per job

| Entity | How many | Details |
|--------|----------|---------|
| Caseworkers | 100 (pool) | Created once at start, reused across all applications |
| Individuals | 1,000 (pool) | Created once at start, linked to applications |
| Applications | N (your `count` param) | Full `ApplicationContent` JSON via datafaker |
| Proceedings | 1+ per application | Derived from the generated content, includes lead proceeding |
| Decisions | ~40% of applications | GRANTED (30%) or REFUSED (70%) |
| Merits Decisions | 1 per proceeding (on decided apps) | Linked to the decision |
| Certificates | 1 per GRANTED decision | Only for granted applications |

Data is realistic — `FullJsonGenerator` builds a fully-populated `ApplicationContent` with
randomised values from datafaker that mirror the real schema (office codes, LAA references,
category of law, matter types, delegated functions, etc).

---

## How it works

### Async job pattern

```
                         ┌──────────────────────────────────────────────┐
                         │           Background Thread                  │
POST /generate           │  (ThreadPoolTaskExecutor, @Async)            │
  ├─ Creates job row     │                                              │
  │   in generation_jobs │  1. If cleanup=true:                         │
  │   table (QUEUED)     │     DELETE all data in FK-safe order         │
  │                      │     Status → CLEANING                        │
  ├─ Kicks off @Async ──►│  2. Create caseworker + individual pools     │
  │   method             │     Status → RUNNING                         │
  │                      │  3. Loop N times:                            │
  └─ Returns 202 with    │     - Generate ApplicationContent            │
     job ID immediately  │     - Persist app + proceedings + linked     │
                         │     - 40% chance: decision + merits + cert   │
                         │     - On error: log, increment errorCount,   │
GET /jobs/{id}           │       continue to next record                │
  ├─ SELECT from         │  4. Every 500 records:                       │
  │   generation_jobs    │     - Flush Hibernate session                 │
  │   table              │     - UPDATE generation_jobs with progress    │
  └─ Returns current     │  5. On completion:                           │
     status + progress   │     Status → COMPLETED (or FAILED)           │
                         │     Record throughput + final counts          │
DELETE /jobs/{id}        └──────────────────────────────────────────────┘
  └─ Sets status to CANCELLED
     (checked each iteration)
```

### Key components

| Component | What it does |
|-----------|-------------|
| `AsyncConfiguration` | Defines a `ThreadPoolTaskExecutor` bean (2 core threads, 4 max, queue of 10). Annotated with `@EnableAsync`. |
| `MassGeneratorController` | REST endpoints: `POST /generate`, `GET /jobs/{id}`, `GET /jobs`, `DELETE /jobs/{id}` |
| `AsyncMassGeneratorService` | The `@Async("massGeneratorExecutor")` method that does the actual generation loop |
| `DataCleanupService` | Runs `DELETE FROM` on all tables in FK-safe order (certificates → merits_decisions → decisions → proceedings → linked_individuals → applications → individuals → caseworkers) |
| `JobRepository` | Reads/writes `generation_jobs` table via JPA |
| `GenerationJobEntity` | JPA entity mapped to `generation_jobs` table |

### Progress polling

Progress is **DB-backed** — not in-memory:

```
Background thread (every 500 records):
  → UPDATE generation_jobs SET processed_count = 1500, decided_count = 580, status = 'RUNNING'

GET /api/mass-generator/jobs/{id}:
  → SELECT * FROM generation_jobs WHERE id = '{id}'
  → Returns: {"status":"RUNNING", "processedCount":1500, "targetCount":5000, "errorCount":0, ...}
```

This means:
- Progress survives pod restarts
- You can query the DB directly if the API is down
- Multiple consumers can poll the same job

### Error handling

- **Per-record errors**: Each record is wrapped in its own try-catch. If one record fails, it logs the error, increments `errorCount`, and moves on to the next record.
- **Job-level errors**: If something catastrophic happens (DB connection lost, etc), the outer catch sets status to `FAILED` and records the error message.
- All exceptions are logged with full stack traces.

---

## Running locally

### Prerequisites

- Java 25+
- Docker running with Postgres up (`docker compose up -d`)

### Start the web server

```bash
./scripts/run-mass-generator-web.sh
```

This builds the JAR and starts the mass generator as a web server on **port 8081**.

### Start a generation job

```bash
curl -X POST "http://localhost:8081/api/mass-generator/generate?count=1000&cleanup=true"
```

Response (immediate, < 1 second):
```json
{
  "jobId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "message": "Generation job started",
  "statusUrl": "/api/mass-generator/jobs/a1b2c3d4-e5f6-7890-abcd-ef1234567890"
}
```

### Poll for progress

```bash
curl http://localhost:8081/api/mass-generator/jobs/<jobId> | jq .
```

Response (in progress):
```json
{
  "jobId": "a1b2c3d4-...",
  "status": "RUNNING",
  "targetCount": 1000,
  "processedCount": 500,
  "decidedCount": 195,
  "errorCount": 0,
  "startedAt": "2026-04-30T10:15:30.123Z",
  "completedAt": null,
  "throughput": null,
  "cleanupRequested": true
}
```

Response (completed):
```json
{
  "jobId": "a1b2c3d4-...",
  "status": "COMPLETED",
  "targetCount": 1000,
  "processedCount": 1000,
  "decidedCount": 412,
  "errorCount": 0,
  "startedAt": "2026-04-30T10:15:30.123Z",
  "completedAt": "2026-04-30T10:22:15.456Z",
  "throughput": 142.5,
  "cleanupRequested": true
}
```

### Other endpoints

```bash
# List all jobs
curl http://localhost:8081/api/mass-generator/jobs | jq .

# Cancel a running job
curl -X DELETE http://localhost:8081/api/mass-generator/jobs/<jobId>
```

### Parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| `count` | 1000 | Number of applications to generate |
| `cleanup` | false | If `true`, deletes all existing test data before generating |

---

## Running via port-forward

The mass generator runs as a web server in the pod. Port-forward to it and use curl.

```bash
kubectl port-forward -n <namespace> pod/<pod-name> 8080:8080

# Start a job
curl -X POST "http://localhost:8080/api/mass-generator/generate?count=1000&cleanup=true"

# Poll status
curl http://localhost:8080/api/mass-generator/jobs/<jobId> | jq .

# Cancel
curl -X DELETE http://localhost:8080/api/mass-generator/jobs/<jobId>
```

---

## Job lifecycle

```
QUEUED → CLEANING (if cleanup=true) → RUNNING → COMPLETED
                                              → FAILED (on error)
                                              → CANCELLED (via DELETE)
```

| Status | Meaning |
|--------|---------|
| `QUEUED` | Job created, waiting for background thread to pick it up |
| `CLEANING` | Deleting existing test data |
| `RUNNING` | Generating records (processedCount updates every 500) |
| `COMPLETED` | All records generated successfully |
| `FAILED` | A fatal error occurred (see `errorMessage`) |
| `CANCELLED` | You sent `DELETE /jobs/{id}` — checked each loop iteration |

---

## File structure

```
data-access-mass-generator/src/main/java/.../massgenerator/
├── MassGeneratorApp.java                   # Entry point — CLI mode or web mode (--web flag)
├── MassDataGeneratorRunner.java            # Original CLI CommandLineRunner (still works)
├── config/
│   └── AsyncConfiguration.java             # ThreadPoolTaskExecutor bean (2 core, 4 max)
├── controller/
│   └── MassGeneratorController.java        # REST: POST /generate, GET /jobs, DELETE /jobs
├── job/
│   ├── GenerationJob.java                  # POJO model for API responses
│   ├── GenerationJobEntity.java            # JPA entity → generation_jobs table
│   ├── GenerationJobJpaRepository.java     # Spring Data JPA repository
│   ├── JobRepository.java                  # Reads/writes DB, maps entity ↔ model
│   └── JobStatus.java                      # Enum: QUEUED, CLEANING, RUNNING, COMPLETED, FAILED, CANCELLED
├── service/
│   ├── AsyncMassGeneratorService.java      # @Async generation loop + job lifecycle
│   └── DataCleanupService.java             # DELETE all tables in FK-safe order

scripts/
├── run-mass-generator-web.sh               # Builds JAR + starts web server on port 8081


data-access-service/src/main/resources/db/migration/
└── V24__add_generation_jobs_table.sql      # Flyway migration for generation_jobs table
```
