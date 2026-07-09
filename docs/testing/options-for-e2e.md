# Options for E2E testing

## 1. What each layer is proving

### Integration test layer

The integration tests prove that the **assembled application behaves correctly as a system** within a controlled, ephemeral environment. Specifically they verify:

- The full Spring Boot application context starts and wires correctly (controller → service → repository → JPA → PostgreSQL)
- Every HTTP endpoint returns the correct status code, headers (`X-XSS-Protection`, `X-Frame-Options`, `Cache-Control`, `Content-Type`), and response body shape
- Business logic enforced by the service layer is observable through the HTTP contract (e.g. FK validation when linking applications, domain event creation on caseworker assignment)
- Input validation rejects malformed requests with the correct problem+json error shape
- Security and authentication: unauthorised (no token), forbidden (wrong role), and authorised paths all behave correctly
- Database persistence is correct: rows written by the API are exactly what the data model expects (asserted by querying the repository directly after an HTTP call)
- FK-constrained relationships between entities are correctly maintained (applications→caseworkers, applications→decisions, proceedings→merits_decisions etc.)
- The Flyway schema migrations produce a consistent, queryable structure

The key characteristic is that the test sends a **real HTTP request** to a **real running server** backed by a **real (Testcontainers) database**. There is no mocking anywhere in the path. This is what distinguishes it from a unit or slice test.

### End-to-end test layer

These tests prove that the **deployed system in a real environment is correctly configured and operational**. They verify:

- The API process is alive and reachable at `LAA_ACCESS_API_URL`
- TLS, load balancer, and network routing are working
- The running application's database connection (`LAA_ACCESS_DB_URL`) is valid — JPA can write and read rows
- Flyway migrations have been applied correctly to the production schema (same entity model maps)
- Authentication configuration is live (tokens are accepted/rejected correctly)
- Critical request routing (header validation, 404 paths) matches expectations in the deployed config
- Feature flags and environment-specific configuration are correct

This layer is *additive and destructive by design*: some components of the architecture will be called by features which add/remove/update rows in the database.
Any data added or changed must be cleaned up after the test to prevent pollution of the environment and interference with other tests. This is a key difference from the integration layer, where the database is ephemeral and can be wiped between tests without risk.

---

## 2. The problem to solve

We need confidence that:

1. **The code is correct** — every time a change is made, automated tests must verify that the full application stack (HTTP → service → JPA → database) behaves as specified.
2. **A deployment is correct** — after releasing to any environment, tests must verify that the deployed system is alive, correctly configured, and wired to a working database — without damaging real data.

The challenge is not just satisfying both goals. It is **satisfying both goals without creating a maintenance burden that grows with every API change**.

### Why maintenance is the central risk

Every endpoint added, every field changed, every schema migration applied must be reflected in the test pack. If integration tests and E2E tests are separate artefacts — different tools, different codebases, different assertion styles — then every change must be made twice. In practice this means:

- **Two codebases must be kept in sync, and divergence is hard to detect.** Every change to an endpoint or schema must be applied in both packs. Even with diligent maintenance, the two packs have no shared language — a field rename that breaks the integration tests may produce a subtly different failure in the E2E pack, or no failure at all if that path was never covered there. The gap between what the two packs are actually testing is not visible without reading both in detail.
- **Coverage gaps compound over time.** A new endpoint or schema change adds coverage to the integration pack automatically when the new tests are written. The E2E pack only gains that coverage if a parallel update is made. Over time, the E2E pack tests a narrowing subset of what the integration pack tests, without anything making that gap explicit.
- **Understanding the full picture requires holding two systems in your head.** Every investigation into a test failure, every change to an endpoint, every schema migration requires knowing how both packs are structured and where they overlap.

**Goal 1** is well served by a standard `@SpringBootTest` integration suite. Data cleanup is straightforward because the Testcontainers database is ephemeral.

**Goal 2** requires hitting a real deployed environment over HTTP, against a real persistent database that may contain UAT or production data. A test suite that is safe and correct for Goal 1 can be catastrophic for Goal 2:

- Any cleanup strategy that uses `deleteAll()`, `TRUNCATE`, or unscoped `DELETE FROM` will wipe real data
- `@Transactional` rollback cannot work because HTTP requests execute on separate server threads with their own committed transactions
- Search and list tests that assert on exact row counts will produce false results when other data exists in the database

### The core constraints

Any solution must satisfy all of the following:

