# performance-tests

k6 performance testing framework for `laa-data-access-api`. JavaScript test
scripts driven either by a local `k6` binary (developer iteration) or by the
K6 Operator running pods in the UAT cluster (Prometheus remote-write output).

## Layout

```
performance-tests/
  build.gradle              Gradle tasks for local k6 invocation
  package.json              Lint/format tooling (no runtime deps)
  src/
    config/{env,endpoints,thresholds}.js
    client/{auth,http}.js                       auth refresh + timeout/failure logging
    scenarios/{smoke,load,stress,soak,spike,throughput,index}.js
    helpers/{check,data,preflight,summary}.js   assertions, SharedArray pool, setup(), output
  tests/
    get-applications.js     GET /api/v0/applications happy path
  fixtures/                 JSON pools loaded via helpers/data.loadPool
```

The matching helm chart for cluster runs lives at `.helm/k6-perf/`.

## Local runs

Install k6 (`brew install k6`) — falls back to the `grafana/k6` Docker image
if the binary is not on PATH.

```bash
# Smoke run (1 VU, 30s) against localhost:8080
./gradlew :performance-tests:smoke

# Choose a different scenario
./gradlew :performance-tests:load

# Override target URL or knobs
./gradlew :performance-tests:smoke \
  -PtargetUrl=http://localhost:9080 \
  -Pvus=5 -Pduration=2m

# Different test file
./gradlew :performance-tests:smoke -Pscript=get-applications.js
```

Summaries land in `performance-tests/build/reports/k6/<scenario>-summary.json`.

## Cluster runs

Cluster runs are driven by the K6 Operator and the helm chart at
`.helm/k6-perf/`. They are NOT triggered from gradle, GitHub Actions, or PR
checks — performance tests are launched on demand to avoid noisy CI runs.

See `.helm/k6-perf/README.md` for install instructions.

## Environment variables

| Var                           | Default                          | Description                                                |
| ----------------------------- | -------------------------------- | ---------------------------------------------------------- |
| `K6_TARGET_URL`               | `http://localhost:8080`          | API base URL                                               |
| `K6_SERVICE_NAME`             | `CIVIL_DECIDE`                   | Value for `X-Service-Name` header                          |
| `K6_TOKEN`                    | `swagger-caseworker-token`       | Bearer token                                               |
| `K6_SCENARIO`                 | `smoke`                          | smoke \| load \| stress \| soak \| spike \| throughput     |
| `K6_VUS_OVERRIDE`             | scenario default                 | Override VU count (or req/s for throughput scenario)       |
| `K6_DURATION_OVERRIDE`        | scenario default                 | Override scenario duration                                 |
| `K6_REQUEST_TIMEOUT`          | `10s`                            | Default per-request timeout (override per-call in params)  |
| `K6_HEALTH_URL`               | `<targetUrl>/actuator/health`    | Preflight health check URL                                 |
| `K6_TOKEN_REFRESH_URL`        | _(unset)_                        | If set, auth refreshes the bearer on TTL expiry            |
| `K6_TOKEN_TTL_SECONDS`        | `3600`                           | How long a fetched token is treated as valid               |
| `K6_SUMMARY_OUT`              | `/tmp/k6-summary.json`           | JSON report path                                           |
| `K6_JUNIT_OUT`                | `/tmp/k6-junit.xml`              | JUnit XML report path (CI reporter compatible)             |
| `K6_PROMETHEUS_RW_SERVER_URL` | _(unset)_                        | If set, k6 emits to Prometheus remote-write                |

## Utilities reference

**`src/client/http.js`** — `get/post/patch/del(name, url, params)`. Auto auth
headers, request tagged with `name`, default 10s timeout, logs first 5
failures per request name to stop log spam in a failing run.

**`src/helpers/check.js`** — assertion helpers, all tagged with the request
name so failures attribute correctly in dashboards:
- `expectStatus(name, res, 200)`
- `expectJsonBody(name, res)`
- `expectField(name, res, 'page.totalElements')` — dot-path
- `expectArrayLength(name, res, 'content', '>=10')` — comparators: `>`, `>=`, `<`, `<=`, `=`
- `expectResponseTime(name, res, 500)` — per-request ms cap
- `expectHeader(name, res, 'Location')`

**`src/helpers/data.js`** — SharedArray-backed pools (loaded once, shared
across VUs):
- `loadPool('app-ids', './fixtures/app-ids.json')`
- `pickRandom(pool)` / `pickRoundRobin(pool)`

**`src/helpers/preflight.js`** — `preflight()` returns a `setup()` that fails
the run if the target is unreachable. Re-export it: `export const setup = preflight;`

**`src/helpers/summary.js`** — `handleSummary` writes stdout text + JSON +
JUnit XML. Re-export from your test: `export { handleSummary };`

**`src/client/auth.js`** — static token by default. Set `K6_TOKEN_REFRESH_URL`
to enable refresh-on-TTL (required for soak runs that outlast the token).

## Adding a new endpoint test

1. Add the URL builder to `src/config/endpoints.js`. Builders accept an
   optional params object — `getApplications({ status: 'APPLICATION_SUBMITTED', size: 50 })`
   — which is encoded and appended as a query string. Array values repeat the
   key (e.g. `{ tag: ['a', 'b'] }` → `?tag=a&tag=b`).
2. Add tighter SLOs (optional) to `endpointThresholds` in `src/config/thresholds.js`.
3. Create `tests/<name>.js`. Copy `get-applications.js` as the template — it
   already wires up scenario selection, thresholds, checks, and the summary handler.

Request names (the first arg to `get`/`post`/etc.) should stay at the path
level — don't include query params in the tag, or per-filter cardinality will
fragment your dashboards.

## Lint and format

```bash
cd performance-tests
npm install
npm run lint
npm run format
```
