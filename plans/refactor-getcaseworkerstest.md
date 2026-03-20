# Refactor Plan: `GetCaseworkersTest` → Harness-Based Test (Minimal-Diff Revision)

## Goal

Port `GetCaseworkersTest` to run via the harness **while keeping the test body as close to
unchanged as possible**. Other developers are actively editing the other integration tests, so
we avoid sweeping changes. Instead, we introduce bridge infrastructure (`BaseHarnessTest`,
`HarnessResult`) that mirrors the `BaseIntegrationTest` API surface, then make only the
unavoidable changes inside the test itself.

---

## Context

`GetCaseworkersTest` currently extends `BaseIntegrationTest`, which boots a full Spring context
via `@SpringBootTest` + `@ContextConfiguration(PostgresContainerInitializer)`, uses `MockMvc`
for in-process HTTP, `@Transactional` for automatic data rollback, and `@WithMockUser` for
security simulation.

The harness (`HarnessExtension`) boots a real server on a random port and provides a
`WebTestClient`. The two transport mechanisms are fundamentally different, so we cannot avoid
**all** changes in the test — but we can reduce the diff to the absolute minimum.

---

## Key Differences to Bridge

| Concern              | `BaseIntegrationTest`                         | Harness                                          |
|----------------------|-----------------------------------------------|--------------------------------------------------|
| Transport            | `MockMvc` (in-process)                        | `WebTestClient` (real HTTP over port)            |
| Security             | `@WithMockUser` (Spring Security test mock)   | Real `Authorization: Bearer` header over HTTP    |
| Data teardown        | `@Transactional` auto-rollback per test       | Manual `deleteAll` in `@AfterEach`               |
| Bean injection       | `@Autowired`                                  | `@HarnessInject`                                 |
| Container lifecycle  | `PostgresContainerInitializer` (static)       | `IntegrationTestContextProvider` (per extension) |
| Error body access    | `MvcResult.getResolvedException()`            | Response body only (no servlet internals)        |

---

## Authentication Strategy

`DevTokenConfig` and `DevBearerTokenResolverConfig` are both annotated
`@ConditionalOnProperty(prefix = "feature", name = "enable-dev-token", havingValue = "true")`.
When active, the literal bearer token `swagger-caseworker-token` bypasses OAuth2 JWT validation
and injects `APPROLE_LAA_CASEWORKER` into the security context.

- **Caseworker role:** `Authorization: Bearer swagger-caseworker-token`
- **Unknown role:** Any token string not in `DEV_TOKENS` (e.g. `"Bearer unknown-token"`) → OAuth2 rejects it → `403 Forbidden`
- **No user:** Omit the `Authorization` header entirely → `401 Unauthorized`

---

## Minimal-Diff Strategy

Rather than rewriting the test to use `WebTestClient` directly, we introduce two bridging types:

### `HarnessResult`

A thin wrapper that exposes the same methods the existing assert helpers and test code
call on `MvcResult`:

```java
// The test and helper methods currently call:
result.getResponse().getStatus()          // → int
result.getResponse().getHeader(name)      // → String
result.getResponse().getContentAsString() // → String
```

`HarnessResult` provides these via an inner `Response` class so the existing `ResponseAsserts`
static helpers (`assertOK`, `assertForbidden`, `assertUnauthorised`, `assertSecurityHeaders`,
`assertBadRequest`) and inline `objectMapper.readValue(result.getResponse().getContentAsString(), ...)`
all **compile and run unchanged**.

### `BaseHarnessTest`

Abstract base that mirrors the `BaseIntegrationTest` API:

- `@HarnessInject` fields for `WebTestClient`, `CaseworkerRepository`, `PersistedDataGenerator`,
  `ObjectMapper`
- Same `CaseworkerJohnDoe`, `CaseworkerJaneDoe`, `Caseworkers` field names (instance, not static,
  because each harness test class gets its own context)
