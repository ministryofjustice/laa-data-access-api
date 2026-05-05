# ADR-XXX: Adopt Clean Architecture for laa-data-access-api

**Status:** Awaiting Approval  
**Date:** 2026-04-21  
**Deciders:** laa-data-access-api engineering team

---

## Context and Problem Statement

The `laa-data-access-api` service exposes an API generated from an OpenAPI specification and
persists data via Spring Data JPA. As the service has grown, two structural problems have emerged:

### 1. API versioning and contract isolation

The API contract (OpenAPI spec) changes independently of the business rules that govern legal aid
applications. In the existing codebase, generated API model types (e.g. `ApplicationCreateRequest`)
flow directly into service classes, mapper classes, and in some cases repository interactions.
This means that a change to a field name or type in the OpenAPI spec can silently propagate
into business logic, requiring changes in multiple unrelated classes and increasing the risk of
regressions.

There is no stable internal representation that absorbs API changes at a single boundary. The
absence of such a boundary makes it impossible to version the API independently of the business
logic.

### 2. Pluggable infrastructure

The service currently couples business logic directly to Spring Data JPA repositories and, in
some areas, to the `DomainEventService` and `ProceedingsService` implementations. This coupling
makes it difficult to:

- Replace the persistence mechanism (e.g. move from JPA to jOOQ, an event-sourced store, or a
  different database) without touching business logic.
- Swap the policy decision mechanism (e.g. move from local role checks to an external OPA or
  Keycloak authorization server) without touching use-case code.
- Test business logic in isolation without starting a Spring context or an in-memory database.

The question this ADR addresses is: **what structural pattern should govern how the codebase is
organised so that the API contract, the business logic, and the infrastructure are independently
changeable?**

---

## Decision Drivers

- API model types must not appear in business logic classes.
- Business logic must be testable with plain JUnit and Mockito — no `@SpringBootTest`, no
  embedded database.
- The persistence mechanism must be replaceable without changing business logic.
- The authorization mechanism must be replaceable without changing business logic.
- The pattern must be enforceable at build time (CI), not left to code review.
- The pattern must provide a clear, repeatable template for new endpoints.

---

## Options Considered

### Option A: Clean Architecture (selected)

Organise the codebase into concentric layers enforced by the Dependency Rule: source code
dependencies may only point inward. The layers are:

| Layer | Package | Contents |
|-------|---------|----------|
| Domain | `domain/` | Plain Java records and enums. No Spring, no JPA, no API model imports. |
| Use Case | `usecase/` | Business logic classes. Depends only on domain types and gateway interfaces. |
| Interface Adapter | `controller/`, `usecase/*/infrastructure/` (interfaces) | Translates between the outside world and use cases. |
| Infrastructure | `infrastructure/`, `repository/`, `entity/` | JPA entities, Spring Data repositories, gateway implementations. |
| Config | `config/` | Spring `@Configuration` classes that wire all layers together. |

API model types are translated to plain Command or Query records at the controller boundary by a
dedicated command mapper. Gateway interfaces, owned by the use case, define the persistence
contract. JPA implementations satisfy those interfaces without the use case ever importing them.

ArchUnit rules (`CleanArchitectureTest`) assert the layer boundaries at CI time, turning
accidental cross-layer imports into build failures.

### Option B: Layered Architecture (traditional three-tier)

Organise into `controller` → `service` → `repository` layers. Services hold business logic and
call repositories directly. API model types are passed into service methods or translated by
ad-hoc mapper classes.

### Option C: Hexagonal Architecture (Ports and Adapters) — explicit variant

Define explicit `Port` interfaces in a central `port/` package (both inbound and outbound).
Adapters implement the ports. The application core depends only on port interfaces.

Note that the "difference" between Clean Architecture and Hexagonal Architecture is largely one of emphasis and naming conventions. Both patterns enforce a separation of concerns and the Dependency Rule, but Hexagonal Architecture explicitly names the interfaces as "ports" and the implementations as "adapters". This option would require more upfront renaming of existing classes to fit the port/adapter terminology and result in a structure less intuitive to developers familiar with traditional layered patterns.

### Option D: Vertical Slice Architecture

Organise by feature rather than by layer. Each feature (e.g. `createapplication/`) contains its
own controller, service, repository, and model classes. No shared layer boundaries are enforced
across features.

---

## Comparison of Options

