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
│   │   │   ├── AuthorizeOperation.java      # Annotation: declares named operation string
│   │   │   ├── PolicyContext.java           # Record: operation + caller + args (plain Java)
│   │   │   └── PolicyDecisionPort.java      # Interface: authorize(PolicyContext) — no Spring imports
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
│   │   ├── LocalPolicyDecisionPort.java     # Phase 1: evaluates roles/rules locally
│   │   ├── ExternalPolicyDecisionPort.java  # Phase 2: calls OPA / external PDP
│   │   └── PolicyEnforcementAspect.java     # @Aspect: builds PolicyContext, calls port
│   └── jpa/
│       └── createapplication/
│           ├── ApplicationJpaGateway.java   # Implements ApplicationGateway using JPA (no @Component)
│           ├── ProceedingJpaGateway.java    # Implements ProceedingGateway (proceedings logic inlined)
│           ├── DomainEventJpaGateway.java   # Implements DomainEventGateway
│           ├── ApplicationGatewayMapper.java  # ApplicationDomain ↔ ApplicationEntity (fully reimplemented)
│           └── ProceedingGatewayMapper.java   # ProceedingDomain ↔ ProceedingEntity (fully reimplemented)
│
└── config/
    ├── CreateApplicationConfig.java         # @Configuration — wires the beans together
    └── MethodSecurityConfig.java            # @Configuration — registers PolicyEnforcementInterceptor
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
- Is annotated with `@AuthorizeOperation("resource:action")` on `execute(...)` — this is the
  only security declaration the use case makes. The method body contains **no authorization code**.
  Convention: `"application:create"`, `"application:makeDecision"`, `"caseworker:assign"`, etc.
  This string becomes the stable operation identity passed to the `PolicyDecisionPort` and,
  eventually, to any external PDP.
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
means security cannot be enforced by AOP alone. The solution is a **Policy Decision Port** — a
plain Java interface that an aspect calls on behalf of the use case. The use case method body
contains **zero authorization code**. This design is forward-compatible with an external Policy
Decision Point (OPA, Keycloak Authorization Server, or any bespoke PDP API): when that migration
happens, only the `PolicyDecisionPort` implementation changes — use case code is untouched.

### Why not `@PreAuthorize` or calling an `AuthorizationPort` in the method body?

- `@PreAuthorize` SpEL expressions are untestable without a Spring context and break silently on
  parameter rename.
- Calling a port inside `execute(...)` means authorization logic is scattered across use case
  bodies and must be changed when moving to an external PDP.
- An aspect that reads the annotation, the caller identity, and the method arguments can build a
  complete policy context without any cooperation from the use case — making the use case
  completely oblivious to authorization.

---

### Package structure

```
usecase/
└── shared/
    └── security/
        ├── AuthorizeOperation.java    ← annotation: declares a named operation string
        ├── PolicyContext.java         ← record: operation + caller + method args (plain Java)
        └── PolicyDecisionPort.java    ← interface: authorize(PolicyContext) — no Spring imports

infrastructure/
└── security/
    ├── LocalPolicyDecisionPort.java   ← Phase 1: evaluates roles + rules locally
    ├── ExternalPolicyDecisionPort.java ← Phase 2: calls OPA / external PDP HTTP API
    └── PolicyEnforcementAspect.java   ← @Aspect: builds PolicyContext and calls the port
```

---

### `@AuthorizeOperation` — declares a stable operation identity (use case layer)

```java
// usecase/shared/security/AuthorizeOperation.java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthorizeOperation {
    /**
     * A stable, human-readable name for the operation.
     * Used as the policy input to the PolicyDecisionPort.
     * Convention: "<resource>:<action>", e.g. "application:create", "application:makeDecision".
     * This string is the contract with the external PDP — do not change it without updating
     * the policy definitions in the PDP.
     */
    String value();
}
```

Usage — the use case body contains **no authorization code**:

