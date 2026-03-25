# Plan 06 — Port Remaining Controller Tests to `BaseHarnessTest`

## Status: **PENDING**

---

## Scope

Six controller test files still extend `BaseIntegrationTest`. This plan covers migrating
all of them. Repository tests are explicitly excluded and stay on `BaseIntegrationTest`.

| File | HTTP method(s) | Role variants |
|---|---|---|
| `GetApplicationsTest` | GET | CASEWORKER, UNKNOWN, no-user |
| `AssignCaseworkerTest` | POST | CASEWORKER, UNKNOWN, no-user |
| `ReassignCaseworkerTest` | POST | CASEWORKER |
| `UnassignCaseworkerTest` | POST | CASEWORKER, UNKNOWN, no-user |
| `UpdateApplicationTest` | PATCH | CASEWORKER, UNKNOWN, no-user |
| `GetDomainEventTest` | GET | CASEWORKER, UNKNOWN, no-user |
| `GetIndividualsTest` | GET | CASEWORKER, UNKNOWN, no-user, `@WithMockUser` (no authorities) |

---

## What Plans 04 and 05 Established

### From Plan 04 — the tracking teardown pattern

`PersistedDataGenerator.deleteTrackedData()` is the single teardown mechanism for all
harness tests. It automatically tracks every entity created via `createAndPersist` /
`createAndPersistMultiple`, deletes them in the correct order in `@AfterEach`, and is safe
for both integration mode (ephemeral DB) and infrastructure mode (real DB — must never
bulk-delete). No per-test `@AfterEach` is needed in any test class. Plan 04 also
established that `BaseHarnessTest.setupHarness()` calls
`persistedDataGenerator.clearTrackedIds()` as a belt-and-braces guard at the start of
every test.

### From Plan 05 — lessons learned migrating `ApplicationMakeDecisionTest`

Three bugs emerged that will likely recur in the remaining tests:

1. **`@WithMockUser` → token helpers.** `BaseHarnessTest` uses real HTTP. Replace
   `@WithMockUser(authorities = Roles.CASEWORKER)` with nothing (default token is
   caseworker). Replace `@WithMockUser(authorities = Roles.UNKNOWN)` with
   `withToken(TestConstants.Tokens.UNKNOWN)` at the start of the test body. Replace bare
   `@WithMockUser` (no authorities, `givenUserWithNoAuthorities` tests in
   `GetIndividualsTest`) with `withToken(TestConstants.Tokens.UNKNOWN)` — the unknown
   token maps to a user with no recognised role, producing 403.  Replace tests with no
   `@WithMockUser` annotation (no-user / unauthorised tests) with `withNoToken()` at the
   start of the test body.

2. **`LazyInitializationException` on lazy collections.** The `@Transactional` session no
   longer wraps the test thread. Any test that reads a lazy association after the
   repository call must use `EntityManager` with a `JOIN FETCH` query (inject via
   `@HarnessInject`) or re-fetch from a repository that uses `JOIN FETCH`. This
   requirement is flagged per-file below.

3. **Detached entities in cascade chains.** Any entity persisted via `persistedDataGenerator`
   and then placed into a cascade-`PERSIST` relationship must be created as a transient
   entity via `DataGenerator.createDefault` instead. This does not apply to the tests
   covered here (none set up pre-persisted entities inside cascade relationships), but the
   pattern is documented here so it is not forgotten.

---

## What `BaseHarnessTest` Already Provides

These are **not** new additions — they exist today and every test can use them
immediately upon switching `extends BaseIntegrationTest` → `extends BaseHarnessTest`:

