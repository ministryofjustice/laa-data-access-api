# Mass Generator - Testing Both Approaches

## Approach 1: POST Request (local / port-forward)

```bash
docker compose up -d
./scripts/run-mass-generator-web.sh   # starts on port 8081

# Start a job
curl -X POST "http://localhost:8081/api/mass-generator/generate?count=100&cleanup=true"

# Poll status
curl http://localhost:8081/api/mass-generator/jobs/<jobId> | jq .

# Cancel
curl -X DELETE http://localhost:8081/api/mass-generator/jobs/<jobId>
```

**Pros:** Simple, works with port-forward, no timeout
**Cons:** Job state is in-memory (lost on pod restart)

## Approach 2: kubectl exec + GitHub Actions

```bash
./scripts/run-mass-generator-pod.sh laa-data-access-dev 1000 true
```

Or trigger via Actions tab → "Mass Data Generator" workflow (workflow_dispatch).

**Pros:** No port-forward needed, GitHub UI for logs/summary
**Cons:** Requires kubectl access / KUBE_CONFIG secret

## Test Plan

| # | Test | Expected |
|---|------|----------|
| 1 | POST count=100 cleanup=false | Job ID in <1s, completes |
| 2 | Poll status during run | processedCount increments every 500 |
| 3 | POST cleanup=true | CLEANING → RUNNING → COMPLETED |
| 4 | Cancel mid-run | CANCELLED |
| 5 | Two concurrent jobs | Both complete |
| 6 | Pod script in dev | Finds pod, shows progress |
| 7 | GitHub Actions workflow | Workflow completes with summary |

## Validation

```sql
SELECT COUNT(*) FROM applications;
SELECT COUNT(*) FROM decisions;
SELECT COUNT(*) FROM proceedings;
```
