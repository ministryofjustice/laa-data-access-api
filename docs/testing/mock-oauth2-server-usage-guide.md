# Mock OAuth2 Server Usage Guide

**Overview:** How to obtain and use test tokens with `mock-oauth2-server` across different environments

---

## What is mock-oauth2-server?

`mock-oauth2-server` is an open-source mock OAuth2/OIDC server that issues **real signed JWTs** for testing. Unlike disabling security, it exercises the full Spring Security validation chain—signature verification, issuer checks, audience validation, and role extraction—giving high confidence that authentication works correctly.

**Key benefits:**
- Issues real signed JWTs that validate through Spring Security's standard `JwtDecoder`
- No client secrets to manage
- Works across all environments (local dev, integration tests, UAT, CI/CD)
- Trivial role configuration via claims

---

## How It Works: Step-by-Step

### The Token Flow in UAT/Pipeline Smoke Tests

When running smoke tests against a deployed API with mock-oauth2-server:

```
┌─────────────────┐
│  Test Script    │
│  (curl/HTTP)    │
└────────┬────────┘
         │ 1. Request token
         ▼
┌─────────────────────────────┐
│  mock-oauth2-server         │
│  (Docker container)         │
│  - Generates RSA key pair   │
│  - Signs JWT with claims    │
│  - Exposes JWKS endpoint    │
└────────┬────────────────────┘
         │ 2. Returns signed JWT
         ▼
┌─────────────────┐
│  Test Script    │
│  Stores token   │
└────────┬────────┘
         │ 3. Makes API request with
         │    Authorization: Bearer <token>
         ▼
┌─────────────────────────────┐
│  API Application            │
│  - Receives JWT             │
│  - Fetches JWKS from mock   │
│  - Validates signature      │
│  - Checks issuer/audience   │
│  - Extracts roles           │
└─────────────────────────────┘
```

**Step-by-step breakdown:**

1. **Mock server starts** and generates an RSA key pair
2. **Test script requests a token** via HTTP POST to `http://mock-oauth2-server:9999/entra/token`
3. **Mock server signs a JWT** with the requested claims (roles, audience, subject)
4. **Mock server returns the JWT** in the response
5. **Test script uses the JWT** in the `Authorization: Bearer` header when calling the API
6. **API validates the token:**
   - Downloads the public key from `http://mock-oauth2-server:9999/entra/jwks`
   - Verifies the JWT signature matches
   - Checks the issuer is `http://mock-oauth2-server:9999/entra`
   - Checks the audience claim contains `laa-data-access-api`
   - Extracts roles from the `roles` claim
7. **API processes the request** with the authenticated user context

---

## Environment-Specific Usage

### 1. Integration Tests (Testcontainers)

**How it works:** Mock server runs **in-process** within the test JVM as a library.

**Location:** `IntegrationTestContextProvider.java`

**Setup (already implemented):**

```java
private static final MockOAuth2Server mockOAuth2Server = new MockOAuth2Server();

static {
    mockOAuth2Server.start();  // Starts on random port
}

// Configure Spring to trust the mock server
String issuerUrl = mockOAuth2Server.issuerUrl("entra").toString();
String jwksUrl = mockOAuth2Server.jwksUrl("entra").toString();

applicationContext = new SpringApplicationBuilder(AccessApp.class)
    .run(
        "--spring.security.oauth2.resourceserver.jwt.issuer-uri=" + issuerUrl,
        "--spring.security.oauth2.resourceserver.jwt.jwk-set-uri=" + jwksUrl
    );
```

**Getting a token:**

```java
// Via TestTokenFactory
TestTokenFactory tokenFactory = new TestTokenFactory(mockOAuth2Server());
String token = tokenFactory.caseworkerToken();

// Use in test
webTestClient.get()
    .uri("/api/access/caseworkers")
    .header("Authorization", "Bearer " + token)
    .exchange()
    .expectStatus().isOk();
```

**Available token types:**
- `caseworkerToken()` - Valid token with `LAA_CASEWORKER` role
- `unknownRoleToken()` - Token with unauthorized role → 403
- `tokenWithNoRoles()` - Token without `LAA_APP_ROLES` claim → 401
- `wrongAudienceToken()` - Token with wrong audience → 401

**When to use:**
- All integration tests that need authentication
- Fast execution (in-process, no network)
- Full Spring Security validation

---

### 2. Local Development (docker-compose)

**How it works:** Mock server runs as a **Docker container** alongside Postgres.

**Location:** `docker-compose.yml`

**Setup (already implemented):**

