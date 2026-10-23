# k6 journey harness

The local Compose mock OAuth server and the shared ephemeral mock OAuth server expose the same
client-ID contract. The client credentials below mint deterministic identities for k6 journeys.
All identities carry the API-recognised `LAA_CASEWORKER` role; the provider has
`access_as_provider` and office `0Z123A`, while the three caseworkers have `access_as_user`.

| Actor | Environment variable | Client ID | Stable subject |
| --- | --- | --- | --- |
| Provider | `K6_PROVIDER_CLIENT_ID` | `k6-provider` | `k6-provider` |
| Application Caseworker | `K6_APPLICATION_CASEWORKER_CLIENT_ID` | `k6-application-caseworker` | `k6-application-caseworker` |
| PA Caseworker | `K6_PA_CASEWORKER_CLIENT_ID` | `k6-pa-caseworker` | `k6-pa-caseworker` |
| Contender Caseworker | `K6_CONTENDER_CLIENT_ID` | `k6-contender-caseworker` | `k6-contender-caseworker` |

Create an untracked environment file and provide the secrets from the target environment:

```bash
cp k6/.env.example k6/.env
# Set K6_*_CLIENT_SECRET values in k6/.env, then export them for k6.
set -a
source k6/.env
set +a
```

`k6/.env` and all client secrets must remain outside version control. The mock OAuth configuration
only selects identities by client ID; target-environment credentials are supplied to k6 through the
`K6_*_CLIENT_ID` and `K6_*_CLIENT_SECRET` environment variables.

## Run locally

Start the local dependencies and API in separate terminals:

```bash
docker compose up -d
./gradlew bootRun --args='--spring.profiles.active=local'
```

Create and export the local configuration, then run the runtime smoke scenario:

```bash
cp k6/.env.example k6/.env
# Set the required K6_*_CLIENT_SECRET values.
set -a && source k6/.env && set +a
./k6/run.sh
```

`run.sh` uses an installed `k6` binary when available. Otherwise it runs the pinned official
`grafana/k6` image using Docker. The default scenario gets a provider token in `setup()` and checks
that the API health endpoint is reachable; it performs no mutations. Pass a scenario relative to
`k6/` as the first argument, for example `./k6/run.sh scenarios/smoke-complete-journey.js`.

## Journey scenarios

All journeys tag HTTP metrics with their scenario and canonical step name. Every virtual-user
iteration creates a unique run context, so performance scenarios do not share externally visible
Application, Prior Authority, client, or LAA-reference identifiers.

| Scenario | Command | Default execution |
| --- | --- | --- |
| Complete smoke journey | `./k6/run.sh scenarios/smoke-complete-journey.js` | One VU, one complete Application and PA grant |
| Application assignment contention | `./k6/run.sh scenarios/contention-application-assignment.js` | `K6_CONTENTION_VUS=2`, one shared Application and one assignment attempt per contender |
| Complete journey baseline | `./k6/run.sh scenarios/performance-complete-journey.js` | `K6_PERFORMANCE_VUS=5` for `K6_PERFORMANCE_DURATION=5m` |
| Provider drafting baseline | `./k6/run.sh scenarios/performance-provider-drafting.js` | Application draft, document, and submission phase |
| Work Queue baseline | `./k6/run.sh scenarios/performance-work-queue.js` | Application discovery, assignment, and decision phase |
| PA lifecycle baseline | `./k6/run.sh scenarios/performance-prior-authority.js` | Granted parent setup followed by PA lifecycle and decision |

The PA caseworker uses the Application Caseworker credentials when both `K6_PA_CASEWORKER_*`
variables are omitted; supply PA credentials to run them as distinct identities. Contention
records `assigned` and `conflict` metrics for its one attempt per configured contender. Set
`K6_THRESHOLDS` to add optional correctness, latency, or error thresholds.

## Run against an ephemeral environment

Port-forward the target API and its configured mock OAuth service, then override the URLs before
running the same command:

```bash
export K6_API_BASE_URL=http://localhost:8080
export K6_OAUTH_TOKEN_URL=http://localhost:9999/entra/token
./k6/run.sh
```

The token issuer must be the one trusted by the target API. Obtain only the client secrets from
the target environment and do not add them to files under version control.

## Configuration and output

All settings are validated before the scenario starts. Required actors fail fast if their client ID
or secret is missing; the PA caseworker falls back to the Application Caseworker only when both PA
values are omitted.

| Variable | Default |
| --- | --- |
| `K6_API_BASE_URL` | `http://localhost:8080` |
| `K6_OAUTH_TOKEN_URL` | `http://localhost:9999/entra/token` |
| `K6_SERVICE_NAME` | `CIVIL_APPLY` |
| `K6_POLL_TIMEOUT_MS` / `K6_POLL_INTERVAL_MS` | `30000` / `500` |
| `K6_SUMMARY_PATH` | `k6/results/summary.json` |
| `K6_CONTENTION_VUS` | `2` |
| `K6_PERFORMANCE_VUS` / `K6_PERFORMANCE_DURATION` | `5` / `5m` |
| `K6_THRESHOLDS` | unset |

The k6 console summary and JSON summary are both written at the end of a run. The default JSON
path is `k6/results/summary.json`. Diagnostics report only method, path, status, safe record IDs,
and a correlation ID; tokens, secrets, and authorization headers are never printed. Set
`K6_DEBUG_RESPONSE_BODY=true` only for local debugging to include a capped failed-response preview.

## Fixtures and step catalogue

The synthetic fixture templates in `k6/fixtures/` contain no personal or case data. Apply a
run context to them before use so each virtual user and iteration receives distinct identifiers.
The registry in `k6/lib/step-registry.js` is the source of truth for the generated catalogue and
feature-step validation:

```bash
npm --prefix k6 run generate-step-catalogue
npm --prefix k6 test
npm --prefix k6 run validate-documentation
```
