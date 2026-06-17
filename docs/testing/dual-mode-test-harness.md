# Dual-mode Test Harness

## Overview

The integration test suite uses a dual-harness design that can target two different
execution environments without any change to the test code itself:

| Mode | System property | Spring context | Database |
|---|---|---|---|
| **Integration** (default) | _(none)_ | Full `AccessApp` boot | Testcontainers Postgres — ephemeral, torn down after the suite |
| **Infrastructure** (only tests annotated with `@SmokeTest`) | `-Dtest.mode=infrastructure` | Minimal JPA-only context | Real environment DB at `LAA_ACCESS_DB_URL` |

The same base class, test lifecycle, and teardown mechanism runs identically in both
modes. The only thing that changes is which `TestContextProvider` implementation is
wired in.

This allows us to run a subset of critical smoke tests against the real infrastructure
environment — providing confidence that the deployed API behaves as expected with the
real database and real deployed configuration.

In terms of end-to-end testing for **our** API, this covers what is necessary without
needing more tests and more things to maintain. We can be confident that if the smoke
tests pass in infrastructure mode, then the API is working correctly in that
environment.

---

## Architecture

### Class hierarchy

```
TestContextProvider  (interface)
├── IntegrationTestContextProvider    — integration mode
└── InfrastructureTestContextProvider — infrastructure mode

BaseHarnessTest  (abstract)
└── <concrete test class>   e.g. GetApplicationTest, CreateApplicationTest
```

### Key components

| Component | Location | Responsibility |
|---|---|---|
| `HarnessExtension` | `utils/harness/` | JUnit 5 extension; selects the `TestContextProvider`, injects `@HarnessInject` fields, captures the before-suite row-count snapshot (infrastructure mode only), and registers the end-of-suite parity asserter |
| `TestContextProvider` | `utils/harness/` | Interface; exposes `webTestClient()` and `getBean(Class<T>)` |
| `IntegrationTestContextProvider` | `utils/harness/` | Boots a full Spring application context with a Testcontainers Postgres; used for normal `./gradlew integrationTest` runs |
| `InfrastructureTestContextProvider` | `utils/harness/` | Builds a minimal JPA-only context pointing at a real database; used when `test.mode=infrastructure` |
| `BaseHarnessTest` | `utils/harness/` | Abstract base for all harness tests; owns the `@BeforeEach` / `@AfterEach` lifecycle and HTTP helper methods |
| `PersistedDataGenerator` | `utils/generator/` | Single gateway for all database writes; tracks every entity ID for teardown |
| `ApplicationDomainTables` | `utils/harness/` | Single source of truth for the ordered list of application-domain tables and the Flyway seed caseworker count; referenced by both assertion classes |
| `DatabaseCleanlinessAssertion` | `utils/harness/` | Queries every domain table and asserts each is empty; runs `@AfterEach @Order(2)` after tracked data is deleted — **integration mode only** |
| `TableRowCountAssertion` | `utils/harness/` | Captures a row-count snapshot and asserts parity between before/after snapshots; used by `HarnessExtension` at end-of-suite — **infrastructure mode only** |
| `AllTablesEmptyAfterSuiteTest` | `utils/harness/` | End-of-suite sentinel; asserts all tables are empty after the full integration test suite — **integration mode only** (no `@SmokeTest`) |
| `HarnessInject` | `utils/harness/` | Marker annotation; fields annotated with this are injected by `HarnessExtension.postProcessTestInstance` |
| `HarnessResult` | `utils/harness/` | Thin wrapper around an HTTP response |
| `SmokeTest` | `utils/harness/` | Marker annotation; controls which tests run in infrastructure mode |

---

## The two modes in detail

### Integration mode (`IntegrationTestContextProvider`)

A full `AccessApp` Spring Boot application is started once for the entire test suite
(stored in JUnit's root `ExtensionContext.Store` by `HarnessExtension` so that one
context is shared across all test classes). It is configured with:

