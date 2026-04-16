# Folder Structure and ArchUnit Design

## Purpose
This document defines a pragmatic target folder structure for hexagonal architecture and explains how ArchUnit should be introduced to enforce boundaries as migration progresses.

It is intended to be implementation-guiding documentation, not a ticket.

## Design Goals
- Make architectural boundaries obvious from package names alone.
- Keep dependency direction explicit and enforceable.
- Support incremental migration without large disruptive moves.
- Enable ArchUnit to act as an automated guardrail against regressions.
- Preserve current API behavior while structure evolves.

## Architectural Principles
- Business logic should depend on abstractions and domain concepts, not framework details.
- Inbound adapters call use cases through inbound ports.
- Use cases interact with infrastructure through outbound ports.
- Concrete infrastructure implementations live outside core application and domain layers.
- Cross-cutting concerns should be placed deliberately and consistently.

## Recommended Target Package Layout

The following layout is recommended for migrated write flows:

- api.rest
  - Responsibility: HTTP controllers and request/response adapter concerns.
  - Contains: endpoint classes, request boundary orchestration, HTTP-specific mapping.

- application.usecase
  - Responsibility: use case orchestration.
  - Contains: use case implementations and command handlers.

- application.port.in
  - Responsibility: inbound contracts that define what application capabilities are exposed.
  - Contains: use case interfaces and related input contracts where appropriate.

- application.port.out
  - Responsibility: outbound contracts required by use cases.
  - Contains: persistence, event, external-service abstractions.

- domain
  - Responsibility: domain models, value objects, and domain services.
  - Contains: business concepts independent of framework and delivery mechanisms.

- infra.persistence
  - Responsibility: persistence adapters and mapping to/from storage models.
  - Contains: repository-backed adapter implementations, persistence mappers.

- infra.events
  - Responsibility: event publishing adapters and event integration concerns.
  - Contains: outbound event adapter implementations.

- infra.security
  - Responsibility: framework-level security integration.
  - Contains: access-control integration and security helpers.

## Responsibility Boundaries

- api.rest can depend on application.port.in and boundary mapping components.
- application.usecase can depend on domain, application.port.in, and application.port.out.
- application.port.in and application.port.out should remain stable contracts.
- infra packages can depend on framework, repositories, and storage models.
- domain should not depend on api.rest or infra packages.

## Migration Strategy

### Phase 1: Boundary Stabilization
- Ensure controller-to-usecase boundaries are interface-driven.
- Move request validation/parsing to boundary factories where relevant.
- Keep repository usage as-is while boundaries stabilize.

### Phase 2: Outbound Port Adoption
- Introduce outbound ports for migrated write flows.
- Move concrete repository/event dependencies behind adapters.
- Keep behavior and endpoint contracts unchanged.

### Phase 3: Domain-Centric Refinement
- Gradually replace infrastructure-centric data handling in use cases with domain-centric models.
- Keep entity mapping in adapter/infrastructure side.

### Phase 4: Package Realignment
- Move classes into the target package layout in small, flow-scoped increments.
- Land ArchUnit rules with each migration slice to prevent backsliding.

### Phase 5: Hardening
- Standardize transaction and cross-cutting concern placement.
- Remove temporary exceptions used during migration.

## ArchUnit Introduction Plan

## What ArchUnit Is and How It Works

ArchUnit is a Java test library used to enforce architecture rules through automated tests.

At a practical level, it works by:

- Scanning compiled classes from the codebase.
- Building a dependency model between packages, classes, and annotations.
- Evaluating those dependencies against rules defined by the team.
- Failing tests when a rule is violated.

This means architecture constraints are checked in the same way as other automated tests. Instead of relying only on code review memory, boundary rules become executable and repeatable in CI.

For this migration, ArchUnit is not replacing behavior tests. It complements them by guarding package boundaries and dependency direction while functional tests continue to validate runtime behavior.

### Objective
Use ArchUnit to enforce intended dependency direction and package-level boundaries automatically in CI.

### Rule Rollout Approach
- Start with a minimal, high-signal rule set.
- Apply rules first to migrated flows only if needed, then expand scope.
- Add temporary, time-boxed exceptions only when unavoidable.
- Tighten rule scope as migration progresses.

### Initial Rule Themes
- Domain independence:
  - Domain layer must not depend on infrastructure or API layers.

- Use case isolation:
  - Application use case layer must not depend on concrete infrastructure implementations.

- Port integrity:
  - Outbound and inbound ports are contracts and should remain stable and technology-agnostic.

- Adapter placement:
  - Controller classes must live in API adapter package.
  - Infrastructure implementations must live under infra packages.

- Dependency direction:
  - Dependencies should point inward from adapters to ports/use cases, not the reverse.

### Rule Maturity Stages
- Stage A: Informational mode for new rules during first adoption.
- Stage B: Blocking mode for agreed stable boundaries.
- Stage C: Full blocking mode with narrow and explicit exceptions only.

## Testing and Quality Considerations
- Keep existing functional and integration tests as regression safety net.
- Add targeted unit tests around command factories and use case orchestration.
- Treat ArchUnit as complementary to behavior tests, not a replacement.
- Ensure CI includes ArchUnit execution in the default test pipeline.

## Governance and Change Control
- Use small PRs focused on one flow or one boundary concern.
- Require architecture review for package moves crossing layer boundaries.
- Maintain an explicit checklist per ticket: boundary placement, dependency direction, test coverage, and no API contract drift.
- Track and regularly prune temporary architecture exceptions.

## Risks and Mitigations

- Risk: Large-scale package moves create merge conflicts.
  - Mitigation: Flow-by-flow migration and short-lived branches.

- Risk: Overly strict rules block incremental progress.
  - Mitigation: Stage rules by maturity and apply scoped enforcement first.

- Risk: Hidden behavior drift while refactoring boundaries.
  - Mitigation: Preserve tests, compare endpoint behavior, and separate structural changes from logic changes.

- Risk: Inconsistent naming conventions across teams.
  - Mitigation: Define naming conventions once and enforce during review.

## Definition of Success
- Migrated write flows are organized by clear hexagonal boundaries.
- Dependencies follow intended inward direction and are automatically enforced.
- ArchUnit runs reliably in CI and catches boundary regressions early.
- Team can add new use cases with predictable placement and low architectural ambiguity.
