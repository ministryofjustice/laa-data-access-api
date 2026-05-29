# Per-Client Integration: Early Access Strategy

## The Problem

Some clients may want early access to validate a specific feature — for example, a breaking schema change — while other clients need to remain on the current stable schema. This requires a strategy that provides **per-client isolation** without introducing long-running branch complexity or undermining the existing RC workflow.

---

## Foundational Constraints

Before evaluating options, two constraints define what is and isn't viable:

### 1. Code Must Already Be on Main (Post-UAT Merge)

Per-client environments are only safe if the code being tested has **already merged to main and deployed to UAT**. If a per-client environment is created from a feature branch that has not yet merged to main:

- The branch must be kept alive while the client tests
- Main continues to move forward, causing divergence
- Rebase is required to keep the branch current, but rebasing invalidates the client's testing surface
- Merging back to main risks cherry-pick complexity or large, conflict-heavy merge diffs

```
Safe:
feature branch → merge to main → deploy to UAT → tag for client RC ✅

Unsafe:
feature branch → tag for client RC (branch still open, not merged) ❌
```

**The RC tag must only ever be created from a commit already on main.**

### 2. Feature Flags Are Required for Per-Client Behaviour Differences

If all clients are testing the same RC tag (same commit on main), the **only** way to give Client A schema v2 and Client B schema v1 without branching is feature flags. Without them:

- Different behaviour for different clients requires different code
- Different code requires different branches
- Different branches reintroduce all the divergence and cherry-pick problems

Feature flags are what make it possible for **one codebase on main** to serve multiple clients with different behaviours simultaneously.

```
Without feature flags:
  Per-client env → needs different code → needs different branch → rebase/cherry-pick problems ❌

With feature flags:
  Per-client env → same code on main → different Helm values → no branch, no divergence ✅
```

---

## The Approach: RC Tags from Main + Per-Client Environments + Feature Flags

> Per-client RC environments exist to give each client a **stable, isolated space** to run their automation tests against the exact code that will be promoted to Staging. They are not demo environments, not exploratory testing environments, and not a mechanism for clients to have permanently different feature sets. The code deployed is identical across all client environments — it is the same code that will eventually reach Production.

### How It Works

```
main (single source of truth)
  └── git tag v1.3.0-rc.1-apply
        └── GitHub Actions detects tag matching *-apply
              └── Helm deploy to namespace with release: laa-data-access-api-rc-apply
                    ├── values/rc-apply.yaml
                    │     ├── featureFlags.schemaV2: true
                    │     └── database: laa-data-access-api-rc-apply-postgresql
                    └── Own isolated Bitnami PostgreSQL database
```

- **Apply client** tests against `laa-data-access-api-rc-apply-uat.cloud-platform.service.justice.gov.uk` with feature flags enabled
- **Decide client** tests against `laa-data-access-api-rc-decide-uat.cloud-platform.service.justice.gov.uk` with feature flags disabled (or different flags)
- Both environments run **identical code** from the same RC tag on main

### What Each Component Does

| Component | Role |
|---|---|
| **Tag name** (`v1.3.0-rc.1-apply`) | Triggers the correct GitHub Actions workflow and identifies the client and version |
| **GitHub Actions tag pattern** (`*-apply`, `*-decide`) | Routes deployment to the correct client environment automatically |
| **Helm values file** (`values/rc-apply.yaml`, `values/rc-decide.yaml`) | Sets client-specific feature flags, Sentry environment, access controls |
| **Isolated database** | Each client gets their own Bitnami PostgreSQL instance — schema migrations run independently |
| **Kubernetes release name** (`laa-data-access-api-rc-apply`) | Full environment isolation — networking, secrets, resources |

---

## Why Individual Databases Are Non-Negotiable

If clients share a database in the RC environment, a schema migration triggered by Client A's test run will structurally alter the database — breaking Client B entirely. This is not just best practice; it is a hard requirement when schema changes are being validated.

