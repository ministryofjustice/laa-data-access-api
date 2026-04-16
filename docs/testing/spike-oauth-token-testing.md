# Spike: Real Token Testing Locally and in Environments

## Background

The tokens we will receive in production come from the following flow:

1. User logs into client app, receives an ID and access token
2. Client app passes access token to middleware API
3. Middleware API swaps the access token for an OBO (On-Behalf-Of) token
4. OBO token is passed to ADS API
5. Our API receives the token, reads it into a JWT, validates that the encrypted part matches the decrypted part using the OAuth issuer public key, then uses information inside to set roles

We need a way to test tokens that come to our API for **local development**, **UAT environments (via pipeline)**, and potentially for **our consumers** to use in their own testing.

A simple script is not enough — it would require having client ID/secrets for a selection (or possibly all) of our clients to simulate the flow.

---

## Requirements

| Requirement | Description |
|---|---|
| **Local dev** | Developers can run integration tests and manually test with real JWT validation enabled |
| **UAT / pipeline** | Smoke tests in deployed environments can obtain valid tokens |
| **Consumer testing** | Other teams integrating with our API can test with realistic auth |
| **Role support** | Must support configurable roles (currently `reader`, `writer`; more in future) |
| **Security** | Must not risk accidentally disabling auth in production |

---

## Investigation Questions

**1. Can we mock OAuth for our purposes easily enough?**

> **Yes.** `mock-oauth2-server` (Option 1) provides a drop-in mock OAuth2/OIDC server that issues real signed JWTs, exposes JWKS and well-known endpoints, and requires minimal configuration. Our spike confirmed it can be added as a single Gradle dependency and integrated into the existing test harness with ~50 lines of setup code. Tokens with custom roles are created in one line via `DefaultOAuth2TokenCallback`. Spring Security validates these tokens through the normal `JwtDecoder` chain — no production code changes required.

**2. If not, can we use open source packages to set up a "local" OAuth server that can be easily configured with applicable roles?**

> **Yes — and this is the same answer as above.** `mock-oauth2-server` serves as both an in-process test library and a standalone Docker image. Roles are configured by passing a `claims` map (e.g., `Map.of("roles", List.of("reader", "writer"))`) when creating a token callback. Adding new roles in future is a one-line change. Alternatives like Keycloak (Option 8) also work but are significantly heavier and slower to start.

**3. Do we need to go down the road of knowing and keeping our client secrets in sync with a test harness because the above will not work?**

> **No.** The mock server approach eliminates the need for real client secrets entirely. The mock server generates its own signing keys, issues tokens against them, and exposes the corresponding public keys via JWKS. Our API validates tokens using standard OIDC discovery — it doesn't know or care that the issuer is a mock. No Azure AD client registrations, no secret rotation, no network dependency on an external identity provider.

---

## Summary

Of the 12 options investigated, only three work across all three environments (local integration tests, UAT/pipeline smoke tests, and consumer testing):

1. **`mock-oauth2-server`** (Option 1) — open-source mock OIDC server
2. **Self-written token factory** (Option 3) — essentially building our own version of the above
3. **Dual issuer via environment variable** (Option 12) — configuring the app to trust a test issuer alongside the real one

The dual issuer approach carries a higher risk of misconfiguration causing a security issue in production — an accidentally set environment variable could open a backdoor allowing forged tokens.

This leaves `mock-oauth2-server` as the recommended choice, unless there is a specific desire to build and maintain an equivalent solution in-house as a MoJ/LAA asset. The self-written option would deliver the same outcome with significantly more implementation and maintenance effort.

---

## Options Considered

### Option 1: `mock-oauth2-server` (NAV / Norwegian Labour and Welfare)

A scriptable/customizable mock OAuth2/OIDC server (`no.nav.security:mock-oauth2-server`). Runs as an in-process server or standalone Docker image. Issues real signed JWTs and exposes JWKS/well-known endpoints so Spring Security validates tokens through the normal chain.

**Strengths:**
- Tokens are real, signed JWTs validated by Spring Security's standard `JwtDecoder` — the full `SecurityConfig` chain runs
- Multi-issuer support out of the box — just change the URL path
- Custom claims and roles are trivially added via `DefaultOAuth2TokenCallback`
- Available as a library (in-process for tests) and a Docker image (for docker-compose / pipeline)
- Well-maintained open-source project, actively used in Norwegian government services
- No changes to production code or `SecurityConfig`
- JWKS endpoint is auto-exposed, so token signature validation works exactly as in prod
- Works for all three contexts: local tests, UAT pipeline, and consumers

