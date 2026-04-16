# Ticket 02: Standardise Controller Boundary and Security Placement

## Jira Summary
Apply a consistent controller boundary pattern across application endpoints: security on controllers, API validation/parsing before use case invocation, and interface-based use case dependencies.

## Problem Statement
After splitting create/make-decision paths, boundary responsibilities may still be inconsistent across endpoints. This creates drift in architecture and makes future migration harder.

## Scope
- Review `ApplicationController` endpoints and align to a standard pattern.
- Ensure controller dependencies are use case interfaces where available.
- Ensure `@AllowApiCaseworker` is controller-level for applicable endpoints.
- Ensure payload conversion/validation remains in factory/controller boundary layer (not use case internals).
- Document endpoint-by-endpoint mapping in ticket notes or PR description.

## Out of Scope
- Deep refactor of read/query services.
- Persistence adapter introduction.
- Domain model introduction.

## Notes for Implementation
- Keep behavior stable; this is placement and wiring work.
- Prefer small, endpoint-scoped commits to reduce regression risk.

## Acceptance Criteria
- AC1: All write endpoints in `ApplicationController` use interface-based dependencies where a use case abstraction exists.
- AC2: Security annotation placement is controller-level for migrated flows.
- AC3: API payload validation/parsing is not performed inside migrated use case implementations.
- AC4: Existing endpoint tests continue to pass with no response schema or status code regressions.

## Risks
- Inconsistent treatment of legacy endpoints.
- Accidental change to exception mapping path.

## Suggested Definition of Done
- Review checklist confirms each endpoint follows agreed pattern.
- No behavior changes observed in automated tests.
