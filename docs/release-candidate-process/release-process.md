# Release Process

## Overview

The release process is managed entirely through GitHub Actions. Pull requests get their own
isolated preview environment. Promotion to staging and production is gated behind
**manually pushed release tags** — nothing beyond UAT deploys automatically.

For feature-specific testing before tagging an RC, see
[Per-Feature RC Environments](feature-environments.md).

---

## Workflows

| Workflow | Trigger | Purpose |
|---|---|---|
| **CI** (`ci.yml`) | PR, push to `main`, `workflow_dispatch` | Build, test, scan, deploy preview/UAT/feature environments, run smoke tests |
| **Deploy Release Candidate** (`deploy-rc.yml`) | `push: tags: v*-rc.*` | Build, deploy to RC environment, run smoke tests |
| **Deploy Release** (`deploy-release.yml`) | `push: tags: v*` (no `-rc.`) | Build, validate tag is on main, deploy to staging then production |
| **Delete preview deployment** (`delete-preview-release.yml`) | PR closed/merged | Tear down the PR preview environment |

---

## 1. Developer Workflow (Pull Requests)

### Creating a PR

1. Create a branch from `main` and push your changes.
2. Open a pull request against `main`. The PR template prompts you to:
   - Link the Jira story (`DSTEW-XXX`)
   - Confirm tests pass: `./gradlew test integrationTest`
   - Confirm CheckStyle passes: `./gradlew checkStyleMain`
   - Rebase against `main` with no conflicts
   - Review the diff and commit messages

### What Happens Automatically (on PR open/push)

The **CI** workflow is triggered and runs the following jobs **in parallel** after a
successful build:

| Job | Description |
|-----|-------------|
| `build-test` | Compiles, runs unit tests, integration tests. Publishes an API models SNAPSHOT package. Uploads JARs, checkstyle, test, and Jacoco reports as artifacts. |
| `vulnerability-scan-app` | Runs Snyk code analysis (`--severity-threshold=high`) against the application source. |
| `vulnerability-scan-docker` | Builds the Docker image locally and runs a Snyk container scan. |
| `build-push-docker` | Builds the service Docker image and pushes it to AWS ECR, tagged with the Git SHA. |

> **Note:** Branches prefixed `openapi/` skip the `build-test` job entirely.

---

## 2. Preview Environments (Pull Requests)

**Trigger:** Every pull request opened or updated.

### Deployment (`deploy-preview`)

After all build/scan jobs succeed, `deploy-preview` runs in the **`uat` GitHub environment**:

- Uses the `deploy_branch` composite action with `app-environment: preview`.
- Derives a Helm **release name** from the branch name: lowercased, spaces/slashes/dots replaced
  with `-`, truncated to **15 characters**, trailing `-` stripped
  (e.g. `feature/my-change` → `feature-my-cha`).
- Spins up a **dedicated Bitnami PostgreSQL** instance (`<release>-postgresql`) using the
  `preview` Spring profile.
- Creates branch-specific ingress hostnames:
  - **External:** `<release>-uat.cloud-platform.service.justice.gov.uk`
  - **Internal:** `<release>-internal-uat.internal-non-prod.cloud-platform.service.justice.gov.uk`

### Smoke Tests (`smoke-test-preview`)

Runs automatically after deployment via the reusable `smoke-test.yml` workflow:

1. Authenticates to the Kubernetes cluster.
2. Port-forwards to the **Bitnami PostgreSQL** pod.
3. Port-forwards to the **application** pod.
4. Reads DB credentials from the Kubernetes secret and configures environment variables.
5. Runs `./gradlew :data-access-service:infrastructureTest` against the live preview deployment.
6. Uploads the smoke test report as artifact `smoke-test-report-preview` (retained 14 days).
7. Cleans up port-forward resources.

### Preview Cleanup

When a PR is **merged or closed**, the **Delete preview deployment** workflow tears down all
resources created for that branch:

1. Helm uninstall — application release
2. Helm uninstall — Bitnami PostgreSQL release (`<release>-postgresql`) *(skipped if not present)*
3. `kubectl delete pvc` — persistent volume claim for the PostgreSQL instance *(skipped if not present)*
4. Helm uninstall — mass-generator release (`<release>-mass-generator`) *(skipped if not present)*

---

## 3. UAT (Main Branch)

**Trigger:** Push to `main`.

### Deployment (`deploy-uat`)

After all build/scan jobs succeed, `deploy-uat` runs in the **`uat` GitHub environment**:

- Uses the `deploy_branch` composite action with `app-environment: uat`.
- Deploys to a fixed Helm release name derived from the branch (resolves to `main`).
- Uses the **shared RDS PostgreSQL** instance with the `unsecured` Spring profile.
- Creates fixed ingress hostnames in the UAT namespace.

### Smoke Tests (`smoke-test-uat`)

Runs automatically after deployment via the reusable `smoke-test.yml` workflow.
Same steps as preview, except port-forwards to the **RDS port-forward proxy pod** rather
than a Bitnami instance. Uploads artifact `smoke-test-report-uat`.