**Tradeoffs:**
- Adds a Kotlin transitive dependency (the library is written in Kotlin)
- Need to manage the mock server lifecycle in tests (start/stop)
- Slightly more initial setup than just disabling security
- Consumer teams would need to spin up the Docker image or use the library themselves

**Security:**
- ✅ No risk to production — production code is untouched; the mock server is only a test/dev dependency
- ✅ Security is never disabled — tokens go through real validation
- ✅ No client secrets to manage or leak

**Adding new roles in future:**
- Trivial — just add the role string to the claims map in `DefaultOAuth2TokenCallback`. No config changes needed.

---

### Option 2: Spring Security Test (`@WithMockUser` / `SecurityMockMvcRequestPostProcessors`)

Use Spring's built-in test security support to inject mock authentication contexts directly, bypassing JWT parsing entirely.

**Strengths:**
- Zero additional dependencies — comes with `spring-security-test`
- Very fast, no server to start
- Simple for unit-style controller tests

**Tradeoffs:**
- Does **not** test the real token validation chain — bypasses `JwtDecoder`, `SecurityConfig` JWT parsing, claim extraction, role mapping
- Only works in `@WebMvcTest` or `MockMvc`-based tests — not usable for full integration tests hitting a real HTTP server
- Not usable in UAT/pipeline smoke tests or by consumers
- Cannot be used in docker-compose or standalone scenarios

**Security:**
- ⚠️ Does not validate that your `SecurityConfig` actually works — a misconfiguration in JWT validation would not be caught
- ✅ No production risk (test-only)

**Adding new roles in future:**
- Easy — just change the `authorities` parameter.

---

### Option 3: Self-Written Token Factory (Generate JWTs with Nimbus)

Write our own utility class that generates RSA key pairs, signs JWTs with Nimbus JOSE, and exposes the public key via a minimal JWKS endpoint (e.g., using OkHttp `MockWebServer` or a tiny embedded server).

**Strengths:**
- Full control over token structure and claims
- No external library dependency beyond Nimbus (already in Spring Security)
- Can be shaped exactly to match production token structure
- Useful for edge-case testing (expired tokens, malformed tokens, missing claims)

**Tradeoffs:**
- Significant implementation effort — need to build and maintain: key pair generation, JWT construction, JWKS endpoint, server lifecycle
- Essentially re-building what `mock-oauth2-server` already provides
- Higher maintenance burden as OAuth requirements evolve
- Consumer teams would need to replicate or be given the utility
- Risk of subtle bugs in the token factory itself

**Security:**
- ✅ No production risk — test utility only
- ✅ Real JWT validation still runs
- ⚠️ Risk of the factory diverging from real token format over time

**Adding new roles in future:**
- Easy — just add claims to the JWT builder. But any structural changes to how roles are encoded require updating the factory.

---

### Option 4: Real Azure AD / OAuth Provider Test Tenants

Use actual Azure AD (Entra ID) test tenants with real client credentials to obtain real tokens.

**Strengths:**
- Highest fidelity — tokens are identical to production
- Tests the complete end-to-end OAuth flow including OBO exchange

**Tradeoffs:**
- Requires managing client IDs, secrets, and tenant configuration
- Secrets must be kept in sync across environments
- Network dependency — tests fail if Azure is unreachable
- Slow — network round-trip for every token
- Complex setup for local development
- Every consumer needs their own client registration
- Secrets management overhead (Kubernetes secrets, CI/CD variables, rotation)

**Security:**
- ⚠️ Client secrets must be stored securely and rotated
- ⚠️ Leaked test credentials could potentially be used against the test tenant
- ✅ Highest confidence in auth correctness

**Adding new roles in future:**
- Requires Azure AD admin configuration — app role definitions, group assignments, etc. Slower feedback loop.

---

### Option 5: Nginx / Reverse Proxy Layer

Place an Nginx (or similar) reverse proxy in front of the API that injects or rewrites auth headers.

**Strengths:**
- Language/framework agnostic
- Could strip auth requirements for specific environments