```yaml
services:
  mock-oauth2-server:
    image: ghcr.io/navikt/mock-oauth2-server:2.1.10
    container_name: laa-mock-oauth2
    ports:
      - "9999:9999"
    environment:
      SERVER_PORT: 9999
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
                    "sub": "local-dev-user",
                    "aud": ["laa-data-access-api"],
                    "roles": ["LAA_CASEWORKER"],
                    "LAA_APP_ROLES": "LAA_CASEWORKER"
                  }
                }
              ]
            }
          ]
        }
```

**Application configuration:** `application-local.yml`

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:9999/entra
          jwk-set-uri: http://localhost:9999/entra/jwks
          audience: laa-data-access-api
```

**Getting a token:**

```bash
# Start infrastructure
docker compose up -d

# Get a token with LAA_CASEWORKER role
curl -X POST http://localhost:9999/entra/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=test" \
  -d "client_secret=test" \
  -d "scope=api://laa-data-access-api/.default"
```

> **Note:** The `client_id` and `client_secret` can be any values (e.g., "test", "test"). 
> The mock server requires these parameters per the OAuth2 spec but doesn't validate them—it 
> issues tokens for any credentials. This differs from production where real credentials are required.

**Response:**
```json
{
#   "access_token": "...",
#   "token_type": "Bearer",
#   "expires_in": 3600
# }

# Extract the token
TOKEN=$(curl -s -X POST http://localhost:9999/entra/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=test" \
  -d "client_secret=test" | jq -r .access_token)

# Use the token in API requests
curl http://localhost:8080/api/access/caseworkers \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Service-Name: CIVIL_APPLY"
```

**For Swagger UI:**

1. Start the app: `./gradlew :data-access-service:bootRun --args='--spring.profiles.active=local'`
2. Get a token using the curl command above
3. Open Swagger UI: http://localhost:8080/swagger-ui.html
4. Click **Authorize** button
5. Paste the token (without "Bearer " prefix)
6. Click **Authorize**
7. Make requests via Swagger

**When to use:**
- Manual testing during development
- Testing with Postman/Insomnia
- Demonstrating the API to stakeholders

---

### 3. UAT / Pipeline Smoke Tests (docker-compose.smoke-test.yml)

**How it works:** Mock server runs as a **Docker container** in the smoke test infrastructure. Test scripts obtain tokens via HTTP before making authenticated API calls.

**Current state:** Not yet implemented (requires adding mock-oauth2-server to smoke test docker-compose)

**Proposed setup:**

#### Step 1: Add mock-oauth2-server to `docker-compose.smoke-test.yml`

```yaml
services:
  postgres-smoketest:
    # ...existing postgres config...

  mock-oauth2-server-smoketest:
    image: ghcr.io/navikt/mock-oauth2-server:2.1.10
    container_name: laa-mock-oauth2-smoketest
    ports:
      - "9998:9999"  # Different host port to avoid conflicts
    environment:
      SERVER_PORT: 9999
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
                    "sub": "smoke-test-user",
                    "aud": ["laa-data-access-api"],
                    "roles": ["LAA_CASEWORKER"],
                    "LAA_APP_ROLES": "LAA_CASEWORKER"
                  }
                }
              ]
            }
          ]
        }
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:9999/entra/jwks || exit 1"]
      interval: 5s
      timeout: 3s
      retries: 5

  app-smoketest:
    # ...existing app config...
    environment:
      # Update to point at mock server instead of real Azure AD
      ENTRA_ISSUER_URI: http://mock-oauth2-server-smoketest:9999/entra
      ENTRA_JWK_SET_URI: http://mock-oauth2-server-smoketest:9999/entra/jwks
      ENTRA_AUD: laa-data-access-api
      FEATURE_DISABLE_SECURITY: false  # Security is ON
    depends_on:
      postgres-smoketest:
        condition: service_healthy
      mock-oauth2-server-smoketest:
        condition: service_healthy
```

#### Step 2: Update smoke test scripts to obtain tokens

**Option A: Add token fetching to `run-infrastructure-smoke-tests.sh`**

```bash
# After infrastructure starts, before running tests
log "Obtaining test token from mock OAuth server..."

