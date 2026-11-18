# SDS WireMock for local BDD

This fixture replaces SDS only in `docker-compose.smoke-test.yml`. It provides client-credentials token, upload, download, and deletion responses needed by document BDD scenarios.

## Run locally

```bash
./gradlew :data-access-service-axon:bootJar

docker compose -f docker-compose.smoke-test.yml up --build --detach

export LAA_BDD_TOKEN="$(./scripts/get-token.sh smoke)"
./gradlew :data-access-service-axon:bddTest
```

The BDD suite targets `http://localhost:9000` by default. The mock returns the same small PDF payload for every SDS document download.

Stop the stack with:

```bash
docker compose -f docker-compose.smoke-test.yml down --volumes --remove-orphans
```

