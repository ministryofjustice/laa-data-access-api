# ADR 0007: Event content is stored beside its event

- Status: Proposed
- Date: 2026-11-17
- Scope: `data-access-service-axon`

## Related papers

- [ADR 0002][sensitive-data-adr]: sensitive application data is stored as immutable versions
  referenced by thin events.
- [ADR 0003][retention-adr]: application behaviour after retention deletion.
- [ADR 0006][genesis-adr]: every event names its genesis.

This ADR records the first layer of the event-scoped sensitive data work: the foundation, with
no change to API behaviour. Prior art is the spike `spike/event-scoped-pii-payloads`
(`docs/spikes/event-scoped-pii.md`, "Gate 4" and "4b-1"). The observations below refer to
`feat/event-content-foundation`, on top of the lifecycle stream work, not a deployed service.

## Context

Axon's event store is append-only and keeps events for as long as the aggregate exists. Anything
an event carries is kept with it, so an event cannot hold data that must later be erased. ADR 0002
moved most application content into `application_data`, but some events still carried content:
potential duplicates, document metadata, Prior Authority document types and decision details.

We want to move more content into the event stream (drafts, submitted applications, Prior
Authorities), so aggregates and queries can read it from one place. That needs a way to keep
content with its event while still being able to erase it, without weakening the guarantee that
every aggregate loads and decides from what remains.

ADS is not in production. Changing how events are stored is cheap now and expensive after
go-live, when stored formats become contracts.

## Decision

Each stored event is split in two when appended. The event keeps only the fields its schema
lists; everything else is **content**, written to `axon.event_content` keyed by event identifier,
inside Axon's append transaction. Erasing content deletes the row; the event remains.

### Classification rule

An event keeps only:

1. identity and linking keys: aggregate, document, group, caseworker and work-item IDs, the LAA
   reference, and the application IDs its `genesisIds()` derive from (ADR 0006);
2. values a decider rule uses: status, versions, decision outcome, `autoGranted`, schema
   version, request fingerprint, `checksum`, Prior Authority `billingType`;
3. values that decide access or work allocation: `officeCode`, `priorAuthorityType`,
   `workItemType`;
4. the event's own timestamp (`occurredAt`, or `uploadedAt` / `deletedAt` on document events);
5. non-personal decision outcomes.

Everything else is content. Usefulness after erasure is not a reason to keep a field; data that
is needed after erasure needs its own solution.

Content fields are typed `Erasable<T>`. Erased is a real state: reading an erased value throws
`ContentErasedException`, so every use of content is visible. Each event's compact constructor
normalises a missing content field to erased, so an event whose content row is gone still loads.

**Invariant:** with all content erased, every aggregate loads and every command decides as it
would with content. `ErasureReplayTest` folds Application and Prior Authority histories through
the real evolve functions with and without content and compares the states and decider outcomes.

| Event | Content |
|---|---|
| `ApplicationCreated` | `potentialDuplicates` (links this person to other applications). Gains the kept `laaReference`. |
| `ApplicationDocumentUploaded` | `documentType`, `size`, `contentType`, `sourceService`, `fileSuffix` |
| `PriorAuthorityDocumentUploaded` | `size`, `contentType`, `sourceService`, `fileSuffix` |
| `PriorAuthorityDocumentTypeUpdated` | `documentType` |
| `PriorAuthorityDecisionMade` | `decisionJustification`, `amountGranted`, `dateGranted` |
| The other 16 event types | None |

`fileSuffix` can hold part of a filename, so it is content. Once erased, deleting a Prior
Authority document cannot build the SDS filename; that clean-up is best-effort and logged, and
erasure is expected to remove the SDS files itself.

### Kept-field schemas

Every stored event type has an explicit name, `@Event(namespace = "access")`, so the stored type
is `access.<Name>` rather than the Java class name. Each has a JSON Schema at
`src/main/resources/event-schemas/access.<Name>.json` listing exactly its kept fields, with
`additionalProperties: false`. The schema is an allowlist: a field it does not list is content.
Kept fields are validated on append, including formats and enums, so a kept field cannot
silently become free text. An event type without a schema is rejected; there is no report-only
mode. Schema changes change what is kept permanently and need data stewardship review.

Event metadata has the same kind of allowlist: `causationId`, `correlationId`, `X-Service-Name`
and `authenticatedUserId`. The last two identify the actor, not the data subject.

### One mapper

`EventJson` is the one JSON mapper for event payloads and content, and Axon's `EventConverter` is
built from it. The spike found that Axon's own mapper reads decimals in JSON trees as floating
point, so splitting and rejoining lost `BigDecimal` scale (`1234.50` became `1234.5`). `EventJson`
keeps decimals exact through trees. Anything that parses stored payloads or content, including
`LifecycleStreamReader`, uses the same converter.