- `@BeforeEach setupCaseworkers()` / `@AfterEach tearDownCaseworkers()` replacing `@Transactional`
- `getUri(String uri)` — issues GET with `X-Service-Name: CIVIL_APPLY` + caseworker bearer token
- `getUri(String uri, HttpHeaders headers)` — same but with supplied headers (null = no headers)
- `ServiceNameHeader(String serviceName)` — identical signature to `BaseIntegrationTest`
- `withToken(String token)` — sets the bearer token for the next `getUri()` call (replaces `@WithMockUser`)
- `withNoToken()` — omits the `Authorization` header for the next `getUri()` call (replaces no-`@WithMockUser`)

The token resets to the caseworker default after each `getUri()` call, so tests that need a
different security context only need one extra line before the call.

---

## Changes Required

### 1. `IntegrationTestContextProvider.java` — add `--feature.enable-dev-token=true`

```java
// Before
applicationContext = new SpringApplicationBuilder(AccessApp.class)
    .web(org.springframework.boot.WebApplicationType.SERVLET)
    .run(
        "--server.port=0",
        "--spring.datasource.url=" + postgreSQLContainer.getJdbcUrl(),
        "--spring.datasource.username=" + postgreSQLContainer.getUsername(),
        "--spring.datasource.password=" + postgreSQLContainer.getPassword()
    );

// After
applicationContext = new SpringApplicationBuilder(AccessApp.class)
    .web(org.springframework.boot.WebApplicationType.SERVLET)
    .run(
        "--server.port=0",
        "--spring.datasource.url=" + postgreSQLContainer.getJdbcUrl(),
        "--spring.datasource.username=" + postgreSQLContainer.getUsername(),
        "--spring.datasource.password=" + postgreSQLContainer.getPassword(),
        "--feature.enable-dev-token=true"
    );
```

---

### 2. `InfrastructureTestContextProvider.java` — fix placeholder package references

```java
// Before
@EnableJpaRepositories(basePackages = "com.benchmark.infra.jpa.repository")
@ComponentScan(basePackages = "com.benchmark.infra.jpa.adapter")
static class InfrastructureJpaConfig {
    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
        factory.setPackagesToScan("com.benchmark.infra.jpa.entity");
    }
}

// After
@EnableJpaRepositories(basePackages = "uk.gov.justice.laa.dstew.access.repository")
@ComponentScan(basePackages = "uk.gov.justice.laa.dstew.access")
static class InfrastructureJpaConfig {
    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
        factory.setPackagesToScan("uk.gov.justice.laa.dstew.access.entity");
    }
}
```

---

### 3. `TestConstants.java` — add `Tokens` inner class

```java
public static class Tokens {
    public static final String CASEWORKER = "swagger-caseworker-token";
}
```

---

### 4. `HarnessResult.java` — create

A thin façade over a real-HTTP response that mimics the `MvcResult.getResponse()` surface used
by `ResponseAsserts` and test code. Lives in the harness package.

```
src/integrationTest/java/uk/gov/justice/laa/dstew/access/utils/harness/HarnessResult.java
```

```java
package uk.gov.justice.laa.dstew.access.utils.harness;

import java.util.Map;

/**
 * Mimics the MvcResult.getResponse() surface so ResponseAsserts static helpers
 * (assertOK, assertForbidden, assertSecurityHeaders, etc.) work unchanged.
 */
public class HarnessResult {

    private final int status;
    private final Map<String, String> headers;
    private final String body;

    public HarnessResult(int status, Map<String, String> headers, String body) {
        this.status = status;
        this.headers = headers;
        this.body = body;
    }

    public Response getResponse() {
        return new Response();
    }

    public class Response {
        public int getStatus()                { return status; }
        public String getHeader(String name)  { return headers.get(name); }
        public String getContentAsString()    { return body; }
    }
}
```

---

### 5. `BaseHarnessTest.java` — create

```
src/integrationTest/java/uk/gov/justice/laa/dstew/access/utils/harness/BaseHarnessTest.java
```

