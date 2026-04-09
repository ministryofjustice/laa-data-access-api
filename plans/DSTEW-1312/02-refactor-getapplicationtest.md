# Refactor Plan: `GetApplicationTest` → Harness-Based Test (Minimal-Diff Revision)

## Goal

Port `GetApplicationTest` to run via the harness **while keeping the test body as close to
unchanged as possible**. The same bridging infrastructure used for `GetCaseworkersTest`
(`BaseHarnessTest`, `HarnessResult`, `ResponseAsserts` overloads) is already in place.
The only new work is:

1. Extending `BaseHarnessTest` with application-data lifecycle support.
2. Adding the handful of `ResponseAsserts` overloads the test calls that don't yet exist for
   `HarnessResult` (`assertNotFound`, `assertContentHeaders`, `assertNoCacheHeaders`).
3. Adding a `deserialise` helper and a `getUri(String uri, Object... args)` variant to
   `BaseHarnessTest`.
4. Making the minimal line-level changes inside the test itself.

---

## Context

`GetApplicationTest` currently extends `BaseIntegrationTest`, which provides:

- `@Transactional` auto-rollback — every entity created in a test is automatically cleaned up.
- `applicationRepository.saveAndFlush(application)` — to save mid-test mutations.
- `clearCache()` — to flush and clear the JPA `EntityManager`.
- `deserialise(result, Class)` — convenience wrapper over `objectMapper.readValue(...)`.
- `getUri(String uri, Object... args)` — GET with path-variable args.
- `applicationAsserts.assertErrorGeneratedByBadHeader(result, serviceName)` — inspects
  `MvcResult.getResolvedException()`.

The harness has no `EntityManager`, no `@Transactional`, and no servlet internals. We need to
replace each of these without touching test logic more than necessary.

---

## Key Differences to Bridge

| Concern | `BaseIntegrationTest` | Harness |
|---|---|---|
| Data teardown | `@Transactional` auto-rollback | Manual `deleteAll` in `@AfterEach` |
| Cache clearing | `entityManager.flush()` / `.clear()` | No-op (real HTTP; no shared session) |
| `deserialise` helper | On `BaseIntegrationTest` | Must add to `BaseHarnessTest` |
| `getUri(uri, args)` | On `BaseIntegrationTest` (path vars) | Must add to `BaseHarnessTest` |
| `assertErrorGeneratedByBadHeader` | Inspects `getResolvedException()` | Reduce to `assertBadRequest` |
| `assertNotFound` / `assertContentHeaders` / `assertNoCacheHeaders` | `MvcResult` overloads exist | `HarnessResult` overloads must be added |
| `applicationRepository.saveAndFlush` | Direct `@Autowired` repo | Obtain via `harnessProvider.getBean(...)` |

---

## Authentication Strategy

Identical to `GetCaseworkersTest` — already handled by `BaseHarnessTest`:

- **Caseworker role (default):** `Authorization: Bearer swagger-caseworker-token`
- **Unknown role:** `withToken(TestConstants.Tokens.UNKNOWN);` before the call
- **No user:** `withNoToken();` before the call

---

## Data Lifecycle Strategy

`GetApplicationTest` creates `ApplicationEntity`, `ProceedingEntity`, and `DecisionEntity`
records (with a `MeritsDecisionEntity` nested inside). Because there is no `@Transactional`
rollback in the harness, every test that creates data must clean up after itself in `@AfterEach`.

### Tracking created entities

`BaseHarnessTest` already tracks `Caseworkers` for teardown. We extend the same pattern inside
`GetApplicationTest` with a per-test accumulator:

```java
private final List<ApplicationEntity> createdApplications = new ArrayList<>();
private final List<ProceedingEntity>  createdProceedings  = new ArrayList<>();
private final List<DecisionEntity>    createdDecisions    = new ArrayList<>();
```

Each `persistedDataGenerator.createAndPersist(...)` call result is added to the relevant list.
The `@AfterEach` deletes them in the correct dependency order:

```
applications → (nullify decision FK first) → decisions → proceedings
```

Concretely, because `ApplicationEntity.decision` is a `@OneToOne` FK on the `applications`
table pointing at `decisions`, the application row must be deleted (or have its FK nullified)
before the decision row. The safest sequence is:

