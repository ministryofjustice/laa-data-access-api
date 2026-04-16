# Use Case Interface Split with Command Factory

## Summary

This document describes a lightweight first step towards hexagonal architecture when the current baseline is a single `ApplicationService` class containing all the application change logic.

The proposed change is to extract create-application into a dedicated use case abstraction and implementation, introduce a `CreateApplicationCommand` input model, and build that command via a dedicated factory.

This keeps the migration small: no persistence adapters, no new domain model layer, and no entity-to-domain mapper work in this step.

Command construction is handled by a dedicated `CreateApplicationCommandFactory` rather than being inlined in the controller. This keeps the controller thin and makes the translation from API request to use case input independently testable.

---

## Background: Current State

`ApplicationService` currently contains multiple responsibilities, including:

- Create application logic.
- Make decision logic.
- Update/query operations.

For the create-application path specifically, the service currently accepts `ApplicationCreateRequest` directly and performs payload validation and content parsing itself. That means API concerns and use case concerns are mixed together in one place.

The controller also depends on concrete service classes rather than use case interfaces.

---

## What Changes

| | Before | After |
|---|---|---|
| Location of create logic | Inside `ApplicationService` | In a dedicated create use case implementation |
| Controller dependency | Concrete service class | `CreateApplicationUseCase` interface |
| Use case input type | `ApplicationCreateRequest` (OpenAPI-generated) | `CreateApplicationCommand` |
| `@AllowApiCaseworker` | On service method | On `ApplicationController.createApplication()` |
| Payload validation | Called in service method | Called in `CreateApplicationCommandFactory` |
| Content parsing | Called in service method | Called in `CreateApplicationCommandFactory` |
| Existing repository/entity flow | Direct JPA usage in service | Unchanged in this step |

---

## New Classes

### `CreateApplicationUseCase`

A single-method interface for the create-application path. The controller depends on this interface rather than a concrete service class.

Method signature: `UUID createApplication(CreateApplicationCommand command)`

Suggested package: `service/usecase/`.

---

### `CreateApplicationCommand`

A Lombok `@Builder` record that carries everything the use case needs, pre-validated and pre-parsed. The use case never sees `ApplicationCreateRequest` or `ApplicationContent`.

Fields:
- `ApplicationStatus status`
- `String laaReference`
- `Map<String, Object> applicationContent` - kept as a raw map so existing mapper and downstream logic need minimal change
- `List<IndividualCreateRequest> individuals` - OpenAPI-generated type, acceptable at this stage since this is still a service-layer split, not a full domain-model migration
- `ParsedAppContentDetails parsedContent` — carries the result of `ApplicationContentParserService.normaliseApplicationContentDetails()`; the use case no longer calls the parser itself

---

### `CreateApplicationCommandFactory`

A `@Component` that translates an `ApplicationCreateRequest` into a `CreateApplicationCommand`. It owns the two API-layer concerns currently inside the service:

1. **Payload validation** — calls `PayloadValidationService.convertAndValidate()` to deserialise and validate the raw `applicationContent` map into a typed `ApplicationContent`.
2. **Content parsing** — calls `ApplicationContentParserService.normaliseApplicationContentDetails()` to extract structured fields (`applyApplicationId`, `categoryOfLaw`, `submittedAt`, etc.) from the validated content.

The factory then constructs and returns a fully-populated `CreateApplicationCommand`.

**Why a factory and not inline in the controller?**

- The controller is annotated `@ExcludeFromGeneratedCodeCoverage` and is deliberately kept logic-free — putting construction logic there means it cannot be unit tested.
- The factory can be tested with a plain Mockito test: no Spring context, no JPA, no security wiring needed.
- It establishes a consistent pattern — when the make-decision path is split out, a `MakeDecisionCommandFactory` can follow the same shape.

---

## Updated Classes

### Create use case implementation

Extract create-application behaviour from `ApplicationService` into a dedicated implementation class (for example `CreateApplicationUseCaseService`) that implements `CreateApplicationUseCase`.

The public method signature is `createApplication(CreateApplicationCommand)`.

Dependencies on `PayloadValidationService` and `ApplicationContentParserService` are removed from this implementation because those concerns move to the factory. Repository calls, mapper calls, proceedings saving, and domain event publishing stay the same.

### `ApplicationMapper`

