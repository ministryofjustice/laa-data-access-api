# Infrastructure Smoke Tests

## Overview

Infrastructure smoke tests are capable of testing a live HTTP API — no mocking, no in-process Spring context. 
They are intended to catch failures in real infrastructure that unit and standard integration tests cannot, such as
Flyway migration problems, security filter misconfiguration, and environment-specific wiring
issues.

They are distinct from the standard integration test suite, which boots a local Spring context
backed by Testcontainers.

---

## Running the smoke tests locally

A convenience script builds the application, starts the full local infrastructure, runs the
smoke tests, then tears everything down regardless of the outcome:

```bash
./scripts/run-infrastructure-smoke-tests.sh
```

### What the script does

| Step | Detail |
|------|--------|
| **Build JAR** | `./gradlew :data-access-service:bootJar` — required by the `Dockerfile` `COPY` step |
| **Start infrastructure** | `docker compose -f docker-compose.smoke-test.yml up --build --detach --wait` — builds the app image and starts Postgres, blocking until both healthchecks pass |
| **Run smoke tests** | `./gradlew :data-access-service:infrastructureTest` — only `@SmokeTest`-annotated tests execute; all others are excluded by the task's `includeTags("smoke")` filter |
| **Teardown** | `docker compose down --volumes --remove-orphans` — runs unconditionally via a shell `trap`, whether tests pass, fail, or the script is interrupted |

### Prerequisites

- Docker running locally
- Java 25 on `$PATH` (or `JAVA_HOME` set)
- A built JAR in `data-access-service/build/libs/` (the script builds this automatically)

### Environment variables

The following variables default to the values used by `docker-compose.smoke-test.yml` but can be
overridden before running the script:

| Variable | Default |
|----------|---------|
| `LAA_SMOKE_ACCESS_API_URL` | `http://localhost:9000` |
| `LAA_SMOKE_ACCESS_DB_URL` | `jdbc:postgresql://localhost:6432/laa_data_access_api` |
| `LAA_SMOKE_ACCESS_DB_USERNAME` | `laa_user` |
| `LAA_SMOKE_ACCESS_DB_PASSWORD` | `laa_password` |

```bash
LAA_SMOKE_ACCESS_API_URL=http://my-host:9000 ./scripts/run-infrastructure-smoke-tests.sh
```

---

## How it works

### `infrastructureTest` Gradle task

The `infrastructureTest` task is a dedicated Gradle task that unconditionally sets
`test.mode=infrastructure` as a JVM system property, restricts execution to `@SmokeTest`-annotated
tests via `useJUnitPlatform { includeTags 'smoke' }`, and forwards the `LAA_ACCESS_*` environment
variables into the test JVM.

This means running `./gradlew :data-access-service:infrastructureTest` always means
"run smoke tests against a live environment" — no flags needed, no ambiguity.

The `integrationTest` task is completely separate and always runs all integration tests against
Testcontainers — it has no knowledge of infrastructure mode.

### `test.mode` system property

The `infrastructureTest` task sets `systemProperty 'test.mode', 'infrastructure'` unconditionally.
`HarnessExtension` reads this to switch from `IntegrationTestContextProvider` (local Spring context +
Testcontainers) to `InfrastructureTestContextProvider` (thin JPA context + `WebTestClient` pointed at
the live app).

When running `integrationTest`, no `test.mode` property is set and `HarnessExtension` defaults to
`"integration"` mode.

### `InfrastructureTestContextProvider`

Bootstraps only what is needed to support the tests against the live infrastructure:

- A `DataSource` connecting to the running Postgres container
- JPA repositories (via `@EnableJpaRepositories`) for test data setup and teardown
- A `WebTestClient` targeting `LAA_ACCESS_API_URL`
- An `ObjectMapper` bean
- Test utility components (`PersistedDataGenerator` etc.) via a targeted
  `@ComponentScan("...utils.generator")`

No application beans (controllers, security config, services) are loaded — the real app
running in Docker provides those.

### `HarnessExtension` — `ExecutionCondition`

When `test.mode=infrastructure` is set (by the `infrastructureTest` task), `HarnessExtension`
implements JUnit 5's `ExecutionCondition` to enforce that only `@SmokeTest`-annotated tests run:

- **Class-level evaluation**: if the class (or any of its methods) carries `@SmokeTest`,
  the class is enabled and per-method evaluation proceeds; otherwise the whole class is
  skipped.
- **Method-level evaluation**: a method runs if `@SmokeTest` is present on the method
  itself or on the enclosing class; otherwise it is skipped.

In normal integration mode (`test.mode=integration`, or unset) the condition always returns
enabled and every test runs as usual — `@SmokeTest` has no effect.

---

## Marking a test as a smoke test

Apply `@SmokeTest` to a test class to include all of its tests in a smoke run:

```java
@SmokeTest
public class GetCaseworkersTest extends BaseHarnessTest {
    // all tests in this class run in infrastructure mode
}
```

Or apply it to individual methods for finer-grained control:

```java
public class GetApplicationsTest extends BaseHarnessTest {

    @Test
    @SmokeTest
    void givenValidRequest_whenGetApplications_thenReturnOK() { ... }

    @Test
    void givenInvalidHeader_whenGetApplications_thenReturnBadRequest() { ... } // skipped in infra mode
}
```

Only tests that extend `BaseHarnessTest` (and therefore register `HarnessExtension`) are
eligible as smoke tests. Tests that still extend `BaseIntegrationTest` must be ported to the
harness first — see the `--tests` filter in the script which scopes the run to harness-based
classes in the interim.

---

## Adding a new smoke test class

1. Ensure the test class extends `BaseHarnessTest`.
2. Annotate the class (or specific methods) with `@SmokeTest`.

The `infrastructureTest` task's `includeTags 'smoke'` filter automatically selects all
`@SmokeTest`-annotated tests — no changes to the script or build file are needed.

