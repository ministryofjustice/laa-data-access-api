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
export AUTH_CLIENT_ID=test
export AUTH_CLIENT_SECRET=test
export AUTH_SCOPE=api://laa-data-access-api/.default
export AUTH_TENANT_ID=entra
```

**Note:** When using the local mock-oauth2-server (see below), the ENTRA variables are configured to point to it.
If you prefer to disable security entirely for local development, set `FEATURE_DISABLE_SECURITY=true`.

This will ensure that where-ever you run the application from locally (IntelliJ, any terminal window, etc)
, these environment variables will be set.

You can verify that they have been set by running `printenv` in your terminal or
## Build and run application

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

```bash
docker compose up -d
```

This will start:
- **Postgres** (port 5432) - Database
- **mock-oauth2-server** (port 9999) - OAuth2 test server for local authentication
- **Prometheus** (port 9090) - Metrics collection
- **Grafana** (port 3000) - Metrics visualization

Or if you want to use a different database name or credentials:

`docker compose run -p 5432:5432 -e POSTGRES_DB={database name} -e POSTGRES_USER={username} -e POSTGRES_PASSWORD={password} postgres`

Then run the application with the `local` profile:

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

The app will start on http://localhost:8080 with all OAuth2 configuration pre-configured.

### Executing endpoints

You can use curl or Postman to execute endpoints. First, get an authentication token:

```bash
# Get a token and save it as an environment variable
export TOKEN=$(./scripts/get-token.sh local)

# Call the API
curl -X GET "http://localhost:8080/api/v0/caseworkers" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Service-Name: CIVIL_APPLY"
```

You can also use the Swagger UI to execute endpoints, which is described below in the
[API documentation](#api-documentation) section.

#### Getting tokens from local mock-oauth2-server

You can use the helper script to fetch a token from the local mock-oauth2-server:

```bash
# Get a token and copy it to clipboard
./scripts/get-token.sh local --copy

# Get a token and decode it to see the payload
./scripts/get-token.sh local --decode
```

**How to use the token:**
1. Run the script with `--copy` to copy the token to your clipboard
2. Open Swagger UI at http://localhost:8080/swagger-ui/index.html
3. Click the "Authorize" button in the top right
4. Paste the token into the "Value" field
5. Click "Authorize" then "Close"

Now you can execute endpoints directly from Swagger UI.

---

## Authentication Across Environments

This section explains how to get JWT tokens for testing the API in different environments using mock-oauth2-server.

### Local Development

**Setup:**
```bash
# 1. Start the infrastructure
docker compose up -d

# 2. Run the app with local profile (includes all OAuth2 config)
./gradlew bootRun --args='--spring.profiles.active=local'
```

**Get a token:**
```bash
# Option 1: Copy to clipboard for Swagger UI
./scripts/get-token.sh local --copy

# Option 2: Save to environment variable for curl
export TOKEN=$(./scripts/get-token.sh local)
```

**Use the token:**
```bash
# With curl
curl -X GET "http://localhost:8080/api/v0/caseworkers" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Service-Name: CIVIL_APPLY"

# With Swagger UI - paste the token in the Authorize dialog
# http://localhost:8080/swagger-ui/index.html
```

**What's happening:**
- The `local` profile configures the app to accept JWTs from `http://localhost:9999/entra`
- The script requests a token from the same mock server
- No environment variables needed - everything is preconfigured

---

### UAT/Deployed Environments

**Prerequisites:**
- Access to the Kubernetes cluster
- `kubectl` configured with correct context
- Namespace and release name from your GitHub Actions deployment output
  - The deployment logs on GitHub will show the exact service name and `OAUTH_ISSUER_HOST` to use
  - Look for the "Deploy to UAT" 

**Step 1: Find the mock-oauth2 service**
```bash
# Set your namespace
export KUBE_NAMESPACE=laa-data-access-api-uat

# Find the service name
kubectl -n $KUBE_NAMESPACE get svc -l app.kubernetes.io/component=mock-oauth2

# Example output:
# NAME                                        TYPE        CLUSTER-IP      PORT(S)
# pr-123-data-access-api-mock-oauth2         ClusterIP   10.200.1.45     9999/TCP
```

**Step 2: Port-forward the mock-oauth2 service**

In a separate terminal window, keep this running:
```bash
# Replace <service-name> with the actual service name from step 1
kubectl -n $KUBE_NAMESPACE port-forward svc/<service-name> 9999:9999

# Example:
# kubectl -n laa-data-access-api-uat port-forward svc/pr-123-data-access-api-mock-oauth2 9999:9999
```

**Step 3: Get a token**

**Note:** The `OAUTH_ISSUER_HOST` is the service name from your port-forward command (the part after `svc/`), followed by `:9999`.

