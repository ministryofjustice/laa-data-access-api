# DSTEW-1361: Refactor `createApplication` to Clean Architecture

## Overview

The `createApplication` method in `ApplicationService` mixes API models (`ApplicationCreateRequest`),
JPA entities, domain logic, and infrastructure calls in a single class. This plan extracts it into
a layered clean architecture structure scoped to the `createapplication` use case, while leaving
all other `ApplicationService` methods untouched until future tickets.

> **Revision notes (post-review):** This version reflects decisions made after critical review:
> domain enums/records are created in `domain/` (not reused from `model/`);
> `@Component` is removed from all new non-mapper classes in favour of `@Configuration`/`@Bean`;
> `ProceedingJpaGateway` inlines proceedings logic rather than delegating to `ProceedingsService`;
> `UseCaseValidations` duplicates the relevant business validation into `usecase/shared/`;
> `AuthorisationException` is mapped to `org.springframework.security.access.AccessDeniedException`;
> `DomainEventService` is refactored in this ticket to accept domain types;
> `ApplicationGatewayMapper` is fully reimplemented without delegating to `ApplicationMapper`;
> and ArchUnit excluded legacy packages are explicitly documented.

---

## Target Package Structure

All paths are relative to:
`data-access-service/src/main/java/uk/gov/justice/laa/dstew/access/`

```
controller/
    ApplicationController.java               ← MODIFY (swap service call for use case)

usecase/
    shared/
        security/
            AccessPolicy.java                ← CREATE (interface, no Spring imports)
            RequiredRole.java                ← CREATE (enum: API_CASEWORKER, ADMIN, …)
            EnforceRole.java                 ← CREATE (annotation, java.lang.annotation only)
        validation/
            UseCaseValidations.java          ← CREATE (duplicated business validation logic)
        ApplicationConstants.java            ← CREATE (APPLICATION_SCHEMA_VERSION etc.)
    createapplication/
        CreateApplicationUseCase.java         ← CREATE (orchestration logic)
        CreateApplicationCommand.java         ← CREATE (input object, no API model imports)
        CreateApplicationCommandMapper.java   ← CREATE (maps ApplicationCreateRequest → Command)
        infrastructure/
            ApplicationGateway.java           ← CREATE (interface)
            ProceedingGateway.java            ← CREATE (interface)
            DomainEventGateway.java           ← CREATE (interface)

domain/
    ApplicationDomain.java                    ← CREATE (plain Java record, shared across use cases)
    ProceedingDomain.java                     ← CREATE (plain Java record, shared across use cases)
    ParsedAppContentDetails.java              ← CREATE (domain record — replaces model version)
    ApplicationStatus.java                    ← CREATE (plain Java enum — duplicate of model.ApplicationStatus)
    CategoryOfLaw.java                        ← CREATE (plain Java enum — duplicate of model.CategoryOfLaw)
    MatterType.java                           ← CREATE (plain Java enum — duplicate of model.MatterType)
    Individual.java                           ← CREATE (plain Java record — domain version)
    LinkedApplication.java                    ← CREATE (plain Java record — domain version)

infrastructure/
    security/
        SpringSecurityAccessPolicy.java       ← CREATE (implements AccessPolicy via SecurityContextHolder)
        EnforceRoleAspect.java                ← CREATE (@Aspect — intercepts @EnforceRole, calls AccessPolicy)
    jpa/
        createapplication/
            ApplicationJpaGateway.java        ← CREATE (implements ApplicationGateway)
            ProceedingJpaGateway.java         ← CREATE (implements ProceedingGateway — proceedings logic inlined)
            DomainEventJpaGateway.java        ← CREATE (implements DomainEventGateway)
            ApplicationGatewayMapper.java     ← CREATE (ApplicationDomain ↔ ApplicationEntity — fully reimplemented)
            ProceedingGatewayMapper.java      ← CREATE (ProceedingDomain ↔ ProceedingEntity — fully reimplemented)

config/
    CreateApplicationConfig.java              ← CREATE (@Configuration, constructor injection, no @Component on impls)

service/
    ApplicationService.java                   ← MODIFY (deprecate createApplication method)
    DomainEventService.java                   ← MODIFY (add domain-type overload, deprecate entity overload)
    ApplicationContentParserService.java      ← MODIFY (add parseFromMap returning domain types)
```

Test paths:
```
data-access-service/src/test/java/uk/gov/justice/laa/dstew/access/
    usecase/
        createapplication/
            CreateApplicationUseCaseTest.java  ← CREATE
    infrastructure/
        security/
            EnforceRoleAspectTest.java         ← CREATE (Spring slice test — written once for the project)
    arch/
        CleanArchitectureTest.java             ← CREATE
    service/
        application/
            CreateApplicationTest.java         ← MODIFY (@Disabled with cross-reference)
```

