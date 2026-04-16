# ADR-016: Adopt Hexagonal Architecture for the Use Case Layer

By David Stuart

**Status:** Pending  
**Impact:** MEDIUM  
**Driver:** David Stuart  
**Approver:**  
**Contributors:**  
**Informed:**  
**Date:** 2026-04-16  

---

## Context

The `laa-data-access-api` service layer mixes API concerns (HTTP handling, security enforcement, request validation), business logic, and infrastructure concerns (JPA repository calls, entity manipulation) directly within service classes. There is no enforced boundary between these responsibilities.

## Problem

Without an explicit architectural boundary:

- Business logic cannot be tested without a Spring context — every service test must wire up JPA, security, and Jackson.
- OpenAPI-generated DTOs flow from the controller directly into service methods, meaning the API shape dictates the internal model.
- Security annotations sit on service methods, coupling use cases to the HTTP security model.
- Controllers depend on concrete service classes rather than abstractions, making the wiring hard to evolve.
- Boundaries are unclear: it is hard to tell where API mapping ends and business logic begins, which increases the risk of architectural drift as the codebase grows.

## Decision

Adopt hexagonal architecture (ports & adapters) for the use case layer, migrated incrementally one use case at a time.

The migration introduces three explicit layers:

| Layer | Purpose |
|---|---|
| **Inbound adapters** | Convert API requests into domain command objects; enforce security at the HTTP boundary |
| **Domain / ports** | Use case interfaces, command records, outbound port interfaces, and pure domain models with no framework dependencies |
| **Outbound adapters** | Implement the outbound port interfaces using JPA, event publishers, or other infrastructure |

Dependencies flow strictly inward: adapters may depend on domain types, but the domain has no dependency on any adapter or framework class.

The migration is executed incrementally: each use case is migrated in a self-contained change with no API contract impact. A legacy bridge method is retained on each migrated service so that existing tests continue to pass without modification. The architecture supports an indefinite mixed state — migrated use cases sit alongside unmigrated ones without conflict.

Automated import rules (ArchUnit) are introduced as part of the package realignment phase to enforce dependency direction on every CI build and prevent regressions.

## Consequences

**Positive:**
- Business logic is testable with plain unit tests against port interfaces — no Spring context required.
- Clear separation between API concerns, domain logic, and persistence.
- Architectural boundaries enforced automatically in CI, preventing drift.
- New developers can understand layer boundaries from package structure alone.
- New use cases can be written against the pattern from the start, avoiding future migration debt.

**Negative:**
- More files per use case compared to the current flat service structure.
- Additional indirection between service and repository.
- Learning curve for developers unfamiliar with the pattern.

**Neutral:**
- No changes to API contracts, database schema, CI/CD pipelines, or infrastructure.
- Existing tests continue to pass via legacy bridge methods throughout the migration.
- Unmigrated services can coexist with migrated ones indefinitely.

## Alternatives considered

| Alternative | Why rejected |
|---|---|
| **Do nothing** | Coupling increases as the codebase grows. Testing remains slow and brittle. Boundaries become harder to establish the longer the change is deferred. |
| **Big-bang rewrite** | Too risky. Blocks feature work and has a high chance of stalling partway through. |
| **Clean Architecture (full layers)** | Over-engineered for this codebase size. Hexagonal architecture provides the same boundary guarantees with fewer moving parts. |
| **Just add interfaces to repositories** | Does not address the entity/DTO coupling or the missing domain model. Half-measures add complexity without delivering the full testability and separation benefits. |

## Out of scope

- Changes to API contracts, response shapes, error messages, or status codes.
- Database migrations.
- New build or runtime dependencies.
- CI/CD pipeline changes.
- Infrastructure changes.
- Read-only query services (these are lower priority and can be migrated if capacity allows, but are not required for the decision to be considered implemented).
