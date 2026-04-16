# Hexagonal Architecture Migration — Architecture Decision Record

**Status:** ADR supporting note  
**Baseline:** `main`  
**Validated against:** POC branch findings  
**Last reviewed:** 2026-04-16

## ADR-001: Adopt Hexagonal Architecture for Use Case Layer

**Status:** Proposed  
**Date:** 2026-04-15  
**Deciders:** Data Stewardship team  

### Context

The `laa-data-access-api` service layer has grown to 9 services totalling ~1,350 lines. Use cases directly depend on JPA repositories, entities, and OpenAPI-generated DTOs. This coupling makes the business logic difficult to test in isolation and tightly binds the domain to infrastructure choices.

### Decision

Adopt hexagonal architecture (ports & adapters) for the use case layer, migrated incrementally one use case at a time.

### Consequences

**Positive:**
- Business logic is testable without Spring context
- Clear separation between API concerns, domain logic, and persistence
- Enforced by import rules: `domain/` never imports from `entity/`, `repository/`, `adapter/`
- New developers can understand boundaries by looking at package structure

**Negative:**
- More files per use case (~12 new files for the first migration, ~8 for subsequent ones)
- Additional indirection layer between service and repository
- Learning curve for developers unfamiliar with the pattern

**Neutral:**
- No changes to API contracts, database schema, CI/CD, or infrastructure
- Existing tests continue to pass via legacy bridge methods
- Can coexist indefinitely with unmigrated services

### Alternatives Considered

| Alternative | Why rejected |
|---|---|
| **Do nothing** | Coupling increases as the codebase grows. Testing remains slow and brittle. |
| **Big-bang rewrite** | Too risky, blocks feature work, high chance of stalling. |
| **Clean Architecture (full layers)** | Over-engineered for this codebase size. Hexagonal is sufficient and simpler. |
| **Just add interfaces to repositories** | Doesn't address the entity/DTO coupling or the missing domain model. Half measures add complexity without the full benefit. |

### Compliance

The migration is validated by:
1. **ArchUnit tests** — once Ticket 11 lands, ArchUnit rules enforce forbidden import directions in CI on every build (e.g., `domain` cannot import from `infra`, `application` cannot import from `api`). Before Ticket 11, a lightweight stopgap is to grep for forbidden imports in `domain/` as part of PR review or a pre-commit hook — but this is not a substitute for automated enforcement.
2. **Existing test suite** — 565 tests (290 unit + 275 integration) must pass after each migration
3. **Code review** — each migration PR is reviewed against the conventions established by the `CreateApplicationService` hexagonal migration on branch `DSTEW-1361-Hexagonal-Create-Application-POC`

