# Refactor Plan: `CreateApplicationTest` → Harness-Based Test (Minimal-Diff Revision)

## Goal

Port `CreateApplicationTest` to run via the harness **while keeping the test body as close to
unchanged as possible**, following the same pattern established for `GetApplicationTest` and
`GetCaseworkersTest`.

The harness infrastructure already provides most of what this test needs. The work is:

1. Adding `postUri` overloads to `BaseHarnessTest` (mirrors `BaseIntegrationTest`).
2. Adding `assertCreated` and `assertProblemRecord` `HarnessResult` overloads to
   `ResponseAsserts`.
3. Bridging `domainEventAsserts` and `applicationAsserts` access from `BaseHarnessTest`.
4. Handling `HeaderUtils.GetUUIDFromLocation` — already works as-is since it only
   operates on the `Location` header string.
5. Making the minimal line-level changes inside the test itself.

---

## Context

`CreateApplicationTest` currently extends `BaseIntegrationTest`, which provides:

- `@Transactional` auto-rollback — created entities are cleaned up automatically.
- `@WithMockUser(authorities = ...)` — security simulation via Spring Security test context.
- `postUri(String uri, TRequestModel body)` and `postUri(String uri, TRequestModel body, HttpHeaders headers)` — POST helpers.
- `applicationAsserts.assertErrorGeneratedByBadHeader(result, serviceName)` — inspects `MvcResult.getResolvedException()`.
- `domainEventAsserts.assertDomainEventForApplication(entity, type)` — queries `DomainEventRepository` directly.
- `applicationRepository.count()` — for asserting no entities were persisted.
- `applicationRepository.findById(id)` — to load the created entity after the POST.
- `deserialise(result, Class)` — convenience wrapper over `objectMapper.readValue(...)`.

The harness has none of these out-of-the-box. We replicate each without touching test logic
more than necessary.

---

## Key Differences to Bridge

| Concern | `BaseIntegrationTest` | Harness |
|---|---|---|
| Transport | `MockMvc` (in-process) | `WebTestClient` (real HTTP) |
| Security | `@WithMockUser` | Real `Authorization: Bearer` header |
| Data teardown | `@Transactional` auto-rollback | Manual `deleteAll` in `@AfterEach` |
| `postUri` helpers | On `BaseIntegrationTest` | Must add to `BaseHarnessTest` |
| `domainEventAsserts` | `@Autowired` Spring bean | Obtain via `harnessProvider.getBean(...)` |
| `applicationAsserts` | `@Autowired` Spring bean | Obtain via `harnessProvider.getBean(...)` |
| `domainEventRepository` | `@Autowired` on `BaseIntegrationTest` | Obtain via `harnessProvider.getBean(...)` |
| `assertErrorGeneratedByBadHeader` | Inspects `getResolvedException()` (servlet-only) | Replace with `assertBadRequest(result)` |
| `assertCreated` / `assertProblemRecord` | `MvcResult` overloads exist | `HarnessResult` overloads must be added |
| `applicationRepository.count()` | Direct `@Autowired` field | Via `applicationRepository` field on `BaseHarnessTest` — already added by `GetApplicationTest` refactor |
| `TestInstance.Lifecycle.PER_CLASS` | Present on this test | **Remove** — harness uses `PER_METHOD` default |
| `@ActiveProfiles("test")` | Class-level | **Remove** — the harness manages profiles |

---

## Authentication Strategy

Identical to `GetApplicationTest` — already handled by `BaseHarnessTest`:

- **Caseworker role (default):** `Authorization: Bearer swagger-caseworker-token`
- **Unknown role:** `withToken(TestConstants.Tokens.UNKNOWN);` before the call
- **No user:** `withNoToken();` before the call

---

## Data Lifecycle Strategy

`CreateApplicationTest` creates `ApplicationEntity` rows (and linked `ApplicationEntity` rows
for linking tests), plus `DomainEventEntity` rows that are emitted as a side-effect of a
successful POST. Because there is no `@Transactional` rollback, every test that creates data
must clean up after itself in `@AfterEach`.

### Tracking created entities

Follow the same pattern used in `GetApplicationTest`:

```java
private final List<ApplicationEntity> createdApplications = new ArrayList<>();
```

Each entity returned by the POST (obtained via `applicationRepository.findById(createdId)`)
is added to the list. The pre-existing lead applications created by
`persistedDataGenerator.createAndPersist` are also added.

The `@AfterEach` deletes in FK-safe order:

