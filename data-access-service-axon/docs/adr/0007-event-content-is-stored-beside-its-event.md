# ADR 0007: Event content is stored beside its event

- Status: Proposed
- Date: 2026-11-17
- Updated: 2026-11-24
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

### Why content moves out of the state stores

Today events record what happened, but most content is not in them. It lives in stores that hold
state, not events:

- **Drafts are mutable current state.** `application_draft` and `prior_authority_draft` are
  overwritten in place. A draft event says that something changed, but not what, and the earlier
  content is lost.
- **Submitted content is whole-body versions.** `application_data` and `prior_authority_data`
  store a complete body per data version, and events point at a version instead of carrying the
  facts they introduced.

As a result:

- **The event stream is not the source of truth for content.** Aggregates and read models cannot
  be rebuilt from events alone; replay depends on the side store still holding the pinned version.
- **Concurrency has several versions.** The stream version, the drafts' optimistic-lock versions
  and the data version sequence are separate, alongside the public version.
- **Every change rewrites the whole body.** Updates, decisions and notes each read and append a
  complete new version.
- **Personal data is copied into every version.** Erasure means finding and rewriting every
  version.

**Before:** content is held as state beside the events.

```mermaid
flowchart LR
  cmd[Command handlers]
  subgraph write[Written by commands]
    events[(domain_event_entry<br/>control facts<br/>+ data version pointer)]
    draft[(application_draft<br/>prior_authority_draft<br/>mutable, overwritten)]
    data[(application_data<br/>prior_authority_data<br/>whole body per version)]
  end
  subgraph read[Projections]
    cur[(application_current_state<br/>prior_authority_current_state)]
    life[(lifecycle_stream)]
  end
  cmd -->|append| events
  cmd -->|upsert| draft
  cmd -->|read + append new version| data
  events --> cur & life
  data -.->|looked up by version| cur
  api[GET details / history] --> cur & life
```

ADS is not in production. Changing how events are stored is cheap now and expensive after
go-live, when stored formats become contracts.

## Decision

Each stored event is split in two when appended. The event keeps only the record components
marked `@RetainedField`; everything else is **content**, written to `axon.event_content` keyed by
event identifier, inside Axon's append transaction. Erasure replaces the row's content with `{}`
and records when; the row and the event remain.

The direction is that **each event carries the content it introduced**, beside it in
`event_content` rather than inside it, so erasure can replace content without rewriting immutable
events. The stream becomes the ordered history of content as well as control facts. This layer
builds the mechanism and moves the content events already carry; later layers move drafts and
submitted content onto it. Even then `application_data`, `prior_authority_data` and the
`prior_authority_draft` read copy are still written and GET details still read the current-state
projections; removing them is later work.

**After this stack:** events carry their content; the data stores remain until later work.

```mermaid
flowchart LR
  cmd[Command handlers]
  subgraph write[Written atomically per event]
    events[(domain_event_entry<br/>kept fields)]
    content[(event_content<br/>content per event<br/>erased_at)]
  end
  data[(application_data<br/>prior_authority_data<br/>prior_authority_draft read copy<br/>until later work)]
  agg[Aggregate fold<br/>events + content]
  cur[(current-state projections)]
  life[(lifecycle_stream<br/>kept fields only)]
  cmd -->|append| events
  cmd -->|append| content
  cmd -.-> data
  events & content --> agg
  events & content --> cur
  events --> life
  api[GET details / history] --> cur & life
```

This has costs: reads must join the two tables consistently, content needs a schema-evolution
policy like kept fields, and aggregates fold content at load time.

### Erasure keeps the row

Direction agreed on 2026-11-09:

- **Soft deletion will be an event.** An erasure request will raise a soft deletion that folds
  and read models treat as erased while the content stays stored.
- **Hard erasure will run automatically** after a retention period. It replaces the row's content
  with `{}` and stamps `erased_at`, once. The row is kept. No other replacement is used: content
  fields are nullable, and a field absent from the row loads as `null`, which evolve treats as
  erased.
- **A missing row is an error, not erased,** whenever the event type has content fields: record
  components not marked `@RetainedField`. Readers that join content throw
  `MissingEventContentException`. A field absent from an existing or erased row still reads as
  erased.

