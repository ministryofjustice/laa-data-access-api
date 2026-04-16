# Hexagonal Architecture Migration — Impact Assessment

**Status:** Impact assessment  
**Baseline:** `main`  
**Validated against:** `CreateApplication` POC branch work  
**Last reviewed:** 2026-04-16

## What Changes?

### New packages introduced

| Package | Purpose |
|---|---|
| `domain/model/` | Plain domain objects (no JPA, no Swagger annotations) |
| `domain/port/inbound/` | Use case interfaces + command records |
| `domain/port/outbound/` | Persistence and event port interfaces |
| `adapter/inbound/rest/` | Command factories (API DTO → domain command conversion) |
| `adapter/outbound/persistence/` | JPA adapters implementing persistence ports |
| `adapter/outbound/event/` | Event adapters implementing event ports |

### Existing packages — unchanged

The existing `entity/`, `repository/`, `mapper/`, `model/`, `controller/`, `service/` packages **stay where they are**. Adapters wrap them. Nothing is deleted or restructured.

### What is added per use case migration

Based on the `CreateApplicationService` migration on the POC branch (actual counts):

| Artefact | Files | Total lines |
|---|---|---|
| Domain model classes | 2 | ~83 |
| Port interfaces (inbound + outbound) | 4 | ~96 |
| Outbound adapters (persistence + event) | 3 | ~149 |
| Inbound adapter (command factory) | 1 | ~69 |
| Domain-entity mapper (shared, created once) | 1 | ~172 |
| **Total new files** | **12** | **~591** |
| Modified files (controller + service) | 2 | Net reduction in service complexity |

### What is reusable across use cases

Several artefacts created for `CreateApplicationService` on the POC branch are **shared infrastructure** that won't need to be recreated if that work is reused:

- `DomainEntityMapper` — maps `Application` ↔ `ApplicationEntity`, `Individual` ↔ `IndividualEntity`. Extended as new domain models are added.
- `ApplicationPersistenceAdapter` — wraps `ApplicationRepository`. Any use case that reads/writes applications reuses this.
- `domain/model/Application.java` and `domain/model/Individual.java` — the domain models are shared.

Subsequent use cases will only need:
- Their own **port interface** (e.g. `MakeDecisionUseCase`)
- Their own **command record** (e.g. `MakeDecisionCommand`)
- Their own **command factory** (e.g. `MakeDecisionCommandFactory`)
- Any **new persistence adapters** for repositories they use that haven't been wrapped yet
- Possibly new **domain model** classes if they touch entities not yet modelled

## Impact on Existing Tests

| Test type | Impact |
|---|---|
| **Unit tests** (`src/test/`) | The legacy bridge method (`createApplication(ApplicationCreateRequest)`) means existing tests continue to pass without modification. The same pattern applies to future migrations. |
| **Integration tests** (`src/integrationTest/`) | These test through the real HTTP layer and are unaffected — the API contract hasn't changed. |
| **New tests** | Each migrated use case should gain a **pure unit test** that mocks port interfaces only (no Spring context). This is optional but recommended. |

## Impact on Team Workflow

| Concern | Impact |
|---|---|
| **Learning curve** | The pattern is straightforward. The `CreateApplicationService` POC migration serves as a complete worked example. |
| **PR size** | Each use case migration is a self-contained PR. Expect 10–15 new files, most of which are small interfaces or thin adapters. |
| **Merge conflicts** | Low risk — new packages don't overlap with existing code. The only files modified are the controller and the service being migrated. |
| **Feature work** | Can continue in parallel. New features written against the old pattern still work. Migration is purely structural. |
| **CI/CD** | No impact. No new dependencies. No configuration changes. |

## Risk Assessment

| Risk | Likelihood | Mitigation |
|---|---|---|
| Adapter introduces subtle behaviour change | Low | All 565 existing tests (290 unit + 275 integration) serve as a safety net. The adapter is a thin pass-through. |
| Increased file count confuses developers | Medium | Clear package naming, consistent conventions, and the worked example reduce confusion. |
| Domain model drifts from entity | Low | The `DomainEntityMapper` is the single point of translation. Any field added to the entity must be added to the domain model and the mapper. |
| Migration stalls partway | Medium | Each use case is independently valuable. Even if only `CreateApplication` and `MakeDecision` are migrated, those two are the most complex services and benefit the most. |