| Concern | Option A: Clean Architecture | Option B: Layered (three-tier) | Option C: Hexagonal | Option D: Vertical Slice |
|---------|-----------------------------|---------------------------------|---------------------|--------------------------|
| API contract isolation | Strong — API models touch only the command mapper at the use-case boundary | Weak — API models typically flow into service methods | Strong — inbound port adapters absorb API changes | Variable — depends on per-feature discipline |
| Pluggable infrastructure | Strong — gateway interfaces decouple use case from JPA; swap by changing config | Weak — services import repositories directly | Strong — outbound port adapters decouple core from infrastructure | Variable — repository coupling is per-feature |
| Unit testability | High — use case has no Spring or JPA imports; mock gateway interfaces with Mockito | Medium — must mock repository classes or use `@SpringBootTest` | High — same as Clean Architecture | Medium — depends on whether repositories are abstracted |
| Enforceability at build time | High — ArchUnit rules assert layer boundaries | Low — no structural rule prevents a service importing a repository from another layer | High — ArchUnit can assert port dependency rules | Medium — ArchUnit rules are harder to express for per-feature boundaries |
| Pluggable authorization | Strong — `PolicyDecisionPort` interface swapped via config; use case is oblivious | Weak — security annotations (`@PreAuthorize`) scattered across service methods | Strong — authorization is an outbound port | Variable |
| Familiarity / onboarding cost | Medium — requires understanding of Dependency Rule and gateway pattern | Low — widely understood pattern | Medium-High — port naming conventions can be ambiguous | Medium — intuitive for features in isolation; harder at scale |
| Boilerplate per new endpoint | Medium — command record, gateway interface(s), gateway implementation, config class, command mapper | Low — add a service method and a repository call | Medium — same as Clean Architecture with explicit port naming | Low — add a self-contained feature slice |
| Independent versioning of API | Strong — the command record is the stable internal contract; API changes are absorbed at the mapper | Weak — API model changes propagate into service signatures | Strong | Variable |
| Legacy migration path | Incremental — new endpoints use clean pattern; legacy `service/` and `mapper/` packages excluded from ArchUnit scope until migrated | N/A — current state | Requires more upfront renaming of existing classes | Requires per-feature rewrites |

---

## Decision

**Option A (Clean Architecture)** is adopted.

The Dependency Rule is implemented as follows:

1. `CreateApplicationUseCase` (and all subsequent use-case classes) carry zero imports from
   `entity`, `repository`, or `model` packages. They are not annotated with `@Component` — they
   are wired via `@Bean` in a dedicated `@Configuration` class.
2. Gateway interfaces (`ApplicationGateway`, `ProceedingGateway`, `DomainEventGateway`) live in
   `usecase/<usecasename>/infrastructure/` and are owned by the use case. JPA implementations
   live in `infrastructure/jpa/<usecasename>/` and are unknown to the use case.
3. API model types cross the boundary only inside command mapper classes
   (`CreateApplicationCommandMapper`). No other use-case class imports an API model.
4. Authorization is enforced via a `@AuthorizeOperation` annotation on use-case `execute(...)`
   methods, read at runtime by a Spring Security method interceptor (`PolicyEnforcementInterceptor`)
   that calls a `PolicyDecisionPort` interface. Phase 1 evaluates roles locally
   (`LocalPolicyDecisionPort`). Phase 2 replaces this with an external PDP by swapping the
   `@Bean` — no use-case code changes.
5. ArchUnit rules in `CleanArchitectureTest` assert the boundaries at CI time. Pre-existing
   packages (`service/`, `mapper/`, `validation/`, etc.) are excluded from scope until migrated
   incrementally.

---

## Consequences

### Positive

- Business logic is testable with plain JUnit and Mockito in milliseconds.
- The API contract (OpenAPI spec) can change without propagating into use-case or domain code.
- The persistence mechanism can be replaced by providing a new gateway implementation and
  updating the config class.
- The authorization mechanism can be replaced by providing a new `PolicyDecisionPort`
  implementation and updating the config class.
- Layer violations become build failures, not code-review comments.
- A repeatable, documented template exists for every new endpoint.

### Negative / Trade-offs

- More classes and packages per endpoint than a three-tier approach (command record, gateway
  interface, gateway implementation, gateway mapper, config class, command mapper).
- Developers unfamiliar with the Dependency Rule require onboarding before contributing new
  endpoints.
- During migration - Legacy packages (`service/`, `mapper/`, etc.) coexist with the new structure during the
  migration period, which increases apparent complexity until the migration is complete.
- During migration - ArchUnit rules must be maintained alongside the codebase; exclusions for legacy packages must
  be documented and progressively removed.

---

## References

- Robert C. Martin, *Clean Architecture*, 2017.
- ArchUnit documentation: https://www.archunit.org
- Spring Security `AuthorizationManagerBeforeMethodInterceptor` javadoc.
