# mock-oauth2-server infrastructure

This directory contains config and deployment manifests for
[navikt/mock-oauth2-server](https://github.com/navikt/mock-oauth2-server), used as a
lightweight stand-in for Microsoft Entra during local development, integration testing,
infrastructure smoke tests, and performance tests.

## Files

```
infra/mock-oauth2/
├── config.json              # Shared config for local, docker-compose, and Testcontainers
└── README.md                # This file
```

**Kubernetes deployment:** See `.helm/data-access-api/templates/mock-oauth2-*.yaml` (auto-deployed via Helm)

## Updating the config

The config exists in **two places** (keep them in sync):

1. **`infra/mock-oauth2/config.json`** - Used by docker-compose and Testcontainers
2. **`.helm/data-access-api/templates/mock-oauth2-configmap.yaml`** - Embedded in Helm chart for k8s deployments

When changing the config, update **both** files with the same content.

**Key settings:**

- **`sub`** — the subject claim returned in tokens (currently generic `test-user`)
- **`aud`** — the audience claim (must match `spring.security.oauth2.resourceserver.jwt.audience`)
- **`roles` / `LAA_APP_ROLES`** — the role claims checked by `@AllowApiCaseworker`

After editing:

```bash
# Local
docker compose restart mock-oauth2-server

# Smoke test
docker compose -f docker-compose.smoke-test.yml restart mock-oauth2-server-smoketest

# Integration tests
# (automatic - Testcontainers reads file on startup)

# Kubernetes (UAT/staging)
# (automatic - next Helm deployment will apply the updated ConfigMap)
# Or force update: kubectl rollout restart deployment/mock-oauth2-server -n <namespace>
```

## Deployed pod

The mock server is **automatically deployed** to UAT/staging environments via the Helm chart.

When you push code to a PR, the CI/CD pipeline deploys:
- ✅ Your application
- ✅ mock-oauth2-server (ConfigMap + Deployment + Service)

**The config is embedded in the Helm chart** (`.helm/data-access-api/templates/mock-oauth2-configmap.yaml`), so it deploys automatically with every Helm upgrade.

### Accessing locally

After deployment, the endpoints are available within the cluster at:

| Endpoint | URL |
|----------|-----|
| Token | `http://mock-oauth2-server.<namespace>.svc.cluster.local:9999/entra/token` |
| JWKS | `http://mock-oauth2-server.<namespace>.svc.cluster.local:9999/entra/jwks` |
| OIDC discovery | `http://mock-oauth2-server.<namespace>.svc.cluster.local:9999/entra/.well-known/openid-configuration` |

To reach the mock server locally (e.g. for Postman):

```bash
kubectl port-forward -n <namespace> svc/mock-oauth2-server 9999:9999
```

## Version pinning

The image version is defined in `Constants.MOCK_OAUTH2_SERVER_IMAGE` and referenced in multiple places:

| Location | Reference |
|----------|-----------|
| `data-access-service/.../Constants.java` | `MOCK_OAUTH2_SERVER_IMAGE = "ghcr.io/navikt/mock-oauth2-server:2.1.10"` **(source of truth)** |
| `data-access-service/build.gradle` | `integrationTestImplementation 'no.nav.security:mock-oauth2-server:2.1.10'` |
| `data-access-service/.../MockOAuth2Container.java` | Uses `Constants.MOCK_OAUTH2_SERVER_IMAGE` |
| `docker-compose.yml` | `image: ghcr.io/navikt/mock-oauth2-server:2.1.10` |
| `docker-compose.smoke-test.yml` | `image: ghcr.io/navikt/mock-oauth2-server:2.1.10` |
| `.helm/.../templates/mock-oauth2-deployment.yaml` | `image: ghcr.io/navikt/mock-oauth2-server:2.1.10` |

**To update the version:** Change it in `Constants.java` first, then update all other references to match.
