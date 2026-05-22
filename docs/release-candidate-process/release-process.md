# Release Process

## Overview

The release process is managed entirely through GitHub Actions. Pull requests get their own
isolated preview environment in UAT. Promotion to staging and production is gated behind
**manually pushed release tags** — nothing beyond UAT deploys automatically.

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

The `Deploy with Helm` workflow is triggered and runs the following jobs **in parallel** after a
successful build:

| Job | Description |
|-----|-------------|
| `build-test` | Compiles, runs unit tests, coverage verification, and integration tests. Publishes an API models SNAPSHOT package. Uploads JARs, checkstyle, test, and Jacoco reports as artifacts. |
| `vulnerability-scan-app` | Runs Snyk code analysis (`--severity-threshold=high`) against the application source. |
| `vulnerability-scan-docker` | Builds the Docker image locally and runs a Snyk container scan. |
| `build-push-docker` | Builds the service Docker image and pushes it to AWS ECR, tagged with the Git SHA. |
| `build-push-mass-generator-docker` | Builds the mass-generator Docker image and pushes it to ECR, tagged `<sha>-mass-generator`. |

> **Note:** Branches prefixed `openapi/` skip the `build-test` job entirely.

---

## 2. UAT (Preview Environment)

**Trigger:** Every pull request (and pushes to `main`).

### Deployment (`deploy-uat`)

After all build/scan jobs succeed, `deploy-uat` runs in the **`uat` GitHub environment**:

- Uses the `deploy_branch` composite action.
- Derives a Helm **release name** from the branch name: lowercased, spaces/slashes/dots replaced
  with `-`, truncated to **15 characters**, trailing `-` stripped
  (e.g. `feature/my-change` → `feature-my-cha`).
- For non-`main` branches, a **dedicated Bitnami PostgreSQL** instance is spun up in the cluster
  alongside the application (`<release>-postgresql`), using the `preview` Spring profile.
- For the `main` branch, the shared RDS PostgreSQL instance is used, with the `unsecured` Spring
  profile.
- Deploys both the **application** Helm chart and the **mass-generator** Helm chart with the `uat`
  values file.
- Creates branch-specific ingress hostnames:
  - **External:** `<release>-<namespace>.cloud-platform.service.justice.gov.uk`
  - **Internal:** `<release>-internal-<namespace>.internal-non-prod.cloud-platform.service.justice.gov.uk`

### Smoke Tests (`uat-smoke-test`)

After UAT deployment succeeds:

1. Authenticates to the Kubernetes cluster.
2. Port-forwards to the **Postgres** pod (Bitnami for PRs, RDS pod for `main`).
3. Port-forwards to the **application** pod.
4. Reads DB credentials from the Kubernetes secret and configures environment variables.
5. Runs `./gradlew :data-access-service:infrastructureTest` against the live UAT deployment.
6. Uploads the smoke test report as an artifact (retained 14 days).
7. Cleans up port-forward resources.

### Preview Cleanup

When a PR is **merged or closed** (without merging), the `delete-preview-release` workflow tears
down all resources created for that branch:

1. Helm uninstall — application release
2. Helm uninstall — Bitnami PostgreSQL release (`<release>-postgresql`)
3. `kubectl delete pvc` — persistent volume claim for the PostgreSQL instance
4. Helm uninstall — mass-generator release (`<release>-mass-generator`)

---

## 3. Release Candidate (RC)

**Trigger:** Manually pushing a tag matching `v*-rc.*` (e.g. `v1.2.0-rc.1`).

- Runs the same build, scan, and Docker push jobs.
- Deploys to the **`uat` GitHub environment** using the fixed Helm release name
  `laa-data-access-api-rc` (via `release-name-override`), so it sits alongside PR preview
  deployments without overwriting them.
- Gets its own **dedicated Bitnami PostgreSQL** instance (`laa-data-access-api-rc-postgresql`),
  isolated like a PR preview branch rather than sharing the UAT RDS instance.
