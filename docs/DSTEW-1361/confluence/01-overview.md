# Hexagonal Architecture Migration — Overview

**Status:** Summary note based on the `main` branch baseline and validated findings from the POC branch.

## What Is This?

A proposal to incrementally migrate the `laa-data-access-api` service layer from its current structure — where use cases depend directly on JPA repositories, entities, and OpenAPI-generated DTOs — to a **hexagonal (ports & adapters) architecture** where business logic is isolated behind well-defined interfaces.

## Why Are We Doing This?

| Problem today | How hexagonal helps |
|---|---|
| **Use cases directly import JPA entities and Spring Data repositories** — business logic is tightly coupled to the database layer | Business logic depends on **port interfaces** that the team defines. The JPA implementation becomes a swappable adapter. |
| **OpenAPI-generated DTOs flow from controller straight into the service** — the API shape dictates the internal model | The use case defines its own **command/query objects**. The controller maps API DTOs into these before calling the domain. |
| **Unit tests require a Spring context** — every service test boots `@SpringBootTest` with mocked repositories | Use cases can be tested with plain Mockito against port interfaces. No Spring context, faster feedback. |
| **Security annotations sit on the service** — `@AllowApiCaseworker` couples the use case to the HTTP security model | Security is enforced at the **controller (driving adapter)** level. The domain has no knowledge of authentication mechanisms. |
| **Unclear boundaries** — hard to tell where API mapping ends and business logic begins | Explicit layers with clear import rules: the domain never imports from infrastructure packages. |

## What Has Already Been Done?

On the **POC branch**, `CreateApplicationService` has been migrated as a proof of concept. This established:

- An interim package structure (`domain/`, `adapter/`)
- Conventions for domain models, port interfaces, command objects, and adapters
- A working pattern that all existing tests (290 unit, 275 integration) pass against as of 2026-04-16 on the POC branch
- A reusable `DomainEntityMapper` and persistence adapter that other use cases can share

The final agreed package layout remains the end-state structure described in the master plan.

## What Does the Target Look Like?

```
Controller (driving adapter)
  │  maps API DTO → Command via CommandFactory
  │  enforces security (@AllowApiCaseworker)
  ▼
UseCase port interface (e.g. CreateApplicationUseCase)
  │
  ▼
Service implementation (e.g. CreateApplicationService)
  │  works with domain Application, not ApplicationEntity
  │  calls port interfaces, not repositories
  ├──→ ApplicationPersistencePort ──→ Adapter ──→ JPA Repository
  ├──→ ProceedingsPersistencePort ──→ Adapter ──→ JPA Repository
  └──→ DomainEventPort            ──→ Adapter ──→ DomainEventService
```

## Scope

This proposal covers a **gradual, use-case-by-use-case migration**. It does not propose a big-bang rewrite. Each use case can be migrated independently as a normal ticket alongside feature work.

