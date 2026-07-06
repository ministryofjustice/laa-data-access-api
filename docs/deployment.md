# CI/CD – Deployment

## Overview

The pipeline is defined in `.github/workflows/build-test-deploy.yml` and uses GitHub Actions with Helm to build, test, and deploy the application. Two Docker images are produced per commit: one for `data-access-service` and one for `data-access-mass-generator`. Both are pushed to AWS ECR tagged with the Git commit SHA.

There is no tag-based RC promotion flow. Feature preset deployments are driven directly from `main` after UAT smoke tests succeed.

---

## Triggers

| Event | Effect |
|---|---|
| `pull_request` (opened / updated) | Build, test, scan, push images, deploy ephemeral PR environment, run smoke tests |
| `push` to `main` | Build, test, scan, push images, deploy UAT, run UAT smoke tests, then run approval-gated deploys for RC feature preset environments, Staging, and Production |
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
  ├── build-push-docker
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
  ├── build-push-docker
  └── build-push-mass-generator-docker
        └── deploy-uat   (UAT namespace, `main` release)
              ├── deploy-shared-mock-oauth2  ← shared mock-oauth2 for all PRs
              └── uat-smoke-test
                    ├── prepare-feature-environments
                    │     └── deploy-matrix-rc-feature
                    └── deploy-staging
                          └── deploy-production
```

`deploy-staging` waits for both `deploy-uat` and `uat-smoke-test`. `deploy-shared-mock-oauth2` runs after `deploy-uat` and before `uat-smoke-test`, ensuring the shared mock-oauth2 instance is available for all PR deployments. `deploy-matrix-rc-feature`, `deploy-staging`, and `deploy-production` are all approval-gated deployment steps.

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

- **Triggered by:** push to `main`, after UAT smoke tests pass
- **Approval gate:** required before `deploy-matrix-rc-feature` runs
- **Helm values:** `.helm/data-access-api/values/rc-feature.yaml`
- **Spring profile:** `preview`
- **Release names:** derived from the preset catalog (see below)
- **Database:** one ephemeral Bitnami PostgreSQL per environment (isolated per release)
- **Autoscaling:** disabled
- **Lifecycle:** re-deployed on every push to `main`; no automatic teardown — manual cleanup required

### 4. Staging

- **Triggered by:** push to `main`, after UAT deploy and UAT smoke tests succeed
- **Approval gate:** required before `deploy-staging` runs
- **Helm values:** `.helm/data-access-api/values/staging.yaml`
- **Spring profile:** `unsecured`
- **Release name:** `laa-data-access-api` (fixed)
- **Replicas:** 2 (autoscaling 2–8)
- **Sentry environment:** `staging`

### 5. Production

- **Triggered by:** `deploy-staging` succeeding
- **Approval gate:** required before `deploy-production` runs
- **Helm values:** `.helm/data-access-api/values/production.yaml`
- **Spring profile:** `main`
- **Release name:** `laa-data-access-api` (fixed)
- **Replicas:** 2 (autoscaling 2–8)
- **External ingress:** disabled — internal ingress only (`internal.cloud-platform.service.justice.gov.uk`)
- **Sentry environment:** `production`
- **Mass generator:** disabled in production values

---

## RC feature preset environments

Feature environments are prepared on every push to `main` after UAT smoke tests pass.

Deployment of the matrix environments is approval-gated before `deploy-matrix-rc-feature` runs.

Preset resolution is performed by the `prepare-feature-environments` job and passed to `deploy-matrix-rc-feature` as a GitHub Actions matrix.

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
| `DECIDE_ENV` | `decide-env` | _(none)_ |

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

## Shared mock-oauth2 server

A single shared mock-oauth2 server instance is deployed to the UAT namespace for use by all PR and feature deployments.

### Deployment

- **When:** Automatically deployed when code is merged to `main` (via the `deploy-shared-mock-oauth2` job in `.github/workflows/build-main.yml`)
- **Release name:** `laa-data-access-mock-oauth2-shared`
- **Helm chart:** `.helm/shared-mock-oauth2`
- **Namespace:** UAT (`laa-data-access-api-uat`)
- **Service URL:** `http://laa-data-access-mock-oauth2-shared.<namespace>.svc.cluster.local:9999`

### Lifecycle

The shared mock-oauth2 server:
- Stays running permanently (not deleted with PRs)
- Is upgraded/redeployed on every merge to `main`
- Provides test tokens for all PR and feature deployments
- Eliminates the need for per-PR mock-oauth2 instances

### Manual deployment

If needed outside of CI/CD:

```bash
./scripts/deploy-shared-mock-oauth2.sh [namespace]
```

Default namespace is `laa-data-access-api-uat`.

### Verification

Check the shared mock-oauth2 deployment:

```bash
kubectl get pods -n laa-data-access-api-uat -l app.kubernetes.io/name=shared-mock-oauth2
kubectl get svc -n laa-data-access-api-uat laa-data-access-mock-oauth2-shared
```

### Getting test tokens

For PR deployments, port-forward the shared service and generate tokens:

```bash
# Port-forward the shared mock-oauth2
kubectl -n laa-data-access-api-uat port-forward svc/laa-data-access-mock-oauth2-shared 9999:9999

# Get a token (in another terminal)
./scripts/get-token.sh uat --copy
```

The script now defaults to the shared mock-oauth2 service. Tokens will have the correct issuer (`http://laa-data-access-mock-oauth2-shared:9999/entra`) that matches PR deployment expectations.

See the [Authentication section in README.md](../README.md) for full details on using tokens.

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
kubectl delete pvc -l app.kubernetes.io/instance=<preset-release-name>-postgresql --namespace <namespace>
```

Replace `<preset-release-name>` with the sanitised suffix from the preset catalog (e.g. `baseline`, `disable-jpa-audit`, `example-feature-flag`).
