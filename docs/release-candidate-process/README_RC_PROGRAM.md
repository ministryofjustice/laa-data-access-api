# Release Candidate (RC) Process

**Last Updated**: 22 May 2026

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

For how this fits into the full release pipeline, see [`release-process.md`](./release-process.md).

---

## Documents

### [`RC_WORKFLOW.md`](./RC_WORKFLOW.md) — How to create and manage an RC
Step-by-step process for creating a tag, monitoring deployment, notifying clients, handling issues,
and promoting to Staging. **Start here** if you're creating an RC.

### [`RC_OPERATIONS.md`](./RC_OPERATIONS.md) — Day-to-day operations
Deployment commands, post-deployment verification, monitoring, troubleshooting, and emergency
procedures. Keep this open during and after an RC deployment.

### [`HELM_RC_VALUES.md`](./HELM_RC_VALUES.md) — Helm configuration reference
Detailed explanation of every setting in `rc.yaml`, the Bitnami PostgreSQL configuration, and how
RC compares to other environments. Includes known limitations of the ephemeral database and the
future consideration for a persistent RDS instance.

### [`CLIENT_INTEGRATION_GUIDE.md`](./CLIENT_INTEGRATION_GUIDE.md) — For external clients
What RC is, how to connect, what to test, and how to report issues. Send this to clients when an RC
is available.

### [`release-process.md`](./release-process.md) — Full pipeline reference
Documents every environment (UAT preview, RC, Staging, Production) — triggers, Spring profiles,
database setup, and the environment summary table.

---

## Configuration Files

| File | Purpose |
|------|---------|
| `.helm/data-access-api/values/rc.yaml` | Helm values for the RC deployment (resources, scaling, ingress, Sentry) |
| `.helm/bitnami_postgres/values.yaml` | Bitnami PostgreSQL configuration shared by RC and PR preview deployments |
| `data-access-service/src/main/resources/application-rc.yaml` | Spring Boot profile for RC — connects to the Bitnami PostgreSQL instance via `DB_HOST`/`DB_NAME`/`DB_PASSWORD` |
| `.github/actions/deploy_branch/action.yml` | Composite action that deploys RC and PR previews, sets `SPRING_PROFILE=rc` for RC tags |
| `.github/workflows/build-test-deploy.yml` | Workflow — the `deploy-rc` job triggers on `v*-rc.*` tags pushed from `main` |

---

## Key Behaviours

**Tag must originate from `main`**  
The `deploy-rc` job condition checks `github.event.base_ref == 'refs/heads/main'`. Tags pushed from
feature branches are ignored.

**Fixed release name**  
RC always deploys as `laa-data-access-api-rc` (via `release-name-override`). Re-deploying a new RC
tag upgrades the existing release in place rather than creating a new one.

**Dedicated ephemeral database**  
Each RC deployment gets a fresh Bitnami PostgreSQL instance. Data does not persist between RC
releases. See `HELM_RC_VALUES.md` for the known migration testing limitations this introduces and
the future consideration for a persistent RDS instance.

**No smoke tests**  
Smoke tests run for UAT (PR and `main`) but not for RC. Manual verification is expected after
deployment — see the post-deployment checklist in `RC_WORKFLOW.md`.

---

## Quick Reference

```bash
# Create and deploy an RC
git checkout main && git pull
git tag -a v1.2.3-rc.1 -m "Release candidate for v1.2.3"
git push origin v1.2.3-rc.1

# Check deployment
kubectl get pods -n laa-data-access-api-uat -l app.kubernetes.io/instance=laa-data-access-api-rc

# Clean up after promotion to Staging
helm uninstall laa-data-access-api-rc -n laa-data-access-api-uat
```
