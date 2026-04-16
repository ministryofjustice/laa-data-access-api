# Hexagonal Architecture Migration — Gradual Migration Strategy

**Status:** Proposed delivery strategy from the `main` branch baseline, informed by the POC branch.

## Guiding Principles

1. **One use case per PR** — each migration is self-contained and independently reviewable.
2. **No behaviour change** — API contracts, response shapes, error messages, and status codes remain identical.
3. **Existing tests must pass** — a legacy bridge method on the service preserves the old API for tests.
4. **New code follows the pattern** — any new use case written after the pattern is established should follow it from the start.
5. **Feature work is not blocked** — migration PRs can be interleaved with feature work.

## Recommended Migration Order

### Phase 1: Proof of Concept ✅ Complete on POC Branch

**`CreateApplicationService`** — already migrated on the POC branch. This established:
- An interim package structure (`domain/`, `adapter/`)
- Shared domain models (`Application`, `Individual`)
- Shared infrastructure (`DomainEntityMapper`, `ApplicationPersistenceAdapter`)
- The `CreateApplicationCommandFactory` pattern for API → domain conversion
- The legacy bridge method pattern for backward-compatible test support

These points describe validated POC work, not changes already merged to `main`.

### Phase 2: Second Use Case — `MakeDecisionService`

**Why next:** It's the most complex remaining service (249 lines, 11 entity/repo imports, 6 entity types). Migrating it proves the pattern works for multi-entity orchestration — if this one works, everything else is simpler.

See the separate **MakeDecisionService Migration Worked Example** document for a detailed breakdown.

### Phase 3: `ApplicationService`

**Why next:** Largest remaining service (298 lines). Contains update, get, assign/unassign, and notes operations. Can be split into multiple use cases:
- `UpdateApplicationUseCase`
- `GetApplicationUseCase`
- `AssignCaseworkerUseCase` / `UnassignCaseworkerUseCase`
- `CreateApplicationNoteUseCase`

Each can be its own PR, migrated independently within the `ApplicationService`.

### Phase 4: Medium Services

`ApplicationSummaryService`, `DomainEventService`, `IndividualsService` — these are read-heavy services with fewer entity dependencies. By this phase, most domain models and persistence adapters already exist.

**Note on `DomainEventService`:** On the POC branch, `CreateApplicationService` calls `DomainEventService` *via* the `DomainEventPort` interface — so `CreateApplicationService` is already decoupled from the concrete class. However, `DomainEventService` itself still accepts JPA entity types and OpenAPI-generated request types in its own method signatures (e.g., `saveMakeDecisionDomainEvent(UUID, MakeDecisionRequest, UUID, DomainEventType)`). Its Phase 4 migration means rewriting those method signatures to accept domain types instead (e.g., `MakeDecisionCommand`), and updating the `DomainEventAdapter` to perform any remaining translation before delegating to `DomainEventService`. This is what the effort estimate of ~1 day in the effort estimation document covers. It is not automatically trivial — each event-publishing method signature must be updated, and the adapter must correctly map from domain types to the `DomainEventDetails` records used internally.

### Phase 5: Small Services

`CertificateService`, `CaseworkerService`, `ProceedingsService` — these are thin wrappers around single repositories. Migration is mechanical and quick.

## How to Run Migration Alongside Feature Work

```
Sprint N:
  ├── Feature ticket A (normal work)
  ├── Feature ticket B (normal work)
  └── Migration: MakeDecisionService (1 PR)

Sprint N+1:
  ├── Feature ticket C (normal work, follows hex pattern if touching migrated code)
  ├── Feature ticket D (normal work)
  └── Migration: ApplicationService — UpdateApplication (1 PR)

Sprint N+2:
  ├── Feature ticket E (written in hex pattern from the start)
  └── Migration: ApplicationService — remaining use cases (1–2 PRs)
```

## When to Stop

There's no requirement to migrate everything. The architecture supports a **mixed state** indefinitely — migrated use cases sit alongside unmigrated ones. Priorities:

| Priority | Services | Rationale |
|---|---|---|
| **High** | `CreateApplicationService` ✅, `MakeDecisionService` | Most complex, most likely to change, benefit most from testability |
| **Medium** | `ApplicationService` | Large and multi-purpose, but relatively stable |
| **Low** | Everything else | Small, simple, and unlikely to need the isolation benefits |

If the team only migrates the two high-priority services, the investment from the `main` baseline is ~3 days of work and covers the most complex business logic in the codebase.

## Conventions for New Code

Once the pattern is established, any **new use case** should follow it from the start:

1. Define a `UseCase` interface in `domain/port/inbound/`
2. Define a `Command` record in `domain/port/inbound/`
3. Implement the use case in `service/usecase/`, depending only on ports
4. Create a `CommandFactory` in `adapter/inbound/rest/`
5. Create persistence/event adapters as needed in `adapter/outbound/`
6. Wire the controller to the use case interface via the factory

This avoids creating technical debt that needs to be migrated later.

