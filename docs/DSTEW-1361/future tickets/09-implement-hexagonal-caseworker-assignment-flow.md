# Ticket 09: Implement Hexagonal Changes for Caseworker Assignment Flow

## Jira Summary
Migrate caseworker assignment/unassignment flow to hexagonal structure with dedicated use case interfaces, command factories, and outbound ports/adapters.

## Problem Statement
Caseworker assignment operations currently depend on concrete service/repository wiring and are not aligned with the target use case and ports/adapters approach.

## Scope
- Introduce `AssignCaseworkerUseCase` and `UnassignCaseworkerUseCase` interfaces (or a single caseworker-assignment use case if preferred by domain model).
- Introduce command objects and command factory/factories for assignment and unassignment requests.
- Extract assignment orchestration from `ApplicationService` into dedicated use case implementation(s).
- Move API validation and request normalization to boundary layer.
- Introduce outbound ports for application lookup, caseworker lookup, assignment persistence updates, and event publication.
- Implement thin adapters delegating to existing repositories/services.
- Update controller wiring to use interface + factory pattern.

## Out of Scope
- Broader caseworker query/search endpoint redesign.
- Full domain model replacement outside assignment flow.

## Notes for Implementation
- Preserve current validation and not-found behavior for caseworker/application IDs.
- Preserve existing event history integration behavior if present.

## Acceptance Criteria
- AC1: Caseworker assign/unassign endpoints in `ApplicationController` depend on dedicated use case interfaces.
- AC2: Assign/unassign use cases accept command objects produced by command factory/factories.
- AC3: Use case implementations depend on outbound ports, not concrete repository classes.
- AC4: Existing assignment/unassignment tests pass with no API contract changes.
- AC5: New unit tests cover command mapping, missing-resource validation paths, and successful assignment/unassignment orchestration.

## Risks
- Behavior drift around bulk assignment edge cases.
- Regression in event-history payload composition.

## Suggested Definition of Done
- Caseworker assignment flows match the same hexagonal migration pattern as other write paths.