| Constraint | Reason                                                                                                          |
|---|-----------------------------------------------------------------------------------------------------------------|
| Tests must exercise real HTTP | Proves routing, auth filters, serialisation, and the servlet container are wired correctly                      |
| Tests must exercise real DB writes | Proves Flyway migrations, JPA mappings, and FK relationships are correct in the target environment              |
| Cleanup must be surgical (delete only test-owned rows) | The tests must run safely against a database containing other data (test data for UAT, other clients, etc) |
| Test isolation must be guaranteed | A failed test must not leave data that corrupts a subsequent test                                               |

Note that any test that needs to **update** data (e.g. `PATCH /applications/{id}`) must also be able to clean up after itself.
This is easiest done by ensuring the test creates the data it needs, tracks the IDs, and deletes those specific IDs at teardown.
This mitigates risk where existing data fails the test because new fields/validations have been added since the data was created and ensures
tests are isolated.

---

## 3. Approaches compared

### Approach A — The current dual-harness (recommended)

**How it works:**
`BaseHarnessTest` owns a `@BeforeEach`/`@AfterEach` lifecycle. `PersistedDataGenerator` is the single gateway for database writes; it tracks every entity ID in per-type `List<UUID>` fields. `deleteTrackedData()` deletes in FK-order after each test. `DatabaseCleanlinessAssertion` provides a belt-and-braces `@AfterEach @Order(2)` check that every table is empty.

**Integration mode:** One Spring Boot context + one Testcontainers Postgres shared across the entire suite (stored in the JUnit root store). Tests hit the real HTTP stack.

**Infrastructure mode:** Only the JPA layer is wired up. `WebTestClient` targets `LAA_ACCESS_API_URL`. Teardown runs identically.

| Quality | Assessment |
|---|---|
| **Solves the problem** | ✅ One codebase serves both goals; surgical teardown is safe in production; `@SmokeTest` annotation selects the E2E subset without duplication |
| Proves full HTTP stack | ✅ Real request → real server → real DB |
| Safe for real environments | ✅ Surgical delete-by-tracked-ID only |
| Reuses test code for smoke tests | ✅ Same class, same method, `@SmokeTest` annotation |
| Test isolation | ✅ Per-test teardown + cleanliness assertion |
| Maintenance cost | ⚠️ Every new entity type requires: registration in `generatorRepoMap`, tracking logic in `track()`, and FK-aware ordering in `deleteTrackedData()` |
| FK complexity | ⚠️ Must be manually maintained; V14 reversal required explicit JDBC pre-fetch of `decision_id` |
| Schema evolution cost | ⚠️ Adding a new table needs `DatabaseCleanlinessAssertion.TABLES`, `PersistedDataGenerator.deleteTrackedData()`, and `track()` all updated together |
| Parallel execution | ⚠️ Shared context; per-test data isolation relies on tracked IDs, not schema separation |

---

### Approach B — Separate Postman/Newman (or REST-Assured) collection for smoke tests

**How it works:**
A separate test pack (e.g. Postman collection, REST-Assured suite, or Karate DSL) is maintained for HTTP endpoint verification in deployed environments. Teardown is typically done via explicit DELETE calls or a test-management endpoint.

Low-code tools such as Postman are attractive for this because they lower the barrier to writing HTTP tests. However, they create a second source of truth that must track every change to the Java API, and they introduce practical friction around complex assertions — particularly those involving dynamically generated UUIDs, FK relationships, and versioned schema — that the Java integration suite handles natively.

| Quality | Assessment |
|---|---|
| **Solves the problem** | ❌ Does not solve it — creates a second source of truth for the same API contract; teardown is rarely safe for production; schema changes must be maintained in two places |
| Proves full HTTP stack | ✅ Real HTTP |
| Safe for real environments | ⚠️ Only if teardown is implemented carefully — most Postman-style suites skip this |
| Reuses test code for smoke tests | ❌ Entirely separate codebase and toolchain to maintain |
| Data isolation | ⚠️ Usually achieved by using known test IDs or ignoring cleanup; highly inconsistent |
| Maintenance cost | ❌ High — two sources of truth for the same API contract |
| Schema evolution | ❌ Schema changes must be reflected in both test packs |
| Developer experience | ❌ Non-Java toolchain breaks the mental model; contract drift goes undetected |

---

### Approach C — Contract testing (e.g. Pact)

**How it works:**
Consumer-driven contracts are verified against provider stubs or the live API. The consumer publishes a pact; the provider verifies it.

