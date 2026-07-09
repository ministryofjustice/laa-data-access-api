# k6 Performance Tests

Native [k6](https://k6.io/) script that mirrors the `ApiPerformanceTestRunner` Java class.

---

## Prerequisites

```bash
brew install k6
```

---

## Files

```
k6/
  make-decision-performance-test.js          # main script
  get-individuals-performance-test.js        # get applications -> get individuals flow
  README.md                    # this file
  fixtures/
    applicationContent.json    # base applicationContent template, mutated per iteration
```

---

## Environment variables

| Variable            | Default                 | Description                                  |
|---------------------|-------------------------|----------------------------------------------|
| `BASE_URL`          | `http://localhost:8080` | API base URL                                 |
| `BEARER_TOKEN`      | _(required)_            | Bearer token for the `Authorization` header  |
| `VUS`               | `1`                     | Number of virtual users (concurrent threads) |
| `ITERATIONS`        | `100`                   | Iterations **per VU** (total = VUs × ITERATIONS) |
| `DECIDE_RATE`       | `0.5`                   | Probability (0–1) that a decision is made after each create |
| `THINK_TIME_MIN_MS` | `0`                     | Minimum think-time sleep between calls (ms)  |
| `THINK_TIME_MAX_MS` | `0`                     | Maximum think-time sleep between calls (ms); `0` = no sleep |

Extra variables used by `get-individuals-performance-test.js`:

| Variable               | Default          | Description |
|------------------------|------------------|-------------|
| `APPLICATIONS_PAGE_SIZE` | `20`          | `GET /api/v0/applications` page size used to source application IDs |
| `CLIENT_FIRST_NAME`    | _empty_          | Optional filter used when fetching applications |
| `CLIENT_LAST_NAME`     | _empty_          | Optional filter used when fetching applications |
| `CLIENT_DATE_OF_BIRTH` | _empty_          | Optional `YYYY-MM-DD` filter used when fetching applications |
| `STATUS`               | _empty_          | Optional application status filter |
| `INCLUDE`              | `CLIENT_DETAILS` | Value passed to `GET /api/v0/individuals?include=...` |
| `INDIVIDUAL_TYPE`      | `CLIENT`         | Value passed to `GET /api/v0/individuals?individualType=...` |

---

## Run commands

**Local with defaults** (1 VU, 100 iterations, no auth):
```bash
k6 run k6/make-decision-performance-test.js
```

**With overrides** (e.g. staging):
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

**Synthetic stress mode** (no think time, maximum throughput):
```bash
k6 run \
  -e BEARER_TOKEN=eyJ... \
  -e VUS=20 \
  -e ITERATIONS=500 \
  k6/make-decision-performance-test.js
```

**Get individuals flow** (fetch applications, pick an ID, then fetch individuals with client details):
```bash
k6 run \
  -e BASE_URL=http://localhost:9080 \
  -e BEARER_TOKEN=eyJ... \
  -e VUS=10 \
  -e ITERATIONS=50 \
  -e CLIENT_FIRST_NAME=John \
  -e CLIENT_LAST_NAME=Doe \
  -e CLIENT_DATE_OF_BIRTH=1985-04-16 \
  -e INCLUDE=CLIENT_DETAILS \
  k6/get-individuals-performance-test.js
```

---

## Output

k6's built-in end-of-run summary prints p50/p90/p95/p99, mean, min/max, and error count
broken down per tagged request name:

- `POST /api/v0/applications`
- `GET /api/v0/applications/{id}`
- `PATCH /api/v0/applications/{id}/decision`

The new script also reports:

- `GET /api/v0/applications`
- `GET /api/v0/individuals`

---

## How the fixture works

`fixtures/applicationContent.json` is a representative template of the ~50 fields the API
validates (matching the structure produced by `FullJsonGenerator`). It is loaded **once at
init time** and **shallow-mutated per iteration**: fresh UUIDs, a new `laaReference`, and
updated timestamps are injected on each call so every request carries unique data without
the overhead of generating the full payload from scratch inline.

To extend the payload, edit `fixtures/applicationContent.json` directly.

