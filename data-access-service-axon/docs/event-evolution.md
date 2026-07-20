# Event Evolution and Compatibility

Events in Axon's store are long-lived serialized contracts. The module currently uses Axon
Framework 4.11.2 with Jackson serialization. PostgreSQL stores the payload type, optional revision,
and serialized bytes in `domain_event_entry`.

Changing a Java event record does not change events already stored in the database.

## Why ordinary refactoring can break replay

When Axon reads an event, it must resolve its stored payload type and deserialize its historical
fields. These apparently simple changes can therefore be breaking:

- moving an event to another package;
- renaming the event class;
- renaming or removing a record component;
- changing a field's Java type;
- making a previously absent field mandatory;
- changing constructor validation or collection-copy behaviour;
- changing the meaning of an existing value;
- changing serializer configuration.

Compilation and tests that construct only the new event type will not detect those failures.

## Compatibility decision process

Before changing an event, answer:

1. Has this event been persisted in any environment that matters?
2. Can the new Java type deserialize the old JSON shape safely?
3. Does the old event retain the same business meaning?
4. Do aggregate and projection handlers know how to process both forms?
5. Does the change add sensitive data or weaken retention controls?

Use one of the strategies below rather than assuming compatibility.

## Safe additive change

An optional field can sometimes be added to an existing event if all consumers treat absence as a
defined default. With Java records and constructor normalization, verify this rather than assuming
it: an old missing collection may deserialize as `null`, while `List.copyOf(null)` fails.

Required work includes:

- define the meaning of the absent value;
- make construction/deserialization tolerate it;
- keep event-sourcing and projection handlers compatible;
- add a test using the exact old serialized JSON;
- verify replay against PostgreSQL serialization.

Do not reuse an existing field with a new meaning. That is a semantic breaking change even if JSON
deserialization succeeds.

## New event type

Prefer a new event type when the business fact has materially changed. Keep the old handler for
historical events and add a handler for the new event:

```text
OldDecisionRecordedEvent → old historical meaning
ApplicationDecisionMadeEvent → new meaning and fields
```

This is often clearer than making one event schema represent several eras of behaviour.

Do not delete the old class while its fully qualified name remains in `payload_type`.

## Revision and upcasting

When old serialized data must be transformed into a newer representation before deserialization,
use an Axon event revision and upcaster.

Conceptually:

```text
stored event type + revision 0
  → upcaster transforms old serialized fields
  → current event type + revision 1
  → current handler
```

An upcaster must be deterministic, side-effect free, and independent of mutable database or network
state. It transforms the serialized event representation; it is not a Flyway migration and should
not query `application_data` to fill missing history.

The module does not currently register custom upcasters or event revisions. Introducing the first
one requires explicit serializer configuration, old-payload fixtures, aggregate replay tests,
projection replay tests, and PostgreSQL integration coverage.

## Renaming or moving an event

Because the stored payload type normally includes the fully qualified class name, moving a class can
make historical events unresolvable. Options include:

- leave a compatibility type at the old name;
- configure a serializer alias/type mapping deliberately;
- upcast the old type to the new type;
- introduce a new event for future writes while retaining the old handler.

Do not perform a package-wide event refactor as a purely mechanical change.

## Projection evolution

Changing a projection table is separate from changing an event. Flyway can migrate disposable read
tables, and a projection reset can rebuild them—but only if current handlers can process every old
event and any referenced `application_data` still exists.

When adding a new projection field:

- decide which historical event supplies it;
- define a value for events that predate it;
- update reset/replay tests;
- verify replay does not call unavailable external systems;
- decide what happens after retention deletion.

## Thin-event and PII review

Event evolution is also a privacy boundary. Before adding a field, ask whether it is required for:

- command-side state reconstruction;
- routing or correlation;
- concurrency;
- an approved durable business fact.

If it is detailed content or free text, put it in a new immutable `application_data` version instead.
Remember that stable IDs can still be personal data.

## Required test evidence

For a persisted event change, include:

- an old serialized payload fixture;
- deserialization or upcasting into the intended current representation;
- aggregate replay followed by a command that relies on restored state;
- projection reset/replay assertions;
- PostgreSQL integration coverage of payload type, revision, and bytes;
- an assertion that prohibited sensitive fields are absent from stored event JSON.

Record a significant event-compatibility strategy in an ADR. See
[Testing Axon code](testing-axon-code.md) for the appropriate test layers.
