# CI/CD - Deployment

## Continuous Integration (CI)
When a developer pushes code to GitHub:
- GitHub Actions triggers a workflow
- The workflow:
  - Builds the application
  - Runs unit tests and linting
  - Pushes the Docker image to a container registry

## Continuous Delivery/Deployment (CD)
Once the image is built and tested:
- GitHub Actions triggers a deployment workflow
- The workflow:
  - Uses Helm to upgrade or install the application in the Kubernetes cluster
  - Passes values to the Helm chart

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
