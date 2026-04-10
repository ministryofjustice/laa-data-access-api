# Plan 07 — Investigate Test Data Cross-Contamination

## Context

`PersistedDataGenerator` is the single authorised mechanism for persisting test data.
It tracks every row it creates and removes them in `@AfterEach` via `deleteTrackedData()`.

Three controller tests currently write to the database **outside** this mechanism:

| File | Line | Pattern | Risk |
|------|------|---------|------|
| `GetApplicationsTest` | 142 | `applicationRepository.saveAndFlush(applicationEntity)` — mid-test mutation of `submittedAt` | row already tracked by generator; mutation is safe but inconsistent |
| `GetApplicationTest` | 94 | `applicationRepository.saveAndFlush(application)` — attaches a `DecisionEntity` to an already-persisted application | tracked app gets mutated out-of-band; decision child correctly tracked |
| `ApplicationMakeDecisionTest` | 255 | `applicationRepository.save(applicationEntity)` — attaches a `DecisionEntity` to an already-persisted application | same pattern as above |

Additionally, `ApplicationMakeDecisionTest` line 436 uses `entityManager.createQuery(...)` for a **read** to
eagerly load lazy collections — this is harmless (no writes) but worth documenting.

The `repository/` integration tests (`ApplicationRepositoryTest`, `DomainEventRepositoryTest`) still extend
`BaseIntegrationTest` rather than `BaseHarnessTest`. They call `applicationRepository.saveAndFlush` and
`domainEventRepository.save` directly, and do not benefit from `PersistedDataGenerator` tracking.
These are out of scope for this plan but must not be forgotten.

---

## Problem Statement

Because some writes bypass `PersistedDataGenerator`, those rows are:

1. **Not tracked** — `deleteTrackedData()` never deletes them.
2. **Potentially leaked** — if a test method ends without explicitly cleaning up, the row remains in the DB and
   can contaminate a later test, especially if tests are reordered or executed in parallel.

The two `saveAndFlush` call-sites (`GetApplicationTest:94`, `ApplicationMakeDecisionTest:255`) mutate
applications that *are* already tracked by the generator, so those applications **will** be deleted at
teardown.  The mutation is not the leak; the risk is that between mutation and teardown the child rows
(decision, proceedings, merits decisions) accumulate and might FK-constrain deletion order, or get left
behind if teardown throws early.

`GetApplicationsTest:142` is a flush of an in-memory change to `submittedAt` on an already-tracked entity —
lower risk, but still bypasses the generator contract.

---

## Goals

1. Add an end-of-suite assertion that **all database tables are empty** after every test class completes.
2. Verify each controller integration test individually leaves no data behind.
3. Verify the full controller test pack (all test classes run together) leaves no data behind.
4. Migrate the three out-of-band writes into `PersistedDataGenerator` so that every controller test only
   persists data through the generator.

---

## Step 1 — Add a `DatabaseCleanlinessAssertion` utility

### 1a. Create `DatabaseCleanlinessAssertion`

Create a new class at
`data-access-service/src/integrationTest/java/uk/gov/justice/laa/dstew/access/utils/harness/DatabaseCleanlinessAssertion.java`.

```java
/**
 * Queries every application-domain table and asserts each one is empty.
 * Intended to be called from @AfterAll in test classes, and from a dedicated
 * end-of-suite marker test.
 */
@Component
public class DatabaseCleanlinessAssertion {

    private final JdbcTemplate jdbc;

    public DatabaseCleanlinessAssertion(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final List<String> TABLES = List.of(
        "domain_events",
        "merits_decisions",
        "decisions",
        "proceedings",
        "certificates",
        "linked_applications",
        "application_individuals",   // join table
        "individuals",
        "applications",
        "caseworkers"
    );

    /**
     * Asserts every table is empty.
     * @param context human-readable label appended to failure messages (e.g. the test class name)
     */
    public void assertAllTablesEmpty(String context) {
        List<String> violations = new ArrayList<>();
        for (String table : TABLES) {
            int count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
            if (count != null && count > 0) {
                violations.add(table + " has " + count + " row(s)");
            }
        }
        if (!violations.isEmpty()) {
            throw new AssertionError(
                "[" + context + "] Database is not clean after test teardown:\n  "
                + String.join("\n  ", violations)
            );
        }
    }
}
```

Register `DatabaseCleanlinessAssertion` as a Spring bean in `TestConfiguration.java`.

The table list must match the actual Flyway/Liquibase migration names; verify them against the migration
scripts before finalising.  The order (child → parent) avoids FK issues should the assertion ever need
to be extended to also delete.

### 1b. Wire into `BaseHarnessTest`

Add `@HarnessInject DatabaseCleanlinessAssertion dbCleanliness;` to `BaseHarnessTest`.

