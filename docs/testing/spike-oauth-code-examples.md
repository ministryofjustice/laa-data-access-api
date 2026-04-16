# Code Examples: Option 1 (`mock-oauth2-server`) vs Option 3 (Self-Written)

Companion to [spike-oauth-token-testing.md](./spike-oauth-token-testing.md).

These examples show what it would look like to integrate each option into our existing
codebase. Both approaches are shown side-by-side for the same three contexts:
local integration tests, docker-compose, and consumer usage.

---

## Option 1: `mock-oauth2-server`

### 1a. Gradle dependency

```groovy
// data-access-service/build.gradle
testImplementation 'no.nav.security:mock-oauth2-server:2.1.10'
```

### 1b. Integration test — `IntegrationTestContextProvider` changes

The mock server runs in-process alongside the Testcontainers Postgres. The API's
OAuth config is pointed at it, so the full `SecurityConfig` validation chain runs.

```java
package uk.gov.justice.laa.dstew.access.utils.harness;

import no.nav.security.mock.oauth2.MockOAuth2Server;
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import uk.gov.justice.laa.dstew.access.AccessApp;
import uk.gov.justice.laa.dstew.access.Constants;

import java.util.List;
import java.util.Map;

public class IntegrationTestContextProvider implements TestContextProvider {

  private static final PostgreSQLContainer<?> postgreSQLContainer =
      new PostgreSQLContainer<>(Constants.POSTGRES_INSTANCE);

  // ── Mock OAuth server ─────────────────────────────────────────────────
  private static final MockOAuth2Server mockOAuth2Server = new MockOAuth2Server();

  static {
    postgreSQLContainer.start();
    mockOAuth2Server.start();  // starts on a random port
  }

  private final ConfigurableApplicationContext applicationContext;
  private final WebTestClient webTestClient;

  public IntegrationTestContextProvider() {

    // The mock server's issuer URL includes the issuer ID in the path.
    // "entra" is an arbitrary issuer ID — you can name it anything.
    String issuerUrl = mockOAuth2Server.issuerUrl("entra").toString();
    String jwksUrl = mockOAuth2Server.jwksUrl("entra").toString();

    applicationContext =
        new SpringApplicationBuilder(AccessApp.class)
            .web(org.springframework.boot.WebApplicationType.SERVLET)
            .run(
                "--server.port=0",
                "--spring.datasource.url=" + postgreSQLContainer.getJdbcUrl(),
                "--spring.datasource.username=" + postgreSQLContainer.getUsername(),
                "--spring.datasource.password=" + postgreSQLContainer.getPassword(),
                // Point OAuth config at the mock server instead of Entra
                "--spring.security.oauth2.resourceserver.jwt.issuer-uri=" + issuerUrl,
                "--spring.security.oauth2.resourceserver.jwt.jwk-set-uri=" + jwksUrl,
                "--spring.security.oauth2.resourceserver.jwt.audience=laa-data-access-api",
                // Dev token bypass is no longer needed — real JWTs are used
                "--feature.enable-dev-token=false",
                "--feature.disable-security=false");

    int port =
        applicationContext
            .getBean(Environment.class)
            .getRequiredProperty("local.server.port", Integer.class);

    webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
  }

  // ── Expose the mock server so TestTokenFactory can use it ─────────────

  public static MockOAuth2Server mockOAuth2Server() {
    return mockOAuth2Server;
  }

  @Override
  public WebTestClient webTestClient() {
    return webTestClient;
  }

  @Override
  public <T> T getBean(Class<T> type) {
    return applicationContext.getBean(type);
  }

  @Override
  public void close() {
    mockOAuth2Server.shutdown();
  }
}
```

### 1c. Token factory — minting tokens for tests

A utility that replaces `TestConstants.Tokens` with real, signed JWTs.

