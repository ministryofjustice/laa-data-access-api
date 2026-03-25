# Integration Test Harness

## Overview

The integration test suite uses a dual-harness design that can target two different
execution environments without any change to the test code itself:

| Mode                                                      | System property | Spring context | Database |
|-----------------------------------------------------------|----------------|----------------|----------|
| **Integration** (default)                                 | _(none)_ | Full `AccessApp` boot | Testcontainers Postgres — ephemeral, torn down after the suite |
| **Infrastructure** (only tests annotated with @SmokeTest) | `-Dtest.mode=infrastructure` | Minimal JPA-only context | Real environment DB at `LAA_ACCESS_DB_URL` |

The same base class, test lifecycle, and teardown mechanism runs identically in both
modes. The only thing that changes is which `TestContextProvider` implementation is
wired in.

---

## Architecture

### Class hierarchy

```
TestContextProvider  (interface)
├── IntegrationTestContextProvider   — integration mode
└── InfrastructureTestContextProvider — infrastructure mode

BaseHarnessTest  (abstract)
└── <concrete test class>   e.g. GetApplicationTest, CreateApplicationTest
    └── HarnessDataIsolationTest
```

### Key components

| Component | Location | Responsibility |
|-----------|----------|---------------|
| `HarnessExtension` | `utils/harness/` | JUnit 5 extension; selects the `TestContextProvider`, injects `@HarnessInject` fields, and conditionally skips non-`@SmokeTest` tests in infrastructure mode |
| `TestContextProvider` | `utils/harness/` | Interface; exposes `webTestClient()` and `getBean(Class<T>)` |
| `IntegrationTestContextProvider` | `utils/harness/` | Boots a full Spring application context with a Testcontainers Postgres; used for normal `./gradlew integrationTest` runs |
| `InfrastructureTestContextProvider` | `utils/harness/` | Builds a minimal JPA-only context pointing at a real database; used when `test.mode=infrastructure` |
| `BaseHarnessTest` | `utils/harness/` | Abstract base for all harness tests; owns the `@BeforeEach` / `@AfterEach` lifecycle and HTTP helper methods |
| `PersistedDataGenerator` | `utils/generator/` | Persists entities and automatically tracks their IDs for teardown |
| `HarnessInject` | `utils/harness/` | Marker annotation; fields annotated with this are injected by `HarnessExtension.postProcessTestInstance` |
| `HarnessResult` | `utils/harness/` | Thin wrapper around an HTTP response, mirroring the `MvcResult` surface used by shared assertion helpers |
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
|----------|---------|
| `LAA_ACCESS_API_URL` | Base URL of the live API under test |
| `LAA_ACCESS_DB_URL` | JDBC URL of the live database |
| `LAA_ACCESS_DB_USERNAME` | Database username |
| `LAA_ACCESS_DB_PASSWORD` | Database password |

---

## Why two modes need the same teardown strategy

`BaseHarnessTest` drives the application via `WebTestClient`. Requests execute in a
separate thread inside the embedded (or real) server; they commit their own JPA
transactions independently of the test method. This means `@Transactional` rollback
— the strategy used by `BaseIntegrationTest` with `MockMvc` — cannot be used here.

Instead, every entity that the test creates must be deleted explicitly after the test.
Critically, **the same mechanism must work safely in infrastructure mode** where the
database may contain real production data. A `deleteAll()` or any bulk-delete call
would be catastrophic. The teardown must only ever touch rows that the current test
itself created.

---

## Data tracking and teardown

### How tracking works

`PersistedDataGenerator` is the single gateway through which tests persist entities.
Every call to `createAndPersist(...)` or `createAndPersistMultiple(...)` does two
things:

1. Calls `repository.saveAndFlush(entity)` to commit the row to the database.
2. Calls `track(entity)` to add the entity's UUID to the appropriate in-memory list:

```
trackedCaseworkerIds   — populated for CaseworkerEntity
trackedApplicationIds  — populated for ApplicationEntity
trackedDomainEventIds  — populated for DomainEventEntity
```

Because tracking happens immediately after the `saveAndFlush` returns — on the very
same line, before any assertion or further logic that could throw — the ID is
registered even if the test subsequently fails.

