# Plan: Separate Gradle Tasks and Isolate Smoke-Test Docker Compose

## Background

Two problems to fix:

1. The `integrationTest` Gradle task currently serves two purposes, controlled by a `-PtestMode=infrastructure`
   flag. This makes it unclear what will run and why. The fix is two separate, self-describing tasks.

2. `docker-compose.local.yml` (used by the smoke-test script) shares Docker resource names — container names
   and the `pgdata` named volume — with `docker-compose.yml` (the developer environment). Running
   `docker compose down --volumes` from the smoke-test script silently deletes the developer's database.
   The fix is a dedicated compose file with isolated resource names.

The `test.mode` system property remains — `HarnessExtension` still reads it to select the correct
`TestContextProvider` at runtime. The only change is that the `infrastructureTest` Gradle task sets it
unconditionally instead of it being passed in by the caller via `-PtestMode`.

---

## Changes

### 1. Rename `docker-compose.local.yml` → `docker-compose.smoke-test.yml`

Rename the file and update all resource names inside it so they are completely isolated from
`docker-compose.yml`:

| Resource | Current (shared with dev env) | After (isolated) |
|---|---|---|
| Postgres service / container name | `postgres` / `laa-postgres` | `postgres-smoketest` / `laa-postgres-smoketest` |
| App service / container name | `app` / `laa-data-access-api` | `app-smoketest` / `laa-data-access-api-smoketest` |
| Named volume | `pgdata` | `pgdata-smoketest` |

After this change, `docker compose -f docker-compose.smoke-test.yml down --volumes` only removes
`pgdata-smoketest`. The developer's `pgdata` volume is completely safe.

The `SPRING_DATASOURCE_URL` inside the compose file must also be updated to reference
`postgres-smoketest` (the new service name) instead of `postgres`.

---

### 2. Simplify `tasks.named('integrationTest')` in `data-access-service/build.gradle`

Remove the `findProperty('testMode')` branching entirely. The task always runs all integration tests
against Testcontainers:

```groovy
tasks.named('integrationTest') {
    useJUnitPlatform()

    testLogging {
        events TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED
        exceptionFormat = 'full'
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }
}
```

The `LAA_ACCESS_*` environment-variable forwarding and the `systemProperty 'test.mode'` line move to
the new `infrastructureTest` task below.

---

### 3. Add `infrastructureTest` task to `data-access-service/build.gradle`

Register a new task that sets `test.mode=infrastructure` unconditionally and filters to `@SmokeTest`
tests only. No `-PtestMode` flag is needed or accepted:

```groovy
tasks.register('infrastructureTest', Test) {
    description = 'Runs @SmokeTest-annotated tests against a live deployed environment'
    group = 'verification'
    testClassesDirs = sourceSets.integrationTest.output.classesDirs
    classpath = sourceSets.integrationTest.runtimeClasspath

    // Unconditionally use infrastructure mode — no flag needed
    systemProperty 'test.mode', 'infrastructure'

    filter {
        includeTestsMatching 'uk.gov.justice.laa.dstew.access.controller.*'
    }
    useJUnitPlatform {
        includeTags 'smoke'
    }

    ['LAA_ACCESS_API_URL', 'LAA_ACCESS_DB_URL', 'LAA_ACCESS_DB_USERNAME', 'LAA_ACCESS_DB_PASSWORD'].each { key ->
        def value = System.getenv(key)
        if (value) environment key, value
    }

    testLogging {
        events TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED
        exceptionFormat = 'full'
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }
}
```

This task is not wired into `check` or `build` — it only runs when explicitly invoked.

---

### 4. Update `scripts/run-infrastructure-smoke-tests.sh`

Three changes:

- Update `COMPOSE_FILE` to point at `docker-compose.smoke-test.yml`
- Remove `-PtestMode=infrastructure` from the `./gradlew` invocation
- Change the task from `integrationTest` to `infrastructureTest`
- Update the failure log line that references the report path
  (`integrationTest/index.html` → `infrastructureTest/index.html`)

---

### 5. Update `SmokeTest.java` Javadoc

The existing Javadoc references `-Dtest.mode=infrastructure`. Update it to describe the
`infrastructureTest` Gradle task instead, since that is now the only entry point for infrastructure
mode.

---

### 6. Update `docs/infrastructure-smoke-tests.md`

- Replace all references to `docker-compose.local.yml` with `docker-compose.smoke-test.yml`
- Replace the "Run smoke tests" table row: change `-Dtest.mode=infrastructure` / `integrationTest`
  to `infrastructureTest`
- Update the "`test.mode` system property" section to explain it is now set by the Gradle task
  rather than passed in by the caller
- Update the "Adding a new smoke test class" section: remove the instruction to add classes to a
  `--tests` filter in the script (the `includeTags 'smoke'` in the Gradle task handles selection)

---

## What does NOT change

| Item | Reason |
|---|---|
| `HarnessExtension.java` | Still reads `System.getProperty("test.mode", "integration")` — no change needed |
| `IntegrationTestContextProvider` / `InfrastructureTestContextProvider` | No change needed |
| `@SmokeTest` annotation | Still marks tests; `@Tag("smoke")` still drives `includeTags` |
| `BaseHarnessTest` | No change needed |
| `docker-compose.yml` | Developer environment — completely untouched |
| All test classes | No change needed |

---

## Developer workflow after this change

| Command | What runs |
|---|---|
| `./gradlew integrationTest` | All integration tests, Testcontainers-backed, no flags needed |
| `./gradlew infrastructureTest` | `@SmokeTest` tests only, against a live environment (requires `LAA_ACCESS_*` env vars) |
| `./scripts/run-infrastructure-smoke-tests.sh` | Builds JAR, starts `docker-compose.smoke-test.yml`, runs `infrastructureTest`, tears down |
| `docker compose up -d` | Developer Postgres only, `pgdata` volume — completely unaffected |

---

## CI pipeline note

Any CI job currently calling `./gradlew :data-access-service:integrationTest -PtestMode=infrastructure`
must be updated to call `./gradlew :data-access-service:infrastructureTest` instead. The old invocation
will silently run all integration tests in Testcontainers mode (the property is ignored after this
change), which is unlikely to be what CI intends.