```java
@AfterEach
void tearDownApplicationData() {
    // Domain events reference applications — delete first (or rely on DB CASCADE if configured)
    domainEventRepository.deleteAll(domainEventRepository.findAll()
        .stream()
        .filter(e -> createdApplications.stream()
            .anyMatch(a -> a.getId().equals(e.getApplicationId())))
        .toList());
    applicationRepository.deleteAll(createdApplications);
    createdApplications.clear();
}
```

> **Note on domain events:** `DomainEventEntity` has an `applicationId` column but there is
> no FK constraint in the schema (domain events are an outbox pattern — they reference
> applications logically, not via a DB-level FK). Therefore deleting applications first
> is also safe. Check the actual schema; if there **is** a FK, delete domain events first.
> The safest choice is to delete domain events tied to `createdApplications` before the
> applications themselves.

### `applicationRepository.count()` assertions

Several tests assert `assertEquals(0, applicationRepository.count())` to confirm no
application was persisted for an invalid request. In a shared integration database, other
tests may have left rows. Replace with a scoped check, or — since these tests are testing
POST failure (4xx returned), the correct approach is:

**Keep `applicationRepository.count()` but scope it.** Because each test runs in its own
`@BeforeEach` / `@AfterEach` lifecycle and `createdApplications` is tracked, a simpler
replacement is:

```java
// Old:
assertEquals(0, applicationRepository.count());

// New:
assertTrue(createdApplications.isEmpty(),
    "Expected no application to be persisted, but some were created");
```

This avoids asserting the total row count in a potentially shared database, and instead
checks that _this test_ did not create any applications (because nothing was added to
`createdApplications`).

---

## Changes Required

### 1. `ResponseAsserts.java` — add `HarnessResult` overloads

Two methods are called on `MvcResult` in `CreateApplicationTest` that have no `HarnessResult`
equivalent yet:

#### `assertCreated`

```java
public static void assertCreated(HarnessResult response) {
    assertEquals(HttpStatus.CREATED.value(), response.getResponse().getStatus());
    assertNotNull(response.getResponse().getHeader("Location"));
}
```

#### `assertProblemRecord` (four overloads are used)

`CreateApplicationTest` calls three different signatures:

```java
// (1) full signature
assertProblemRecord(HttpStatus status, ProblemDetail expected, MvcResult result, ProblemDetail actual)

// (2) short signature with explicit title/detail
assertProblemRecord(HttpStatus status, String title, String detail, MvcResult result, ProblemDetail actual, Map<?,?> props)
```

Both delegate to the same internal validation logic. Add `HarnessResult` versions:

```java
public static void assertProblemRecord(
        HttpStatus expectedStatus,
        ProblemDetail expectedDetail,
        HarnessResult response,
        ProblemDetail actualDetail) {
    assertProblemRecord(expectedStatus, expectedDetail.getTitle(), expectedDetail.getDetail(),
            response, actualDetail, expectedDetail.getProperties());
}

public static void assertProblemRecord(
        HttpStatus expectedStatus,
        String expectedTitle,
        String expectedDetail,
        HarnessResult response,
        ProblemDetail actualDetail,
        Map<String, Object> expectedProperties) {
    assertThat(response.getResponse().getHeader("Content-Type"))
            .startsWith("application/problem+json");
    assertEquals(expectedStatus.value(), response.getResponse().getStatus());
    assertEquals(expectedTitle, actualDetail.getTitle());
    assertEquals(expectedDetail, actualDetail.getDetail());
    // property-comparison logic identical to existing MvcResult overload
    Map<String, Object> actualProperties = actualDetail.getProperties();
    if (expectedProperties == null) {
        assertNull(actualProperties);
        return;
    }
    assertNotNull(actualProperties);
    assertThat(actualProperties.keySet())
            .containsExactlyInAnyOrderElementsOf(expectedProperties.keySet());
    for (Object value : expectedProperties.values()) {
        if (value instanceof List<?> expectedList) {
            String key = expectedProperties.entrySet().stream()
                    .filter(e -> e.getValue() == value).findFirst().get().getKey();
            Object actualValue = actualProperties.get(key);
            assertThat(actualValue).isInstanceOf(List.class);
            List<Object> actualList = new ArrayList<>((List<?>) actualValue);
            assertThat(actualList).containsExactlyInAnyOrderElementsOf(new ArrayList<>(expectedList));
        } else {
            String key = expectedProperties.entrySet().stream()
                    .filter(e -> e.getValue() == value).findFirst().get().getKey();
            assertEquals(value, actualProperties.get(key));
        }
    }
}
```

