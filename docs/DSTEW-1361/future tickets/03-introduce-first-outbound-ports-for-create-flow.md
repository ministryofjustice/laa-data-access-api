# Ticket 03: Introduce First Outbound Ports for Create Flow

**Status:** Ticket draft  
**Baseline:** `main`  
**Validated against:** `CreateApplication` POC branch work  
**Last reviewed:** 2026-04-16

Assumption: scope is described relative to `main`. If the POC branch is reused, focus this ticket on reconciling and hardening the create-flow port pattern.

## Jira Summary
Introduce outbound ports for the create-application path only, with thin adapters over existing repository/services, while preserving behavior.

## Problem Statement
Use case seams exist, but create flow still depends directly on infrastructure details. Without ports, inward dependency direction is not yet established.

## Scope
- Define create-flow outbound ports (persistence, proceedings persistence, domain event publication).
- Add thin outbound adapters that delegate to current repositories/services.
- Update create use case implementation to depend on ports only.
- Keep mapper and entity usage inside adapters as much as practical in this stage.

## Out of Scope
- Full domain model replacement.
- Refactor of unrelated endpoints.
- Transaction strategy overhaul.

## Notes for Implementation
- Keep adapter implementations intentionally thin.
- Avoid broad package moves in this ticket.

## Acceptance Criteria
- AC1: Create use case implementation has no direct dependency on `ApplicationRepository`, `ProceedingRepository`, or `DomainEventService` concrete infrastructure classes.
- AC2: Outbound ports are introduced and used by create use case through constructor injection.
- AC3: Adapters delegate to existing infrastructure components with equivalent behavior.
- AC4: Existing create tests pass; additional unit tests verify adapter behavior and mapping assumptions.

## Risks
- Mapping drift between use case inputs and persistence/event layers.
- Over-coupling ports to current infrastructure types.

## Suggested Definition of Done
- Dependency direction for create flow is interface-first (use case -> port, adapter -> infrastructure).
- No API behavior change.