---

## Step-by-Step Implementation

### Step 1 – Create domain types

**Location:** `domain/`

These are pure Java records and enums. No JPA, no Spring, no API model imports. They live directly
under `domain/` (no use-case sub-package) because domain types are shared across use cases.

**Domain enums** — these duplicate enums currently in `model/`. The `model/` copies remain for
legacy code. Once all callers migrate, the `model/` copies can be retired.

```java
// domain/ApplicationStatus.java
public enum ApplicationStatus { PENDING, SUBMITTED, … }   // mirror model.ApplicationStatus values exactly

// domain/CategoryOfLaw.java
public enum CategoryOfLaw { … }   // mirror model.CategoryOfLaw values exactly

// domain/MatterType.java
public enum MatterType { … }      // mirror model.MatterType values exactly
```

**Domain records** — all field types are either Java standard library types or other types within
`domain/`. There are **zero imports** from `model/`, `entity/`, or any framework package.

```java
// domain/Individual.java
public record Individual(
    String firstName,
    String lastName,
    LocalDate dateOfBirth
    // … fields mirroring what the use case needs from the applicant
) {}

// domain/LinkedApplication.java
public record LinkedApplication(
    UUID leadApplicationId,
    UUID associatedApplicationId
) {}

// domain/ParsedAppContentDetails.java
public record ParsedAppContentDetails(
    UUID applyApplicationId,
    CategoryOfLaw categoryOfLaw,       // domain enum
    MatterType matterType,             // domain enum
    Instant submittedAt,
    String officeCode,
    Boolean usedDelegatedFunctions,
    List<LinkedApplication> allLinkedApplications
) {}

// domain/ApplicationDomain.java
public record ApplicationDomain(
    UUID id,
    ApplicationStatus status,
    String laaReference,
    String officeCode,
    UUID applyApplicationId,
    Boolean usedDelegatedFunctions,
    CategoryOfLaw categoryOfLaw,
    MatterType matterType,
    Instant submittedAt,
    Instant createdAt,              // populated after persistence
    Map<String, Object> applicationContent,
    List<Individual> individuals,
    int schemaVersion
) {}

// domain/ProceedingDomain.java
public record ProceedingDomain(
    UUID applicationId,
    boolean isLead,
    Map<String, Object> proceedingContent
) {}
```

---

### Step 2 – Add `parseFromMap` to `ApplicationContentParserService`

**Location:** `service/ApplicationContentParserService.java`

The existing `normaliseApplicationContentDetails(ApplicationContent)` remains for legacy callers.
Add a new method that reads from a `Map<String, Object>` (the raw JSON content from the command)
and returns `domain.ParsedAppContentDetails` with domain types:

```java
public domain.ParsedAppContentDetails parseFromMap(Map<String, Object> applicationContentMap) {
    // Internally converts the map to ApplicationContent (model) using Jackson, then
    // extracts and translates fields to domain types. ApplicationContent stays
    // entirely inside this method — it is never visible to the use case.
    ApplicationContent content =
        objectMapper.convertValue(applicationContentMap, ApplicationContent.class);
    var legacy = processingApplicationContent(content);
    return new domain.ParsedAppContentDetails(
        legacy.applyApplicationId(),
        toDomainCategoryOfLaw(legacy.categoryOfLaw()),
        toDomainMatterType(legacy.matterType()),
        legacy.submittedAt(),
        legacy.officeCode(),
        legacy.usedDelegatedFunctions(),
        toDomainLinkedApplications(legacy.allLinkedApplications())
    );
}
```

Private helpers `toDomainCategoryOfLaw`, `toDomainMatterType`, `toDomainLinkedApplications`
translate from `model/` to `domain/` types by matching enum value names.

> The use case calls `contentParser.parseFromMap(command.applicationContent())` and receives only
> domain types. It never imports `ApplicationContent`.

---

### Step 3 – Create shared use-case validation

**Location:** `usecase/shared/validation/UseCaseValidations.java`

`checkApplicationIdList` in `ApplicationService` is business logic that belongs in use cases.
Duplicate it here so the use case does not depend on `validation.ApplicationValidations` (which
carries additional dependencies on `model/` types and `EffectiveAuthorizationProvider`). Only
the subset of validations needed by the `createapplication` use case is extracted; additional
validations are added here as new use cases are introduced.