**Tradeoffs:**
- Does not test real JWT validation at all
- Adds infrastructure complexity
- Another component to maintain, configure, and debug
- Not useful for integration tests
- Awkward for consumers — they'd need the same proxy setup

**Security:**
- ⚠️ Risk of the proxy accidentally being deployed to production or staging, effectively disabling auth
- ⚠️ Does not test your `SecurityConfig` works

**Adding new roles in future:**
- Requires proxy config changes — less intuitive than code-level changes.

---

### Option 6: Spring Profiles to Disable Security

Use a Spring profile (e.g., `nosecurity`) that registers a permissive `SecurityFilterChain` allowing all requests.

**Strengths:**
- Extremely simple to implement
- Fast — no token handling at all

**Tradeoffs:**
- Does not test security at all
- Explicitly defeats the purpose of having security
- The `nosecurity` profile could accidentally be activated in production

**Security:**
- 🚨 **High risk** — a single misconfiguration (wrong profile active, wrong env var) could disable authentication in production
- This is the approach the `mock-oauth2-server` authors explicitly warn against

**Adding new roles in future:**
- N/A — there are no roles when security is disabled.

---

### Option 7: WireMock as a Mock OIDC Provider

Use WireMock to stub the OIDC discovery, JWKS, and token endpoints.

**Strengths:**
- Familiar library (may already be in the project)
- Flexible request/response matching

**Tradeoffs:**
- Requires manually constructing JWTs and JWKS JSON — effectively the same work as Option 3 but with WireMock boilerplate on top
- Easy to get the JWKS/JWT alignment wrong
- More verbose than `mock-oauth2-server` for this specific use case

**Security:**
- ✅ No production risk
- ✅ Real validation runs

**Adding new roles in future:**
- Similar to Option 3 — update the JWT construction logic.

---

### Option 8: Keycloak Testcontainer

Run a Keycloak instance via Testcontainers. Full OIDC provider in Docker.

**Strengths:**
- Full-featured OIDC provider
- Realm/role configuration via JSON import
- Realistic multi-client scenarios

**Tradeoffs:**
- Very heavy — Keycloak container takes 15–30s to start
- Complex configuration (realms, clients, roles, users)
- Overkill for our current needs (we just need signed JWTs with roles)
- Significant memory usage in CI

**Security:**
- ✅ No production risk
- ✅ Real validation

**Adding new roles in future:**
- Requires updating realm JSON config — more ceremony than code-level changes.

---

### Option 9: Hardcoded / Static Test Tokens

Pre-generate a set of signed JWTs with known keys and check them into the repo or load from config.

**Strengths:**
- Zero runtime overhead
- Deterministic

**Tradeoffs:**
- Tokens expire (or must have absurdly long expiry)
- Keys are static — if validation logic changes, all tokens must be regenerated
- Fragile and hard to maintain

**Security:**
- ⚠️ Static tokens in a repo could be mistaken for real credentials
- ✅ No production risk if clearly separated

**Adding new roles in future:**
- Requires regenerating tokens manually.

---

### Option 10: Testcontainers + `mock-oauth2-server` Docker Image

Run `mock-oauth2-server` as a Testcontainer rather than in-process.

**Strengths:**
- Full network isolation — closest to how it would run in docker-compose
- Reuses same image as UAT/pipeline

**Tradeoffs:**
- Slower startup than in-process
- Slightly more complex than using the library directly
- Two possible modes to maintain (in-process for speed, container for fidelity)

**Security:**
- ✅ Same as Option 1

**Adding new roles in future:**
- Same as Option 1 — trivial.

---

### Option 11: Custom Spring `BeanPostProcessor` / `JwtDecoder` Override for Tests

Replace the `JwtDecoder` bean in test context with one that uses a known key pair.

**Strengths:**
- Lightweight
- No external server

**Tradeoffs:**
- Partial bypass — replaces the decoder but the rest of the security chain runs
- Risk of test decoder diverging from production decoder behaviour
- Not usable outside of Spring test context

**Security:**
- ⚠️ If the bean override is accidentally included in a production profile, it would bypass real JWT validation
- Need to be very careful with `@Profile` / `@ConditionalOnProperty` guards

**Adding new roles in future:**
- Easy at code level.