This layer adds only the store mechanism: `event_content.erased_at`, a trigger in
`V36__create_event_content.sql` that rejects deletes and every update except that single erasure
transition, and `EventContentStore.erase(eventIds)`, which always writes `{}`. The soft deletion
event, retention period, scheduler and any erasure endpoint are later work.

### Classification rule

An event keeps only:

1. identity and linking keys: aggregate, document, group, caseworker, work-item and proceeding
   IDs, the LAA reference, and the application IDs its `genesisIds()` derive from (ADR 0006);
2. values a decider rule uses: status, versions, decision outcome, `autoGranted`, schema
   version, request fingerprint, `checksum`, Prior Authority `billingType`;
3. values that decide access or work allocation: `officeCode`, `priorAuthorityType`,
   `workItemType`;
4. the event's own timestamp (`occurredAt`, or `uploadedAt` / `deletedAt` on document events);
5. non-personal decision outcomes, including the decision value for each proceeding, which
   informs retention schedules.

Everything else is content. Usefulness after erasure is not a reason to keep a field; data that
is needed after erasure needs its own solution.

Content fields on events are plain types marked `@Nullable`; `null` means absent or erased. An
event therefore loads whether its row holds the original content or `{}` after erasure, and when
read with kept fields only. Evolve functions convert each content field once, with
`Erasable.of(...)`, into `Erasable<T>` in aggregate state (`ApplicationState`,
`PriorAuthorityState`). Erased is a real state there: reading an erased value throws
`ContentErasedException`, so every use of content by a command is visible. Events never hold
`Erasable`, and projections that read content handle `null` themselves. A record's default
`toString()` would print its content, so every event with content fields overrides it with
`RedactedEventString`, which prints retained values and `<content>` in place of any content value;
logging an event, or an Axon message describing it, never writes personal data.
`EventContentArchitectureTest` fails the build if an event with content fields does not declare that
override, and `ReportingErrorHandler` reports only the processor name and the number of failed
events, never the event message.

**Invariant:** with all content erased, every aggregate loads and every command decides as it
would with content. `ErasureReplayTest` folds Application and Prior Authority histories through
the real evolve functions with and without content and compares the states and decider outcomes.
A draft is an exception: its content is what submit turns into an Application or Prior Authority.
If the current draft body is erased, commands needing that draft return not found. PA commands never rebuild a missing draft from a read-side copy.

Note, decision, auto-grant outcome, update and mark-ready commands that need to write a new
`application_data` version treat erased submitted content as missing (`EntityNotFoundException`,
HTTP 404). If a manual or automatic outcome was already recorded, no new version is needed:
matching retries still succeed without writing, and conflicting outcomes still return 409.
The guard recognises submission by the `ApplicationCreated` event, not by a non-null status;
an open draft has no submitted payload to erase.

A Prior Authority decision is never idempotent: deciding a decided Prior Authority is a 409.
Making a decision therefore runs every decider rule (validation, version, status and assignment)
from kept fields first, and only when a `PriorAuthorityDecisionMade` event is to be written does it
need the submitted payload to build the next `prior_authority_data` version. If the
`PriorAuthoritySubmitted` content has been erased, that write returns not found
(`EntityNotFoundException`, HTTP 404) and nothing is written; every validation and conflict
outcome is unchanged. Erasing only a decision's content leaves the submitted payload in aggregate
state with the outcome and empty decision details, because a decision with no optional details
cannot be told apart from an erased one.

| Event | Content |
|---|---|
| `ApplicationCreated` | `content` (parsed application content), `potentialDuplicates` (links this person to other applications). Gains the kept `laaReference` and `proceedingIds`. |
| `ApplicationUpdated` | `content`. Gains the kept `proceedingIds`. |
| `NoteCreated` | `noteText` |
| `ApplicationDecisionMade` | `meritsDecisions`, `certificate`, `eventDescription`. Gains the kept `decisionRequestFingerprint` and `proceedingDecisions`. |
| `ApplicationDraftStarted` | `applicationContent`, `potentialDuplicates`. Gains the kept `status` and `laaReference`. |
| `ApplicationDocumentUploaded` | `documentType`, `size`, `contentType`, `sourceService`, `fileSuffix`, `originalFilename` |
| `PriorAuthorityDraftStarted`, `PriorAuthorityDraftUpdated` | `content` |
| `PriorAuthorityDocumentUploaded` | `size`, `contentType`, `sourceService`, `fileSuffix`, `originalFilename` |
| `PriorAuthorityDocumentTypeUpdated` | `documentType` |
| `PriorAuthoritySubmitted` | `content` (the submitted Prior Authority content). Keeps `dataVersion` and `billingType`. |
| `PriorAuthorityDecisionMade` | `decisionJustification`, `amountGranted`, `dateGranted`, `expertFee` (including any granted-time override), `disbursementInformation`, `apportionmentInformation`. Keeps `dataVersion` and `overallDecision`. |
| Other event types | None |