```java
package uk.gov.justice.laa.dstew.access.utils.harness;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.repository.CaseworkerRepository;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;
import uk.gov.justice.laa.dstew.access.utils.builders.HttpHeadersBuilder;
import uk.gov.justice.laa.dstew.access.utils.generator.PersistedDataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.caseworker.CaseworkerGenerator;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@ExtendWith(HarnessExtension.class)
public abstract class BaseHarnessTest {

    @HarnessInject
    protected WebTestClient webTestClient;

    @HarnessInject
    protected CaseworkerRepository caseworkerRepository;

    @HarnessInject
    protected PersistedDataGenerator persistedDataGenerator;

    @HarnessInject
    protected ObjectMapper objectMapper;

    // Mirror BaseIntegrationTest field names so test code compiles unchanged.
    // Instance fields (not static) because each harness test class has its own context.
    protected CaseworkerEntity CaseworkerJohnDoe;
    protected CaseworkerEntity CaseworkerJaneDoe;
    protected List<CaseworkerEntity> Caseworkers;

    // Token used by getUri(); defaults to caseworker, reset after each call.
    private String currentToken = TestConstants.Tokens.CASEWORKER;
    private boolean omitToken = false;

    @BeforeEach
    void setupCaseworkers() {
        currentToken = TestConstants.Tokens.CASEWORKER;
        omitToken = false;
        CaseworkerJohnDoe = persistedDataGenerator.createAndPersist(
                CaseworkerGenerator.class, b -> b.username("JohnDoe").build());
        CaseworkerJaneDoe = persistedDataGenerator.createAndPersist(
                CaseworkerGenerator.class, b -> b.username("JaneDoe").build());
        Caseworkers = List.of(CaseworkerJohnDoe, CaseworkerJaneDoe);
    }

    @AfterEach
    void tearDownCaseworkers() {
        caseworkerRepository.deleteAll(Caseworkers);
    }

    // ── Auth helpers (replace @WithMockUser) ─────────────────────────────────

    /** The next getUri() call will use this token instead of the caseworker default. */
    protected void withToken(String token) {
        this.currentToken = token;
        this.omitToken = false;
    }

    /** The next getUri() call will omit the Authorization header (→ 401). */
    protected void withNoToken() {
        this.omitToken = true;
    }

    // ── HTTP helpers — same signatures as BaseIntegrationTest ─────────────────

    /** GET uri with X-Service-Name: CIVIL_APPLY and the current bearer token. */
    public HarnessResult getUri(String uri) {
        return getUri(uri, defaultServiceNameHeader());
    }

    /**
     * GET uri with the supplied headers and the current bearer token.
     * Passing null omits all extra headers (no X-Service-Name).
     */
    public HarnessResult getUri(String uri, HttpHeaders headers) {
        WebTestClient.RequestHeadersSpec<?> spec = webTestClient.get().uri(uri);

        if (!omitToken) {
            spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + currentToken);
        }
        if (headers != null) {
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
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
        String body = raw.getResponseBody() == null
                ? "" : new String(raw.getResponseBody(), StandardCharsets.UTF_8);

        return new HarnessResult(status, responseHeaders, body);
    }

    /** Mirrors BaseIntegrationTest.ServiceNameHeader(). */
    public HttpHeaders ServiceNameHeader(String serviceName) {
        HttpHeadersBuilder headersBuilder = new HttpHeadersBuilder();
        return (serviceName == null) ? null : headersBuilder.withServiceName(serviceName).build();
    }

    private HttpHeaders defaultServiceNameHeader() {
        return new HttpHeadersBuilder().withServiceName("CIVIL_APPLY").build();
    }
}
```

---

### 6. `ResponseAsserts.java` — add `HarnessResult` overloads

`ResponseAsserts` static helpers currently accept `MvcResult`. We need them to also accept
`HarnessResult`. The cleanest approach without touching the existing overloads is to add
parallel overloads for `HarnessResult`:

```java
// Add these alongside the existing MvcResult versions:

public static void assertSecurityHeaders(HarnessResult response) {
    assertEquals("0", response.getResponse().getHeader("X-XSS-Protection"));
    assertEquals("DENY", response.getResponse().getHeader("X-Frame-Options"));
}

public static void assertOK(HarnessResult response) {
    assertEquals(HttpStatus.OK.value(), response.getResponse().getStatus());
}

public static void assertBadRequest(HarnessResult response) {
    assertEquals(HttpStatus.BAD_REQUEST.value(), response.getResponse().getStatus());
}

public static void assertForbidden(HarnessResult response) {
    assertEquals(HttpStatus.FORBIDDEN.value(), response.getResponse().getStatus());
}

public static void assertUnauthorised(HarnessResult response) {
    assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getResponse().getStatus());
}
```

> **Alternative:** Because `HarnessResult.Response` has the exact same method signatures as
> `MockHttpServletResponse`, the existing `MvcResult`-based helpers could instead be extracted
> to accept a common interface. But that would touch more files. The overload approach is safer
> with other developers in flight.

---

### 7. `GetCaseworkersTest.java` — minimal diff

The **only changes** needed vs. the current file:

| What | Old | New |
|---|---|---|
| Class declaration | `extends BaseIntegrationTest` | `extends BaseHarnessTest` |
| Class-level annotation | `@ActiveProfiles("test")` | Remove (no-op for harness) |
| Auth on caseworker tests | `@WithMockUser(authorities = TestConstants.Roles.CASEWORKER)` | Remove — `getUri()` sends caseworker token by default |
| Auth on unknown-role test | `@WithMockUser(authorities = TestConstants.Roles.UNKNOWN)` | Remove + add `withToken("unknown-token");` as first line of body |
| Auth on no-user test | *(no annotation — already relies on absence)* | Add `withNoToken();` as first line of body |
| Private helper | `applicationAsserts.assertErrorGeneratedByBadHeader(result, serviceName)` | `assertBadRequest(result)` |
| Return type of `getUri()` calls | `MvcResult result` | `HarnessResult result` |
| Import | `import ... MvcResult` | `import ... HarnessResult` |

Everything else — `getUri()`, `ServiceNameHeader()`, `assertOK()`, `assertForbidden()`,
`assertUnauthorised()`, `assertSecurityHeaders()`, `objectMapper.readValue(...)`,
`assertCaseworkerListEquals()`, `Caseworkers` — is **identical**.

**Full rewritten class:**

