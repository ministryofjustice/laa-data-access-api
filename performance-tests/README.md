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
    client/{auth,http}.js
    scenarios/{smoke,load,stress,soak,spike,index}.js
    helpers/{check,summary}.js
  tests/
    get-applications.js     GET /api/v0/applications happy path
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

| Var                       | Default                            | Description                                       |
| ------------------------- | ---------------------------------- | ------------------------------------------------- |
| `K6_TARGET_URL`           | `http://localhost:8080`            | API base URL                                      |
| `K6_SERVICE_NAME`         | `CIVIL_DECIDE`                     | Value for `X-Service-Name` header                 |
| `K6_TOKEN`                | `swagger-caseworker-token`         | Bearer token                                      |
| `K6_SCENARIO`             | `smoke`                            | smoke \| load \| stress \| soak \| spike          |
| `K6_VUS_OVERRIDE`         | scenario default                   | Override VU count                                 |
| `K6_DURATION_OVERRIDE`    | scenario default                   | Override scenario duration                        |
| `K6_PROMETHEUS_RW_SERVER_URL` | _(unset)_                      | If set, k6 emits to Prometheus remote-write       |

## Adding a new endpoint test

1. Add the URL to `src/config/endpoints.js`.
2. Add tighter SLOs (optional) to `endpointThresholds` in `src/config/thresholds.js`.
3. Create `tests/<name>.js`. Copy `get-applications.js` as the template — it
   already wires up scenario selection, thresholds, checks, and the summary handler.

## Lint and format

```bash
cd performance-tests
npm install
npm run lint
npm run format
```
