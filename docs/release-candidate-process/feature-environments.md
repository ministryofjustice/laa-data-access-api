# Per-Feature RC Environments

## Overview

Per-feature RC environments allow developers to deploy an isolated instance of the API for a
specific feature under development — with its own ephemeral database and optional feature flags
enabled — without needing to tag a release or create any additional config files.

They sit alongside the existing common RC environment in the overall promotion flow:

```
PR preview  →  [merge to main]  →  UAT (main branch)
                                         │
                          ┌──────────────┴──────────────┐
                          │                             │
              workflow_dispatch                    git tag v{n}-rc.{n}
          Per-feature RC environment            Common RC environment
          (isolated, feature-flagged)           (all features, pre-staging gate)
                          │                             │
                          └──────────────┬──────────────┘
                                         │
                                   git tag v{n}
                               Staging → Production
```

---

## Triggering a Feature Environment

Feature environments are deployed via **GitHub Actions → workflow_dispatch** (the "Run workflow"
button). No tag is needed.

### Inputs

| Input | Required | Description | Example |
|---|---|---|---|
| `feature-name` | ✅ | Unique name for this environment. Becomes the Helm release name suffix and part of the URL. | `schema-v2` |
| `feature-flag` | ❌ | A single feature flag key to enable for this environment. | `schemaV2` |
| `feature-flag-value` | ❌ | Value for the flag above. Defaults to `true`. | `true` / `false` |

### Example

Deploying a feature environment for a new schema with the `schemaV2` flag enabled:

```
feature-name:       schema-v2
feature-flag:       schemaV2
feature-flag-value: true
```

This deploys to:
- **Release name**: `laa-data-access-api-rc-schema-v2`
- **External URL**: `laa-data-access-api-rc-schema-v2-uat.cloud-platform.service.justice.gov.uk`
- **Internal URL**: `laa-data-access-api-rc-schema-v2-internal-uat.internal-non-prod.cloud-platform.service.justice.gov.uk`
- **Spring profile**: `rc-feature`
- **Env var in pod**: `FEATURE_SCHEMAV2=true`

---

## How Feature Flags Work

Feature flags are passed through the deployment pipeline as follows:

```
workflow_dispatch input
  feature-flag=schemaV2, feature-flag-value=true
      │
      ▼
deploy_branch action
  helm upgrade ... --set featureFlags.schemaV2=true
      │
      ▼
_envs.tpl (Helm template)
  iterates .Values.featureFlags map
  → FEATURE_SCHEMAV2=true (env var in pod)
      │
      ▼
Spring application
  feature.schema-v2: ${FEATURE_SCHEMAV2:false}
```

The `featureFlags` values block in `rc-feature.yaml` is intentionally left empty (`{}`).
Flags are always passed dynamically at deploy time — no per-feature values file is needed.

### Adding a new flag to the application

For a flag to be readable by Spring, it must be declared in `application.yml`:

```yaml
# data-access-service/src/main/resources/application.yml
feature:
  disable-security: ${FEATURE_DISABLE_SECURITY:false}
  schema-v2: ${FEATURE_SCHEMAV2:false}   # ← add your flag here with a safe default
```

The flag key passed to `workflow_dispatch` (`schemaV2`) is uppercased and prefixed with
`FEATURE_` to produce the env var name (`FEATURE_SCHEMAV2`). The Spring property name uses
the standard relaxed binding — `feature.schema-v2`, `feature.schemaV2`, and
`FEATURE_SCHEMAV2` all map to the same property.

---

## Infrastructure

Each feature environment gets:

- Its own **Bitnami PostgreSQL** instance (Helm release: `laa-data-access-api-rc-{name}-postgresql`)
- Its own **ingress** with an isolated URL
- Spring profile `rc-feature` (shared by all feature environments, distinct from preview/rc)
- Resource limits from `.helm/data-access-api/values/rc-feature.yaml` (same as common RC)
- **No autoscaling** — single replica, suitable for integration/feature testing

---

## Shared Values File

All feature environments share a single Helm values file: `rc-feature.yaml`. There is **no
per-feature values file**. Resource limits, ingress config, and Sentry settings are all
inherited from this shared file.

If a feature environment needs different resources or settings, that is a signal it should be
promoted to staging rather than tested in RC.

---

## Common RC Environment (unchanged)

The tag-triggered common RC environment continues to operate as before and is unaffected by
this change.

| | Per-Feature RC | Common RC |
|---|---|---|
| **Trigger** | `workflow_dispatch` | `git tag v{n}-rc.{n}` |
| **Release name** | `laa-data-access-api-rc-{feature}` | `laa-data-access-api-rc` |
| **Values file** | `rc-feature.yaml` | `rc.yaml` |
| **Spring profile** | `rc-feature` | `rc` |
| **Purpose** | Isolate a single feature under development | Final pre-staging gate with full feature set |
| **Autoscaling** | No | Yes (1–5 replicas) |

Tag a common RC once all feature branches are merged to `main` and you are ready for final
pre-staging sign-off.

---

## Cleaning Up

Feature environments are **not automatically cleaned up**. Once testing is complete, delete
the Helm releases manually:

```bash
helm uninstall laa-data-access-api-rc-schema-v2 -n <namespace>
helm uninstall laa-data-access-api-rc-schema-v2-postgresql -n <namespace>
```

Or trigger the existing `cleanup_branch` action if it has been extended to support RC feature
release names.

