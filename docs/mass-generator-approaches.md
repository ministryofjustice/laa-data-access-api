# Mass Generator - Execution

## How it works

```
POST /api/mass-generator/generate?count=1000&cleanup=true
  → 202 Accepted {"jobId": "abc-123", "statusUrl": "/api/mass-generator/jobs/abc-123"}

Background thread:
  1. If cleanup=true → DELETE all existing test data (FK-safe order)
  2. Generate records in batches of 500
  3. Update job progress every batch
  4. If any single record fails → log it, increment errorCount, continue

GET /api/mass-generator/jobs/abc-123
  → {"status": "RUNNING", "processedCount": 1500, "targetCount": 5000, "errorCount": 0, ...}

DELETE /api/mass-generator/jobs/abc-123
  → Cancels the job
```

---

## Running via curl (port-forward)

Port-forward to the mass generator pod, then trigger via HTTP.

```bash
# Port-forward to the pod
kubectl port-forward -n <namespace> pod/<pod-name> 8080:8080

# Start job
curl -X POST "http://localhost:8080/api/mass-generator/generate?count=1000&cleanup=true"

# Poll status
curl http://localhost:8080/api/mass-generator/jobs/<jobId> | jq .

# Cancel
curl -X DELETE http://localhost:8080/api/mass-generator/jobs/<jobId>
```

For local development, start the web server directly:

```bash
docker compose up -d
./scripts/run-mass-generator-web.sh

curl -X POST "http://localhost:8081/api/mass-generator/generate?count=100&cleanup=true"
```

---

## Key design decisions

| Decision | Rationale |
|----------|-----------|
| `@Async` + `ThreadPoolTaskExecutor` | Non-blocking, pod stays responsive |
| DB-backed job state (`generation_jobs` table) | Survives pod restarts, pollable from anywhere |
| Per-record try-catch | One bad record doesn't kill the whole run |
| `cleanup=true` deletes in FK order | No duplicates on re-runs |
| Batch flush every 500 records | Keeps memory stable at scale |
| Job status includes `errorCount` | Visibility into partial failures |
| Progress written to DB every 500 records | Pollable from any client |

---

## What's deployed

```
data-access-mass-generator/src/main/java/.../massgenerator/
├── config/AsyncConfiguration.java          # ThreadPoolTaskExecutor bean
├── controller/MassGeneratorController.java # REST endpoints
├── job/GenerationJob.java                  # Job state model
├── job/JobStatus.java                      # QUEUED/CLEANING/RUNNING/COMPLETED/FAILED/CANCELLED
├── job/JobRepository.java                  # In-memory ConcurrentHashMap store
├── service/AsyncMassGeneratorService.java  # @Async generation logic
└── service/DataCleanupService.java         # DELETE all test data in FK-safe order

scripts/run-mass-generator-web.sh           # Local web server launcher
```