---

### Option 12: Environment-Variable-Driven Dual `JwtDecoder`

Configure the app to accept multiple JWT issuers — the real Azure AD issuer in production and an additional test issuer (mock-oauth2-server) in non-production environments, controlled by environment variables.

**Strengths:**
- Production code supports multiple issuers natively
- Could allow both real and mock tokens in UAT for parallel testing

**Tradeoffs:**
- Adds complexity to `SecurityConfig`
- ⚠️ **Risk of accidentally enabling the test issuer in production** if the env var is misconfigured — this would allow anyone with access to the mock server's signing key to forge valid tokens
- Requires careful validation that the test issuer is never active in prod

**Security:**
- 🚨 **Moderate-to-high risk** — a misconfigured environment variable could add a test issuer to production, effectively creating a backdoor. This is a real concern, not theoretical.
- Must have safeguards: fail-loud if test issuer is configured in `production` profile, CI checks, etc.

**Adding new roles in future:**
- Same as whatever mock solution is backing it.

---

## Open Questions & Answers

| Question | Answer / Status |
|---|---|
| What is "real environment testing"? | Confirmed: UAT environments tested via pipeline smoke tests. Not a consumer-facing test environment — it's our own verification that the deployed API works end-to-end with auth enabled. |
| Do consumers need this solution? | Potentially yes — we're the first team doing this piece of work, so finding a good reusable solution is valuable. `mock-oauth2-server` as a Docker image is easy to share: consumers add it to their docker-compose, point at the well-known URL, and generate tokens with whatever roles they need. No secrets to exchange. |
| What roles do we need now? | `reader` and `writer` for now, with expectation of more in future. Both options 1 and 3 handle this trivially — roles are just strings in a claims map. |
| Does the solution need to match exact Azure AD token format? | For integration tests: close enough that `SecurityConfig` validates it — issuer, audience, signature, and roles claims must be present and correct. For smoke tests: the deployed API must accept the token, so the issuer-uri must be configured to trust the mock server. The internal JWT structure (e.g., `roles` claim path) must match what our `SecurityConfig` expects. |
| Can we add more roles easily in future? | Yes with both recommended options. Option 1: add the role string to the `claims` map in `DefaultOAuth2TokenCallback` — one line change, no config. Option 3: add the role to the JWT builder — also straightforward. Neither requires infrastructure or Azure AD admin changes. |
| Does Option 12 (dual issuer) risk accidentally turning off security in prod? | Yes. If the environment variable pointing to the test issuer is accidentally set in production, anyone who can sign tokens with the mock server's key can authenticate as any user with any role. This is a real backdoor risk, not theoretical. Option 1 avoids this entirely because the mock server only exists in test/dev contexts — production never knows about it. |
| Could `mock-oauth2-server` be used by consumers who aren't Java/Spring? | Yes. The Docker image is language-agnostic. Any HTTP client can request tokens from its `/token` endpoint or use pre-configured token callbacks. The JWKS endpoint works with any JWT validation library in any language. |

---

## Option Viability by Environment

| Option | Local Integration Tests | UAT / Pipeline Smoke Tests | Consumer Testing | Tests Real SecurityConfig | Prod Safety |
|---|---|---|---|---|---|
| **1. `mock-oauth2-server`** | ✅ In-process library | ✅ Docker image | ✅ Docker image | ✅ Yes | ✅ No prod changes |
| **2. Spring `@WithMockUser`** | ⚠️ MockMvc only | ❌ Not usable | ❌ Not usable | ❌ Bypassed | ✅ Test-only |
| **3. Self-written token factory** | ✅ With effort | ⚠️ Needs custom tooling | ⚠️ Must share utility | ✅ Yes | ✅ No prod changes |
| **4. Real Azure AD tenants** | ⚠️ Complex setup | ✅ Highest fidelity | ⚠️ Each needs registration | ✅ Yes | ⚠️ Secrets to manage |
| **5. Nginx proxy** | ❌ Not useful | ⚠️ Infra overhead | ❌ Awkward | ❌ Bypassed | 🚨 Could leak to prod |
| **6. Disable security (profile)** | ⚠️ Fast but no coverage | ❌ Dangerous | ❌ Not realistic | ❌ Disabled entirely | 🚨 High risk |
| **7. WireMock OIDC stubs** | ✅ With effort | ⚠️ Needs custom tooling | ⚠️ Must share stubs | ✅ Yes | ✅ No prod changes |
| **8. Keycloak Testcontainer** | ⚠️ Heavy / slow | ⚠️ Heavy in CI | ⚠️ Complex for consumers | ✅ Yes | ✅ No prod changes |
| **9. Static test tokens** | ⚠️ Fragile | ❌ Tokens expire | ❌ Hard to share | ✅ Yes | ✅ No prod changes |
| **10. `mock-oauth2-server` Testcontainer** | ✅ Slightly slower | ✅ Docker image | ✅ Docker image | ✅ Yes | ✅ No prod changes |
| **11. `JwtDecoder` bean override** | ✅ Lightweight | ❌ Not usable | ❌ Not usable | ⚠️ Partial | ⚠️ Profile leak risk |
| **12. Dual issuer env var** | ✅ With mock backing | ✅ With mock backing | ✅ With mock backing | ✅ Yes | 🚨 Misconfiguration risk |

