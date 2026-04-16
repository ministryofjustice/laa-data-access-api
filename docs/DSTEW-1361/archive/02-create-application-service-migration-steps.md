# Step-by-Step: Migrate `CreateApplicationService` to Hexagonal Architecture

**Status:** Archived POC implementation plan  
**Baseline:** POC branch exploration  
**Validated against:** `CreateApplication` proof-of-concept work  
**Last reviewed:** 2026-04-16

This file is preserved as historical implementation guidance from the POC branch and is not the live plan for `main`.

## Current State

`CreateApplicationService` lives in `service/usecase/` and directly depends on:

- `ApplicationRepository` (Spring Data JPA)
- `ApplicationMapper` (MapStruct, maps API DTO → JPA entity)
- `ApplicationEntity` (JPA `@Entity`)
- `ApplicationCreateRequest` (OpenAPI-generated model with `@Schema` annotations)
- `ApplicationContent` (API model with `@JsonAnySetter`, `@Schema`)
- `DomainEventService` (concrete class, persists directly to `DomainEventRepository`)
- `ProceedingsService` (concrete class, persists directly to `ProceedingRepository`)
- `PayloadValidationService` (Jackson + Bean Validation — API-layer concern)
- `ApplicationValidations` (mixed domain / API validation)
- `@AllowApiCaseworker` (Spring Security annotation)
- `@Transactional` (Jakarta transaction annotation)

---

## Phase 1: Create the Domain Model

### Step 1 — Create `domain/model/Application.java`

Create a plain Java class with no framework annotations. It holds the fields the use case operates on:

```java
package uk.gov.justice.laa.dstew.access.domain.model;

import java.time.Instant;
import java.util.*;
import lombok.*;

@Getter @Setter @Builder(toBuilder = true)
@NoArgsConstructor @AllArgsConstructor
public class Application {
    private UUID id;
    private Long version;
    private ApplicationStatus status;
    private String laaReference;
    private String officeCode;
    private Map<String, Object> applicationContent;
    private Set<Individual> individuals;
    private Integer schemaVersion;
    private Instant createdAt;
    private Instant modifiedAt;
    private UUID applyApplicationId;
    private Instant submittedAt;
    private Boolean usedDelegatedFunctions;
    private CategoryOfLaw categoryOfLaw;
    private MatterType matterType;
    private Set<Application> linkedApplications;

    public void addLinkedApplication(Application toAdd) {
        if (linkedApplications == null) {
            linkedApplications = new HashSet<>();
        }
        linkedApplications.add(toAdd);
    }
}
```

Also create `domain/model/Individual.java` as a plain object mirroring the fields from `IndividualEntity` that the use case needs.

**Why:** The use case must not operate on JPA entities. A domain object has no persistence annotations and can be tested without any framework.

### Step 2 — Move enums and value objects into the domain package

Move (or duplicate as aliases) these into `domain/model/`:

- `ApplicationStatus`
- `CategoryOfLaw`
- `MatterType`

These are already simple enums with no framework coupling, so this is a package move. If other code still imports from `model/`, keep the originals and have the domain types be the canonical source (the API-layer models can wrap or re-export them).

### Step 3 — Move `ParsedAppContentDetails` into `domain/model/`

This record is already framework-free. Move it to `domain/model/ParsedAppContentDetails.java`. Update imports in `ApplicationContentParserService`.

### Step 4 — Extract content-parsing logic into `domain/service/ApplicationContentParser`

Create `domain/service/ApplicationContentParser.java` by extracting the logic from `ApplicationContentParserService`. This class should:

- Accept domain types only (e.g. proceedings list, office code) — not `ApplicationContent` (which has `@Schema` / `@JsonAnySetter`).
- Return `ParsedAppContentDetails`.
- Contain the lead-proceeding lookup, category-of-law derivation, matter-type derivation, and submitted-at parsing.

The existing `ApplicationContentParserService` becomes a thin adapter that converts the API `ApplicationContent` into domain inputs and delegates to `ApplicationContentParser`.

