# laa-data-access-api

## Overview

Source code for LAA Digital's Access Data Stewardship API, owned by the Access Data Stewardship team..

This API will provide a trusted API source of truth for the Civil Applications and Civil Decide projects for data
related to applications, proceedings, delegated functions, scope limitations, cost limitations and level of service.

### Add GitHub Token
Generate a Github PAT (Personal Access Token) to access the required plugin, via https://github.com/settings/tokens

Specify the Note field, e.g. “Token to allow access to LAA Gradle plugin”

If you haven’t got a gradle.properties file create one under `~/.gradle/gradle.properties`

Add the following properties to `~/.gradle/gradle.properties` and replace the placeholder values as follows:

```
project.ext.gitPackageUser = YOUR_GITHUB_USERNAME
project.ext.gitPackageKey = PAT_CREATED_ABOVE
```

Go back to Github to authorize MOJ for SSO

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
- Check why `build-test-pr.yml` and `pr-merge-main.yml` were not brought across.

## Build and run application
### Developing application within Intellij
Java version 21 is recommended

Set the security environment variable
`FEATURE_DISABLESECURITY=true`

### Build application
Execute

`./gradlew clean build`

Note that completing the build and unit tests currently requires:
- GitHub token with `read:packages` access - used by [`laa-ccms-spring-boot-gradle-plugin`](#gradle-plugin-used)

### Run integration tests
Execute

`./gradlew integrationTest`

### Run application
If the environment setting does not exist then set it

`export FEATURE_DISABLESECURITY=true`

To start up Localstack and Postgres

`docker compose up -d`

Then execute

`./gradlew bootRun`

### Dropping database tables (may not be applicable)
You may need to drop database tables manually prior to running app so Flyway can create the latest schema. To do this:
- Start up a Postgres management tool e.g. pgadmin
- Go to the laa_db database
- Drop the tables

### API documentation
#### Swagger UI
- http://localhost:8080/swagger-ui/index.html
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
