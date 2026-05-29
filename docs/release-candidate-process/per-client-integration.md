# Per-Feature Integration: Early Access Strategy

## Overview

When a feature under development needs to be tested in isolation — before the common RC is tagged
and without affecting other in-flight work — a **per-feature RC environment** can be deployed
on demand via `workflow_dispatch`.

This replaces the previous per-client approach (which required client-specific tag suffixes and
hardcoded `rc-apply.yaml` / `rc-decide.yaml` values files) with a more flexible model where any
named feature can get its own isolated environment.

For a step-by-step guide and full technical reference, see
[**`feature-environments.md`**](./feature-environments.md).

---

## Foundational Constraints

These constraints apply regardless of how feature environments are triggered.

### 1. Code Must Already Be on Main (Post-UAT Merge)

Feature environments are only safe if the code being tested has **already merged to main and
deployed to UAT**. If a feature environment is created from a branch that has not yet merged:

- The branch must be kept alive while testing continues
- Main continues to move forward, causing divergence
- Rebasing invalidates the testing surface
- Merging back to main risks large conflict-heavy diffs

```
Safe:
feature branch → merge to main → deploy to UAT → trigger feature environment ✅

Unsafe:
feature branch → trigger feature environment (branch still open, not merged) ❌
```

### 2. Feature Flags Are Required for Per-Feature Behaviour Differences

When all feature environments deploy the same code from `main`, the only way to give Environment A
different behaviour from Environment B is **feature flags**. Without them, different behaviour
requires different code, which requires different branches, which reintroduces all the divergence
problems above.

Feature flags are what make it possible for **one codebase on `main`** to exhibit different
behaviours in different environments simultaneously.

---

## The Approach: workflow_dispatch + Feature Flags

```
main (single source of truth)
  └── workflow_dispatch
        feature-name=schema-v2, feature-flag=schemaV2, feature-flag-value=true
              └── Helm deploy to namespace
                    release: laa-data-access-api-rc-schema-v2
                    env var: FEATURE_SCHEMAV2=true
                    database: laa-data-access-api-rc-schema-v2-postgresql (isolated)
```

- The environment is accessible at
  `laa-data-access-api-rc-schema-v2-uat.cloud-platform.service.justice.gov.uk`
- The feature flag is passed at deploy time via `--set featureFlags.schemaV2=true` — no
  per-feature values file required
- The isolated Bitnami PostgreSQL database ensures schema migrations run independently of any
  other environment

---

## Why Individual Databases Are Non-Negotiable

If feature environments share a database, a schema migration in one environment alters the
database structure for all others. This is a hard requirement when schema changes are being
validated.

```
Shared DB (unsafe):
Feature A runs schema v2 migration → DB schema changes → Feature B's tests break ❌

Individual DBs (safe):
Feature A runs schema v2 migration → Feature A's DB only → Feature B's DB untouched ✅
```

### Handling Database Migrations (Expand and Contract)

Because all feature environments deploy the same codebase from `main`, any migration scripts
will execute in all feature databases on startup. Therefore, schema changes managed by feature
flags should follow the **Expand and Contract pattern**:

1. **Expand**: Add new columns/tables non-destructively. All environments get the schema
   change; only the environments with the flag enabled use the new structure in the API.
2. **Contract**: Once the feature is promoted to Staging/Production and the flag is removed,
   write a migration to clean up the old columns.

---

## Feature Flags

Feature flags in per-feature environments are passed dynamically at dispatch time — there is no
per-feature values file. The flag flows through the pipeline as:

```
workflow_dispatch input
  → helm --set featureFlags.schemaV2=true
  → _envs.tpl iterates featureFlags map
  → FEATURE_SCHEMAV2=true env var in pod
  → Spring reads ${FEATURE_SCHEMAV2:false}
```

For a flag to be readable by Spring, declare it in `application.yml` with a safe default:

```yaml
feature:
  schema-v2: ${FEATURE_SCHEMAV2:false}
```

### What Feature Flags Are and Are Not For

| Use | Safe? |
|---|---|
| Deploying a feature environment to validate behaviour with the flag on | ✅ Primary use case |
| Keeping a flag permanently off in one environment while on in another indefinitely | ❌ Anti-pattern — hides a breaking change rather than resolving it |

Flags are a **temporary testing gate**, not a substitute for compatibility work. When the feature
promotes to Staging and Production, all environments are affected regardless of what the flag was
set to during RC testing.

### Before Promoting to Staging

1. Validate the feature works with the flag on in a feature environment
2. Validate the common RC environment (all features enabled, no flags) passes client tests
3. Promote only when all validation is complete

---

## Common RC Environment

The per-feature approach sits alongside — not replacing — the existing common RC environment.
That environment is triggered by `v{version}-rc.{n}` tags and is unchanged.

See [**`feature-environments.md`**](./feature-environments.md) for the full comparison table.

---

## Alternative Approaches Considered

### Long-Running Per-Client Feature Branches

Creating a dedicated branch per client/feature and tagging that branch for deployment.

**Why ruled out**: Branch divergence, rebase burden, cherry-pick complexity, CI false confidence.
Only viable for very short-lived branches (hours, not days). Not a sustainable strategy.

### API Versioning (`/v1/` and `/v2/`)

Running multiple API versions simultaneously.

**Why not chosen now**: High code maintenance burden, permanent commitment, significant retrofit
effort. Worth revisiting if clients persistently require different API versions indefinitely.

### Feature Flag Service (Unleash, LaunchDarkly)

A dedicated flag management service with a runtime UI.

**Why not chosen now**: Introduces infrastructure dependency and operational overhead.
Per-environment Helm `--set` achieves the same result with no new tooling. Worth revisiting if
flag complexity grows significantly.

---

## Related Documentation

- [Per-Feature RC Environments](./feature-environments.md) — Full technical guide
- [RC Operations](./RC_OPERATIONS.md) — Common RC operations runbook
- [Release Process](./release-process.md) — Full pipeline reference
