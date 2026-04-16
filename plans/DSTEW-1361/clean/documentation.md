# Clean Architecture in laa-data-access-api

## What Is Clean Architecture?

Clean Architecture (popularised by Robert C. Martin, *Clean Architecture*, 2017) organises code
into concentric layers. The central rule is the **Dependency Rule**: source code dependencies can
only point **inward**. Inner layers know nothing about outer layers.

```
┌──────────────────────────────────────┐
│  Infrastructure / Frameworks (outer) │
│  ┌──────────────────────────────┐    │
│  │  Interface Adapters          │    │
│  │  ┌────────────────────────┐  │    │
│  │  │  Use Cases             │  │    │
│  │  │  ┌──────────────────┐  │  │    │
│  │  │  │  Domain / Entities│  │  │    │
│  │  │  └──────────────────┘  │  │    │
│  │  └────────────────────────┘  │    │
│  └──────────────────────────────┘    │
└──────────────────────────────────────┘
```

Applied to this Spring Boot project the layers translate to:

| Layer | Package | Description |
|-------|---------|-------------|
| **Domain** | `domain/` | Pure Java records, enums, and value objects. No Spring, no JPA, no imports from `model/`. Types in `model/` that are truly domain concepts (e.g. `ApplicationStatus`, `CategoryOfLaw`, `MatterType`, `Individual`) are **duplicated** here as plain Java types and the `model/` copies retired over time. |
| **Use Case** | `usecase/` | Business logic. Depends only on domain and gateway interfaces. |
| **Interface Adapter** | `controller/`, `usecase/*/infrastructure/` (interfaces + mappers) | Translates between the outside world and use cases. |
| **Infrastructure** | `infrastructure/`, `repository/`, `entity/` | JPA entities, Spring Data repositories, gateway implementations. |
| **Config** | `config/` | Spring `@Configuration` wiring — ties everything together. |

---

## Package Structure (createApplication example)

```
uk.gov.justice.laa.dstew.access
│
├── controller/
│   └── ApplicationController.java          # Receives HTTP request; delegates to use case
│
├── usecase/
│   ├── shared/
│   │   ├── security/
│   │   │   ├── AccessPolicy.java            # Interface: enforce(RequiredRole) — no Spring imports
│   │   │   ├── RequiredRole.java            # Enum: API_CASEWORKER, ADMIN, …
│   │   │   └── EnforceRole.java             # Annotation — pure java.lang.annotation
│   │   ├── validation/
│   │   │   └── UseCaseValidations.java      # Business validation helpers (e.g. checkApplicationIdList)
│   │   └── ApplicationConstants.java        # e.g. APPLICATION_SCHEMA_VERSION
│   └── createapplication/
│       ├── CreateApplicationUseCase.java    # Orchestrates the business flow
│       ├── CreateApplicationCommand.java    # Input to the use case (no API model imports)
│       ├── CreateApplicationCommandMapper.java  # Maps ApplicationCreateRequest → Command
│       └── infrastructure/
│           ├── ApplicationGateway.java      # Interface: what the use case needs from storage
│           ├── ProceedingGateway.java       # Interface: proceeding persistence contract
│           └── DomainEventGateway.java      # Interface: event publishing contract
│
├── domain/
│   ├── ApplicationDomain.java               # Plain Java record — shared across use cases
│   ├── ProceedingDomain.java                # Plain Java record — shared across use cases
│   ├── ParsedAppContentDetails.java         # Plain Java record (domain version)
│   ├── ApplicationStatus.java               # Plain Java enum (domain duplicate of model version)
│   ├── CategoryOfLaw.java                   # Plain Java enum (domain duplicate of model version)
│   ├── MatterType.java                      # Plain Java enum (domain duplicate of model version)
│   ├── Individual.java                      # Plain Java record (domain version)
│   └── LinkedApplication.java               # Plain Java record (domain version)
│
├── infrastructure/
│   ├── security/
│   │   ├── SpringSecurityAccessPolicy.java  # Implements AccessPolicy via SecurityContextHolder
│   │   └── EnforceRoleAspect.java           # @Aspect that intercepts @EnforceRole
│   └── jpa/
│       └── createapplication/
│           ├── ApplicationJpaGateway.java   # Implements ApplicationGateway using JPA (no @Component)
│           ├── ProceedingJpaGateway.java    # Implements ProceedingGateway (proceedings logic inlined)
│           ├── DomainEventJpaGateway.java   # Implements DomainEventGateway
│           ├── ApplicationGatewayMapper.java  # ApplicationDomain ↔ ApplicationEntity (fully reimplemented)
│           └── ProceedingGatewayMapper.java   # ProceedingDomain ↔ ProceedingEntity (fully reimplemented)
│
└── config/
    └── CreateApplicationConfig.java         # @Configuration — wires the beans together
```

