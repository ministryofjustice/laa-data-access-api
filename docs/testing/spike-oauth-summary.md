# Spike Summary: OAuth Token Testing

**Date:** April 2025
**Ticket:** Real token testing locally and in environments

## Problem

We need real JWT validation in local development, integration tests, UAT smoke tests, and potentially for consumers — without requiring real Azure AD client secrets.

## Recommendation

**[`mock-oauth2-server`](https://github.com/navikt/mock-oauth2-server)** — an open-source mock OAuth2/OIDC server maintained by NAV (Norwegian government). It issues real signed JWTs, exposes JWKS and well-known endpoints, and plugs into Spring Security's standard `JwtDecoder` chain with no production code changes.

## Why this option?

12 options were evaluated ([full analysis](./spike-oauth-token-testing.md)). Only three work across all environments:

| Option | Prod safety | Effort | Tradeoff |
|---|---|---|---|
| **`mock-oauth2-server`** | ✅ Safe — test dependency only | Low | Adds a Kotlin transitive dependency |
| Self-written token factory | ✅ Safe | High | We build and maintain what mock-oauth2-server already provides |
| Dual issuer (env var) | 🚨 Risk — misconfigured env var = prod backdoor | Medium | Security concern outweighs benefit |

## How it works

| Context | How |
|---|---|
| **Integration tests** | In-process library (`testImplementation`). Server starts with the test, tokens created via `TestTokenFactory` |
| **Local dev (Swagger/Postman)** | Docker image in `docker-compose.yml`. Curl a token, paste into Swagger UI |
| **UAT smoke tests** | Docker image in `docker-compose.smoke-test.yml` (follow-up) |
| **Consumer testing** | Consumers add the Docker image to their own docker-compose |
| **Unit tests** | Not needed — `@MockitoBean JwtDecoder` satisfies the bean dependency; no JWT is decoded |

## Token creation (one line)

```java
server.issueToken("entra", "client-id",
    new DefaultOAuth2TokenCallback("entra", "test-user",
        JOSEObjectType.JWT.getType(),
        List.of("api://ads-api"),
        Map.of("roles", List.of("LAA_CASEWORKER")),
        3600));
```

Adding a new role = adding a string to the list. No infrastructure or Azure AD changes.

## What was implemented in the spike

- ✅ `mock-oauth2-server` dependency added (`testImplementation`)
- ✅ `TestTokenFactory` — mints tokens with configurable roles from the mock server's keys
- ✅ Integration tests pass (all 274) with real JWT validation through `SecurityConfig`
- ✅ `@Disabled` test re-enabled (caseworker-not-found decision test)
- ✅ Docker setup for local dev (`docker-compose.yml` with mock-oauth2-server on port 9999)
- ✅ `application-local.yml` points at mock server for `bootRun`

## What's left (follow-up work)

| Item | Notes |
|---|---|
| Remove `FEATURE_DISABLE_SECURITY` flag | Blocked by smoke test decision — needs mock-oauth2-server in smoke test docker-compose first |
| Remove `NoSecurityConfig` / `application-unsecured.yml` | Follows from above |
| Smoke tests with real tokens | Wire mock-oauth2-server into `docker-compose.smoke-test.yml` |
| `AccessAppTests` update | Add `@MockitoBean JwtDecoder` (one line) when flag is removed |
| Docker healthcheck for mock server | Prevents race condition on startup |

## Answers to spike questions

**Can we mock OAuth easily enough?**
Yes — `mock-oauth2-server` requires ~50 lines of setup and a single Gradle dependency.

**Can we use open source packages for a local OAuth server with configurable roles?**
Yes — this is what `mock-oauth2-server` does. Roles are strings in a claims map.

**Do we need real client secrets?**
No — the mock server generates its own signing keys. No secrets to manage, rotate, or sync.

## Key files changed

- `data-access-service/build.gradle` — added `testImplementation` dependency
- `TestTokenFactory.java` — token minting utility
- `IntegrationTestContextProvider.java` — starts mock server, configures Spring to trust it
- `BaseHarnessTest.java` — creates tokens per test via `TestTokenFactory`
- `docker-compose.yml` — added mock-oauth2-server service
- `application-local.yml` — points `bootRun` at mock server