TOKEN=$(curl -s -X POST http://localhost:9998/entra/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" | jq -r .access_token)

if [[ -z "$TOKEN" || "$TOKEN" == "null" ]]; then
  fail "Failed to obtain token from mock OAuth server"
fi

log "Token obtained successfully"

# Export for tests
export LAA_SMOKE_ACCESS_TOKEN="$TOKEN"
```

**Option B: Fetch token within Java smoke tests**

Create a test utility:

```java
// InfrastructureTestContextProvider.java or new TokenProvider.java
public class SmokeTestTokenProvider {
    
    private static final String TOKEN_URL = 
        System.getenv().getOrDefault("LAA_SMOKE_OAUTH_TOKEN_URL", 
                                     "http://localhost:9998/entra/token");
    
    public static String getCaseworkerToken() {
        RestTemplate restTemplate = new RestTemplate();
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");
        body.add("scope", "api://laa-data-access-api/.default");
        
        HttpEntity<MultiValueMap<String, String>> request = 
            new HttpEntity<>(body, headers);
        
        ResponseEntity<Map> response = 
            restTemplate.postForEntity(TOKEN_URL, request, Map.class);
        
        return (String) response.getBody().get("access_token");
    }
}
```

**Use in smoke tests:**

```java
@SmokeTest
public class GetCaseworkersInfrastructureTest extends BaseHarnessTest {
    
    @Test
    void getCaseworkers_withValidToken_returnsOK() {
        String token = SmokeTestTokenProvider.getCaseworkerToken();
        
        webTestClient.get()
            .uri("/api/access/caseworkers")
            .header("Authorization", "Bearer " + token)
            .exchange()
            .expectStatus().isOk();
    }
}
```

#### Step 3: Environment Variables

Add to the smoke test script:

```bash
# scripts/run-infrastructure-smoke-tests.sh

# OAuth mock server (for smoke tests)
export LAA_SMOKE_OAUTH_TOKEN_URL="${LAA_SMOKE_OAUTH_TOKEN_URL:-http://localhost:9998/entra/token}"
export LAA_SMOKE_OAUTH_JWKS_URL="${LAA_SMOKE_OAUTH_JWKS_URL:-http://localhost:9998/entra/jwks}"
```

**When to use:**
- CI/CD pipeline smoke tests
- UAT environment verification
- Pre-deployment validation
- Infrastructure testing

---

### 4. Consumer Testing (Other Teams)

**How it works:** Consumer teams add the same mock-oauth2-server Docker image to their own docker-compose and configure their apps to accept tokens from it.

**Consumer setup:**

```yaml
# Consumer's docker-compose.yml
services:
  mock-oauth2-server:
    image: ghcr.io/navikt/mock-oauth2-server:2.1.10
    ports:
      - "9999:9999"
    environment:
      SERVER_PORT: 9999
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
                    "sub": "consumer-test-user",
                    "aud": ["laa-data-access-api"],
                    "roles": ["LAA_CASEWORKER"],
                    "LAA_APP_ROLES": "LAA_CASEWORKER"
                  }
                }
              ]
            }
          ]
        }

  laa-data-access-api:
    image: ghcr.io/ministryofjustice/laa-data-access-api:latest
    ports:
      - "8080:8080"
    environment:
      ENTRA_ISSUER_URI: http://mock-oauth2-server:9999/entra
      ENTRA_JWK_SET_URI: http://mock-oauth2-server:9999/entra/jwks
      ENTRA_AUD: laa-data-access-api
```

**Consumer gets tokens:**

```bash
# Get token
TOKEN=$(curl -s -X POST http://localhost:9999/entra/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=test" \
  -d "client_secret=test" | jq -r .access_token)

# Use token
curl http://localhost:8080/api/access/caseworkers \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Service-Name: CIVIL_APPLY"
```

**Documentation to provide consumers:**

- Mock server Docker image: `ghcr.io/navikt/mock-oauth2-server:2.1.10`
- Issuer ID: `entra`
- Required claims:
  - `aud`: `["laa-data-access-api"]`
  - `roles`: `["LAA_CASEWORKER"]` (or other valid role)
  - `LAA_APP_ROLES`: `"LAA_CASEWORKER"` (same as roles)
- Token endpoint: `http://mock-oauth2-server:9999/entra/token`
- JWKS endpoint: `http://mock-oauth2-server:9999/entra/jwks`

**When to use:**
- Consumer integration testing
- Consumer local development
- Cross-team testing

---

## Comparison with Previous Approach

### Before: `FEATURE_DISABLE_SECURITY` Flag

```yaml
# docker-compose.smoke-test.yml (OLD)
environment:
  FEATURE_DISABLE_SECURITY: true  # ⚠️ Security completely bypassed
```

**Problems:**
- ❌ No security validation—tests don't exercise JWT validation
- ❌ Production risk—misconfigured env var could disable auth in prod
- ❌ False confidence—security bugs go undetected until production

### After: mock-oauth2-server

```yaml
# docker-compose.smoke-test.yml (NEW)
environment:
  ENTRA_ISSUER_URI: http://mock-oauth2-server:9999/entra
  ENTRA_JWK_SET_URI: http://mock-oauth2-server:9999/entra/jwks
  FEATURE_DISABLE_SECURITY: false  # ✅ Security enabled
```

