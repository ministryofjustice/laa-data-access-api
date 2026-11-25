# Event Evolution and Compatibility

Events in Axon's store are long-lived serialized contracts. The module uses Axon Framework with
Jackson serialization through `EventJson`, the one mapper for event payloads and content.
PostgreSQL stores the payload type, optional revision, and serialized bytes in `domain_event_entry`.
Every event type declares its stored name explicitly, `@Event(namespace = "access", name = ...)`,
so the payload type is `access.<Name>`, not the Java class name. The stored bytes hold only the
fields in the event's kept-field schema; content is stored beside them in `event_content` (see
[ADR 0007](adr/0007-event-content-is-stored-beside-its-event.md)).

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
- rely on the existing [stored event fixtures](#stored-event-fixtures), which already hold the
  old JSON and accept an added field; a kept field added this way must be nullable in its schema;
- verify replay against PostgreSQL serialization.

Do not reuse an existing field with a new meaning. That is a semantic breaking change even if JSON
deserialization succeeds.

### Document upload suffix

`ApplicationDocumentUploadedEvent` and `PriorAuthorityDocumentUploadedEvent` add the optional string
`fileSuffix`. It records only the exact case-sensitive suffix of the stored UUID filename, never
the full original filename. Existing IDs, version pointers, and upload semantics are unchanged.

- Missing or null means historical metadata with an unknown suffix.
- An empty string means a new upload known to have no suffix.
- A value such as `.PDF` means the exact persisted storage suffix.

Both forms deserialize without a revision or upcaster. Aggregate event folds and projections retain
the field, including null. Historical queries and deletion fall back to the sensitive filename
when available; they never infer a suffix from MIME type. The document metadata JSON columns need
no schema migration. Reset/replay restores suffixes from events that contain them, but cannot
recover suffixes missing from historical events. Existing projected rows may keep null until
rebuilt. `DocumentUploadEventCompatibilityTest` uses old event and metadata JSON shapes, while
PostgreSQL tests verify persisted suffixes and absence of full filenames from events.

### Prior Authority draft-update stream tagging

`PriorAuthorityDraftUpdatedEvent` retains its original three kept fields:
`priorAuthorityId`, `parentApplicationId`, and `occurredAt`. Its owner ID is now annotated
with `@EventTag(key = "PriorAuthorityAggregate")`, and the aggregate explicitly handles
the event, replacing the draft body and raw request from erasable event content. This associates new draft saves with the
same optimistic-concurrency stream as document mutations. A stale append rolls back
its draft write, allowing the existing bounded dispatcher retry to reload current state.

No event rename, revision, upcaster, or JSON migration is required. Historical JSON
still deserializes and can be folded without altering identity, lifecycle, or documents.
Previously persisted events are not retagged by changing the Java annotation, and
missing content cannot be reconstructed from the kept fields. Any historical
store remediation needs a separate decision; do not rewrite persisted metadata here.

Like every event, the record declares `@Event(namespace = "access", name = ...)`, so tooling that
resolves stored type names through `@Event` classes can resolve it. `GenesisScopeArchitectureTest`
requires `@Event` on every `*Event` in `command` packages. `LegacyGenesisPayloadCompatibilityTest`
and `LegacyGenesisPayloadReplayIntegrationTest` check that payloads written before a genesis field
existed still load.

`PriorAuthorityAggregateTest` covers the old JSON and a following document-type event.
The PostgreSQL draft integration tests force both competing commands to read stale
state before writing, assert one rolled-back/retried attempt, and check that both
accepted changes and both owner-stream events survive without sensitive event fields.

### Prior Authority submission and decision content

`PriorAuthoritySubmittedEvent` adds the erasable `content` and `serialisedRequest`, and
`PriorAuthorityDecisionMadeEvent` adds the erasable `expertFee`, `disbursementInformation`,
`apportionmentInformation` and `serialisedRequest`. All are content, so the kept-field schemas and
stored payload bytes are unchanged. Each compact constructor normalises an absent content field to
erased, so an event whose row has been erased still deserializes and folds; its submitted payload
then reads as erased and a decision that needs to write a new `prior_authority_data` version
returns not found. Kept fields, including `dataVersion`, are unchanged.

`PriorAuthoritySubmitted` had no content fields before, so a Submitted event stored before this
change has no content row and now fails to load with `MissingEventContentException`. No upcaster
is provided: this layer ships with the stack's single data reset (ADR 0007).
`PriorAuthorityEventContentTest` splits and rejoins both events through `EventJson`, including
`BigDecimal` scale.

### Genesis IDs (ADR 0006)

`WorkItemAssigned` and `WorkItemUnassigned` add `applicationId`;
`PriorAuthorityDocumentTypeUpdatedEvent` adds `parentApplicationId`. Events stored before the
change deserialize with `null`, name no genesis and fold exactly as before, so no reset or
upcaster is needed. `LegacyGenesisPayloadCompatibilityTest` reads the exact old JSON through the
Axon `EventConverter` built by the application's Jackson auto-configuration.
`LegacyGenesisPayloadReplayIntegrationTest` strips the field from rows written to PostgreSQL by
the running service, then loads the aggregates and checks their assignment decisions.

## New event type

Prefer a new event type when the business fact has materially changed. Keep the old handler for
historical events and add a handler for the new event:

```text
OldDecisionRecordedEvent → old historical meaning
ApplicationDecisionMadeEvent → new meaning and fields
```

This is often clearer than making one event schema represent several eras of behaviour.

Do not delete the old class while its event name remains in `payload_type`.

`ApplicationUpdatedEvent` follows this rule for the Axon update route: it records only identifiers,
versions, previous/current status, Application type, and occurrence time. Detailed replacement
content is stored as a new immutable `application_data` version. The external
`ApplicationSubmitted` JSON is a separate integration contract and must not be substituted for the
internal replayable event.

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

Axon writes the `@Event` version to `domain_event_entry.payload_revision`; no event declares one,
so every row has the default `0.0.1`. Nothing reads it to choose a decoder today: the separator
selects the kept-field schema by type name alone, and the current record decodes every stored
row. A first upcaster must make the revision mean something on both halves of the event, the
kept payload and its content row.

## Stored event fixtures

`src/test/resources/stored-events/<event type>/vN.json` holds one stored event for every type in
`EventTypes`: `kept` is the payload row and `content` is the `event_content` row, or `null` for a
type with no content fields. The `v1` fixtures were captured from real appends through the HTTP
API on disposable PostgreSQL, using synthetic data, with UUIDs replaced by sequential ones so that
each history's fixtures share IDs. `StoredEventCompatibilityTest` checks that:

- every event type has a fixture, and every stored field is still a component of the current
  record;
- the type has a content row exactly when it has content fields;
- each fixture decodes into the current record, and every stored value, at any depth, survives
  re-serialising in the same row (kept or content) it was stored in; fields added since may
  appear, and the re-split event passes the kept-field schema;
- with an erased (`{}`) content row each content field loads as erased, matching
  `EventContentSeparator.erase`; a content type with no row is rejected, a content-free one is not;
- the Prior Authority and Application fixtures fold through the real evolve functions to a decided
  aggregate with its content.

The fixtures exist because decoding alone does not notice a changed stored shape. Without an
upcaster, an unknown stored field is ignored, and a missing one, even a required kept field, loads
as `null` or erased without error. A renamed `noteText` loads as erased and silently erases the
application's folded content. Only a changed field type fails, and so does a missing primitive,
because the mapper rejects `null` for primitives.

The policy is:

- never edit, regenerate or delete a fixture: it is the stored form of rows that exist;
- adding a field, at any depth, needs no new fixture: older events lack it, so it loads as `null`,
  erased or its default, and readers must accept that;
- renaming, removing, retyping or reclassifying a stored field fails the test; add an upcaster
  for the old version and a new `vN.json` captured from the new code; every older version must
  still load to the same current record;
- a new event type needs its own `v1.json`, which the coverage check enforces;
- capture fixtures from real appends, not by hand, and use synthetic data only.

### What common changes need

| Change | Kept schema | Event record | Upcaster | New fixture |
|---|---|---|---|---|
| New value in an enum carried as content | — | — | — | — |
| New value in a kept enum (`ApplicationDecisionMade`, work-item events) | Add to `enum`, with review | — | — | — |
| New nested object in a request, returned to clients only | — | — | — | — |
| New field in a content class the aggregate uses for a new rule | — | Add it | — | — |
| New kept field, for a rule that must hold after erasure | Add it as nullable, with review | Add it | — | — |
| Rename a content or kept field | If kept | Rename | Yes | Yes |
| Change a field's type | If kept | Change | Yes | Yes |
| Remove a stored field | If kept | Remove | Yes, to drop it | Yes |
| Move a field between kept and content | Change, with review | Change | Yes, plus migration | Yes |
| New event type | New file | New record with `@Event` name | — | `v1.json` |
| API-only change (computed field, renamed response field) | — | — | — | — |

Notes:

- The full request is stored as content in `serialisedRequest`, so a new request field is
  captured without any event change. Only a read model that must show it changes.
- Evolve functions already handle erased content, so an older event without a new content field
  reads like an erased one. A new kept field is `null` on older events: the evolve function
  treats that as a defined default, or a migration event sets it.
- A renamed content field without an upcaster reads as erased, silently; this is the main thing
  the fixtures exist to catch. `ApplicationAddress` keeps unknown fields, so a renamed address
  field is preserved rather than lost.
- Java enum fields are read with Jackson's defaults, so code from before a new value was added cannot
  read events carrying it. Consider this before relying on a rollback.

The two-release exercise in the event-scoped payload spike depends on this guard: release A's
rows are kept and read by release B, and these fixtures keep that check running for each later
event-format or Axon upgrade.

## Renaming or moving an event

The stored payload type is the explicit `@Event` name, so moving or renaming the Java class does not
change it. Changing the `@Event` name does, and makes historical events unresolvable. Options include:

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

External publication must remain outside tracking handlers. The `ApplicationSubmitted` producer is
a subscribing live-event router that registers an `AFTER_COMMIT` action; projection replay cannot
invoke it.

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

- an old serialized payload fixture: the existing `stored-events` fixtures stay unchanged and
  still pass, and a changed shape adds a new `vN.json` (see [Stored event fixtures](#stored-event-fixtures));
- deserialization or upcasting into the intended current representation;
- aggregate replay followed by a command that relies on restored state;
- projection reset/replay assertions;
- PostgreSQL integration coverage of payload type, revision, and bytes;
- an assertion that prohibited sensitive fields are absent from stored event JSON.

Record a significant event-compatibility strategy in an ADR. See
[Testing Axon code](testing-axon-code.md) for the appropriate test layers.