- `--server.port=0` — a random free port
- `--spring.datasource.*` — pointed at the Testcontainers Postgres instance
- `--feature.enable-dev-token=true` — allows the test JWTs in `TestConstants.Tokens`

A `WebTestClient` is bound to the random port and exposed to tests.

### Infrastructure mode (`InfrastructureTestContextProvider`)

Instead of booting the full application, only the JPA layer is initialised.
`InfrastructureJpaConfig` (an inner `@Configuration` class) registers:

- A `DataSource` pointing at `LAA_ACCESS_DB_URL`
- A Hibernate `EntityManagerFactory` scanning `uk.gov.justice.laa.dstew.access.entity`
- JPA repositories under `uk.gov.justice.laa.dstew.access.repository`
- The `PersistedDataGenerator` component (for teardown)

The `WebTestClient` is bound to `LAA_ACCESS_API_URL` — the live API endpoint.

Required environment variables:

| Variable | Purpose |
|---|---|
| `LAA_ACCESS_API_URL` | Base URL of the live API under test |
| `LAA_ACCESS_DB_URL` | JDBC URL of the live database |
| `LAA_ACCESS_DB_USERNAME` | Database username |
| `LAA_ACCESS_DB_PASSWORD` | Database password |

---

## Why `@Transactional` rollback cannot be used

`BaseHarnessTest` drives the application via `WebTestClient`. Requests execute in a
separate thread inside the embedded (or real) server; they commit their own JPA
transactions independently of the test method. This means `@Transactional` rollback
— the strategy used by `BaseIntegrationTest` with `MockMvc` — cannot be used here.

Instead, every entity that the test creates must be deleted explicitly after the test.
Critically, **the same mechanism must work safely in infrastructure mode** where the
database may contain real data. A `deleteAll()` or any bulk-delete call would be
catastrophic. The teardown must only ever touch rows that the current test itself
created.

---

## Per-test lifecycle

```
HarnessExtension.postProcessTestInstance
  └── injects @HarnessInject fields (webTestClient, harnessProvider)

@BeforeEach @Order(1) BaseHarnessTest.setupHarness()
  ├── resolves beans from harnessProvider
  │     (objectMapper, persistedDataGenerator, repositories, asserters, dbCleanliness)
  ├── resets token state to CASEWORKER / omitToken = false
  ├── clearTrackedIds()                          ← defensive reset
  ├── createAndPersist(CaseworkerJohnDoe)        ← auto-tracked
  ├── createAndPersist(CaseworkerJaneDoe)        ← auto-tracked
  └── Caseworkers = List.of(JohnDoe, JaneDoe)

@Test method body
  └── createAndPersist(...)                      ← each persist auto-tracked
      postUri / patchUri / getUri(...)           ← HTTP via WebTestClient
      (token state reset automatically after each HTTP call)

@AfterEach @Order(1) BaseHarnessTest.tearDownTrackedData()
  └── persistedDataGenerator.deleteTrackedData()
        ├── JDBC pre-fetch: collect decision_id for each tracked application
        │     (needed because V14 reversed the FK: applications.decision_id → decisions,
        │      so deleting an application does NOT cascade-delete its decision)
        ├── delete tracked domain_events          (by ID, idempotent)
        ├── delete tracked applications           (cascade removes proceedings,
        │                                          merits_decisions, certificates,
        │                                          linked_individuals join rows)
        ├── delete tracked decisions              (after application; FK now clears first)
        ├── delete tracked caseworkers            (by ID, idempotent)
        ├── delete tracked individuals            (by ID, idempotent)
        └── clearTrackedIds()                     ← always, even if a delete threw

@AfterEach @Order(2) BaseHarnessTest.assertDatabaseCleanAfterTest()
  └── DatabaseCleanlinessAssertion.assertAllTablesEmpty()
        ├── queries every domain table (domain_events → caseworkers)
        ├── subtracts FLYWAY_SEEDED_CASEWORKER_COUNT from the caseworkers count
        │     (R__insert_test_data.sql seeds fixed rows that must not be treated as pollution)
        └── throws AssertionError if anything remains — fails the test immediately
```