---

## How the Layers Interact

```
HTTP Request
    │
    ▼
ApplicationController
    │  calls CreateApplicationCommandMapper.toCommand(req)
    │  calls CreateApplicationUseCase.execute(command)
    ▼
CreateApplicationUseCase         ← knows only domain types and gateway interfaces
    │  calls ApplicationContentParserService.parseFromMap(map) → ParsedAppContentDetails (domain)
    │  calls ApplicationGateway.save(domain)
    │  calls ProceedingGateway.saveAll(...)
    │  calls DomainEventGateway.saveCreatedEvent(saved)
    ▼
ApplicationJpaGateway / DomainEventJpaGateway  ← knows about JPA entities and Spring Data
    │  uses ApplicationGatewayMapper to convert domain ↔ entity
    │  calls ApplicationRepository.save(entity)
    │  DomainEventJpaGateway calls DomainEventService.saveCreateApplicationDomainEvent(domain, …)
    ▼
Database
```

Key observations:
- `CreateApplicationUseCase` has **zero imports** from `entity`, `repository`, or `model/`
  packages. It is **not** annotated with `@Component` — it is wired via `@Bean` in
  `CreateApplicationConfig`.
- `ApplicationController` has **zero imports** from `repository` or `entity` packages.
- Gateway interfaces live in `usecase/createapplication/infrastructure/` — they are owned by the
  use case, not the infrastructure. This is the Dependency Inversion Principle in action.

---

## Why This Is Best Practice

### 1. Testability
Business logic lives in a class (`CreateApplicationUseCase`) whose only dependencies are
interfaces. Unit tests can mock those interfaces with plain Mockito — no `@SpringBootTest`, no
database, no in-memory Postgres. Tests run in milliseconds.

### 2. Changeability / Independence of frameworks
If the project moved from Spring Data JPA to jOOQ or an event-sourced store, only the
`infrastructure/createapplication/` classes would change. The use case and domain are untouched.

### 3. Explicit dependencies
Constructor injection (no `@Autowired` fields) makes every dependency visible in the class
signature. There are no hidden service locators. Configuration in `CreateApplicationConfig`
documents exactly which implementations wire to which interfaces.

### 4. Enforced boundaries via ArchUnit
`CleanArchitectureTest` (in `test/.../arch/`) uses ArchUnit to assert the layering at CI time.
Accidental cross-layer imports (e.g. a use case that imports `ApplicationEntity`) become
**build failures**, not code-review comments.

### 5. API / domain decoupling
`ApplicationCreateRequest` is generated from the OpenAPI spec and lives in `data-access-api`.
It changes when the API contract changes. `CreateApplicationCommand` (a plain record) is defined
by the use case. The only place these two touch is `CreateApplicationCommandMapper` — a small,
easy-to-read translation class. A change in the API spec never propagates automatically into
business logic.

---

## How to Add a New Endpoint Following This Pattern

Use `createApplication` as the reference implementation. Follow these steps for each new
endpoint:

### 1. Name the use case
Pick a verb-noun name: `AssignCaseworkerUseCase`, `MakeDecisionUseCase`, etc.
Create a sub-package under `usecase/` for it.

### 2. Define the command (or query)
- **Command** — mutates state (POST, PUT, PATCH, DELETE). Record that carries input data.
- **Query** — reads state (GET). Record that carries filter/pagination parameters.