> **Note on `Content-Type` assertion:** The existing `MvcResult` version uses
> `assertEquals("application/problem+json", response.getResponse().getContentType())`.
> Over real HTTP the header may include charset. Use `startsWith("application/problem+json")`
> for the `HarnessResult` overload, consistent with the pattern used in `assertContentHeaders`.

---

### 2. `BaseHarnessTest.java` — add `postUri` helpers, `domainEventAsserts`, `domainEventRepository`, `applicationAsserts`

#### New fields (resolved in `@BeforeEach setupHarness()`):

```java
protected DomainEventAsserts   domainEventAsserts;
protected ApplicationAsserts   applicationAsserts;
protected DomainEventRepository domainEventRepository;
```

Resolved alongside existing fields:

```java
domainEventAsserts    = harnessProvider.getBean(DomainEventAsserts.class);
applicationAsserts    = harnessProvider.getBean(ApplicationAsserts.class);
domainEventRepository = harnessProvider.getBean(DomainEventRepository.class);
```

#### `postUri` overloads

`BaseIntegrationTest` exposes several `postUri` signatures. `CreateApplicationTest` uses three:

1. `postUri(String uri, TRequestModel body)` — POST with body + default headers
2. `postUri(String uri, TRequestModel body, HttpHeaders headers)` — POST with body + explicit headers
3. `postUri(String uri, String rawBody)` — POST with a raw string body (used for the
   `givenNoRequestBody` test which passes `""` or `"{}"`)

Add all three to `BaseHarnessTest`:

```java
public <T> HarnessResult postUri(String uri, T requestModel) throws Exception {
    return postUri(uri, requestModel, defaultServiceNameHeader());
}

public <T> HarnessResult postUri(String uri, T requestModel, HttpHeaders headers) throws Exception {
    String body = (requestModel instanceof String)
            ? (String) requestModel
            : objectMapper.writeValueAsString(requestModel);

    WebTestClient.RequestHeadersSpec<?> spec = webTestClient.post()
            .uri(uri)
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .bodyValue(body);

    if (!omitToken) {
        spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + currentToken);
    }
    if (headers != null) {
        for (Map.Entry<String, List<String>> entry : headers.headerSet()) {
            spec = spec.header(entry.getKey(), entry.getValue().toArray(String[]::new));
        }
    }

    // Reset token state after each use
    currentToken = TestConstants.Tokens.CASEWORKER;
    omitToken = false;

    EntityExchangeResult<byte[]> raw = spec.exchange()
            .expectBody().returnResult();

    int status = raw.getStatus().value();
    Map<String, String> responseHeaders = raw.getResponseHeaders().toSingleValueMap();
    String responseBody = raw.getResponseBody() == null
            ? "" : new String(raw.getResponseBody(), StandardCharsets.UTF_8);

    return new HarnessResult(status, responseHeaders, responseBody);
}
```

> **Note on `omitToken` / `currentToken` reset:** `getUri` already resets these after each
> call. `postUri` must do the same, so `withToken(...)` / `withNoToken()` act as
> "one-shot" overrides for the immediately following call only.

> **Note on the raw-string overload:** The `givenNoRequestBody_whenCreateApplication_thenReturnBadRequest`
> test passes `""` and `"{}"` directly as the request body. The generic `<T>` overload handles
> this when `T` is `String` — no separate overload is required if the body-serialisation
> branch checks `instanceof String`.

---

### 3. `CreateApplicationTest.java` — minimal diff

#### Class-level changes

| What | Old | New |
|---|---|---|
| Class declaration | `extends BaseIntegrationTest` | `extends BaseHarnessTest` |
| `@ActiveProfiles("test")` | Present | **Remove** |
| `@TestInstance(TestInstance.Lifecycle.PER_CLASS)` | Present | **Remove** — harness uses `PER_METHOD` default |
| Import `MvcResult` | Present | **Remove** |
| Import `BaseIntegrationTest` | Present | **Remove** |
| Import `BaseHarnessTest` | Absent | **Add** |
| Import `HarnessResult` | Absent | **Add** |

#### Per-method changes