### The `@BeforeEach` lifecycle

`BaseHarnessTest.setupHarness()` runs before every test method:

1. Resolves beans from the `TestContextProvider` (`PersistedDataGenerator`,
   repositories, assertion helpers, etc.).
2. Calls `persistedDataGenerator.clearTrackedIds()` as a defensive measure, ensuring
   no stale IDs from a previous test's failed teardown can bleed in.
3. Creates and persists `CaseworkerJohnDoe` and `CaseworkerJaneDoe` via
   `persistedDataGenerator.createAndPersist(...)`. Because these go through
   `PersistedDataGenerator`, their IDs are auto-tracked.

### The `@AfterEach` teardown

`BaseHarnessTest.tearDownTrackedData()` runs after every test method (and is also
callable mid-test when a test needs manual control):

```java
persistedDataGenerator.deleteTrackedData();
```

`PersistedDataGenerator.deleteTrackedData()` deletes in leaf-to-root order to satisfy
foreign-key constraints:

1. **Domain events** — `trackedDomainEventIds` deleted first
2. **Applications** — `trackedApplicationIds` deleted next
   (DB `ON DELETE CASCADE` handles proceedings, decisions, merits decisions, certificates)
3. **Caseworkers** — `trackedCaseworkerIds` deleted last

Each delete uses `findById(id).ifPresent(repo::delete)`, which is:

- **Idempotent** — safe if the test already deleted the row
- **Surgical** — only touches the specific rows whose IDs were registered

After deletion, `clearTrackedIds()` is always called in a `finally` block, so stale
IDs are never carried into the next test even if a delete operation throws.

@AfterEach teardown is guaranteed to run even if the test method throws an exception, 
so any test failure will still trigger the cleanup of tracked data.

### Tracking list access

`PersistedDataGenerator` also exposes:

```java
List<UUID> trackedApplicationIds()  // read-only view — useful for "nothing persisted" assertions
```

`BaseHarnessTest` surfaces this as:

```java
protected List<UUID> trackedApplicationIds()
```

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
populated by `HarnessExtension.postProcessTestInstance` before any test method runs:

```java
@HarnessInject
protected WebTestClient webTestClient;

@HarnessInject
protected TestContextProvider harnessProvider;
```

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

The class extends `BaseHarnessTest` and is annotated
`@TestInstance(Lifecycle.PER_CLASS)` so that `@BeforeAll` / `@AfterAll` can manage
**sentinel** entities that persist across all test methods.

### Sentinel strategy

Before any test method runs, `@BeforeAll createSentinels()` persists three rows
**directly via repositories**, bypassing `PersistedDataGenerator`. Because they
bypass `createAndPersist`, they are never registered in the tracking lists:

| Sentinel | Entity type | How created |
|----------|-------------|-------------|
| `sentinelCaseworker` | `CaseworkerEntity` | `cwRepo.saveAndFlush(...)` |
| `sentinelApplication` | `ApplicationEntity` | `appRepo.saveAndFlush(...)` |
| `sentinelDomainEvent` | `DomainEventEntity` | `deRepo.saveAndFlush(...)` |

After all test methods (and their `@AfterEach` teardowns) have completed,
`@AfterAll assertSentinelsSurvivedThenDelete()` asserts that every sentinel is still
present, then manually deletes them. If any sentinel is missing, the assertion fails —
proving the harness deleted data it should not have.

### Test descriptions

#### Test 1 — Pre-existing caseworker survives harness teardown

```
Given: a sentinel caseworker was persisted before any test (not tracked)
And:   @BeforeEach created CaseworkerJohnDoe (auto-tracked)
When:  the test body completes and @AfterEach tearDownTrackedData() fires
Then:  the sentinel caseworker is still present
And:   CaseworkerJohnDoe is still present during the test body
```

Verified by `givenPreExistingCaseworker_whenHarnessTeardownRuns_thenSentinelIsUntouched`.

#### Test 2 — Pre-existing application survives harness teardown

```
Given: a sentinel application was persisted before any test (not tracked)
And:   the test body creates a second application via persistedDataGenerator (auto-tracked)
When:  @AfterEach tearDownTrackedData() fires
Then:  the sentinel application is still present
```

