# ADR 0001: Use a Subscribing Event Router for Application Linking

- Status: Accepted
- Date: 2026-07-20
- Scope: `data-access-service-axon`

## Context

Creating an application can also create or extend a linked application group. The implementation
must coordinate three consistency boundaries:

- the newly created associated `ApplicationAggregate`;
- the lead `ApplicationAggregate`;
- the `LinkedApplicationGroupAggregate` that owns group membership.

The public API currently treats linking as part of application creation. Before the request
returns, it must establish that referenced applications exist and that the lead is permitted to
lead a group. A missing reference must produce a 404 on the original request, and an invalid group
relationship must reject that request rather than create an application whose linking may fail
later.

The coordination is a short, deterministic reaction to domain events:

```text
ApplicationCreatedEvent
  → validate other referenced applications
  → ask the lead application to request group formation

LinkedApplicationGroupRequested
  → initialise the group or add missing members
```

Every command required by the next step can be derived from the event being handled. There is no
workflow state that needs to survive between messages: no waiting stage, deadline, timeout,
external response, compensation, or manually recoverable intermediate status.

## Decision drivers

- Preserve the synchronous application-creation API contract.
- Roll back the originating unit of work when reference validation or a group invariant fails.
- Keep group membership authoritative in `LinkedApplicationGroupAggregate` rather than duplicate
  it in a coordinator.
- Avoid persistent coordination state when there is no process state to remember.
- Keep duplicate handling in the aggregates, where creation and membership changes are
  idempotent.
- Make the design straightforward to test and operate during the proof of concept.

## Decision

Use `ApplicationGroupEventRouter` as a stateless event handler for linking. Register its
`linked-application-group-router` processing group as a subscribing processor in
`AxonCommandBusConfig`, with listener and processor errors configured to propagate.

The router dispatches commands synchronously with `sendAndWait`. The application and group
aggregates remain responsible for their own state and invariants. The router contains no durable
workflow state and is not a saga.

This means that linking executes in the command thread and Axon unit of work that published the
event. With the configured Spring transaction manager, a linking failure propagates to the
originating command and rolls back the request.

## Why not a saga

An Axon saga is useful when coordination itself has persistent state or a lifecycle. Typical
examples include waiting for responses, correlating later events, enforcing deadlines, recording
progress across restarts, and issuing compensation after a partially completed workflow.

None of those capabilities is required by the current linking flow. A saga would introduce:

- saga instances and lifecycle rules;
- association values for application and group identifiers;
- persistent saga storage;
- duplicate and partially completed workflow handling;
- additional operational and test scenarios for stuck saga instances.

That state would duplicate no meaningful business fact. Application existence belongs to each
application aggregate, and group membership belongs to the group aggregate.

A saga is not inherently asynchronous. It could also run on a subscribing processor and preserve
the current HTTP semantics. However, a subscribing saga would add persistent coordination
machinery without solving a problem that the stateless router has.

An asynchronous or tracking saga would change the public behaviour: application creation could
succeed while linking remained pending or later failed. Supporting that model would require an
explicit intermediate status, recovery behaviour, and a product decision that eventual linking is
acceptable.

## Consequences

### Positive

- Missing linked applications and group invariant failures are returned on the original request.
- A failure does not leave an application creation awaiting later workflow recovery.
- There is no saga table state to inspect, clean up, or migrate.
- The router is easy to understand: each event maps directly to its next command.
- Aggregate event streams remain the durable source of application and group state.

### Negative

- Application creation is coupled to successful completion of the complete linking chain.
- The command executes nested commands against multiple aggregates in one unit of work, increasing
  lock and transaction scope.
- The approach does not suit slow or unreliable external dependencies.
- Adding more steps will make the synchronous call chain harder to reason about and may increase
  deadlock or timeout risk.
- The router must use propagating error handlers; a logging handler that swallows failures would
  break the API contract.

## Alternatives considered

### Axon saga

Rejected for the current flow because there is no persistent process state. A subscribing saga
could preserve synchronous behaviour but would add lifecycle, association, and storage overhead.
An asynchronous saga would be appropriate only alongside a deliberate change to eventual linking
semantics.

### Tracking event processor with a stateless handler

Rejected because the original application request could return before reference validation and
group creation completed. Failure would require retry and recovery behaviour outside the current
API contract.

### Put all linking state in `ApplicationAggregate`

Rejected because membership spans several applications. Making one application own the full group
would blur consistency boundaries and make concurrent membership changes harder to protect.

### Perform all linking in the HTTP controller

Rejected because it would couple the transport layer to domain event sequencing and bypass the
event-driven relationship between application creation and group formation.

## When to revisit

Reconsider this decision if any of the following become true:

- linking calls another service or message broker;
- a linking step can take long enough that the HTTP request should not wait;
- the workflow needs deadlines, scheduled retries, or compensation;
- linking is allowed to be pending or fail after application creation succeeds;
- operators need to observe and resume intermediate workflow stages;
- the nested multi-aggregate transaction creates unacceptable locking, timeout, or throughput
  problems;
- linking gains process state that cannot be derived from a single triggering event or owned by an
  aggregate.

At that point, introduce an explicit linking process model and reconsider an asynchronous saga or
process manager. The API and domain model should first define pending, failed, retry, and
compensation semantics rather than treating a saga as an implementation-only substitution.

## Related documentation

- [Linked applications](../linked-applications.md)
- [Linked application creation sequence](../sequence-diagrams/02-linked-application-creation.md)
- [Second member joins an existing group](../sequence-diagrams/03-second-member-joins-group.md)
- [Architecture overview](../architecture.md)
