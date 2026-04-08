# Plan 04 — Ensuring Test Data Is Removed

## Context

The test suite contains two distinct base classes:

| Base class | Location | Isolation mechanism |
|---|---|---|
| `BaseIntegrationTest` | `src/integrationTest/.../utils/BaseIntegrationTest.java` | `@Transactional` on the class – Spring rolls back the transaction after every test method automatically |
| `BaseHarnessTest` | `src/integrationTest/.../utils/harness/BaseHarnessTest.java` | Manual `@AfterEach` teardown – no transaction wrapping; data is committed by the application's own service layer |

`@Transactional` was the original strategy. It works well for `MockMvc`-based tests (the same thread executes the test body and the HTTP dispatch), so the transactional context is shared and the rollback discards everything. However, it **cannot** be used on `BaseHarnessTest` because those tests drive a real embedded server via `WebTestClient`; the HTTP request executes in a separate thread, commits its own transactions, and is therefore outside the test-method's transaction boundary. That is why `BaseHarnessTest` switched to explicit teardown.

---

## Current teardown approach (what exists today)

### `BaseHarnessTest`

```java
@AfterEach
void tearDownCaseworkers() {
    if (caseworkerRepository != null) {
        caseworkerRepository.deleteAll(Caseworkers);
    }
}
```

- Deletes **only** `CaseworkerJohnDoe` and `CaseworkerJaneDoe` from the shared `Caseworkers` list that was created in `@BeforeEach`.
- No other data is deleted here.

### Tests that extend `BaseHarnessTest`

#### `GetCaseworkersTest`
No `@AfterEach`. Relies solely on the base-class teardown. Only the two pre-seeded caseworkers are created, so this is currently **safe** (no orphan data).

#### `GetApplicationTest`
```java
private final List<ApplicationEntity> createdApplications = new ArrayList<>();

@AfterEach
void tearDownApplicationData() {
    applicationRepository.deleteAll(createdApplications);
    createdApplications.clear();
}
```
Tracks applications added to `createdApplications` and deletes them. Relies on DB `ON DELETE CASCADE` for related `proceedings`, `decisions`, `merits_decisions`, and `linked_individuals`.

#### `CreateApplicationTest`
```java
private final List<ApplicationEntity> createdApplications = new ArrayList<>();

@AfterEach
void tearDownApplicationData() {
    domainEventRepository.deleteAll(domainEventRepository.findAll().stream()
        .filter(e -> createdApplications.stream()
            .anyMatch(a -> a.getId().equals(e.getApplicationId())))
        .toList());
    applicationRepository.deleteAll(createdApplications);
    createdApplications.clear();
}
```
Manually removes domain events keyed to the tracked applications, then removes the applications.

### Tests that extend `BaseIntegrationTest` (transaction-based)

`@Transactional` on `BaseIntegrationTest` wraps each test in a transaction that is rolled back automatically. In principle this is sufficient, but see gaps below.

---

## Identified gaps

### Gap 1 — `BaseHarnessTest`: caseworker teardown swallows failures silently

The `@AfterEach` only deletes the two pre-seeded caseworkers by **reference** to the original `Caseworkers` list. If `@BeforeEach` fails part-way through (e.g. `CaseworkerJohnDoe` persisted but `CaseworkerJaneDoe` did not), the teardown will still attempt to delete both references, but depending on the failure mode the `Caseworkers` field may be `null` or only partially initialised, leaving orphan rows.

### Gap 2 — `GetApplicationTest`: `createdApplications` tracking is manual and fragile

Applications are only cleaned up if the test remembers to call `createdApplications.add(application)` immediately after persisting. Several test methods persist applications and do add them, but **if a test throws before the `add` call** (e.g. an assertion failure or an exception in the `persistedDataGenerator` call) the reference is never recorded and the row is left in the database.

Concrete examples:

- `givenExistingApplication_whenGetApplication_thenReturnOKWithCorrectData` — persists an `ApplicationEntity`, a `ProceedingEntity`, and a `DecisionEntity`. The proceeding and decision are **not** added to `createdApplications`; deletion relies entirely on cascade. If the cascade is missing or the FK relationship is not set up, those rows would be left behind.
- `givenUnknownRole_whenGetApplication_thenReturnForbidden` — persists an application and adds it, but the add is done *after* the persist. If `persistedDataGenerator.createAndPersist` throws, nothing is tracked.