The `@Order` annotation ensures `tearDownTrackedData()` always runs before
`assertDatabaseCleanAfterTest()`. Both `@AfterEach` methods are guaranteed to run
even if the test method throws.

> **Infrastructure mode note:** `assertDatabaseCleanAfterTest()` uses
> `DatabaseCleanlinessAssertion`, which asserts tables are _empty_. This would
> always fail against a live database with real data. In infrastructure mode the
> per-test empty check is replaced by the end-of-suite row-count reconciliation
> described in the next section.

---

## End-of-suite row-count reconciliation (infrastructure mode only)

In infrastructure mode the live database legitimately contains data before the suite
starts, so asserting tables are empty after each test is not appropriate. Instead,
`HarnessExtension` performs a **row-count reconciliation** across the whole suite:

1. **Before any test runs** — on the very first `beforeAll` call, `HarnessExtension`
   captures a snapshot of the current row count for every application-domain table
   (via `TableRowCountAssertion.captureRowCounts()`). This snapshot is stored in the
   JUnit root `ExtensionContext.Store`.

2. **After all tests have finished** — a `CloseableResource` registered in the same
   root store fires when JUnit closes the store at the end of the suite. It calls
   `TableRowCountAssertion.assertRowCountsMatch(snapshot, ...)`, which re-queries
   every table and compares the current counts against the snapshot. Any delta
   (rows inserted or deleted without cleanup) produces an `AssertionError` naming
   the affected tables and the size of the change.

This approach avoids all class-ordering concerns: the snapshot is captured inside
`HarnessExtension.beforeAll` (which fires before any test in that class runs), and the
assertion fires via `CloseableResource.close()` which is guaranteed to execute after
every test class has torn down.

The `CloseableResource` asserter is registered in the root store **after** the
`TestContextProvider`, so JUnit closes it first (in reverse-registration order) —
meaning the Spring context and `JdbcTemplate` are still alive when the final SQL
queries run.

### What the reconciliation catches

| Scenario | Detected? |
|---|---|
| Test inserts a row and fails to delete it | ✅ Row count increased |
| Test deletes a pre-existing row (data destruction) | ✅ Row count decreased |
| Test inserts and deletes the same number of rows in different tables | ✅ Per-table deltas are checked independently |
| Two tests each leave one row, but in different test runs | ✅ Caught within the same suite run |

### What the reconciliation does not catch

| Scenario | Not detected |
|---|---|
| Test inserts a row and deletes a different pre-existing row in the same table | ❌ Net delta is zero — counts match but content changed |

This is an acceptable trade-off for infrastructure mode. The `PersistedDataGenerator`
teardown mechanism and the per-test `@AfterEach` ordering already prevent accidental
deletion of untracked rows (proven by `HarnessDataIsolationTest`). The reconciliation
check is a safety net for cases where teardown fails silently.

---

## Data tracking and teardown

### How tracking works

`PersistedDataGenerator` is the single gateway through which tests persist entities.
Every call to `createAndPersist(...)` or `createAndPersistMultiple(...)` does two things:

1. Calls `repository.saveAndFlush(entity)` — commits the row immediately so it is visible to the HTTP server
2. Calls `track(entity)` — adds the entity's UUID to the appropriate in-memory list

The tracking lists maintained by `PersistedDataGenerator`:

| List | Populated by |
|---|---|
| `trackedCaseworkerIds` | `CaseworkerEntity` |
| `trackedApplicationIds` | `ApplicationEntity` (also auto-tracks cascade-persisted `IndividualEntity` instances) |
| `trackedDomainEventIds` | `DomainEventEntity` |
| `trackedIndividualIds` | `IndividualEntity`; also populated by `trackExistingApplication()` via JDBC |
| `trackedDecisionIds` | `DecisionEntity`; also populated by JDBC pre-fetch during `deleteTrackedData()` |

Because tracking happens on the same line as `saveAndFlush`, before any assertion or
subsequent logic that could throw, the ID is registered even if the test subsequently
fails.