```
Shared DB (unsafe):
Client A tests schema v2 migration → DB schema changes → Client B's tests break ❌

Individual DBs (safe):
Client A tests schema v2 migration → Client A's DB only → Client B's DB untouched ✅
```

**Each client RC environment must have its own isolated database instance.**

### Handling Database Migrations (Expand and Contract)

Because both environments deploy the same codebase from `main`, any database migration scripts (e.g., Flyway/Liquibase) on `main` will execute in **both** clients' isolated databases on startup. 

Therefore, schema changes managed by feature flags must follow the **Expand and Contract pattern**:
1. **Expand**: Add new columns/tables in a non-breaking way. Both clients get the database structure, but only Client A's feature flag makes the API use it.
2. **Contract**: Only once the feature is fully promoted to Staging/Production and the feature flag is removed for all clients, can you safely write a migration to drop the old columns.

### Data Strategy

Given the legal aid context, fixture data is the recommended approach:

| Approach | Suitability |
|---|---|
| **Fixture data** (predefined, version-controlled test records) | ✅ Recommended — consistent, repeatable, no compliance risk |
| Empty database + seed scripts | ✅ Acceptable — simple and clean |
| Anonymised production snapshot | ⚠️ GDPR/data handling risk — avoid |

---

## Feature Flags Implementation

Feature flags should be implemented as **environment variables in per-client Helm values files**. This requires no new tooling and reuses existing Helm infrastructure.

```yaml
# .helm/data-access-api/values/rc-apply.yaml
featureFlags:
  schemaV2: true

# .helm/data-access-api/values/rc-decide.yaml
featureFlags:
  schemaV2: false
```

The application code gates behaviour behind the flag:

```java
if (featureFlags.isSchemaV2Enabled()) {
    // new schema behaviour
} else {
    // old schema behaviour
}
```

### What Feature Flags Are and Are Not For

Feature flags enable the staggered access model described in this document: **Client A tests a feature first, Client B tests it before release, and both must have validated it before promotion to Staging.** Flags are the mechanism that allows one codebase on trunk to serve multiple clients at different points in the same testing cycle — without branching.

| Use | Safe? |
|---|---|
| Client A wants early access — flag on in Client A's RC environment while Client B's is still off. Client B will test with the flag on before promotion to Staging. This is the intended staggered access model. | ✅ Primary use case |
| Client B is unaffected by the change and does not use the changed endpoint. Client B runs automation tests against the same RC deployment; their contract is unchanged. | ✅ |
| Client A has the feature on in production; Client B has it off indefinitely. Flag is never cleaned up. | ❌ Anti-pattern |

**Why permanent per-client divergence via feature flags is the wrong model**: flags should be a temporary testing gate, not a substitute for compatibility work. The feature is going to production regardless. If it breaks Client B, a flag does not defer that problem — it hides it. Client B will still hit the breaking change when the flag is turned on in Staging and Production. Running RC testing with Client B's flag permanently set to `false` means Client B has never validated what will actually be deployed to them.

The staggered model works precisely because it is temporary. The discipline required is that Client B must test with the flag on before any promotion proceeds — not that both clients test simultaneously from day one.

### Before Promoting to Staging

When a feature is complete and ready to promote:

1. The feature flag must be set to `true` in **every** client RC environment.
   *Note: Since we do not use a runtime feature flag service, this requires updating the per-client Helm values files on main and creating a new RC tag so that all client environments deploy the updated flag state.*
   **Operational Impact:** Because toggling a flag requires a pull request to `main` and a full pipeline run, it is a heavyweight operation. This enforces GitOps discipline but adds friction. Client teams should map out their readiness and consolidate flag toggles rather than rapidly flipping them back and forth.
2. Every client must run their automation tests with the flag on
3. If any client's tests fail, the feature is not ready to promote — fix the incompatibility first