**Key:** ✅ = well suited, ⚠️ = possible with caveats, ❌ = not viable, 🚨 = security concern

---

## Recommended Combinations

### For Integration Tests (Local + CI)

**→ Option 1: `mock-oauth2-server` in-process**

- Best balance of fidelity, simplicity, and safety
- Full `SecurityConfig` chain is exercised
- No production code changes
- Trivial role configuration
- Already spiked and working (see [code examples](./spike-oauth-code-examples.md))

### For UAT / Pipeline Smoke Tests

**→ Option 1: `mock-oauth2-server` Docker image in docker-compose**

- Same tool, different deployment mode
- Smoke test scripts obtain tokens via HTTP from the mock server
- API is configured to trust the mock server's issuer in UAT only (via env var / Spring profile)

### For Consumer Testing

**→ Option 1: `mock-oauth2-server` Docker image**

- Consumers add the Docker image to their own docker-compose
- We document the expected issuer URL, claims structure, and role values
- They generate their own tokens against the mock server
- No secrets to share

### Overall Recommendation

**Option 1 (`mock-oauth2-server`) is recommended for all three contexts.** It provides:

- Real JWT validation without production risk
- A single tool that works locally, in CI, in docker-compose, and for consumers
- Minimal setup with maximal fidelity
- Easy extensibility as roles and requirements grow
- No client secrets to manage
- A solution we can share with other teams as a reusable pattern

Option 3 (self-written) is a viable fallback if we need to avoid the Kotlin dependency, but it requires significantly more implementation and maintenance effort for the same outcome.

**Options to explicitly avoid:**
- Option 6 (disable security) — unacceptable production risk
- Option 12 (dual issuer via env var) — carries real risk of accidental prod misconfiguration
- Option 5 (Nginx proxy) — doesn't test what we need to test

---

## Spike Implementation

A working spike of Option 1 has been implemented. See:

- [Code Examples](./spike-oauth-code-examples.md) — side-by-side comparison of Option 1 vs Option 3
- `TestTokenFactory` — utility class for generating tokens with configurable roles
- `BaseHarnessTest` — integration with the existing test harness

### Current Status

- ✅ `mock-oauth2-server` dependency added to `build.gradle`
- ✅ `TestTokenFactory` created using the mock server's JWKS for signing
- ✅ Unit tests pass (290 pass)
- ⚠️ Integration tests have compilation errors related to `DefaultOAuth2TokenCallback` constructor — the `audience` parameter expects `List<String>` not `String`, and `claims` expects `Map<String, Any>` not `List<String>`. These are fixable API mismatches.
- ⚠️ Some integration tests fail with "Malformed token" / 401 — indicates the token factory is not yet correctly wiring tokens to the mock server's JWKS. The signing key must come from the mock server's key set (not a separate generated key).

### Next Steps

1. Fix `TestTokenFactory` to use correct `DefaultOAuth2TokenCallback` constructor signature
2. Ensure tokens are signed with the mock server's private key (obtained from server's JWK set)
3. Point the Spring Boot test application's `spring.security.oauth2.resourceserver.jwt.issuer-uri` at the mock server
4. Validate all integration tests pass
5. Document docker-compose setup for UAT smoke tests
6. Share approach with other teams
