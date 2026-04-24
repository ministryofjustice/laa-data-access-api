# Plan: `data-access-mass-generator` Console App Module

## Overview
A new Gradle submodule at the root level that boots a Spring Boot command-line application (`CommandLineRunner`). It depends on `data-access-service` main compiled output and `data-access-service` `testUtilities` compiled output (for the generators). It has its **own** `PersistedDataGenerator` living inside the module itself, and connects directly to a running PostgreSQL instance.

---

## Module Structure

```
laa-data-access-api/
└── data-access-mass-generator/
    ├── build.gradle
    └── src/
        └── main/
            ├── java/
            │   └── uk/gov/justice/laa/dstew/access/massgenerator/
            │       ├── MassGeneratorApp.java              ← Spring Boot entry point
            │       ├── MassDataGeneratorRunner.java       ← CommandLineRunner, calls PersistedDataGenerator
            │       └── generator/
            │           └── PersistedDataGenerator.java    ← own copy, lives in massgenerator package
            └── resources/
                └── application.yml                        ← DB config (no security/JWT)
```

---

## Step-by-Step Plan

### 1. Register the module in `settings.gradle`
Add `include 'data-access-mass-generator'` to the existing includes.

---

### 2. Create `data-access-mass-generator/build.gradle`

- Apply `java`, `io.freefair.lombok`, and `org.springframework.boot` plugins.
- Depend on `data-access-service` main output:
  ```groovy
  implementation project(':data-access-service')
  ```
- Expose and consume `testUtilities` compiled classes from `data-access-service` via a custom configuration:
  ```groovy
  // in data-access-service/build.gradle — add once:
  configurations {
      testUtilitiesRuntimeElements {
          canBeConsumed = true
          canBeResolved = false
          extendsFrom testUtilitiesImplementation
      }
  }
  artifacts {
      testUtilitiesRuntimeElements(sourceSets.testUtilities.output)
  }

  // in data-access-mass-generator/build.gradle:
  dependencies {
      implementation project(path: ':data-access-service', configuration: 'testUtilitiesRuntimeElements')
      // ... JPA, PostgreSQL, Flyway, datafaker runtime deps
  }
  ```
- `bootJar` enabled; `jar` disabled.
- No Sentry, checkstyle, or jacoco needed.

---

### 3. Create `PersistedDataGenerator.java` inside the mass generator module

This is a **new class**, owned by the mass generator module, in package `uk.gov.justice.laa.dstew.access.massgenerator.generator`. It mirrors the logic of the one in `integrationTest` but lives here independently — no source files are shared or moved.

The `integrationTest` version remains completely untouched.

It will:
- Extend `DataGenerator` (from `testUtilities`)
- Be a `@Component` wired with `ApplicationContext` and `EntityManager`
- Register all generator → repository mappings in `@PostConstruct`
- Provide `createAndPersist`, `createAndPersistMultiple`, and `persist` methods — identical API to the integration test version

---

### 4. Create `MassGeneratorApp.java`

```java
@SpringBootApplication(
    scanBasePackages = {
        "uk.gov.justice.laa.dstew.access",           // repos, entities, services from main
        "uk.gov.justice.laa.dstew.access.massgenerator"
    },
    exclude = {
        SecurityAutoConfiguration.class,
        OAuth2ResourceServerAutoConfiguration.class
    }
)
@ConfigurationPropertiesScan
public class MassGeneratorApp {
    public static void main(String[] args) {
        SpringApplication.run(MassGeneratorApp.class, args);
    }
}
```

---

### 5. Create `MassDataGeneratorRunner.java`

```java
@Component
public class MassDataGeneratorRunner implements CommandLineRunner {

    @Autowired
    private PersistedDataGenerator persistedDataGenerator;  // the one in massgenerator.generator

    @Override
    public void run(String... args) {
        int count = parseCount(args);  // default e.g. 100
        persistedDataGenerator.createAndPersistMultiple(ApplicationEntityGenerator.class, count);
        persistedDataGenerator.createAndPersistMultiple(CaseworkerGenerator.class, count);
        // ... other entity types
    }
}
```

---

### 6. Create `application.yml`

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/laa_data_access_api
    username: laa_user
    password: laa_password
  jpa:
    hibernate.ddl-auto: none
  flyway:
    enabled: true
    locations: classpath:db/migration
# No security config — local tool only
```

---

## Dependency Flow

```
data-access-mass-generator
  ├── implementation → data-access-service (main: Entities, Repos, Services, Flyway migrations)
  ├── implementation → data-access-service:testUtilitiesRuntimeElements
  │                        (BaseGenerator, all *Generator classes, DataGenerator, datafaker, etc.)
  └── own source    → massgenerator/generator/PersistedDataGenerator.java  ← lives here, not shared
```

## What is NOT changed
- `data-access-service/src/integrationTest/` — untouched entirely
- `data-access-service/src/testUtilities/` — no files moved in or out
- `data-access-service/build.gradle` — only additive: expose `testUtilitiesRuntimeElements` configuration