| What | Old | New |
|---|---|---|
| `@WithMockUser(authorities = TestConstants.Roles.CASEWORKER)` | On all caseworker tests | **Remove** (default token handles it) |
| `@WithMockUser(authorities = TestConstants.Roles.UNKNOWN)` | On forbidden test | **Remove** + add `withToken(TestConstants.Tokens.UNKNOWN);` as first line |
| No `@WithMockUser` (no-auth test) | On `givenCorrectRequestBodyAndNoAuthentication_whenCreateApplication_thenReturnUnauthorised` | Add `withNoToken();` as first line |
| `MvcResult result` (all call sites) | Present | Replace with `HarnessResult result` |
| `applicationAsserts.assertErrorGeneratedByBadHeader(result, serviceName)` | Inspects `getResolvedException()` | Replace with `assertBadRequest(result)` |
| `assertEquals(0, applicationRepository.count())` | Asserts total DB row count | Replace with `assertTrue(createdApplications.isEmpty(), ...)` |
| `postUri(...)` return type | `MvcResult` | `HarnessResult` — call sites unchanged (method name is the same on `BaseHarnessTest`) |

#### Data teardown addition

Add a tracking list and `@AfterEach`:

```java
private final List<ApplicationEntity> createdApplications = new ArrayList<>();

@AfterEach
void tearDownApplicationData() {
    domainEventRepository.deleteAll(domainEventRepository.findAll()
        .stream()
        .filter(e -> createdApplications.stream()
            .anyMatch(a -> a.getId().equals(e.getApplicationId())))
        .toList());
    applicationRepository.deleteAll(createdApplications);
    createdApplications.clear();
}
```

In every test that creates an entity, add `createdApplications.add(...)` immediately after the
`applicationRepository.findById(...)` lookup or the `createAndPersist(...)` call:

```java
// verifyCreateNewApplication helper — after findById:
ApplicationEntity createdApplication = applicationRepository.findById(createdApplicationId)
    .orElseThrow(...);
createdApplications.add(createdApplication);          // ← new line

// givenCreateNewApplication_whenCreateApplicationWithLinkedApplication:
final ApplicationEntity leadApplicationToLink =
    persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class);
createdApplications.add(leadApplicationToLink);       // ← new line

// givenDuplicateApplyApplicationId:
ApplicationEntity existingApplication =
    persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class);
createdApplications.add(existingApplication);         // ← new line

// givenCreateNewApplication_whenCreateApplicationWithLinkedApplication_raiseIfAssociatedNotFound:
persistedDataGenerator.createAndPersist(
    ApplicationEntityGenerator.class,
    applicationEntityBuilder -> ...);
createdApplications.add(<returned entity>);           // ← new line
```

---

### 4. `@SmokeTest` annotation — which tests to mark

Following the convention from `GetApplicationTest` — mark tests that validate the core
request/response contract and are safe to run against a live deployment:

| Test | `@SmokeTest`? | Reason |
|---|---|---|
| `givenCreateNewApplication_whenCreateApplication_thenReturnCreatedWithLocationHeader` (both office variants) | ✅ | Core happy-path POST |
| `givenCreateNewApplication_whenCreateApplicationAndNoOffice_thenReturnCreatedWithLocationHeader` | ✅ | Happy path variant |
| `givenCreateNewApplication_whenCreateApplicationAndInvalidServiceNameHeader_thenReturnBadRequest` | ✅ | Header enforcement |
| `givenCreateNewApplication_whenCreateApplicationAndNoServiceNameHeader_thenReturnBadRequest` | ✅ | Header enforcement |
| `givenCreateNewApplication_whenCreateApplicationWithCivilDecideServiceName_thenReturnCreatedAndPersistServiceName` | ❌ | ServiceName variant — leave for integration only |
| `givenCreateNewApplication_whenCreateApplicationWithLinkedApplication_thenReturnCreatedWithLocationHeader` | ❌ | Complex linked data; leave for integration only |
| `givenCreateNewApplication_whenCreateApplicationWithLinkedApplication_raiseIfLeadNotFound` | ❌ | Error path with linked data |
| `givenCreateNewApplication_whenCreateApplicationWithLinkedApplication_raiseIfAssociatedNotFound` | ❌ | Error path with linked data |
| `givenDuplicateApplyApplicationId_whenCreateApplication_thenReturnBadRequest` | ❌ | Requires pre-existing data |
| `givenInvalidApplicationRequestData_whenCreateApplication_thenReturnBadRequest` (parameterised) | ❌ | Validation; no infra coverage |
| `givenInvalidApplicationContent_*` | ❌ | Validation; no infra coverage |
| `givenNoRequestBody_whenCreateApplication_thenReturnBadRequest` | ❌ | Validation; no infra coverage |
| `givenCorrectRequestBodyAndReaderRole_whenCreateApplication_thenReturnForbidden` | ❌ | Security simulation |
| `givenCorrectRequestBodyAndNoAuthentication_whenCreateApplication_thenReturnUnauthorised` | ❌ | Security simulation |

