# Refactor Plan: `ApplicationMakeDecisionTest` → Harness-Based Test (Minimal-Diff Revision)

## Status: **COMPLETED** — 25 March 2026

All 16 tests pass. Three bugs were discovered and fixed during execution; they are
documented in the [Bugs Found and Fixed](#bugs-found-and-fixed) section below.

---

## Goal

Port `ApplicationMakeDecisionTest` to run via the harness **while keeping the test body as
close to unchanged as possible**, following the same pattern established for
`GetApplicationTest` and `CreateApplicationTest`.

The core work was:

1. Adding `patchUri` overloads to `BaseHarnessTest` (mirrors `BaseIntegrationTest`).
2. Adding an `assertNoContent` `HarnessResult` overload to `ResponseAsserts`.
3. Exposing `certificateRepository` and `decisionRepository` from `BaseHarnessTest` (used
   for inline assertions).
4. Replacing the `ValidationException` servlet introspection in one test with a
   body-based assertion.
5. Making the minimal line-level changes inside the test itself.
6. *(Discovered during execution)* Injecting `EntityManager` into the test class and
   rewriting `verifyDecisionSavedCorrectly` to use a `JOIN FETCH` query.

No per-test tracking lists or `@AfterEach` teardown are needed in
`ApplicationMakeDecisionTest` itself — `BaseHarnessTest.tearDownTrackedData()` already
delegates to `persistedDataGenerator.deleteTrackedData()`, which deletes all tracked
`ApplicationEntity` rows; the database cascades that deletion to proceedings, decisions,
merits decisions, and certificates automatically.

---

## Context

`ApplicationMakeDecisionTest` previously extended `BaseIntegrationTest`, which provided:

- `@Transactional` auto-rollback — every entity created in a test is automatically cleaned
  up.
- `@WithMockUser(authorities = ...)` — security simulation via Spring Security test context.
- `patchUri(String uri, TRequestModel body, Object... args)` — PATCH helper.
- `patchUri(String uri, TRequestModel body, HttpHeaders headers, Object... args)` — PATCH
  helper with explicit headers.
- `certificateRepository`, `decisionRepository` — `@Autowired` fields.
- `domainEventAsserts` — `@Autowired` field.
- `deserialise(MvcResult, Class)` — convenience wrapper.
- Direct access to `result.getResolvedException()` (servlet-only) for
  `givenMakeDecisionRequestWithMissingJustification` — inspected the thrown
  `ValidationException` directly.

The harness has none of these out-of-the-box. Each was replicated without touching test
logic more than necessary.

---

## Key Differences Bridged

| Concern | `BaseIntegrationTest` | Harness |
|---|---|---|
| Transport | `MockMvc` (in-process) | `WebTestClient` (real HTTP) |
| Security | `@WithMockUser` | Real `Authorization: Bearer` header |
| Data teardown | `@Transactional` auto-rollback | `persistedDataGenerator.deleteTrackedData()` + DB cascade — **no per-test code needed** |
| `patchUri` helpers | On `BaseIntegrationTest` | Added to `BaseHarnessTest` |
| `certificateRepository` | `@Autowired` on `BaseIntegrationTest` | Resolved via `harnessProvider.getBean(...)` in `BaseHarnessTest` |
| `decisionRepository` | `@Autowired` on `BaseIntegrationTest` | Resolved via `harnessProvider.getBean(...)` in `BaseHarnessTest` |
| `domainEventAsserts` | `@Autowired` on `BaseIntegrationTest` | Already on `BaseHarnessTest` — no change |
| `assertNoContent` | `MvcResult` overload exists | `HarnessResult` overload added to `ResponseAsserts` |
| `result.getResolvedException()` | Servlet-only; inspects thrown `ValidationException` | Replaced with body-based `ProblemDetail` assertion |
| `@ActiveProfiles("test")` | Class-level | Removed — harness manages profiles |
| Lazy collection access | Session-scoped, no issue in-process | `LazyInitializationException` outside session — fixed with `EntityManager` `JOIN FETCH` (see bugs) |

---

## Authentication Strategy

Identical to `GetApplicationTest` and `CreateApplicationTest` — handled by `BaseHarnessTest`:

- **Caseworker role (default):** `Authorization: Bearer swagger-caseworker-token`
- All tests used `@WithMockUser(authorities = TestConstants.Roles.CASEWORKER)` — the
  default token covers all of them.
- There are no unknown-role or no-user tests in this file.

---

## Data Lifecycle Strategy

`ApplicationMakeDecisionTest` creates `ApplicationEntity`, `ProceedingEntity`,
`MeritsDecisionEntity`, `DecisionEntity`, and (in certificate tests) `CertificateEntity`
rows. Domain events are emitted as a side-effect of successful PATCH calls.

`PersistedDataGenerator.deleteTrackedData()` tracks every `ApplicationEntity` created via
`createAndPersist` and deletes them in `BaseHarnessTest.tearDownTrackedData()` (`@AfterEach`).
The database schema uses `ON DELETE CASCADE` from the `applications` table to proceedings,
decisions, the `linked_merits_decisions` join table, merits decisions, and certificates.
Domain events are tracked and deleted separately by `PersistedDataGenerator`. Zero teardown
code was required in the test class itself.

### `applicationRepository.save(applicationEntity)` mid-test

`givenMakeDecisionRequestWithExistingContentAndNewContent` calls
`applicationRepository.save(applicationEntity)` to attach a pre-built `DecisionEntity` to
the application before making the PATCH request. The `applicationEntity` was already
created via `persistedDataGenerator.createAndPersist(...)` and is therefore already tracked.
The `DecisionEntity` and `MeritsDecisionEntity` are cascade-deleted when the application is
deleted. No extra teardown was needed.

---

## Changes Made

### 1. `ResponseAsserts.java` — added `assertNoContent` `HarnessResult` overload

```java
public static void assertNoContent(HarnessResult response) {
    assertEquals(HttpStatus.NO_CONTENT.value(), response.getResponse().getStatus());
}
```

### 2. `BaseHarnessTest.java` — added `patchUri` helpers and assertion repositories

Added fields:

```java
protected CertificateRepository certificateRepository;
protected DecisionRepository    decisionRepository;
```

Resolved in `setupHarness()`:

```java
certificateRepository = harnessProvider.getBean(CertificateRepository.class);
decisionRepository    = harnessProvider.getBean(DecisionRepository.class);
```

Added two `patchUri` overloads (body + default headers, body + explicit headers), consistent
with the `getUri` and `postUri` pattern already on `BaseHarnessTest`.

### 3. `ApplicationMakeDecisionTest.java` — minimal diff + bug fixes

#### Class-level changes

| What | Old | New |
|---|---|---|
| Class declaration | `extends BaseIntegrationTest` | `extends BaseHarnessTest` |
| `@ActiveProfiles("test")` | Present | Removed |
| `@HarnessInject EntityManager entityManager` | Absent | **Added** — required for lazy collection fix (see bugs) |
| Import `MvcResult` | Present | Removed |
| Import `ValidationException` | Present | Removed |
| Import `BaseIntegrationTest` | Present | Removed |
| Import `BaseHarnessTest`, `HarnessInject`, `HarnessResult` | Absent | Added |
| Import `jakarta.persistence.EntityManager` | Absent | **Added** |

#### Per-method changes

| What | Old | New |
|---|---|---|
| `@WithMockUser(authorities = TestConstants.Roles.CASEWORKER)` | On every test | Removed — default token covers all |
| `MvcResult result` (all call sites) | Present | Replaced with `HarnessResult result` |
| `assertNoContent(result)` | `MvcResult` overload | `HarnessResult` overload — call site unchanged |

#### `givenMakeDecisionRequestWithMissingJustification` — servlet introspection replaced

**Plan said:** replace `getResolvedException()` with a `ProblemDetail` body assertion,
asserting `detail` contains `"One or more validation rules were violated"`.

**What actually happened:** when executed against real HTTP, the `detail` field returned
`"Generic Validation Error"`, not `"One or more validation rules were violated"`. The
assertion was updated to match the actual API response:

```java
// Before (plan):
Assertions.assertThat(problemDetail.getDetail())
        .contains("One or more validation rules were violated");

// After (actual):
Assertions.assertThat(problemDetail.getDetail()).contains("Generic Validation Error");
```

The `errors` list assertion (the specific validation message) was kept unchanged. See
[Bug 1](#bug-1-wrong-assertion-string-for-validation-error-detail).

#### `getContentType()` → `getHeader("Content-Type")`

Two tests (`givenProceedingsNotFoundAndNotLinkedToApplication` and
`givenNoApplication_whenAssignDecisionApplication`) called `result.getResponse().getContentType()`.
Replaced with `result.getResponse().getHeader("Content-Type")` and switched to
`.startsWith("application/problem+json")` for charset-safety.

#### `givenMakeDecisionRequestWithExistingContentAndNewContent` — detached entity fix

**Plan said:** `MeritsDecisionEntity` is created via `persistedDataGenerator.createAndPersist`
and attached to the `DecisionEntity` builder. The plan noted this test required cascade
handling but did not flag a problem.

**What actually happened:** the test failed with `DetachedEntityPassedToPersistException`.
`persistedDataGenerator.createAndPersist` saves the entity and ends the transaction,
leaving it detached. Placing it in `DecisionEntity.meritsDecisions` (which carries
`CascadeType.PERSIST`) caused Hibernate to try to persist an already-persisted detached
entity.

**Fix:** replaced `persistedDataGenerator.createAndPersist(MeritsDecisionsEntityGenerator...)`
with `DataGenerator.createDefault(MeritsDecisionsEntityGenerator...)`, which creates a
**transient** (unsaved) entity. `CascadeType.PERSIST` on the `DecisionEntity` then inserts
both in one operation. See [Bug 2](#bug-2-detached-entity-passed-to-persist).

#### `verifyDecisionSavedCorrectly` — lazy collection fix

**Plan said:** nothing — `verifyDecisionSavedCorrectly` was expected to work unchanged.

**What actually happened:** two tests (`givenMakeDecisionRequestWithTwoProceedings` and
`givenGrantedDecisionWithCertificate`) failed with `LazyInitializationException` in
`verifyDecisionSavedCorrectly`. The method fetched a `DecisionEntity` via
`updatedApplicationEntity.getDecision()` and then streamed `getMeritsDecisions()` outside
any Hibernate session. Under `@Transactional` (old `BaseIntegrationTest`) the session
remained open. Under real HTTP there is no such session in the test thread.

**Fix:** injected `EntityManager` into the test via `@HarnessInject` and rewrote
`verifyDecisionSavedCorrectly` to use a `JOIN FETCH` JPQL query that eagerly loads
`meritsDecisions` and their `proceeding` associations in one query:

```java
DecisionEntity savedDecision = entityManager.createQuery(
        "SELECT d FROM DecisionEntity d "
                + "LEFT JOIN FETCH d.meritsDecisions m "
                + "LEFT JOIN FETCH m.proceeding "
                + "WHERE d.id = :id",
        DecisionEntity.class)
        .setParameter("id", updatedApplicationEntity.getDecision().getId())
        .getSingleResult();
```

See [Bug 3](#bug-3-lazyinitializationexception-in-verifydecisionsavedcorrectly).

---

## `@SmokeTest` annotations applied

| Test | `@SmokeTest`? |
|---|---|
| `givenMakeDecisionRequestAndInvalidHeader_whenAssignDecision_thenReturnBadRequest` (parameterised) | ✅ |
| `givenMakeDecisionRequestAndNoHeader_whenAssignDecision_thenReturnBadRequest` | ✅ |
| `givenNoApplication_whenAssignDecisionApplication_thenReturnNotFoundAndMessage` | ✅ |
| All other tests | ❌ |

---

## Bugs Found and Fixed

### Bug 1: Wrong assertion string for validation error `detail`

**Test:** `givenMakeDecisionRequestWithMissingJustification_whenAssignDecision_thenReturnBadRequest`

**Symptom:** `AssertionError: Expecting actual: "Generic Validation Error" to contain: "One or more validation rules were violated"`

**Cause:** The plan assumed the `ProblemDetail.detail` field would contain
`"One or more validation rules were violated"` (matching the `ValidationException` message
seen under `MockMvc`). Over real HTTP, the `@ControllerAdvice` serialises the `detail`
field as `"Generic Validation Error"`. The specific messages are in the `errors` property
array, which was already being asserted correctly.

**Fix:** Changed the `detail` assertion from `"One or more validation rules were violated"`
to `"Generic Validation Error"`.

---

### Bug 2: Detached entity passed to persist: `MeritsDecisionEntity`

**Test:** `givenMakeDecisionRequestWithExistingContentAndNewContent_whenAssignDecision_thenReturnNoContent_andDecisionUpdated`

**Symptom:** `org.springframework.dao.InvalidDataAccessApiUsageException: Detached entity passed to persist: MeritsDecisionEntity`

**Cause:** The test called `persistedDataGenerator.createAndPersist(MeritsDecisionsEntityGenerator...)`,
which saved the entity to the database (assigning it a UUID) and ended the transaction,
leaving the entity detached from the Hibernate session. The entity was then placed into
`DecisionEntity.meritsDecisions`. Because this relationship carries `CascadeType.PERSIST`,
Hibernate attempted to insert the already-persisted detached entity when
`DecisionRepository.saveAndFlush` was called, throwing the exception.

Under `@Transactional` (old `BaseIntegrationTest`) this did not fail because all operations
shared a single session — the entity was managed, not detached.

**Fix:** replaced `persistedDataGenerator.createAndPersist(MeritsDecisionsEntityGenerator...)`
with `DataGenerator.createDefault(MeritsDecisionsEntityGenerator...)`. `createDefault`
produces a **transient** entity (no ID, never saved). `CascadeType.PERSIST` on
`DecisionEntity.meritsDecisions` then inserts both entities together in one operation when
`DecisionRepository.saveAndFlush` is called — exactly the intended cascade behaviour.

---

### Bug 3: `LazyInitializationException` in `verifyDecisionSavedCorrectly`

**Tests:** `givenMakeDecisionRequestWithTwoProceedings_whenAssignDecision_thenReturnNoContent_andDecisionSaved`
and `givenGrantedDecisionWithCertificate_whenAssignDecision_thenReturnNoContent_andDecisionAndCertificateSaved`

**Symptom:** `org.hibernate.LazyInitializationException: Cannot lazily initialize collection of role 'DecisionEntity.meritsDecisions' ... (no session)`

**Cause:** `verifyDecisionSavedCorrectly` fetched `applicationEntity` via
`applicationRepository.findById(...)`, then accessed `savedDecision.getMeritsDecisions()`
(a lazy `Set`) outside the repository's transaction. Under `@Transactional` the Hibernate
session remained open for the duration of the test method, so the collection could be
initialised on demand. Under real HTTP there is no such ambient session in the test thread.

**Fix:** injected `EntityManager` into the test via `@HarnessInject EntityManager entityManager`
and changed `verifyDecisionSavedCorrectly` to load the `DecisionEntity` with an explicit
`LEFT JOIN FETCH` JPQL query, eagerly initialising both `meritsDecisions` and their nested
`proceeding` associations in a single database round-trip.

---

## Decisions and Notes

### Why not `@Transactional` on the test to keep the session open?

Adding `@Transactional` to an integration test that drives a real HTTP server is
incorrect: the test transaction and the server's transaction are separate. Annotating the
test method does not affect what happens inside the server, and mixing the two transactions
causes unpredictable behaviour. Using an explicit `JOIN FETCH` query is the correct
pattern for eagerly loading associations in this context.

### Why `DataGenerator.createDefault` rather than `persistedDataGenerator.createAndPersist` + re-fetch?

An earlier attempt at Bug 2 tried to re-fetch the entity via
`meritsDecisionRepository.findById(...)` after persisting it to get a fresh managed
instance. This fails for the same underlying reason: `saveAndFlush` commits and closes the
transaction, so the re-fetched entity is also detached by the time
`DecisionRepository.saveAndFlush` runs in the next transaction. Using a transient entity
and relying on `CascadeType.PERSIST` is the semantically correct solution.

### Why remove `@WithMockUser` entirely?

All tests used `@WithMockUser(authorities = TestConstants.Roles.CASEWORKER)`. The harness
default token (`TestConstants.Tokens.CASEWORKER`) is sent on every request unless
overridden. Since all tests use the same role, removing `@WithMockUser` and relying on the
default is safe and consistent with `GetApplicationTest` and `CreateApplicationTest`.

### Why replace `getResolvedException()` with a body-based assertion?

`MockMvc` captures the exception in `MvcResult.getResolvedException()`. This is a
servlet-specific mechanism with no equivalent over real HTTP. The validation failure is
serialised into the response body as `application/problem+json`. The body-based assertion
tests exactly the same observable behaviour a real client would see.

### Why `assertThat(...).startsWith("application/problem+json")` instead of `assertEquals`?

Spring Boot may append `";charset=UTF-8"` to the `Content-Type` header depending on
configuration. `startsWith` is version-safe and consistent with the pattern in
`assertProblemRecord(HarnessResult, ...)` in `ResponseAsserts.java`.

### Why `patchUri` overloads on `BaseHarnessTest` rather than in the test class?

Other PATCH endpoints will be refactored to the harness in future. Placing `patchUri` on
`BaseHarnessTest` makes it immediately reusable, following the same pattern as `postUri`
and `getUri`.