```
1. applicationRepository.deleteAll(createdApplications)
2. decisionRepository.deleteAll(createdDecisions)
3. proceedingRepository.deleteAll(createdProceedings)
```

`MeritsDecisionEntity` rows are cascade-managed by the `@ManyToMany(cascade = CascadeType.PERSIST)`
on `DecisionEntity` — but **not** `CascadeType.REMOVE`. The `linked_merits_decisions` join-table
rows are removed by the JPA provider when the `DecisionEntity` is deleted; the
`merits_decisions` table rows themselves can be left (they are orphaned, not FK-constrained
back to decisions). In practice, `meritsDecisionRepository.deleteAll()` can be called
optionally if you want strict cleanup — but it is not required for referential integrity.

### `saveAndFlush` mid-test

One test (`givenExistingApplication_whenGetApplication_thenReturnOKWithCorrectData`) calls:

```java
application.setDecision(decision);
applicationRepository.saveAndFlush(application);
clearCache();
```

In the harness there is no `EntityManager.flush/clear` to call (no shared persistence
context). The equivalent is simply:

```java
application.setDecision(decision);
applicationRepository.saveAndFlush(application);
// no clearCache() needed — the real HTTP call fetches fresh from DB
```

`applicationRepository` is obtained from `harnessProvider.getBean(ApplicationRepository.class)`,
exposed as a lazily-resolved field on `BaseHarnessTest` (same pattern as `caseworkerRepository`).

---

## Changes Required

### 1. `ResponseAsserts.java` — add missing `HarnessResult` overloads

The test calls `assertNotFound`, `assertContentHeaders`, and `assertNoCacheHeaders`.
The `HarnessResult` versions do not yet exist. Add them alongside the existing ones:

```java
public static void assertNotFound(HarnessResult response) {
    assertEquals(HttpStatus.NOT_FOUND.value(), response.getResponse().getStatus());
}

public static void assertContentHeaders(HarnessResult response) {
    assertEquals("application/json", response.getResponse().getHeader("Content-Type"));
}

public static void assertNoCacheHeaders(HarnessResult response) {
    assertEquals("no-cache, no-store, max-age=0, must-revalidate",
            response.getResponse().getHeader("Cache-Control"));
    assertEquals("no-cache", response.getResponse().getHeader("Pragma"));
    assertEquals("0", response.getResponse().getHeader("Expires"));
}
```

> **Note on `assertContentHeaders`:** `MockHttpServletResponse.getContentType()` returns the
> full content-type string (e.g. `"application/json"`). Over real HTTP the `Content-Type`
> header may include the charset, e.g. `"application/json;charset=UTF-8"`. Use
> `startsWith("application/json")` rather than `assertEquals` so both transport paths pass:
>
> ```java
> public static void assertContentHeaders(HarnessResult response) {
>     String ct = response.getResponse().getHeader("Content-Type");
>     assertThat(ct).startsWith("application/json");
> }
> ```

---

### 2. `BaseHarnessTest.java` — add application repository, `getUri` path-var overload, and `deserialise`

```java
// Additional @HarnessInject field (lazily resolved alongside caseworkerRepository):
protected ApplicationRepository applicationRepository;

// Resolved in @BeforeEach setupHarness(), alongside existing lines:
applicationRepository = harnessProvider.getBean(ApplicationRepository.class);
```

Add a `getUri` overload that mirrors `BaseIntegrationTest.getUri(String uri, Object... args)`:

```java
/**
 * GET uri with path-variable args, X-Service-Name: CIVIL_APPLY, and the current bearer token.
 * Mirrors BaseIntegrationTest.getUri(String, Object...).
 */
public HarnessResult getUri(String uri, Object... args) {
    return getUri(uri, defaultServiceNameHeader(), args);
}

/**
 * GET uri with path-variable args and the supplied headers.
 */
public HarnessResult getUri(String uri, HttpHeaders headers, Object... args) {
    // expand Spring-style {id} placeholders into the URI before issuing the request
    String expandedUri = UriComponentsBuilder.fromUriString(uri)
            .buildAndExpand(args)
            .toUriString();
    return getUri(expandedUri, headers);
}
```