---

## Full Change Summary

### Files Changed

| File | Action |
|---|---|
| `utils/asserters/ResponseAsserts.java` | Add `HarnessResult` overloads for `assertCreated` and `assertProblemRecord` (two signatures) |
| `utils/harness/BaseHarnessTest.java` | Add `domainEventAsserts`, `applicationAsserts`, `domainEventRepository` fields; resolve in `setupHarness()`; add `postUri(String, T)` and `postUri(String, T, HttpHeaders)` overloads |
| `controller/application/CreateApplicationTest.java` | **Minimal diff** — swap base class, remove `@TestInstance`, `@ActiveProfiles`, `@WithMockUser`; add `withToken()`/`withNoToken()`; `MvcResult` → `HarnessResult`; add tracking list + `@AfterEach tearDownApplicationData()`; replace `assertErrorGeneratedByBadHeader` → `assertBadRequest`; replace `applicationRepository.count()` → `assertTrue(createdApplications.isEmpty())`; add `createdApplications.add(...)` after each entity creation |

---

## Decisions and Notes

### Why remove `@TestInstance(Lifecycle.PER_CLASS)`?

`PER_CLASS` was used in `BaseIntegrationTest` tests so that `@MethodSource` static factory
methods (`createApplicationTestParameters`, `applicationCreateRequestInvalidDataCases`) could
be instance methods (removing the `static` requirement). In `PER_CLASS` mode, JUnit creates
one test instance for all methods in the class.

The harness uses `PER_METHOD` (the JUnit default). To preserve the `@MethodSource` behaviour
without `PER_CLASS`, declare the provider methods `static`:

```java
private static Stream<Arguments> createApplicationTestParameters() { ... }
private static Stream<Arguments> applicationCreateRequestInvalidDataCases() { ... }
```

The bodies of these methods only call `DataGenerator.createDefault(...)` and build
`ProblemDetailBuilder` instances — none of which require `this`. Making them `static` is
safe and idiomatic.

### Why `assertTrue(createdApplications.isEmpty())` instead of `applicationRepository.count()`?

In a shared database (e.g. running against a real Postgres with other test classes touching
the same tables), a global `count()` is fragile — it will fail if any other test has left a
row. Checking that _this_ test's tracking list is empty is both more precise and more
resilient.

### Why `assertBadRequest` instead of `assertErrorGeneratedByBadHeader`?

`assertErrorGeneratedByBadHeader` inspects `MvcResult.getResolvedException()`, which is a
servlet-specific mechanism unavailable over real HTTP. The observable contract — a `400 Bad
Request` response — is fully covered by `assertBadRequest(result)`. If the exact error
message needs to be verified, it can be extracted from the response body (a `ProblemDetail`
JSON object) rather than from the servlet exception.

### Why is `HeaderUtils.GetUUIDFromLocation` unchanged?

`HeaderUtils.GetUUIDFromLocation(String location)` only manipulates a string. The `Location`
header is available on `HarnessResult` via `result.getResponse().getHeader("Location")`, which
is exactly how `assertCreated` already reads it. No change needed.

### `verifyCreateNewApplicationWithServiceName` — `domainEventAsserts` call

This private helper calls `domainEventAsserts.assertDomainEventForApplication(...)`.
`domainEventAsserts` will be a field on `BaseHarnessTest` (resolved from `harnessProvider`).
The call site is unchanged.

### `DomainEventAsserts` uses `domainEventRepository.findAll()`

In `BaseIntegrationTest` tests, each test is `@Transactional` so the `findAll()` only sees
rows created in the current transaction. In the harness, `findAll()` sees all rows in the DB.
This means `assertDomainEventForApplication` calling `domainEvents.getFirst()` could pick up
a stale event from a previous test if teardown isn't thorough.

The `@AfterEach` teardown above handles this by deleting domain events linked to
`createdApplications`. However, for a belt-and-braces approach, consider filtering
`domainEventRepository.findAll()` by the specific `application.getId()` inside the assert
method — but that is a change to `DomainEventAsserts` itself, which is out of scope for this
refactor.