```java
package uk.gov.justice.laa.dstew.access.utils;

import no.nav.security.mock.oauth2.MockOAuth2Server;
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback;
import com.nimbusds.jwt.SignedJWT;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mints real signed JWTs from the mock OAuth2 server. These tokens exercise the
 * full SecurityConfig validation chain: signature verification, issuer check,
 * audience check, and role extraction.
 */
public class TestTokenFactory {

  private final MockOAuth2Server server;

  public TestTokenFactory(MockOAuth2Server server) {
    this.server = server;
  }

  /**
   * Mint a token with the given roles. The token will have:
   * - A valid signature (verifiable via the mock's JWKS endpoint)
   * - issuer = the mock server's "entra" issuer
   * - audience = "laa-data-access-api"
   * - roles claim = the provided roles
   * - LAA_APP_ROLES claim = comma-separated roles (required by SecurityConfig)
   * - sub = a random UUID (simulates a real user/service principal)
   */
  public String tokenWithRoles(String... roles) {
    SignedJWT jwt = server.issueToken(
        "entra",                          // issuer ID (matches IntegrationTestContextProvider)
        UUID.randomUUID().toString(),     // client ID
        new DefaultOAuth2TokenCallback(
            "entra",                      // issuer ID
            UUID.randomUUID().toString(), // subject
            List.of("laa-data-access-api"), // audience
            Map.of(
                "roles", List.of(roles),
                "LAA_APP_ROLES", String.join(",", roles)
            ),
            3600                          // expiry in seconds
        )
    );
    return jwt.serialize();
  }

  /** Convenience: token with LAA_CASEWORKER role. */
  public String caseworkerToken() {
    return tokenWithRoles("LAA_CASEWORKER");
  }

  /** Convenience: token with an unknown/unauthorised role. */
  public String unknownRoleToken() {
    return tokenWithRoles("UNKNOWN");
  }

  /** Token with no roles and no LAA_APP_ROLES — should be rejected by the audience validator. */
  public String tokenWithNoRoles() {
    SignedJWT jwt = server.issueToken(
        "entra",
        UUID.randomUUID().toString(),
        new DefaultOAuth2TokenCallback(
            "entra",
            UUID.randomUUID().toString(),
            List.of("laa-data-access-api"),
            Map.of("roles", List.of()),   // empty roles
            3600
        )
    );
    return jwt.serialize();
  }

  /** Token with wrong audience — should be rejected. */
  public String wrongAudienceToken() {
    SignedJWT jwt = server.issueToken(
        "entra",
        UUID.randomUUID().toString(),
        new DefaultOAuth2TokenCallback(
            "entra",
            UUID.randomUUID().toString(),
            List.of("some-other-api"),    // wrong audience
            Map.of(
                "roles", List.of("LAA_CASEWORKER"),
                "LAA_APP_ROLES", "LAA_CASEWORKER"
            ),
            3600
        )
    );
    return jwt.serialize();
  }
}
```

### 1d. Updated `TestConstants`

```java
package uk.gov.justice.laa.dstew.access.utils;

public class TestConstants {

  // ...existing URIs, MediaTypes, Roles...

  /**
   * Tokens are no longer hardcoded strings. Use TestTokenFactory to mint
   * real signed JWTs. These constants are kept for backward compatibility
   * during migration, but tests should move to TestTokenFactory.
   */
  public static class Tokens {
    // These will be replaced — see TestTokenFactory
    @Deprecated public static final String CASEWORKER = "swagger-caseworker-token";
    @Deprecated public static final String UNKNOWN = "unknown-token";
  }
}
```

### 1e. How a test changes

**Before (current — bypasses JWT validation):**
```java
// BaseHarnessTest defaults to TestConstants.Tokens.CASEWORKER = "swagger-caseworker-token"
// DevTokenConfig intercepts it, injects APPROLE_LAA_CASEWORKER, JWT filter never runs.

HarnessResult result = getUri("/api/v0/applications/{id}", applicationId);
assertThat(result.status()).isEqualTo(200);
```

