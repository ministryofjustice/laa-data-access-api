# Ticket 04: Align Make Decision with Outbound Ports

## Jira Summary
Apply the same outbound-port pattern to make-decision flow, keeping adapters thin and behavior stable.

## Problem Statement
If create flow is port-based but make-decision is not, architecture becomes mixed and harder to maintain.

## Scope
- Introduce make-decision outbound ports (decision persistence, proceeding updates, event publication as needed).
- Add adapters over existing repositories/services.
- Refactor make-decision use case implementation to depend on ports.
- Reuse shared patterns established in create flow.

## Out of Scope
- Full domain object migration.
- Changes to endpoint contracts.

## Notes for Implementation
- Keep changes incremental and isolated to make-decision path.
- Ensure optimistic-locking and validation behavior remains unchanged.

## Acceptance Criteria
- AC1: Make-decision use case depends on outbound port interfaces, not concrete repositories.
- AC2: Adapter layer provides concrete implementations without changing business behavior.
- AC3: Existing make-decision tests pass unchanged in intent; only wiring/mocking updates are required.
- AC4: New unit tests cover make-decision adapter edge cases and failure propagation.

## Risks
- Regression in version-locking semantics.
- Hidden dependency on transaction order.

## Suggested Definition of Done
- Create and make-decision write flows both follow the same port/adapter pattern.