### API-created rows

When the production API creates rows (e.g. via `POST /applications`), those rows are
not tracked automatically. The test must call:

```java
persistedDataGenerator.trackExistingApplication(createdId);
```

This adds the application ID to the tracking list and also uses a JDBC query on
`linked_individuals` to register any individual IDs that were cascade-persisted with
the application, since the `individuals` table is not cascade-deleted when an
application is removed.

### The V14 FK reversal

Prior to migration `V14__change_decision_relationship.sql`, decisions were a child of
applications and `ON DELETE CASCADE` removed them automatically. V14 reversed the FK
so that `applications.decision_id → decisions`. This means deleting an application
row no longer cascade-deletes its decision.

`deleteTrackedData()` handles this explicitly:

1. Before deleting any application, a JDBC query fetches the `decision_id` for each
   tracked application and adds it to `trackedDecisionIds`.
2. Applications are deleted (clearing the FK column).
3. Decisions are deleted afterwards, now that the FK reference is gone.

### Updating already-tracked entities

When a test needs to mutate an entity after its initial persist (e.g. setting a FK
after both sides are saved), use:

```java
persistedDataGenerator.updateAndFlush(entity);
```

This calls `saveAndFlush` without re-registering the ID in the tracking list.
Supported entity types: `ApplicationEntity`, `CaseworkerEntity`, `IndividualEntity`.

---

## Auth and token helpers

`BaseHarnessTest` manages a per-test token state. All HTTP helpers read from this
state and reset it to the default caseworker token after each call.

```java
// Default: every request uses TestConstants.Tokens.CASEWORKER
withToken(TestConstants.Tokens.UNKNOWN);  // set a different token for the next call
withNoToken();                             // omit the Authorization header entirely
```

Because the state resets after each HTTP call, there is no need to restore the token
between test steps.

---

## `@SmokeTest` and mode selection

`HarnessExtension` implements `ExecutionCondition`. When `test.mode=infrastructure`:

- A test class or method annotated `@SmokeTest` — **included**
- Everything else — **skipped**

In the default integration mode the annotation has no effect and all tests run.

`@SmokeTest` can be applied at **class level** (all methods become smoke tests) or at
**individual method level** for finer-grained control.

---

## `@HarnessInject`

Fields in `BaseHarnessTest` (and its subclasses) annotated with `@HarnessInject` are
populated by `HarnessExtension.postProcessTestInstance` before any test method runs.
`HarnessExtension` resolves `WebTestClient` directly from the `TestContextProvider`,
and resolves any other annotated field type via `provider.getBean(field.getType())`.
The annotation is processed up the full inheritance hierarchy, so subclasses can
declare their own `@HarnessInject` fields.

---

## Testing the harness itself: `HarnessDataIsolationTest`

### Purpose

`HarnessDataIsolationTest` proves that `tearDownTrackedData()` never deletes data that
was not created by the current test — the core safety guarantee needed for
infrastructure mode.

### Sentinel strategy

Before any test method runs, `@BeforeAll createSentinels()` persists entities
**directly via repositories**, bypassing `PersistedDataGenerator`. Because they bypass
`createAndPersist`, they are never registered in the tracking lists.

After all test methods (and their `@AfterEach` teardowns) have completed,
`@AfterAll assertSentinelsSurvivedThenDelete()` asserts that every sentinel is still
present, then manually deletes them. If any sentinel is missing, the assertion fails —
proving the harness deleted data it should not have.

### Test coverage

| Test | What it proves |
|---|---|
| `givenPreExistingCaseworker_whenHarnessTeardownRuns_thenSentinelIsUntouched` | Pre-existing caseworkers survive `@AfterEach` teardown |
| `givenPreExistingApplication_whenHarnessTeardownRuns_thenOnlyTrackedApplicationIsRemoved` | Only the tracked application is deleted; the sentinel application survives |
| `givenPreExistingDomainEvent_whenHarnessTeardownRuns_thenOnlyTrackedDomainEventIsRemoved` | Only the tracked domain event is deleted; the sentinel survives |
| `givenUntrackedAndTrackedEntities_whenHarnessTeardownRuns_thenOnlyTrackedEntityIsRemoved` | Core invariant: teardown is purely surgical |
| `givenPartialSetupFailure_whenTeardownRuns_thenPartiallyCreatedDataIsRemoved` | Entities registered before a mid-setup failure are still cleaned up |

