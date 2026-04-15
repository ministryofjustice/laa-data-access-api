# DSTEW-1361: Hexagonal Architecture — CreateApplication POC

## Summary

Migrate `CreateApplicationService` to hexagonal (ports & adapters) architecture as a proof of concept, establishing the pattern and shared infrastructure for future use case migrations.

As a prerequisite, extract `CreateApplicationService` and `MakeDecisionService` out of the monolithic `ApplicationService` into dedicated classes in `service/usecase/`.

## What Was Done

### 1. Extract use cases from ApplicationService

`ApplicationService` on `main` was a 666-line god-class containing 8 unrelated public methods. Two complex, self-contained operations were extracted into dedicated classes:

| Extracted to | Lines | Methods | What it does |
|---|---|---|---|
| `CreateApplicationService` | 183 | `createApplication()` | Creates an application, validates content, saves proceedings, links related applications, publishes domain event |
| `MakeDecisionService` | 249 | `makeDecision()` | Applies a decision to an application — saves decisions, merits decisions, certificates, publishes domain event |

`ApplicationService` is now 298 lines with 6 remaining operations (get, update, assign, unassign, create note, get notes).

The controller was updated to dispatch to the new services:

```java
// Before (main) — everything went through ApplicationService
service.createApplication(applicationCreateReq);
service.makeDecision(applicationId, request);

// After — dedicated use case services
createApplicationUseCase.createApplication(command);
makeDecisionService.makeDecision(applicationId, request);
```

### 2. Migrate CreateApplicationService to hexagonal architecture

The extracted `CreateApplicationService` was then migrated to depend on **port interfaces** instead of JPA repositories and entities. This is the core of the hexagonal proof of concept.

#### New domain layer (`domain/`)

| File | Purpose |
|---|---|
| `domain/model/Application.java` | Plain domain object — no `@Entity`, no `@Schema`, no framework annotations |
| `domain/model/Individual.java` | Plain domain object for individuals |
| `domain/port/inbound/CreateApplicationUseCase.java` | Driving port interface — what the controller calls |
| `domain/port/inbound/CreateApplicationCommand.java` | Immutable command record carrying validated input |
| `domain/port/outbound/ApplicationPersistencePort.java` | Driven port for application persistence |
| `domain/port/outbound/ProceedingsPersistencePort.java` | Driven port for proceedings persistence |
| `domain/port/outbound/DomainEventPort.java` | Driven port for domain event publishing |

**Import constraint:** Nothing in `domain/` imports from `entity/`, `repository/`, `adapter/`, `controller/`, or `security/`. ✅ Verified.

#### New adapter layer (`adapter/`)

| File | Purpose |
|---|---|
| `adapter/inbound/rest/CreateApplicationCommandFactory.java` | Converts `ApplicationCreateRequest` (OpenAPI DTO) → `CreateApplicationCommand` (domain). Owns payload validation and content parsing. |
| `adapter/outbound/persistence/ApplicationPersistenceAdapter.java` | Implements `ApplicationPersistencePort` by delegating to `ApplicationRepository` |
| `adapter/outbound/persistence/ProceedingsPersistenceAdapter.java` | Implements `ProceedingsPersistencePort` by delegating to `ProceedingsService` |
| `adapter/outbound/persistence/DomainEntityMapper.java` | Maps `Application` ↔ `ApplicationEntity`, `Individual` ↔ `IndividualEntity` (shared across use cases) |
| `adapter/outbound/event/DomainEventAdapter.java` | Implements `DomainEventPort` by delegating to `DomainEventService` |

#### Refactored `CreateApplicationService`

- Implements `CreateApplicationUseCase`
- Depends on `ApplicationPersistencePort`, `ProceedingsPersistencePort`, `DomainEventPort` — not on `ApplicationRepository`, `ProceedingsService`, `DomainEventService`
- Works with domain `Application` — not with `ApplicationEntity`
- **Zero imports from `entity/` or `repository/`** ✅ Verified
- Retains a `@Deprecated` legacy bridge method `createApplication(ApplicationCreateRequest)` so existing tests pass without modification

#### Refactored `ApplicationController`

- Depends on `CreateApplicationUseCase` (port interface), not `CreateApplicationService` (concrete class)
- Delegates conversion to `CreateApplicationCommandFactory`
- `createApplication()` is now 4 lines — no mapping, no validation, just delegation

### 3. Documentation

Created planning and Confluence-ready documentation in `docs/DSTEW-1361/`:

| File | Purpose |
|---|---|
| `01-hexagonal-architecture-overview.md` | General architecture overview and rationale |
| `02-create-application-service-migration-steps.md` | Step-by-step migration plan with checklist |
| `03-implementation-prompt.md` | Reusable prompt for implementing future migrations |
| `confluence/01-overview.md` | Confluence: what and why |
| `confluence/02-impact-assessment.md` | Confluence: impact on codebase, tests, and team |
| `confluence/03-effort-estimation.md` | Confluence: per-service effort estimates |
| `confluence/04-gradual-migration-strategy.md` | Confluence: phased migration order |
| `confluence/05-make-decision-worked-example.md` | Confluence: detailed plan for next migration |
| `confluence/06-architecture-decision-record.md` | Confluence: formal ADR |

## Change Statistics

| Metric | Value |
|---|---|
| Branch | `DSTEW-1361-Hexagonal-Create-Application-POC` |
| Commits | 6 |
| Files changed (code) | 18 Java files |
| Files changed (docs) | 9 Markdown files |
| Java lines added | 1,040 |
| Java lines removed | 377 |
| Java net change | +663 lines |
| New Java files | 14 |
| Modified Java files | 4 (controller, ApplicationService, 2 tests) |
| Deleted Java files | 0 |

### Breakdown by category

| Category | New files | Lines |
|---|---|---|
| Domain models | 2 | 83 |
| Port interfaces (inbound + outbound) | 5 | 112 |
| Adapters (inbound + outbound) | 5 | 396 |
| Use case services (extracted) | 2 | 430 |
| Modified controller | — | +13 / -2 |
| Modified ApplicationService | — | +2 / -371 (methods removed) |
| Modified tests | — | +4 / -4 (import changes only) |

## Test Results

| Suite | Result |
|---|---|
| Unit tests | **290 passed**, 1 skipped (pre-existing) |
| Integration tests | **275 passed**, 1 skipped (pre-existing) |
| Checkstyle | ✅ Passed |
| Spotless | ✅ Passed |

**No existing tests were modified** beyond changing the import from `ApplicationService` to `CreateApplicationService` / `MakeDecisionService`. All assertions, test data, and expected behaviour are identical.

## What Is NOT Changed

- ❌ No API contract changes (request/response shapes, status codes, error messages, headers)
- ❌ No database schema changes
- ❌ No new dependencies (Gradle)
- ❌ No CI/CD changes
- ❌ No configuration changes
- ❌ No changes to `MakeDecisionService` internals (only extracted, not migrated to hex)
- ❌ No changes to `ApplicationService` remaining operations (get, update, assign, unassign, notes)
- ❌ No changes to `DomainEventService`, `ProceedingsService`, or any mapper

## Architecture Constraints Verified

| Constraint | Status |
|---|---|
| `domain/` has no imports from `entity/`, `repository/`, `adapter/`, `controller/`, `security/` | ✅ |
| `CreateApplicationService` has no imports from `entity/` or `repository/` | ✅ |
| Controller `createApplication()` delegates to port interface, not concrete service | ✅ |
| All existing tests pass without modification to test logic | ✅ |

## Dependency Flow (Before → After)

### Before (on main)

```
ApplicationController
  └── ApplicationService (666 lines, 8 methods, 15+ dependencies)
        ├── ApplicationRepository
        ├── ApplicationMapper
        ├── DomainEventService
        ├── ProceedingsService
        ├── PayloadValidationService
        ├── DecisionRepository
        ├── MeritsDecisionRepository
        ├── CertificateRepository
        ├── ProceedingRepository
        ├── CaseworkerRepository
        ├── NoteRepository
        └── ... (everything in one class)
```

### After (this branch)

```
ApplicationController
  ├── CreateApplicationUseCase (port interface)
  │     └── CreateApplicationService (183 lines, ports only)
  │           ├── ApplicationPersistencePort → ApplicationPersistenceAdapter → ApplicationRepository
  │           ├── ProceedingsPersistencePort → ProceedingsPersistenceAdapter → ProceedingsService
  │           └── DomainEventPort → DomainEventAdapter → DomainEventService
  │
  ├── MakeDecisionService (249 lines, extracted but not yet hexagonal)
  │     ├── ApplicationRepository (direct — future migration)
  │     ├── DecisionRepository
  │     ├── ProceedingRepository
  │     ├── MeritsDecisionRepository
  │     └── CertificateRepository
  │
  └── ApplicationService (298 lines, remaining operations)
        ├── ApplicationRepository
        ├── CaseworkerRepository
        ├── ProceedingRepository
        └── NoteRepository
```

## Follow-Up Work

This branch establishes the pattern. Subsequent tickets can migrate additional use cases:

| Ticket | Scope | Effort |
|---|---|---|
| Next | `MakeDecisionService` → hexagonal | 2–3 days |
| Future | `ApplicationService` → decompose into focused use cases | 3–5 days |
| Future | Remaining small services | 2–3 days |

See `docs/DSTEW-1361/confluence/` for the full migration strategy and effort estimates.


