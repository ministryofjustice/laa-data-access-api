# CI/CD – Deployment

## Overview

The pipeline is defined in `.github/workflows/build-test-deploy.yml` and uses GitHub Actions with Helm to build, test, and deploy the application. Two Docker images are produced per commit: one for `data-access-service` and one for `data-access-mass-generator`. Both are pushed to AWS ECR tagged with the Git commit SHA.

There is no tag-based RC promotion flow.  Feature preset deployments are gated by a manual approval step in the workflow. Deployments are otherwise driven by branch events.

---

## Triggers

| Event | Effect |
|---|---|
| `pull_request` (opened / updated) | Build, test, scan, push images, deploy ephemeral PR environment, run smoke tests |
| `push` to `main` | Build, test, scan, push images, deploy UAT + Staging + Production, run smoke tests, deploy RC feature preset environments |
| `workflow_dispatch` | Runs the workflow; jobs guarded with `if: github.ref == 'refs/heads/main'` only deploy when dispatched from `main` |
| `pull_request` (closed / merged) | Tear down the ephemeral PR environment |

A `concurrency` group keyed to `workflow + ref` ensures that a new run cancels any in-progress run for the same branch.

---

## Pipeline jobs

### On pull request

```
build-test
  ├── vulnerability-scan-app
  ├── vulnerability-scan-docker
  └── build-push-docker
        └── build-push-mass-generator-docker
              └── deploy-ephemeral        ← ephemeral PR environment (UAT namespace)
                    └── ephemeral-smoke-test
```

`openapi/` branches skip `build-test` and `ephemeral-smoke-test` entirely.

### On push to `main`

```
build-test
  ├── vulnerability-scan-app
  ├── vulnerability-scan-docker
  └── build-push-docker ──────────────────────────────────────────┐
        └── build-push-mass-generator-docker                      │
              ├── deploy-uat   (UAT namespace, `main` release)    │
              │     └── uat-smoke-test                            │
              │           └── base-resolve-presets                │
              │                 └── approve-matrix-rc-feature     │
              │                       └── deploy-matrix-rc-feature│
              └── deploy-staging  ◄──────────────────────────────┘
                    └── deploy-production
```

`deploy-staging` runs in parallel with the UAT / RC-feature chain; it does not wait for smoke tests. `deploy-production` requires `deploy-staging` to succeed and has a manual approval gate (`environment: production`).

---

## Environment types

### 1. Pull request (ephemeral)

- **Triggered by:** `pull_request` events
- **Helm values:** `.helm/data-access-api/values/uat.yaml`
- **Spring profile:** `preview`
- **Release name:** derived from the branch name (sanitised for Kubernetes)
- **Database:** ephemeral Bitnami PostgreSQL deployed alongside the application, cleaned up with it
- **Hostname pattern:** `<release-name>-<namespace>.cloud-platform.service.justice.gov.uk`
- **Autoscaling:** disabled (single replica)
- **Lifecycle:** created when a PR is opened or updated; torn down when the PR is closed or merged (the `delete-preview-release.yml` workflow uninstalls the Helm release, the PostgreSQL release, its PVC, and the mass-generator release)
- **Smoke tests:** run against the ephemeral deployment via in-cluster port-forwards to both the app and its ephemeral PostgreSQL

### 2. UAT

- **Triggered by:** push to `main`
- **Helm values:** `.helm/data-access-api/values/uat.yaml`
- **Spring profile:** `unsecured`
- **Release name:** `laa-data-access-api` (fixed, not branch-derived)
- **Database:** shared RDS instance, accessed via an in-cluster port-forward pod (`run=port-forward-pod`)
- **Hostname:** set by the deploy action; external and internal ingresses both active
- **Autoscaling:** enabled (1–5 replicas)
- **Smoke tests:** run after UAT deploy succeeds; required before RC feature environments are deployed

### 3. RC feature environments

- **Triggered by:** push to `main`, after UAT smoke tests pass and manual approval is granted
- **Helm values:** `.helm/data-access-api/values/rc-feature.yaml`
- **Spring profile:** `preview`
- **Release names:** derived from the preset catalog (see below)
- **Database:** one ephemeral Bitnami PostgreSQL per environment (isolated per release)
- **Autoscaling:** disabled
- **Lifecycle:** re-deployed on every push to `main`; no automatic teardown — manual cleanup required

### 4. Staging

- **Triggered by:** push to `main` (in parallel with UAT deploy, not gated on smoke tests)
- **Helm values:** `.helm/data-access-api/values/staging.yaml`
- **Spring profile:** `unsecured`
- **Release name:** `laa-data-access-api` (fixed)
- **Replicas:** 2 (autoscaling 2–8)
- **Sentry environment:** `staging`

### 5. Production

- **Triggered by:** `deploy-staging` succeeding, with a manual approval gate
- **Helm values:** `.helm/data-access-api/values/production.yaml`
- **Spring profile:** `main`
- **Release name:** `laa-data-access-api` (fixed)
- **Replicas:** 2 (autoscaling 2–8)
- **External ingress:** disabled — internal ingress only (`internal.cloud-platform.service.justice.gov.uk`)
- **Sentry environment:** `production`
- **Mass generator:** disabled in production values

