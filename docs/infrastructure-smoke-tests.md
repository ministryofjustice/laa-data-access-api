# Infrastructure Smoke Tests

## Purpose

The Cucumber `@smoke` scenarios verify the live Axon HTTP API in a disposable Docker
Compose stack. They exercise application and prior-authority lifecycles, including
PostgreSQL migrations, OAuth2 security, asynchronous projections and SDS document
operations. OAuth2 and SDS are test doubles; no deployed services are contacted.

These black-box checks complement the unit and integration suites. They use
`:data-access-service-axon:bddTest`, not the retired `infrastructureTest` harness.
See the [BDD framework guide](../data-access-service-axon/docs/testing/bdd-testing-framework.md)
for scenario structure and how to add tests.

## Run locally

Prerequisites: Docker with Compose, Java 25 and available host ports 9000, 9998 and
6432. Run from the repository root:

```bash
./scripts/run-infrastructure-smoke-tests.sh
```

The script uses fixed container names and a fixed Compose project name, so it will
stop and remove any smoke-test stack already running under that project, including
one started by a concurrent run of this script. It deletes that stack's disposable
database volume on exit. It does not touch the separate stack started by
`docker compose up` for local development.

| Step | Behaviour |
|------|----------|
| Build | Runs `./gradlew :data-access-service-axon:bootJar` for `Dockerfile.axon`. |
| Start | Starts `docker-compose.smoke-test.yml` under Compose project `laa-data-access-smoke-test`, with a bounded healthcheck wait. |
| Readiness | Checks the app's `/actuator/health` through the published host port with bounded retries. |
| Test | Runs `./gradlew :data-access-service-axon:bddTest -Dcucumber.filter.tags='@smoke' --rerun-tasks --console=plain`. Browser downloads are disabled because the suite uses Playwright's HTTP client. |
| Diagnostics | Collects Compose status and container logs before teardown, including on failure. |
| Cleanup | Removes the stack, network and database volume via an exit trap, including after startup or test failure. |

The script exits nonzero if the build, startup, readiness check or test run fails.
It also exits nonzero if collecting diagnostics or cleaning up fails, even when the
tests themselves passed. The test task always reruns, so a cached result from an
earlier run can never stand in for testing the service that this run just started.

The BDD defaults match the stack:

| Variable | Default |
|----------|---------|
| `LAA_BDD_BASE_URL` | `http://localhost:9000` |
| `LAA_BDD_TOKEN_URL` | `http://localhost:9998/entra/token` |
| `LAA_BDD_TOKEN_ISSUER_HOST` | `mock-oauth2-server-smoketest:9999` |

Use the direct Gradle command, rather than this stack-owning script, when targeting
another compatible environment. Additional `LAA_BDD_*` settings are described in the
BDD framework guide. The old `LAA_SMOKE_*` settings do not configure this suite.

## Reports and diagnostics

All paths are relative to `data-access-service-axon/build/`:

| Path | Contents |
|------|----------|
| `reports/cucumber/index.html` | Human-readable Cucumber report |
| `reports/cucumber/cucumber.json` | Machine-readable scenario results |
| `reports/tests/bddTest/` | Gradle HTML test report |
| `test-results/bddTest/` | JUnit XML results |
| `reports/smoke-stack/` | Container status, logs and host readiness response |

If startup fails, test reports may not exist; inspect the stack diagnostics first.

## GitHub Actions

The [Smoke tests](../.github/workflows/smoke-tests.yml) workflow is reusable only
(`workflow_call`). It does not run on its own. Two build workflows call it as a
separate `smoke-tests` job:

- [`build-feature.yml`](../.github/workflows/build-feature.yml) runs it on PRs,
  after `build-and-test`.
- [`build-main.yml`](../.github/workflows/build-main.yml) runs it on pushes to
  `main`, after `release`.

The image publish jobs (`ecr-publish-service-image`) wait for `smoke-tests`. The
deploy jobs wait for those publish jobs, so a smoke failure stops the deployment
chain.

The job uses a GitHub-hosted Ubuntu runner, Corretto Java 25, the Gradle wrapper
and the same script used locally. It needs no deployment credentials. It has a
25-minute timeout and a final cleanup step as a backstop for the script's trap.
Reports and diagnostics are uploaded as the `smoke-test-reports` artifact and kept
for 14 days, even if an earlier step fails. If a report file is missing because the
run failed before producing it, the upload step logs a warning instead of failing.

To require success before merging, add the **Smoke tests** job as a required check
in the repository's ruleset or branch protection after its first CI run. No branch
protection or repository settings are changed by this workflow.