- Uses the `rc` Spring profile (`application-rc.yaml`), which constructs the datasource URL from
  `DB_HOST` and `DB_NAME` environment variables injected by the Helm `dbConnectionDetails` template.
- Uses the `rc` Helm values file (higher resource limits than preview branches, suitable for
  client load testing).
- No smoke tests are run as part of this job.

---

## 4. Staging

**Trigger:** Manually pushing a release tag matching `v*` that does **not** contain `-rc.`
(e.g. `v1.2.0`).

- Runs in the **`staging` GitHub environment**.
- Does **not** run automatically on merge to `main` — a tag must be pushed explicitly.
- The `staging` environment should be configured with **Required reviewers** in
  _GitHub → Settings → Environments → staging_ to provide a manual approval gate before
  the deployment proceeds. Without this, staging will deploy automatically once the tag
  build and vulnerability scans pass.
- **No UAT deployment or smoke tests are run as part of this job.** When a tag is pushed,
  `deploy-uat` is skipped (its condition requires a PR or a push to `main`). Staging only
  waits for `build-push-docker`, `vulnerability-scan-app`, and `vulnerability-scan-docker`
  to succeed.
- The implicit expectation is that the tag is cut **after** the commit has already been merged
  to `main` and passed UAT smoke tests at that point. If a tag is pushed on a commit that
  has not gone through `main`, there is no automated UAT gate protecting staging.
- Uses the `deploy` composite action (fixed Helm release name `laa-data-access-api`).
- Spring profile: `unsecured`.
- Fetches the LAA IP allowlist from the MoJ GitHub repository and applies it to the ingress.
- Uses the `staging` Helm values file.

---

## 5. Production

**Trigger:** Same release tag as staging (`v*`, no `-rc.`), after staging succeeds.

- Runs after `deploy-staging` succeeds (`needs: deploy-staging`).
- Runs in the **`production` GitHub environment**, which should be configured with
  **Required reviewers** in _GitHub → Settings → Environments → production_ to provide
  a manual approval gate before the deployment proceeds.
- Uses the `deploy` composite action with the `production` values file.
- Spring profile: `main` (security enabled).
- Same ECR image as staging (same Git SHA).

---

## Environment Summary

| Environment | Trigger | Spring Profile | Database | Ingress |
|-------------|---------|----------------|----------|---------|
| **UAT (PR)** | Pull request opened/updated | `preview` | Bitnami PostgreSQL (per-PR) | Branch-specific URL |
| **UAT (main)** | Push to `main` | `unsecured` | Shared RDS | Fixed `main-<namespace>` URL |
 **RC**  Manual tag `v*-rc.*`  `rc`  Bitnami PostgreSQL (dedicated)  Fixed `laa-data-access-api-rc` 
| **Staging** | Manual tag `v*` (no `-rc.`) | `unsecured` | Shared RDS | Fixed `laa-data-access-api` |
| **Production** | Manual tag `v*` (no `-rc.`) after staging | `main` | Shared RDS | Fixed `laa-data-access-api` |

---

## Release Tag Convention

| Pattern | Target |
|---------|--------|
| `v1.2.0-rc.1` | RC → UAT environment |
| `v1.2.0` | Staging, then Production |

---

## Artifacts & Reports

All of the following are uploaded as GitHub Actions artifacts (retention: 14 days unless noted):

| Artifact | Retention |
|----------|-----------|
| Service JAR | 1 day |
| Mass-generator JAR | 1 day |
| Checkstyle report | 14 days |
| Unit test report | 14 days |
| Jacoco coverage report | 14 days |
| UAT smoke test report | 14 days |

---

## Dependency Management

Renovate Bot runs on a schedule (see `renovate-schedule.yml`) to automatically raise dependency
update PRs. It uses a minimum release age of **3 days** before raising a PR, reducing the risk of
picking up immediately-broken releases.