```java
public final class UseCaseValidations {

    private UseCaseValidations() {}

    public static void checkApplicationIdList(List<UUID> appIds) {
        if (appIds.stream().anyMatch(Objects::isNull)) {
            throw new ValidationException(List.of("Request contains null values for ids"));
        }
    }
}
```

---

### Step 4 – Create shared constants

**Location:** `usecase/shared/ApplicationConstants.java`

```java
public final class ApplicationConstants {
    private ApplicationConstants() {}

    public static final int APPLICATION_SCHEMA_VERSION = 1;
}
```

---

### Step 5 – Define gateway interfaces

**Location:** `usecase/createapplication/infrastructure/`

```java
// ApplicationGateway.java
public interface ApplicationGateway {
    ApplicationDomain save(ApplicationDomain domain);
    boolean existsByApplyApplicationId(UUID applyApplicationId);
    Optional<ApplicationDomain> findByApplyApplicationId(UUID applyApplicationId);
    ApplicationDomain addLinkedApplication(ApplicationDomain lead, ApplicationDomain linked);
}

// ProceedingGateway.java
public interface ProceedingGateway {
    void saveAll(UUID applicationId, List<ProceedingDomain> proceedings);
}

// DomainEventGateway.java
public interface DomainEventGateway {
    void saveCreatedEvent(ApplicationDomain saved);
}
```

**Key constraint:** These interfaces know only about domain types. No entity classes, no Spring
Data, no API models cross this boundary.

> `DomainEventGateway.saveCreatedEvent` takes only `ApplicationDomain`. The gateway implementation
> sources everything it needs for the event from the saved domain record — the command is not
> threaded through (see Step 7 on `DomainEventService` refactor).

---

### Step 6 – Create gateway mappers

**Location:** `infrastructure/jpa/createapplication/`

The mappers translate between domain POJOs and JPA entities. They live alongside the JPA gateway
implementations — not in `usecase/` — because they directly reference JPA entity classes.

**`ApplicationGatewayMapper.java`**
- `ApplicationEntity toEntity(ApplicationDomain domain)`
- `ApplicationDomain toDomain(ApplicationEntity entity)`

Implement the mapping **fully in this class** — do not delegate to `ApplicationMapper`.
`ApplicationMapper.toApplicationEntity` maps from `ApplicationCreateRequest` (an API model), not
from `ApplicationDomain`, so the inputs are incompatible. The two mappers are maintained
independently. `ApplicationMapper.toApplicationEntity` is deprecated for removal once all callers
migrate to the use-case path.

**`ProceedingGatewayMapper.java`**
- `ProceedingEntity toEntity(ProceedingDomain domain, UUID applicationId)`
- `ProceedingDomain toDomain(ProceedingEntity entity)`

Implement independently; do not delegate to `ProceedingMapper`.

---

### Step 7 – Refactor `DomainEventService.saveCreateApplicationDomainEvent`

**Location:** `service/DomainEventService.java`

Add a new overload that accepts only domain types:

```java
/**
 * Posts an APPLICATION_CREATED domain event using domain types.
 * Preferred over the deprecated entity/request overload.
 */
public void saveCreateApplicationDomainEvent(ApplicationDomain saved, String createdBy) {

    CreateApplicationDomainEventDetails domainEventDetails =
        CreateApplicationDomainEventDetails.builder()
            .applicationId(saved.id())
            .createdDate(saved.createdAt())
            .laaReference(saved.laaReference())
            .applicationStatus(saved.status().name())
            .build();   // .request() omitted — event carries domain state, not raw API payload

    DomainEventEntity entity =
        DomainEventEntity.builder()
            .applicationId(saved.id())
            .caseworkerId(null)
            .type(DomainEventType.APPLICATION_CREATED)
            .createdAt(Instant.now())
            .createdBy(createdBy)
            .data(getEventDetailsAsJson(domainEventDetails, DomainEventType.APPLICATION_CREATED))
            .serviceName(serviceNameContext.getServiceName())
            .build();

    domainEventRepository.save(entity);
}
```

Deprecate the old overload:

```java
/**
 * @deprecated Use {@link #saveCreateApplicationDomainEvent(ApplicationDomain, String)} instead.
 */
@Deprecated
@AllowApiCaseworker
public void saveCreateApplicationDomainEvent(
    ApplicationEntity applicationEntity, ApplicationCreateRequest request, String createdBy) { … }
```

> `CreateApplicationDomainEventDetails.request` (the raw JSON snapshot of the API request) is
> intentionally **omitted** from the new overload. If downstream consumers require it, a follow-up
> ticket should add `Map<String, Object> applicationContent` to the event details sourced from
> `ApplicationDomain.applicationContent()`.

