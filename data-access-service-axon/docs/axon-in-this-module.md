# Axon in This Module

This guide provides the minimum execution model needed to work safely in the Axon module. It uses
Axon Framework 5.2.0 and the terminology defined in the [glossary](glossary.md).

## The central idea

The write model and read model have different jobs:

- Aggregates decide whether a command is allowed and emit events.
- Events are the durable record of accepted changes.
- Event-sourcing handlers rebuild aggregate state from those events.
- Projection handlers turn events into database views designed for queries.

```mermaid
flowchart LR
    HTTP[HTTP request] --> Mapper[Request mapper]
    Mapper --> Command[Command]
    Command --> Gateway[CommandGateway]
    Gateway --> Aggregate[Aggregate command handler]
    EventStore[(Event store)] -->|replay existing events| Aggregate
    Aggregate -->|apply| Event[Domain event]
    Event --> EventStore
    EventStore --> Processor[Tracking processor]
    Processor --> Projection[(Read projection)]
    Projection --> Query[Query endpoint]
```

## One command from start to finish

### 1. The controller creates a command

The HTTP controller does not update a repository. A mapper converts a generated OpenAPI request to
an internal command:

```java
var command = mapper.toCommand(applicationId, request);
commandGateway.sendAndWait(command);
```

The field annotated with `@TargetAggregateIdentifier` tells Axon which event stream to load.

### 2. Axon loads the aggregate

Before invoking an ordinary command handler, Axon reads the target event stream and creates the
aggregate by replaying its events in sequence. Each event invokes an `@EventSourcingHandler`.

If the stream does not exist, the command fails with `AggregateNotFoundException`. Handlers using
`@CreationPolicy(CREATE_IF_MISSING)` are the exception: Axon may provide a new empty aggregate, so
those handlers must explicitly distinguish new and existing state.

### 3. The command handler decides

A `@CommandHandler` can:

- inspect current aggregate state;
- validate business rules;
- call an injected command-side service when the decision needs external persisted data;
- reject the command with an exception;
- apply one or more events.

It must not treat direct field mutation as the durable result. Only events can reproduce the change
when the aggregate is loaded again.

### 4. Applying the event changes aggregate state

Calling `apply(event)` records the new fact and invokes the matching event-sourcing handler. The
event-sourcing handler performs the state transition:

```java
@CommandHandler
void handle(AssignCaseworkerToApplicationCommand command) {
  apply(new ApplicationAssignedToCaseworkerEvent(...));
}

@EventSourcingHandler
void on(ApplicationAssignedToCaseworkerEvent event) {
  applicationVersion = event.applicationVersion();
  applicationDataVersion = event.applicationDataVersion();
  caseworkerId = event.caseworkerId();
}
```

The second method runs both when the event is first applied and on every future aggregate replay.
It must therefore be deterministic and perform no repository, network, clock, or command-gateway
calls.

## Three kinds of event reaction

The annotations look similar but serve different purposes:

| Code | Runs where | Purpose | External I/O? |
|---|---|---|---:|
| Aggregate `@EventSourcingHandler` | During initial apply and aggregate replay | Rebuild command-side state | No |
| Projection `@EventHandler` on pooled streaming processor | Independently after event commit | Update query tables | Yes |
| Router `@EventHandler` on subscribing processor | In publishing command thread/unit of work | Validate links and request group formation | Only deliberately, with synchronous failure semantics |
| Initializer `@EventHandler` on pooled streaming processor | Independently after the request event commits | Create or extend the linked group | Yes; it must be idempotent and retryable |

Confusing projection handlers with event-sourcing handlers is one of the most damaging Axon
mistakes. Both consume events, but only one rebuilds aggregate state.

## Commit and projection timing

The command bus is connected to Spring transaction management. A successful command commits its
event and any `application_data` append together. The normal projections are streaming processors,
so they update after the command and can temporarily lag.

```text
sendAndWait completed
  means: command handling and event commit completed
  does not mean: every query projection is already current
```

Application creation uses a subscription query to wait briefly for its projection. It returns
`202 Accepted` if the durable write succeeds but the projection is not visible within the timeout.

Application creation does not dispatch linked-group commands. Explicit link requests use durable
route rows, maintained by `ApplicationGroupRouteProjection`, so the command handler can validate
and lock source/target membership before dispatching a linked-group command.

## Multi-aggregate linked-group commands

Linking, group assignment and readiness can change several application streams in one request. The
coordinator secures the request where required, locks the relevant `application_group_route` rows
through `ApplicationGroupRouteResolver`, then routes one command. The handler receives its primary
aggregate with `@InjectEntity`; it loads any other group or application aggregates through
`StateManager` and `LinkedGroupMembers` in the same `ProcessingContext`. A pure decider makes the
assignment decision, and the handler appends the group and application events in one append.

The route locks remain necessary even though the handler reads aggregate state: an application
loaded only to read its assignment is not protected by an append to that application's stream.
Locks serialize membership and assignment writers while the handler uses that state. The resolver
checks membership again after locking and returns 409 if it changed; it does not acquire more rows
in a second pass. Do not lock `work_item_route` before appending to an application, because a
decision appends to the application stream before its route row is removed.

`AxonArchitectureTest` limits `StateManager` dependencies to `LinkedGroupMembers` and the
establish, add, group-assignment and readiness handlers that load additional state. It also limits
route `…ForUpdate` calls to `ApplicationGroupRouteResolver` and the route projection, and only lets
`*Coordinator` classes send commands that rely on route locks.

The readiness coordinator retries the complete lock-and-dispatch attempt in a fresh transaction.
This reacquires the locks after a failed attempt instead of reusing locks from a failed transaction.

## Command state versus query state

Aggregates must not query projections to make decisions. A tracking projection may be stale, and
doing so would make the aggregate's result depend on asynchronous read-side timing.

Similarly, query handlers should not load aggregates or dispatch commands. They read projections
and hydrate sensitive fields from the immutable data versions when required.

```text
Command decision → aggregate events and command-side data
API read         → projections and referenced application_data
```

## What replay does

Aggregate replay and projection replay are separate operations:

- Aggregate replay happens whenever Axon loads an aggregate. It invokes only that aggregate's
  event-sourcing handlers to rebuild in-memory command state.
- Projection replay is an administrative recovery operation. It resets a tracking processor and
  invokes projection event handlers for historical events to rebuild query tables.

Neither replay should call an external service. Aggregate replay does not load detailed application
payloads; it restores their version pointers from thin events.

## Where to go next

Follow the [worked decision example](worked-example-decision.md), then read
[Guardrails and common mistakes](guardrails.md) before changing aggregate or event code.
