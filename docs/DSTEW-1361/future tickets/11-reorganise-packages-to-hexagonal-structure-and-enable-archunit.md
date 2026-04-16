# Ticket 11: Reorganise Packages to Hexagonal Structure and Enable ArchUnit

**Status:** Ticket draft  
**Baseline:** `main`  
**Validated against:** Target architecture design and POC branch patterns  
**Last reviewed:** 2026-04-16

Assumption: scope is described relative to `main`. This ticket should follow successful use-case and port migration slices rather than precede them.

## Jira Summary
After initial use-case and port/adaptor refactors are stable, reorganise package/folder structure to a conventional hexagonal layout and introduce ArchUnit rules to enforce architecture boundaries.

## Problem Statement
Current package layout evolved incrementally and does not yet make layer boundaries explicit. This increases risk of architectural drift and makes rule-based enforcement difficult.

## Scope
- Define and implement target package structure for migrated flows.
- Move classes in small, behavior-preserving slices to align with hexagonal layering.
- Introduce ArchUnit test suite to enforce dependency direction and package boundaries.
- Add initial baseline rules focused on migrated write flows (create, make-decision, update, notes, caseworker where available).
- Integrate ArchUnit tests into standard CI test execution.

## Out of Scope
- Large-scale business logic rewrites.
- Replacing existing test suites with ArchUnit (ArchUnit is additive).
- Full read-side architecture redesign unless directly required by package moves.

## Proposed Target Layout (high level)
- `api.rest` for HTTP adapters/controllers.
- `application.usecase` for use case orchestration.
- `application.port.in` for inbound use case interfaces.
- `application.port.out` for outbound port interfaces.
- `domain` for domain model/value objects/domain services.
- `infra.persistence`, `infra.events`, `infra.security` for adapters/framework concerns.

## Notes for Implementation
- Execute package moves flow-by-flow to limit merge conflicts.
- Add ArchUnit rules as each slice lands so boundaries are protected immediately.
- Keep endpoint behavior and contracts unchanged.

## Acceptance Criteria
- AC1: Agreed target package structure is implemented for migrated write flows.
- AC2: Controllers/adapters sit in API/infra packages and do not host use case business logic.
- AC3: Use case classes depend only on domain + port abstractions, not concrete infra implementations.
- AC4: ArchUnit tests are added and run in CI.
- AC5: ArchUnit rules enforce at minimum: (a) domain does not depend on infra/api, (b) application.usecase does not depend on infra concrete classes, (c) ports are interfaces and are not implemented in application layer.
- AC6: Existing functional tests pass with no API contract changes.

## Risks
- High churn from package moves if done in too-large PRs.
- Temporary rule exceptions needed while migration is in progress.

## Suggested Definition of Done
- New package structure is visible and consistent for migrated write flows.
- ArchUnit guards are active and prevent boundary regressions.