**After (real JWT validation runs):**
```java
// In BaseHarnessTest, setupHarness() creates a TestTokenFactory from the mock server.
// The default token is now a real signed JWT with LAA_CASEWORKER role.

HarnessResult result = getUri("/api/v0/applications/{id}", applicationId);
assertThat(result.status()).isEqualTo(200);
// SecurityConfig.jwtDecoder() verified the signature, issuer, audience.
// SecurityConfig.jwtAuthenticationConverter() extracted ROLE_LAA_CASEWORKER from claims.
// @AllowApiCaseworker passed because @entra.hasAppRole('LAA_CASEWORKER') is true.
```

**Testing negative cases:**
```java
// Wrong role — should get 403
withToken(tokenFactory.unknownRoleToken());
HarnessResult result = getUri("/api/v0/applications/{id}", applicationId);
assertThat(result.status()).isEqualTo(403);

// Wrong audience — should get 401 (token rejected by jwtDecoder)
withToken(tokenFactory.wrongAudienceToken());
HarnessResult result = getUri("/api/v0/applications/{id}", applicationId);
assertThat(result.status()).isEqualTo(401);

// No token — should get 401
withNoToken();
HarnessResult result = getUri("/api/v0/applications/{id}", applicationId);
assertThat(result.status()).isEqualTo(401);
```

### 1f. Docker Compose for local development

```yaml
# docker-compose.yml
services:
  postgres:
    image: postgres:17
    container_name: laa-postgres
    restart: unless-stopped
    ports:
      - "5432:5432"
    environment:
      POSTGRES_DB: laa_data_access_api
      POSTGRES_USER: laa_user
      POSTGRES_PASSWORD: laa_password
    volumes:
      - pgdata:/var/lib/postgresql/data

  mock-oauth2:
    image: ghcr.io/navikt/mock-oauth2-server:2.1.10
    container_name: laa-mock-oauth2
    ports:
      - "8090:8090"
    environment:
      SERVER_PORT: 8090
      # Configure an issuer called "entra" with a specific issuer URL
      JSON_CONFIG: >
        {
          "interactiveLogin": false,
          "tokenCallbacks": [
            {
              "issuerId": "entra",
              "tokenExpiry": 3600,
              "requestMappings": [
                {
                  "requestParam": "grant_type",
                  "match": "client_credentials",
                  "claims": {
                    "aud": ["laa-data-access-api"],
                    "LAA_APP_ROLES": "LAA_CASEWORKER",
                    "roles": ["LAA_CASEWORKER"]
                  }
                }
              ]
            }
          ]
        }

volumes:
  pgdata:
```

Developers would then set in `~/.zshrc`:
```bash
export ENTRA_ISSUER_URI=http://localhost:8090/entra
export ENTRA_JWK_SET_URI=http://localhost:8090/entra/jwks
export ENTRA_AUD=laa-data-access-api
export FEATURE_ENABLE_DEV_TOKEN=false
export FEATURE_DISABLE_SECURITY=false
```

### 1g. Consumer usage — getting a token

A consuming team just needs one HTTP call:

```bash
# Request a token from the mock server with desired roles
curl -X POST http://localhost:8090/entra/token \
  -d "grant_type=client_credentials" \
  -d "scope=openid" \
  | jq -r '.access_token'
```

Or with custom roles for a specific test scenario:

```bash
# Request a token with specific claims
curl -X POST http://localhost:8090/entra/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "scope=openid" \
  | jq -r '.access_token'

# Use it to call the API
TOKEN=$(curl -s -X POST http://localhost:8090/entra/token \
  -d "grant_type=client_credentials" \
  -d "scope=openid" | jq -r '.access_token')

curl -H "Authorization: Bearer $TOKEN" \
     -H "X-Service-Name: CIVIL_APPLY" \
     http://localhost:9000/api/v0/caseworkers
```

### 1h. Kubernetes Helm values for UAT