Raw requests are not stored in events. `serialisedRequest` was removed from every event, from
aggregate state and commands, and from the `application_data`, `prior_authority_data` and
`prior_authority_draft` payloads before any deployment: no audit or legal need was found for it,
and it repeated the parsed content. Where a retry must be recognised, the controller mapper
computes a request fingerprint and the command carries it (`CreateApplicationCommand`,
`CreateApplicationDraftCommand`, `MakeApplicationDecisionCommand`,
`RecordAutoGrantedOutcomeCommand`); submitting a draft uses the draft's kept fingerprint. The kept
`requestFingerprint` and `decisionRequestFingerprint` hold the same values as before. The
`payload_hash` columns, which nothing read, were dropped by `V40__drop_payload_hash_columns.sql`.

`fileSuffix` can hold part of a filename, so it is content. Once erased, deleting a Prior
Authority document cannot build the SDS filename; that clean-up is best-effort and logged, and
erasure is expected to remove the SDS files itself.

### Retained fields

Every stored event type has an explicit name, `@Event(namespace = "access")`, so the stored type
is `access.<Name>` rather than the Java class name. Each event is a record, and each component it
keeps is annotated `@RetainedField`. The annotation is an allowlist: a component without it is
content. `EventContentSeparator` takes the retained components of every `@Event` class found by
`EventTypes`, and rejects an event type it does not know; there is no report-only mode.

A retained field must be non-null on append unless it is marked `@RetainedField(nullable = true)`.
`EventContentSeparator` rejects an event whose required retained field is null or absent with
`KeptFieldViolationException`, so the append fails before anything is written. Primitive
components are never null and are never marked nullable. The check runs only when an event is
split on append, so stored events that lack a field added later still load.

`EventContentArchitectureTest` checks that every event is a record, that its retained components
use supported value types, that no primitive retained component is marked nullable, that the
nullable retained components match the list pinned in the test, and that no event component is
`Erasable`. Apart from the null check, retained values are not validated on append: their Java
types are the check, so a retained field must not be free text. Adding, removing, retyping or
changing the nullability of a retained field changes what is kept permanently and needs data
stewardship review.

Kept fields were first listed in one JSON Schema resource file per event type. The schemas
repeated the record in a second file that could drift from it, so they were replaced by the
annotation, which keeps the rule on the field it applies to. The schemas also rejected a null kept
field unless its type allowed `null`; `nullable = true` marks exactly those fields.

Event metadata has the same kind of allowlist: `causationId`, `correlationId`, `X-Service-Name`
and `authenticatedUserId`. The last two identify the actor, not the data subject.

### What has a schema

This design adds no schema file. Kept fields are the record components marked `@RetainedField`,
and content is whatever else the event carries. The other shapes already existed and change
under their own rules.

| Shape | Defined by | Depended on by | How it changes |
|---|---|---|---|
| API requests and responses | OpenAPI under `data-access-api/` | Clients | Coordinated with clients, or deprecated before removal |
| Pact contracts | Consumers' tests, verified in `pactTest` | Clients | Follows the API; Pact never sees events |
| Commands and domain classes | Java | This service only | Refactored freely, unless serialised into events or state stores |
| Kept event fields | `@RetainedField` components of the event record | Stored history, forever | Additive where possible; otherwise an upcaster, with data stewardship review |
| Event content | The event record's other components, all `@Nullable` | Aggregates and `@ReadsEventContent` projections | An upcaster when a field is renamed or retyped; erasure blanks it |
| Read models | Projections | API reads | Rebuilt by replay; no compatibility promise |

The API and event shapes are deliberately separate, so a client-facing rename need not rewrite
stored history, and an event change need not break clients. The risk to watch is a domain class
used directly in both a request and an event: renaming one of its fields changes stored data
too. Kept fields are reviewed on purpose, because they are the permanent record. Content is loose
by default, but a renamed content field in old events reads as erased unless an upcaster maps
it.

