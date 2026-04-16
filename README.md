# laa-data-access-api

## Overview

Source code for LAA Digital's Access Data Stewardship API, owned by the Access Data Stewardship team..

This API will provide a trusted API source of truth for the Civil Applications and Civil Decide projects for data
related to applications, proceedings, delegated functions, scope limitations, cost limitations and level of service.

### Add GitHub Token
Generate a Github PAT (Personal Access Token) to access the required plugin, via https://github.com/settings/tokens

Specify the Note field, e.g. “Token to allow access to LAA Gradle plugin”

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

Start Postgres and the mock OAuth2 server:

`docker compose up -d`

Then run the application with the `local` profile:

`./gradlew bootRun --args='--spring.profiles.active=local'`

This starts the app with JWT validation pointed at the local mock-oauth2-server (port 9999).
No dummy environment variables or security flags needed.

#### Getting a local dev token

To get a valid Bearer token for Swagger or Postman, run:

```bash
curl -s -X POST http://localhost:9999/entra/token \
  -d grant_type=client_credentials \
  -d client_id=test \
  -d client_secret=test | jq -r .access_token
```

Or as a one-liner you can copy straight into the Swagger "Authorize" dialog:

```bash
TOKEN=$(curl -s -X POST http://localhost:9999/entra/token -d grant_type=client_credentials -d client_id=test -d client_secret=test | jq -r .access_token) && echo $TOKEN | pbcopy && echo "Token copied to clipboard"
```

The token is pre-configured with the `LAA_CASEWORKER` role and a 1-hour expiry.

> **Why not auto-authenticate in Swagger?** Swagger UI supports OAuth2 flows, but switching
> the OpenAPI security scheme from Bearer to OAuth2 would affect all environments (not just local).
> In production, tokens come from the Entra OBO flow, not client_credentials. Keeping a single
> Bearer scheme keeps Swagger consistent across environments.

### Executing endpoints

You can use a tool such as Postman or curl to execute endpoints. For example, to execute the `GET /applications` 
endpoint using curl:

```
curl -X GET "http://localhost:8080/applications" -H "accept: application/json" -H "Authorization: Bearer {token}"
```

You can also use the Swagger UI to execute endpoints, which is described below in the 
[API documentation](#api-documentation) section.


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
See [Getting a local dev token](#getting-a-local-dev-token) for how to obtain a token.

#### API docs (JSON)
- http://localhost:8080/v3/api-docs

### Actuator endpoints
The following actuator endpoints have been configured:
- http://localhost:8080/actuator
- http://localhost:8080/actuator/health
- http://localhost:8080/actuator/info

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