No API model imports allowed in these records.

### 3. Define gateway interfaces
In `usecase/<usecasename>/infrastructure/`, define one interface per external resource
(database table / external service) the use case needs. Keep methods narrow — only what the use
case requires.

### 4. Write the use case class
In `usecase/<usecasename>/`, write the orchestration logic. The use case:
- Takes a command/query as input.
- Is annotated with `@EnforceRole(RequiredRole.API_CASEWORKER)` on `execute(...)` if the
  operation requires authorisation (see [Security in the use case layer](#security-in-the-use-case-layer)).
- Calls gateway interfaces.
- Returns a domain type or a simple Java value.
- Carries `@Transactional` if it writes.

### 5. Write unit tests first (TDD encouraged)
In `test/.../usecase/<usecasename>/`, write `<UseCaseName>Test.java` mocking the gateway
interfaces. Cover all scenarios before writing infrastructure code.

### 6. Create gateway mappers
In `infrastructure/jpa/<usecasename>/`, create mappers that translate domain records to/from
JPA entities. **Do not place gateway mappers in `usecase/`** — they directly reference JPA entity
classes and therefore belong in the infrastructure layer. **Do not delegate to existing `mapper/`
classes** — those map from API model types (e.g. `ApplicationCreateRequest`) which are not
available in the gateway layer; implement the translation independently. Mappers have no
`@Component` annotation; they are wired via `@Bean` in the use-case config class.

### 7. Implement gateway classes
In `infrastructure/jpa/<usecasename>/`, implement the gateway interfaces. Inject the Spring Data
repositories. Do **not** annotate gateway implementations with `@Component` — wire them via
`@Bean` in the use-case config class to avoid duplicate bean definitions. Keep `@Transactional`
annotations here when the use case itself does not already declare them.

For `DomainEventGateway` implementations, call the domain-type overload of `DomainEventService`
(i.e. `saveCreateApplicationDomainEvent(ApplicationDomain, String)`). Do not reconstruct
entities or API model types inside the gateway. Inject `ServiceNameContext` into `DomainEventService`
(it already holds a reference) — do not pass it through the gateway.

### 8. Wire in config
Add `@Bean` definitions to a new `config/<UseCaseName>Config.java` (or add to an existing config
if closely related). Always use constructor parameters — no `@Autowired`.

### 9. Update the controller
Add constructor parameters for the new use case and command mapper. Keep the controller body
trivial: map request → command → call use case → map result → return `ResponseEntity`.

The existing `ApplicationService` stays injected for all other endpoints — do not remove it.

### 10. Deprecate the old service method
Add `@Deprecated` to the corresponding method on `ApplicationService` with a Javadoc `@deprecated`
tag pointing to the new use case. Remove `@AllowApiCaseworker` (and any other security
annotations) from the deprecated method to prevent the security check firing **twice** once the
controller delegates to the use case. The deprecated method can be removed once the use-case path
is proven in production.

### 11. Check the ArchUnit rules pass
Run `./gradlew test` and confirm `CleanArchitectureTest` reports no violations. If you need an
exception (e.g. a mapper that intentionally imports an API model), annotate it with the
`@SuppressWarnings` equivalent for ArchUnit or update the rule with a named exclusion and a
comment explaining why.

---

## ArchUnit Rules Reference

Rules are in `test/.../arch/CleanArchitectureTest.java`.

| Rule | What it checks |
|------|---------------|
| `useCasesMustNotDependOnEntities` | Use-case classes (except mappers) do not import JPA entity classes |
| `useCasesMustNotDependOnRepositories` | Use-case classes do not import Spring Data repositories |
| `useCasesMustNotDependOnApiModels` | Use-case classes (except mappers) do not import `model.*` classes |
| `domainMustBeFrameworkFree` | Domain records/enums have no Spring, JPA, entity, repository, or `model/` imports |
| `controllersMustNotDependOnRepositories` | Controllers do not call repositories directly |
| `useCasesMustNotDependOnInfrastructureImplementations` | Use-case classes depend on gateway interfaces, not their JPA implementations |
| `useCasesMustNotDependOnSpringSecurityAnnotations` | Use-case classes do not import Spring Security packages |

### Excluded legacy packages

`@AnalyzeClasses` scans the entire `uk.gov.justice.laa.dstew.access` package. The following
pre-existing packages are **not yet subject to clean-architecture rules** and will not cause
ArchUnit failures because the rules are scoped to `..usecase..`, `..domain..`, and `..controller..`:

| Package | Reason not yet in scope |
|---------|------------------------|
| `service/` | Legacy services call repositories directly |
| `mapper/` | Existing mappers depend on both entity and model types |
| `validation/` | `ApplicationValidations` depends on model types |
| `specification/` | Spring Data Specification helpers |
| `transformation/` | AOP transformation pipeline |
| `convertors/` | Enum conversion utilities |
| `model/` | Internal model classes not yet moved to `domain/` |

As each area migrates, tighten or extend the rules to cover it.

---

## Security in the use case layer

Keeping the use case free of Spring annotations (`@AllowApiCaseworker`, `@PreAuthorize`, etc.)
means security cannot be enforced by AOP alone. The solution is a **Security Port** — a plain
Java interface that the use case calls as a precondition. Spring provides one implementation;
any other entry point (CLI tool, batch job, test) provides its own.

### Package structure

```
usecase/
└── shared/
    └── security/
        ├── AccessPolicy.java       ← interface (no Spring imports)
        ├── RequiredRole.java       ← enum: API_CASEWORKER, ADMIN, …
        └── EnforceRole.java        ← annotation (no Spring imports — pure java.lang.annotation)

infrastructure/
└── security/
    ├── SpringSecurityAccessPolicy.java   ← implements AccessPolicy via SecurityContextHolder
    └── EnforceRoleAspect.java            ← @Aspect that intercepts @EnforceRole and calls AccessPolicy
```

### The annotation (use case layer — zero Spring imports)

```java
// usecase/shared/security/EnforceRole.java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface EnforceRole {
    RequiredRole value();
}
```

Only `java.lang.annotation.*` — no Spring, no security framework.

### The interface and enum (use case layer — zero Spring imports)

```java
// usecase/shared/security/AccessPolicy.java
public interface AccessPolicy {
    /** Throws AccessDeniedException if the current caller does not hold the required role. */
    void enforce(RequiredRole role);
}

// usecase/shared/security/RequiredRole.java
public enum RequiredRole {
    API_CASEWORKER,
    ADMIN
}
```

### The Spring implementation and aspect (infrastructure layer)

```java
// infrastructure/security/SpringSecurityAccessPolicy.java
@Component
public class SpringSecurityAccessPolicy implements AccessPolicy {
    @Override
    public void enforce(RequiredRole role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!hasRole(auth, role)) {
            throw new AccessDeniedException("Required role: " + role);
        }
    }
}

// infrastructure/security/EnforceRoleAspect.java
@Aspect
@Component
public class EnforceRoleAspect {
    private final AccessPolicy accessPolicy;

    public EnforceRoleAspect(AccessPolicy accessPolicy) {
        this.accessPolicy = accessPolicy;
    }

    @Before("@annotation(enforceRole)")
    public void enforce(EnforceRole enforceRole) {
        accessPolicy.enforce(enforceRole.value());
    }
}
```

The aspect lives entirely in infrastructure. It knows about Spring AOP and `AccessPolicy`, but
the use case and the annotation itself know about neither.

### Usage in a use case

```java
public class CreateApplicationUseCase {
    private final ApplicationGateway applicationGateway;
    // … other gateways — no AccessPolicy needed as a constructor arg

    @EnforceRole(RequiredRole.API_CASEWORKER)   // ← declarative, no Spring imports
    public UUID execute(CreateApplicationCommand command) {
        // … business logic, no security boilerplate
    }
}
```

The annotation is the only thing the use case imports from `usecase/shared/security/`. There is
no `AccessPolicy` constructor dependency to inject.

### Usage in a console app (or any non-Spring entry point)

```java
public class ConsoleAccessPolicy implements AccessPolicy {
    private final Set<String> grantedRoles;

    @Override
    public void enforce(RequiredRole role) {
        if (!grantedRoles.contains(role.name())) {
            throw new AccessDeniedException("Required role: " + role);
        }
    }
}
```

Wire a `ConsoleEnforceRoleAspect` (or a simple reflective proxy) that reads `@EnforceRole` from
the method and calls the `ConsoleAccessPolicy`. The use case code is unchanged.

### Testing security without Spring

Because `@EnforceRole` is processed by an AOP proxy, calling `execute(...)` directly in a plain
JUnit test means the aspect never fires — security is silently bypassed. Keep tests Spring-free
by splitting the concern into two:

**1. Assert the annotation is present (per use case — no Spring needed)**

```java
@Test
void execute_isAnnotatedWithCorrectRole() throws NoSuchMethodException {
    var method = CreateApplicationUseCase.class
        .getMethod("execute", CreateApplicationCommand.class);
    var annotation = method.getAnnotation(EnforceRole.class);

    assertThat(annotation).isNotNull();
    assertThat(annotation.value()).isEqualTo(RequiredRole.API_CASEWORKER);
}
```

This confirms the security intent without wiring Spring. It lives in the use-case unit test
alongside the business logic tests.

**2. Verify the aspect enforces the annotation (once for the whole project)**

Write a single `EnforceRoleAspectTest` as a focused Spring slice test. It only needs the aspect
and a mock `AccessPolicy` — no full application context:

```java
@ExtendWith(SpringExtension.class)
@Import({EnforceRoleAspect.class, EnforceRoleAspectTest.StubUseCase.class})
class EnforceRoleAspectTest {

    @MockBean AccessPolicy accessPolicy;
    @Autowired StubUseCase stubUseCase;

    @Test
    void callsAccessPolicyWithCorrectRole() {
        stubUseCase.doSomething();
        verify(accessPolicy).enforce(RequiredRole.API_CASEWORKER);
    }

    @Test
    void throwsWhenAccessPolicyDenies() {
        doThrow(new AccessDeniedException("denied"))
            .when(accessPolicy).enforce(any());
        assertThatThrownBy(() -> stubUseCase.doSomething())
            .isInstanceOf(AccessDeniedException.class);
    }

    @Component
    static class StubUseCase {
        @EnforceRole(RequiredRole.API_CASEWORKER)
        public void doSomething() {}
    }
}
```

This test is written **once** and covers every use case that uses `@EnforceRole`. Individual
use-case unit tests only need the reflection assertion above.

### ArchUnit rule to add

```java
@ArchTest
static final ArchRule useCasesMustNotDependOnSpringSecurityAnnotations =
    noClasses()
        .that().resideInAPackage("..usecase..")
        .should().dependOnClassesThat()
            .resideInAnyPackage(
                "org.springframework.security..",
                "org.springframework.security.access.prepost..");
```

---

## Known Coupling Considerations

### `@AllowApiCaseworker` AOP proxy scope
`@AllowApiCaseworker` is a Spring AOP annotation and must not be placed on use-case classes.
Security for use cases is enforced declaratively via `@EnforceRole(RequiredRole.API_CASEWORKER)`
on `execute(...)`. The annotation itself (`EnforceRole`) lives in `usecase/shared/security/` with
no Spring imports; the `EnforceRoleAspect` in `infrastructure/security/` intercepts it and
delegates to `SpringSecurityAccessPolicy`, which throws
`org.springframework.security.access.AccessDeniedException` when access is denied. Remove
`@AllowApiCaseworker` from the deprecated `ApplicationService` method to prevent double-checking
during the migration period. See [Security in the use case layer](#security-in-the-use-case-layer)
for full details.

### `DomainEventService` coupling
`DomainEventService.saveCreateApplicationDomainEvent` previously accepted `ApplicationEntity` and
`ApplicationCreateRequest`. A domain-type overload `saveCreateApplicationDomainEvent(ApplicationDomain, String)`
has been added in this ticket; the old overload is deprecated. The new overload uses
`ApplicationDomain.createdAt()`, `laaReference()`, `status()`, and `id()` from the saved record.
Note that `CreateApplicationDomainEventDetails.request` (the raw request JSON snapshot) is
intentionally omitted from the new overload — if downstream consumers require it, add
`applicationContent` from the domain record in a follow-up.

### `ApplicationContentParserService` and domain types
`ApplicationContentParserService` has been extended with `parseFromMap(Map<String, Object>)` which
reads raw application content and returns `domain.ParsedAppContentDetails`. Internally it still
converts the map to `ApplicationContent` (model) via Jackson — this conversion detail stays inside
the service. The use case never imports `ApplicationContent`.

### Mapper overlap with `ApplicationMapper`
`ApplicationGatewayMapper` is a full independent reimplementation. It does not delegate to
`ApplicationMapper` because `ApplicationMapper.toApplicationEntity` maps from
`ApplicationCreateRequest` (an API model), not `ApplicationDomain`. `ApplicationMapper.toApplicationEntity`
should be deprecated once `ApplicationService.createApplication` is removed.

### `ProceedingsService` inlining
`ProceedingJpaGateway` inlines the proceedings save logic rather than delegating to
`ProceedingsService.saveProceedings`, which accepts `ApplicationContent` (a model type unavailable
in the gateway). The inline logic is equivalent. `ProceedingsService` continues to serve any
remaining legacy callers unchanged until a follow-up ticket migrates it.

### `@Configuration`/`@Bean` wiring only — no `@Component` on implementations
Gateway implementations (`ApplicationJpaGateway`, `ProceedingJpaGateway`, `DomainEventJpaGateway`)
and use cases (`CreateApplicationUseCase`) are **not** annotated with `@Component`. They are
instantiated exclusively via `@Bean` methods in `CreateApplicationConfig`. This avoids duplicate
bean definitions and makes dependency wiring explicit and inspectable in one place.

---

## Glossary

| Term | Meaning in this project |
|------|------------------------|
| **Domain** | A plain Java record or enum in `domain/` representing a business concept. No framework dependencies, no imports from `model/`. Types that exist in `model/` and are truly domain concepts are duplicated here as plain Java types; the `model/` copies are retired as callers migrate. |
| **Use Case** | A class wired via `@Bean` (no `@Component`) that contains all business logic for one operation. Depends only on domain types and gateway interfaces. |
| **Command** | A plain Java record passed into a use case. Carries data needed to perform one operation. Uses domain types, not API model types. |
| **Gateway interface** | An interface in `usecase/*/infrastructure/` that declares persistence or event-publishing operations the use case needs. Owned by the use case. |
| **Gateway implementation** | A class in `infrastructure/jpa/*/` that implements a gateway interface using JPA. Wired via `@Bean` — no `@Component`. Owned by infrastructure. |
| **Gateway mapper** | A class in `infrastructure/jpa/*/` that translates between domain records and JPA entities. Fully reimplemented — does not delegate to legacy `mapper/` classes because those map from API model types. Wired via `@Bean`. |
| **Command mapper** | A class in `usecase/*/` that translates between generated API models and command records. The only place an API model is permitted on the use-case side of the boundary. |
| **UseCaseValidations** | A utility class in `usecase/shared/validation/` containing static business validation helpers (e.g. null-ID checks). Business validation logic that is needed by use cases is duplicated here rather than depending on `validation.ApplicationValidations` (which carries model-layer dependencies). |
| **AccessPolicy** | A plain Java interface in `usecase/shared/security/` that the use case calls to enforce authorisation. Spring provides `SpringSecurityAccessPolicy`; other entry points provide their own implementation. |
| **RequiredRole** | A plain Java enum in `usecase/shared/security/` that names the roles a use case may require. No Spring or security framework imports. |
| **EnforceRole** | A plain Java annotation in `usecase/shared/security/` placed on use-case `execute(...)` methods. Contains only `java.lang.annotation.*` imports. The `EnforceRoleAspect` in `infrastructure/security/` intercepts it and delegates to `AccessPolicy`, throwing `AccessDeniedException` on denial. |

