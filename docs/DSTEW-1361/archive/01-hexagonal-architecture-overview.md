# Hexagonal Architecture Overview for `laa-data-access-api`

> **Archived POC design note:** This document captures the proof-of-concept design used during branch exploration. It does **not** describe the `main` branch as merged today, and its package examples are an interim POC structure rather than the final end-state package naming in the master plan.

## What is Hexagonal Architecture?

Hexagonal architecture (also called Ports & Adapters) organises code into three concentric layers:

| Layer | Contains | Depends on |
|---|---|---|
| **Domain** (centre) | Business logic, domain models, use case orchestration | Nothing external |
| **Ports** (boundary) | Interfaces that define how the domain talks to / is talked to by the outside world | Domain types only |
| **Adapters** (outside) | Implementations that plug into ports — controllers, JPA repositories, event publishers, etc. | Ports + frameworks |

The fundamental rule: **dependencies point inward**. The domain never imports from an adapter; adapters import from the domain.

```
┌─────────────────────────────────────────────────────────────┐
│                        Adapters                             │
│                                                             │
│  ┌──────────────┐                    ┌───────────────────┐  │
│  │  Controller   │   ── drives ──►   │  Driving Port     │  │
│  │  (REST API)   │                   │  (use case iface) │  │
│  └──────────────┘                    └───────┬───────────┘  │
│                                              │              │
│                                  ┌───────────▼──────────┐   │
│                                  │      Domain          │   │
│                                  │  ┌────────────────┐  │   │
│                                  │  │  Use Case impl │  │   │
│                                  │  │  Domain models  │  │   │
│                                  │  │  Domain svc     │  │   │
│                                  │  └────────────────┘  │   │
│                                  └───────────┬──────────┘   │
│                                              │              │
│  ┌──────────────┐                    ┌───────▼───────────┐  │
│  │  JPA Repo    │   ◄── driven ──    │  Driven Port      │  │
│  │  Event Store │                    │  (persistence     │  │
│  │  etc.        │                    │   / event iface)  │  │
│  └──────────────┘                    └───────────────────┘  │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## Why Change?

The current codebase has a `usecase/` package, signalling an intent towards use-case-driven design, but the actual dependency flow still runs **use case → infrastructure**:

- Use cases directly depend on `JpaRepository` interfaces, JPA `@Entity` classes, and OpenAPI-generated DTOs.
- There are no port interfaces — controllers reference concrete service classes, and services reference concrete repositories.
- Cross-cutting concerns (security annotations, `@Transactional`) sit on the use case instead of the adapter layer.

This means:

1. **Business logic cannot be tested without Spring context** — every test must wire up JPA, security, and Jackson.
2. **Framework lock-in** — switching a persistence mechanism or adding an alternative driving adapter (e.g. an event consumer) requires changing the domain.
3. **Unclear boundaries** — it's hard to tell where API mapping ends and business logic begins.

---

## Where the Current Code Deviates

| Concern | Current State | Hexagonal Ideal |
|---|---|---|
| Repository access | Use case depends directly on `ApplicationRepository` (Spring Data `JpaRepository`) | Use case depends on a domain-defined `ApplicationPersistencePort` interface |
| Domain model | JPA `@Entity` (`ApplicationEntity`) used inside the use case | Plain domain objects; entity ↔ domain mapping lives in the persistence adapter |
| Input model | OpenAPI/Swagger-annotated `ApplicationCreateRequest` passed straight from controller to use case | Use-case-defined command object; controller maps API DTO → command |
| Security | `@AllowApiCaseworker` annotation on the use case method | Handled in the driving adapter (controller) |
| Transactions | `@Transactional` on the use case method | Managed in infrastructure / adapter layer |
| Use case contract | Controller depends on concrete `CreateApplicationService` class | Controller depends on a `CreateApplicationUseCase` port interface |
| Event publishing | Use case calls concrete `DomainEventService` | Use case calls a `DomainEventPort` interface |

---

## Scope of This Ticket

This ticket migrates **one use case** — `CreateApplicationService` — to a hexagonal pattern as a proof of concept. This establishes:

- The package structure and conventions for domain / port / adapter layers.
- A worked example that the team can follow for subsequent use cases.
- Confidence that the approach works within the existing Spring Boot + Gradle multi-module setup.

Future tickets will migrate `MakeDecisionService` and the remaining services in the same pattern.

At the time of writing, this reflected planned or validated POC work rather than changes merged to `main`.

---

## Target Package Structure

```
uk.gov.justice.laa.dstew.access
├── domain/
│   ├── model/
│   │   └── Application.java              # Plain domain object (no JPA/Swagger)
│   ├── service/
│   │   └── ApplicationContentParser.java  # Pure domain logic (extracted)
│   └── port/
│       ├── inbound/
│       │   └── CreateApplicationUseCase.java   # Driving port interface
│       └── outbound/
│           ├── ApplicationPersistencePort.java  # Driven port interface
│           ├── ProceedingsPersistencePort.java  # Driven port interface
│           └── DomainEventPort.java             # Driven port interface
├── adapter/
│   ├── inbound/
│   │   └── rest/
│   │       └── ApplicationController.java      # Driving adapter (existing, updated)
│   └── outbound/
│       ├── persistence/
│       │   ├── ApplicationPersistenceAdapter.java
│       │   └── ProceedingsPersistenceAdapter.java
│       └── event/
│           └── DomainEventAdapter.java
├── service/
│   └── usecase/
│       └── CreateApplicationService.java  # Implements CreateApplicationUseCase
└── (existing packages: entity/, repository/, mapper/, etc. — unchanged, used by adapters only)
```

> **Note:** The existing packages (`entity/`, `repository/`, `mapper/`, `controller/`) remain in place. Adapters wrap them. We are not restructuring the entire project — just introducing the domain layer and wiring the use case through ports.

> **Status note:** The master plan now describes a different end-state package naming model (`api`, `application`, `infra`). The structure shown here is the interim POC layout used to validate the approach.

---

## Guiding Principles

1. **Incremental migration** — Only `CreateApplicationService` moves in this ticket. Other services continue working as-is.
2. **No behaviour change** — All existing API contracts, responses, and error handling remain identical.
3. **Existing tests must pass** — The existing `CreateApplicationTest` is updated to mock port interfaces instead of repositories, but all assertions remain equivalent.
4. **Pragmatic `@Transactional`** — For now, `@Transactional` stays on the use case. Extracting transaction management to an adapter is a future refinement.

