# Mass Generator - Approach Comparison

## Comparison Table

| Factor | POST only | .sh script (kubectl exec) | POST + GitHub Actions |
|--------|:---------:|:-------------------------:|:---------------------:|
| Works locally | ✅ | ❌ | ❌ |
| No timeout risk | ✅ immediate 202 response | ❌ shell session can drop | ✅ |
| Team can trigger without kubectl | ❌ need port-forward | ❌ need kubectl configured | ✅ Actions UI |
| Progress tracking | ✅ poll endpoint | ❌ stdout only, lost if terminal drops | ✅ polls + visible in GHA logs |
| Audit trail (who ran it, when, what params) | ❌ | ❌ | ✅ workflow run history |
| No local setup needed | ❌ need port-forward or network access | ❌ need kubectl + context | ✅ just click "Run workflow" |
| Works for local dev/debugging | ✅ | ✅ | ❌ only works against deployed envs |
| Cancellation support | ✅ DELETE endpoint | ❌ kill process manually | ✅ via DELETE or cancel workflow |
| Error visibility | ✅ job status JSON with errorCount | partial (logs only) | ✅ both JSON + GHA logs |
| Scheduling (future) | ❌ manual only | ❌ manual only | ✅ add cron trigger trivially |
| Notifications (future) | ❌ | ❌ | ✅ Slack/email via GHA steps |
| Approval gates (future) | ❌ | ❌ | ✅ environment protection rules |

---

## Summary

| Use case | Recommended approach |
|----------|---------------------|
| Local development / debugging | POST only |
| Quick one-off from your machine (with kubectl) | .sh script |
| Team-triggered runs against deployed environments | POST + GitHub Actions |
| Scheduled / automated runs (future) | POST + GitHub Actions (add cron) |
| Full audit trail + notifications | POST + GitHub Actions |

---

## Approach 1: POST Request (direct)

**Pros:**
- Works for local development — just `docker compose up` and hit localhost
- Immediate 202 response, no timeout
- Full job lifecycle (create, poll, cancel)
- Simple and portable

**Cons:**
- Requires port-forward or network access to talk to a deployed pod
- No audit trail — no record of who triggered it or when
- No built-in notifications or scheduling

---

## Approach 2: Shell Script (kubectl exec)

**Pros:**
- No port-forward needed — sends POST from inside the pod via kubectl exec
- Single command to run
- Good for quick one-off runs from a developer machine

**Cons:**
- Requires kubectl configured with the right context
- If terminal drops (SSH timeout, laptop sleep), you lose visibility (job continues in background)
- No audit trail
- Can't easily share with non-technical team members
- Essentially just a wrapper around the POST endpoint

---

## Approach 3: POST + GitHub Actions (recommended for team use)

**Pros:**
- Anyone on the team can trigger without any local setup
- Full audit trail (who triggered, when, with what parameters)
- Real-time progress in GitHub Actions logs
- Workflow summary with final metrics
- No new secrets needed — uses same KUBE_CERT/KUBE_TOKEN as deploy
- Easy to extend: cron schedule, Slack notifications, approval gates
- Can be re-run from GitHub UI

**Cons:**
- Only works against deployed environments (not local)
- ~10 second polling interval means slightly delayed progress updates
- GitHub Actions has a 6-hour job timeout (fine for 250k @ ~30 mins)