### One mapper

`EventJson` is the one JSON mapper for event payloads and content, and Axon's `EventConverter` is
built from it. The spike found that Axon's own mapper reads decimals in JSON trees as floating
point, so splitting and rejoining lost `BigDecimal` scale (`1234.50` became `1234.5`). `EventJson`
keeps decimals exact through trees. Anything that parses stored payloads or content, including
`LifecycleStreamReader`, uses the same converter.

### Where it hooks into Axon

The content model and port (`Erasable`, `RetainedField`, `ReadsEventContent`, `EventJson`,
`EventContentSeparator`, `EventContentStore`) are framework-neutral, in `eventcontent`. Only
thin adapters know about Axon (`eventcontent.axon`) or JDBC (`eventcontent.jdbc`).

- **Storage engine decorator.** `ContentSeparatingStorageEngine` wraps Axon's storage engine
  through `registerDecorator`. On append it splits each event and writes content in the same
  transaction, so content and event commit or roll back together, including on append
  conflicts. On sourcing it reads the aggregate's events first, then fetches their content in one
  query by event identifier (`EventContentStore.findByEventIds`) and rejoins it before
  conversion, so aggregates load whole. Each event read was committed with its row, so no event
  can be missing from that query. Content was first found by stream tag before the events were
  read; that needed a second lookup for events committed in between, and a copy of the tags on
  every row. `V39__drop_event_content_tags.sql` drops that `tags` column, its index and the
  trigger's check on it.
- **Content-reading projections.** The event stream used by processors is passed through
  unchanged, so processors receive kept fields with content fields `null`. Only a projection
  class marked `@ReadsEventContent` (`ApplicationProjection` and `PriorAuthorityProjection`) gets
  an event source that rejoins content by event identifier, and fails on a missing row as
  sourcing does. Processor configuration reads the annotation, so the rule sits on the class it
  describes. Each projection marked must say what content it stores and how erasure reaches it.
  Subscribing processors receive the published event with content, but do not read it.
- **Lifecycle stream.** `lifecycle-stream` and application history need no content.
  `LifecycleStreamReader` decodes stored rows as Axon does, so content fields are `null`.

`EventContentArchitectureTest` enforces that every event is a record whose `@RetainedField`
components use supported types, with `nullable = true` only on the pinned non-primitive
components, and which has no `Erasable` component; only `eventcontent` and
classes named `*Evolve`, `*State`, `*Decider`, `*CommandHandler` or `*CommandHandlerSupport` read
`Erasable` values; only `eventcontent` adapters and processor configuration use the content
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
- **Encrypt content in the event and destroy the key (crypto-shredding):** rejected, as in ADR
  0002. Destroying a key is a recognised way to put data beyond use, but the ciphertext stays in
  the event store and every backup indefinitely, so erasure depends on the key never resurfacing
  from a backup, a leak or future cryptanalysis. It needs per-subject key ownership, rotation,
  backup, audit and partial decryption, and it does not reach the plaintext copies in
  projections, read models and SDS, which need erasing anyway. Crypto-shredding is usually chosen
  because events cannot be changed; keeping content in its own row lets us blank it directly.
- **A JSON Schema of kept fields per event type:** used first, then replaced by
  `@RetainedField`. The schema repeated the record in a second file that could drift from it.
- **A list of content-reading processor names:** used first, then replaced by
  `@ReadsEventContent` on the projection class, so the review note sits beside the code it covers.
- **Keep the raw request as event content:** rejected. No audit or legal need was found, it
  repeated the parsed content, and a kept fingerprint is enough to recognise a retry.

## Consequences

- **One-off data reset.** Stored type names change from Java class names to `access.<Name>`,
  and Axon's mapper changes, so events stored before this change no longer load. ADS has no
  production data. The whole stack (genesis IDs, lifecycle stream, event content) is deployed
  together with one reset of each environment's `axon` schema: the event store, processor
  tokens, projections, lifecycle stream and the sensitive-data stores (`application_data`,
  drafts, Prior Authority data) are emptied together. Migrations delete no rows;
  `V36__create_event_content.sql` only creates the content table and its guard trigger. As V36
  has not been deployed, the erasure column and trigger are part of it rather than a new
  migration. Later changes add new migrations rather than editing V36 to V38:
  `V39__drop_event_content_tags.sql` drops the content `tags` column and its index, and
  `V40__drop_payload_hash_columns.sql` drops the unused `payload_hash` columns from
  `application_data`, `prior_authority_data` and `prior_authority_draft`.