| Capability | How it is exposed |
|---|---|
| `getUri(String)` | `BaseHarnessTest` |
| `getUri(String, HttpHeaders)` | `BaseHarnessTest` |
| `getUri(String, Object...)` | `BaseHarnessTest` |
| `getUri(String, HttpHeaders, Object...)` | `BaseHarnessTest` |
| `postUri(String, T)` | `BaseHarnessTest` |
| `postUri(String, T, HttpHeaders)` | `BaseHarnessTest` |
| `patchUri(String, T, Object...)` | `BaseHarnessTest` |
| `patchUri(String, T, HttpHeaders, Object...)` | `BaseHarnessTest` |
| `deserialise(HarnessResult, Class<T>)` | `BaseHarnessTest` |
| `ServiceNameHeader(String)` | `BaseHarnessTest` |
| `withToken(String)` / `withNoToken()` | `BaseHarnessTest` |
| `persistedDataGenerator` | `BaseHarnessTest` |
| `applicationRepository`, `caseworkerRepository`, `domainEventRepository`, `certificateRepository`, `decisionRepository` | `BaseHarnessTest` |
| `applicationAsserts`, `domainEventAsserts` | `BaseHarnessTest` |
| `CaseworkerJohnDoe`, `CaseworkerJaneDoe`, `Caseworkers` | `BaseHarnessTest` |
| All `ResponseAsserts` overloads (`assertOK`, `assertForbidden`, `assertUnauthorised`, `assertNotFound`, `assertBadRequest`, `assertNoContent`, `assertCreated`, `assertSecurityHeaders`, `assertNoCacheHeaders`, `assertContentHeaders`, `assertProblemRecord`) | `ResponseAsserts` — `HarnessResult` overloads already exist |

### What is missing and must be added

| Gap | Where to add | Notes |
|---|---|---|
| `postUri(String, T, Object...)` — POST with body and path-variable args | `BaseHarnessTest` | `UnassignCaseworkerTest` calls `postUri(uri, body, id)` where `id` is a UUID path variable. Pattern matches `BaseIntegrationTest.postUri(String, T, Object...)`. |
| `postUri(String, T, HttpHeaders, Object...)` — POST with body, headers, and path-variable args | `BaseHarnessTest` | `UnassignCaseworkerTest.verifyBadServiceNameHeader` calls `postUri(uri, body, ServiceNameHeader(name), id)`. |
| `getUri(URI uri)` / `getUri(URI uri, HttpHeaders)` — URI-object overloads | `BaseHarnessTest` | `GetApplicationsTest` constructs `URI` objects for paginated searches. Simplest implementation: delegate to the `String` overloads via `uri.toString()`. |
| `IndividualRepository` field | `BaseHarnessTest` | `GetIndividualsTest` has no explicit assert on the repository, but `PersistedDataGenerator` is already registered with `IndividualRepository` so teardown works automatically. No field needed on `BaseHarnessTest` unless an assertion requires it (none do). |

---

## Infrastructure Changes Required

### 1. `BaseHarnessTest.java` — add missing `postUri` overloads

```java
/** POST with body serialised to JSON, default CIVIL_APPLY header, and path-variable args. */
public <T> HarnessResult postUri(String uri, T requestModel, Object... args) throws Exception {
    return postUri(uri, requestModel, defaultServiceNameHeader(), args);
}

/** POST with body serialised to JSON, the supplied headers, and path-variable args. */
public <T> HarnessResult postUri(String uri, T requestModel, HttpHeaders headers, Object... args) throws Exception {
    String expandedUri = UriComponentsBuilder.fromUriString(uri)
            .buildAndExpand(args)
            .toUriString();
    return postUri(expandedUri, requestModel, headers);
}
```

These mirror the `patchUri` overloads already added in Plan 05.

### 2. `BaseHarnessTest.java` — add `getUri(URI, ...)` overloads

```java
public HarnessResult getUri(URI uri) {
    return getUri(uri.toString(), defaultServiceNameHeader());
}

public HarnessResult getUri(URI uri, HttpHeaders headers) {
    return getUri(uri.toString(), headers);
}
```

---

## Per-File Migration Guide

### Checklist applicable to every file