Add a `deserialise` helper:

```java
public <T> T deserialise(HarnessResult result, Class<T> clazz) throws Exception {
    return objectMapper.readValue(result.getResponse().getContentAsString(), clazz);
}
```

---

### 3. `GetApplicationTest.java` — minimal diff

The **only changes** needed vs. the current file:

| What | Old | New |
|---|---|---|
| Class declaration | `extends BaseIntegrationTest` | `extends BaseHarnessTest` |
| Class-level annotation | `@ActiveProfiles("test")` | Remove |
| Import | `MvcResult` | `HarnessResult` |
| Import | `BaseIntegrationTest` | `BaseHarnessTest` |
| Return types | `MvcResult result` (all call sites) | `HarnessResult result` |
| Auth — caseworker tests | `@WithMockUser(authorities = TestConstants.Roles.CASEWORKER)` | Remove |
| Auth — unknown role | `@WithMockUser(authorities = TestConstants.Roles.UNKNOWN)` | Remove + add `withToken(TestConstants.Tokens.UNKNOWN);` as first line |
| Auth — no user | *(no annotation)* | Add `withNoToken();` as first line |
| `applicationAsserts.assertErrorGeneratedByBadHeader(result, serviceName)` | Inspects servlet exception | Replace with `assertBadRequest(result)` |
| `applicationRepository.saveAndFlush(application)` | Via `@Autowired` field on `BaseIntegrationTest` | Via `applicationRepository` field on `BaseHarnessTest` — **no change to call site** |
| `clearCache()` | Flush + clear JPA entity manager | Remove — no-op in harness, fresh DB read guaranteed by real HTTP |
| `deserialise(result, Class)` | On `BaseIntegrationTest` | On `BaseHarnessTest` — **no change to call site** |
| Data teardown | `@Transactional` auto-rollback | Track created entities; `@AfterEach` deletes in FK-safe order |
| `BaseIntegrationTest.CaseworkerJohnDoe` (static) | Referenced as `CaseworkerJohnDoe` | Same field name, already instance field on `BaseHarnessTest` — **no change** |
| `assertContentHeaders` / `assertNotFound` / `assertNoCacheHeaders` | `MvcResult` overloads | New `HarnessResult` overloads (step 1 above) — **no change to call site** |

#### Data teardown additions (inside `GetApplicationTest`)

Add three tracking lists and an `@AfterEach`, which is the only structural addition to the
test class itself:

```java
// tracking lists — populated by every createAndPersist call in this class
private final List<ApplicationEntity> createdApplications = new ArrayList<>();
private final List<ProceedingEntity>  createdProceedings  = new ArrayList<>();
private final List<DecisionEntity>    createdDecisions    = new ArrayList<>();

@AfterEach
void tearDownApplicationData() {
    applicationRepository.deleteAll(createdApplications);
    decisionRepository.deleteAll(createdDecisions);
    proceedingRepository.deleteAll(createdProceedings);
    createdApplications.clear();
    createdDecisions.clear();
    createdProceedings.clear();
}
```

`decisionRepository` and `proceedingRepository` follow the same pattern as
`applicationRepository` — resolved from `harnessProvider` in `BaseHarnessTest.setupHarness()`.

Each `createAndPersist` call in a test body gains one line immediately after it:

```java
// Example — existing line:
ApplicationEntity application = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class, ...);
// New line added directly after:
createdApplications.add(application);

// Likewise for proceedings and decisions:
ProceedingEntity proceeding = persistedDataGenerator.createAndPersist(ProceedingsEntityGenerator.class, ...);
createdProceedings.add(proceeding);

DecisionEntity decision = persistedDataGenerator.createAndPersist(DecisionEntityGenerator.class, ...);
createdDecisions.add(decision);
```

This is the **only** change to the body of each test method beyond removing `@WithMockUser` and
replacing `MvcResult` with `HarnessResult`.

---

### 4. `@SmokeTest` annotation — which tests to mark

Following the same convention as `GetCaseworkersTest`, mark the "happy-path" tests that will
also exercise real infrastructure and are safe to run against a live deployment:

| Test | `@SmokeTest`? | Reason |
|---|---|---|
| `givenApplicationDataAndIncorrectHeader_whenGetApplications_thenReturnBadRequest` | ✅ | Validates header enforcement |
| `givenApplicationDataAndNoHeader_whenGetApplication_thenReturnBadRequest` | ✅ | Validates header enforcement |
| `givenExistingApplication_whenGetApplication_thenReturnOKWithCorrectData` | ✅ | Core happy path |
| `givenApplicationNotExist_whenGetApplication_thenReturnNotFound` | ✅ | Core error path |
| `givenUnknownRole_whenGetApplication_thenReturnForbidden` | ❌ | Security simulation — leave for integration only |
| `givenNoUser_whenGetApplication_thenReturnUnauthorised` | ❌ | Security simulation — leave for integration only |
| Opponent / provider content tests | ❌ | Data-shape tests; no additional infra coverage |

---

## Full Rewritten `GetApplicationTest.java`

```java
package uk.gov.justice.laa.dstew.access.controller.application;

import com.fasterxml.jackson.core.type.TypeReference;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.ProblemDetail;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.DecisionEntity;
import uk.gov.justice.laa.dstew.access.entity.ProceedingEntity;
import uk.gov.justice.laa.dstew.access.model.Application;
import uk.gov.justice.laa.dstew.access.model.ApplicationContent;
import uk.gov.justice.laa.dstew.access.model.ApplicationProceeding;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.DecisionStatus;
import uk.gov.justice.laa.dstew.access.model.Opponent;
import uk.gov.justice.laa.dstew.access.model.OpponentDetails;
import uk.gov.justice.laa.dstew.access.model.Opposable;
import uk.gov.justice.laa.dstew.access.model.Provider;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;
import uk.gov.justice.laa.dstew.access.utils.builders.HttpHeadersBuilder;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationContentGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationMeritsGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.decision.DecisionEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.merit.MeritsDecisionsEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.proceeding.ProceedingsEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.harness.BaseHarnessTest;
import uk.gov.justice.laa.dstew.access.utils.harness.HarnessResult;
import uk.gov.justice.laa.dstew.access.utils.harness.SmokeTest;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertContentHeaders;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertForbidden;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertNoCacheHeaders;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertSecurityHeaders;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertOK;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertNotFound;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertUnauthorised;

public class GetApplicationTest extends BaseHarnessTest {

    private final List<ApplicationEntity> createdApplications = new ArrayList<>();
    private final List<ProceedingEntity>  createdProceedings  = new ArrayList<>();
    private final List<DecisionEntity>    createdDecisions    = new ArrayList<>();

    @AfterEach
    void tearDownApplicationData() {
        applicationRepository.deleteAll(createdApplications);
        decisionRepository.deleteAll(createdDecisions);
        proceedingRepository.deleteAll(createdProceedings);
        createdApplications.clear();
        createdDecisions.clear();
        createdProceedings.clear();
    }

    @SmokeTest
    @ParameterizedTest
    @ValueSource(strings = {"", "invalid-header", "CIVIL-APPLY", "civil_apply"})
    void givenApplicationDataAndIncorrectHeader_whenGetApplications_thenReturnBadRequest(
            String serviceName
    ) throws Exception {
        verifyBadServiceNameHeader(serviceName);
    }

    @SmokeTest
    @Test
    void givenApplicationDataAndNoHeader_whenGetApplication_thenReturnBadRequest() throws Exception {
        verifyBadServiceNameHeader(null);
    }

    private void verifyBadServiceNameHeader(String serviceName) throws Exception {
        HarnessResult result = getUri(TestConstants.URIs.GET_APPLICATION, ServiceNameHeader(serviceName), UUID.randomUUID());
        assertBadRequest(result);
    }

    @SmokeTest
    @Test
    public void givenExistingApplication_whenGetApplication_thenReturnOKWithCorrectData() throws Exception {
        // given
        ApplicationEntity application = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class, builder ->
                builder.caseworker(CaseworkerJohnDoe).linkedApplications(Set.of()));
        createdApplications.add(application);

        ProceedingEntity proceeding = persistedDataGenerator.createAndPersist(ProceedingsEntityGenerator.class, builder -> {
            builder.applicationId(application.getId());
        });
        createdProceedings.add(proceeding);

        DecisionEntity decision = persistedDataGenerator.createAndPersist(DecisionEntityGenerator.class, builder -> {
            builder.meritsDecisions(Set.of(DataGenerator.createDefault(MeritsDecisionsEntityGenerator.class, mBuilder -> {
                mBuilder.proceeding(proceeding);
            })));
        });
        createdDecisions.add(decision);

        application.setDecision(decision);
        applicationRepository.saveAndFlush(application);

        // when
        HarnessResult result = getUri(TestConstants.URIs.GET_APPLICATION, application.getId());
        Application actualApplication = deserialise(result, Application.class);

        // then
        assertContentHeaders(result);
        assertSecurityHeaders(result);
        assertNoCacheHeaders(result);
        assertOK(result);
        Application expectedApplication = createApplication(application, proceeding, decision);
        assertThat(actualApplication).isEqualTo(expectedApplication);
    }

    @SmokeTest
    @Test
    public void givenApplicationNotExist_whenGetApplication_thenReturnNotFound() throws Exception {
        // given
        UUID notExistApplicationId = UUID.randomUUID();

        // when
        HarnessResult result = getUri(TestConstants.URIs.GET_APPLICATION, notExistApplicationId);

        // then
        assertSecurityHeaders(result);
        assertNoCacheHeaders(result);
        assertNotFound(result);
        assertEquals("application/problem+json", result.getResponse().getHeader("Content-Type"));
        ProblemDetail problemDetail = deserialise(result, ProblemDetail.class);
        assertEquals("No application found with id: " + notExistApplicationId, problemDetail.getDetail());
    }

    @Test
    public void givenUnknownRole_whenGetApplication_thenReturnForbidden() throws Exception {
        // given
        withToken(TestConstants.Tokens.UNKNOWN);
        ApplicationEntity expectedApplication = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class);
        createdApplications.add(expectedApplication);

        // when
        HarnessResult result = getUri(TestConstants.URIs.GET_APPLICATION, expectedApplication.getId());

        // then
        assertSecurityHeaders(result);
        assertForbidden(result);
    }

    @Test
    public void givenNoUser_whenGetApplication_thenReturnUnauthorised() throws Exception {
        // given
        withNoToken();
        ApplicationEntity expectedApplication = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class);
        createdApplications.add(expectedApplication);

        // when
        HarnessResult result = getUri(TestConstants.URIs.GET_APPLICATION, expectedApplication.getId());

        // then
        assertSecurityHeaders(result);
        assertUnauthorised(result);
    }

    @Test
    void givenApplicationWithOpponents_whenGetApplication_thenReturnsOpponents() throws Exception {
        ApplicationEntity application = persistedDataGenerator.createAndPersist(
            ApplicationEntityGenerator.class,
            builder -> builder
                .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
                .createdAt(Instant.now().minusSeconds(10000))
                .modifiedAt(Instant.now())
        );
        createdApplications.add(application);

        HarnessResult result = getUri(TestConstants.URIs.GET_APPLICATION, application.getId());
        Application response = deserialise(result, Application.class);

        assertContentHeaders(result);
        assertSecurityHeaders(result);
        assertNoCacheHeaders(result);
        assertOK(result);

        Assertions.assertThat(response.getOpponents()).isNotNull();
        Assertions.assertThat(response.getOpponents()).hasSize(1);

        var mapped = response.getOpponents().get(0);
        Assertions.assertThat(mapped.getOpposableType()).isEqualTo("ApplicationMeritsTask::Individual");
        Assertions.assertThat(mapped.getFirstName()).isEqualTo("John");
        Assertions.assertThat(mapped.getLastName()).isEqualTo("Smith");
        Assertions.assertThat(mapped.getOrganisationName()).isEqualTo("Acme Ltd");
    }

    @Test
    void givenApplicationWithEmptyOpponents_whenGetApplication_thenReturnsEmptyList() throws Exception {
        ApplicationContent applicationContent =
            DataGenerator.createDefault(ApplicationContentGenerator.class,
                builder -> builder
                    .applicationMerits(DataGenerator.createDefault(ApplicationMeritsGenerator.class,
                        meritsBuilder -> meritsBuilder.opponents(List.of())
                    )));

        ApplicationEntity application = persistedDataGenerator.createAndPersist(
            ApplicationEntityGenerator.class,
            builder -> builder
                .applicationContent(objectMapper.convertValue(applicationContent, Map.class))
        );
        createdApplications.add(application);

        HarnessResult result = getUri(TestConstants.URIs.GET_APPLICATION, application.getId());
        Application response = deserialise(result, Application.class);

        assertOK(result);
        Assertions.assertThat(response.getOpponents()).isNotNull();
        Assertions.assertThat(response.getOpponents()).isEmpty();
    }

    @Test
    void givenApplicationWithoutOpponentsSection_whenGetApplication_thenOpponentsIsEmpty() throws Exception {
        Map<String, Object> content = Map.of(
            "someOtherKey", "value"
        );

        ApplicationEntity application = persistedDataGenerator.createAndPersist(
            ApplicationEntityGenerator.class,
            builder -> builder.applicationContent(content)
        );
        createdApplications.add(application);

        HarnessResult result = getUri(TestConstants.URIs.GET_APPLICATION, application.getId());
        Application response = deserialise(result, Application.class);

        assertOK(result);
        Assertions.assertThat(response.getOpponents()).isEmpty();
    }

    @Test
    void givenOpponentWithMissingFirstName_whenGetApplication_thenReturnsRemainingFields() throws Exception {
        // leaving this here as a TODO as it does something specific that the test harness currently does not,
        // i.e. test that a key in the JSON completely missing does not break the functionality.

        Map<String, Object> opposable = Map.of(
            "opposableType", "ApplicationMeritsTask::Individual",
            // firstName intentionally missing
            "lastName", "Smith",
            "name", "Acme Ltd"
        );

        Map<String, Object> opponent = Map.of(
            "opposable", opposable
        );

        Map<String, Object> merits = Map.of(
            "opponents", List.of(opponent)
        );

        Map<String, Object> content = Map.of(
            "applicationMerits", merits
        );

        ApplicationEntity application = persistedDataGenerator.createAndPersist(
            ApplicationEntityGenerator.class,
            builder -> builder.applicationContent(content)
        );
        createdApplications.add(application);

        HarnessResult result = getUri(TestConstants.URIs.GET_APPLICATION, application.getId());
        Application response = deserialise(result, Application.class);

        assertOK(result);
        Assertions.assertThat(response.getOpponents()).isNotNull();
        Assertions.assertThat(response.getOpponents()).hasSize(1);

        var mapped = response.getOpponents().get(0);
        Assertions.assertThat(mapped.getOpposableType()).isEqualTo("ApplicationMeritsTask::Individual");
        Assertions.assertThat(mapped.getFirstName()).isNull();
        Assertions.assertThat(mapped.getLastName()).isEqualTo("Smith");
        Assertions.assertThat(mapped.getOrganisationName()).isEqualTo("Acme Ltd");
    }

    @Test
    void givenApplicationWithSubmitterEmail_whenGetApplication_thenReturnsProviderWithContactEmail() throws Exception {
        ApplicationEntity application = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class);
        createdApplications.add(application);

        HarnessResult result = getUri(TestConstants.URIs.GET_APPLICATION, application.getId());
        Application response = deserialise(result, Application.class);

        assertOK(result);
        Assertions.assertThat(response.getProvider()).isNotNull();
        Assertions.assertThat(response.getProvider().getOfficeCode()).isEqualTo("officeCode");
        Assertions.assertThat(response.getProvider().getContactEmail()).isEqualTo("test@example.com");
    }

    @Test
    void givenApplicationWithoutSubmitterEmail_whenGetApplication_thenReturnsProviderWithoutContactEmail() throws Exception {
        ApplicationEntity application = persistedDataGenerator.createAndPersist(
            ApplicationEntityGenerator.class,
            builder -> builder.applicationContent(Map.of("someOtherKey", "value"))
        );
        createdApplications.add(application);

        HarnessResult result = getUri(TestConstants.URIs.GET_APPLICATION, application.getId());
        Application response = deserialise(result, Application.class);

        assertOK(result);
        Assertions.assertThat(response.getProvider()).isNotNull();
        Assertions.assertThat(response.getProvider().getOfficeCode()).isEqualTo("officeCode");
        Assertions.assertThat(response.getProvider().getContactEmail()).isNull();
    }

    // ── private helpers (unchanged from original) ─────────────────────────────

    private Application createApplication(ApplicationEntity applicationEntity,
                                          ProceedingEntity proceeding,
                                          DecisionEntity decision) {
        // ... identical to current implementation ...
    }

    private List<Opponent> extractOpponents(Map<String, Object> applicationContent) {
        // ... identical to current implementation ...
    }

    private static String extractContactEmail(Map<String, Object> content) {
        // ... identical to current implementation ...
    }
}
```

