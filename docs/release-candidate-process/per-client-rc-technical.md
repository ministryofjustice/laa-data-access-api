# Per-Client RC Deployments - Technical README

This document describes the technical implementation of per-client Release Candidate (RC) environments, allowing different clients to test new features independently before promotion to Staging.

## Overview

Per-client RC environments provide isolated testing surfaces for individual clients (Apply, Decide) with:
- Independent feature flags per client
- Isolated temporary databases (Bitnami PostgreSQL)
- Automatic deployment triggered by client-specific Git tags

## Architecture

```
                              ┌─────────────────────────────────────┐
                              │           main branch               │
                              └──────────────┬──────────────────────┘
                                             │
                    ┌────────────────────────┼────────────────────────┐
                    │                        │                        │
                    ▼                        ▼                        ▼
         v1.3.0-rc.1-apply          v1.3.0-rc.1             v1.3.0-rc.1-decide
                    │                        │                        │
                    ▼                        ▼                        ▼
    ┌───────────────────────┐  ┌───────────────────┐  ┌───────────────────────┐
    │  rc-apply environment │  │   rc environment  │  │ rc-decide environment │
    │  ─────────────────────│  │  ─────────────────│  │  ─────────────────────│
    │  • values/rc-apply.yaml│  │  • values/rc.yaml │  │  • values/rc-decide.yaml│
    │  • Own PostgreSQL DB  │  │  • Own PostgreSQL │  │  • Own PostgreSQL DB  │
    │  • Feature flags: ON  │  │  • Standard flags │  │  • Feature flags: OFF │
    └───────────────────────┘  └───────────────────┘  └───────────────────────┘
```

## File Structure

```
.github/
├── workflows/
│   └── build-test-deploy.yml    # Contains deploy-rc-client job
└── actions/
    └── deploy_branch/
        └── action.yml           # Handles rc-* environment deployments

.helm/
└── data-access-api/
    └── values/
        ├── rc.yaml              # Standard RC environment
        ├── rc-apply.yaml        # Apply client RC environment
        └── rc-decide.yaml       # Decide client RC environment

docs/
└── release-candidate-process/
    ├── per-client-integration.md    # Strategy documentation
    └── per-client-rc-technical.md   # This file
```

## Tag Naming Convention

| Tag Pattern | Target Environment | Promotable to Staging? |
|---|---|---|
| `v{version}-rc.{n}` | Standard RC | Yes |
| `v{version}-rc.{n}-apply` | Apply client RC | No |
| `v{version}-rc.{n}-decide` | Decide client RC | No |

**Examples:**
- `v1.3.0-rc.1` → deploys to standard RC
- `v1.3.0-rc.1-apply` → deploys to Apply client RC
- `v1.3.0-rc.1-decide` → deploys to Decide client RC

## Deployment Process

### 1. Create and Push a Client-Specific Tag

```bash
# Ensure you're on main and up to date
git checkout main
git pull origin main

# Create a tag for the Apply client
git tag v1.3.0-rc.1-apply

# Push the tag to trigger deployment
git push origin v1.3.0-rc.1-apply
```

### 2. GitHub Actions Workflow

The `deploy-rc-client` job in `build-test-deploy.yml`:

1. **Triggers** when a tag matches `v*-rc.*-apply` or `v*-rc.*-decide`
2. **Uses matrix strategy** to parameterise deployment for each client
3. **Checks tag suffix** to determine which client environment to deploy
4. **Invokes** the `deploy_branch` action with client-specific parameters

```yaml
deploy-rc-client:
  if: startsWith(github.ref, 'refs/tags/v') && contains(github.ref, '-rc.') && (endsWith(github.ref, '-apply') || endsWith(github.ref, '-decide'))
  strategy:
    matrix:
      include:
        - client: apply
          tag_suffix: "-apply"
        - client: decide
          tag_suffix: "-decide"
```

### 3. Resulting Infrastructure

| Client | Helm Release Name | PostgreSQL Release | URL |
|---|---|---|---|
| Apply | `laa-data-access-api-rc-apply` | `laa-data-access-api-rc-apply-postgresql` | `laa-data-access-api-rc-apply-uat.cloud-platform.service.justice.gov.uk` |
| Decide | `laa-data-access-api-rc-decide` | `laa-data-access-api-rc-decide-postgresql` | `laa-data-access-api-rc-decide-uat.cloud-platform.service.justice.gov.uk` |