For example, if your port-forward command is:
```bash
kubectl -n laa-data-access-api-uat port-forward svc/spike-dstew1360-data-access-api-mock-oauth2 9999:9999
```

Then your `OAUTH_ISSUER_HOST` is: `spike-dstew1360-data-access-api-mock-oauth2:9999`

Choose one of these methods:

**Option A: Using the helper script**
```bash
# Set the issuer host (extract from your port-forward command above)
export OAUTH_ISSUER_HOST=spike-dstew1360-data-access-api-mock-oauth2:9999

# Get the token
./scripts/get-token.sh uat --copy --decode
```

**Option B: Using curl directly**
```bash
# Get token and save to variable
TOKEN=$(curl -sS -X POST "http://localhost:9999/entra/token" \
  -H "Host: spike-dstew1360-data-access-api-mock-oauth2:9999" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=test" \
  -d "client_secret=test" \
  -d "scope=api://laa-data-access-api/.default" | jq -r '.access_token')

# Copy to clipboard (macOS)
echo $TOKEN | pbcopy
```

**Step 4: Use the token**

The token is now in your clipboard. Use it in:

1. **Swagger UI:**
   - Navigate to UAT Swagger (replace with your actual hostname from deployment output)
   - Example: `https://spike-dstew1360-laa-data-access-api-uat.cloud-platform.service.justice.gov.uk/swagger-ui/index.html`
   - Click "Authorize"
   - Paste the token
   - Test endpoints

2. **curl:**
   ```bash
   # Using the token from Option A
   curl -X GET "https://spike-dstew1360-laa-data-access-api-uat.cloud-platform.service.justice.gov.uk/api/v0/caseworkers" \
     -H "Authorization: Bearer $TOKEN" \
     -H "X-Service-Name: CIVIL_APPLY"
   
   # Or get token inline with Option B
   export TOKEN=$(curl -sS -X POST "http://localhost:9999/entra/token" \
     -H "Host: spike-dstew1360-data-access-api-mock-oauth2:9999" \
     -H "Content-Type: application/x-www-form-urlencoded" \
     -d "grant_type=client_credentials" \
     -d "client_id=test" \
     -d "client_secret=test" \
     -d "scope=api://laa-data-access-api/.default" | jq -r '.access_token')
   ```

**Important Notes:**
- The port-forward must stay running while you use the API
- Tokens expire, currently set to 24 hours - get a fresh token if you see 401 errors
- The `OAUTH_ISSUER_HOST` must match the in-cluster service name (not `localhost`)
- Find the exact commands in your GitHub Actions deployment logs

---

### Smoke Test Environment

For smoke tests that run via `docker-compose.smoke-test.yml`:

```bash
# Start smoke test infrastructure
docker compose -f docker-compose.smoke-test.yml up -d

# Get a token (uses port 9998)
./scripts/get-token.sh smoke --copy

# Use with smoke test API (runs on port 9000)
export TOKEN=$(./scripts/get-token.sh smoke)
curl -X GET "http://localhost:9000/api/v0/caseworkers" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Service-Name: CIVIL_APPLY"
```

**Smoke test ports:**
- App: http://localhost:9000
- Mock OAuth2: http://localhost:9998
- Postgres: localhost:6432

---

### Troubleshooting Authentication

**Problem: "401 Unauthorized"**

Causes:
- Token expired
- Token from wrong environment
- Wrong issuer configuration

Solution:
```bash
# Get a fresh token
export TOKEN=$(./scripts/get-token.sh local)  # or 'uat'

# Decode to check claims
./scripts/get-token.sh local --decode

# Verify the 'iss' (issuer) matches your app's expected issuer
```

**Problem: Token works locally but not in UAT**

Cause: Issuer mismatch - local tokens won't work in UAT

Solution:
```bash
# Must get a UAT-specific token with correct OAUTH_ISSUER_HOST
OAUTH_ISSUER_HOST=<service-name>:9999 ./scripts/get-token.sh uat --copy
```

---

### Quick Reference

| Environment | Command | Mock Server Port |
|------------|---------|------------------|
| **Local** | `./scripts/get-token.sh local --copy` | 9999 |
| **Smoke Test** | `./scripts/get-token.sh smoke --copy` | 9998 |
| **UAT** | `OAUTH_ISSUER_HOST=<svc>:9999 ./scripts/get-token.sh uat --copy` | 9999 (via port-forward) |

**Required Headers for API Calls:**
- `Authorization: Bearer <token>`
- `X-Service-Name: CIVIL_APPLY` (or other valid service)

---

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

For authentication tokens, see the [Authentication Across Environments](#authentication-across-environments) section above.

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