---

## Phase 2: Define Port Interfaces

### Step 5 — Create the driving port (inbound)

```java
// domain/port/inbound/CreateApplicationUseCase.java
package uk.gov.justice.laa.dstew.access.domain.port.inbound;

import java.util.UUID;

public interface CreateApplicationUseCase {
    UUID createApplication(CreateApplicationCommand command);
}
```

### Step 6 — Create `CreateApplicationCommand`

```java
// domain/port/inbound/CreateApplicationCommand.java
package uk.gov.justice.laa.dstew.access.domain.port.inbound;

import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import uk.gov.justice.laa.dstew.access.domain.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.domain.model.Individual;

@Builder
public record CreateApplicationCommand(
    ApplicationStatus status,
    String laaReference,
    Map<String, Object> applicationContent,
    Set<Individual> individuals,
    // Pre-validated and parsed content details:
    ParsedApplicationContent parsedContent
) {}
```

Where `ParsedApplicationContent` holds the already-validated `ApplicationContent` fields the use case needs (proceedings, linked applications, office, submitted-at, etc.) — i.e. the result of `PayloadValidationService.convertAndValidate()` mapped into domain types.

**Why:** The use case defines exactly what it needs. The controller adapter is responsible for constructing this from the API request.

### Step 7 — Create driven port (outbound) interfaces

```java
// domain/port/outbound/ApplicationPersistencePort.java
package uk.gov.justice.laa.dstew.access.domain.port.outbound;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.domain.model.Application;

public interface ApplicationPersistencePort {
    Application save(Application application);
    boolean existsByApplyApplicationId(UUID applyApplicationId);
    Optional<Application> findByApplyApplicationId(UUID applyApplicationId);
    List<Application> findAllByApplyApplicationIdIn(List<UUID> applyApplicationIds);
}
```

```java
// domain/port/outbound/ProceedingsPersistencePort.java
package uk.gov.justice.laa.dstew.access.domain.port.outbound;

import java.util.UUID;

public interface ProceedingsPersistencePort {
    void saveProceedings(Object applicationContent, UUID applicationId);
}
```

> The `Object applicationContent` parameter type is intentionally loose here — the adapter will know the concrete type. Alternatively, define a domain `Proceeding` list if cleaner.

```java
// domain/port/outbound/DomainEventPort.java
package uk.gov.justice.laa.dstew.access.domain.port.outbound;

import java.util.UUID;
import uk.gov.justice.laa.dstew.access.domain.model.Application;
import uk.gov.justice.laa.dstew.access.domain.port.inbound.CreateApplicationCommand;

public interface DomainEventPort {
    void publishApplicationCreated(Application application, CreateApplicationCommand command);
}
```

---

## Phase 3: Create Adapters

### Step 8 — Create `ApplicationPersistenceAdapter`

```java
// adapter/outbound/persistence/ApplicationPersistenceAdapter.java
@Component
@RequiredArgsConstructor
public class ApplicationPersistenceAdapter implements ApplicationPersistencePort {

    private final ApplicationRepository applicationRepository;
    private final DomainEntityMapper domainEntityMapper;

    @Override
    public Application save(Application domain) {
        ApplicationEntity entity = domainEntityMapper.toEntity(domain);
        ApplicationEntity saved = applicationRepository.save(entity);
        return domainEntityMapper.toDomain(saved);
    }

    @Override
    public boolean existsByApplyApplicationId(UUID id) {
        return applicationRepository.existsByApplyApplicationId(id);
    }

    @Override
    public Optional<Application> findByApplyApplicationId(UUID id) {
        ApplicationEntity entity = applicationRepository.findByApplyApplicationId(id);
        return Optional.ofNullable(entity).map(domainEntityMapper::toDomain);
    }

    @Override
    public List<Application> findAllByApplyApplicationIdIn(List<UUID> ids) {
        return applicationRepository.findAllByApplyApplicationIdIn(ids).stream()
            .map(domainEntityMapper::toDomain)
            .toList();
    }
}
```

