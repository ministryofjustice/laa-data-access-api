# Ticket 06: Transaction Boundary and Cross-Cutting Hardening

## Jira Summary
Harden transaction and cross-cutting concern placement after use case and port/adaptor migration is in place.

## Problem Statement
During incremental migration, transactional and cross-cutting concerns may remain inconsistently placed. This can obscure architecture and increase operational risk.

## Scope
- Review and standardise `@Transactional` placement for migrated write flows.
- Confirm security, logging, and validation concerns are located at intended boundaries.
- Introduce supporting abstractions only if needed (for example transactional executor pattern).
- Verify event publication ordering and rollback behavior.

## Out of Scope
- New functional features.
- Non-migrated read/query architecture redesign.

## Notes for Implementation
- This is a hardening ticket, not a broad refactor.
- Prefer evidence-driven changes using existing integration tests.

## Acceptance Criteria
- AC1: Transaction boundaries for create and make-decision flows are explicitly documented and implemented consistently.
- AC2: Cross-cutting concerns (security, payload validation, logging) are not reintroduced into domain-oriented use case internals.
- AC3: Failure scenarios involving persistence and event publication are covered by tests and show expected rollback/consistency behavior.
- AC4: No endpoint contract changes and no regressions in existing smoke/integration suites.

## Risks
- Subtle transaction propagation issues.
- Event consistency edge cases under partial failures.

## Suggested Definition of Done
- Technical debt from incremental migration is reduced and architecture boundaries are stable for future enhancements.