- [ ] Change `extends BaseIntegrationTest` → `extends BaseHarnessTest`
- [ ] Remove `@ActiveProfiles("test")` class annotation
- [ ] Remove `@TestInstance(TestInstance.Lifecycle.PER_CLASS)` if present (see note below)
- [ ] Remove all `@WithMockUser` annotations — replace with token helpers per the table above
- [ ] Replace `MvcResult result` declarations with `HarnessResult result`
- [ ] Replace `BaseIntegrationTest.CaseworkerJohnDoe` / `BaseIntegrationTest.CaseworkerJaneDoe` references with `CaseworkerJohnDoe` / `CaseworkerJaneDoe` (inherited from `BaseHarnessTest`)
- [ ] Remove `import org.springframework.test.web.servlet.MvcResult`
- [ ] Remove `import org.springframework.security.test.context.support.WithMockUser`
- [ ] Remove `import org.springframework.test.context.ActiveProfiles`
- [ ] Add `import uk.gov.justice.laa.dstew.access.utils.harness.BaseHarnessTest`
- [ ] Add `import uk.gov.justice.laa.dstew.access.utils.harness.HarnessResult`
- [ ] Add `import uk.gov.justice.laa.dstew.access.utils.harness.SmokeTest` (if `@SmokeTest` needed)

**Note on `@TestInstance(PER_CLASS)`:** `BaseIntegrationTest` is `@Transactional`, so
`PER_CLASS` is safe there (the transaction rolls back per method regardless of instance
lifecycle). `BaseHarnessTest` does not use `@Transactional`. Tests that use `PER_CLASS`
for `@MethodSource` stream generation are fine — the source method is static. Remove
`PER_CLASS` unless there is a non-`@MethodSource` reason to keep it; the default
`PER_METHOD` lifecycle is correct for harness tests.

---

### `GetApplicationsTest`

**Complexity: HIGH** — 1200 lines, many count-based assertions, URI building helpers,
`@TestInstance(PER_CLASS)`.

#### Class-level changes

| What | Old | New |
|---|---|---|
| Base class | `extends BaseIntegrationTest` | `extends BaseHarnessTest` |
| `@ActiveProfiles("test")` | Present | Remove |
| `@TestInstance(PER_CLASS)` | Present | Remove — `@MethodSource` sources are static, so PER_METHOD is fine |
| `@WithMockUser(authorities = CASEWORKER)` | On every test | Remove |
| `@WithMockUser(authorities = UNKNOWN)` | On some tests | Replace with `withToken(TestConstants.Tokens.UNKNOWN)` in test body |
| Tests with no `@WithMockUser` | `givenNoUser_*` tests | Add `withNoToken()` in test body |

#### Count-based assertions — key risk

`GetApplicationsTest` makes assertions like `assertThat(actual.getApplications().size()).isEqualTo(9)`.
Under `@Transactional` the database was empty at the start of each test. Under the harness,
the database is clean because `deleteTrackedData()` removes all tracked entities in
`@AfterEach`, and `clearTrackedIds()` is called in `@BeforeEach` — so the slate is clean at
the start of every test. However, **only entities created via `persistedDataGenerator`
are tracked and deleted**. If any test creates entities through a route that bypasses
`persistedDataGenerator` (e.g. direct repository calls), those rows must be cleaned up
separately. Check all test methods for direct repository saves before migrating.

#### URI building helpers

The test uses `getUri(String)` with manually constructed query strings (no path variables)
and also constructs `URI` objects. The `getUri(URI)` overload added to `BaseHarnessTest`
(see above) covers the URI-object calls.

#### `@SmokeTest` candidates

| Test | `@SmokeTest`? | Reason |
|---|---|---|
| `givenValidApplicationsDataAndIncorrectHeader_whenGetApplications_thenReturnBadRequest` | ✅ | Header enforcement, no data |
| `givenValidApplicationsDataAndNoHeader_whenGetApplications_thenReturnBadRequest` | ✅ | Header enforcement, no data |
| All data-dependent tests | ❌ | Require pre-seeded data |

---

### `AssignCaseworkerTest`