---

## Notes

### Why per-test tracking lists instead of a shared "nuke all" `deleteAll()`?

Tests in the harness may run concurrently against a shared database (as in the infrastructure
smoke-test mode, where the DB is a real running Postgres). Calling `applicationRepository.deleteAll()`
unconditionally would destroy data created by other tests. Tracking only what _this_ test
created and deleting exactly that is the safe, idiomatic pattern.

### Why `createdApplications.clear()` after `deleteAll`?

If JUnit re-uses the test instance (which it does by default with `@TestInstance(Lifecycle.PER_METHOD)`,
the default), each test method gets a fresh instance so the lists are already empty. However,
calling `.clear()` is cheap insurance if the lifecycle is ever changed to `PER_CLASS`.

### Why is `clearCache()` simply removed?

In `BaseIntegrationTest`, `clearCache()` flushes and clears the JPA `EntityManager` to force
the next read to hit the database rather than the first-level cache. In the harness, each
HTTP call goes over the network to a separate JVM — there is no shared session. The database
is always the source of truth.

### Why does the `getUri` overload use `UriComponentsBuilder`?

`BaseIntegrationTest.getUri(String, Object...)` delegates to MockMvc's `get(uri, args)` which
uses Spring's URI template expansion internally. We replicate that exactly with
`UriComponentsBuilder.fromUriString(uri).buildAndExpand(args).toUriString()`, keeping the
call-site signature identical.