Needs a new `toApplicationEntity(CreateApplicationCommand)` overload alongside the existing `toApplicationEntity(ApplicationCreateRequest)`. The new overload reads the same fields (`status`, `laaReference`, `applicationContent`, `individuals`). The existing overload is kept unchanged so other code is unaffected.

### `DomainEventService`

`saveCreateApplicationDomainEvent` currently takes `(ApplicationEntity, ApplicationCreateRequest, ...)`. With the command in place it needs an overload accepting `(ApplicationEntity, CreateApplicationCommand, ...)` — it reads the same `status` and `laaReference` fields from the command that it currently reads from the request. Keeping the existing overload means no other callers are affected.

### `ApplicationController`

- Field type changes from concrete service dependency to `CreateApplicationUseCase`.
- `CreateApplicationCommandFactory` is injected.
- `@AllowApiCaseworker` is added to the `createApplication()` method (moved from service layer).
- The method body becomes: call the factory to produce a command, then call the use case with it.

---

## Impact on Tests

### `CreateApplicationTest` (existing)

This test uses a partial Spring context (`@SpringBootTest` with JPA excluded) and mocks repositories via `@MockitoBean`. It currently tests create behaviour through service-level entry points.

Required changes:
- The create call changes from `createApplication(applicationCreateRequest)` to `createApplication(command)` at the extracted use case boundary.
- A `CreateApplicationCommand` must be constructed in test setup — either by injecting and calling the factory, or by building the command directly using its `@Builder`.
- All assertions on repository interactions, domain event publishing, and the returned ID remain unchanged.

### New: `CreateApplicationCommandFactoryTest`

A pure unit test — no Spring context, no JPA, no security. Mocks `PayloadValidationService` and `ApplicationContentParserService` and verifies:

- Given a valid request, the factory calls both services, builds a command with the correct field values, and returns it.
- Given a validation failure from `PayloadValidationService`, the `ValidationException` propagates out of the factory.

---

## What This Does Not Do

This approach deliberately defers the following:

| Concern | Why deferred |
|---|---|
| Persistence adapters (`ApplicationPersistencePort`) | Extracted create use case still calls `ApplicationRepository` directly |
| Domain model (plain `Application` object) | Extracted create use case still operates on `ApplicationEntity` |
| Entity ↔ domain mapping (`DomainEntityMapper`) | Not needed until persistence adapters are introduced |
| `@Transactional` placement | Stays on the service method |
| Migrating make decision path | Same pattern applies; separate ticket |

When persistence adapter work begins, `CreateApplicationCommand` is already the stable input contract — the adapter changes happen inside the service, not at the interface boundary.

---

## Package Layout After This Ticket

```
service/
├── ApplicationService.java                      ← create logic removed from here
└── usecase/
	├── CreateApplicationCommand.java           ← new record
	├── CreateApplicationCommandFactory.java    ← new @Component
	├── CreateApplicationUseCase.java           ← new interface
	└── CreateApplicationUseCaseService.java    ← extracted implementation
```

`ApplicationService` remains for existing non-create operations in this step.

---

## Checklist

- [ ] `CreateApplicationUseCase` interface created in `service/usecase/`
- [ ] `CreateApplicationCommand` record created in `service/usecase/`
- [ ] `CreateApplicationCommandFactory` created in `service/usecase/`
- [ ] create-application method removed from `ApplicationService`
- [ ] extracted implementation class (for example `CreateApplicationUseCaseService`) implements `CreateApplicationUseCase`
- [ ] `ApplicationController` updated: depends on `CreateApplicationUseCase` and `CreateApplicationCommandFactory`
- [ ] `@AllowApiCaseworker` moved from service layer to `ApplicationController.createApplication()`
- [ ] extracted use case implementation accepts `CreateApplicationCommand`
- [ ] `PayloadValidationService` and `ApplicationContentParserService` removed from extracted use case implementation
- [ ] `ApplicationMapper.toApplicationEntity(CreateApplicationCommand)` overload added
- [ ] `DomainEventService.saveCreateApplicationDomainEvent` overload added for `CreateApplicationCommand`
- [ ] Existing `CreateApplicationTest` updated to build/inject `CreateApplicationCommand`
- [ ] New `CreateApplicationCommandFactoryTest` written (pure Mockito, no Spring context)
- [ ] All existing tests pass