```yaml
# .helm/data-access-api/values/uat.yaml (additions)
mockOauth2Server:
  enabled: true
  image: ghcr.io/navikt/mock-oauth2-server:2.1.10
  port: 8090

# The API's Entra config points at the mock
entra:
  issuerUri: http://mock-oauth2-server:8090/entra
  jwkSetUri: 
  audience: laa-data-access-api
```

---

## Option 3: Self-Written Token-Issuing Microservice

### 3a. Project structure

A new minimal module or standalone project:

```
mock-token-server/
├── Dockerfile
├── build.gradle
└── src/main/java/uk/gov/justice/laa/dstew/mocktoken/
    ├── MockTokenServerApp.java
    ├── JwksController.java
    ├── TokenController.java
    ├── DiscoveryController.java
    └── KeyProvider.java
```

### 3b. Key generation

```java
package uk.gov.justice.laa.dstew.mocktoken;

import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;

/**
 * Generates and holds an RSA key pair for the lifetime of the server.
 * The private key signs tokens; the public key is served via /jwks.
 */
public class KeyProvider {

  private final RSAKey rsaKey;

  public KeyProvider() {
    try {
      this.rsaKey = new RSAKeyGenerator(2048)
          .keyID("mock-key-1")
          .generate();
    } catch (Exception e) {
      throw new RuntimeException("Failed to generate RSA key", e);
    }
  }

  /** Full key pair (private + public) — used for signing. */
  public RSAKey signingKey() {
    return rsaKey;
  }

  /** Public key only — served via JWKS endpoint. */
  public RSAKey publicKey() {
    return rsaKey.toPublicJWK();
  }
}
```

### 3c. JWKS endpoint

```java
package uk.gov.justice.laa.dstew.mocktoken;

import com.nimbusds.jose.jwk.JWKSet;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the public key as a JWKS document.
 * Our API's SecurityConfig fetches this to verify JWT signatures.
 */
@RestController
public class JwksController {

  private final KeyProvider keyProvider;

  public JwksController(KeyProvider keyProvider) {
    this.keyProvider = keyProvider;
  }

  @GetMapping(value = "/jwks", produces = "application/json")
  public String jwks() {
    return new JWKSet(keyProvider.publicKey()).toString();
  }
}
```

### 3d. OIDC Discovery endpoint

```java
package uk.gov.justice.laa.dstew.mocktoken;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal OpenID Connect discovery document.
 * Spring's JwtValidators.createDefaultWithIssuer() fetches this
 * to discover the JWKS URI and validate the issuer.
 */
@RestController
public class DiscoveryController {

  @GetMapping(value = "/.well-known/openid-configuration", produces = "application/json")
  public Map<String, Object> discovery(HttpServletRequest request) {
    String baseUrl = request.getScheme() + "://" + request.getServerName()
        + ":" + request.getServerPort();

    return Map.of(
        "issuer", baseUrl,
        "authorization_endpoint", baseUrl + "/authorize",
        "token_endpoint", baseUrl + "/token",
        "jwks_uri", baseUrl + "/jwks",
        "response_types_supported", java.util.List.of("code"),
        "subject_types_supported", java.util.List.of("public"),
        "id_token_signing_alg_values_supported", java.util.List.of("RS256")
    );
  }
}
```

### 3e. Token endpoint

