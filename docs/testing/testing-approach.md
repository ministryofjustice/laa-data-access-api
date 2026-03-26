# Testing Approach

## Philosophy

Our goal in testing is to **prove that the system works** . To do this we need multiple layers of testing. These are:

- Unit tests that check all business logic
- Integration tests that check the full stack from HTTP request to database and back
- End-to-end tests that run against a deployed environment.

(and more coming soon!)

We are consciously adopting a **classicist** approach to unit testing, where the "unit" is a behaviour (e.g. "create application") rather than a class (e.g. `ApplicationService`). Tests exercise the real collaborators of that behaviour, with mocks only at the external boundary (the repository). This means:
- A passing test suite is a genuine signal that the system works.
- Internal refactors do not require test changes as long as the behaviour is preserved.
- The tests will still be valid and meaningful when the codebase looks different in six months.

Implementation details change. Tests that are tightly coupled to implementation details change with them, which means:

- Changing application code **and** test code at the same time only tells you that your code is implemented a certain way, not that it behaves correctly.
- Testing each individual layer creates a maintenance burden that grows with the codebase, and for a team our size the effort rarely pays off.

The test suite we write should be a **stable contract** — tests that pass when the system works and fail when it doesn't, regardless of how the internals change.

We are also using a dual-test harness for integration and end-to-end tests, where the same test class can be run in "integration mode" against a local Testcontainers database, or in "infrastructure mode" against a deployed environment. This allows us to write comprehensive integration tests that cover all HTTP concerns and JPA correctness, and then select a subset of critical "smoke tests" to run against the deployed environment without needing to maintain a separate suite. 
More details on that are in the [dual mode test harness documentation](./dual-mode-test-harness.md).

A more detailed explanation of the classicist approach, and how it compares to the London school (mockist) approach, is provided at the bottom of the document.

---

## What we test and where

We have three distinct categories of test, each with a specific purpose.

### 1. Service unit tests (`src/test`)

**What they are:** Spring Boot tests that load the full application context but with all repositories replaced by Mockito mocks. Every other class — services, validators, mappers, security, exception handlers — is a real implementation.

**Base class:** `BaseServiceTest`

**What to test:**

| Concern | Example |
|---|---|
| All input/output combinations | Parameterised tests covering happy paths and every meaningful variation of the request |
| Validation | Every invalid input combination; assert the exact `ValidationException` message |
| Authorisation | Every role that should be denied access; assert `AuthorizationDeniedException` is thrown and no repository method is called |
| Business logic | All branching behaviour that the service is responsible for (e.g. linking applications, auto-grant logic, delegated functions) |
| Exception messages | Assert the exact exception type and message — these form part of the API contract |
| Repository interactions | Use `ArgumentCaptor` to assert that the correct entity was passed to the repository, with the correct field values, the correct number of times |
| Mapping from request DTO to entity | Assert every applicable property on the captured entity against the corresponding property on the request — required _and_ optional fields |

**What to mock:** Only the repository interfaces. Everything else is real.

**Why:** Running the full application context without a database allows fast, deterministic tests that cover all the business logic paths and prove that all the classes work together correctly. Mocking only the repository boundary means these tests genuinely exercise the code path that will run in production, up to the point of database I/O. These tests are the primary proof that our business logic is working.

