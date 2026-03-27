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
export FEATURE_ENABLE_DEV_TOKEN=true
export FEATURE_DISABLE_SECURITY=true
export FEATURE_X_AUTHZ=false
```

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

### Run application with WireMock Entra stub (recommended for local dev)

Rather than pointing at a real Entra instance, you can run a local WireMock container that
stubs the JWKS and OIDC discovery endpoints. This removes the need for `ENTRA_ISSUER_URI`,
`ENTRA_JWK_SET_URI`, and `ENTRA_AUD` environment variables when running locally.

**1. Generate the local dev RSA key pair (once per clone):**

The private key and JWKS response body are git-ignored and must be generated locally.

```bash
pip3 install cryptography PyJWT   # one-time dependency install
python3 scripts/gen_jwk.py
```

This writes two git-ignored files:
- `wiremock/local-dev-private-key.pem` — private key used to sign local JWTs
- `wiremock/__files/jwks.json` — public JWKS served by WireMock so the app can verify those JWTs

**2. Start Postgres and WireMock together:**

```bash
docker compose up -d
```

WireMock will be available at `http://localhost:8181`. Verify with:

```bash
curl http://localhost:8181/.well-known/jwks.json
curl http://localhost:8181/issuer/.well-known/openid-configuration
```

**3. Set the required environment variables:**

```bash
export ENTRA_AUD=local-dev-audience
# ENTRA_ISSUER_URI and ENTRA_JWK_SET_URI are overridden by the wiremock profile below
```

Or keep `FEATURE_DISABLE_SECURITY=true` to skip JWT validation entirely (see [Set up environment variables](#set-up-environment-variables)).

**4. Run the app with the `wiremock` Spring profile:**

```bash
./gradlew bootRun --args='--spring.profiles.active=wiremock'
```

The `wiremock` profile (`application-wiremock.yml`) overrides the JWT issuer and JWKS URIs to
point at `http://localhost:8181`.

**5. Generate a signed JWT for requests:**

```bash
# Default: sub=local-dev-user, aud=local-dev-audience, roles=LAA_CASEWORKER
python3 scripts/gen_local_token.py

# Custom options
python3 scripts/gen_local_token.py --sub alice --aud local-dev-audience --roles LAA_CASEWORKER,LAA_ADMIN --ttl 7200
```

Use the printed token as the `Authorization: Bearer <token>` header.

> **Key rotation:** re-run `python3 scripts/gen_jwk.py` then restart WireMock (`docker compose restart wiremock`)
> to pick up the new JWKS.

---

### Run application

Ensure that the environment variables specified in the 
[Set up environment variables](#set-up-environment-variables) section have been set.

To start up Postgres (and WireMock)

`docker compose up -d`


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

