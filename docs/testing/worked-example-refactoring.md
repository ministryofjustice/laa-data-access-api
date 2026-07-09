# Worked Example: Refactoring existing implementation

## Scenario

The logic for building the domain event payload in `ApplicationService.createApplication` has grown complex. We decide to extract it into a dedicated `DomainEventService` method. Internally, the code moves — but no behaviour changes.

This scenario illustrates the most important property of the classicist testing approach: **you can refactor freely without touching the tests**.

---

## What changes in the implementation

**Before:**

```java
// ApplicationService.java
public UUID createApplication(ApplicationCreateRequest request) {
    // ... save application ...

    // Domain event payload built inline
    String data = objectMapper.writeValueAsString(
        CreateApplicationDomainEventDetails.builder()
            .applicationId(savedEntity.getId())
            .laaReference(savedEntity.getLaaReference())
            .applicationStatus(savedEntity.getStatus().toString())
            .request(objectMapper.writeValueAsString(request))
            .build()
    );
    DomainEventEntity event = DomainEventEntity.builder()
        .applicationId(savedEntity.getId())
        .type(DomainEventType.APPLICATION_CREATED)
        .data(data)
        .build();
    domainEventRepository.save(event);

    return savedEntity.getId();
}
```

**After:**

```java
// ApplicationService.java
public UUID createApplication(ApplicationCreateRequest request) {
    // ... save application ...
    domainEventService.publishCreatedEvent(savedEntity, request);  // ← extracted
    return savedEntity.getId();
}

// DomainEventService.java — new method
public void publishCreatedEvent(ApplicationEntity application, ApplicationCreateRequest request) {
    String data = objectMapper.writeValueAsString(
        CreateApplicationDomainEventDetails.builder()
            .applicationId(application.getId())
            .laaReference(application.getLaaReference())
            .applicationStatus(application.getStatus().toString())
            .request(objectMapper.writeValueAsString(request))
            .build()
    );
    DomainEventEntity event = DomainEventEntity.builder()
        .applicationId(application.getId())
        .type(DomainEventType.APPLICATION_CREATED)
        .data(data)
        .build();
    domainEventRepository.save(event);
}
```

---

## What changes in the tests

**Nothing.**

The service unit test `givenNewApplication_whenCreateApplication_thenReturnNewId` already asserts:

- The correct entity was passed to `applicationRepository.save` (via `ArgumentCaptor`)
- The correct domain event was passed to `domainEventRepository.save` (via `ArgumentCaptor`)
- The returned ID matches the saved entity's ID

These assertions are against the **behaviour** — what was saved and what was returned — not against how the code is structured internally. Moving code from `ApplicationService` into `DomainEventService` does not change any of those outcomes.

The controller integration test `givenCreateNewApplication_whenCreateApplication_thenReturnCreatedWithLocationHeader` already asserts:

- HTTP 201 is returned
- The `Location` header contains the created ID
- The persisted entity matches the request
- A domain event was saved with the correct type and data

Again, none of these assertions care where in the codebase the event-building logic lives.

**Run the tests after the refactor. If they pass, the refactor is correct.**

---

## What would break under the London school

If the code had been tested using the London school approach, the test for `ApplicationService.createApplication` would have mocked `DomainEventService` and asserted:

```java
verify(domainEventService, times(1)).publishCreatedEvent(savedEntity, request);
```

This test would:
1. **Pass before the refactor** — `DomainEventService` did not have this method yet, so the test would not even have been written this way.
2. **Require rewriting** after the refactor, because the collaborator and its interface changed.
3. **Provide no proof** that the domain event payload is correct — only that a method was called.

Under the classicist approach, the same refactor requires zero test changes and still proves the domain event payload is correct.

---

## The general rule

> If a refactor changes the **external behaviour** of the system (what is returned, what is saved, what errors are raised), tests should change.
>
> If a refactor only changes the **internal structure** (which class owns the logic, how methods are named, how code is split), tests should not change.

This is the practical test for whether you have written the right kind of tests.

---

## A related scenario: renaming a service method

If `getAllCaseworkers()` is renamed to `getCaseworkers()`:

- The **service unit test** class (`GetAllCaseworkersTest`) calls `serviceUnderTest.getAllCaseworkers()`. This line must be updated to `serviceUnderTest.getCaseworkers()` — but only that line, not any assertions.
- The **controller integration test** (`GetCaseworkersTest`) sends an HTTP `GET /caseworkers` request and asserts the response. **No changes are needed** — the test operates at the HTTP boundary and is completely unaware of the Java method name.

The renaming touches the minimum surface: one call site in one test file, and the implementation. Everything that matters — the HTTP contract, the response shape, the data correctness — is still proven by the unchanged assertions.

---

## Checklist when refactoring

Before starting:
- [ ] Run the full test suite — all tests should pass.

After refactoring:
- [ ] Run the full test suite again — all tests should still pass.
- [ ] If any service unit test fails, check whether the failure is because the *behaviour* changed (legitimate failure) or because the *call site* changed (rename the call, not the assertion).
- [ ] If any controller integration test fails, the refactor has changed the external behaviour of the API — this needs investigating before merging.
- [ ] Do not update assertions to make tests pass. Update the implementation so the original assertions hold.