**Naming convention:** `given<precondition>_when<action>_then<outcome>` — see [Baeldung's unit testing best practices](https://www.baeldung.com/java-unit-testing-best-practices#3-test-case-naming-convention). For example:

```
givenNewApplication_whenCreateApplication_thenReturnNewId
givenNewApplicationAndNotRoleReader_whenCreateApplication_thenThrowUnauthorizedException
givenDuplicateApplyApplicationId_whenCreateApplication_thenThrowValidationException
```

**Example structure:**

```java
@Test
public void givenNewApplicationAndNotRoleWriter_whenCreateApplication_thenThrowUnauthorizedException() {
    // given
    setSecurityContext(TestConstants.Roles.NO_ROLE);

    // when / then
    assertThatExceptionOfType(AuthorizationDeniedException.class)
        .isThrownBy(() -> serviceUnderTest.createApplication(...))
        .withMessageContaining("Access Denied");

    verify(applicationRepository, never()).save(any());
}
```

---

### 2. Mapper unit tests (`src/test`)

**What they are:** Lightweight unit tests that test mapper classes directly with `@ExtendWith(MockitoExtension.class)` and `@InjectMocks`.

**Base class:** `BaseMapperTest`

**What to test:**

| Concern | Example |
|---|---|
| Required field mapping | Assert each required field is mapped to the correct target field |
| Optional field mapping | Separate tests for null inputs, empty collections, and missing optional properties |
| Null safety | Verify that `null` input returns `null` (or whatever the mapper contract specifies) |
| All mapping directions | Entity → DTO, DTO → Entity, update mutations (e.g. `updateApplicationEntity`) |

**Why separate from service tests?** Mappers are the place where optional vs required field behaviour is most granular. Testing them separately means we can enumerate every combination of present/absent optional field without making the service tests unreadably long. It also gives a focused failure point when a mapping bug is introduced.

**Naming convention:** Same given/when/then format. For example:

```
givenApplicationEntity_whenToApplication_thenMapsFieldsCorrectly
givenApplicationWithNullCaseworker_whenToApplication_thenMapsFieldsCorrectlyWithNullCaseworker
givenNullApplicationEntity_whenToApplication_thenReturnsNull
givenEmptyApplicationUpdateRequest_whenUpdateApplicationEntity_thenMapperOnlyUpdatesMandatoryFields
```

---

### 3. Repository integration tests (`src/integrationTest`, `repository` package)

**What they are:** Spring Boot integration tests with a real Testcontainers Postgres database that test the JPA repository `save` and `get` methods in isolation.

**Base class:** `BaseIntegrationTest`

**What to test:**

| Concern | Example |
|---|---|
| All entity fields are persisted and retrieved correctly | Save a fully-populated entity, clear the JPA cache, fetch it back, assert equality |
| Aggregate roots | Saving child entities (e.g. `IndividualEntity`) via the parent repository (e.g. `ApplicationRepository`) |
| Database-generated fields | After save, assert fields like `id`, `createdAt`, `modifiedAt` are **not null** |
| Entity equality | Use `usingRecursiveComparison().ignoringFields("createdAt", "modifiedAt")` for the field-by-field comparison; assert the ignored fields separately with `isNotNull()` |

**Why are these necessary?** The controller integration tests (described below) and the test data factories both depend on being able to save and retrieve entities reliably. If a repository is broken, errors will appear across many tests in misleading ways. These tests pin down the persistence layer so that failures are caught at the right level.

**Example:**

```java
@Test
public void givenSaveOfExpectedCaseworker_whenGetCalled_expectedAndActualAreEqual() {
    // given
    CaseworkerEntity expected = persistedDataGenerator.createAndPersist(CaseworkerGenerator.class);
    clearCache();

    // when
    CaseworkerEntity actual = caseworkerRepository.findById(expected.getId()).orElse(null);

    // then
    assertThat(expected)
        .usingRecursiveComparison()
        .ignoringFields("createdAt", "modifiedAt")
        .isEqualTo(actual);
    assertThat(expected.getCreatedAt()).isNotNull();
    assertThat(expected.getModifiedAt()).isNotNull();
}
```

---

### 4. Controller integration tests (`src/integrationTest`, `controller` package)

**What they are:** Full end-to-end tests that send real HTTP requests through the running application, backed by a real Testcontainers Postgres database. The whole application stack — controller, security, validation, service, mapper, repository, database — is exercised in a single test.

**Base class:** `BaseHarnessTest`

**What to test:**

| Concern | Example |
|---|---|
| Every HTTP response code for each endpoint | 200, 201, 400, 401, 403, 404 |
| Every property of every response body | Assert each field in the JSON response against the persisted entity or the request DTO |
| Security headers | Assert standard security headers are present on every response (`assertSecurityHeaders`) |
| Validation error responses | Every field-level validation rule; assert the exact `invalidFields` map in the `ProblemDetail` response |
| Authentication and authorisation | No token → 401, wrong role → 403, correct role → 2xx |
| Business rules that produce error responses | Linking to a non-existent application, duplicate apply IDs, etc. |
| Domain events | After a write operation, assert the correct `DomainEventEntity` was persisted with the correct type, data, and service name |
| Service name header | Missing, invalid, and valid values |
| No data leakage | After each test, assert no unexpected data remains in the database |

**Why?** These tests prove that the HTTP layer and JPA layer are working correctly. They are the closest we can get to real usage without a deployed environment.

**Naming convention:** Same given/when/then format. The "given" clause describes both the pre-persisted state and the request, making it easy to read logs:

```
givenCreateNewApplication_whenCreateApplication_thenReturnCreatedWithLocationHeader
givenCorrectRequestBodyAndNoAuthentication_whenCreateApplication_thenReturnUnauthorised
givenDuplicateApplyApplicationId_whenCreateApplication_thenReturnBadRequest
```

---

## Smoke tests and infrastructure mode

Controller integration tests can be annotated with `@SmokeTest`. This annotation controls which tests run when the suite is executed in **infrastructure mode** against a real deployed environment.

| Mode | System property | What runs |
|---|---|---|
| Integration (default) | _(none)_ | All tests |
| Infrastructure | `-Dtest.mode=infrastructure` | `@SmokeTest` tests only |

Smoke tests should cover the most critical happy paths and the most likely failure modes in a deployed environment (e.g. basic auth, a successful create, a successful get). They should not require test data setup that assumes a blank database, since the infrastructure database may already contain data.

See [integration-test-harness.md](dual-mode-test-harness.md) for full details of the harness architecture.

---

## Test data

Test data is generated using generator classes (e.g. `ApplicationEntityGenerator`, `ApplicationCreateRequestGenerator`). These use a builder pattern and can be customised per-test:

```java
ApplicationEntity entity = DataGenerator.createDefault(ApplicationEntityGenerator.class, builder ->
    builder.status(ApplicationStatus.APPLICATION_SUBMITTED)
           .laaReference("LAA-123")
);
```

Generators live in `src/testUtilities` so they can be shared between the unit test (`src/test`) and integration test (`src/integrationTest`) source sets.

For controller integration tests, `PersistedDataGenerator` wraps the generators and tracks every entity that is persisted, so that all test data can be deleted in `@AfterEach` teardown. This is what keeps each test isolated without relying on `@Transactional` rollback (which cannot be used when requests are made over HTTP).

---

## What we deliberately do not test in isolation

The following concerns are covered *within* the service unit tests and controller integration tests rather than as standalone layer tests:

- **Validation rules** — tested as part of the service unit tests (exception path) and as part of the controller integration tests (HTTP 400 response)
- **Authorisation annotations** — tested as part of the service unit tests (security context) and controller integration tests (HTTP 401/403 response)
- **Exception handler** — exercised end-to-end by the controller integration tests; the `GlobalExceptionHandlerTest` and `ResponseEntityExceptionHandlerAdviceTest` exist to cover edge cases that are difficult to trigger via HTTP

The rationale: for a codebase and team of our size, testing every layer independently creates more test code than application code, requires tests to be updated every time the implementation changes, and provides false confidence via line/branch coverage metrics that are better measured against the service-level tests. If the system works end-to-end, that is the proof that matters.

---

## Summary table

| Test type | Source set | Base class | DB | Mock? | Primary concern |
|---|---|---|---|---|---|
| Service unit | `src/test` | `BaseServiceTest` | None | Repositories only | Business logic, validation, authorisation, entity mapping, repository interactions |
| Mapper unit | `src/test` | `BaseMapperTest` | None | None | All mapping combinations, optional/required fields |
| Repository integration | `src/integrationTest` | `BaseIntegrationTest` | Testcontainers | None | All entity fields persisted and retrieved correctly |
| Controller integration | `src/integrationTest` | `BaseHarnessTest` | Testcontainers | None | Full HTTP contract, response bodies, security, domain events |

---

## Worked examples

The following examples walk through exactly which tests to write (and which to skip) for the most common development scenarios:

| Scenario | Document |
|---|---|
| Adding a new field to existing functionality (e.g. `CreateApplication`) | [worked-example-new-field.md](./worked-example-new-field.md) |
| Adding new behaviour to existing functionality (e.g. `MakeDecision` granted path) | [worked-example-new-behaviour.md](./worked-example-new-behaviour.md) |
| Adding a new service with new entities (e.g. Document upload/retrieval) | [worked-example-new-service.md](./worked-example-new-service.md) |
| Refactoring existing implementation without changing behaviour | [worked-example-refactoring.md](./worked-example-refactoring.md) |

---

## Classicist vs London school: a comparison

There are two well-established schools of thought on how to write unit tests. These are:

### The London school (mockist / interaction-based)

Every class is tested in strict isolation. All collaborators — services, mappers, validators, even simple helper classes — are replaced with mocks. Tests assert on the *interactions* between objects: which methods were called, with what arguments, how many times.

**Pros:**
- Failures are immediately localised to a single class
- Tests are easy to read and understand in isolation — you can see the exact inputs and outputs for the class under test without needing to understand the behaviour of its collaborators
- Encourages designing classes with clearly defined interfaces and dependencies
- Can make it easier to test a class before its collaborators are written

**Cons:**
- Tests are coupled to the *implementation*, not the *behaviour* — changing how something is wired internally breaks tests even if the system still works correctly
- Produces a large volume of mock setup code that often mirrors the implementation, making it hard to read what the test is actually asserting
- Refactoring becomes expensive: tests have to be rewritten whenever classes are extracted, merged, or reorganised, even if no user-visible behaviour changes
- Mock verification (`verify(mock).method(...)`) can give false confidence — it proves a method was called, not that the right thing happened as a result
- Coverage metrics can be misleading: 100% line coverage across isolated class tests does not mean the classes work together
- Particularly problematic in an iterative, evolving codebase where the internal structure is expected to change frequently

**Key risk:** The tests become a description of the current implementation rather than a specification of the required behaviour. When the implementation changes — which it will — you end up changing application code and test code together. At that point the tests are not proving anything about the system or whether the change you made has broken anything; they are just reflecting the code you have written. The test suite becomes a maintenance burden that provides little value, and over time the team loses confidence in it as a signal of whether the system works.

---

### The classicist school (unit of behaviour / Detroit school)

The unit under test is a *behaviour*, not a *class*. Tests exercise a slice of the application through real collaborators, mocking only at a meaningful external boundary — in our case, the repository (database I/O). Tests assert on *outcomes*: what was returned, what was persisted, what exception was thrown.

**Pros:**
- Tests remain valid across internal refactors — as long as the behaviour is correct, the tests pass
- A failing test means the *system* is broken, not just that a class was wired differently
- Less test code: one test exercises many classes, so the total volume of tests is lower
- The test suite becomes a living specification of the system's behaviour, readable by anyone on the team
- Easier to maintain during iterative development — tests only need to change when behaviour changes, not when implementation changes
- Line/branch coverage is meaningful because it is measured against paths that are actually reachable through real usage

**Cons:**
- When a test fails it may be less immediately obvious *which* class is the source of the problem (though in a small codebase this is rarely a significant issue in practice)
- Requires real implementations of all collaborators to exist before a test can be written. This can be mitigated by writing tests in the order of the expected development flow (e.g. start with a service test that covers the behaviour you want to implement, then implement the collaborators as needed).
- Can produce slower test feedback if the "unit of behaviour" grows very large, though in our setup the service tests run without a database and remain fast

**Key risk:** Without discipline, the "unit of behaviour" can grow to encompass too much, making individual tests hard to understand. This is mitigated by keeping services focused on a single responsibility and using parameterised tests to enumerate combinations rather than writing one enormous test.

---

### Why we use the classicist approach

Our service unit tests are classicist by design. The full Spring context is loaded, all real collaborators are wired together, and only the repositories are mocked. This means:

- A passing test suite is a genuine signal that the system works.
- Internal refactors — splitting a service, extracting a helper, changing a mapper — do not require test changes as long as the behaviour is preserved.
- The tests will still be valid and meaningful when the codebase looks different in six months.

The London school can be valuable in specific contexts — particularly for shared library code or when working in very large teams where isolating failure to a single class genuinely saves significant debugging time. For a small team, on a single application, where every developer has a vertical understanding of the codebase, that trade-off does not apply. The classicist approach gives us the same confidence with a fraction of the maintenance cost.

#### The relationship between London school tests and integration tests

There is a telling tension between the London school and integration tests that makes the case for the classicist approach clearly.

Integration tests (controller → database) are the ultimate proof that the system works. The question is whether they cover every combination of input and output — every validation path, every error case, every business logic branch.

Either:

1. **The integration tests do cover every combination.** In that case, the London school unit tests for each individual class are pure duplication. They assert the same outcomes that the integration tests already assert, but through mocks rather than the real stack. Any failure caught by a London school test would also be caught by the integration test. You are paying the full authoring and maintenance cost of two test suites for the same coverage.

Or:

2. **The integration tests do not cover every combination.** In that case there are gaps in the integration test suite — combinations of input and output that are not proven to work end-to-end. The London school tests may cover those paths in isolation, but they only prove that a class behaves a certain way given a particular mock setup. They do not prove that the system works. The gaps remain.

The classicist service unit tests resolve this tension directly. They exercise all combinations of input and output through real collaborators — no mocks except the repository boundary — at the same time as the integration tests exercise the full HTTP stack. Together they provide complete coverage without duplication: the service tests cover all the logic branches and edge cases fast and cheaply; the integration tests confirm the full stack wires together correctly for the most important paths. Neither set of tests is redundant, and neither leaves gaps that require a third layer to fill.

---

### On bugs that only mockist tests would find

a scenario worth considering is: a bug exists in a classes internal logic, the classicist tests pass because the bug happens to be cancelled out by something elsewhere in the real collaborator chain, and the system therefore behaves correctly. A mockist test for that class in isolation would have found the bug because the cancelling collaborator is mocked away.

**First, it is genuinely unlikely.** For a classicist test to pass in the presence of a real bug, the bug must be exactly compensated by a coincidental behaviour in another real collaborator. In practice this is rare — bugs tend to produce wrong outputs, which the classicist tests assert on. The scenario is theoretically possible but not a realistic everyday risk.

**Second, and more importantly, the question is whether the bug matters — not whether it exists.** A bug that does not affect the observable behaviour of the system is not a bug in any meaningful sense for application code. The system does what it is supposed to do. If the classicist tests pass, we have proven that. The internal implementation could be written in any number of ways and the external behaviour is what we have contracted to deliver.

A genuine risk is around code re-use: if the buggy class were extracted into a shared library and consumed by another team or service without the compensating collaborator, the bug would surface. That is a real concern — Shared library code has a different testing contract: the public API of the library describes *its* units of behaviour, and they should be tested as such in isolation. Deciding to re-use a feature will also warrant a set of tests on the features API if it does not exist. This does not necessarily need to be written now - it can be written at the point of re-use, when the risk is real and the testing contract is clear.

In summary: **for this codebase, a bug that classicist tests do not catch is a bug that does not affect the system's behaviour.** That is a tolerable risk given our team size, our delivery pace, and the maintenance cost of the alternative.

