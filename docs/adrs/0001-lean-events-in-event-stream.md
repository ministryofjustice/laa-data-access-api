---
status: accepted
date: 2026-07-17
---

# Store only decision-relevant data in the event stream (lean events)

## Context and Problem Statement

The application uses Axon Framework with event sourcing. When a command is processed, a significant amount of data is available: business-relevant state (status, case references, proceeding details), contextual metadata (submitting service, timestamps), and personal data about individuals involved (applicant name, provider details, contact information).

The question is whether events should carry all data known at processing time ("rich events"), or only the data that aggregates and sagas need in order to make decisions about state transitions and routing ("lean events").

This decision has a direct dependency on ADR-0002, which addresses where non-event-stream data — particularly PII — should be stored.

## Decision Drivers

* GDPR obligations require that personal data can be erased on request. Storing PII in an append-only event store makes erasure complex without crypto-shredding.
* Events should express business facts precisely. Overloading them with contextual data dilutes their semantic value and couples them to the shape of external systems.
* Aggregate state is reconstructed by replaying the event stream; large event payloads slow replay and increase storage costs.
* Schema evolution of events requires upcasters. Minimising what is in each event reduces the frequency and surface area of upcasting.

## Considered Options

* **Lean events** — events contain only the fields read by `@EventSourcingHandler` or `@SagaEventHandler` methods
* **Rich events** — events carry all data available at command-processing time

## Decision Outcome

Chosen option: **Lean events**, because it keeps PII out of the event store by design, preserves the semantic clarity of events as business facts, and reduces coupling to external data shapes.

The guiding test for any proposed event field is: _"If I removed this field, would any `@EventSourcingHandler` or `@SagaEventHandler` need to change?"_ If no, the field does not belong in the event.

This is not a licence to exclude genuinely business-relevant data. Fields that drive status transitions, aggregate routing, or saga branching belong in the event regardless of whether they are also present elsewhere.

### Consequences

* Good, because PII never enters the event store, avoiding crypto-shredding complexity and keeping the GDPR deletion path simple.
* Good, because events remain semantically meaningful — they record what happened at the domain level, not what was known at the time.
* Good, because aggregate replay is faster and event storage is smaller.
* Good, because events are less brittle; supplementary data can evolve without requiring upcasters.
* Bad, because developers must exercise discipline to decide what is decision-relevant. This requires clear domain understanding and shared conventions.
* Bad, because if a field excluded from events is later needed for business logic, it cannot be back-filled from the event stream — there is a permanent historical gap.

### Confirmation

An ArchUnit rule should assert that no event class contains fields of types known to carry PII (e.g. types from the `individuals` package, or fields named `name`, `dateOfBirth`, `address`, etc.). This rule enforces the boundary mechanically.

Code review should verify that any new event field has a corresponding read in an `@EventSourcingHandler` or `@SagaEventHandler`. Fields that are only read in `@EventHandler` projection methods are candidates for removal from the event.

## Pros and Cons of the Options

### Lean events

* Good, because GDPR surface is minimised by design.
* Good, because event semantics are preserved — each event is a precise business fact.
* Good, because replay performance and storage costs are lower.
* Good, because events are decoupled from the data shape of external systems.
* Bad, because historical gaps are permanent if excluded data is later needed for business logic.
* Bad, because it requires active discipline and shared team conventions to apply consistently.

### Rich events

* Good, because a complete audit log is available from the event stream alone.
* Good, because future read models can be built purely from replay without needing supplementary stores.
* Good, because simpler initial development — no upfront decision about relevance.
* Bad, because PII in the event store creates a significant GDPR liability without crypto-shredding.
* Bad, because events become coupled to external data shapes, increasing the frequency of upcasting.
* Bad, because replay is slower and storage grows larger.
* Bad, because the semantic clarity of events is lost.

## More Information

This decision is a prerequisite for ADR-0002 (PII storage) and ADR-0003 (read model composition). Lean events make a supplementary PII store mandatory rather than optional.