---

### Step 8 – Implement JPA gateways

**Location:** `infrastructure/jpa/createapplication/`

Do **not** annotate these classes with `@Component`. They are wired exclusively via `@Bean` in
`CreateApplicationConfig`.

**`ApplicationJpaGateway.java`** (implements `ApplicationGateway`)
```java
public class ApplicationJpaGateway implements ApplicationGateway {
    private final ApplicationRepository applicationRepository;
    private final ApplicationGatewayMapper mapper;

    public ApplicationJpaGateway(ApplicationRepository applicationRepository,
                                  ApplicationGatewayMapper mapper) { … }

    @Override public ApplicationDomain save(ApplicationDomain domain) {
        return mapper.toDomain(applicationRepository.save(mapper.toEntity(domain)));
    }
    @Override public boolean existsByApplyApplicationId(UUID id) {
        return applicationRepository.existsByApplyApplicationId(id);
    }
    @Override public Optional<ApplicationDomain> findByApplyApplicationId(UUID id) {
        return Optional.ofNullable(applicationRepository.findByApplyApplicationId(id))
                       .map(mapper::toDomain);
    }
    @Override public ApplicationDomain addLinkedApplication(ApplicationDomain lead, ApplicationDomain linked) {
        ApplicationEntity leadEntity = mapper.toEntity(lead);
        leadEntity.addLinkedApplication(mapper.toEntity(linked));
        return mapper.toDomain(applicationRepository.save(leadEntity));
    }
}
```

**`ProceedingJpaGateway.java`** (implements `ProceedingGateway`)

Inline the save logic directly — do **not** delegate to `ProceedingsService`.
`ProceedingsService.saveProceedings` accepts `ApplicationContent` (a model type) which is
unavailable in the gateway layer. The inline logic mirrors what `ProceedingsService` does:

```java
public class ProceedingJpaGateway implements ProceedingGateway {
    private final ProceedingRepository proceedingRepository;
    private final ProceedingGatewayMapper mapper;

    @Override
    public void saveAll(UUID applicationId, List<ProceedingDomain> proceedings) {
        if (proceedings == null || proceedings.isEmpty()) {
            return;
        }
        List<ProceedingEntity> entities = proceedings.stream()
            .map(p -> mapper.toEntity(p, applicationId))
            .toList();
        proceedingRepository.saveAll(entities);
    }
}
```

**`DomainEventJpaGateway.java`** (implements `DomainEventGateway`)

```java
public class DomainEventJpaGateway implements DomainEventGateway {
    private final DomainEventService domainEventService;

    @Override
    public void saveCreatedEvent(ApplicationDomain saved) {
        domainEventService.saveCreateApplicationDomainEvent(saved, null);
    }
}
```

`DomainEventService` already injects `ServiceNameContext` internally — it does not need to be
passed through the gateway.

---

### Step 9 – Create `CreateApplicationCommand` and `CreateApplicationCommandMapper`

**Location:** `usecase/createapplication/`

**`CreateApplicationCommand.java`**
```java
public record CreateApplicationCommand(
    domain.ApplicationStatus status,
    String laaReference,
    Map<String, Object> applicationContent,
    List<domain.Individual> individuals
) {}
```

> The `serialisedOriginalRequest` field from earlier drafts has been removed. The domain event no
> longer carries a raw JSON snapshot of the API request — it uses domain state (see Step 7).

**`CreateApplicationCommandMapper.java`**
```java
@Component
public class CreateApplicationCommandMapper {

    public CreateApplicationCommand toCommand(ApplicationCreateRequest req) {
        return new CreateApplicationCommand(
            toDomainStatus(req.getStatus()),
            req.getLaaReference(),
            req.getApplicationContent(),
            toDomainIndividuals(req.getIndividuals())
        );
    }

    // private helpers for status and individuals enum/record translation
}
```

This is the **only** place `ApplicationCreateRequest` (the generated API model) appears on the
use-case side of the boundary.

---

### Step 10 – Write `CreateApplicationUseCase`

**Location:** `usecase/createapplication/CreateApplicationUseCase.java`

