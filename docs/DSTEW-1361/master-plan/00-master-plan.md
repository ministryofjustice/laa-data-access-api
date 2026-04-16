# Hexagonal Architecture Migration Plan

**Status:** Agreed migration plan  
**Baseline:** `main`  
**Validated against:** POC branch `DSTEW-1361-Hexagonal-Create-Application-POC`  
**Last reviewed:** 2026-04-16

This document provides a consolidated, pragmatic roadmap for migrating the `laa-data-access-api` to a hexagonal architecture. It synthesizes the key design decisions from the exploratory documents in `archive/` into a single, actionable plan.

This document is the single source of truth for the agreed migration direction. Where other documents describe completed hexagonal work, read those statements as referring to the POC branch unless explicitly stated otherwise.

## 1. Motivation & Design Goals

### Current State
The codebase currently mixes API-layer concerns (HTTP handling, security, validation) and infrastructure concerns (JPA entities, repositories) directly within its service classes. This tight coupling makes the business logic difficult to test in isolation, hard to evolve, and increases the risk of architectural drift.

### Design Goals
- **Isolate Business Logic:** Decouple core use cases from both Spring Web and Spring Data JPA details.
- **Improve Testability:** Enable pure unit testing of use cases without requiring a Spring context.
- **Enforce Boundaries:** Use automated tools (ArchUnit) to ensure dependencies flow inward and prevent architectural regressions.
- **Increase Maintainability:** Make the codebase easier to understand and safer to change by establishing clear, conventional boundaries.

## 2. Target Architecture

The end-state architecture will be organized into explicit layers, enforced by package structure and ArchUnit rules.

### Target Package Layout
- `api.rest`: Controllers and other HTTP-specific components.
- `application.usecase`: Use case implementations (orchestration).
- `application.port.in`: Use case interfaces.
- `application.port.out`: Outbound interfaces for persistence, events, etc.
- `domain`: Core domain models and business rules, with no framework dependencies.
- `infra.persistence`: Adapters that implement outbound persistence ports using JPA.
- `infra.events`: Adapters for publishing domain events.
- `infra.security`: Security-related components.

This is the **end-state** package model. The POC branch uses an interim structure based around `domain/`, `adapter/`, and `service/usecase/` to validate the pattern before package realignment.

### Dependency Flow
Dependencies will be strictly enforced to flow "inward":
`api` -> `application` -> `domain`
`infra` -> `application`

The `domain` layer will have zero external dependencies.

## 3. Phased Migration Strategy

The migration will be executed in phases to minimize risk and disruption.

### Phase 0: POC Reconciliation (If Reusing Branch Work)
If the team intends to apply validated POC work onto `main`, start with a short reconciliation step to clean up, decompose, and align that work with the ticket plan before implementation begins.

This step is captured as `future tickets/00-prepare-poc-work-for-main-application.md`.

### Phase 1: Use Case Boundary Stabilization (Lightweight First)
The first step is to create a "seam" between the API layer and the application layer without touching the persistence layer.

1.  **Extract Use Case Interfaces:** For each write operation (e.g., Create Application, Make Decision), define a `...UseCase` interface in `application.port.in`.
2.  **Introduce Command Objects:** Define `...Command` records to act as the data transfer objects (DTOs) for the use case interfaces. This decouples the use cases from the OpenAPI-generated request models.
3.  **Create Command Factories:** Implement factories responsible for converting the API request into a `Command` object. This is where API-level validation and content parsing will now live.
4.  **Refactor Controller & Service:**
    *   The Controller will now depend on the `...UseCase` interface.
    *   The Controller's responsibility will be to call the factory and then execute the use case.
    *   Security annotations (`@AllowApiCaseworker`) will be moved to the Controller.
    *   The existing service class will be refactored to implement the new `...UseCase` interface. Its internals (repository calls, etc.) will remain unchanged for this phase.

### Phase 2: Outbound Port Adoption
Once the use case boundaries are stable, we will decouple the use cases from the infrastructure.

1.  **Define Outbound Ports:** For each external dependency a use case has (e.g., saving an application, publishing an event), define an interface in `application.port.out`.
2.  **Implement Infrastructure Adapters:** Create adapter classes in the `infra` packages that implement the outbound ports. These adapters will encapsulate the direct calls to JPA repositories, event services, etc.
3.  **Update Use Case:** The use case implementation will be changed to depend on the outbound port interfaces, not the concrete infrastructure components.

### Phase 3: Domain Model Introduction
With the application logic now fully isolated, we can introduce a pure domain model.

1.  **Define Domain Entities:** Create plain Java objects in the `domain` package that represent the core business concepts. These will have no JPA or Jackson annotations.
2.  **Map in Adapters:** The persistence adapters will become responsible for mapping between the domain entities and the JPA `@Entity` classes.
3.  **Refactor Use Cases:** The use cases will be updated to operate on the new domain models, making the core logic completely independent of any infrastructure concerns.

### Phase 4: Package Realignment & ArchUnit Enforcement
As the refactoring of each flow completes, we will physically move the classes into the target package structure and enforce the boundaries.

> **Package naming note:** Tickets 00–09 use the **interim package structure** established on the POC branch (`domain/port/inbound/`, `domain/port/outbound/`, `adapter/inbound/rest/`, `adapter/outbound/persistence/`). This is the naming validated by `DSTEW-1361-Hexagonal-Create-Application-POC` and reflected in all future ticket drafts. Phase 4 (Ticket 11) is the explicit step where those interim packages are renamed to the end-state names shown in the Target Architecture section above. Do not apply end-state naming in earlier tickets; doing so would conflict with the tested POC conventions and cause merge churn.

1.  **Move Classes:** Relocate the refactored classes into the packages defined in the "Target Architecture" section.
2.  **Introduce ArchUnit Tests:** Add tests to the build pipeline that enforce the dependency rules (e.g., `domain` cannot access `infra`, `application` cannot access `api`, etc.).

## 4. Governance and Quality

- **Incremental Changes:** All work will be done in small, focused PRs, ideally one use case flow at a time.
- **Testing:** Existing integration and functional tests will be preserved to prevent regressions. New unit tests will be added for use cases, factories, and adapters as they are created.
- **Documentation:** This master plan is the single source of truth. The `future tickets` folder provides detailed ticket drafts for each step of the migration, but they should be checked against the `main` baseline and any validated POC work before implementation.