| Quality | Assessment |
|---|---|
| **Solves the problem** | ❌ Does not solve it — does not prove DB wiring or infrastructure configuration; requires consumer adoption that does not currently exist; cannot serve as an E2E deployment check |
| Proves full HTTP stack | ⚠️ Provider verification runs against the real app but with fixed/mocked data |
| Safe for real environments | ✅ No additive data — provider verifies against controlled state |
| Reuses test code for smoke tests | ❌ Different paradigm; not a drop-in |
| Proves end-to-end DB wiring | ❌ Provider tests typically mock or stub the DB layer |
| Maintenance cost | ⚠️ Moderate — pact files must be versioned and published |

Note PACT tests will be created for the project but they are not a substitute for E2E smoke tests hitting the real API and database. They serve a different purpose: proving that the API contract is correctly implemented against consumer expectations, but not proving that the deployed system is correctly configured and wired to a working database.

---

### Approach D — `@Sql` reset scripts per test class

**How it works:**
`@Sql(scripts = "classpath:cleanup.sql", executionPhase = AFTER_TEST_METHOD)` runs a fixed SQL script after each test to truncate or delete rows.

| Quality | Assessment |
|---|---|
| **Solves the problem** | ❌ Does not solve it — script-based teardown cannot be made safe for a real database; two separate test packs would still be needed for E2E coverage |
| Proves full HTTP stack | ✅ Compatible with `WebTestClient` |
| Safe for real environments | ❌ `TRUNCATE` or `DELETE FROM` without a WHERE clause would wipe production tables |
| Reuses test code for smoke tests | ❌ Script-based teardown cannot be made safe for real data |
| FK complexity | ⚠️ Script must maintain delete order; any new table requires script update |
| Maintenance cost | ⚠️ SQL scripts are outside the type-safe Java model; schema drift goes undetected until runtime |

---

### Summary

| Dimension | Current harness (A) | Separate Postman suite (B) | Contract / Pact (C) | `@Sql` reset scripts (D) |
|---|---|---|---|---|
| **Solves the problem** | ✅ one codebase, low ongoing cost | ❌ doubles maintenance | ❌ requires consumer adoption, no DB coverage | ❌ unsafe for real environments |
| Proves real HTTP stack | ✅ | ✅ | ⚠️ | ✅ |
| Proves DB wiring | ✅ | ⚠️ indirect | ❌ | ✅ |
| Safe for real environments | ✅ surgical | ⚠️ varies | ✅ | ❌ |
| Dual-use (integration + smoke) | ✅ one codebase | ❌ two codebases | ❌ | ❌ |
| Catches FK/schema evolution issues | ✅ | ❌ | ❌ | ⚠️ at runtime |
| Maintenance overhead | ⚠️ track every entity type | ❌ high (dual) | ⚠️ pact versioning | ⚠️ SQL drift |
| Gaps | See §5 | Drift, no teardown guarantee | No DB coverage | Not safe for real envs |

---

## 4. Recommended option — the dual-harness approach

### Design intent

Both goals are served by the **same test code**. A single test method carrying `@SmokeTest` runs:

- In CI against Testcontainers to prove correctness of the code
- In a deployment pipeline against a real environment to prove correctness of the configuration

This eliminates an entire parallel test pack that would need to be maintained alongside the integration tests. The contract being tested is identical because it is literally identical code.

```
Same test method
   ├── runs with IntegrationTestContextProvider  → Testcontainers + full Spring Boot
   └── runs with InfrastructureTestContextProvider → real DB + real API URL
```

### How it works

#### Mode selection

`HarnessExtension` is a JUnit 5 extension that selects the `TestContextProvider` implementation based on the `test.mode` system property and stores it in the JUnit root store — meaning one context is shared across the entire test suite, not recreated per class.

| Mode | System property | Spring context | Database |
|---|---|---|---|
| **Integration** (default) | _(none)_ | Full `AccessApp` boot | Testcontainers Postgres — ephemeral |
| **Infrastructure** | `-Dtest.mode=infrastructure` | Minimal JPA-only context | Real environment DB at `LAA_ACCESS_DB_URL` |

In infrastructure mode, only `@SmokeTest`-annotated tests are executed; everything else is skipped by `HarnessExtension`'s `ExecutionCondition`. `@SmokeTest` can be applied at class or method level.

#### Test lifecycle

Every test class extends `BaseHarnessTest`, which owns the full lifecycle:

```
@BeforeEach setupHarness()
  ├── resolves beans from the shared context (repositories, generators, asserters)
  ├── clearTrackedIds()                    ← defensive reset before every test
  ├── createAndPersist(CaseworkerJohnDoe)  ← auto-tracked
  └── createAndPersist(CaseworkerJaneDoe)  ← auto-tracked

@Test method body
  └── createAndPersist(...) / postUri(...) ← each persist auto-tracked

@AfterEach @Order(1) tearDownTrackedData()
  └── persistedDataGenerator.deleteTrackedData()
        ├── JDBC pre-fetch decision_id for each tracked application
        ├── delete tracked domain_events   (by ID, idempotent)
        ├── delete tracked applications    (cascade removes proceedings, certificates, linked rows)
        ├── delete tracked decisions       (after application, FK allows it)
        ├── delete tracked caseworkers     (by ID, idempotent)
        ├── delete tracked individuals     (by ID, idempotent)
        └── clearTrackedIds()              ← always runs, even if a delete threw

@AfterEach @Order(2) assertDatabaseCleanAfterTest()
  └── DatabaseCleanlinessAssertion.assertAllTablesEmpty()
        └── counts every domain table; throws AssertionError if anything remains
```

#### Data tracking

`PersistedDataGenerator` is the single gateway through which tests write to the database. Every call to `createAndPersist(...)` or `createAndPersistMultiple(...)`:

1. Calls `repository.saveAndFlush(entity)` — the row is committed and immediately visible to the HTTP server
2. Calls `track(entity)` — the entity's UUID is added to the appropriate in-memory list

When the production API creates rows (e.g. via `POST /applications`), the test must call `persistedDataGenerator.trackExistingApplication(id)` so that the API-created rows are also cleaned up at teardown.

#### Infrastructure mode context

`InfrastructureTestContextProvider` boots only a minimal JPA context — no embedded server, no full Spring Boot startup. It connects directly to the real database for teardown writes, while `WebTestClient` is bound to `LAA_ACCESS_API_URL` for the HTTP calls. Required environment variables:

| Variable | Purpose |
|---|---|
| `LAA_ACCESS_API_URL` | Base URL of the live API under test |
| `LAA_ACCESS_DB_URL` | JDBC URL of the live database |
| `LAA_ACCESS_DB_USERNAME` | Database username |
| `LAA_ACCESS_DB_PASSWORD` | Database password |

### How it mitigates the key risks

| Risk | Mitigation |
|---|---|
| **Data leakage between tests** | `DatabaseCleanlinessAssertion` runs `@AfterEach @Order(2)` — fails immediately on the offending test, not silently in a later one |
| **Data destruction in production** | `deleteTrackedData()` only deletes IDs registered via `PersistedDataGenerator`; `findById().ifPresent(repo::delete)` is idempotent and surgical — it cannot touch rows it did not create |
| **FK violation on delete** | `deleteTrackedData()` deletes in explicit leaf-to-root order: domain_events → applications (cascade) → decisions → caseworkers → individuals |
| **Orphaned decisions (V14 FK reversal)** | `applications.decision_id → decisions` means ON DELETE CASCADE does not remove decisions; JDBC pre-fetches `decision_id` for each tracked application before deletion, then removes decisions after their parent application is gone |
| **API-created rows not tracked** | `trackExistingApplication(UUID)` handles rows created by the API; also JDBCs `linked_individuals` to capture individual IDs that cascade-delete would not reach |
| **Stale IDs from a previous failed teardown** | `clearTrackedIds()` is always called in a `finally` block; `@BeforeEach` makes a second defensive call before each test |
| **Harness deleting data it should not** | `HarnessDataIsolationTest` uses a sentinel strategy — rows persisted outside `PersistedDataGenerator` survive every `@AfterEach` teardown, proving the invariant |
| **Infrastructure context startup cost** | `InfrastructureTestContextProvider` starts only the JPA layer; no full application boot is required to run smoke tests |
| **Auth misconfiguration in real env** | Every endpoint has `@SmokeTest` tests asserting 401 (no token) and 400 (bad header) responses, verifying the auth layer is correctly wired in the deployed environment |
| **Maintenance burden** | A single codebase covers both layers; an API change is reflected in one place and immediately applies to both integration and E2E smoke coverage |

---

## 5. Gaps in the current implementation

### 5.1 `DomainEventAsserts` is not safe in infrastructure mode

`DomainEventAsserts.assertDomainEventsCreatedForApplications()` calls `domainEventRepository.findAll()` and asserts on the **total count**. In infrastructure mode, other concurrent processes may have written domain events before or during the test. A `findAll()` assertion will produce false negatives.

**Fix needed:** Scope the query to the specific application IDs tracked in the current test (e.g. `findByApplicationIdIn(trackedIds)`).

