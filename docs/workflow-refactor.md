# GitHub Actions Workflow Refactor

> **Commit:** `315e1b41` — `ci: Refactor github actions and workflows to be reusuable and clearer`
> **Branch:** `DSTEW-workflow-refactor`
> **Date:** 4 June 2026

---

## Overview

The CI/CD pipeline was split from a single monolithic workflow into two purpose-built workflows, and several repeated steps were extracted into reusable composite actions. The legacy monolithic workflow has since been removed.

```
Before                              After
──────────────────────────────────  ──────────────────────────────────────────────────
build-test-deploy.yml               build-and-test-pr.yml   ← triggered on PRs
(one file, all events)              deploy-main.yml         ← triggered on push to main
```

---

## New Workflows

### `build-and-test-pr.yml`
**Trigger:** `pull_request`

Runs the full build-and-test pipeline on every PR, then pushes Docker images if all checks pass.

| Job | Depends on | What it does |
|-----|-----------|--------------|
| `build-test` | — | Gradle build + unit/integration tests, publishes API models package as a `SNAPSHOT`, uploads JARs + reports as artifacts |
| `vulnerability-scan-app` | `build-test` | Snyk code scan (high severity threshold) |
| `vulnerability-scan-docker` | `build-test` | Builds Docker image locally and runs Snyk container scan |
| `build-push-docker` | `build-test`, both Snyk jobs | Downloads JAR artifact, logs in to ECR, builds & pushes `data-access-service` image |
| `build-push-mass-generator-docker` | `build-test`, both Snyk jobs | Downloads JAR artifact, builds & pushes `data-access-mass-generator` image (tag suffix `-mass-generator`) |

**Key details:**
- API models published with a `-SNAPSHOT` version suffix on PR builds.
- All jobs skip branches starting with `openapi/`.
- Concurrency group cancels in-progress runs on the same branch.

---

### `deploy-main.yml`
**Trigger:** `push` to `main` or `workflow_dispatch`

Full pipeline: build → scan → push Docker → deploy to UAT → smoke test → deploy staging → deploy production.

| Job | Depends on | What it does |
|-----|-----------|--------------|
| `build-test` | — | Same as PR workflow (no `-SNAPSHOT` suffix on version) |
| `vulnerability-scan-app` | `build-test` | Snyk code scan |
| `vulnerability-scan-docker` | `build-test` | Snyk Docker scan |
| `build-push-docker` | `build-test` | Builds & pushes main service image |
| `build-push-mass-generator-docker` | `build-test` | Builds & pushes mass-generator image |
| `deploy-uat` | both push jobs | Deploys to UAT using **`deploy-branch-main`** action (autoscaling on, Spring profile `main`) |
| `uat-smoke-test` | `deploy-uat` | Runs smoke tests against UAT |
| `deploy-staging` | `deploy-uat` | Deploys to staging namespace |
| `deploy-production` | `deploy-staging` | Deploys to production namespace |

---

## New Composite Actions

### `.github/actions/aws-ecr-login`
Extracts the repeated two-step ECR login pattern (assume AWS role → `amazon-ecr-login`) into a single reusable action.

**Inputs:** `ecr-region`, `ecr-role-to-assume`
**Outputs:** `registry` (ECR registry URL)

Previously, each action that needed ECR access (`build_and_push`, `deploy`, `deploy_branch`) contained these two steps inline. They now all delegate to `aws-ecr-login`.

---

### `.github/actions/get-release-name`
Consolidates two separate actions (`get_release_name` and `get_release_name_to_delete`) into a single unified action.

**Input:** `event-type` — one of `pr`, `delete`, or `auto` (default). When set to `auto`, the event type is inferred from `github.event_name`.

**Branch extraction logic:**

| Event type | Branch source |
|-----------|--------------|
| `delete` | `github.event.ref` |
| `pr` | `github.head_ref` |
| default | `github.ref` (strips `refs/heads/` prefix) |

**Outputs:** `branch-name`, `release-name`, `merged-branch-name`, `release-name-to-delete`

The `cleanup_branch` action now passes `event-type: 'delete'` explicitly instead of using the old dedicated action.

---

### `.github/actions/deploy-branch-main`
Deploys the **`main` branch** to a Kubernetes namespace — no ephemeral database, autoscaling enabled.

**Inputs:** ECR credentials, Kubernetes credentials (`kube-cert`, `kube-token`, `kube-cluster`, `kube-namespace`), `app-environment`  
**Outputs:** `branch-name`, `release-name`

**Spring profile selection:**

| `app-environment` | Spring profile |
|------------------|---------------|
| `production` | `main` |
| anything else | `unsecured` |

Deploys two Helm releases:
1. `data-access-api` — with `autoscaling.enabled=true`
2. `mass-generator` — no PostgreSQL reference (DB managed externally for main)

---

### `.github/actions/deploy-ephemeral-release`
Deploys a **feature branch** to a Kubernetes namespace with an ephemeral PostgreSQL database for preview/testing.

**Inputs:** Same as `deploy-branch-main` + optional `spring-profile` (default: `preview`)  
**Outputs:** `branch-name`, `release-name`

**Spring profile:** Configurable via `spring-profile` input (default: `preview`)

Deploys three Helm releases in order:
1. **`{release-name}-postgresql`** — Bitnami PostgreSQL chart (PostgreSQL 17.2), ephemeral
2. **`data-access-api`** — with `autoscaling.enabled=false` and `spring.profile=${SPRING_PROFILE}`
3. **`{release-name}-mass-generator`** — with `db.postgresqlReleaseName` pointing at the ephemeral DB

---

## Updated Existing Actions

| Action | Change |
|--------|--------|
| `build_and_push` | Replaces inline ECR steps with `aws-ecr-login`; references renamed `get-release-name` (was `get_release_name`) |
| `deploy` | Replaces inline ECR steps with `aws-ecr-login` |
| `deploy_branch` | Replaces inline ECR steps with `aws-ecr-login`; references renamed `get-release-name`; updates step ID reference `login-ecr` → `ecr-login` |
| `cleanup_branch` | References renamed `get-release-name` with explicit `event-type: 'delete'` |

---

## Deployment Behaviour by Branch

```
Event                     Workflow triggered          Deploy action used
───────────────────────────────────────────────────────────────────────────
PR opened/updated         build-and-test-pr.yml       (no deploy — build/test/push only)
Push to main              deploy-main.yml             deploy-branch-main (UAT/staging/prod)
```

---

## Rationale

- **Separation of concerns** — PRs no longer trigger full deployments; only merges to `main` do.
- **Reusability** — ECR login and release-name extraction are no longer copy-pasted across four actions.
- **Clarity** — Branch vs. main deployments use distinct actions with explicit intent (`deploy-ephemeral-release` vs. `deploy-branch-main`).
- **Ephemeral environments** — Feature branch deployments now automatically provision a scoped PostgreSQL instance, enabling independent testing without a shared database.
