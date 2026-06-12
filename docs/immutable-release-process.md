# Immutable Release Process

## Purpose

This project now promotes immutable container artifacts through deployment environments.

Instead of deploying by mutable image tag only, deployment workflows resolve the built image digest and deploy using:

- `repository@sha256:<digest>` for `data-access-api`
- matching SHA-based tag for `mass-generator`

This ensures the exact same build is promoted across environments.

## Workflows

### PR workflow

- File: `.github/workflows/deploy-pr-ephemeral.yml`
- Trigger: pull request
- Flow:
  1. Build, test, scan, push image
  2. Deploy ephemeral environment
  3. Run smoke tests

### Main workflow

- File: `.github/workflows/deploy-main-uat-rc.yml`
- Trigger: push to `main`
- Flow:
  1. Build, test, scan, push image
  2. Resolve pushed image digest from ECR
  3. Deploy UAT using resolved immutable image
  4. Run UAT smoke tests
  5. Deploy RC feature matrix using same immutable image

### Tag workflow

- File: `.github/workflows/deploy-tag-staging-production.yml`
- Trigger: push tag matching `v*`
- Flow:
  1. Validate tag commit is reachable from `main`
  2. Build, test, scan, push image
  3. Resolve pushed image digest from ECR
  4. Deploy staging with immutable image
  5. Run staging smoke tests (blocking)
  6. Deploy production with the exact same immutable image
  7. Run production smoke tests

## Security controls

- Tag ancestry validation blocks releases from unmerged branches.
- Staging smoke tests gate production deployment.
- Promotion concurrency is serialized for tag workflow.
- AWS authentication uses OIDC (`id-token: write`) rather than long-lived static cloud keys.

## Implementation notes

- Reusable build workflow: `.github/workflows/build-test-docker.yml`
- Deploy actions supporting immutable metadata:
  - `.github/actions/deploy/action.yml`
  - `.github/actions/deploy_branch/action.yml`
  - `.github/actions/deploy_feature_branch/action.yml`
- Helm chart supports digest deployment in:
  - `.helm/data-access-api/templates/deployment.yaml`

## Operational guidance

1. Create release tags from commits already merged into `main`.
2. Use signed tags where possible.
3. Treat digest resolution failure as release-blocking.
4. Keep smoke tests blocking between environment promotions.

## Related docs

- `docs/release-runbook.md` - proposed operational runbook for RC and final releases via GitHub UI.
- `docs/release-next-steps.md` - proposed phased implementation plan for RC-tag-driven promotion.
