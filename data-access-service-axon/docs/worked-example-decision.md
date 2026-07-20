# Worked Example: Making an Application Decision

This walkthrough follows `PATCH /api/v0/applications/{id}/decision` through the actual module. It is
the best starting point for understanding how thin events and immutable sensitive-data versions fit
into normal Axon command handling.

## Component map

| Step | Class | Responsibility |
|---|---|---|
| HTTP adapter | `ApplicationCommandController` | Accept generated API types and dispatch the command |
| Request mapping | `MakeDecisionCommandMapper` | Convert the request, serialize audit input, and capture time |
| Command message | `MakeApplicationDecisionCommand` | Carry the target ID, expected version, and requested decision |
| Consistency boundary | `ApplicationAggregate` | Check optimistic locking and decision rules |
| Sensitive-data store | `ApplicationDataStore` | Read the current immutable payload and append the next one |
| Domain event | `ApplicationDecisionMadeEvent` | Record thin outcome and new version pointers |
| Current projection | `ApplicationProjection` | Advance the query-side versions and modification time |
| History projection | `ApplicationHistoryProjection` | Record audit history and hydrate descriptions when queried |

The matching diagram is [Sensitive-data change and thin event](sequence-diagrams/04-sensitive-data-change.md).

## 1. HTTP request and mapping

`ApplicationCommandController.makeDecision` receives the generated `MakeDecisionRequest` and asks
the mapper for an internal command:

```java
commandGateway.sendAndWait(decisionCommandMapper.toCommand(id, request));
```

`MakeDecisionCommandMapper` maps the public request to `MakeApplicationDecisionCommand`. It also:

- maps each proceeding decision into an internal record;
- serializes the original request for audit storage outside the event stream;
- extracts the free-text event description;
- captures `Instant.now()` once as the operation time.

The command's `applicationId` has `@TargetAggregateIdentifier`. The caller supplies
`expectedApplicationVersion`, but does not supply `applicationDataVersion`.

## 2. Aggregate loading

Axon reads the application's stream and invokes the aggregate's event-sourcing handlers. For
example:

- `ApplicationCreatedEvent` establishes ID, schema, fingerprint, and initial versions;
- previous decision events advance both versions;
- assignment events restore the current caseworker and versions;
- note events advance only the data version.

If no application stream exists, loading fails before the decision handler runs.
`ApplicationExceptionHandler` maps that failure to the public 404 response.

## 3. Optimistic-lock check

The handler first compares the request's expected application version with its rebuilt state:

```java
if (command.expectedApplicationVersion() != applicationVersion) {
  throw new ApplicationVersionConflictException(...);
}
```

This occurs before reading `application_data`. A stale caller receives `409 Conflict` without an
unnecessary sensitive-data query or write.

The version is a domain/API concurrency check in addition to Axon's own event-stream sequence
protection. Axon protects concurrent writes to the stream; `applicationVersion` tells the API
whether the caller made its decision using the state it expected.

## 4. Domain validation

The aggregate validates the requested outcome, including:

- at least one proceeding;
- a certificate for a granted application;
- refusal justification where required;
- no duplicate proceeding IDs;
- every requested proceeding exists in the current application payload.

Validation failure emits no event.

## 5. Load and update sensitive data

The aggregate reads the complete payload selected by its current pointer:

```text
(applicationId, applicationDataVersion)
```

It builds a complete updated `ApplicationDataPayload`, preserving unchanged fields and adding the
decision, certificate, serialized request, and free-text description. It then chooses
`applicationDataVersion + 1` and appends that immutable row.

This read is command handling, not aggregate replay. Event-sourcing handlers never query
`ApplicationDataStore`.

## 6. Apply the thin event

After the data append succeeds, the aggregate applies `ApplicationDecisionMadeEvent`. The event
contains:

- application ID;
- next `applicationVersion`;
- next `applicationDataVersion`;
- overall outcome and auto-granted flag;
- occurrence time.

It does not contain proceedings, certificate content, serialized request, or event description.
Those remain in the referenced immutable payload.

The aggregate's event-sourcing handler advances both version fields. If the append fails, the event
is never applied. If transaction commit fails, the data insert and event are rolled back together.

## 7. Return and project

`sendAndWait` returns when command processing completes, and the controller returns `204 No
Content`. Tracking processors then consume the event independently:

- `ApplicationProjection` updates the two version pointers and `modifiedAt`.
- `ApplicationHistoryProjection` stores a thin public history row.

When decision history is queried, the history projection uses the event's data version to retrieve
the stored description and reconstruct the public history payload.

## 8. How this is tested

`ApplicationAggregateTest.givenCurrentApplicationVersion_whenDecisionMade_thenStoresNextVersionAndEmitsThinEvent`
starts with an `ApplicationCreatedEvent`, registers a mocked `ApplicationDataStore`, sends the
decision command, and expects the exact thin event.

Related tests cover:

- stale application versions;
- missing and duplicate proceedings;
- certificate and justification validation;
- automatic version increments;
- data append failure producing no event;
- concurrent PostgreSQL decisions committing only one result;
- serialized event payloads not containing the sensitive decision request.

Use [Testing Axon code](testing-axon-code.md) to decide where a new scenario belongs.