```java
@AuthorizeOperation("application:create")
public UUID execute(CreateApplicationCommand command) {
    // pure business logic
}

@AuthorizeOperation("application:makeDecision")
public void execute(MakeDecisionCommand command) {
    // pure business logic — no authorizationPort.requireApplicationAccess(...) call here
}
```

---

### `PolicyContext` — the input to any PDP (use case layer — plain Java)

```java
// usecase/shared/security/PolicyContext.java
public record PolicyContext(
    String operation,              // e.g. "application:makeDecision"
    String callerId,               // extracted from the auth token by the aspect
    List<String> callerRoles,      // extracted from the auth token by the aspect
    Map<String, Object> input      // method arguments, keyed by parameter name
) {}
```

### `PolicyDecisionPort` interface (use case layer — zero Spring imports)

```java
// usecase/shared/security/PolicyDecisionPort.java
public interface PolicyDecisionPort {
    /**
     * Evaluates the policy for the given context.
     * Throws AccessDeniedException if the policy denies the operation.
     */
    void authorize(PolicyContext context);
}
```

---

### The aspect — builds context from `JoinPoint` (infrastructure layer)

Rather than writing a custom `@Aspect`, use Spring Security 6's
**`AuthorizationManagerBeforeMethodInterceptor`**. This is the framework's built-in plumbing for
exactly this pattern — it intercepts annotated methods, provides the full `MethodInvocation`
(method name, parameter names, argument values) and a `Supplier<Authentication>` (caller
identity + roles), and handles the AOP proxy setup automatically.

```java
// infrastructure/security/PolicyEnforcementAspect.java
// NOT a custom @Aspect — registered as Spring Security's method interceptor instead.
// No @Component — wired via @Bean in SecurityConfig (or CreateApplicationConfig).

public class PolicyEnforcementInterceptor
        implements AuthorizationManager<MethodInvocation> {

    private final PolicyDecisionPort policyDecisionPort;

    public PolicyEnforcementInterceptor(PolicyDecisionPort policyDecisionPort) {
        this.policyDecisionPort = policyDecisionPort;
    }

    @Override
    public AuthorizationDecision check(
            Supplier<Authentication> authenticationSupplier,
            MethodInvocation invocation) {

        AuthorizeOperation annotation =
            invocation.getMethod().getAnnotation(AuthorizeOperation.class);
        if (annotation == null) {
            return new AuthorizationDecision(true);   // not annotated → allow
        }

        Authentication auth = authenticationSupplier.get();
        PolicyContext context = new PolicyContext(
            annotation.value(),
            extractCallerId(auth),
            extractRoles(auth),
            extractInput(invocation)          // see below
        );

        policyDecisionPort.authorize(context);    // throws AccessDeniedException if denied
        return new AuthorizationDecision(true);
    }

    private Map<String, Object> extractInput(MethodInvocation invocation) {
        // MethodInvocation gives parameter names and values directly.
        // Requires -parameters compile flag (enabled by Spring Boot by default).
        Parameter[] params = invocation.getMethod().getParameters();
        Object[]    args   = invocation.getArguments();
        Map<String, Object> input = new LinkedHashMap<>();
        for (int i = 0; i < params.length; i++) {
            input.put(params[i].getName(), args[i]);
        }
        return input;
    }

    private String       extractCallerId(Authentication auth) { … }
    private List<String> extractRoles(Authentication auth)    { … }
}
```

**Wiring** — register as a Spring Security method interceptor advisor, not as an `@Aspect`:

```java
// config/MethodSecurityConfig.java  (or alongside CreateApplicationConfig)
@Configuration
@EnableMethodSecurity(prePostEnabled = false)   // disable SpEL-based annotations; use ours only
public class MethodSecurityConfig {

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public Advisor authorizeOperationAdvisor(PolicyDecisionPort policyDecisionPort) {
        return AuthorizationManagerBeforeMethodInterceptor.annotated(
            AuthorizeOperation.class,
            new PolicyEnforcementInterceptor(policyDecisionPort)
        );
    }
}
```

