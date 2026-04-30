# Mass Generator - Execution Approaches

## Summary

Convert to async job pattern with `@Async` and `ThreadPoolTaskExecutor` — POST returns job ID
immediately, runs in background, can poll for status. Add cleanup option to wipe old test data
before seeding.

---

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

## Approach Comparison

| Factor | POST only | .sh script (kubectl exec) | POST + GitHub Actions |
|--------|:---------:|:-------------------------:|:---------------------:|
| No timeout risk | ✅ immediate 202 response | ❌ shell session can drop | ✅ |
| Team can trigger without kubectl access | ❌ need port-forward | ❌ need kubectl configured | ✅ Actions UI |
| Progress tracking | ✅ poll status endpoint | ❌ stdout only, lost if terminal drops | ✅ polls + visible in GHA logs |
| Audit trail (who ran it, when, what params) | ❌ | ❌ | ✅ workflow run history |
| No local setup needed | ❌ need port-forward or network access | ❌ need kubectl + context | ✅ just click "Run workflow" |
| Works for local dev/debugging | ✅ | ✅ | ❌ only works against deployed envs |
| Cancellation support | ✅ DELETE endpoint | ❌ kill process manually | ✅ via DELETE or cancel workflow |
| Error visibility | ✅ job status JSON with errorCount | partial (logs only) | ✅ both JSON + GHA logs |
| Scheduling (future) | ❌ manual only | ❌ manual only | ✅ add cron trigger trivially |
| Notifications (future) | ❌ | ❌ | ✅ Slack/email via GHA steps |
| Approval gates (future) | ❌ | ❌ | ✅ environment protection rules |

---

## Approach 1: POST Request (direct)

### What it is
Run the mass generator as a web server. Trigger generation via HTTP POST.
Poll a status endpoint for progress.

### How to use

```bash
# Local
docker compose up -d
./scripts/run-mass-generator-web.sh

# Start job
curl -X POST "http://localhost:8081/api/mass-generator/generate?count=100&cleanup=true"

# Poll
curl http://localhost:8081/api/mass-generator/jobs/<jobId> | jq .

# Against a pod via port-forward
kubectl port-forward -n laa-data-access-dev pod/<pod-name> 8080:8080 &
curl -X POST "http://localhost:8080/api/mass-generator/generate?count=1000&cleanup=true"
```

### Pros
- Simple, portable, works anywhere with HTTP access
- Immediate response — no timeout
- Full job lifecycle (create, poll, cancel)
- Works for local development

### Cons
- Requires port-forward or network access to the pod
- No audit trail — anyone can trigger, no record of who/when
- No built-in notifications or scheduling
- Job state is in-memory (lost if pod restarts)

---

## Approach 2: Shell Script (kubectl exec)

### What it is
A shell script that finds a pod, sends the POST internally via `kubectl exec`,
and polls for completion.

### How to use

```bash
./scripts/run-mass-generator-pod.sh laa-data-access-dev 1000 true
```

### Pros
- No port-forward needed — talks to localhost inside the pod
- Simple single command
- Good for quick one-off runs from a developer machine

### Cons
- Requires kubectl configured with the right context
- If your terminal drops (SSH timeout, laptop sleep), you lose visibility
- No audit trail
- Can't easily share with non-technical team members
- Basically just a wrapper around the POST endpoint — adds little value over Approach 1

---

## Approach 3: GitHub Actions + POST (recommended)

### What it is
A GitHub Actions workflow that:
1. Authenticates to the cluster (same secrets as deploy)
2. Finds the pod
3. Sends the POST request internally via kubectl exec
4. Polls status every 10 seconds
5. Reports results in the workflow summary

### How to use (manual)

1. Actions tab → "Mass Data Generator" → "Run workflow"
2. Pick environment, count, cleanup
3. Watch logs for progress

### How it's tested (automatic)

The workflow also triggers automatically after a successful deploy to UAT.
It runs a small validation (100 records, cleanup=true) to confirm the endpoints work.

### Pros
- Anyone on the team can trigger without local setup
- Full audit trail (who triggered, when, with what parameters)
- Real-time progress in GitHub Actions logs
- Workflow summary with final metrics
- Easy to add: cron schedule, Slack notifications, approval gates
- No new secrets needed — uses same `KUBE_CERT`/`KUBE_TOKEN` as deploy
- Can be re-run from GitHub UI

### Cons
- Only works against deployed environments (not local)
- ~10 second polling interval means slightly delayed progress updates
- GitHub Actions has a 6-hour job timeout (fine for 250k @ 30 mins)

---

## Key design decisions

| Decision | Rationale |
|----------|-----------|
| `@Async` + `ThreadPoolTaskExecutor` | Non-blocking, pod stays responsive |
| DB-backed job state (`generation_jobs` table) | Survives pod restarts, pollable from anywhere |
| Per-record try-catch | One bad record doesn't kill the whole run (AC9) |
| `cleanup=true` deletes in FK order | No duplicates on re-runs (AC6/AC7) |
| Batch flush every 500 records | Keeps memory stable at 250k scale |
| Job status includes `errorCount` | Visibility into partial failures (AC8) |
| Progress written to DB every 500 records | GitHub Actions polls `GET /jobs/{id}` which reads from DB |

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

.github/workflows/mass-generator.yml        # workflow_dispatch + auto after deploy
scripts/run-mass-generator-web.sh           # Local web server launcher
scripts/run-mass-generator-pod.sh           # kubectl exec wrapper
```
