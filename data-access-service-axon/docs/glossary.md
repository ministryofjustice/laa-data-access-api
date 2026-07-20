# Glossary

## Aggregate

A consistency boundary that handles commands and protects business rules. Axon rebuilds an
event-sourced aggregate by replaying its event stream. This module has application and linked-group
aggregates.

## Aggregate identifier

The value Axon uses to route a command to an event stream. Commands mark it with
`@TargetAggregateIdentifier`; aggregates mark their field with `@AggregateIdentifier`.

## Application version

The public optimistic-lock version of an application. A decision request supplies the version it
was based on so the aggregate can reject stale changes.

## Application data version

An internal version identifying one immutable `application_data` row. It changes whenever the
detailed payload changes and is not controlled by API callers.

## Command

A request to perform an action. A command may be rejected. It is routed to one command handler and
normally targets one aggregate.

## Command handler

Code that receives a command, checks current state and business rules, and may apply events. It must
not directly mutate event-sourced aggregate state as the durable outcome.

## Consistency boundary

The state and rules that must change atomically. In this module, an application owns its lifecycle,
while a linked group separately owns its lead and membership.

## Event

An immutable record that something happened. Applying an event changes aggregate state through an
event-sourcing handler and publishes the event to projections and other event handlers.

## Event handler

A component that reacts to a published event. Projection event handlers update read models;
`ApplicationGroupEventRouter` dispatches the next linking command.

## Event-sourcing handler

An aggregate method annotated with `@EventSourcingHandler`. It updates in-memory aggregate state
when an event is first applied and whenever the aggregate is rebuilt. It should be deterministic
and perform no external I/O.

## Event stream

The ordered events for one aggregate identifier. Axon's sequence number records their order.

## Hydration

Combining a thin projection or event with its referenced `application_data` version to construct a
detailed API response.

## Idempotency

The property that repeating an operation has no additional effect. Identical application creation,
existing group members, and repeated unassignment are handled idempotently.

## Projection

A query-oriented view built from events. Projections are disposable and can be reset and replayed;
they are not aggregate state.

## Process manager

A coordinator for a multi-step process. The term is often used for stateless or stateful event-driven
coordination that does not necessarily use Axon's saga infrastructure.

## Replay

Processing historical events again to rebuild an aggregate or projection. Aggregate replay restores
command-side state. Projection replay rebuilds disposable read tables.

## Saga

A persistent coordinator for a process spanning messages or consistency boundaries. Axon sagas use
association values and lifecycle state and are useful for waits, timeouts, later responses, and
compensation. A saga can use subscribing or tracking processing; it is not inherently asynchronous.

## Subscribing event processor

An event processor that invokes handlers in the publishing thread. The linking router uses one so
failures propagate to the originating command's unit of work.

## Thin event

An event containing the minimum control data needed for routing, aggregate rebuilding, and business
history, plus a pointer to detailed immutable data. Thin is a data-minimisation goal, not a guarantee
that the event contains no personal data.

## Tracking event processor

An independently running processor that reads events using a stored token. It can lag behind
commands, stop on failure, and be reset and replayed. Query projections use tracking processors.

## Unit of work

Axon's boundary around message handling. It coordinates event publication, nested subscribing
handlers, commit, and rollback. This module bridges it to Spring transaction management.