```java
package uk.gov.justice.laa.dstew.access.controller.caseworker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.model.Caseworker;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;
import uk.gov.justice.laa.dstew.access.utils.harness.BaseHarnessTest;
import uk.gov.justice.laa.dstew.access.utils.harness.HarnessResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertBadRequest;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertSecurityHeaders;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertOK;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertForbidden;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertUnauthorised;

public class GetCaseworkersTest extends BaseHarnessTest {

    @Test
    void givenRoleReaderAndNoHeader_whenGetCaseworkers_thenReturnBadRequest() throws Exception {
        verifyServiceNameHeader(null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "invalid-header", "CIVIL-APPLY", "civil_apply"})
    void givenRoleReaderAndIncorrectHeader_whenGetCaseworkers_thenReturnBadRequest(
            String serviceName
    ) throws Exception {
        verifyServiceNameHeader(serviceName);
    }

    private void verifyServiceNameHeader(String serviceName) throws Exception {
        HarnessResult result = getUri(TestConstants.URIs.GET_CASEWORKERS, ServiceNameHeader(serviceName));
        assertBadRequest(result);
    }

    @Test
    public void givenRoleReader_whenGetCaseworkers_thenReturnOk() throws Exception {
        // given
        // two caseworkers created in BaseHarnessTest data setup.

        // when
        HarnessResult result = getUri(TestConstants.URIs.GET_CASEWORKERS);
        List<Caseworker> actualCaseworkers = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new com.fasterxml.jackson.core.type.TypeReference<List<Caseworker>>() {}
        );

        // then
        assertSecurityHeaders(result);
        assertOK(result);
        assertCaseworkerListEquals(actualCaseworkers, Caseworkers);
    }

    @Test
    public void givenUnknownRole_whenGetCaseworkers_thenReturnForbidden() throws Exception {
        withToken("unknown-token");
        HarnessResult result = getUri(TestConstants.URIs.GET_CASEWORKERS);

        assertSecurityHeaders(result);
        assertForbidden(result);
    }

    @Test
    public void givenNoUser_whenGetCaseworkers_thenReturnUnauthorised() throws Exception {
        withNoToken();
        HarnessResult result = getUri(TestConstants.URIs.GET_CASEWORKERS);

        assertSecurityHeaders(result);
        assertUnauthorised(result);
    }

    private void assertCaseworkerListEquals(List<Caseworker> caseworkers, List<CaseworkerEntity> entities) {
        // No size check — real infrastructure may return pre-existing caseworkers.
        // Only assert that each caseworker created by this test is present in the response.
        for (CaseworkerEntity entity : entities) {
            assertThat(caseworkers).anyMatch(c ->
                    entity.getId().equals(c.getId()) &&
                    entity.getUsername().equals(c.getUsername()));
        }
    }
}
```

---

## Notes

### Why `HarnessResult` instead of just using `WebTestClient` assertions inline?

Because the test currently uses `assertOK(result)`, `assertSecurityHeaders(result)`, and
`objectMapper.readValue(result.getResponse().getContentAsString(), ...)`. Replacing all of
those with `WebTestClient` fluent API would touch every line of the test body — exactly what
we want to avoid. `HarnessResult` lets us keep those calls verbatim.

### Why overloads in `ResponseAsserts` instead of an interface?

Other developers are in-flight on tests that use the existing `MvcResult` overloads. Extracting
a shared interface would require touching all callers. Adding parallel `HarnessResult` overloads
leaves existing code untouched.

### Why `withToken()` / `withNoToken()` instead of a parameter on `getUri()`?

Adding a token parameter to `getUri()` would require changing every call site. The "set before
call, auto-reset after" pattern adds only one extra line per affected test and doesn't touch
the default-caseworker tests at all.

### `assertErrorGeneratedByBadHeader` → `assertBadRequest`

`assertErrorGeneratedByBadHeader` inspects `MvcResult.getResolvedException()` — a servlet
internal not available over real HTTP. The assertion is reduced to `assertBadRequest(result)`.
The detailed exception-message assertion is already covered by the existing
`BaseIntegrationTest`-based tests and does not need to be duplicated in the harness path.

---

## Files Changed

| File | Action |
|---|---|
| `utils/harness/IntegrationTestContextProvider.java` | Add `--feature.enable-dev-token=true` boot arg |
| `utils/harness/InfrastructureTestContextProvider.java` | Fix `com.benchmark.infra.*` package references |
| `utils/TestConstants.java` | Add `Tokens.CASEWORKER = "swagger-caseworker-token"` |
| `utils/harness/HarnessResult.java` | **Create** — thin façade mimicking `MvcResult.getResponse()` surface |
| `utils/harness/BaseHarnessTest.java` | **Create** — mirrors `BaseIntegrationTest` API with same field/method names |
| `utils/asserters/ResponseAsserts.java` | Add `HarnessResult` overloads for the five assert helpers |
| `controller/caseworker/GetCaseworkersTest.java` | **Minimal diff** — swap base class, remove `@WithMockUser`/`@ActiveProfiles`, add `withToken()`/`withNoToken()`, `MvcResult` → `HarnessResult`, `assertErrorGeneratedByBadHeader` → `assertBadRequest` |