```java
@Transactional
public class CreateApplicationUseCase {

    private final ApplicationGateway applicationGateway;
    private final ProceedingGateway proceedingGateway;
    private final DomainEventGateway domainEventGateway;
    private final ApplicationContentParserService contentParser;
    private final PayloadValidationService payloadValidationService;

    public CreateApplicationUseCase(…) { /* constructor injection */ }

    @EnforceRole(anyOf = RequiredRole.API_CASEWORKER)
    public UUID execute(CreateApplicationCommand command) {
        //    and returns domain types; ApplicationContent (model) never enters the use case
        ParsedAppContentDetails details = contentParser.parseFromMap(command.applicationContent());

        // 2. check for duplicate
        if (applicationGateway.existsByApplyApplicationId(details.applyApplicationId())) {
            throw new ValidationException(List.of(
                "Application already exists for Apply Application Id: "
                    + details.applyApplicationId()));
        }

        // 3. validate linked application ids
        if (details.allLinkedApplications() != null) {
            List<UUID> associatedIds = details.allLinkedApplications().stream()
                .map(LinkedApplication::associatedApplicationId)
                .filter(id -> !id.equals(details.applyApplicationId()))
                .toList();
            UseCaseValidations.checkApplicationIdList(associatedIds);
        }

        // 4. build ApplicationDomain
        ApplicationDomain domain = new ApplicationDomain(
            null,
            command.status(),
            command.laaReference(),
            details.officeCode(),
            details.applyApplicationId(),
            details.usedDelegatedFunctions(),
            details.categoryOfLaw(),
            details.matterType(),
            details.submittedAt(),
            null,           // createdAt — populated by persistence layer
            command.applicationContent(),
            command.individuals(),
            APPLICATION_SCHEMA_VERSION
        );

        // 5. save
        ApplicationDomain saved = applicationGateway.save(domain);

        // 6. link to lead if applicable
        linkToLeadIfApplicable(details, saved);

        // 7. save proceedings
        List<ProceedingDomain> proceedings = buildProceedingDomains(
            command.applicationContent(), saved.id());
        proceedingGateway.saveAll(saved.id(), proceedings);

        // 8. publish domain event
        domainEventGateway.saveCreatedEvent(saved);

        return saved.id();
    }

    private void linkToLeadIfApplicable(ParsedAppContentDetails details, ApplicationDomain saved) {
        if (details.allLinkedApplications() == null || details.allLinkedApplications().isEmpty()) {
            return;
        }
        UUID leadApplyId = details.allLinkedApplications().getFirst().leadApplicationId();
        if (leadApplyId == null) {
            return;
        }
        ApplicationDomain lead = applicationGateway.findByApplyApplicationId(leadApplyId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Linking failed > Lead application not found, ID: " + leadApplyId));
        applicationGateway.addLinkedApplication(lead, saved);
    }

    private List<ProceedingDomain> buildProceedingDomains(
            Map<String, Object> contentMap, UUID applicationId) {
        // Use Jackson to extract the proceedings list from the raw content map
        // and convert each entry to a ProceedingDomain record
        …
    }
}
```

Note: No `@Component` — the use case is wired via `@Bean` in `CreateApplicationConfig`. No
`@AllowApiCaseworker` — security is enforced by `@EnforceRole` / `EnforceRoleAspect`. No Spring
Security imports in this class.

**What moves here from `ApplicationService`:**
- `createApplication` body
- `checkForDuplicateApplication` logic
- `linkToLeadApplicationIfApplicable` / `getLeadApplication` / `getLeadApplicationId` logic
- `checkIfAllAssociatedApplicationsExist` logic (via `UseCaseValidations.checkApplicationIdList`)
- `setValuesFromApplicationContent` logic (via `contentParser.parseFromMap`)

**What stays in `ApplicationService`:**
- All other public methods (`getApplication`, `updateApplication`, `assignCaseworker`, etc.)

---

### Step 11 – Update `ApplicationController`

Inject `CreateApplicationUseCase` and `CreateApplicationCommandMapper` via constructor.
Replace the `createApplication` body:

```java
// Before:
UUID id = service.createApplication(applicationCreateReq);

// After:
UUID id = createApplicationUseCase.execute(commandMapper.toCommand(applicationCreateReq));
```

`ApplicationService` remains injected for all other endpoints — do not remove it.

---

### Step 12 – Deprecate `ApplicationService.createApplication`

Add `@Deprecated` with a Javadoc pointer to `CreateApplicationUseCase`. **Remove
`@AllowApiCaseworker`** from the deprecated method to prevent a double security check:

```java
/**
 * @deprecated Use {@link CreateApplicationUseCase} instead.
 */
@Deprecated
@Transactional
public UUID createApplication(final ApplicationCreateRequest req) { … }
```

---

### Step 13 – Wire beans in `config/CreateApplicationConfig.java`