### `assertContentHeaders` — `Content-Type` format difference

`MockHttpServletResponse.getContentType()` returns `"application/json"` (no charset).
Real HTTP responses from Spring Boot typically include `"application/json;charset=UTF-8"` or
`"application/json"` depending on Spring version. The `assertContentHeaders(HarnessResult)`
overload should use `assertThat(ct).startsWith("application/json")` rather than `assertEquals`
to be version-safe.

### `assertEquals("application/problem+json", result.getResponse().getContentType())`

The `givenApplicationNotExist` test directly checks the content type on the response object.
Over real HTTP, `getHeader("Content-Type")` is used (since `getContentType()` is not available
on `HarnessResult.Response`). The plan therefore replaces this one-liner with:

```java
assertEquals("application/problem+json", result.getResponse().getHeader("Content-Type"));
```

(The value returned over HTTP may again include charset — add a `startsWith` check if this
proves fragile in practice.)

---

## Files Changed

| File | Action |
|---|---|
| `utils/asserters/ResponseAsserts.java` | Add `HarnessResult` overloads for `assertNotFound`, `assertContentHeaders`, `assertNoCacheHeaders` |
| `utils/harness/BaseHarnessTest.java` | Add `applicationRepository`, `decisionRepository`, `proceedingRepository` fields; resolve in `setupHarness()`; add `getUri(String, Object...)`, `getUri(String, HttpHeaders, Object...)`, and `deserialise(HarnessResult, Class)` |
| `controller/application/GetApplicationTest.java` | **Minimal diff** — swap base class, remove `@WithMockUser`/`@ActiveProfiles`, add `withToken()`/`withNoToken()`, `MvcResult` → `HarnessResult`, remove `clearCache()`, add tracking lists + `@AfterEach tearDownApplicationData()`, one `createdX.add(...)` line per `createAndPersist` call, `assertErrorGeneratedByBadHeader` → `assertBadRequest`, `getContentType()` → `getHeader("Content-Type")` |