**Complexity: MEDIUM** — 360 lines, parameterised cases, `@TestInstance(PER_CLASS)`,
role variants including UNKNOWN and no-user.

#### Class-level changes

| What | Old | New |
|---|---|---|
| Base class | `extends BaseIntegrationTest` | `extends BaseHarnessTest` |
| `@ActiveProfiles("test")` | Present | Remove |
| `@TestInstance(PER_CLASS)` | Present | Remove |
| `@WithMockUser(authorities = CASEWORKER)` | On most tests | Remove |
| `@WithMockUser(authorities = UNKNOWN)` | `givenReaderRole_*`, `givenUnknownRole_*` | Replace with `withToken(TestConstants.Tokens.UNKNOWN)` in test body |
| Tests with no `@WithMockUser` | `givenNoUser_*` | Add `withNoToken()` in test body |

#### `BaseIntegrationTest.CaseworkerJohnDoe` references

The test references `BaseIntegrationTest.CaseworkerJohnDoe` and
`BaseIntegrationTest.CaseworkerJaneDoe` as static fields. In `BaseHarnessTest` these are
instance fields (`protected CaseworkerEntity CaseworkerJohnDoe`). Update all references
from `BaseIntegrationTest.CaseworkerJohnDoe` to `CaseworkerJohnDoe` (and same for
`CaseworkerJaneDoe`).

#### `@SmokeTest` candidates

| Test | `@SmokeTest`? | Reason |
|---|---|---|
| `givenValidAssignRequestAndInvalidHeader_*` | ✅ | Header enforcement |
| `givenValidAssignRequestAndNoHeader_*` | ✅ | Header enforcement |
| `givenNoUser_whenAssignCaseworker_thenReturnUnauthorised` | ✅ | Security baseline, no data |
| All data-dependent tests | ❌ | |

---

### `ReassignCaseworkerTest`

**Complexity: LOW** — 109 lines, one data test and header/bad-request tests.

#### Class-level changes

| What | Old | New |
|---|---|---|
| Base class | `extends BaseIntegrationTest` | `extends BaseHarnessTest` |
| `@ActiveProfiles("test")` | Present | Remove |
| No `@TestInstance` | — | No change |
| `@WithMockUser(authorities = CASEWORKER)` | On all tests | Remove |

#### `BaseIntegrationTest.CaseworkerJaneDoe` references

Same as `AssignCaseworkerTest` — update to `CaseworkerJaneDoe` / `CaseworkerJohnDoe`.

#### `@SmokeTest` candidates

| Test | `@SmokeTest`? | Reason |
|---|---|---|
| `givenValidReassignRequestAndInvalidHeader_*` | ✅ | Header enforcement |
| `givenValidReassignRequestAndNoHeader_*` | ✅ | Header enforcement |
| `givenValidReassignRequest_whenAssignCaseworker_*` | ❌ | Creates data |

---

### `UnassignCaseworkerTest`

**Complexity: MEDIUM** — 267 lines, role variants, a test that asserts
`domainEventRepository.findAll().size() == 0`.

#### Class-level changes

| What | Old | New |
|---|---|---|
| Base class | `extends BaseIntegrationTest` | `extends BaseHarnessTest` |
| `@ActiveProfiles("test")` | Present | Remove |
| No `@TestInstance` | — | No change |
| `@WithMockUser(authorities = CASEWORKER)` | On most tests | Remove |
| `@WithMockUser(authorities = UNKNOWN)` | `givenReaderRole_*`, `givenUnknownRole_*` | Replace with `withToken(TestConstants.Tokens.UNKNOWN)` in test body |
| Tests with no `@WithMockUser` | `givenNoUser_*` | Add `withNoToken()` in test body |

#### `postUri` with path variables

`UnassignCaseworkerTest` calls `postUri(TestConstants.URIs.UNASSIGN_CASEWORKER, body, id)`
where `id` is a UUID path variable, and also
`postUri(TestConstants.URIs.UNASSIGN_CASEWORKER, body, ServiceNameHeader(name), id)`.
These require the two new `postUri` overloads described in the Infrastructure section.