---

## 4. Release Candidate (RC)

**Trigger:** Manually pushing a tag matching `v*-rc.*` (e.g. `v1.2.0-rc.1`) from `main`.

Handled by the **Deploy Release Candidate** (`deploy-rc.yml`) workflow — entirely separate
from the CI workflow so it does not appear alongside PR runs in the Actions tab.

- Runs build, test, scan, and Docker push jobs.
- Deploys to the **`uat` GitHub environment** using the fixed Helm release name
  `laa-data-access-api-rc` (via `release-name-override`).
- Gets its own **dedicated Bitnami PostgreSQL** instance (`laa-data-access-api-rc-postgresql`).
- Uses the `rc` Spring profile (`application-rc.yaml`).
- Uses the `rc` Helm values file.
- Runs **smoke tests automatically** (`smoke-test-rc`) after deployment via the reusable
  `smoke-test.yml` workflow. Uploads artifact `smoke-test-report-rc`.

For per-feature RC environments (deployed via `workflow_dispatch` in the CI workflow), smoke
tests also run automatically as `smoke-test-rc-feature`. See
[Per-Feature RC Environments](feature-environments.md).

---

## 5. Staging

**Trigger:** Manually pushing a release tag matching `v*` that does **not** contain `-rc.`
(e.g. `v1.2.0`).

Handled by the **Deploy Release** (`deploy-release.yml`) workflow.

- Runs in the **`staging` GitHub environment**.
- Does **not** run automatically on merge to `main` — a tag must be pushed explicitly.
- The `staging` environment should be configured with **Required reviewers** in
  _GitHub → Settings → Environments → staging_ to provide a manual approval gate.
- Runs `validate-release-on-main` to confirm the tagged commit is reachable from `main`
  before any deployment proceeds.
- Uses the `deploy` composite action (fixed Helm release name `laa-data-access-api`).
- Spring profile: `unsecured`.
- Fetches the LAA IP allowlist and applies it to the ingress.
- Uses the `staging` Helm values file.

---

## 6. Production

**Trigger:** Same release tag as staging (`v*`, no `-rc.`), after staging succeeds.

- Runs after `deploy-staging` succeeds (`needs: deploy-staging`).
- Runs in the **`production` GitHub environment**, which should be configured with
  **Required reviewers**.
- Uses the `deploy` composite action with the `production` values file.
- Spring profile: `main` (security enabled).
- Same ECR image as staging (same Git SHA).

---

## Environment Summary

| Environment | Workflow | Trigger | Spring Profile | Database | Smoke Tests |
|-------------|---------|---------|----------------|----------|-------------|
| **Preview (PR)** | `ci.yml` | Pull request | `preview` | Bitnami (per-PR, ephemeral) | ✅ `smoke-test-preview` |
| **UAT (main)** | `ci.yml` | Push to `main` | `unsecured` | Shared RDS | ✅ `smoke-test-uat` |
| **RC (feature)** | `ci.yml` | `workflow_dispatch` | `rc-feature` | Bitnami (per-feature, ephemeral) | ✅ `smoke-test-rc-feature` |
| **RC (common)** | `deploy-rc.yml` | Tag `v*-rc.*` | `rc` | Bitnami (dedicated, ephemeral) | ✅ `smoke-test-rc` |
| **Staging** | `deploy-release.yml` | Tag `v*` | `unsecured` | Shared RDS | ❌ |
| **Production** | `deploy-release.yml` | Tag `v*` after staging | `main` | Shared RDS | ❌ |

---

## Release Tag Convention

| Pattern | Workflow | Target |
|---------|---------|--------|
| `v1.2.0-rc.1` | `deploy-rc.yml` | RC environment (`laa-data-access-api-rc`) |
| `v1.2.0` | `deploy-release.yml` | Staging → Production |

Per-feature RC environments are **not tag-triggered** — they are deployed via `workflow_dispatch`
in the CI workflow. See [Per-Feature RC Environments](feature-environments.md).

---

## Artifacts & Reports

| Artifact | Workflow | Retention |
|----------|---------|-----------|
| Service JAR | `ci.yml`, `deploy-rc.yml`, `deploy-release.yml` | 1 day |
| Mass-generator JAR | `ci.yml` | 1 day |
| Checkstyle report | `ci.yml` | 14 days |
| Unit test report | `ci.yml` | 14 days |
| Jacoco coverage report | `ci.yml` | 14 days |
| `smoke-test-report-preview` | `ci.yml` | 14 days |
| `smoke-test-report-uat` | `ci.yml` | 14 days |
| `smoke-test-report-rc-feature` | `ci.yml` | 14 days |
| `smoke-test-report-rc` | `deploy-rc.yml` | 14 days |

---

## Dependency Management

Renovate Bot runs on a schedule (see `renovate-schedule.yml`) to automatically raise dependency
update PRs. It uses a minimum release age of **3 days** before raising a PR, reducing the risk of
picking up immediately-broken releases.