### Step 9 — Create `DomainEntityMapper`

Add new mapping methods to `ApplicationMapper` (or create a dedicated `DomainEntityMapper`):

- `toDomain(ApplicationEntity) → Application`
- `toEntity(Application) → ApplicationEntity`

The existing `toApplicationEntity(ApplicationCreateRequest)` remains for any other code that still uses it, but `CreateApplicationService` no longer calls it.

### Step 10 — Create `ProceedingsPersistenceAdapter`

The simplest approach: have the existing `ProceedingsService` implement `ProceedingsPersistencePort`, or create a thin wrapper that delegates to it.

### Step 11 — Create `DomainEventAdapter`

```java
// adapter/outbound/event/DomainEventAdapter.java
@Component
@RequiredArgsConstructor
public class DomainEventAdapter implements DomainEventPort {

    private final DomainEventService domainEventService;
    private final DomainEntityMapper domainEntityMapper;

    @Override
    public void publishApplicationCreated(Application application, CreateApplicationCommand command) {
        // Map domain Application → ApplicationEntity for the existing DomainEventService API
        ApplicationEntity entity = domainEntityMapper.toEntity(application);
        // Map command → ApplicationCreateRequest for the existing event details builder
        // (or refactor DomainEventService to accept domain types in a future ticket)
        domainEventService.saveCreateApplicationDomainEvent(entity, toApiRequest(command), null);
    }
}
```

> **Pragmatic note:** The `DomainEventService` currently expects `ApplicationEntity` and `ApplicationCreateRequest`. Rather than rewriting `DomainEventService` in this ticket, the adapter bridges between domain types and the existing API. A future ticket can refactor `DomainEventService` to accept domain types directly.

---

## Phase 4: Rewire the Use Case and Controller

### Step 12 — Update `ApplicationController` (driving adapter)

```java
// Before
private final CreateApplicationService createApplicationService;

public ResponseEntity<Void> createApplication(..., ApplicationCreateRequest req) {
    UUID id = createApplicationService.createApplication(req);
    ...
}

// After
private final CreateApplicationUseCase createApplicationUseCase;

@AllowApiCaseworker  // ← security moves here
public ResponseEntity<Void> createApplication(..., ApplicationCreateRequest req) {
    // API-layer validation stays in the controller
    ApplicationContent content = payloadValidationService
        .convertAndValidate(req.getApplicationContent(), ApplicationContent.class);

    CreateApplicationCommand command = mapToCommand(req, content);
    UUID id = createApplicationUseCase.createApplication(command);
    ...
}
```

