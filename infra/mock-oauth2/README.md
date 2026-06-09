# mock-oauth2-server infrastructure

This directory contains config and deployment manifests for
[navikt/mock-oauth2-server](https://github.com/navikt/mock-oauth2-server), used as a
lightweight stand-in for Microsoft Entra during local development, integration testing,
infrastructure smoke tests, and performance tests.

## Files

```
infra/mock-oauth2/
├── config.json              # Token config for local dev (docker-compose.yml)
├── config-smoke-test.json   # Token config for smoke tests (docker-compose.smoke-test.yml)
└── k8s/
    ├── configmap.yml        # ConfigMap holding the token callback config
    ├── deployment.yml       # Deployment (single replica, lightweight)
    └── service.yml          # ClusterIP Service on port 9999
```

## Updating the config

The config files use the [mock-oauth2-server JSON config format](https://github.com/navikt/mock-oauth2-server#configuration).
Key things you might need to change:

- **`sub`** — the subject claim returned in tokens (e.g. a user ID)
- **`aud`** — the audience claim (must match `spring.security.oauth2.resourceserver.jwt.audience`)
- **`roles` / `LAA_APP_ROLES`** — the role claims checked by `@AllowApiCaseworker`

After editing `config.json`, restart the relevant container/pod:

```bash
# Local
docker compose restart mock-oauth2-server

# Smoke test
docker compose -f docker-compose.smoke-test.yml restart mock-oauth2-server-smoketest

# Deployed pod (re-deploy via the manual workflow)
# See: .github/workflows/deploy-mock-oauth2.yml
```

## Deployed pod

The mock server is deployed to cluster environments via the manual GitHub Actions workflow
`.github/workflows/deploy-mock-oauth2.yml`. It should only need deploying once, or when
`k8s/configmap.yml` changes.

**Do not** wire this into a per-PR pipeline — it is a shared, stable fixture.

After deployment, the endpoints are available within the cluster at:

| Endpoint | URL |
|----------|-----|
| Token | `http://mock-oauth2-server.<namespace>.svc.cluster.local:9999/entra/token` |
| JWKS | `http://mock-oauth2-server.<namespace>.svc.cluster.local:9999/entra/jwks` |
| OIDC discovery | `http://mock-oauth2-server.<namespace>.svc.cluster.local:9999/entra/.well-known/openid-configuration` |

To reach the token endpoint from your laptop (e.g. for Postman or `get-token.sh`):

```bash
kubectl port-forward -n <namespace> svc/mock-oauth2-server 9999:9999
```

## Version pinning

The image tag is pinned to `2.1.10`. It appears in three places — keep them in sync:

| Location | Line |
|----------|------|
| `docker-compose.yml` | `image: ghcr.io/navikt/mock-oauth2-server:2.1.10` |
| `docker-compose.smoke-test.yml` | `image: ghcr.io/navikt/mock-oauth2-server:2.1.10` |
| `data-access-service/build.gradle` | `integrationTestImplementation 'no.nav.security:mock-oauth2-server:2.1.10'` |
| `infra/mock-oauth2/k8s/deployment.yml` | `image: ghcr.io/navikt/mock-oauth2-server:2.1.10` |
| `data-access-service/src/integrationTest/.../MockOAuth2Container.java` | `IMAGE` constant |
