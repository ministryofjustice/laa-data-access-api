# Plan: k6 Performance Test Script

Mirror the `ApiPerformanceTestRunner` Java class as a native k6 JavaScript script,
parameterised via environment variables, placed in a new `k6/` folder at the repo root.

---

## 1. Install k6

```bash
brew install k6
```

No project dependencies needed — k6 is a standalone binary.

---

## 2. Folder structure

Create the following at the repo root:

```
k6/
  make-decision-performance-test.js          # main script
  README.md                    # run instructions
  fixtures/
    applicationContent.json    # base applicationContent template, mutated per-iteration
```

---

## 3. `options` block

Read VUs and iterations from env vars and configure a `per-vu-iterations` scenario so that
total iterations = `VUS × ITERATIONS_PER_VU`. Add `thresholds` mirroring the report focus:

```
http_req_duration: ['p(95)<2000', 'p(99)<5000']
```

---

## 4. Environment variables

All configurable via `-e` flags at run time:

| Variable            | Default               | Maps to Java property |
|---------------------|-----------------------|-----------------------|
| `BASE_URL`          | `http://localhost:8080` | `baseUrl`           |
| `BEARER_TOKEN`      | _(required)_          | auth header           |
| `VUS`               | `1`                   | `concurrency`         |
| `ITERATIONS`        | `100`                 | `iterations`          |
| `DECIDE_RATE`       | `0.5`                 | `decideRate`          |
| `THINK_TIME_MIN_MS` | `0`                   | `thinkTimeMinMs`      |
| `THINK_TIME_MAX_MS` | `0`                   | `thinkTimeMaxMs`      |

---

## 5. Helper: `buildCreatePayload()`

Generates a random `ApplicationCreateRequest` per iteration using `Math.random()` and the
`k6/crypto` module for UUID generation:

- Random `firstName`, `lastName`, `dateOfBirth` (age 18–80)
- `type: "CLIENT"`, `details: { source: "perf-test" }`
- `status: "APPLICATION_IN_PROGRESS"`
- `applicationContent` is loaded once at init time from `k6/fixtures/applicationContent.json`
  (a representative subset of the ~50 fields `FullJsonGenerator` produces — specifically the
  fields the API actually validates: `laaReference`, `submittedAt`, `status`, `office`,
  `proceedings`, `applicationMerits`, etc.) and shallow-mutated per iteration (e.g. fresh
  `laaReference` UUID, updated `submittedAt` timestamp, randomised proceeding details).

---

## 6. Three HTTP call helpers

Each call is tagged by name so k6 reports latency separately in the end-of-run summary.

| Helper                        | Method & path                                  | Tag name                                        |
|-------------------------------|------------------------------------------------|-------------------------------------------------|
| `postCreateApplication()`     | `POST /api/v0/applications`                    | `POST /api/v0/applications`                     |
| `getApplicationProceedings()` | `GET /api/v0/applications/{id}`                | `GET /api/v0/applications/{id}`                 |
| `patchDecision()`             | `PATCH /api/v0/applications/{id}/decision`     | `PATCH /api/v0/applications/{id}/decision`      |

### `postCreateApplication`
- Body: output of `buildCreatePayload()`
- On success (201): parse `applicationId` from the `Location` response header
- Return `applicationId` or `null` on failure

### `getApplicationProceedings`
- On success (200): parse `proceedings[*].proceedingId` from the JSON response body
- Return array of proceeding ID strings (may be empty)

### `patchDecision`
- Body mirrors `MakeDecisionRequest`:
  ```json
  {
    "overallDecision": "REFUSED",
    "autoGranted": false,
    "applicationVersion": 0,
    "eventHistory": { "eventDescription": "Performance test decision" },
    "proceedings": [
      {
        "proceedingId": "<uuid>",
        "meritsDecision": {
          "decision": "REFUSED",
          "justification": "<random sentence>",
          "reason": "<random sentence>"
        }
      }
    ]
  }
  ```
  One entry per proceeding ID returned by `getApplicationProceedings`.

> **Note on `overallDecision`:** The Java code weights the random pick 6/6 towards `REFUSED`
> (all six options are `REFUSED`), so the k6 script should always use `REFUSED` to match.

---

## 7. `default` function (one iteration)

```
1. call postCreateApplication() → applicationId
2. if applicationId != null AND Math.random() < DECIDE_RATE:
     a. sleep(randomBetween(THINK_TIME_MIN_MS, THINK_TIME_MAX_MS))
     b. call getApplicationProceedings(applicationId) → proceedingIds
     c. if proceedingIds.length > 0:
          sleep(randomBetween(THINK_TIME_MIN_MS, THINK_TIME_MAX_MS))
          call patchDecision(applicationId, proceedingIds)
```

Think time sleeps are skipped entirely when `THINK_TIME_MAX_MS` is `0`
(matching the Java "synthetic stress mode" behaviour).

---

## 8. Auth headers

Pass the following as default params on every request:

```js
{
  headers: {
    'Authorization': `Bearer ${__ENV.BEARER_TOKEN}`,
    'X-Service-Name': 'CIVIL_DECIDE',      
    'Content-Type': 'application/json',
  }
}
```

---

## 9. Run commands

**Local (defaults):**
```bash
k6 run k6/make-decision-performance-test.js
```

**With overrides (e.g. staging):**
```bash
k6 run \
  -e BASE_URL=https://staging.example.com \
  -e BEARER_TOKEN=eyJ... \
  -e VUS=10 \
  -e ITERATIONS=50 \
  -e DECIDE_RATE=0.8 \
  -e THINK_TIME_MIN_MS=500 \
  -e THINK_TIME_MAX_MS=2000 \
  k6/make-decision-performance-test.js
```

k6's built-in end-of-run summary automatically prints p50/p90/p95/p99, mean, min/max, and
error count per tagged request name — no custom reporting code needed.