**Why this over a custom `@Aspect`:**
- Spring Security manages the AOP proxy — no `@EnableAspectJAutoProxy` needed.
- `@WithMockUser` and `@WithSecurityContext` work in tests because Spring Security's
  authentication propagation handles it.
- Spring Security publishes `AuthorizationDeniedEvent` automatically on denial —
  audit logging comes for free.
- `MethodInvocation` gives parameter names via `Method.getParameters()` — same information
  as `JoinPoint`, no difference in capability.

Key points:
- `Method.getParameters()` gives the declared parameter names (`command`, `applicationId`, etc.)
  — requires the class to be compiled with `-parameters` (Spring Boot does this by default).
- `invocation.getArguments()` gives the runtime argument values in the same order.
- The interceptor never imports any use-case or domain class — it works reflectively.

---

### Phase 1: local policy evaluation

The initial implementation evaluates policies locally using configurable role and rule definitions.
This is fully swappable for an external PDP later:

```java
// infrastructure/security/LocalPolicyDecisionPort.java
@Component
public class LocalPolicyDecisionPort implements PolicyDecisionPort {

    // Operation → required roles (loaded from config or hardcoded initially)
    private static final Map<String, List<String>> REQUIRED_ROLES = Map.of(
        "application:create",       List.of("ROLE_API_CASEWORKER"),
        "application:makeDecision", List.of("ROLE_API_CASEWORKER"),
        "application:assignCaseworker", List.of("ROLE_API_CASEWORKER", "ROLE_ADMIN")
    );

    @Override
    public void authorize(PolicyContext context) {
        List<String> required = REQUIRED_ROLES.getOrDefault(context.operation(), List.of());

        boolean hasAll = required.stream()
            .allMatch(role -> context.callerRoles().contains(role));

        if (!hasAll) {
            throw new AccessDeniedException(
                "Operation '" + context.operation() + "' denied for caller: " + context.callerId());
        }
        // Future: add contextual rules using context.input() fields (e.g. applicationId lookups)
    }
}
```

---

### Phase 2: external PDP (drop-in replacement)

When the PDP is ready, provide an alternative `PolicyDecisionPort` implementation. Wire it in
config — no other change anywhere:

```java
// infrastructure/security/ExternalPolicyDecisionPort.java
@Component
@ConditionalOnProperty("policy.external.enabled")  // or just swap the @Bean
public class ExternalPolicyDecisionPort implements PolicyDecisionPort {

    private final RestClient pdpClient;  // points to OPA / Keycloak / custom PDP

    @Override
    public void authorize(PolicyContext context) {
        // POST to PDP with { operation, callerId, callerRoles, input }
        // PDP returns { allow: true/false, reason: "…" }
        // Throw AccessDeniedException if allow == false
    }
}
```

The `PolicyContext` record is the stable contract between the codebase and the PDP. The `input`
map contains the method arguments — for `MakeDecisionCommand`, that includes `applicationId`,
which the PDP can use to look up additional context (assigned caseworker, application state) on
its own.

---

### Testing

**Use-case unit tests — no Spring, no authorization wiring**

Because `@AuthorizeOperation` is processed by a Spring Security method interceptor, calling
`execute(...)` directly in a plain JUnit test means the interceptor never fires. This is
intentional — use-case tests test business logic only:

```java
class MakeDecisionUseCaseTest {
    // No PolicyDecisionPort mock needed — interceptor never fires in plain unit tests
    @Test
    void makesDecision_happyPath() { … }
}
```

**Assert the annotation is present (per use case — reflection, no Spring)**

```java
@Test
void execute_isAnnotatedWithCorrectOperation() throws NoSuchMethodException {
    var method = MakeDecisionUseCase.class.getMethod("execute", MakeDecisionCommand.class);
    var annotation = method.getAnnotation(AuthorizeOperation.class);

    assertThat(annotation).isNotNull();
    assertThat(annotation.value()).isEqualTo("application:makeDecision");
}
```

