# CI/CD - Deployment

## Continuous Integration (CI)
When a developer opens a Pull Request:
- `deploy-ephemeral-pr.yml` is triggered
- The workflow:
  - Builds the application and runs unit/integration tests
  - Runs Snyk vulnerability scans (code and Docker image)
  - Pushes Docker images to ECR (after scans pass)
  - Deploys an ephemeral PostgreSQL + app release via Helm
  - Runs smoke tests against the ephemeral release

The standalone `build-and-test-pr.yml` workflow is kept for manual runs (workflow_dispatch).

## Continuous Delivery/Deployment (CD)
When code is merged to `main`:
- `deploy-main.yml` is triggered
- The workflow:
  - Builds and tests the application
  - Runs Snyk vulnerability scans (code and Docker image)
  - Pushes Docker images to ECR (after scans pass)
  - Deploys to **UAT** using Helm (with autoscaling enabled, no ephemeral DB)
  - Runs smoke tests against UAT
  - Deploys to **staging**
  - Deploys to **production**

See [workflow-refactor.md](./workflow-refactor.md) for full details of the refactor.

# Helm

## Chart
1. Created the helm chart
    - `helm create .helm/data-access-api`
2. Created the helm values per namespace
    - `.helm/data-access-api/values/*.yaml`
    - and moved shared deployment values to `.helm/data-access-api/templates/deployment.yaml`

## Linting
To identify linting errors you can run:
- `helm lint .helm/data-access-api --values .helm/data-access-api/values/uat.yaml`

## Dry-run
To view the resulting yaml files that will be gnenerated by the helm templating engine but without actually installing/upgrading you can run:
- `helm template data-access-api .helm/data-access-api --values .helm/data-access-api/values/uat.yaml --dry-run --validate`