---

## RC feature preset environments

Feature environments are prepared on every push to `main`, then deployed only after UAT smoke tests pass and the `approve-matrix-rc-feature` manual approval gate is approved.

The approval gate uses the `trstringer/manual-approval` action and expects approvers to be configured in repository variable `RC_FEATURE_APPROVERS`.

The preset catalog lives in `.github/config/ephemeral-environment-presets.json`. Each entry defines:

| Field | Description |
|---|---|
| `suffix` | Used to derive the Helm release name (lowercased, sanitised for Kubernetes) |
| `extraEnv` | Comma-separated `UPPER_CASE_KEY=value` pairs injected as environment variables into the pod |

The current catalog:

| Preset key | Release name suffix | Extra env |
|---|---|---|
| `baseline` | `baseline` | _(none)_ |
| `FEATURE_DISABLEJPAAUDITING` | `disable-jpa-audit` | `FEATURE_DISABLEJPAAUDITING=true` |
| `FEATURE_EXAMPLE_FEATURE_FLAG` | `example-feature-flag` | `FEATURE_EXAMPLE_FEATURE_FLAG=true` |

A `sanitize_release_name` function in the workflow ensures release names are lowercase, contain only `a-z`, `0-9`, `-`, and are at most 53 characters. Names longer than that are truncated with an 8-character SHA suffix.

Each preset environment deploys the same image (the `github.sha` from the same run) using the `rc-feature.yaml` values file, with `extraEnv` values injected via `--set-string extraEnv.<KEY>=<value>`. Each gets its own isolated ephemeral PostgreSQL database.

---

## Feature flags

Feature flags are passed as environment variables, not via Helm `featureFlags` values. The `extraEnv` field in the preset catalog maps directly to `--set-string extraEnv.<KEY>=<value>` in the Helm command.

To add a new feature flag environment:

1. Add an entry to `.github/config/ephemeral-environment-presets.json`
2. Push to `main` — the new environment is deployed automatically after UAT smoke tests

To remove an environment, delete its entry from the catalog and push to `main`. The existing Helm release must be cleaned up manually (see Cleanup below).

---

## Helm chart

The chart lives at `.helm/data-access-api`.

### Values files

| File | Used by |
|---|---|
| `values/uat.yaml` | UAT and PR ephemeral environments |
| `values/rc-feature.yaml` | RC feature preset environments |
| `values/staging.yaml` | Staging |
| `values/production.yaml` | Production |

### Useful Helm commands

Lint:
```bash
helm lint .helm/data-access-api --values .helm/data-access-api/values/uat.yaml
```

Dry-run template rendering:
```bash
helm template data-access-api .helm/data-access-api \
  --values .helm/data-access-api/values/uat.yaml \
  --dry-run --validate
```

---

## Database strategy

| Environment | Database |
|---|---|
| Pull request ephemeral | Ephemeral Bitnami PostgreSQL (one per PR release) |
| UAT | Shared RDS, accessed via in-cluster port-forward pod |
| RC feature | Ephemeral Bitnami PostgreSQL (one per preset release) |
| Staging | RDS (credentials from Kubernetes secrets) |
| Production | RDS (credentials from Kubernetes secrets) |

Ephemeral PostgreSQL releases are named `<release-name>-postgresql`. On PR cleanup, the PVC is also explicitly deleted to avoid stale volume claims accumulating.

---

## Smoke tests

Smoke tests run via `./gradlew :data-access-service:infrastructureTest`. The smoke-test action:

1. Authenticates to the Kubernetes cluster
2. Port-forwards to the target PostgreSQL (ephemeral Bitnami pod for PRs; RDS port-forward pod for UAT `main`)
3. Port-forwards to the application pod
4. Resolves database credentials from the appropriate Kubernetes secret
5. Runs the Gradle infrastructure test suite
6. Uploads the test report as a GitHub Actions artifact (retained 14 days)
7. Tears down port-forward resources

---

## Cleanup

### PR ephemeral environments

Cleaned up automatically when a PR is closed or merged by `delete-preview-release.yml`:

```bash
helm uninstall <release-name> --namespace <namespace>
helm uninstall <release-name>-postgresql --namespace <namespace>
kubectl delete pvc -l app.kubernetes.io/instance=<release-name>-postgresql --namespace <namespace>
helm uninstall <release-name>-mass-generator --namespace <namespace>
```

### RC feature environments

Not automatically torn down. Manual cleanup:

```bash
helm uninstall <preset-release-name> --namespace <namespace>
helm uninstall <preset-release-name>-postgresql --namespace <namespace>
kubectl delete pvc -l app.kubernetes.io/instance=data-<preset-release-name>-postgresql --namespace <namespace>
```

Replace `<preset-release-name>` with the sanitised suffix from the preset catalog (e.g. `baseline`, `disable-jpa-audit`, `example-feature-flag`).