**Interceptor test — verifies context is built correctly (once for the project)**

Because we use Spring Security's method interceptor (not a custom `@Aspect`), `@WithMockUser`
works out of the box. The test wires only the interceptor and a stub use case:

```java
@ExtendWith(SpringExtension.class)
@Import({MethodSecurityConfig.class,
         PolicyEnforcementAspectTest.StubConfig.class})
class PolicyEnforcementAspectTest {

    @MockBean PolicyDecisionPort policyDecisionPort;
    @Autowired StubUseCase stubUseCase;

    @Test
    @WithMockUser(roles = "API_CASEWORKER")
    void buildsPolicyContextFromMethodArgs() {
        UUID id = UUID.randomUUID();
        stubUseCase.doSomething(id);

        ArgumentCaptor<PolicyContext> captor = ArgumentCaptor.forClass(PolicyContext.class);
        verify(policyDecisionPort).authorize(captor.capture());

        PolicyContext ctx = captor.getValue();
        assertThat(ctx.operation()).isEqualTo("stub:doSomething");
        assertThat(ctx.input()).containsEntry("applicationId", id);
    }

    @Test
    @WithMockUser(roles = "API_CASEWORKER")
    void throwsWhenPortDenies() {
        doThrow(new AccessDeniedException("denied"))
            .when(policyDecisionPort).authorize(any());
        assertThatThrownBy(() -> stubUseCase.doSomething(UUID.randomUUID()))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Configuration
    static class StubConfig {
        @Bean StubUseCase stubUseCase() { return new StubUseCase(); }
    }

    static class StubUseCase {
        @AuthorizeOperation("stub:doSomething")
        public void doSomething(UUID applicationId) {}
    }
}
```

**`LocalPolicyDecisionPort` unit test — no Spring, tests all role/rule combinations**

```java
class LocalPolicyDecisionPortTest {
    LocalPolicyDecisionPort port = new LocalPolicyDecisionPort();

    @Test
    void allowsWhenCallerHasRequiredRole() {
        port.authorize(new PolicyContext(
            "application:create", "user-1", List.of("ROLE_API_CASEWORKER"), Map.of()));
        // no exception
    }

    @Test
    void deniesWhenCallerLacksRole() {
        assertThatThrownBy(() -> port.authorize(new PolicyContext(
            "application:create", "user-1", List.of("ROLE_READ_ONLY"), Map.of())))
            .isInstanceOf(AccessDeniedException.class);
    }
}
```

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
Security for use cases is enforced by placing `@AuthorizeOperation("application:create")` on
`execute(...)`. The aspect `PolicyEnforcementAspect` intercepts it, builds a `PolicyContext` from
the caller identity and method arguments, and calls `PolicyDecisionPort.authorize(context)`.
`LocalPolicyDecisionPort` (Phase 1) evaluates roles locally and throws
`org.springframework.security.access.AccessDeniedException` when denied. Remove
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
| **AuthorizeOperation** | A plain Java annotation in `usecase/shared/security/` placed on use-case `execute(...)` methods. Its `value()` is a stable operation name string (e.g. `"application:create"`). The `PolicyEnforcementAspect` reads this, plus caller identity and method args from the `JoinPoint`, builds a `PolicyContext`, and calls `PolicyDecisionPort.authorize(context)`. |
| **PolicyContext** | A plain Java record in `usecase/shared/security/` carrying: `operation` name, `callerId`, `callerRoles`, and `input` (method args keyed by parameter name). This is the stable contract between the codebase and any PDP — local or external. |
| **PolicyDecisionPort** | A plain Java interface in `usecase/shared/security/` with a single `authorize(PolicyContext)` method. `LocalPolicyDecisionPort` evaluates policies locally (Phase 1); `ExternalPolicyDecisionPort` calls an external PDP (Phase 2). Swapping is done in config only. |

