# Hexagonal Architecture Migration — MakeDecisionService Worked Example

**Status:** Worked example and design note  
**Baseline:** `main`  
**Validated against:** Current `MakeDecisionService` structure and partial POC work  
**Last reviewed:** 2026-04-16

## Current State

`MakeDecisionService` (249 lines) is the most complex service in the codebase. It directly depends on **5 repositories** and **6 entity types**:

### Dependencies

| Dependency | Type | Purpose |
|---|---|---|
| `ApplicationRepository` | JPA Repository | Find application by ID, save updated application |
| `DecisionRepository` | JPA Repository | Save decision entity |
| `ProceedingRepository` | JPA Repository | Look up proceedings by ID |
| `MeritsDecisionRepository` | JPA Repository | Save individual merits decisions |
| `CertificateRepository` | JPA Repository | Save/delete certificate on grant/refuse |
| `ApplicationValidations` | Validation | Validate the incoming request |
| `DomainEventService` | Service | Publish GRANTED/REFUSED domain events |

### Entity types used

`ApplicationEntity`, `CaseworkerEntity`, `DecisionEntity`, `MeritsDecisionEntity`, `ProceedingEntity`, `CertificateEntity`

### Current flow

```
Controller.makeDecision(applicationId, MakeDecisionRequest)
  └── MakeDecisionService.makeDecision(applicationId, request)
        ├── applicationRepository.findById(applicationId)
        ├── VersionCheckHelper.checkEntityVersionLocking(...)
        ├── applicationValidations.checkApplicationMakeDecisionRequest(request)
        ├── applicationRepository.save(application)  // update modifiedAt, autoGranted
        ├── for each proceeding in request:
        │     ├── proceedingRepository lookup
        │     └── meritsDecisionRepository.save(meritDecision)
        ├── decisionRepository.save(decision)
        ├── certificateRepository.save/delete (if GRANTED/REFUSED)
        ├── domainEventService.saveMakeDecisionDomainEvent(...)
        └── applicationRepository.save(application)  // set decision reference
```

## Migration Plan

### Step 1: Define domain models

New domain models needed (plain objects, no JPA annotations):

| Domain model | Fields | Notes |
|---|---|---|
| `Decision` | `id`, `overallDecision`, `meritsDecisions`, `modifiedAt` | Mirrors `DecisionEntity` |
| `MeritsDecision` | `id`, `decision` (status), `reason`, `justification`, `proceedingId`, `modifiedAt` | Mirrors `MeritsDecisionEntity` |
| `Proceeding` | `id`, `applicationId`, `isLead`, `proceedingContent` | Mirrors `ProceedingEntity` |
| `Certificate` | `id`, `applicationId`, `certificateContent` | Mirrors `CertificateEntity` |

The existing `Application` domain model (created during CreateApplication migration) is reused.

### Step 2: Define ports

**Inbound port:**

```java
public interface MakeDecisionUseCase {
    void makeDecision(MakeDecisionCommand command);
}
```

**Command record:**

```java
@Builder
public record MakeDecisionCommand(
    UUID applicationId,
    Long applicationVersion,
    Boolean autoGranted,
    DecisionStatus overallDecision,
    List<MakeDecisionProceedingDetail> proceedings,
    Map<String, Object> certificate,
    EventHistoryRequest eventHistory
) {}
```

**Outbound ports** (new ones — `ApplicationPersistencePort` already exists):

```java
public interface DecisionPersistencePort {
    Decision save(Decision decision);
}

public interface ProceedingPersistencePort {
    List<Proceeding> findAllById(List<UUID> ids);
}

public interface MeritsDecisionPersistencePort {
    MeritsDecision save(MeritsDecision meritsDecision);
}

public interface CertificatePersistencePort {
    void save(UUID applicationId, Map<String, Object> content);
    void deleteByApplicationId(UUID applicationId);
    boolean existsByApplicationId(UUID applicationId);
}
```

Extend the existing `DomainEventPort`:

```java
public interface DomainEventPort {
    void publishApplicationCreated(...);  // existing
    void publishMakeDecision(UUID applicationId, MakeDecisionCommand command,
                             UUID caseworkerId, DomainEventType type);  // new
}
```

### Step 3: Create adapters

| Adapter | Wraps | Effort |
|---|---|---|
| `DecisionPersistenceAdapter` | `DecisionRepository` + `DecisionEntity` mapping | ~50 lines |
| `ProceedingPersistenceAdapter` | `ProceedingRepository` + `ProceedingEntity` mapping | ~30 lines |
| `MeritsDecisionPersistenceAdapter` | `MeritsDecisionRepository` + `MeritsDecisionEntity` mapping | ~40 lines |
| `CertificatePersistenceAdapter` | `CertificateRepository` + `CertificateEntity` mapping | ~40 lines |
| Extend `DomainEventAdapter` | Add `publishMakeDecision` method | ~20 lines |
| Extend `DomainEntityMapper` | Add `Decision`, `MeritsDecision`, `Proceeding`, `Certificate` mappings | ~80 lines |

### Step 4: Create command factory

`MakeDecisionCommandFactory` in `adapter/inbound/rest/`:
- Converts `MakeDecisionRequest` (OpenAPI DTO) → `MakeDecisionCommand` (domain command)
- Moves `applicationValidations.checkApplicationMakeDecisionRequest()` into the factory (it's request validation, not domain logic)
- ~40 lines

### Step 5: Refactor the service

`MakeDecisionService`:
1. Implements `MakeDecisionUseCase`
2. Dependencies change to port interfaces
3. Works with domain `Decision`, `MeritsDecision`, `Proceeding`, `Certificate` instead of entities
4. Keeps `@Transactional`
5. Retains a legacy bridge `makeDecision(UUID, MakeDecisionRequest)` for existing tests

### Step 6: Update controller

```java
// Before
makeDecisionService.makeDecision(applicationId, request);

// After
MakeDecisionCommand command = makeDecisionCommandFactory.toCommand(applicationId, request);
makeDecisionUseCase.makeDecision(command);
```

## Test Impact

| Test file | Lines | Impact |
|---|---|---|
| `MakeDecisionForApplicationTest.java` (unit) | 1,078 | Legacy bridge method keeps it passing. No changes required. |
| `ApplicationMakeDecisionTest.java` (integration) | 1,003 | Tests through HTTP. Unaffected by internal restructuring. |

## Artefact Summary

| Category | New files | Estimated lines |
|---|---|---|
| Domain models | 4 | ~120 |
| Port interfaces | 5 | ~80 |
| Adapters | 4 new + 2 extended | ~260 |
| Command factory | 1 | ~40 |
| Service refactor | 1 modified | Net zero (same logic, different types) |
| Controller update | 1 modified | ~5 lines changed |
| **Total** | **~14 new + 3 modified** | **~500 new lines** |

## Verification Checklist

- [ ] `MakeDecisionService` does not import from `entity/` or `repository/`
- [ ] No file in `domain/` imports from infrastructure packages
- [ ] All unit tests pass (`./gradlew test`)
- [ ] All integration tests pass (`./gradlew integrationTest`)
- [ ] Checkstyle and Spotless pass (`./gradlew build`)
- [ ] API contract unchanged (same request/response shapes)

