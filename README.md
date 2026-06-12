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
export ENTRA_ISSUER_URI=http://host.docker.internal:9999/entra
export ENTRA_JWK_SET_URI=http://localhost:9999/entra/jwks
export ENTRA_AUD=api://laa-data-access-api
export FEATURE_ENABLE_DEV_TOKEN=true
export FEATURE_DISABLE_SECURITY=false
export SDS_API_URL=https://dummy-sds-api-url
export SDS_API_BUCKET=dummy-sds-api-bucket-name
export SDS_API_CLIENT_REGISTRATION_ID=dummy-sds-api-client-registration-id
export SDS_API_PRINCIPAL_NAME=dummy-sds-api-principal-name
```

**Note:** When using the local mock-oauth2-server (see below), the ENTRA variables are configured to point to it.
If you prefer to disable security entirely for local development, set `FEATURE_DISABLE_SECURITY=true`.

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

Ensure that the environment variables specified in the
[Set up environment variables](#set-up-environment-variables) section have been set.

To start up mock-oauth2-server, Postgres, Prometheus, and Grafana:

`docker compose up -d`

This will start:
- **Postgres** (port 5432) - Database
- **mock-oauth2-server** (port 9999) - OAuth2 test server for local authentication
- **Prometheus** (port 9090) - Metrics collection
- **Grafana** (port 3000) - Metrics visualization

Or if you want to use a different database name or credentials:

`docker compose run -p 5432:5432 -e POSTGRES_DB={database name} -e POSTGRES_USER={username} -e POSTGRES_PASSWORD={password} postgres`

Then execute

`./gradlew bootRun`

### Executing endpoints

You can use a tool such as Postman or curl to execute endpoints. For example, to execute the `GET /applications`
endpoint using curl:

```
curl -X GET "http://localhost:8080/applications" -H "accept: application/json" -H "Authorization: Bearer {token}"
```

You can also use the Swagger UI to execute endpoints, which is described below in the
[API documentation](#api-documentation) section.

If FEATURE_ENABLE_DEV_TOKEN is set to true, you can use the following token for testing purposes
```
Authorization: Bearer swagger-caseworker-token
```

#### Getting tokens from local mock-oauth2-server

If you're running with `FEATURE_DISABLE_SECURITY=false` and want to test with real JWT tokens locally, 
you can use the helper script to fetch a token from the local mock-oauth2-server:

```bash
# Get a token for local development
./scripts/get-token.sh local

# Get a token and copy it to clipboard
./scripts/get-token.sh local --copy

# Get a token and decode it to see the payload
./scripts/get-token.sh local --decode
```

The token will be valid for the local API when using the mock-oauth2-server environment variables shown above.

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
If you have set up the environment variables as specified in the
[Set up environment variables](#set-up-environment-variables) section,
you can use the "Authorize" button to enter the following token for testing purposes:

```
swagger-caseworker-token
```

### Mock OAuth2 (UAT/deployed environments)

UAT and other deployed environments use [navikt/mock-oauth2-server](https://github.com/navikt/mock-oauth2-server) to issue test JWTs that match the API's issuer validation.

After deploying to UAT, check the Helm deployment notes or run:
```bash
kubectl -n <namespace> get svc -l app.kubernetes.io/component=mock-oauth2
```

**Note:** A copyable script with the correct namespace and release name is available in the `deploy-uat` GitHub Actions workflow output.

Then fetch a test token for **UAT** (or another environment):

**Option 1: Using the helper script** (pass `uat` as the environment)
```bash
# 1. Port-forward the mock-oauth2 service (separate terminal)
kubectl -n <namespace> port-forward svc/<release>-data-access-api-mock-oauth2 9999:9999

# 2. Get a token for UAT
OAUTH_ISSUER_HOST=<release>-data-access-api-mock-oauth2:9999 ./scripts/get-token.sh uat --copy --decode
```

**Option 2: Using curl directly**
```bash
# 1. Port-forward the mock-oauth2 service (separate terminal)
kubectl -n <namespace> port-forward svc/<release>-data-access-api-mock-oauth2 9999:9999

# 2. Get a token via curl
TOKEN=$(curl -sS -X POST "http://localhost:9999/entra/token" \
  -H "Host: <release>-data-access-api-mock-oauth2:9999" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=test" \
  -d "client_secret=test" \
  -d "scope=api://laa-data-access-api/.default" | jq -r '.access_token')

echo "$TOKEN"
```

The token's `iss` claim will match the API's configured `ENTRA_ISSUER_URI`, so it will pass validation.

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
