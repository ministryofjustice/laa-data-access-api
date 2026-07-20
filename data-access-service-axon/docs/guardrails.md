# Guardrails and Common Mistakes

These rules protect replay determinism, consistency boundaries, sensitive data, and asynchronous
query behaviour. Treat a proposed exception as an architectural decision, not a local convenience.

## Aggregate rules

### Do not perform I/O in an event-sourcing handler

An `@EventSourcingHandler` runs every time an aggregate is loaded. It must only derive aggregate
fields from the event.

Do not call:

- repositories or `ApplicationDataStore`;
- `CommandGateway` or `QueryGateway`;
- HTTP clients;
- the system clock;
- random ID generators.

External state may have changed since the event was originally recorded, making replay
nondeterministic.

### Do not directly mutate durable state in a command handler

A command handler checks rules and applies events. If it changes a field without an event-sourcing
handler making the same change, the value disappears when Axon next reloads the aggregate.

### Do not query projections from an aggregate

Tracking projections may lag. Aggregate decisions must use aggregate state, command-side immutable
data, or an explicitly coordinated consistency boundary—not a potentially stale query model.

### Use `CREATE_IF_MISSING` deliberately

Ordinary commands should let Axon reject a missing aggregate. Creation/link-validation handlers use
`CREATE_IF_MISSING` for specific idempotency and error-contract reasons and must guard the empty
aggregate case themselves.

### Keep one aggregate responsible for each invariant

`ApplicationAggregate` owns one application's lifecycle. `LinkedApplicationGroupAggregate` owns
group lead and membership. Do not duplicate group membership rules in controllers or projections.

## Event rules

### Events are persisted contracts

An event record is not an ordinary refactorable DTO. Its fully qualified type name, serialized
fields, revision, and meaning may be stored for years. Read [Event evolution](event-evolution.md)
before renaming, moving, or changing one.

### Keep events thin and reviewed

Do not add application content, individuals, proceedings, certificates, note text, serialized
requests, or free-text descriptions to events. Append them to `application_data` and reference the
new version. Identifiers may still be personal data and also require review.

### Do not nest events inside events

Events should describe one fact using stable value fields. The architecture test rejects event
fields whose type is another event, preventing accidental coupling of two persisted schemas.

### Make event-sourcing handlers tolerant of history

Never assume replay sees only events produced by the current code. Old events keep their original
serialized shape and order.

## Processor and projection rules

### `sendAndWait` does not make tracking projections current

It waits for command completion. Tracking processors can still lag. Use subscription queries where
the API explicitly needs read-your-write behaviour, or return an asynchronous result such as the
creation endpoint's `202 Accepted`.

### Understand subscribing failure semantics

The linking router is in the command unit of work. A slow handler slows the request, and a propagated
failure rolls it back. Do not add external or unreliable calls to that router.

### Make tracking handlers replayable

Projection handlers should be deterministic for an event, tolerate retries, and have a reset
handler for owned tables. They must not emit irreversible external side effects during replay.

### Do not edit tracking tokens manually

Use Axon's processor lifecycle and reset APIs. Editing `token_entry` while a processor owns a token
can create duplicate processing, gaps, or conflicting ownership.

### Keep query and command directions separate

The architecture tests prohibit aggregates depending on `QueryGateway` and query packages depending
on `CommandGateway`. Queries should not cause writes.

## API and error rules

### Do not expose Axon messages as API contracts

Controllers accept generated OpenAPI types and map them to internal commands. Events and commands
can evolve for domain reasons without directly changing the public schema.

### Translate infrastructure language at the HTTP boundary

Clients should receive application-oriented errors, not aggregate, stream, token, serializer, or
processor details. Keep exception translation in `ApplicationExceptionHandler`.

### Distinguish rejection from projection delay

A command exception means no successful commit. `202 Accepted` means the command committed but its
projection was not visible before the timeout.

## Data and transaction rules

### Append; never update `application_data`

Each detailed change creates a complete next version. PostgreSQL rejects update, direct delete, and
truncate operations. Callers never choose the data version.

### Apply the event only after the data append succeeds

The event must never point to a version that was not written. Shared transaction management then
rolls back the insert if event commit fails.

### Missing data is not automatically retention

Until ADR 0003 is implemented, an absent referenced payload could be deletion, corruption, or an
incomplete operation. Do not silently convert every missing row to a retention outcome.

## Automated guardrails

`AxonArchitectureTest` currently enforces that:

- command and event messages in command packages are records;
- events do not contain other event-typed fields;
- aggregates do not depend on `QueryGateway`;
- query classes do not depend on `CommandGateway`.

When a rule is important and mechanically detectable, prefer adding an architecture test rather
than relying only on this document.
