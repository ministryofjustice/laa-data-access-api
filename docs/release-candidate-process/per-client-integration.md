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

### Flag Debt

Flags must be removed once a feature is fully promoted to Staging. Accumulating flags that are never cleaned up adds code complexity and testing burden over time. Flag cleanup should be part of the definition of done for each promoted feature.

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