```java
package uk.gov.justice.laa.dstew.mocktoken;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.*;
import org.springframework.web.bind.annotation.*;

/**
 * Issues signed JWTs with configurable claims.
 * Consumers call POST /token with desired roles to get a valid bearer token.
 */
@RestController
public class TokenController {

  private final KeyProvider keyProvider;

  public TokenController(KeyProvider keyProvider) {
    this.keyProvider = keyProvider;
  }

  /**
   * Issue a token. Accepts either form-encoded (standard OAuth2) or JSON body.
   *
   * Example usage:
   *   curl -X POST http://localhost:8090/token \
   *     -H "Content-Type: application/json" \
   *     -d '{"roles": ["LAA_CASEWORKER", "ProceedingReader"]}'
   *
   * Or standard OAuth2 form:
   *   curl -X POST http://localhost:8090/token \
   *     -d "grant_type=client_credentials"
   */
  @PostMapping(value = "/token")
  public Map<String, Object> issueToken(
      HttpServletRequest request,
      @RequestBody(required = false) TokenRequest body) {

    String baseUrl = request.getScheme() + "://" + request.getServerName()
        + ":" + request.getServerPort();

    // Default roles if none specified
    List<String> roles = (body != null && body.roles() != null && !body.roles().isEmpty())
        ? body.roles()
        : List.of("LAA_CASEWORKER");

    String audience = (body != null && body.audience() != null)
        ? body.audience()
        : "laa-data-access-api";

    try {
      Instant now = Instant.now();

      JWTClaimsSet claims = new JWTClaimsSet.Builder()
          .issuer(baseUrl)
          .audience(audience)
          .subject(UUID.randomUUID().toString())
          .issueTime(Date.from(now))
          .expirationTime(Date.from(now.plusSeconds(3600)))
          .jwtID(UUID.randomUUID().toString())
          // Standard roles claim — read by JwtGrantedAuthoritiesConverter
          .claim("roles", roles)
          // Custom claim — required by SecurityConfig's audience validator
          .claim("LAA_APP_ROLES", String.join(",", roles))
          .build();

      JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
          .keyID(keyProvider.signingKey().getKeyID())
          .build();

      SignedJWT jwt = new SignedJWT(header, claims);
      jwt.sign(new RSASSASigner(keyProvider.signingKey()));

      return Map.of(
          "access_token", jwt.serialize(),
          "token_type", "Bearer",
          "expires_in", 3600
      );

    } catch (JOSEException e) {
      throw new RuntimeException("Failed to sign JWT", e);
    }
  }

  /** Request body for the token endpoint. */
  public record TokenRequest(
      List<String> roles,
      String audience
  ) {}
}
```

### 3f. Application class

```java
package uk.gov.justice.laa.dstew.mocktoken;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class MockTokenServerApp {

  public static void main(String[] args) {
    SpringApplication.run(MockTokenServerApp.class, args);
  }

  @Bean
  public KeyProvider keyProvider() {
    return new KeyProvider();
  }
}
```

### 3g. Dockerfile

```dockerfile
FROM eclipse-temurin:25-jre-alpine
COPY build/libs/mock-token-server.jar app.jar
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar", "--server.port=8090"]
```

### 3h. Integration test usage

The test utility would be similar to Option 1's `TestTokenFactory`, but instead of
calling the `MockOAuth2Server` Java API, it makes HTTP calls to the self-written
server (or embeds it in-process):

```java
package uk.gov.justice.laa.dstew.access.utils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * Fetches tokens from the self-written mock token server via HTTP.
 * Works identically whether the server is in-process, in Docker, or in Kubernetes.
 */
public class TestTokenFactory {

  private final String tokenEndpoint;
  private final HttpClient httpClient = HttpClient.newHttpClient();
  private final ObjectMapper objectMapper = new ObjectMapper();

  public TestTokenFactory(String mockServerBaseUrl) {
    this.tokenEndpoint = mockServerBaseUrl + "/token";
  }

  public String caseworkerToken() {
    return requestToken("{\"roles\": [\"LAA_CASEWORKER\"]}");
  }

  public String unknownRoleToken() {
    return requestToken("{\"roles\": [\"UNKNOWN\"]}");
  }

  public String wrongAudienceToken() {
    return requestToken("{\"roles\": [\"LAA_CASEWORKER\"], \"audience\": \"wrong-api\"}");
  }

  public String tokenWithRoles(String... roles) {
    String rolesJson = "[" + String.join(",",
        java.util.Arrays.stream(roles).map(r -> "\"" + r + "\"").toList()) + "]";
    return requestToken("{\"roles\": " + rolesJson + "}");
  }

  private String requestToken(String jsonBody) {
    try {
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(tokenEndpoint))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
          .build();

      HttpResponse<String> response = httpClient.send(request,
          HttpResponse.BodyHandlers.ofString());

      var tree = objectMapper.readTree(response.body());
      return tree.get("access_token").asText();

    } catch (Exception e) {
      throw new RuntimeException("Failed to request token from mock server", e);
    }
  }
}
```