### Gap 3 — `CreateApplicationTest`: domain-event cleanup is coupled to `createdApplications`

```java
domainEventRepository.deleteAll(domainEventRepository.findAll().stream()
    .filter(e -> createdApplications.stream()
        .anyMatch(a -> a.getId().equals(e.getApplicationId())))
    .toList());
```

This only cleans up domain events linked to applications that made it into `createdApplications`. Domain events created for applications that failed to be tracked (same scenario as Gap 2) are **not cleaned up**.

Additionally, `verifyCreateNewApplication` adds the application to `createdApplications` only after it has been retrieved from the repository. If the `findById` or any assertion throws, the application is persisted but never tracked, so it is never deleted.

### Gap 4 — `CreateApplicationTest`: `verifyCreateNewApplicationWithServiceName` does not add to `createdApplications`

```java
private void verifyCreateNewApplicationWithServiceName(ServiceName serviceName) throws Exception {
    ...
    UUID createdApplicationId = HeaderUtils.GetUUIDFromLocation(...);
    ApplicationEntity createdApplication = applicationRepository.findById(createdApplicationId).orElseThrow();
    createdApplications.add(createdApplication);   // <-- only reached if findById succeeds
    ...
}
```
If `findById` throws (e.g. the entity does not exist due to a bug), the application is created by the API but never tracked, so the domain event and application rows are left behind.

### Gap 5 — `ApplicationMakeDecisionTest` (extends `BaseIntegrationTest`): certificate cleanup relies on `@Transactional` rollback

`ApplicationMakeDecisionTest` checks `certificateRepository.findAll()` for size and verifies a specific certificate was persisted. Because `@Transactional` rolls back after each test, this works in normal conditions. However, if another `@Transactional` test (or any test that circumvents the transaction) leaves a certificate row behind, the assertion `certificates.size() == 0` or `== 1` would become flaky.

More critically, `@Transactional` **on tests** does not protect against data written by **asynchronous** or **background** threads (e.g. scheduled jobs, async service calls). If any part of the application code commits data outside the test transaction, that data will survive the rollback.

### Gap 6 — `GetApplicationsTest` and `AssignCaseworkerTest` (extends `BaseIntegrationTest`): implicit dependency on clean slate

These tests make assertions about total counts (e.g. `assertPaging(actual, 9, …)`, `assertThat(actual.getApplications().size()).isEqualTo(9)`). They rely on the database being empty of applications at the start of each test. `@Transactional` normally achieves this, but:

- Tests are `@TestInstance(PER_CLASS)` in `GetApplicationsTest` and `AssignCaseworkerTest`. With `PER_CLASS`, JUnit reuses the same test instance. Spring's `@Transactional` test support still issues a rollback per method, so this is **safe** in isolation. However, the `@BeforeEach setupCaseworkers` in `BaseIntegrationTest` calls `caseworkerRepository.deleteAll()` — a bulk delete that runs *inside* the test transaction. If anything committed data outside of that transaction (another thread, another test), the `deleteAll()` call would delete it but leave behind anything created in the current test body before that call.

- `GetApplicationsTest` persists many entities (up to 40 in one test) and then performs count assertions. Any leak from prior tests would break these assertions.

### Gap 7 — `BaseIntegrationTest`: `caseworkerRepository.deleteAll()` in `@BeforeEach` is a blunt instrument