```java
@Configuration
public class CreateApplicationConfig {

    @Bean
    public CreateApplicationUseCase createApplicationUseCase(
            ApplicationGateway applicationGateway,
            ProceedingGateway proceedingGateway,
            DomainEventGateway domainEventGateway,
            ApplicationContentParserService contentParser,
            PayloadValidationService payloadValidationService) {
        return new CreateApplicationUseCase(
            applicationGateway, proceedingGateway, domainEventGateway,
            contentParser, payloadValidationService);
    }

    @Bean
    public ApplicationGateway applicationGateway(
            ApplicationRepository repo, ApplicationGatewayMapper mapper) {
        return new ApplicationJpaGateway(repo, mapper);
    }

    @Bean
    public ProceedingGateway proceedingGateway(
            ProceedingRepository repo, ProceedingGatewayMapper mapper) {
        return new ProceedingJpaGateway(repo, mapper);
    }

    @Bean
    public DomainEventGateway domainEventGateway(DomainEventService domainEventService) {
        return new DomainEventJpaGateway(domainEventService);
    }

    @Bean
    public ApplicationGatewayMapper applicationGatewayMapper() {
        return new ApplicationGatewayMapper();
    }

    @Bean
    public ProceedingGatewayMapper proceedingGatewayMapper() {
        return new ProceedingGatewayMapper();
    }

    @Bean
    public CreateApplicationCommandMapper createApplicationCommandMapper() {
        return new CreateApplicationCommandMapper();
    }
}
```

Rules:
- **No `@Component`** on use cases, gateway implementations, or gateway mappers — exclusively
  wired through this `@Configuration` class to avoid duplicate bean definitions.
- No `@Autowired` fields anywhere in the new code; all dependencies via constructors.
- `@Component` is acceptable on `CreateApplicationCommandMapper` if it needs to be injected
  elsewhere, but the canonical instantiation point remains the `@Bean` above.

---

### Step 14 – Write use-case unit tests

**Location:** `test/.../usecase/createapplication/CreateApplicationUseCaseTest.java`

- **No `@SpringBootTest`** — pure JUnit 5 + Mockito.
- Mock: `ApplicationGateway`, `ProceedingGateway`, `DomainEventGateway`.
- Real (or spied): `ApplicationContentParserService`, `PayloadValidationService`.

Scenarios to cover (mirroring `CreateApplicationTest`):

| Scenario | Assertion |
|----------|-----------|
| Happy path — single application | Returns UUID, `applicationGateway.save` called once, `domainEventGateway.saveCreatedEvent` called once |
| Happy path — linked application | `applicationGateway.addLinkedApplication` called, lead application found |
| Duplicate `applyApplicationId` | `ValidationException` thrown with correct message |
| Missing lead application | `ResourceNotFoundException` thrown |
| Missing associated application | `ValidationException` from `UseCaseValidations` |
| Invalid content (no lead proceeding) | `ValidationException` from parser |
| Security annotation present | `execute` method carries `@EnforceRole(anyOf = RequiredRole.API_CASEWORKER)` (reflection assertion) |

```java
@Test
void execute_isAnnotatedWithCorrectRole() throws NoSuchMethodException {
    var method = CreateApplicationUseCase.class
        .getMethod("execute", CreateApplicationCommand.class);
    var annotation = method.getAnnotation(EnforceRole.class);

    assertThat(annotation).isNotNull();
    assertThat(annotation.anyOf()).contains(RequiredRole.API_CASEWORKER);
}
```

**`EnforceRoleAspectTest`** — written once for the whole project.
Located at `test/.../infrastructure/security/EnforceRoleAspectTest.java`:

```java
@ExtendWith(SpringExtension.class)
@Import({EnforceRoleAspect.class, EnforceRoleAspectTest.StubUseCase.class})
class EnforceRoleAspectTest {

    @MockBean AccessPolicy accessPolicy;
    @Autowired StubUseCase stubUseCase;

    @Test
    void passesFullAnnotationToAccessPolicy() {
        stubUseCase.doSomething();
        ArgumentCaptor<EnforceRole> captor = ArgumentCaptor.forClass(EnforceRole.class);
        verify(accessPolicy).enforce(captor.capture());
        assertThat(captor.getValue().anyOf()).containsExactly(RequiredRole.API_CASEWORKER);
    }

    @Test
    void throwsWhenAccessPolicyDenies() {
        doThrow(new AccessDeniedException("denied"))
            .when(accessPolicy).enforce(any(EnforceRole.class));
        assertThatThrownBy(() -> stubUseCase.doSomething())
            .isInstanceOf(AccessDeniedException.class);
    }

    @Component
    static class StubUseCase {
        @EnforceRole(anyOf = RequiredRole.API_CASEWORKER)
        public void doSomething() {}
    }
}
```