---

## Sequence diagram — per-test lifecycle

```
JUnit
  │
  ├─ HarnessExtension.beforeAll (first test class only)
  │    ├─ creates TestContextProvider (integration or infrastructure)
  │    └─ [infrastructure mode only]
  │         ├─ captures before-suite row-count snapshot → root store
  │         └─ registers CloseableResource parity asserter → root store
  │
  ├─ HarnessExtension.postProcessTestInstance
  │    └─ injects @HarnessInject fields (webTestClient, harnessProvider)
  │
  ├─ @BeforeEach @Order(1) setupHarness()
  │    ├─ resolves beans from harnessProvider
  │    ├─ clearTrackedIds()
  │    ├─ createAndPersist(CaseworkerJohnDoe)     ← auto-tracked
  │    ├─ createAndPersist(CaseworkerJaneDoe     ← auto-tracked
  │    └─ Caseworkers = List.of(JohnDoe, JaneDoe)
  │
  ├─ @Test method body
  │    └─ createAndPersist(...)                   ← each persist auto-tracked
  │
  ├─ @AfterEach @Order(1) tearDownTrackedData()
  │    └─ persistedDataGenerator.deleteTrackedData()
  │         ├─ JDBC pre-fetch decision_ids
  │         ├─ delete domain events     (by ID, idempotent)
  │         ├─ delete applications      (cascade: proceedings, certificates, linked rows)
  │         ├─ delete decisions         (after application FK clears)
  │         ├─ delete caseworkers       (by ID, idempotent)
  │         ├─ delete individuals       (by ID, idempotent)
  │         └─ clearTrackedIds()        ← always, even if a delete threw
  │
  ├─ @AfterEach @Order(2) assertDatabaseCleanAfterTest()
  │    └─ [integration mode only] DatabaseCleanlinessAssertion.assertAllTablesEmpty()
  │
  │   ... (repeated for every test class) ...
  │
  └─ JUnit root store closes (end of suite)
       └─ [infrastructure mode only] CloseableResource asserter fires
            └─ TableRowCountAssertion.assertRowCountsMatch(snapshot, "full-infrastructure-suite")
```

---

## File reference

```
data-access-service/src/integrationTest/java/uk/gov/justice/laa/dstew/access/
├── utils/
│   ├── harness/
│   │   ├── AllTablesEmptyAfterSuiteTest.java      — end-of-suite empty check (integration mode only)
│   │   ├── ApplicationDomainTables.java           — canonical table list and Flyway seed count
│   │   ├── BaseHarnessTest.java                   — abstract base; lifecycle & HTTP helpers
│   │   ├── DatabaseCleanlinessAssertion.java      — asserts all domain tables empty after each test (integration mode)
│   │   ├── HarnessDataIsolationTest.java          — proves teardown data isolation
│   │   ├── HarnessExtension.java                  — JUnit 5 extension; mode selection, injection, row-count reconciliation
│   │   ├── HarnessInject.java                     — field injection marker annotation
│   │   ├── HarnessResult.java                     — HTTP response wrapper
│   │   ├── InfrastructureTestContextProvider.java — JPA-only context for real infrastructure
│   │   ├── IntegrationTestContextProvider.java    — full Spring Boot + Testcontainers context
│   │   ├── SmokeTest.java                         — smoke-test marker annotation
│   │   ├── TableRowCountAssertion.java            — captures row-count snapshots and asserts parity (infrastructure mode)
│   │   └── TestContextProvider.java               — interface implemented by both providers
│   └── generator/
│       └── PersistedDataGenerator.java            — persists entities and tracks IDs for teardown
```