## Feature Flags Configuration

Feature flags are configured in the client-specific Helm values files:

```yaml
# .helm/data-access-api/values/rc-apply.yaml
featureFlags:
  schemaV2: true
  newEndpoint: true

# .helm/data-access-api/values/rc-decide.yaml
featureFlags:
  schemaV2: false
  newEndpoint: false
```

### Adding a New Feature Flag

1. **Add the flag to the Helm values file(s)**:
   ```yaml
   featureFlags:
     myNewFeature: true
   ```

2. **Ensure the Helm chart passes the flag to the application** (if not already configured)

3. **Add application code to check the flag**:
   ```java
   if (featureFlags.isMyNewFeatureEnabled()) {
       // new behaviour
   } else {
       // existing behaviour
   }
   ```

## Adding a New Client

To add a new client (e.g., `manage`):

### 1. Create Helm Values File

```bash
cp .helm/data-access-api/values/rc-apply.yaml .helm/data-access-api/values/rc-manage.yaml
```

Edit the new file to set appropriate values:
```yaml
# .helm/data-access-api/values/rc-manage.yaml
sentry:
  environment: rc-manage

featureFlags:
  # Set flags appropriate for this client
```

### 2. Update GitHub Actions Workflow

Add the new client to the matrix in `.github/workflows/build-test-deploy.yml`:

```yaml
strategy:
  matrix:
    include:
      - client: apply
        tag_suffix: "-apply"
      - client: decide
        tag_suffix: "-decide"
      - client: manage          # ← Add new client
        tag_suffix: "-manage"
```

Update the job's `if` condition:
```yaml
if: startsWith(github.ref, 'refs/tags/v') && contains(github.ref, '-rc.') && (endsWith(github.ref, '-apply') || endsWith(github.ref, '-decide') || endsWith(github.ref, '-manage'))
```

### 3. Deploy

```bash
git tag v1.3.0-rc.1-manage
git push origin v1.3.0-rc.1-manage
```

## Cleanup

Per-client RC environments are **temporary** and should be cleaned up after testing is complete.

### Manual Cleanup

```bash
# Authenticate to the cluster
kubectl config use-context <your-context>

# Delete the application release
helm uninstall laa-data-access-api-rc-apply -n laa-data-access-api-uat

# Delete the PostgreSQL database release
helm uninstall laa-data-access-api-rc-apply-postgresql -n laa-data-access-api-uat
```

### Verify Cleanup

```bash
# Check no releases remain
helm list -n laa-data-access-api-uat | grep rc-apply

# Check no pods remain
kubectl get pods -n laa-data-access-api-uat | grep rc-apply
```

## Troubleshooting

### Deployment Not Triggering

**Symptom:** Tag pushed but no GitHub Actions workflow runs.

**Checks:**
1. Verify tag is on `main`:
   ```bash
   git branch --contains <tag-name>
   ```
2. Verify tag format matches pattern (e.g., `v1.3.0-rc.1-apply`)
3. Check GitHub Actions tab for failed/skipped runs

### Database Connection Issues

**Symptom:** Application fails to connect to PostgreSQL.

**Checks:**
1. Verify PostgreSQL pod is running:
   ```bash
   kubectl get pods -n laa-data-access-api-uat -l app.kubernetes.io/instance=laa-data-access-api-rc-apply-postgresql
   ```
2. Check PostgreSQL logs:
   ```bash
   kubectl logs -n laa-data-access-api-uat -l app.kubernetes.io/instance=laa-data-access-api-rc-apply-postgresql
   ```

### Feature Flag Not Taking Effect

**Symptom:** Feature flag is set but behaviour doesn't change.

**Checks:**
1. Verify the Helm values file has the correct flag value
2. Check the deployed ConfigMap/environment:
   ```bash
   kubectl get configmap -n laa-data-access-api-uat -l app.kubernetes.io/instance=laa-data-access-api-rc-apply -o yaml
   ```
3. Verify application code correctly reads the flag

## Related Documentation

- [Per-Client Integration Strategy](./per-client-integration.md) — Detailed strategy and rationale
- [Deployment](../deployment.md) — General deployment documentation
- [RC Process Overview](./README_RC_PROGRAM.md) — Release Candidate process overview
- [RC Operations](./RC_OPERATIONS.md) — Day-to-day RC operations guide
- [Client Integration Guide](./CLIENT_INTEGRATION_GUIDE.md) — Guide for clients integrating with RC environments


