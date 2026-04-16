# Ticket Sequencing and Dependencies (01-09)

**Status:** Sequencing proposal written against the `main` branch baseline. If validated POC work is reused, re-check ticket order and scope before implementation.

## Purpose
This planning note defines a recommended implementation sequence for the DSTEW-1361 future tickets and highlights hard dependencies, soft dependencies, and opportunities to run work in parallel.

## Recommended Delivery Order

1. Ticket 01: Split Make Decision into Use Case + Command Factory
2. Ticket 02: Standardise Controller Boundary and Security Placement
3. Ticket 07: Implement Hexagonal Changes for Update Application Flow
4. Ticket 08: Implement Hexagonal Changes for Application Notes Flow
5. Ticket 09: Implement Hexagonal Changes for Caseworker Assignment Flow
6. Ticket 03: Introduce First Outbound Ports for Create Flow
7. Ticket 04: Align Make Decision with Outbound Ports
8. Ticket 05: Introduce Domain Model for Application Aggregate
9. Ticket 06: Transaction Boundary and Cross-Cutting Hardening

## Dependency Matrix

| Ticket | Depends On | Dependency Type | Reason |
|---|---|---|---|
| 01 | 04-use-case-interface-with-command-factory baseline implementation | Hard | Establishes create-flow pattern and naming conventions that make-decision should mirror |
| 02 | 01 | Soft | Boundary standardization is easier after both create and make-decision use case splits are in place |
| 07 | 02 | Soft | Update flow can be migrated earlier, but controller boundary conventions reduce rework |
| 08 | 02 | Soft | Same as update flow: preferred to align boundary conventions first |
| 09 | 02 | Soft | Caseworker flow migration benefits from consistent controller/use case boundary patterns |
| 03 | 04-use-case-interface-with-command-factory baseline implementation | Hard | Create outbound ports should be applied to already-extracted create use case |
| 04 | 01, 03 | Hard | Make-decision port migration should follow make-decision split and reuse create port/adaptor pattern |
| 05 | 03, 04 | Hard | Domain model introduction is safer after both major write flows already depend on outbound ports |
| 06 | 03, 04, 05 | Hard | Hardening transaction/cross-cutting placement requires stable architectural boundaries |

## Parallelization Guidance

### Wave A (can run largely in parallel after Ticket 02)
- Ticket 07 (update flow)
- Ticket 08 (notes flow)
- Ticket 09 (caseworker flow)

Rationale:
- All three are endpoint-scoped write-flow migrations.
- They share patterns but touch mostly different orchestration paths.
- Coordinate on naming conventions for command records and factory classes to avoid divergence.

### Wave B (mostly sequential)
- Ticket 03 then Ticket 04

Rationale:
- Ticket 04 should reuse abstractions and adapter style validated in Ticket 03.

### Wave C (stabilization)
- Ticket 05 then Ticket 06

Rationale:
- Domain model work should land before transaction/cross-cutting hardening so hardening is done once against the target architecture shape.

## Suggested Milestone Plan

### Milestone 1: Use Case Boundary Consistency
Includes: 01, 02
Outcome:
- Create and make-decision both command-driven at the controller boundary.
- Security and validation placement is standardized.

### Milestone 2: Remaining Write-Flow Use Case Migration
Includes: 07, 08, 09
Outcome:
- Update, notes, and caseworker flows use the same controller -> factory -> use case pattern.

### Milestone 3: Outbound Port Adoption
Includes: 03, 04
Outcome:
- Create and make-decision no longer depend directly on concrete repository/service classes in use case logic.

### Milestone 4: Domain and Hardening
Includes: 05, 06
Outcome:
- Domain model starts to become the primary use case language.
- Transaction and cross-cutting placement is intentionally hardened.

## Blockers and Decision Points

These decisions should be resolved before the corresponding tickets are treated as implementation-ready on `main`.

1. Naming convention lock-in
- Decide once and apply everywhere:
  - `XxxUseCase` interface names
  - `XxxCommand` record names
  - `XxxCommandFactory` class names
  - `XxxUseCaseService` implementation names

2. Event publication boundary
- Confirm whether event publication remains in use case orchestration during port adoption or is delegated fully behind event ports.

3. Transaction ownership
- Confirm target stance before Ticket 06:
  - Keep `@Transactional` on use case methods, or
  - Introduce a transactional executor/decorator pattern.

4. Domain model depth for Ticket 05
- Decide whether Ticket 05 introduces only minimal domain types needed for migrated write flows, or a broader aggregate representation.

## Delivery Risks to Track

- Regression risk from large extraction PRs.
- Inconsistent exception mapping when moving validation/parsing logic out of services.
- Divergent conventions if Wave A tickets are worked in parallel without a short architecture checklist.

## Lightweight Governance Checklist for Every Ticket (01-09)

- Uses interface-based use case boundary in controller.
- Uses command object input for write-path use case.
- Places API payload validation/parsing before use case boundary.
- Avoids direct concrete repository dependency in use case once port-migration tickets are in scope.
- Keeps API contract unchanged unless explicitly agreed.
- Includes tests for success path and at least one failure path.