### 3i. Docker Compose

```yaml
# docker-compose.yml
services:
  postgres:
    image: postgres:17
    # ...existing postgres config...

  mock-token-server:
    build:
      context: ./mock-token-server
      dockerfile: Dockerfile
    container_name: laa-mock-token-server
    ports:
      - "8090:8090"
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:8090/.well-known/openid-configuration || exit 1"]
      interval: 5s
      timeout: 3s
      retries: 5
```

### 3j. Consumer usage

```bash
# Request a token with specific roles
curl -X POST http://localhost:8090/token \
  -H "Content-Type: application/json" \
  -d '{"roles": ["LAA_CASEWORKER", "ProceedingReader"]}'

# Response:
# {
#   "access_token": 
#   "token_type": "Bearer",
#   "expires_in": 3600
# }

# Use it
TOKEN=$(curl -s -X POST http://localhost:8090/token \
  -H "Content-Type: application/json" \
  -d '{"roles": ["LAA_CASEWORKER"]}' | jq -r '.access_token')

curl -H "Authorization: Bearer $TOKEN" \
     -H "X-Service-Name: CIVIL_APPLY" \
     http://localhost:9000/api/v0/caseworkers
```

---

## Production risk analysis

Both options change how the API resolves its OAuth configuration. The central risk is:
**what happens if the mock server is accidentally used in production?**

### The attack scenario

If the API's `ENTRA_ISSUER_URI` and `ENTRA_JWK_SET_URI` pointed at a mock server in
production, anyone who could reach that mock could mint tokens with any role and call
the API as any user. This would be a complete authentication bypass.

### Option 1: `mock-oauth2-server` — risks

| Risk | Severity | Mitigation |
|---|---|---|
| **Mock server deployed to production namespace** | 🔴 Critical | The mock server is a separate Kubernetes deployment. It should only exist in Helm values for dev/UAT (`values/dev.yaml`, `values/uat.yaml`), **never** in `values/production.yaml`. A missing entry means nothing is deployed — fail-safe by default. |
| **Production API configured to point at mock** | 🔴 Critical | `ENTRA_ISSUER_URI` etc. are set per-environment in Helm values. Production values must point at real Entra. This is **the same risk that exists today** — a misconfigured `ENTRA_ISSUER_URI` in production is already catastrophic regardless of this spike. |
| **Mock server accidentally exposed on public network** | 🟡 Medium | In Kubernetes, the mock should be a `ClusterIP` service (no external ingress). Network policies should restrict access to the UAT namespace only. |
| **Dev token bypass left enabled** | 🟡 Medium | Option 1 **removes the need for `feature.enable-dev-token`** in integration tests. The flag defaults to `false` in `application.yml` and should remain `false` in production. This is actually an improvement — we remove a production-risky code path (`DevTokenConfig`) from the test flow. |
| **Someone copies UAT Helm values to production** | 🟡 Medium | PR review and CI linting. Could add a startup check: if `ENTRA_ISSUER_URI` contains `mock-oauth2` or `localhost`, log a `FATAL` error and refuse to start. |

**Net production risk vs today:** **Lower.** Today, the `DevTokenConfig` class is
compiled into the production JAR and activated by a single environment variable
(`FEATURE_ENABLE_DEV_TOKEN=true`). If that variable were set in production, anyone
could authenticate as a caseworker with `Authorization: Bearer swagger-caseworker-token`.
Option 1 makes `DevTokenConfig` unnecessary for testing, so it could eventually be
removed — eliminating that risk entirely.