- After go-live, every stored format becomes a contract needing upcasting or migration. Both
  rows of every event type are pinned by a stored-event fixture that is never edited once
  deployed. Before any deployment, the `v1` fixtures were edited once, in place, to remove
  `serialisedRequest` from their content rows. Adding a field needs nothing more; a renamed,
  removed, retyped or reclassified field needs an upcaster and a new fixture version (see
  [Stored event fixtures](../event-evolution.md#stored-event-fixtures)). Decoding alone would not
  notice: unknown fields are ignored and missing ones load as null or erased.
- New event types need an explicit `@Event` name and a classification review of which components
  are marked `@RetainedField`. The build enforces the name, the record shape and the retained
  field types, not the classification.
- Every aggregate load reads its events, then runs one content query by event identifier on the
  `event_content` primary key. Each event streamed to a projection marked `@ReadsEventContent`
  runs one lookup by event identifier. Batch these only if measurements show a need.
- A lost content row stops the aggregate loading and stalls a content-reading processor, rather
  than silently reading as erased.
- The design relies on Axon's decorator hook, its tags and sourcing criteria, and the observed
  behaviour that the JPA engine appends inside a Spring transaction. The framework-neutral port
  limits how much would move if that changed.
- `ApplicationHistoryMapper` reads only kept fields, including `PriorAuthoritySubmitted`'s
  `priorAuthorityType` and the work-item events' `applicationId`.

## Later layers

This layer moves no content into events that was not there already. Later layers:

1. move draft content into events and remove `ApplicationDraftStore` (done: Application draft
   content and upload filenames are event content; submit and the upload pre-check read the draft
   from the aggregate, and the projection records draft filenames for document downloads);
2. move submitted content into events (done: created, updated, note and decision content is event
   content, and command handlers build the next `application_data` version from aggregate state;
   `application_data` is still written and still serves API reads, projections and history
   descriptions); reading application details from the aggregate and removing
   `ApplicationDataStore` is later work;
3. PA drafts and document mutations now fold from event content. Create, replace, upload, type
   change and delete still maintain `prior_authority_draft` in the command transaction so existing
   projection-backed GETs see the same content and immediate draft-body updates. Submitting reads
   the aggregate, writes the existing immutable `prior_authority_data` version and deletes the
   draft read copy. The public GETs and submission readiness wait have not moved. There is no new
   migration here: retiring the draft table requires a replacement with equivalent read semantics.
   PA submitted and decision content is now event content too: `PriorAuthoritySubmitted` carries
   the submitted content, `PriorAuthorityDecisionMade` carries the full decision details, and the
   decision handler builds the next `prior_authority_data` version from aggregate state instead of
   reading it. PA content no longer carries the raw request. `prior_authority_data` is still
   written and still serves the public GETs, projections and history descriptions, and the
   `prior_authority_draft` read copy is still written; reading Prior Authority details from the
   aggregate and removing `PriorAuthorityDataStore` and `PriorAuthorityDraftStore` is later work;
4. build erasure on the store mechanism: the soft deletion event, the retention period and the
   automatic hard erasure;
5. pin the stored form (done: `stored-events/<type>/v1.json` holds the kept row and content row
   of every event type, captured from real appends; `StoredEventCompatibilityTest` checks each
   still separates, decodes and re-serialises unchanged, loads with an erased content row, and
   that the Prior Authority and Application histories fold to a decided aggregate). The first
   upcaster, and a decoder chosen by `payload_revision`, come with the first changed shape.

When the old stores are gone, ADR 0002 is superseded.

Erasing event-content rows alone is **not** PA hard erasure. A retention design must also reach
the draft read copy, submitted versions, projections and SDS. No historical event is rewritten.

## Reconsider when

- A rule needs to decide from content, or a kept field turns out to be personal data.
- Content or history grows enough that load cost matters.
- Snapshots are needed.
- Axon changes the storage engine, its transaction behaviour or its sourcing criteria.

[sensitive-data-adr]: 0002-separate-sensitive-data-from-domain-events.md
[retention-adr]: 0003-define-application-behaviour-after-retention-deletion.md
[genesis-adr]: 0006-every-event-names-its-genesis.md