Verified by `givenPreExistingApplication_whenHarnessTeardownRuns_thenOnlyTrackedApplicationIsRemoved`.

#### Test 3 — Pre-existing domain event survives harness teardown

```
Given: a sentinel domain event was persisted before any test (not tracked)
And:   the test body creates a domain event via persistedDataGenerator (auto-tracked)
When:  @AfterEach tearDownTrackedData() fires
Then:  the sentinel domain event is still present
```

Verified by `givenPreExistingDomainEvent_whenHarnessTeardownRuns_thenOnlyTrackedDomainEventIsRemoved`.

#### Test 4 — Only tracked entities are removed; untracked entities survive

```
Given: entityA is persisted directly via the repository (not tracked)
And:   entityB is persisted via persistedDataGenerator (auto-tracked)
When:  tearDownTrackedData() is called
Then:  entityB is removed
And:   entityA is still present (the test then explicitly deletes it)
```

This is the core invariant: the tracked teardown is purely surgical.

Verified by `givenUntrackedAndTrackedEntities_whenHarnessTeardownRuns_thenOnlyTrackedEntityIsRemoved`.

#### Test 5 — Partial setup failure does not leave orphaned rows

```
Given: setup partially runs — one caseworker is persisted and auto-tracked
And:   teardown is invoked manually before setup completes
When:  deleteTrackedData() runs
Then:  the partially-created caseworker is removed
And:   the test re-seeds JohnDoe and JaneDoe so the @AfterEach no-ops cleanly
```

This test simulates a scenario where `@BeforeEach` fails mid-way. It verifies that any
entity that was already registered in the tracking lists before the failure is still
cleaned up — no orphaned rows remain.

Verified by `givenPartialSetupFailure_whenTeardownRuns_thenPartiallyCreatedDataIsRemoved`.

---

## Sequence diagram — per-test lifecycle

```
JUnit
  │
  ├─ HarnessExtension.postProcessTestInstance
  │    └─ injects @HarnessInject fields (webTestClient, harnessProvider)
  │
  ├─ @BeforeAll (if PER_CLASS) — e.g. createSentinels in HarnessDataIsolationTest
  │
  ├─ @BeforeEach BaseHarnessTest.setupHarness()
  │    ├─ resolves beans from harnessProvider
  │    ├─ clearTrackedIds()                          ← defensive reset
  │    ├─ createAndPersist(CaseworkerJohnDoe)        ← auto-tracked
  │    └─ createAndPersist(CaseworkerJaneDoe)        ← auto-tracked
  │
  ├─ @Test method body
  │    └─ createAndPersist(...)                      ← each persist auto-tracked
  │
  ├─ @AfterEach BaseHarnessTest.tearDownTrackedData()
  │    └─ persistedDataGenerator.deleteTrackedData()
  │         ├─ delete tracked domain events   (by ID, idempotent)
  │         ├─ delete tracked applications    (by ID, idempotent; cascade removes children)
  │         ├─ delete tracked caseworkers     (by ID, idempotent)
  │         └─ clearTrackedIds()              ← always, even if a delete threw
  │
  └─ @AfterAll (if PER_CLASS) — e.g. assertSentinelsSurvivedThenDelete
```

---

## File reference

```
data-access-service/src/integrationTest/java/uk/gov/justice/laa/dstew/access/
├── utils/
│   ├── harness/
│   │   ├── BaseHarnessTest.java                  — abstract base; lifecycle & HTTP helpers
│   │   ├── HarnessDataIsolationTest.java         — proves teardown data isolation
│   │   ├── HarnessExtension.java                 — JUnit 5 extension; mode selection & injection
│   │   ├── HarnessInject.java                    — field injection marker annotation
│   │   ├── HarnessResult.java                    — HTTP response wrapper
│   │   ├── InfrastructureTestContextProvider.java — JPA-only context for real infrastructure
│   │   ├── IntegrationTestContextProvider.java   — full Spring Boot + Testcontainers context
│   │   ├── SmokeTest.java                        — smoke-test marker annotation
│   │   └── TestContextProvider.java              — interface implemented by both providers
│   └── generator/
│       └── PersistedDataGenerator.java           — persists entities and tracks IDs for teardown
```

