# Ticket 05: Introduce Domain Model for Application Aggregate

## Jira Summary
Introduce a first-cut domain model for the application aggregate used by migrated write use cases, and confine JPA entities to adapters.

## Problem Statement
Even with ports/adapters, use cases still operating directly on JPA entities remain tightly coupled to infrastructure and harder to evolve.

## Scope
- Add domain model classes required by migrated create/make-decision flows.
- Add mapping between domain objects and persistence entities inside adapter layer.
- Update use case logic to operate on domain objects where practical.
- Keep API DTO mapping at controller/factory boundary.

## Out of Scope
- Full model redesign across all read/query paths.
- Replacing all existing mappers in one ticket.

## Notes for Implementation
- Start with minimal domain types required by migrated flows.
- Preserve existing business rules and validation outcomes.

## Acceptance Criteria
- AC1: Migrated use cases no longer mutate JPA entities directly in business logic.
- AC2: Domain-to-entity mapping is handled in adapter/mapping components, not in controllers or use case orchestration methods.
- AC3: Create and make-decision flows retain existing behavior under current tests.
- AC4: New unit tests cover domain-level behavior independently from Spring/JPA wiring.

## Risks
- Mapping complexity increasing test maintenance.
- Partial domain introduction causing temporary duplication.

## Suggested Definition of Done
- Domain model is the primary language of migrated use case logic.
- JPA classes are implementation details behind adapters.
