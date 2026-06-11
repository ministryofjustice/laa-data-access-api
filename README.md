You can also use the Swagger UI to execute endpoints, which is described below in the
[API documentation](#api-documentation) section.
curl -X GET "http://localhost:8080/applications" -H "accept: application/json" -H "Authorization: Bearer {token}"
```
You can use a tool such as Postman or curl to execute endpoints. For example, to execute the `GET /applications`
endpoint using curl:
# laa-data-access-api

## Overview

Source code for LAA Digital's Access Data Stewardship API, owned by the Access Data Stewardship team.

This API will provide a trusted API source of truth for the Civil Applications and Civil Decide projects for data
related to applications, proceedings, delegated functions, scope limitations, cost limitations and level of service.

### Add GitHub Token
Generate a Github PAT (Personal Access Token) to access the required plugin, via https://github.com/settings/tokens

Specify the Note field, e.g. “Token to allow access to LAA Gradle plugin”.

If you don't already have one, create a `gradle.properties` file in your home directory at `~/.gradle/gradle.properties`.

Add the following properties to `~/.gradle/gradle.properties` and replace the placeholder values as follows:

```
project.ext.gitPackageUser = YOUR_GITHUB_USERNAME
project.ext.gitPackageKey = PAT_CREATED_ABOVE
```

Go back to Github to authorize MOJ for SSO

### Monitoring (Prometheus & Grafana)

See `docs/monitoring.md` for details on how application metrics are collected, scraped by Prometheus, and visualised in Grafana dashboards.

### Network Policies

See `docs/network-policies.md` for details on the Kubernetes network policy that allows Prometheus to scrape metrics.

### Pre-commit hooks

See `docs/pre-commit-hooks.md` for information on setting up and using pre-commit hooks in this project.

### Project structure
Includes the following subprojects:

- `data-access-shared` - common Java classes packaged into a library to avoid unexpected dependencies - can depend
  on Spring Web, but must not depend on Spring Data (nor any database code)
- `data-access-api` - OpenAPI specification used for generating API stub interfaces and documentation.
- `data-access-service` - example REST API service with CRUD operations interfacing a JPA repository with PostgreSQL.

### To do items
- Continue to update this `README.md` file to include information such as what this project does.
- Agree provisional content of `CODEOWNERS` file and PR review policy (e.g. number of reviewers).
- Ensure the project has been added to the [Legal Aid Agency Snyk](https://app.snyk.io/org/legal-aid-agency) organisation.

## Build and run application

### Set up environment variables

Create or modify your '~/.zshrc' file to include the following environment variables:

```
export ENTRA_ISSUER_URI=https://dummy-issuer
export ENTRA_JWK_SET_URI=https://dummy-jwk-set-uri
export ENTRA_AUD=dummy-aud
```

> **Note:** These dummy values are only needed if you run `bootRun` without the `local` profile.
> When using the `local` profile (recommended), the mock OAuth2 server provides real JWT validation — see [Run application](#run-application).

This will ensure that where-ever you run the application from locally (IntelliJ, any terminal window, etc)
, these environment variables will be set. 

You can verify that they have been set by running `printenv` in your terminal or 
looking in the environment variable section in the run/debug configuration in IntelliJ 

### Developing application within Intellij
Java version 25 is required

To update to Java 25:

1. Download JDK 25 from https://www.oracle.com/uk/java/technologies/downloads/

2. Configure IntelliJ IDEA:
    - Go to **File** > **Project Structure** > **SDK**
    - Select **Add JDK from disk** and choose your Java 25 installation
    - Go to **IntelliJ IDEA** > **Settings** > **Build, Execution, Deployment** > **Build Tools** > **Gradle**
    - Set **Gradle JVM** to Java 25

### Build application
Execute

`./gradlew clean build`

Note that completing the build and unit tests currently requires:
- GitHub token with `read:packages` access - used by [`laa-ccms-spring-boot-gradle-plugin`](#gradle-plugin-used)

### Run integration tests
Execute

`./gradlew integrationTest`

### Run infrastructure smoke tests

Infrastructure smoke tests run the built Docker image against a real Postgres database and
verify the live HTTP API. See [`docs/infrastructure-smoke-tests.md`](docs/infrastructure-smoke-tests.md)
for full details.

The script is useful for testing the application locally. 
The smoke tests will also run in CI eventually.

```bash
./scripts/run-infrastructure-smoke-tests.sh
```

### Run application

**Simplest approach - automatic startup:**

```bash
./gradlew bootRun
```

This automatically starts the mock-oauth2-server (if not already running), applies the `local` profile, and launches the application with JWT validation enabled.

**Manual approach - control Docker yourself:**

```bash
docker compose up -d
./gradlew bootRun --args='--spring.profiles.active=local'
```

The application uses the mock-oauth2-server on port 9999 for JWT validation. Security is always enabled—no environment variables or feature flags needed.

#### Mock OAuth2 and test tokens

This project uses [`navikt/mock-oauth2-server`](https://github.com/navikt/mock-oauth2-server) as the test identity provider for local development, infrastructure smoke tests, and deployed test fixtures. Security stays enabled: the API validates signed JWTs, issuer, JWKS, audience, and role claims instead of bypassing auth.

| Environment | Mock/token source | Config |
|-------------|-------------------|--------|
| Local development | Docker Compose service `mock-oauth2-server` on `http://localhost:9999` | `infra/mock-oauth2/config.json` |
| Integration tests | In-process `MockOAuth2Server` started by `IntegrationTestContextProvider`; tokens minted by `TestTokenFactory` | Claims built in `TestTokenFactory` |
| Infrastructure smoke tests | Docker Compose service `mock-oauth2-server-smoketest`; tokens fetched by `SmokeTestTokenProvider` from `LAA_SMOKE_OAUTH_TOKEN_URL` | `infra/mock-oauth2/config-smoke-test.json` |
| Deployed test fixture | Kubernetes `mock-oauth2-server` service | `infra/mock-oauth2/k8s/configmap.yml`, `deployment.yml`, `service.yml` |

Key endpoints:

| Use | URL |
|-----|-----|
| Local token endpoint | `http://localhost:9999/entra/token` |
| Local JWKS endpoint | `http://localhost:9999/entra/jwks` |
| Smoke-test host token endpoint | `http://localhost:9998/entra/token` |
| In-cluster deployed token endpoint | `http://<release-name>-data-access-api-mock-oauth2.<namespace>.svc.cluster.local:9999/entra/token` |

To reach a deployed mock OAuth2 service from your laptop, port-forward the Kubernetes **service** using the `svc/` prefix:

```bash
kubectl -n laa-data-access-api-uat port-forward svc/<release-name>-data-access-api-mock-oauth2 9999:9999
```

For example:

```bash
kubectl -n laa-data-access-api-uat port-forward svc/spike-dstew1360-data-access-api-mock-oauth2 9999:9999
```

Then fetch tokens from `http://localhost:9999/entra/token` using `POST`. A browser or `GET` request to `/entra/token` returns `405 Method Not Allowed` because the token endpoint only supports form-encoded `POST` requests.

When calling a deployed API, the token `iss` claim must match that API's configured `ENTRA_ISSUER_URI`. Tokens fetched through a laptop port-forward commonly have `iss: http://localhost:9999/entra`; those will be rejected with a blank `401 Unauthorized` if the deployed API expects an in-cluster issuer such as `http://<release-name>-data-access-api-mock-oauth2:9999/entra`.

If the deployed API expects the in-cluster service hostname, keep the port-forward running and request the token with a matching `Host` header:

```bash
TOKEN=$(curl -sS -X POST http://localhost:9999/entra/token \
  -H "Host: <release-name>-data-access-api-mock-oauth2:9999" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=test" \
  -d "client_secret=test" \
  -d "scope=api://laa-data-access-api/.default" | jq -r .access_token)
```

Decode the token payload and check `iss` before calling the deployed API:

```bash
echo "$TOKEN" | awk -F. '{print $2}' | base64 -d 2>/dev/null | jq .iss
```

To get a valid local Bearer token for Swagger, Postman, or curl, prefer the helper script:

```bash
./scripts/get-token.sh local --copy
```

Or fetch one directly:

```bash
curl -s -X POST http://localhost:9999/entra/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=test" \
  -d "client_secret=test" \
  -d "scope=api://laa-data-access-api/.default" | jq -r .access_token
```

Tokens are pre-configured with the `LAA_CASEWORKER` role and a 1-hour expiry. To change token claims, update the relevant config file above. The important claims are:

- `aud`: must contain `laa-data-access-api`
- `roles`: should include the application role, e.g. `LAA_CASEWORKER`
- `LAA_APP_ROLES`: must be present for the custom audience/role validation

After changing mock OAuth2 config, restart the relevant service:

```bash
# Local development
docker compose restart mock-oauth2-server

# Smoke-test stack
docker compose -f docker-compose.smoke-test.yml restart mock-oauth2-server-smoketest
```

Keep the pinned `ghcr.io/navikt/mock-oauth2-server:2.1.10` version in sync across `docker-compose.yml`, `docker-compose.smoke-test.yml`, `data-access-service/build.gradle`, and `infra/mock-oauth2/k8s/deployment.yml`.

> **Why not auto-authenticate in Swagger?** Swagger UI supports OAuth2 flows, but switching the OpenAPI security scheme from Bearer to OAuth2 would affect all environments. In production, tokens come from the Entra OBO flow, not `client_credentials`. Keeping a single Bearer scheme keeps Swagger consistent across environments.

### Executing endpoints

#### Using curl

```bash
curl -X GET "http://localhost:8080/api/v0/applications" \
  -H "accept: application/json" \
  -H "Authorization: Bearer {token}" \
  -H "X-Service-Name: CIVIL_APPLY"
```

#### Using Postman

The `tools/postman/` directory contains a Postman collection with all endpoints and environment files for local and UAT.

**Quick start:**
1. Open Postman → **Import** → drag in `tools/postman/laa-data-access-api.postman_collection.json` and `tools/postman/local.postman_environment.json`
2. Select the environment in the top-right dropdown (e.g. "LAA Data Access API — Local")
3. Send any request — tokens are fetched automatically from mock-oauth2-server

**For UAT:** Import `tools/postman/uat.postman_environment.json` and run `kubectl port-forward -n laa-data-access-uat svc/mock-oauth2-server 9999:9999` before making requests.

#### Using Swagger UI

See [API documentation](#api-documentation) below for Swagger UI usage.


### Dependency lock files

Gradle [dependency locking](https://docs.gradle.org/current/userguide/dependency_locking.html) is enabled for this
project. Lock files (`gradle.lockfile`) exist in the root and each subproject directory, recording the exact resolved
versions of every dependency.

**Why lock files exist:** They ensure that builds are reproducible across different machines and CI environments. Without
them, Gradle may resolve dynamic or transitive dependency versions differently over time, leading to inconsistent builds.

**What happens when lock files don't match:** If a dependency version changes (e.g. a new dependency is added, upgraded,
or removed) but the lock files have not been updated, the build will **fail** with a dependency verification error. This
is intentional — it forces an explicit decision to accept the new set of resolved dependencies.

**To regenerate lock files** after changing dependencies, run:

- `./gradlew resolveAndLockAll` 

This resolves all configurations across every subproject and writes updated lock files automatically. The updated
`gradle.lockfile` files should be committed alongside the dependency change.

### Useful gradle commands

Prior to pushing code, it's useful to run the following commands to check code style:
- `./gradlew checkStyleMain` - runs checkstyle on `main` source code
- `./gradlew checkStyleTest` - runs checkstyle on `test` source code

To generate coverage reports locally:
- `./gradlew jacocoAggregatedReport` - generates aggregate coverage report

The report will be available in `data-access-service/build/reports/jacoco/jacocoAggregatedReport/html/index.html`

### Dropping database tables (may not be applicable)
You may need to drop database tables manually prior to running app so Flyway can create the latest schema. To do this:
- Start up a Postgres management tool e.g. pgadmin
- Go to the laa database
- Drop the tables

### API documentation
#### Swagger UI
- http://localhost:8080/swagger-ui/index.html

The "Authorize" button is available in the top right of the Swagger UI, which allows you to enter a Bearer token for 
authentication when executing endpoints.
See [Mock OAuth2 and test tokens](#mock-oauth2-and-test-tokens) for how to obtain a token.

#### API docs (JSON)
- http://localhost:8080/v3/api-docs

### Actuator endpoints
The following actuator endpoints have been configured:
- http://localhost:8080/actuator
- http://localhost:8080/actuator/health
- http://localhost:8080/actuator/info

### Run the data generator

Each deployment to UAT will also deploy data-access-mass-generator as a separate pod. Initially it is scaled to 0, 
however you can start the pod and connect to it via kubectl to be able to create performance testing data in that PR's database.

This is also available for the main deployment in UAT.

To start the pod, run the following command:

```bash
export KUBE_NAMESPACE=<uat namespace>
export RELEASE_NAME=<release name for the pod - you can find this in the deployment action>

./scripts/run-mass-generator-pod.sh <-- this will scale up the generator pod. When it is available it will connect to its shell

(in the shell run) java -jar mass-generator.jar <number of applications>
```

Once the command is complete, exit the shell and the pod will be scaled back to 0. 
You can check the database to see the generated data or use swagger to execute endpoints.
## Additional information

### Libraries used
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/reference/actuator/index.html) - used to provide various endpoints to help monitor the application, such as view application health and information.
- [Spring Boot Web](https://docs.spring.io/spring-boot/reference/web/index.html) - used to provide features for building the REST API implementation.
- [Spring Data JPA](https://docs.spring.io/spring-data/jpa/reference/jpa.html) - used to simplify database access and interaction, by providing an abstraction over persistence technologies, to help reduce boilerplate code.
- [Springdoc OpenAPI](https://springdoc.org/) - used to generate OpenAPI documentation. It automatically generates Swagger UI, JSON documentation based on your Spring REST APIs.
- [Lombok](https://projectlombok.org/) - used to help to reduce boilerplate Java code by automatically generating common
  methods like getters, setters, constructors etc. at compile-time using annotations.
- [MapStruct](https://mapstruct.org/) - used for object mapping, specifically for converting between different Java object types, such as Data Transfer Objects (DTOs)
  and Entity objects. It generates mapping code at compile code.
- [H2](https://www.h2database.com/html/main.html) - used to provide an example database and should not be used in production.

### Gradle plugin used
The project uses the `laa-spring-boot-gradle-plugin` Gradle plugin which provides
sensible defaults for the following plugins:

- [Checkstyle](https://docs.gradle.org/current/userguide/checkstyle_plugin.html)
- [Dependency Management](https://plugins.gradle.org/plugin/io.spring.dependency-management)
- [Jacoco](https://docs.gradle.org/current/userguide/jacoco_plugin.html)
- [Java](https://docs.gradle.org/current/userguide/java_plugin.html)
- [Maven Publish](https://docs.gradle.org/current/userguide/publishing_maven.html)
- [Spring Boot](https://plugins.gradle.org/plugin/org.springframework.boot)
- [Test Logger](https://github.com/radarsh/gradle-test-logger-plugin)
- [Versions](https://github.com/ben-manes/gradle-versions-plugin)

The plugin is provided by [laa-spring-boot-common](https://github.com/ministryofjustice/laa-spring-boot-common), where you can find
more information regarding (required) setup and usage.