### Where it hooks into Axon

The content model and port (`Erasable`, `EventJson`, `EventContentSeparator`,
`EventContentStore`, `RegisteredContentReaders`) are framework-neutral, in `eventcontent`. Only
thin adapters know about Axon (`eventcontent.axon`) or JDBC (`eventcontent.jdbc`).

- **Storage engine decorator.** `ContentSeparatingStorageEngine` wraps Axon's storage engine
  through `registerDecorator`. On append it splits each event and writes content in the same
  transaction, so content and event commit or roll back together, including on append
  conflicts. On sourcing it fetches the aggregate's content in one query by tag and rejoins it
  before conversion, so aggregates load whole.
- **Registered content readers.** The event stream used by processors is passed through
  unchanged, so processors receive kept fields with content erased. Only processors on
  `RegisteredContentReaders` (`application-projection` and `prior-authority-projection`) get an
  event source that rejoins content. Each one added must say what content it stores and how
  erasure reaches it. Subscribing processors receive the published event with content, but do
  not read it.
- **Lifecycle stream.** `lifecycle-stream` and application history need no content.
  `LifecycleStreamReader` decodes stored rows as Axon does, so content fields read as erased.

`EventContentArchitectureTest` enforces that every event has a schema matching its kept record
components; only `eventcontent`, `*Evolve`, `ApplicationProjection` and `PriorAuthorityProjection`
read `Erasable` values; only `eventcontent` adapters and processor configuration use the content
store and Axon adapter; and nothing declares snapshots.

### No snapshots

A snapshot would serialise the whole aggregate, content included, into a store erasure does not
reach. Axon 5.4 snapshots are opt-in and none are declared. The spike measured loads of up to 101
events at about 9 ms locally, with history length adding little, so snapshots are not needed.
Snapshots require an erasure design first.

## Alternatives considered

- **Keep all content in side stores (ADR 0002 only):** rejected as the long-term model. Every
  reader must join to the store by version, and content an event does carry is permanent.
- **Split in Axon's serialiser:** rejected. It needs one content query per event and cannot tell
  processors apart.
- **A custom repository:** rejected. It does not cover event processors.
- **Configure only the split's mapper, or split raw bytes:** rejected in favour of one mapper we
  own, so Axon and the split cannot disagree.
- **A report-only mode for unclassified events:** rejected. Every type is classified from the
  data reset.

## Consequences

- **One-off data reset.** Stored type names change from Java class names to `access.<Name>`,
  and Axon's mapper changes, so events stored before this change no longer load. ADS has no
  production data. The whole stack (genesis IDs, lifecycle stream, event content) is deployed
  together with one reset of each environment's `axon` schema: the event store, processor
  tokens, projections, lifecycle stream and the sensitive-data stores (`application_data`,
  drafts, Prior Authority data) are emptied together. Migrations contain no destructive SQL;
  `V36__create_event_content.sql` only creates the content table.
- After go-live, every stored format becomes a contract needing upcasting or migration.
- New event types need an explicit `@Event` name, a schema and a classification review; the build
  enforces the first two.
- Every aggregate load runs one indexed content query, and each event streamed to a registered
  content reader runs one. Batch these only if measurements show a need.
- The design relies on Axon's decorator hook, its tags and sourcing criteria, and the observed
  behaviour that the JPA engine appends inside a Spring transaction. The framework-neutral port
  limits how much would move if that changed.
- `ApplicationHistoryMapper` reads only kept fields, including `PriorAuthoritySubmitted`'s
  `priorAuthorityType` and the work-item events' `applicationId`.

## Later layers

This layer moves no content into events that was not there already. Later layers:

1. move draft content into events and remove `ApplicationDraftStore`;
2. move submitted content into events, read application details from the aggregate and remove
   `ApplicationDataStore`;
3. do the same for Prior Authority drafts, submissions and decisions;
4. define erasure and its recorded state.

When the old stores are gone, ADR 0002 is superseded.

## Reconsider when

- A rule needs to decide from content, or a kept field turns out to be personal data.
- Content or history grows enough that load cost matters.
- Snapshots are needed.
- Axon changes the storage engine, its transaction behaviour or its sourcing criteria.

[sensitive-data-adr]: 0002-separate-sensitive-data-from-domain-events.md
[retention-adr]: 0003-define-application-behaviour-after-retention-deletion.md
[genesis-adr]: 0006-every-event-names-its-genesis.md