---

### 5.2 No `@SmokeTest` coverage for write operations that mutate state

The current `@SmokeTest` annotations are concentrated on read operations (`GET /applications`, `GET /application/{id}`) and header validation. Destructive/additive tests (`POST /applications`, `PATCH /applications/{id}`, `PATCH /applications/{id}/decision`, `POST /caseworkers/assign`) are largely **not** marked `@SmokeTest`.

This means infrastructure mode only proves the API is alive and headers are validated — it does not prove that writes reach the database, that domain events are emitted, or that FK relationships are enforced in the live schema.

**Fix needed:** At least one `@SmokeTest` write test per mutating endpoint, scoped to the minimal happy-path scenario. These tests already exist as integration tests — they simply need `@SmokeTest` added after verifying teardown is safe in infrastructure mode (see 5.1 and 5.3).

---

### 5.3 `trackExistingApplication()` must be called manually by write-path tests

When a test uses `postUri(CREATE_APPLICATION, ...)` and the API creates a row, that row is not tracked until the test code calls `persistedDataGenerator.trackExistingApplication(createdId)`. If a write-path `@SmokeTest` forgets this call, the row is left behind in the live database permanently.

Looking at `CreateApplicationTest`, `verifyCreateNewApplication()` does extract the UUID from the `Location` header and call `trackExistingApplication()` — so it is handled. But this is a convention, not an enforcement. A new test author writing a write-path `@SmokeTest` has no compile-time or runtime guard if they forget.

**Fix needed:** Consider a post-test assertion that cross-checks `trackedApplicationIds` against the count of applications in the database attributed to the test's known data, or document the requirement explicitly in `BaseHarnessTest` Javadoc.

---

### 5.4 Search tests cannot be safely made `@SmokeTest`

Most `GetApplicationsTest` methods assert on exact counts and ordering. In infrastructure mode, other data in the live database would invalidate these assertions. No search test is currently annotated `@SmokeTest`, and making them so naïvely would produce flaky results.

**Fix needed:** For infrastructure mode, search tests would need to filter by a test-owned `laaReference` prefix or `officeCode` that is guaranteed unique to the test run (e.g. a UUID prefix set in `@BeforeEach`). This is an architectural gap — there is currently no mechanism to scope search results to test-owned data.

---

### 5.5 `InfrastructureTestContextProvider` does not register assertion helpers

`InfrastructureJpaConfig` does not register `ApplicationAsserts` or `DomainEventAsserts` as beans. `BaseHarnessTest.setupHarness()` resolves both from `harnessProvider.getBean(...)`. In infrastructure mode this will throw a `NoSuchBeanDefinitionException` if any `@SmokeTest` calls `applicationAsserts` or `domainEventAsserts`.

Currently, `@SmokeTest` methods that use these helpers are avoided in practice, but this is fragile.

**Fix needed:** Register `ApplicationAsserts` and `DomainEventAsserts` in `InfrastructureJpaConfig`'s `@ComponentScan`, or move them to a shared component module that both context providers scan.

---

### 5.6 No end-of-suite cleanliness gate for infrastructure mode

`DatabaseCleanlinessAssertion.assertAllTablesEmpty()` runs `@AfterEach` in integration mode, where the database is ephemeral. In infrastructure mode the database is real, permanent, and likely non-empty. Running `assertAllTablesEmpty()` in infrastructure mode would always fail.

The `@AfterEach` assertion is not currently guarded by mode — if `@SmokeTest` methods ever use all assertion helpers and teardown runs correctly, the cleanliness check would still fire against non-empty production tables.

**Fix needed:** Guard `assertDatabaseCleanAfterTest()` with a mode check (e.g. check whether the active `TestContextProvider` is an `IntegrationTestContextProvider`) and skip the assertion in infrastructure mode. Replace it with a mode-appropriate check: verify that the specific tracked rows have been deleted, rather than asserting global table emptiness.

---

### 5.7 Security header assertions may not be valid through a reverse proxy

Several `@SmokeTest` tests assert `assertSecurityHeaders(result)` which checks `X-XSS-Protection` and `X-Frame-Options`. These headers may differ between the embedded server (integration mode) and the live server if a reverse proxy or WAF strips or rewrites them.

**Risk:** Security header tests may pass in integration mode but fail (or worse, silently pass with wrong values) in infrastructure mode due to proxy behaviour.

**Fix needed:** Decide whether infrastructure-mode security header assertions should target the proxy-layer headers or be excluded from `@SmokeTest`; document this decision.