Add an `@AfterEach` method that runs **after** `tearDownTrackedData()` completes:

```java
@AfterEach
void assertDatabaseCleanAfterTest() {
    dbCleanliness.assertAllTablesEmpty(getClass().getSimpleName());
}
```

The ordering of `@AfterEach` methods in JUnit 5 is undefined by default; use
`@TestMethodOrder(MethodOrderer.OrderAnnotation.class)` on `BaseHarnessTest` or annotate the
cleanliness check with `@Order(Integer.MAX_VALUE)` to ensure it fires **after** `tearDownTrackedData`.

Alternatively, merge the assertion into the existing `tearDownTrackedData()` method as a final block after
the `finally` clause in `deleteTrackedData()`.

---

## Step 2 — Migrate out-of-band writes into `PersistedDataGenerator`

### 2a. `GetApplicationsTest` — `applicationRepository.saveAndFlush` (line 142)

**Current code (inside `createRangeOfSortableApplications`):**
```java
for (ApplicationEntity applicationEntity : applications) {
    applicationEntity.setSubmittedAt(referenceDate.plus(submittedDayCount++, ChronoUnit.DAYS));
    applicationRepository.saveAndFlush(applicationEntity);
}
```

**Problem:** the in-memory mutation of `submittedAt` is flushed directly via the repository.  The entity
is already tracked by the generator (the `@BeforeEach` seeding called `createAndPersistMultiple`), so the
application row *will* be cleaned up — but the pattern is inconsistent with the "all writes through the
generator" contract.

**Proposed solution:** Add an `updateAndFlush(TEntity entity)` method to `PersistedDataGenerator`.
No generator class is needed — the correct repository can be resolved from the entity type directly
(mirroring the existing `track()` dispatch which already switches on `instanceof`):

```java
public <TEntity> TEntity updateAndFlush(TEntity entity) {
    if (entity instanceof ApplicationEntity e) {
        applicationContext.getBean(ApplicationRepository.class).saveAndFlush(e);
    } else if (entity instanceof CaseworkerEntity e) {
        applicationContext.getBean(CaseworkerRepository.class).saveAndFlush(e);
    } else if (entity instanceof IndividualEntity e) {
        applicationContext.getBean(IndividualRepository.class).saveAndFlush(e);
    } else {
        throw new IllegalArgumentException("No repository mapped for entity type: "
            + entity.getClass().getName());
    }
    // entity is already tracked — no need to call track() again
    return entity;
}
```

Update `createRangeOfSortableApplications` to call:
```java
persistedDataGenerator.updateAndFlush(applicationEntity);
```

### 2b. `GetApplicationTest` — `applicationRepository.saveAndFlush` (line 94)

**Current code:**
```java
application.setDecision(decision);
applicationRepository.saveAndFlush(application);
```

**Problem:** The decision FK is set on an application that is already tracked.  The decision itself is also
tracked (it was persisted via `persistedDataGenerator.createAndPersist(DecisionEntityGenerator.class, ...)`).
The flush is safe from a cleanup perspective but bypasses the generator.

**Proposed solution:** Use the same `updateAndFlush` method described in §2a:
```java
application.setDecision(decision);
persistedDataGenerator.updateAndFlush(application);
```

### 2c. `ApplicationMakeDecisionTest` — `applicationRepository.save` (line 255)

**Current code:**
```java
applicationEntity.setDecision(decision);
applicationRepository.save(applicationEntity);
```

**Same pattern as §2b.** Replace with:
```java
applicationEntity.setDecision(decision);
persistedDataGenerator.updateAndFlush(applicationEntity);
```

### 2d. `ApplicationMakeDecisionTest` — `entityManager.createQuery` (line 436)

This is a **read-only JPQL query** used to eagerly load `meritsDecisions` and their `proceeding` associations.
No write occurs; no tracking issue exists.  Document with a comment that this is a read for assertion
purposes and leave it in place, **or** optionally add a `findByIdWithMeritsDecisions` method to
`DecisionRepository` to encapsulate the JPQL.  Defer to implementation preference.

---

## Step 3 — Verify each controller test class individually

For each test class listed below, run it in isolation against a clean Testcontainers database and confirm
the `DatabaseCleanlinessAssertion` passes (i.e. all tables are empty after teardown):

