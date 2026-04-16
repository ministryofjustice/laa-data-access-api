# Ticket 01: Split Make Decision into Use Case + Command Factory

**Baseline note:** This ticket is written against the `main` branch baseline, where no hexagonal changes are merged. The POC branch already contains partial `MakeDecision` work; if that branch is reused, treat this ticket as a reconciliation and uplift exercise rather than a net-new extraction.

## Jira Summary
Extract make-decision logic from `ApplicationService` into a dedicated use case abstraction and implementation, and introduce a command factory so API validation/parsing concerns are outside the use case.

## Problem Statement
`ApplicationService` currently combines multiple responsibilities, and make-decision flow is still coupled to API request types and controller wiring patterns. This limits testability and consistency with the create-application split approach.

## Scope
- Add `MakeDecisionUseCase` interface.
- Add `MakeDecisionCommand` record.
- Add `MakeDecisionCommandFactory` to convert API request to command.
- Extract make-decision behavior from `ApplicationService` into a dedicated implementation (for example `MakeDecisionUseCaseService`).
- Update `ApplicationController` to call factory + use case.
- Move `@AllowApiCaseworker` onto controller endpoint if still present on service method.

## Out of Scope
- Introducing persistence ports/adapters.
- Replacing JPA entities with domain models.
- Refactoring transaction strategy.

## Notes for Implementation
- Keep repository/entity usage unchanged in this ticket.
- Keep behavior and error semantics identical.
- Focus on shape and seam creation only.

## Acceptance Criteria
- AC1: `MakeDecisionUseCase` exists and is used by `ApplicationController` instead of concrete service dependency.
- AC2: `MakeDecisionCommand` is introduced and is the input to the extracted use case method.
- AC3: `MakeDecisionCommandFactory` handles API-to-command conversion, including validation/parsing now required before use case execution.
- AC4: Make-decision logic is removed from `ApplicationService` and moved into extracted use case implementation with no functional behavior change.
- AC5: Existing make-decision tests are updated and pass; new factory unit tests are added for valid and invalid payload flows.

## Risks
- Hidden behavior differences during extraction.
- Missed validation/parsing edge cases if factory mapping is incomplete.

## Suggested Definition of Done
- Unit tests and affected service/controller tests pass.
- No API contract changes.
- Code reviewers can trace a clear boundary: controller -> factory -> use case.
