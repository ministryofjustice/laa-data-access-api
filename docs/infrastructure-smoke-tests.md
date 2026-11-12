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

The [Smoke tests](../.github/workflows/smoke-tests.yml) workflow runs independently on:

- Every PR when opened, reopened or updated with new commits, including draft PRs
  and PRs targeting branches other than `main`.
- Pushes to `main`, including merges and direct pushes.
- Calls from another workflow through `workflow_call`.

The job uses a GitHub-hosted Ubuntu runner, Corretto Java 25, the Gradle wrapper
and the same script used locally. It needs no deployment credentials. Superseded
PR runs are cancelled; main runs are not cancelled by this policy. The job has a
25-minute timeout and a final cleanup step as a backstop for the script's trap.
Reports and diagnostics are uploaded as the `smoke-test-reports` artifact and kept
for 14 days, even if an earlier step fails. If a report file is missing because the
run failed before producing it, the upload step logs a warning instead of failing.

### Enabling a gate later

For now, a smoke failure reports a failed check but does not stop the existing
deployment workflows. No branch protection or repository settings are changed.

To require success before merging, configure the **Smoke tests** job as a required
check in the repository's ruleset or branch protection after its first CI run.

To gate deployments, add a reusable-workflow job to the relevant build workflow:

```yaml
jobs:
  smoke-tests:
    uses: ./.github/workflows/smoke-tests.yml
  # Add smoke-tests to the existing deployment job's needs, preserving its
  # other prerequisites, permissions, environment and steps.
```

Then make the deployment job depend on the caller job with `needs`. Keep the
following in mind when making this change:

- An independent workflow cannot be a `needs` dependency of a job in another
  workflow; only a caller job that uses it can.
- Remove the standalone workflow's own `pull_request` and `push` triggers once the
  caller pattern is in place, so the same commit is not tested twice.
- Give the caller's job a concurrency group distinct from the reusable workflow's
  `-smoke-` group, so a new commit cannot cancel the caller's own run.