| Test class | Tables it touches | Known out-of-band writes (pre-fix) |
|---|---|---|
| `GetApplicationsTest` | applications, individuals, caseworkers, linked_applications | `applicationRepository.saveAndFlush` (line 142) — fixed in §2a |
| `GetApplicationTest` | applications, individuals, caseworkers, proceedings, decisions, merits_decisions | `applicationRepository.saveAndFlush` (line 94) — fixed in §2b |
| `ApplicationMakeDecisionTest` | applications, individuals, caseworkers, proceedings, decisions, merits_decisions, domain_events | `applicationRepository.save` (line 255) — fixed in §2c |
| `CreateApplicationTest` | applications, individuals, caseworkers, domain_events | none identified |
| `AssignCaseworkerTest` | applications, caseworkers, domain_events | none identified |
| `ReassignCaseworkerTest` | applications, caseworkers, domain_events | none identified |
| `UnassignCaseworkerTest` | applications, caseworkers, domain_events | none identified |
| `UpdateApplicationTest` | applications, individuals, caseworkers, domain_events | none identified |
| `GetDomainEventTest` | applications, caseworkers, domain_events | none identified |
| `GetCaseworkersTest` | caseworkers | none identified |
| `GetIndividualsTest` | applications, individuals | none identified |

**How to run a single test class in isolation:**
```
./gradlew :data-access-service:integrationTest \
    --tests "uk.gov.justice.laa.dstew.access.controller.application.GetApplicationsTest"
```

Confirm after each run:
- All tests pass.
- The `DatabaseCleanlinessAssertion` fires without error (no tables have residual rows).

---

## Step 4 — Add a dedicated "all-tables-empty after full suite" marker test

Create a new class `AllTablesEmptyAfterSuiteTest` in the `utils/harness` package.  This test is **ordered
last** using `@Order` or suite ordering so it fires after all other integration tests.  It calls
`dbCleanliness.assertAllTablesEmpty("full-controller-suite")`.

```java
@Tag("integration-suite-end")
public class AllTablesEmptyAfterSuiteTest extends BaseHarnessTest {

    @Test
    void givenAllControllerTestsHaveRun_thenDatabaseIsEmpty() {
        dbCleanliness.assertAllTablesEmpty("full-controller-suite");
    }
}
```

To guarantee it runs last, annotate with `@Order(Integer.MAX_VALUE)` and configure Gradle/Maven surefire
or JUnit Platform to honour test ordering at the class level.

Alternatively, use a JUnit 5 `TestExecutionListener` (`afterTestClass` callback) to assert cleanliness
after **every** class in the suite without needing a dedicated test.

---

## Step 5 — Verify the full controller test pack

Run all controller integration tests together:
```
./gradlew :data-access-service:integrationTest \
    --tests "uk.gov.justice.laa.dstew.access.controller.*"
```

Confirm:
- All individual test classes pass.
- `AllTablesEmptyAfterSuiteTest` (or the equivalent listener assertion) passes.
- No residual data reported by `DatabaseCleanlinessAssertion`.

---

## Acceptance Criteria

- [ ] `DatabaseCleanlinessAssertion` component exists and is wired into `BaseHarnessTest`.
- [ ] `PersistedDataGenerator` exposes `updateAndFlush(TEntity entity)` for in-place mutations — no
      generator class argument required; the correct repository is resolved from the entity type.
- [ ] `GetApplicationsTest:142`, `GetApplicationTest:94`, and `ApplicationMakeDecisionTest:255` all use the
      new `updateAndFlush` path — no direct `applicationRepository.save` calls remain in controller tests.
- [ ] Every controller test class passes in isolation with no residual-data assertion failure.
- [ ] The full controller test pack passes with no residual-data assertion failure.
- [ ] A follow-up ticket exists for migrating `ApplicationRepositoryTest` and `DomainEventRepositoryTest`
      to `BaseHarnessTest`.

---

## Risk & Notes

- **Table name accuracy:** the table list in `DatabaseCleanlinessAssertion` must exactly match the migration
  scripts.  Check the Flyway/Liquibase files under `data-access-service/src/main/resources/db/` before
  implementing.
- **`@BeforeEach` caseworker seeds:** `BaseHarnessTest.setupHarness()` always persists `CaseworkerJohnDoe`
  and `CaseworkerJaneDoe`.  The cleanliness assertion must run **after** `tearDownTrackedData()` deletes
  them.  Ensure `@AfterEach` ordering is correct (see §1b).
- **`linked_applications` table:** `PersistedDataGenerator.persistLink()` uses `entityManager.persist`
  directly because there is no `LinkedApplicationRepository`.  The rows are cascade-deleted when the lead
  application is removed.  The cleanliness assertion will catch any orphaned rows if cascade is ever broken.
- **`HarnessDataIsolationTest` sentinel data:** this test intentionally persists sentinels outside the
  generator and asserts they survive.  Its `@AfterAll` cleans them up.  The cleanliness assertion must
  not fire between tests within that class (only after `@AfterAll` runs).  Restrict the
  `DatabaseCleanlinessAssertion` call to `@AfterAll` in `HarnessDataIsolationTest`, or annotate it to
  skip the per-test check.