`@BeforeEach` in `BaseIntegrationTest` calls `caseworkerRepository.deleteAll()`. This works correctly when `@Transactional` is in use (since the previous test's data was rolled back), but it assumes the caseworker table is clean. If a test from a *different* class ran in the same Spring context without `@Transactional` protection and left caseworker rows, the `deleteAll()` call would remove them, which is the *desired* clean-up, but the dependent `ApplicationEntity` rows (with a FK to caseworker) would be left if cascades are not set up.

### Gap 8 — `GetIndividualsTest` (extends `BaseIntegrationTest`): individual data not cleaned up explicitly

Tests persist `IndividualEntity` rows and assert on `totalRecords`. These rely on `@Transactional` rollback. If rollback fails for any reason (e.g. the test itself spawns a thread or makes a real HTTP call outside the mock), individual rows will bleed into subsequent tests that assert on counts.

### Gap 9 — Tests in `BaseHarnessTest` hierarchy: no teardown for `ProceedingEntity`, `DecisionEntity`, `MeritsDecisionEntity`, `IndividualEntity`, `CertificateEntity`, or `DomainEventEntity` except via cascade

The `GetApplicationTest` and `CreateApplicationTest` teardowns delete applications and rely on cascades. If the cascade is ever changed or a new entity type is added that is not in the cascade chain, those rows will be permanently left in the database.

---

## Critical constraint: infrastructure mode runs against real infrastructure

`BaseHarnessTest` is shared by **two** execution modes, selected at runtime by the `test.mode` system property:

| Mode | `TestContextProvider` implementation | Database |
|---|---|---|
| `integration` (default) | `IntegrationTestContextProvider` | Testcontainers Postgres — ephemeral, owned by the test run |
| `infrastructure` | `InfrastructureTestContextProvider` | Real environment DB pointed to by `LAA_ACCESS_DB_URL` — **may be production** |

Only tests annotated `@SmokeTest` execute in infrastructure mode (`HarnessExtension` skips all others). Because the infrastructure DB may contain real application data, **any bulk `deleteAll()` call in `BaseHarnessTest` would be catastrophic**. The teardown strategy must therefore be:

- **Integration mode** — safe to bulk-delete (ephemeral DB), but only as a secondary defence.
- **Infrastructure mode** — must only delete the specific rows that the test itself created. Existing data must be left completely untouched.

This constraint invalidates the "Step 1 – bulk deleteAll" approach in the original draft. The correct solution is a **tracked teardown** that is rigorous enough to survive test failures and operates identically in both modes — the same single mechanism used in both integration and infrastructure mode.

---

## Recommended plan

### Step 1 — Introduce ID tracking fields in `BaseHarnessTest`

Rather than maintaining ad-hoc `createdApplications` lists in each subclass, centralise tracking inside the base class. Add general-purpose ID lists and protected helper methods that any subclass (or the base itself) calls immediately after each persist:

```java
// Inside BaseHarnessTest
private final List<UUID> trackedCaseworkerIds  = new ArrayList<>();
private final List<UUID> trackedApplicationIds = new ArrayList<>();
private final List<UUID> trackedDomainEventIds = new ArrayList<>();

protected void trackCaseworker(CaseworkerEntity e)  { trackedCaseworkerIds.add(e.getId()); }
protected void trackApplication(ApplicationEntity e) { trackedApplicationIds.add(e.getId()); }
protected void trackDomainEvent(DomainEventEntity e) { trackedDomainEventIds.add(e.getId()); }
```

These methods must be called on the very next line after a persist returns, before any further logic that could throw. If the persist itself throws, the entity was never written — nothing to track.

### Step 2 — Replace `@AfterEach tearDownCaseworkers` with a tracked teardown in `BaseHarnessTest`

Replace the existing `@AfterEach` with a comprehensive tracked teardown that deletes each entity by its recorded ID in dependency order (leaf tables first). Using `findById(...).ifPresent(...)` means the teardown is safe even if the test's own code already deleted the row:

```java
@AfterEach
void tearDownTrackedData() {
    if (domainEventRepository != null) {
        trackedDomainEventIds.forEach(id ->
            domainEventRepository.findById(id).ifPresent(domainEventRepository::delete));
    }
    if (applicationRepository != null) {
        // ON DELETE CASCADE handles proceedings, decisions, merits_decisions, certificates
        trackedApplicationIds.forEach(id ->
            applicationRepository.findById(id).ifPresent(applicationRepository::delete));
    }
    if (caseworkerRepository != null) {
        trackedCaseworkerIds.forEach(id ->
            caseworkerRepository.findById(id).ifPresent(caseworkerRepository::delete));
    }
    trackedDomainEventIds.clear();
    trackedApplicationIds.clear();
    trackedCaseworkerIds.clear();
}
```

This is the **only** delete mechanism, used identically in both integration and infrastructure mode. It is purely surgical — it only removes rows whose IDs were explicitly registered by the test.

### Step 3 — Track the base-class caseworkers immediately after persisting them

In `setupHarness()`, call `trackCaseworker(...)` on the very next line after each persist:

```java
CaseworkerJohnDoe = persistedDataGenerator.createAndPersist(
        CaseworkerGenerator.class, b -> b.username("JohnDoe").build());
trackCaseworker(CaseworkerJohnDoe);   // registered before anything else can throw

CaseworkerJaneDoe = persistedDataGenerator.createAndPersist(
        CaseworkerGenerator.class, b -> b.username("JaneDoe").build());
trackCaseworker(CaseworkerJaneDoe);
```

If `@BeforeEach` throws after the first `createAndPersist` but before the second, the first caseworker is already tracked and `@AfterEach` will clean it up. This closes **Gap 1**.

### Step 4 — Replace per-test `createdApplications` lists in `GetApplicationTest` and `CreateApplicationTest`

Remove the `private final List<ApplicationEntity> createdApplications` field and its `@AfterEach` from both classes. Replace every `createdApplications.add(application)` call with `trackApplication(application)` from the base class:

```java
// Before (fragile — lost if any earlier line throws):
ApplicationEntity app = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class, ...);
createdApplications.add(app);

// After (safe — called immediately after persist returns):
ApplicationEntity app = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class, ...);
trackApplication(app);
```

For `verifyCreateNewApplication` and `verifyCreateNewApplicationWithServiceName`, where the application ID is extracted from the HTTP response `Location` header:

```java
UUID createdId = HeaderUtils.GetUUIDFromLocation(result.getResponse().getHeader("Location"));
applicationRepository.findById(createdId).ifPresent(app -> trackApplication(app));
```

Domain events written by the service layer in response to HTTP calls are **not** directly persisted by the test, so they do not need to be tracked individually — they will be removed automatically when their parent application is deleted (via DB cascade or an explicit domain-event delete in teardown).

For tests in `CreateApplicationTest` that explicitly assert on domain events before teardown, and for `GetDomainEventTest`, any `DomainEventEntity` directly persisted via `persistedDataGenerator` must call `trackDomainEvent(entity)`.

### Step 5 — For `BaseIntegrationTest` tests: add an explicit `@BeforeEach` `deleteAll` as a safety net

`@Transactional` provides rollback, but as a defensive measure add an explicit clear of application data in `BaseIntegrationTest`'s `@BeforeEach`. This guards against any future test that inadvertently commits data outside the transaction boundary. `BaseIntegrationTest` always uses Testcontainers, so bulk deletes are safe:

```java
@BeforeEach
void setupCaseworkers() {
    // existing:
    caseworkerRepository.deleteAll();
    // new safety net:
    applicationRepository.deleteAll();
    // ... rest of existing setup
}
```

### Step 6 — Validate cascade configuration

Audit the database schema (Flyway migrations) and JPA entity mappings to confirm that deleting an `ApplicationEntity` cascades correctly to:

- `proceedings`
- `decisions` → `merits_decisions`
- `linked_individuals` (join table)
- `certificates`

This ensures that `applicationRepository.delete(app)` in the tracked teardown (Step 2) leaves no orphaned rows. If any cascade is missing, add explicit `findById` + delete calls to the teardown for those entity types, in the correct FK order.

---

## Tests that prove the harness does not delete existing data

These tests are placed in a new class, `HarnessDataIsolationTest`, which extends `BaseHarnessTest`. Every test method is annotated `@SmokeTest` so the tests execute in **both** integration mode (Testcontainers) and infrastructure mode (real DB). This means the safety guarantee is verified against real infrastructure, not just the ephemeral test database.

The class uses `@TestInstance(Lifecycle.PER_CLASS)` so that `@BeforeAll` / `@AfterAll` can manage sentinel entities that exist outside the normal test lifecycle (i.e., entities that must not be deleted by the harness teardown).

### New test class: `HarnessDataIsolationTest`

```
src/integrationTest/.../utils/harness/HarnessDataIsolationTest.java
```

#### Sentinel setup / teardown

```
@BeforeAll — persist a CaseworkerEntity (sentinelCaseworker), an ApplicationEntity
             (sentinelApplication), and a DomainEventEntity (sentinelDomainEvent)
             directly via the repositories obtained from harnessProvider.
             Do NOT call trackCaseworker/trackApplication/trackDomainEvent for these —
             they must remain invisible to the harness teardown.

@AfterAll  — delete the three sentinel rows by ID directly via the repositories.
             This is the only place they are removed.
```

#### Test 1 — Pre-existing caseworker is not deleted by harness teardown

```
@SmokeTest
Given sentinelCaseworker exists (created in @BeforeAll, not tracked)
And the @BeforeEach setupHarness() creates CaseworkerJohnDoe and CaseworkerJaneDoe (both tracked)
When the test body completes and @AfterEach tearDownTrackedData() runs
Then caseworkerRepository.findById(sentinelCaseworker.getId()) is present
And caseworkerRepository.findById(CaseworkerJohnDoe.getId()) is empty
And caseworkerRepository.findById(CaseworkerJaneDoe.getId()) is empty
```

This proves tracked teardown removes only the harness-seeded caseworkers and leaves the pre-existing sentinel alone.

#### Test 2 — Pre-existing application is not deleted by harness teardown

```
@SmokeTest
Given sentinelApplication exists (created in @BeforeAll, not tracked)
And the test body creates a second application via persistedDataGenerator and calls trackApplication(...)
When @AfterEach tearDownTrackedData() runs
Then applicationRepository.findById(sentinelApplication.getId()) is present
And applicationRepository.findById(testCreatedApplication.getId()) is empty
```

This is the core invariant: the tracked teardown deletes only what the test created.

#### Test 3 — Pre-existing domain event is not deleted by harness teardown

```
@SmokeTest
Given sentinelDomainEvent exists (created in @BeforeAll, not tracked)
And the test body creates a domain event via persistedDataGenerator and calls trackDomainEvent(...)
When @AfterEach tearDownTrackedData() runs
Then domainEventRepository.findById(sentinelDomainEvent.getId()) is present
And domainEventRepository.findById(testCreatedDomainEvent.getId()) is empty
```

#### Test 4 — Tracked teardown removes only tracked entities, not untracked ones

```
@SmokeTest
Given entityA is persisted directly via the repository (not via persistedDataGenerator,
      no trackXxx() call) — this simulates pre-existing data
And entityB is persisted via persistedDataGenerator and trackApplication() is called
When the test body ends and @AfterEach tearDownTrackedData() runs
Then applicationRepository.findById(entityA.getId()) is present
Then applicationRepository.findById(entityB.getId()) is empty
```

Cleanup: entityA must be explicitly deleted in a local `@AfterEach` or in `@AfterAll`.

#### Test 5 — Partial `@BeforeEach` failure does not leave orphaned rows

```
Given the harness setupHarness() persists CaseworkerJohnDoe and tracks it
And an artificial exception is thrown before CaseworkerJaneDoe is persisted
When @AfterEach tearDownTrackedData() runs despite the @BeforeEach failure
Then CaseworkerJohnDoe has been removed (it was tracked before the failure)
```

This test can be implemented as a standalone JUnit `@Test` that manually invokes the harness lifecycle methods rather than relying on JUnit's own `@BeforeEach` / `@AfterEach` sequencing, allowing the result to be inspected after the simulated failure.

---

## Summary of gaps and resolutions

| Gap | Severity | Affected tests | Resolution |
|---|---|---|---|
| 1 – Partial `@BeforeEach` failure leaves caseworker rows | Medium | All `BaseHarnessTest` subclasses | Step 3: track immediately after each persist |
| 2 – `createdApplications` tracking misses entities if test throws before `add` | High | `GetApplicationTest`, `CreateApplicationTest` | Step 4: base-class `trackApplication` |
| 3 – Domain events not cleaned when application not tracked | Medium | `CreateApplicationTest` | Step 4: base-class `trackDomainEvent` |
| 4 – `verifyCreateNewApplicationWithServiceName` never tracks application | Medium | `CreateApplicationTest` | Step 4: `ifPresent(app -> trackApplication(app))` |
| 5 – Certificate isolation depends purely on `@Transactional` rollback | Low | `ApplicationMakeDecisionTest` | Step 5 |
| 6 – Count assertions flaky if data leaks between tests | High | `GetApplicationsTest`, `AssignCaseworkerTest` | Step 5 |
| 7 – `caseworkerRepository.deleteAll()` does not handle cascades to applications | Low | `BaseIntegrationTest` subclasses | Step 6 + Step 5 |
| 8 – Individual rows rely solely on `@Transactional` | Low | `GetIndividualsTest` | Step 5 |
| 9 – Non-cascade entities left if cascade config changes | Medium | `GetApplicationTest`, `CreateApplicationTest` | Step 6 |
