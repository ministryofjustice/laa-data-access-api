# Ticket 07: Implement Hexagonal Changes for Update Application Flow

**Status:** Ticket draft  
**Baseline:** `main`  
**Validated against:** `CreateApplication` POC branch pattern  
**Last reviewed:** 2026-04-16

Assumption: scope is described relative to `main`. Use the established create-flow pattern unless update-specific requirements force a different shape.

## Jira Summary
Migrate update-application write path from service-centric implementation to hexagonal structure using use case interface, command factory, and outbound ports/adapters.

## Problem Statement
Update application logic is still tied to concrete service and infrastructure dependencies. This prevents a consistent architecture across write flows and makes unit isolation difficult.

## Scope
- Introduce `UpdateApplicationUseCase` interface.
- Introduce `UpdateApplicationCommand` and `UpdateApplicationCommandFactory`.
- Extract update-application orchestration from `ApplicationService` into dedicated use case implementation.
- Move API payload validation/parsing to factory/boundary layer.
- Introduce outbound ports required by update flow (application persistence and event publication at minimum).
- Add thin adapters over existing repository/services.
- Update `ApplicationController` wiring to factory + interface-driven use case call.

## Out of Scope
- Redesign of read/query endpoints.
- Full domain aggregate redesign beyond update-flow needs.
- Transaction strategy overhaul beyond update-flow consistency.

## Notes for Implementation
- Preserve existing update semantics including modified timestamp handling, validation behavior, and domain event publication.
- Keep adapters thin and behavior-preserving.

## Acceptance Criteria
- AC1: `ApplicationController.updateApplication(...)` depends on `UpdateApplicationUseCase` and uses `UpdateApplicationCommandFactory`.
- AC2: Update use case method accepts `UpdateApplicationCommand` rather than API DTO directly.
- AC3: API validation/parsing for update flow is handled before entering use case logic.
- AC4: Update use case implementation depends on outbound port interfaces, not concrete repository classes.
- AC5: Existing update-application tests pass with no API behavior regressions (status codes, response shape, error mapping).
- AC6: New unit tests cover `UpdateApplicationCommandFactory` valid/invalid payload paths and update use case orchestration paths.

## Risks
- Drift in update validation behavior during command mapping.
- Event publication behavior differences if ordering changes.

## Suggested Definition of Done
- Clear boundary exists: controller -> command factory -> update use case -> outbound ports -> adapters.
