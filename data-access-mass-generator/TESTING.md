# Mass Generator - Testing

## Running via curl (port-forward)

Port-forward to the mass generator pod, then use curl to trigger and monitor jobs.

```bash
# Port-forward to the pod
kubectl port-forward -n <namespace> pod/<pod-name> 8080:8080

# Start a job
curl -X POST "http://localhost:8080/api/mass-generator/generate?count=100&cleanup=true"

# Poll status
curl http://localhost:8080/api/mass-generator/jobs/<jobId> | jq .

# Cancel
curl -X DELETE http://localhost:8080/api/mass-generator/jobs/<jobId>
```

## Test Plan

| # | Test | Expected |
|---|------|----------|
| 1 | POST count=100 cleanup=false | Job ID in <1s, completes |
| 2 | Poll status during run | processedCount increments every 500 |
| 3 | POST cleanup=true | CLEANING → RUNNING → COMPLETED |
| 4 | Cancel mid-run | CANCELLED |
| 5 | Two concurrent jobs | Both complete |

## Validation

```sql
SELECT COUNT(*) FROM applications;
SELECT COUNT(*) FROM decisions;
SELECT COUNT(*) FROM proceedings;
```
