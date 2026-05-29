# Release Candidate (RC) Process

**Last Updated**: 29 May 2026

---

## Overview

Release Candidates are stable snapshots of `main` deployed to a dedicated environment in the UAT
cluster, giving external clients a chance to test against an upcoming release before it reaches
Staging. An RC is triggered by pushing a tag matching `v*-rc.*` (e.g. `v1.2.3-rc.1`) from `main`.

The RC environment:
- Runs at a fixed Helm release name (`laa-data-access-api-rc`) alongside PR preview deployments
- Gets its own dedicated Bitnami PostgreSQL instance (ephemeral, isolated from UAT RDS)
- Uses the `rc` Spring profile — see `application-rc.yaml` for the configuration
- Scales more generously than PR previews (see `rc.yaml` values)
- Is deployed only when the tag originates from `main`

For **per-feature testing** before tagging a common RC, see
[`feature-environments.md`](./feature-environments.md).

For how this fits into the full release pipeline, see [`release-process.md`](./release-process.md).

---

## Documents

### [`feature-environments.md`](./feature-environments.md) — Per-feature RC environments
How to spin up an isolated environment for a named feature via `workflow_dispatch`. Covers inputs,
feature flag wiring, infrastructure, and cleanup. **Start here** if you need to test a specific
feature in isolation before the common RC.

### [`RC_WORKFLOW.md`](./RC_WORKFLOW.md) — How to create and manage a common RC
Step-by-step process for creating a tag, monitoring deployment, notifying clients, handling issues,
and promoting to Staging. **Start here** if you're creating a common RC.

### [`RC_OPERATIONS.md`](./RC_OPERATIONS.md) — Day-to-day operations
Deployment commands, post-deployment verification, monitoring, troubleshooting, and emergency
procedures. Keep this open during and after an RC deployment.

### [`HELM_RC_VALUES.md`](./HELM_RC_VALUES.md) — Helm configuration reference
Detailed explanation of every setting in `rc.yaml` and `rc-feature.yaml`, the Bitnami PostgreSQL
configuration, and how RC compares to other environments.

### [`CLIENT_INTEGRATION_GUIDE.md`](./CLIENT_INTEGRATION_GUIDE.md) — For external clients
What RC is, how to connect, what to test, and how to report issues. Send this to clients when an RC
is available.

### [`per-client-integration.md`](./per-client-integration.md) — Feature isolation strategy
Strategy and rationale for per-feature environments: why feature flags are required, why individual
databases are non-negotiable, and alternatives considered.

### [`release-process.md`](./release-process.md) — Full pipeline reference
Documents every environment (UAT preview, RC, Staging, Production) — triggers, Spring profiles,
database setup, and the environment summary table.

---

## Configuration Files

| File | Purpose |
|------|---------|
| `.helm/data-access-api/values/rc.yaml` | Helm values for the common RC deployment (resources, scaling, ingress, Sentry) |
| `.helm/data-access-api/values/rc-feature.yaml` | Helm values shared by all per-feature RC environments |
| `.helm/bitnami_postgres/values.yaml` | Bitnami PostgreSQL configuration shared by RC and PR preview deployments |
| `data-access-service/src/main/resources/application-rc.yaml` | Spring Boot profile for RC — connects to the Bitnami PostgreSQL instance via `DB_HOST`/`DB_NAME`/`DB_PASSWORD` |
| `.github/actions/deploy_branch/action.yml` | Composite action that deploys RC and PR previews; sets `SPRING_PROFILE=rc` for common RC tags, `rc-feature` for feature environments |
| `.github/workflows/build-test-deploy.yml` | Workflow — `deploy-rc` triggers on `v*-rc.*` tags from `main`; `deploy-rc-feature` triggers on `workflow_dispatch` with a `feature-name` input |

---

## Key Behaviours

**Tag must originate from `main`**  
The `deploy-rc` job condition checks `github.event.base_ref == 'refs/heads/main'`. Tags pushed from
feature branches are ignored.

**Fixed release name (common RC)**  
RC always deploys as `laa-data-access-api-rc` (via `release-name-override`). Re-deploying a new RC
tag upgrades the existing release in place rather than creating a new one.

**Dynamic release name (feature environments)**  
Each feature environment uses `laa-data-access-api-rc-{feature-name}` as its release name,
derived from the `workflow_dispatch` input.

**Dedicated ephemeral database**  
Each RC deployment (common and feature) gets a fresh Bitnami PostgreSQL instance. Data does not
persist between RC releases.

**No smoke tests**  
Smoke tests run for UAT (PR and `main`) but not for RC or feature environments. Manual
verification is expected after deployment — see the post-deployment checklist in `RC_WORKFLOW.md`.

---

## Quick Reference

```bash
# Create and deploy a common RC
git checkout main && git pull
git tag -a v1.2.3-rc.1 -m "Release candidate for v1.2.3"
git push origin v1.2.3-rc.1

# Check common RC deployment
kubectl get pods -n laa-data-access-api-uat -l app.kubernetes.io/instance=laa-data-access-api-rc

# Deploy a per-feature environment (via GitHub Actions UI)
# Actions → Deploy with Helm → Run workflow
# feature-name: schema-v2
# feature-flag: schemaV2
# feature-flag-value: true

# Clean up a feature environment after testing
helm uninstall laa-data-access-api-rc-schema-v2 -n laa-data-access-api-uat
helm uninstall laa-data-access-api-rc-schema-v2-postgresql -n laa-data-access-api-uat

# Clean up common RC after promotion to Staging
helm uninstall laa-data-access-api-rc -n laa-data-access-api-uat
```