Add `@Disabled("Superseded by CreateApplicationUseCaseTest")` to the existing
`service/application/CreateApplicationTest` with a comment pointing to the new test class.

---

### Step 15 – Verify integration tests

Run `./gradlew integrationTest` after wiring the controller to the use case. The integration tests
exercise `createApplication` end-to-end through HTTP and must continue to pass. If any test setup
calls `ApplicationService.createApplication` directly (e.g. for data seeding), update those
call sites to use the new use case path or the equivalent HTTP endpoint. Do not mark integration
tests as `@Disabled`.

---

### Step 16 – Add ArchUnit and write architecture tests

**`build.gradle` change (data-access-service):**
```groovy
testImplementation 'com.tngtech.archunit:archunit-junit5:1.3.0'
```

**Location:** `test/.../arch/CleanArchitectureTest.java`

```java
@AnalyzeClasses(packages = "uk.gov.justice.laa.dstew.access")
public class CleanArchitectureTest {

    // ── Excluded legacy packages ──────────────────────────────────────────────
    // The following packages are pre-existing and not yet migrated to clean architecture.
    // They are intentionally excluded from the rules below and must be reviewed as each
    // area migrates:
    //   service/        — service classes call repositories directly (legacy pattern)
    //   mapper/         — existing mappers depend on both entity and model types
    //   validation/     — ApplicationValidations depends on model types
    //   specification/  — Spring Data Specification helpers
    //   transformation/ — AOP transformation pipeline
    //   convertors/     — enum conversion utilities
    //   model/          — internal model classes (non-generated); to be moved to domain/ over time

    @ArchTest
    static final ArchRule useCasesMustNotDependOnEntities =
        noClasses()
            .that().resideInAPackage("..usecase..")
            .and().haveSimpleNameNotEndingWith("Mapper")
            .should().dependOnClassesThat().resideInAPackage("..entity..");

    @ArchTest
    static final ArchRule useCasesMustNotDependOnRepositories =
        noClasses()
            .that().resideInAPackage("..usecase..")
            .should().dependOnClassesThat().resideInAPackage("..repository..");

    // Mapper suffix exemption covers CreateApplicationCommandMapper (imports ApplicationCreateRequest)
    @ArchTest
    static final ArchRule useCasesMustNotDependOnApiModels =
        noClasses()
            .that().resideInAPackage("..usecase..")
            .and().haveSimpleNameNotEndingWith("Mapper")
            .should().dependOnClassesThat()
                .resideInAPackage("uk.gov.justice.laa.dstew.access.model..");

    // domain/ must not import from model/, entity/, repository/, or any framework
    @ArchTest
    static final ArchRule domainMustBeFrameworkFree =
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework..",
                "jakarta.persistence..",
                "..entity..",
                "..repository..",
                "..model..");

    @ArchTest
    static final ArchRule controllersMustNotDependOnRepositories =
        noClasses()
            .that().resideInAPackage("..controller..")
            .should().dependOnClassesThat().resideInAPackage("..repository..");

    @ArchTest
    static final ArchRule useCasesMustNotDependOnInfrastructureImplementations =
        noClasses()
            .that().resideInAPackage("..usecase..")
            .should().dependOnClassesThat()
                .resideInAPackage("..infrastructure.jpa..");

    @ArchTest
    static final ArchRule useCasesMustNotDependOnSpringSecurityAnnotations =
        noClasses()
            .that().resideInAPackage("..usecase..")
            .should().dependOnClassesThat()
                .resideInAnyPackage(
                    "org.springframework.security..",
                    "org.springframework.security.access.prepost..");
}
```

---

## Files Summary

