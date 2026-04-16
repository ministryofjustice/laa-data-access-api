# Ticket 08: Implement Hexagonal Changes for Application Notes Flow

**Status:** Ticket draft  
**Baseline:** `main`  
**Validated against:** `CreateApplication` POC branch pattern  
**Last reviewed:** 2026-04-16

Assumption: scope is described relative to `main`. Reuse the controller -> factory -> use case pattern proven on the POC branch.

## Jira Summary
Migrate application-notes write path to hexagonal structure with dedicated use case abstraction, command model, and outbound ports/adapters.

## Problem Statement
Application notes behavior is currently implemented inside broader service logic and tightly coupled to infrastructure classes, reducing consistency with migrated flows.

## Scope
- Introduce `CreateApplicationNoteUseCase` interface (and additional note use cases if needed by endpoint shape).
- Introduce command object(s) for notes operations and command factory/factories.
- Extract note creation orchestration from `ApplicationService` into dedicated use case implementation.
- Move API validation and request shaping to boundary layer.
- Introduce outbound ports for note persistence and related application lookup/event operations as required.
- Implement thin adapters over existing repositories/services.
- Update controller wiring to use factory + use case interface.

## Out of Scope
- Notes read endpoint redesign unless required by write flow coupling.
- Broad refactor of unrelated application endpoints.

## Notes for Implementation
- Preserve current audit/event behavior and validation messages.
- Keep transaction boundaries equivalent to existing behavior unless explicitly changed.

## Acceptance Criteria
- AC1: Notes write endpoint in `ApplicationController` calls a note use case interface and no longer orchestrates note business logic directly.
- AC2: Notes use case input is command-based (not direct API DTO).
- AC3: Notes use case depends on outbound ports instead of concrete note/application repositories.
- AC4: Existing notes-related tests pass with unchanged API contract and error behavior.
- AC5: New tests verify command factory mapping and use case behavior for success, validation failures, and missing-application scenarios.

## Risks
- Subtle differences in note timestamping/audit behavior.
- Regression in exception handling for missing application IDs.

## Suggested Definition of Done
- Notes write flow follows same architectural pattern as create/make-decision/update migrations.