**Benefits:**
- ✅ Real JWT validation through full Spring Security chain
- ✅ No production risk—mock server is test-only dependency
- ✅ High confidence—catches misconfigured security filters, audience validators, role extractors

---

## Troubleshooting

### Token validation fails with "Invalid signature"

**Cause:** API can't reach mock server's JWKS endpoint

**Check:**
```bash
# From the API container
curl http://mock-oauth2-server:9999/entra/jwks

# Should return:
# {"keys":[{"kty":"RSA","e":"AQAB","use":"sig","kid":"...","alg":"RS256","n":"..."}]}
```

**Fix:** Ensure `ENTRA_JWK_SET_URI` uses the container hostname (not `localhost`)

---

### Token validation fails with "Invalid issuer"

**Cause:** Token issuer doesn't match configured issuer-uri

**Check:**
```bash
# Decode the JWT (paste token from curl response)
echo "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..." | jq -R 'split(".") | .[1] | @base64d | fromjson'

# Check the "iss" claim
```

**Fix:** Ensure issuer in token matches `ENTRA_ISSUER_URI`

---

### Tests get 401 Unauthorized

**Cause:** Token is missing required claims

**Check token payload:**
```bash
TOKEN=$(curl -s -X POST http://localhost:9999/entra/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=test" \
  -d "client_secret=test" | jq -r .access_token)

echo $TOKEN | awk -F. '{print $2}' | base64 -d 2>/dev/null | jq
```

**Required claims:**
- `aud`: `["laa-data-access-api"]`
- `LAA_APP_ROLES`: Must be present (our custom audience validator checks this)
- `roles`: `["LAA_CASEWORKER"]` or other valid role

---

### Tests get 403 Forbidden

**Cause:** Token has wrong role

**Check:** Token has `roles` claim but value doesn't match endpoint requirement

**Fix:** Ensure token callback includes correct role:
```json
"claims": {
  "roles": ["LAA_CASEWORKER"],
  "LAA_APP_ROLES": "LAA_CASEWORKER"
}
```

---

### Mock server not starting in Docker

**Check logs:**
```bash
docker logs laa-mock-oauth2-smoketest
```

**Common issues:**
- Port 9999 already in use → change `ports: "9998:9999"`
- Invalid JSON_CONFIG → validate JSON with `jq`
- Network issues → ensure containers on same Docker network

---

## Adding New Roles

To add support for a new role (e.g., `LAA_ADMIN`):

### 1. Update mock server configuration

**docker-compose.yml and docker-compose.smoke-test.yml:**

```json
"claims": {
  "sub": "test-user",
  "aud": ["laa-data-access-api"],
  "roles": ["LAA_ADMIN"],
  "LAA_APP_ROLES": "LAA_ADMIN"
}
```

### 2. Add token factory method

**TestTokenFactory.java:**

```java
public String adminToken() {
    DefaultOAuth2TokenCallback callback =
        new DefaultOAuth2TokenCallback(
            ISSUER_ID,
            "test-admin-" + UUID.randomUUID(),
            JOSEObjectType.JWT.getType(),
            List.of(AUDIENCE),
            Map.of("roles", "LAA_ADMIN", "LAA_APP_ROLES", "LAA_ADMIN"),
            3600);
    SignedJWT jwt = server.issueToken(ISSUER_ID, UUID.randomUUID().toString(), callback);
    return jwt.serialize();
}
```

### 3. Update SecurityConfig

Ensure `SecurityConfig.hasAppRole()` checks for the new role name.

That's it! No Azure AD configuration, no client registrations, no infrastructure changes.

---

## Summary: When to Use Each Mode

| Environment | Mode | Token Source | Use Case |
|-------------|------|--------------|----------|
| **Integration Tests** | In-process library | `TestTokenFactory` | Fast, isolated tests with full security validation |
| **Local Development** | Docker container | `curl` → Swagger/Postman | Manual testing, API exploration |
| **Smoke Tests (UAT/CI)** | Docker container | `curl` in script or Java utility | Pre-deployment validation with real infrastructure |
| **Consumer Testing** | Docker container (consumer's) | Consumer's scripts/tests | Other teams testing integration |

---

## Further Reading

- [mock-oauth2-server GitHub](https://github.com/navikt/mock-oauth2-server)
- [Spring Security OAuth2 Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/)
- [Spike: OAuth Token Testing Options](./spike-oauth-token-testing.md) - Full analysis of 12 approaches
- [Spike: OAuth Code Examples](./spike-oauth-code-examples.md) - Implementation details

---

**Last Updated:** April 2026