### Option 3: Self-written microservice — risks

| Risk | Severity | Mitigation |
|---|---|---|
| **Same deployment/config risks as Option 1** | 🔴 Critical | Identical to Option 1 — the mock must not be deployed to production, and `ENTRA_*` vars must point at real Entra. Same mitigations apply. |
| **Bugs in the token server itself** | 🟡 Medium | If the self-written server has a bug (e.g. wrong JWKS format, incorrect claim encoding), tests might pass against the buggy server but fail against real Entra. You'd be testing your test infrastructure rather than your API. `mock-oauth2-server` is battle-tested; a self-written server is not. |
| **The self-written server is more code to audit** | 🟡 Low | ~300 lines of crypto/JWT code owned by the team. If a vulnerability is discovered in `nimbus-jose-jwt`, you need to patch it in two places (the API and the mock server). |
| **No community security review** | 🟡 Low | `mock-oauth2-server` is open-source with community scrutiny. A self-written server has only your team's eyes on it. Less important since it should never run in production, but defence-in-depth matters. |

**Net production risk vs today:** **Same as Option 1** for deployment risks. Slightly
higher overall because of the additional code surface area.

### Recommended safeguards (both options)

These are worth implementing regardless of which option is chosen:

1. **Startup guard in the API** — add a check in `SecurityConfig` or a `@PostConstruct`
   method: if `ENTRA_ISSUER_URI` contains `mock`, `localhost`, or a known non-production
   pattern, and the app is running with a production profile, log `ERROR` and fail to
   start.

   ```java
   @PostConstruct
   void validateOAuthConfig() {
     if (issuerUri.contains("mock") || issuerUri.contains("localhost")) {
       if (isProductionProfile()) {
         throw new IllegalStateException(
             "ENTRA_ISSUER_URI points at a mock server — refusing to start in production. "
             + "Value: " + issuerUri);
       }
       log.warn("ENTRA_ISSUER_URI points at a mock server: {}", issuerUri);
     }
   }
   ```

2. **Helm values separation** — the mock server deployment should only exist in
   `values/dev.yaml` and `values/uat.yaml`. `values/production.yaml` should have
   no reference to it. PR review catches accidental additions.

3. **Kubernetes network policy** — the mock server's `ClusterIP` service should only
   be accessible from within the namespace. No `Ingress`, no `LoadBalancer`.

4. **Remove `DevTokenConfig` eventually** — once the mock server is in place, the
   `DevTokenConfig` / `DevBearerTokenResolverConfig` bypass becomes unnecessary. Removing
   it eliminates a production-risky code path that is activated by a single env var.

---

## Side-by-side comparison

| Aspect | Option 1: `mock-oauth2-server` | Option 3: Self-written |
|---|---|---|
| **Lines of code to write** | ~20 lines (config + `TestTokenFactory`) | ~300 lines (5 classes + Dockerfile) |
| **Token minting in tests** | `server.issueToken(...)` — one method call | HTTP call to self-written `/token` endpoint |
| **OIDC compliance** | Built-in — discovery, JWKS, token endpoint all correct | Must be hand-coded — easy to get wrong |
| **Custom claims** | Supported via `DefaultOAuth2TokenCallback` `Map<String, Object>` | Full control — you write the `JWTClaimsSet` |
| **Container image** | `ghcr.io/navikt/mock-oauth2-server:2.1.10` (pre-built) | Must build and maintain your own |
| **Maintenance** | Version bump when NAV releases updates | Fix bugs, update dependencies, test the test infrastructure |
| **Consumer experience** | `POST /entra/token` — standard OAuth2 form params | `POST /token` — custom JSON body (or you implement form params too) |
| **Risk of subtle bugs** | Low — well-tested by NAV and community | Medium — OIDC discovery format, JWKS format, JWT encoding all need to be correct |
| **Flexibility** | Configurable but within the tool's API | Unlimited — you own every line |