```
Staggered (correct — intended model):
  Apply RC:  flag=true  → automation tests pass ✅  ← Client A tests first
  Decide RC: flag=false → not yet tested            ← Client B still pending
  → Decide RC: flag=true → automation tests pass ✅ ← Client B catches up
  → Promote to Staging ✅

Wrong — promoting before Client B has tested:
  Apply RC:  flag=true  → automation tests pass ✅
  Decide RC: flag=false → automation tests pass ✅ (change untested)
  → Promote to Staging: flag=true → Decide breaks ❌
```

Client B having `flag=false` during the staggered testing period is expected and correct. The failure mode is treating that intermediate state as a completed test and promoting from it.

### Flag Debt

Flags must be removed once a feature is fully promoted to Staging. Accumulating flags that are never cleaned up adds code complexity and testing burden over time. Flag cleanup should be part of the definition of done for each promoted feature.

---

## Cross-Client Breaking Changes: The Apply/Decide Scenario

The most complex per-client case is a schema change requested by one client that would also affect another client's API contract. For example: Apply want a new schema on an endpoint that Decide also uses.

### The Core Rule

**Both clients must test the change with the feature flag on. Neither can go to Staging until the change works for both.**

A feature flag does not resolve a cross-client incompatibility — it defers it to a point where it cannot be avoided. Staging and Production have a single configuration. The moment the flag is turned on in Staging, every client is affected.

```
Wrong:
  Apply RC: flag=true → tests pass ✅
  Decide RC: flag=false → tests pass ✅ (change untested)
  → Promote to Staging: flag=true → Decide breaks ❌

Correct:
  Apply RC: flag=true → tests pass ✅
  Decide RC: flag=true → tests fail → fix the incompatibility → tests pass ✅
  → Promote to Staging ✅
```

### The Special Case: Only One Client Uses the Changed Endpoint

If the schema change affects an endpoint that one client uses but the other does not — for example, Apply sends a new field via POST and Decide only uses a GET that does not return it — the problem reduces to a **mapping problem**, not a breaking change:

- The schema migration runs on both clients' isolated databases (same codebase on main)
- The unaffected client's API contract is unchanged — they simply do not interact with the new field
- Both clients still run automation tests against the new RC deployment, but no cross-client coordination is needed

In this case the change is additive from the unaffected client's perspective and the delivery is straightforward.

### If the Change Is Genuinely Breaking for the Other Client

If the schema change cannot be made non-breaking for all clients without full API versioning:

1. The change must be held until all affected clients are ready to accept it
2. Or the team must implement versioned API endpoints (`/v1/`, `/v2/`) — a larger commitment (see [API Versioning](#api-versioning-v1-and-v2) in alternatives)
3. Expand and Contract remains available for database-level changes that can be introduced additively before the old structure is removed

There is no shortcut that allows one client to be on a permanently different API version without accepting the full cost of API versioning.

---

## The Unavoidable Convergence Point

Per-client environments give clients **independent early access testing**, but they do not give clients **independent production timelines**. There is always a point where:

```
RC (per-client flags) → Staging → Production
                                      ↑
                           ALL clients hit this
                           ALL flags are permanently ON
                           Breaking change affects everyone
```

When the feature promotes to Staging and ultimately Production, **every client is on the same code, same schema, same behaviour**. The RC strategy solves the problem of clients being surprised by breaking changes — it does not defer the change indefinitely.

**All clients must be ready before promotion to Staging.** The RC exists to ensure no client is unprepared, not to enable permanent per-client divergence.

---

## Alternative Approaches Considered

### Long-Running Per-Client Feature Branches

Creating a dedicated branch per client (e.g. `feature/schema-v2-client-a`) and tagging that branch for deployment.

**Why this was ruled out:**

| Problem | Detail |
|---|---|
| **Branch divergence** | Main continues to move; the client branch falls further behind with every passing day |
| **Rebase burden** | Regular rebasing from main is required to keep the branch valid — but each rebase is a new deployment that invalidates the client's testing surface |
| **Cherry-pick complexity** | Cherry-picking back to main creates duplicate commit SHAs, causes re-application of changes on future merges, and loses surrounding context |
| **Schema migration risk** | Migrations on a branch cannot safely coexist with main's migration history without explicit ordering |
| **CI false confidence** | Tests pass on the branch but may fail once rebased onto current main |

**Verdict**: Only viable for very short-lived branches (days, not weeks). Not a sustainable per-client strategy.

### API Versioning (`/v1/` and `/v2/`)

Running multiple API versions simultaneously within a single deployment.

**Why this was not chosen now:**

- High code maintenance burden — two versions of the API must be maintained simultaneously
- Only works for schema changes that can be versioned cleanly
- Becomes a permanent commitment, not an early-access mechanism
- Significant effort to retrofit

**Verdict**: The correct long-term answer if clients persistently require different API versions indefinitely. Not the right approach for early-access RC testing. Revisit if per-client version divergence becomes a recurring long-term need.

### Simple Feature Flag Service (Unleash, LaunchDarkly)

A dedicated flag management service with a UI for runtime flag toggling.

**Why this was not chosen now:**

- Introduces a new infrastructure dependency
- Adds operational overhead for a small team
- Per-client Helm values files achieve the same per-client flag toggling with no new tooling

**Verdict**: Worth revisiting if the number of clients or flag combinations grows significantly.

---

## Industry Standards

For context, the two recognised industry standard approaches for this class of problem are:

**Feature flags** — the canonical Continuous Delivery / Trunk-Based Development solution for decoupling deployment from release. Endorsed by Martin Fowler, Dave Farley, and used at scale by Netflix, Facebook, and Google to ship to subsets of users independently.

**API versioning** — the standard contract-level solution, used by major public APIs (GitHub, Stripe, Twilio) to allow clients to opt into new versions on their own timeline.

The approach described in this document — **RC tags from main with per-client Helm-controlled feature flags** — is a pragmatic, right-sized alternative that achieves the same client isolation goal without the overhead those standards carry at the current team size and client scale. If client volume or release frequency increases significantly, feature flags backed by a dedicated flag service or full API versioning would be the natural next steps.

---

## Operational Requirements

For this approach to work reliably, the following must be in place:

1. **Per-client Helm values files** — one per client, version controlled alongside the application (`.helm/data-access-api/values/rc-apply.yaml`, `.helm/data-access-api/values/rc-decide.yaml`)
2. **Per-client database provisioning** — automated via Helm; each client gets their own Bitnami PostgreSQL instance deployed alongside the application
3. **Tag naming convention** — agreed format for GitHub Actions routing to work consistently:
   - `v{version}-rc.{n}` → standard RC, promotable to Staging from main
   - `v{version}-rc.{n}-apply` → Apply client-specific RC, NOT directly promotable
   - `v{version}-rc.{n}-decide` → Decide client-specific RC, NOT directly promotable
4. **Environment TTL and cleanup policy** — explicit ownership of teardown; environment is removed when the feature is promoted to Staging (delete the Helm release: `helm uninstall laa-data-access-api-rc-apply -n laa-data-access-api-uat`)
5. **Flag cleanup discipline** — flags are removed from the codebase as part of the definition of done when a feature is fully promoted

---

## Summary

> To give Client A early access to a new feature (such as a breaking schema change or new application functionality) while Client B remains on the current stable behaviour — without long-running branches, cherry-picks, or rebase complexity — the correct approach is:
>
> **Merge to main → deploy to UAT → create a client-specific RC tag → auto-deploy to a client-named environment with an isolated database and a per-client Helm values file that enables the relevant feature flag.**
>
> Feature flags are not optional in this model. They are the mechanism that allows one codebase on trunk to serve different clients differently. Without them, per-client behaviour differences require per-client branches — and per-client branches reintroduce all the divergence and merge complexity this approach is designed to avoid.