#### `domainEventRepository.findAll().size() == 0` assertion

`givenCaseworkerNotExist_whenUnassignCaseworker_thenReturnOK` asserts
`domainEventRepository.findAll().size() == 0`. Under `@Transactional` this was safe
because the database was clean. Under the harness, `deleteTrackedData()` removes tracked
domain events in `@AfterEach` — but the assertion runs mid-test. Because no domain events
are created by this test (the unassign produces no event when there's no caseworker), the
assertion should still hold. **However**, if any domain events were left by a preceding
test (due to a teardown failure), this test would fail. The assertion should be tightened
to verify that no domain event was created *for this application*:

```java
// Old:
assertEquals(0, domainEventRepository.findAll().size());

// New (scope to the specific application):
assertEquals(0, domainEventRepository.findAll().stream()
        .filter(e -> e.getApplicationId().equals(expectedUnassignedApplication.getId()))
        .count());
```

#### `@SmokeTest` candidates

| Test | `@SmokeTest`? | Reason |
|---|---|---|
| `givenValidUnassignRequestAndInvalidHeader_*` | ✅ | Header enforcement |
| `givenValidUnassignRequestAndNoHeader_*` | ✅ | Header enforcement |
| `givenNoUser_whenUnassignCaseworker_thenReturnUnauthorised` | ✅ | Security baseline |
| All data-dependent tests | ❌ | |

---

### `UpdateApplicationTest`

**Complexity: LOW** — 223 lines, `@TestInstance(PER_CLASS)`, straightforward PATCH tests.

#### Class-level changes

| What | Old | New |
|---|---|---|
| Base class | `extends BaseIntegrationTest` | `extends BaseHarnessTest` |
| `@ActiveProfiles("test")` | Present | Remove |
| `@TestInstance(PER_CLASS)` | Present | Remove |
| `@WithMockUser(authorities = CASEWORKER)` | On most tests | Remove |
| `@WithMockUser(authorities = UNKNOWN)` | `givenReaderRole_*`, `givenUnknownRole_*` | Replace with `withToken(TestConstants.Tokens.UNKNOWN)` in test body |
| Tests with no `@WithMockUser` | `givenNoUser_*` | Add `withNoToken()` in test body |

#### `@SmokeTest` candidates

| Test | `@SmokeTest`? | Reason |
|---|---|---|
| `givenValidApplicationDataAndIncorrectHeader_*` | ✅ | Header enforcement |
| `givenNoUser_whenUpdateApplication_thenReturnUnauthorised` | ✅ | Security baseline |
| All data-dependent tests | ❌ | |

---

### `GetDomainEventTest`

**Complexity: LOW** — 194 lines, no `@TestInstance`, a private `setUpDomainEvents` helper
that uses `persistedDataGenerator`.

#### Class-level changes

| What | Old | New |
|---|---|---|
| Base class | `extends BaseIntegrationTest` | `extends BaseHarnessTest` |
| No `@ActiveProfiles` | — | No change needed (not present) |
| No `@TestInstance` | — | No change |
| `@WithMockUser(authorities = CASEWORKER)` | On most tests | Remove |
| `@WithMockUser(authorities = UNKNOWN)` | `givenNoRole_*` | Replace with `withToken(TestConstants.Tokens.UNKNOWN)` in test body |
| Tests with no `@WithMockUser` | `givenNoUser_*` | Add `withNoToken()` in test body |

#### `BaseIntegrationTest.CaseworkerJohnDoe` reference

`setUpDomainEvents` references `BaseIntegrationTest.CaseworkerJohnDoe.getId()`. Update to
`CaseworkerJohnDoe.getId()`.

#### `@SmokeTest` candidates

| Test | `@SmokeTest`? | Reason |
|---|---|---|
| `givenApplicationWithDomainEventsAndNoHeader_*` | ✅ | Header enforcement |
| `givenApplicationWithDomainEventsAndInvalidHeader_*` | ✅ | Header enforcement |
| `givenNoUser_whenApplicationHistorySearch_thenReturnUnauthorised` | ✅ | Security baseline |
| All data-dependent tests | ❌ | |

---

### `GetIndividualsTest`

**Complexity: MEDIUM** — 432 lines, many filter combinations, `@WithMockUser` (no
authorities — bare annotation) tests, count-based paging assertions.

#### Class-level changes

| What | Old | New |
|---|---|---|
| Base class | `extends BaseIntegrationTest` | `extends BaseHarnessTest` |
| `@ActiveProfiles("test")` | Present | Remove |
| No `@TestInstance` | — | No change |
| `@WithMockUser(authorities = CASEWORKER)` | On most tests | Remove |
| `@WithMockUser(authorities = UNKNOWN)` | Several tests | Replace with `withToken(TestConstants.Tokens.UNKNOWN)` in test body |
| `@WithMockUser` (bare, no authorities) | `givenUserWithNoAuthorities_*` tests | Replace with `withToken(TestConstants.Tokens.UNKNOWN)` — produces 403, same as no-authority user |
| Tests with no `@WithMockUser` | `givenNoUser_*` | Add `withNoToken()` in test body |

#### Count-based assertions — paging tests

`givenPagingParameters_whenGetIndividuals_thenCorrectPagingInResponse` uses
`@MethodSource` with a `totalEntities` parameter and asserts exact `totalRecords`.
These assertions depend on the database being empty of `IndividualEntity` rows before
each test. Under the harness, `deleteTrackedData()` removes all tracked entities.
`IndividualEntity` rows created via `persistedDataGenerator.createAndPersistMultiple` are
tracked (because `IndividualRepository` is registered in `PersistedDataGenerator.init()`).

However, `ApplicationEntity` rows created with inline `Set.of(individual)` individuals
(several filter tests) persist individuals via cascade. Those individuals are **not**
directly tracked by ID — they are removed when the application is cascade-deleted. This
is safe as long as no individual exists without an application at the end of a test. Review
each test's individual-creation pattern and confirm the individual is either:
- Created via `persistedDataGenerator.createAndPersist(IndividualEntityGenerator.class, ...)`
  (directly tracked), or
- Passed inline as a transient entity to an `ApplicationEntity` builder (cascade-deleted
  when the application is removed).

Both patterns already exist in the test; the paging test uses the first pattern and is
safe.

#### `@SmokeTest` candidates

| Test | `@SmokeTest`? | Reason |
|---|---|---|
| `givenPagingParametersAndInvalidHeader_*` | ✅ | Header enforcement |
| `givenPagingParametersAndNoHeader_*` | ✅ | Header enforcement |
| `givenNoUser_whenGetIndividuals_thenReturnUnauthorisedResponse` | ✅ | Security baseline |
| All data-dependent tests | ❌ | |

---

## `@WithMockUser` → Token Helper Substitution Table

This is the complete substitution rule for every pattern found across all six files:

| Old annotation | Replacement |
|---|---|
| `@WithMockUser(authorities = TestConstants.Roles.CASEWORKER)` | Remove — default token is CASEWORKER |
| `@WithMockUser(authorities = TestConstants.Roles.UNKNOWN)` | Remove annotation; add `withToken(TestConstants.Tokens.UNKNOWN);` as first line of test body |
| `@WithMockUser` (bare, no authorities) | Remove annotation; add `withToken(TestConstants.Tokens.UNKNOWN);` as first line of test body |
| *(no annotation)* — unauthenticated / no-user tests | Add `withNoToken();` as first line of test body |

`withToken` and `withNoToken` are already on `BaseHarnessTest`. They set state that is
consumed by the next HTTP call and reset automatically after each call.

---

## Known Gotchas from Plans 04 and 05

### `domainEventRepository.findAll()` size assertions

Any test that asserts on `domainEventRepository.findAll().size()` is sensitive to domain
events left by other tests. Under `@Transactional` this was not a risk. Under the harness,
scope the assertion to events for the specific application (as shown for
`UnassignCaseworkerTest` above) rather than relying on a global count.

### `clearCache()` no longer exists

`BaseIntegrationTest` exposes `clearCache()` which calls `entityManager.flush()` and
`entityManager.clear()`. This is a `MockMvc`-specific technique to force Hibernate to
re-read from the database within the same transaction. Under the harness there is no
shared transaction — each HTTP request commits its own transaction and the test thread
reads freshly from the database on every repository call. Remove any `clearCache()` call
encountered during migration; repository reads in the test body will already see committed
data.

### `@TestInstance(PER_CLASS)` removal and `@MethodSource`

The only reason these tests use `PER_CLASS` is to allow non-static `@MethodSource`
stream methods. In JUnit 5, `@MethodSource` methods must be static by default (or the
test class must be `PER_CLASS`). When removing `PER_CLASS`, make any referenced
`@MethodSource` methods `static`. All source methods in these tests (`validAssignCaseworkerRequestCases`,
`invalidAssignCaseworkerRequestCases`, `invalidApplicationIdListsCases`,
`searchFieldAndOrderParameters`, `invalidApplicationUpdateRequestCases`,
`pagingParameters`, `invalidPagingParameters`) use only literal data — they have no
instance dependencies and can trivially be made static.

### `LazyInitializationException` risk

No test in this batch reads a lazy collection in the same way that `ApplicationMakeDecisionTest.verifyDecisionSavedCorrectly`
did (navigating from an application to its decision's merits decisions). However, any test
that navigates from a fetched entity into a lazy association — for example, fetching an
`ApplicationEntity` then reading `application.getDecision().getMeritsDecisions()` — will
fail. Audit each assertion that traverses entity relationships and apply the `JOIN FETCH`
pattern from Plan 05 if needed.

---

## Recommended Migration Order

Migrate in ascending complexity to build confidence and uncover new patterns early:

1. `ReassignCaseworkerTest` — 109 lines, all CASEWORKER, no role variants, no gotchas
2. `GetDomainEventTest` — 194 lines, simple GET, one static-reference fix
3. `UpdateApplicationTest` — 223 lines, PATCH, role variants, remove `PER_CLASS`
4. `UnassignCaseworkerTest` — 267 lines, needs new `postUri` path-arg overloads, `findAll` scoping fix
5. `AssignCaseworkerTest` — 360 lines, parameterised cases, multiple role variants
6. `GetIndividualsTest` — 432 lines, paging count assertions, bare `@WithMockUser`
7. `GetApplicationsTest` — 1200 lines, URI-object overloads, count assertions, do last

Migrate and run each file's tests before moving to the next. Use:

```bash
./gradlew :data-access-service:integrationTest \
  --tests "uk.gov.justice.laa.dstew.access.controller.application.ReassignCaseworkerTest"
```

---

## Full Change Summary

### Files to create/modify

| File | Action |
|---|---|
| `utils/harness/BaseHarnessTest.java` | Add `postUri(String, T, Object...)` and `postUri(String, T, HttpHeaders, Object...)` overloads; add `getUri(URI)` and `getUri(URI, HttpHeaders)` overloads |
| `controller/application/GetApplicationsTest.java` | Port to harness |
| `controller/application/AssignCaseworkerTest.java` | Port to harness |
| `controller/application/ReassignCaseworkerTest.java` | Port to harness |
| `controller/application/UnassignCaseworkerTest.java` | Port to harness; scope `findAll` assertion; add path-arg `postUri` calls |
| `controller/application/UpdateApplicationTest.java` | Port to harness |
| `controller/application/GetDomainEventTest.java` | Port to harness |
| `controller/individual/GetIndividualsTest.java` | Port to harness; make `@MethodSource` methods static |