| File | Action |
|------|--------|
| `domain/ApplicationStatus.java` | Create (enum — duplicate of model version) |
| `domain/CategoryOfLaw.java` | Create (enum — duplicate of model version) |
| `domain/MatterType.java` | Create (enum — duplicate of model version) |
| `domain/Individual.java` | Create (record — domain version) |
| `domain/LinkedApplication.java` | Create (record — domain version) |
| `domain/ParsedAppContentDetails.java` | Create (record — domain version) |
| `domain/ApplicationDomain.java` | Create |
| `domain/ProceedingDomain.java` | Create |
| `usecase/shared/security/AccessPolicy.java` | Create (interface: `enforce(EnforceRole)`) |
| `usecase/shared/security/RequiredRole.java` | Create (enum: AUTHENTICATED, API_CASEWORKER, ADMIN, SUPERVISOR) |
| `usecase/shared/security/EnforceRole.java` | Create (annotation with `anyOf`/`allOf` arrays) |
| `usecase/shared/security/AuthorizationPort.java` | Create (contextual policy interface) |
| `usecase/shared/security/ApplicationAction.java` | Create (enum: READ, UPDATE, MAKE_DECISION, …) |
| `usecase/shared/validation/UseCaseValidations.java` | Create |
| `usecase/shared/ApplicationConstants.java` | Create |
| `usecase/createapplication/CreateApplicationCommand.java` | Create |
| `usecase/createapplication/CreateApplicationCommandMapper.java` | Create |
| `usecase/createapplication/CreateApplicationUseCase.java` | Create |
| `usecase/createapplication/infrastructure/ApplicationGateway.java` | Create |
| `usecase/createapplication/infrastructure/ProceedingGateway.java` | Create |
| `usecase/createapplication/infrastructure/DomainEventGateway.java` | Create |
| `infrastructure/security/SpringSecurityAccessPolicy.java` | Create (evaluates allOf/anyOf) |
| `infrastructure/security/SpringAuthorizationPort.java` | Create (contextual checks; may load domain state) |
| `infrastructure/security/EnforceRoleAspect.java` | Create |
| `infrastructure/jpa/createapplication/ApplicationJpaGateway.java` | Create (no @Component) |
| `infrastructure/jpa/createapplication/ProceedingJpaGateway.java` | Create (proceedings logic inlined) |
| `infrastructure/jpa/createapplication/DomainEventJpaGateway.java` | Create |
| `infrastructure/jpa/createapplication/ApplicationGatewayMapper.java` | Create (fully reimplemented) |
| `infrastructure/jpa/createapplication/ProceedingGatewayMapper.java` | Create (fully reimplemented) |
| `config/CreateApplicationConfig.java` | Create |
| `controller/ApplicationController.java` | Modify |
| `service/ApplicationService.java` | Modify (deprecate `createApplication`, remove `@AllowApiCaseworker`) |
| `service/DomainEventService.java` | Modify (add domain overload, deprecate entity overload) |
| `service/ApplicationContentParserService.java` | Modify (add `parseFromMap` returning domain types) |
| `test/.../usecase/createapplication/CreateApplicationUseCaseTest.java` | Create |
| `test/.../infrastructure/security/EnforceRoleAspectTest.java` | Create |
| `test/.../arch/CleanArchitectureTest.java` | Create |
| `service/application/CreateApplicationTest.java` | Modify (`@Disabled` with cross-reference) |
| `data-access-service/build.gradle` | Modify (add archunit) |
| Integration tests | Verify pass; update call sites if needed |

---

## Open Questions & Risks

### 1. Security enforcement approach
`@AllowApiCaseworker` must **not** be placed on `CreateApplicationUseCase`. Security is handled via:
- `@EnforceRole(anyOf = RequiredRole.API_CASEWORKER)` on `execute(...)` — a plain Java annotation
  with `anyOf` (OR) and `allOf` (AND) `RequiredRole[]` arrays; zero Spring imports. Defined in
  `usecase/shared/security/`.
- `EnforceRoleAspect` in `infrastructure/security/` — intercepts it and passes the whole
  annotation to `SpringSecurityAccessPolicy`, which evaluates the `allOf`/`anyOf` arrays.
- `SpringSecurityAccessPolicy.enforce` throws
  `org.springframework.security.access.AccessDeniedException` (a Spring Security type; permitted
  in the infrastructure layer where Spring imports are allowed).

Remove `@AllowApiCaseworker` from the deprecated `ApplicationService.createApplication`.
prevent a double security check.

### 2. `DomainEventService` refactor scope
`DomainEventService.saveCreateApplicationDomainEvent` is refactored in this ticket (Step 7) to
add a domain-type overload. The old `(ApplicationEntity, ApplicationCreateRequest)` overload is
deprecated. Note that `CreateApplicationDomainEventDetails.request` (the raw request JSON snapshot)
is omitted from the new event payload — if downstream consumers require it, a follow-up ticket
should add `applicationContent` from the domain record to the event details.

### 3. `ApplicationMapper.toApplicationEntity` deprecation
`ApplicationGatewayMapper` is a full independent reimplementation. `ApplicationMapper.toApplicationEntity`
is deprecated once `ApplicationService.createApplication` is removed.

### 4. `ProceedingsService` inlining
`ProceedingJpaGateway` inlines the proceedings save logic. `ProceedingsService` itself is not
modified in this ticket; it continues to serve any remaining legacy callers. A follow-up ticket
can migrate `ProceedingsService` to clean architecture or remove it once all callers migrate.