Key changes:
- Controller depends on `CreateApplicationUseCase` interface, not the concrete service.
- `@AllowApiCaseworker` moves from the service to the controller method.
- `PayloadValidationService.convertAndValidate()` is called here (it's API payload validation).
- A private `mapToCommand()` method builds `CreateApplicationCommand` from the API DTOs.

### Step 13 — Rewrite `CreateApplicationService`

The class now:
- Implements `CreateApplicationUseCase`.
- Depends only on port interfaces and domain services.
- Works with `Application` (domain model), not `ApplicationEntity`.
- Has no `@AllowApiCaseworker`.
- Keeps `@Transactional` (pragmatic choice for now).

```java
@RequiredArgsConstructor
@Service
public class CreateApplicationService implements CreateApplicationUseCase {

    private static final int APPLICATION_VERSION = 1;

    private final ApplicationPersistencePort applicationPersistence;
    private final ProceedingsPersistencePort proceedingsPersistence;
    private final DomainEventPort domainEvents;
    private final ApplicationContentParser contentParser;

    @Override
    @Transactional
    public UUID createApplication(final CreateApplicationCommand command) {
        Application application = buildApplicationFromCommand(command);
        checkForDuplicateApplication(application.getApplyApplicationId());
        application.setSchemaVersion(APPLICATION_VERSION);

        Application saved = applicationPersistence.save(application);

        linkToLeadApplicationIfApplicable(command, saved);
        proceedingsPersistence.saveProceedings(command.applicationContent(), saved.getId());
        domainEvents.publishApplicationCreated(saved, command);

        return saved.getId();
    }

    // ... private methods operate on domain types only
}
```

---

## Phase 5: Update Tests

### Step 14 — Write pure unit tests for the use case

Create `CreateApplicationServiceUnitTest.java`:
- Mock `ApplicationPersistencePort`, `ProceedingsPersistencePort`, `DomainEventPort`, `ApplicationContentParser`.
- Test business logic: duplicate checking, linked-application logic, schema version setting.
- No Spring context, no JPA, no security — pure Java + Mockito.

### Step 15 — Update the existing `CreateApplicationTest`

The existing test in `src/test/java/.../service/application/CreateApplicationTest.java`:
- Currently mocks `ApplicationRepository` directly.
- Update to mock port interfaces instead.
- All existing assertions about returned IDs, saved data, and domain events remain equivalent — they just verify port method calls instead of repository calls.

---

## Phase 6: Clean Up

### Step 16 — Address validation placement

| Validation | Current location | Target location |
|---|---|---|
| `PayloadValidationService.convertAndValidate()` (JSON + bean validation) | Called inside `CreateApplicationService` | Moves to controller (API-layer concern) |
| `ApplicationValidations.checkApplicationIdList()` | Called inside `CreateApplicationService` | Stays — called from use case (domain validation) |
| `ApplicationContentParserService` null/missing-proceeding checks | Inside `ApplicationContentParserService` | Moves to `ApplicationContentParser` (domain service) |
| Duplicate-application check | Inside `CreateApplicationService` | Stays — it's domain logic using the persistence port |

### Step 17 — `@Transactional` placement (future refinement)

For this ticket, `@Transactional` remains on `CreateApplicationService.createApplication()`. In a future ticket, consider:
- Moving it to a `TransactionalUseCaseExecutor` adapter.
- Or using `TransactionTemplate` inside the persistence adapter.

This is a lower-priority refinement that can be tackled when migrating subsequent use cases.

---

## Checklist

- [ ] `domain/model/Application.java` created (no framework annotations)
- [ ] `domain/model/Individual.java` created
- [ ] Enums (`ApplicationStatus`, `CategoryOfLaw`, `MatterType`) available in domain package
- [ ] `ParsedAppContentDetails` moved to `domain/model/`
- [ ] `domain/service/ApplicationContentParser.java` extracted
- [ ] `domain/port/inbound/CreateApplicationUseCase.java` interface created
- [ ] `domain/port/inbound/CreateApplicationCommand.java` record created
- [ ] `domain/port/outbound/ApplicationPersistencePort.java` interface created
- [ ] `domain/port/outbound/ProceedingsPersistencePort.java` interface created
- [ ] `domain/port/outbound/DomainEventPort.java` interface created
- [ ] `adapter/outbound/persistence/ApplicationPersistenceAdapter.java` created
- [ ] `adapter/outbound/persistence/ProceedingsPersistenceAdapter.java` created
- [ ] `adapter/outbound/event/DomainEventAdapter.java` created
- [ ] `DomainEntityMapper` methods (`toDomain` / `toEntity`) added
- [ ] `ApplicationController` updated to use `CreateApplicationUseCase` interface
- [ ] `@AllowApiCaseworker` moved from service to controller
- [ ] `PayloadValidationService.convertAndValidate()` call moved to controller
- [ ] `CreateApplicationService` refactored to implement `CreateApplicationUseCase` and depend on ports only
- [ ] New unit test `CreateApplicationServiceUnitTest` written
- [ ] Existing `CreateApplicationTest` updated to mock ports
- [ ] All existing tests pass
- [ ] No changes to API contracts or response behaviour

