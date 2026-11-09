# Spike: event content and aggregate-level projection

- Date: 2026-10-30
- Status: investigation; not an approved architecture or production migration
- Baseline: local `main` at `13b78ff2ae32de03e7506c1eacbfbeecb511e236`
- Branch: `spike/event-scoped-pii-payloads`
- Ticket: `ministryofjustice/laa-dstew-workbench`,
  `tickets/2026-10-21-spike-event-scoped-sensitive-payloads.md`

## Tension

A small sensitive change currently requires reading and rewriting an entire
resource payload. Mutable drafts have their own optimistic-lock revision, in
addition to aggregate stream concurrency. Submitted payload versions introduce
another version concept, distinct from both stream position and the public
application version.

Explore storing sensitive facts beside the event that introduced them, then
building one enriched current-state read model per aggregate instance. Keep
command aggregate state and replay independent of the sensitive store. Make the
owning stream's expected version the concurrency authority for ordinary writes.

## Baseline inspected

These are source observations at the recorded commit, not deployed-environment
claims.

| Path | Current behaviour |
|------|-------------------|
| Application draft create | `CreateApplicationDraftCommandHandler` validates content and writes an `ApplicationDraftPayload` before appending `ApplicationDraftStartedEvent`. |
| Application document upload | `ApplicationDocumentUploadCommandHandler` checks sourced draft state and duplicate document IDs, reads the draft, replaces its filename map via `ApplicationDraftStore.upsert`, and appends a filename-free upload event. |
| Application submit | `SubmitApplicationDraftCommandHandler` reads the draft, prepares the full submitted payload, copies saved filenames into `application_data` version 0, appends `ApplicationCreatedEvent`, and deletes the draft. The submit decider takes no current aggregate state. |
| PA draft update | `UpdatePriorAuthorityDraftCommandHandler` reads the existing draft to preserve type and other content, replaces its body and appends a draft-updated event. |
| PA submit | `SubmitPriorAuthorityDraftCommandHandler` reads and validates the draft, appends PA data version 0, pins the parent's latest application-data version, emits the submitted event and deletes the draft. |
| Draft locking | `ApplicationDraft` and `PriorAuthorityDraft` both have JPA `@Version` fields, introduced by migration V28. |
| Submitted payloads | `ApplicationDataStore` appends complete immutable bodies under `(applicationId, version)`; these are authoritative records, not disposable projections. |
| Aggregate replay | `ApplicationAggregate` delegates event transitions to `ApplicationEvolve`; sensitive content is not loaded by sourcing handlers. |
| Read assembly | `ApplicationProjection` delegates detail hydration to `ApplicationReadModelAssembler`; current-state rows are not complete sensitive resource bodies. |
| Retries | `RetryingCommandDispatcher` retries a concurrent-write failure once. The declaration contract below must preserve the client's reviewed version across retries, never silently submit a newer draft. |

The existing public application/assignment versions and pinned historical payload
references are compatibility obligations. Removing redundant PII-row concurrency
does not automatically allow these contracts to be removed or reinterpreted.

## Declaration and submission contract

**Agreed requirement, 2026-10-30:** the declaration page must show the
authoritative application that the client is submitting. A draft GET returns
the complete content plus its owning stream version. Any persisted declaration
candidate must be immutable.

Successful submission must store exactly that reviewed content. If the draft
changes after it was displayed, reject the stale submission and require the
updated application to be displayed and declared again. In particular:

- Content, document uploads and filename changes all participate in stream
  concurrency; none may bypass the declaration's expected-version check.
- A retry must preserve the reviewed candidate and expected version. It must
  not silently advance the expected version or substitute freshly assembled
  content, even if the newer content is valid.
- New submission timestamps and control facts belong separately from the
  declared content; they must not silently change the application the client saw.
- Aggregate command decisions remain based on sourced control state. Content
  assembly and validation may use authoritative content, not a lagging query
  projection.

The preferred vocabulary is **event content** (`event_content`) and **aggregate
control state**. Neither label implies that the event stream is free of personal
data. The experiment now uses `spike.event_content`; its original branch, file
and class names are retained for traceability.

**Working direction:** complete submitted event content becomes a reconstruction
checkpoint. Domain events are retained. The mutable draft and redundant earlier
draft event content can be removed only once lagging projectors and rebuilds can
start content reconstruction from that checkpoint without requiring the removed
content. Earlier draft bodies would intentionally no longer be reproducible.
This is compaction, not erasure of information retained in submitted content.

The isolated experiment now proves immutable declaration candidates,
version-bound reads/submission and checkpoint-aware compaction. Its explicit
prepare command is a prototype design choice, not an agreed production endpoint.
Production version transport and API integration remain to be designed.

## Architecture review, 2026-10-30

A principal-architect review of the experiment concluded **proceed with
changes**. The mechanism is sound for its scope. Atomic event/content writes,
stream-version-only command concurrency and checkpoint-based rebuild are
demonstrated against real Axon and PostgreSQL. However, the experiment was
narrower than the production domain. The review findings are, in priority order:

1. **The persisted declaration candidate is probably redundant.** Event content
   is immutable and reconstruction is deterministic, so content at stream
   version N is already a fixed copy. The prepare command adds an API, two
   extra full copies and a version bump; two tabs preparing unchanged content
   invalidate each other. Compare a side-effect-free review returning version
   plus content hash. *Follow-up implemented below.*
2. **Full-body draft edits were unmodelled.** Only filename deltas were tested,
   yet ordinary draft edits replace the whole body. *Follow-up measured below.*
3. **Post-submission edits are out of scope.** Production `UpdateApplication`,
   `MakeApplicationDecision`, `CreateNote`, `MarkApplicationReady` and
   `RecordAutoGrantedOutcome` handlers read and append whole
   `ApplicationDataStore` versions. That write amplification is untouched.
4. **Pinned-version consumers are unaddressed.** `WorkListProjection`,
   `ApplicationListIndexProjection`, `ApplicationGroupRouteProjection`,
   `ApplicationHistoryAssembler` and `IndividualsQueryHandler` resolve content by
   `applicationDataVersion`. One current-state row per aggregate does not serve
   them, particularly history. Define version-addressable content access, or
   redesign each consumer, before choosing the read-model shape.
5. **Compaction has no consumer gate.** Checkpoint promotion is safe only for
   this projection. Compaction must wait until every content-reading processor
   has passed the submission position and no reset/replay is in progress.
   Nothing triggers compaction automatically.
6. **The read side couples to Axon storage internals.** It queries
   `axon.domain_event_entry`, depends on payload-type naming and serialisation,
   and adds a foreign key to Axon's table. Production should use Axon sourcing
   APIs or an incremental projector.
7. **Versions are embedded in event payloads.** This spike shortcut should come
   from event metadata. Making aggregate stream version the sole concurrency
   authority may also constrain later adoption of Axon 5 tag-based dynamic
   consistency boundaries; record this in the ADR discussion.

Minor: the trigger permits DELETE plus re-INSERT, so immutability relies on
database privileges not yet designed. Document deletion and create-request
fingerprint idempotency are not modelled. The ordinary draft-editing read path
and its read-after-write behaviour are undefined.

### Review follow-up evidence

**Hash-bound review (no write).** `Store.review` reconstructs the latest draft
from authoritative history in one statement and returns its stream version and
a SHA-256 hash over canonical content (sorted filename map, explicit schema
version). It appends nothing. `SubmitReviewed(version, hash)` sources the
aggregate, reconstructs at that version, compares the hash and stores the
content beside the submission event.

The test shows repeated reviews are identical and write nothing, and a wrong
hash is rejected without an event. It also shows that a submit paused before
append, with an upload accepted meanwhile, fails stale after the real
`RetryingCommandDispatcher` retry. A fresh review yields a new version and hash.
The old hash is rejected at the new version. Submitted content decodes equal to,
and hashes identically to, the reviewed content.

The hash also detects a reconstruction-logic change between review and submit
(for example a deployment), turning silent divergence into a rejected submit.
Compared with the persisted candidate, this needs no prepare API or extra copies,
and concurrent tabs do not invalidate one another. The trade-off is reconstruction
cost on each review and submit, bounded by the latest checkpoint. Production
needs a stable canonical-form specification and hash versioning. The prepare
path remains in the spike for comparison.

**Full-body draft edits.** `UpdateDraft` appends a body-replacement event with
the complete body as event content; filenames are preserved. With 40 edits of a
synthetic, deliberately incompressible body of about 16 KB:

| Point | Content rows | Content JSON bytes | Stored bytes (`pg_column_size`) | Domain event bytes |
|-------|-------------:|-------------------:|--------------------------------:|-------------------:|
| After 40 edits | 41 | 674,137 | 674,307 | 2,851 |
| After submit | 42 | 690,604 | 690,783 | 2,920 |
| After compaction | 1 | 16,467 | 16,476 | 3,012 |

Before compaction, draft content grows linearly with edits (about 41 times the
current mutable-draft row here). Compaction returns it to one submitted body.
Domain events stay around 70 bytes each. Real payloads compress differently and
this does not measure latency; treat it as an order-of-magnitude indication.
Abandoned drafts never compact, so retention for unsubmitted drafts needs a
policy. Patch-based draft deltas or rolling draft checkpoints are alternatives if
pre-submission growth is unacceptable.

## First runnable experiment

All experimental Java and SQL live in `src/integrationTest`; nothing is added to
the production JAR, API, Flyway migrations or production Spring configuration.
The experiment uses the application's real Axon/Spring transaction wiring, real
event-store migrations and a disposable PostgreSQL 17 Testcontainer. A separate
`spike` schema contains only the experimental sidecars and read model.

```text
Start draft / upload / rename / prepare declaration / submit / review / compact
  -> thin event + optional event-ID-keyed event content
       -> Axon-sourced aggregate control state, without application content
       -> one aggregate-level streaming projection
            -> one current-state JSON row
```

The small experimental Application has lifecycle, office, document IDs/types and
one stream version. Its synthetic commands are not production API
contracts. Content validation is deliberately minimal, not the real application
JSON schema or semantic parser.

- Draft start records synthetic content beside its event.
- Upload records document identity/type on the event and just `fileName` in the
  sidecar. Rename records the revised filename only.
- An application has multiple documents. Filenames are accumulated in a map
  keyed by document ID, not one application-wide filename. A rename event's
  document ID identifies which entry its sidecar replaces; other documents'
  filenames and metadata are preserved, and earlier stream positions retain
  their earlier names.
- Prepare declaration reconstructs authoritative history through the sourced
  stream version, validates it and appends a complete immutable candidate.
- Draft GET returns that candidate and its stream version from authoritative
  storage, even with the projector stopped. It does not append events or write
  content. An unprepared or subsequently changed draft requires preparation
  again before this declaration read can succeed.
- Submit checks the reviewed expected version and draft lifecycle, validates the
  saved candidate, then copies its JSONB unchanged beside the submission event
  using `INSERT ... SELECT`. It neither reassembles newer content nor consults
  the asynchronous read model.
- Review introduces no application content and therefore has no content row.
- A separate compaction command retains the complete submitted checkpoint,
  deletes earlier draft content and appends a content-free compaction fact in
  the same transaction. All domain events remain.
- Domain state uses one shared pure event fold for sourcing and read-side
  reconstruction. Concrete Axon handler methods delegate to it.

### Identity and transactions

The handler allocates a `GenericEventMessage` before either write, uses its
identifier as the sidecar primary key, then passes that same message to
`EventAppender`. Tests join the sidecar to the actual persisted event identifier.
The sidecar has no independent optimistic-lock version.

A deferred foreign key from the sidecar to the event permits sidecar-first writes
within one database transaction. A required sidecar's absence is also detected
by reconstruction: the foreign key alone cannot enforce event-to-sidecar
completeness. The physical schema and database access controls remain proposals.

The test schema rejects ordinary content updates with a database trigger. It
permits only a one-way explicit erasure transition to a null body and an erasure
timestamp; deletion remains available for compaction. This is not a complete
production immutable-data permissions model: privileged deletion/reinsertion
and erasure authorisation still need access controls.

### Projection and stream position

One projection component handles all seven event types and serialises an
explicit `View(State, Content)` into `spike.application_view`. Normally it
reconstructs through the processed event's version. An upsert refuses to replace
a newer read-model row with an older one.

After compaction, an old draft event cannot reconstruct its former content.
For this **current-state projection only**, processing such an event instead
initialises both control state and content at the submitted checkpoint. It does
not combine old control state with submitted content or jump to unrestricted
latest state. Later events advance from that coherent position. In the test,
handling draft event 0 produces submitted state/content at version 5, not the
later review at version 6; normal processing and reset/rebuild reach version 7.
This checkpoint promotion is not suitable for consumers needing every historical
draft body or per-event side effects. Other consumers must be assessed separately
before enabling compaction.

Direct historical-content requests before the submission checkpoint fail with
an explicit `DraftHistoryCompacted` error. Domain-only replay still works at
those positions. Missing or erased submitted checkpoint content is an error,
not permission to fall back to an older body.

Reconstruction loads events, content and any compaction marker together in one
joined SQL statement. PostgreSQL's statement-level snapshot therefore sees
either the history before compaction or the committed marker and retained
checkpoint, rather than a mixture caused by separate content reads.

This first experiment reconstructs history for each handled event. It therefore
has O(n) per-event reconstruction and O(n^2) work for a complete n-event rebuild,
although the earlier per-sidecar queries have been replaced by that single
joined history query. A checkpoint avoids needing older bodies, not loading
the domain history. This remains a correctness reference, not a performance
recommendation. Compare it with an incremental enriched projector before
choosing the production implementation.

The experimental version is checked against the event store's persisted
`sequence_number`. That equality is proved here, not assumed for existing
`applicationVersion`, `applicationDataVersion` or assignment versions.

### Concurrency evidence boundary

Upload commands deliberately use document ID as their local sequencing/routing
key while `@InjectEntity` still loads the owning Application by application ID.
This lets two commands load the same stream version in parallel, rather than
being serialised by Axon's in-process command sequencer. A barrier gates them
after sidecar insertion and before event append.

This exercises the actual PostgreSQL stream conflict and sidecar rollback. It is
not a multi-pod routing or failover test and does not propose changing production
command routing.

Another test pauses a submission after loading its reviewed candidate, accepts
an upload at the same expected version, then releases the submission. Its append
conflicts and the real `RetryingCommandDispatcher` retries with the original
expected version. That retry fails stale; no submission or orphan content
commits. A fresh prepare/read/declaration is required.

### Erasure boundary

A sidecar may be present, unexpectedly missing, or explicitly marked erased.
Reconstruction fails distinctly for the latter two; domain-only replay still
works. The negative test rolls its fixture changes back before restarting the
processor, so it leaves no poisoned history for other scenarios.

This does **not** implement erasure. In particular it does not purge a previously
materialised read-model copy, coordinate in-flight projection, stop later commands
from recreating erased data, or address SDS, backups and restoration.

## Running the experiment

> **Historical.** These spike tests were removed in 4b-1; see the 4b-1 entry
> under gate 4. The record below describes what they proved at the time.

Requires Java 25 and running Docker:

```bash
./gradlew :data-access-service-axon:integrationTest \
  --tests 'uk.gov.justice.laa.dstew.spike.EventScopedPiiSpikeIntegrationTest'

./gradlew :data-access-service-axon:spotlessCheck \
  :data-access-service-axon:checkstyleIntegrationTest
```

The focused tests exercise:

- event-ID linkage, filename-only deltas, complete submitted content and an event
  with no sidecar;
- multiple documents with interleaved, repeated filename changes addressed by
  document ID, preserving other filenames and metadata through submission;
- sensitive values absent from persisted event payloads and reconstructed domain
  state;
- a lagging projection at an earlier stream position, duplicate/older delivery,
  and reset/rebuild using the real streaming processor;
- competing appends at one stream version, with no committed orphan sidecar;
- upload versus submit, with a stale submit rejected and a fresh retry including
  any accepted upload;
- declaration reads independent of projection lag and without writes, rejected
  in-place candidate updates, and exact JSONB equality between reviewed and
  submitted content;
- a deterministic submit/upload conflict through the real retry dispatcher,
  preserving the reviewed version rather than silently accepting a newer draft;
- draft-content compaction retaining domain events, explicit loss of old draft
  bodies, lagging projection and full reset/rebuild from submitted content;
- compaction rollback restoring deleted content and leaving no compaction event;
- hash-bound review with no writes, wrong-hash rejection and stale retry
  rejection through the real retry dispatcher;
- 40 full-body draft edits with content-size measurements before submission,
  after submission and after compaction;
- forced rollback after append, followed by a successful fresh attempt;
- explicit missing/erased-content failure while domain replay remains possible.

### Recorded validation

On 2026-10-30, under Java 25 with PostgreSQL 17:

- All eleven spike tests passed, including immutable declarations, hash-bound
  review, stale retry rejection, multiple document renames, full-body draft
  edits, transactional compaction and real processor reset/rebuild after
  compaction.
- The existing `ApplicationDraftIntegrationTest` and `RawReplayContractTest`
  passed alongside the initial five spike tests: 36 tests in that combined run,
  with no failures. Those baseline suites were not rerun for this extension.
- `:data-access-service-axon:spotlessCheck` passed.
- `:data-access-service-axon:checkstyleIntegrationTest` remains blocked by 50
  violations in 10 existing files. Each violating file was compared with the
  baseline commit and was unchanged; neither new Java file has violations.
  The quality gate was not relaxed and unrelated files were not edited.

The full build, full integration suite and coverage verification were not run.
This is focused mechanism evidence, not production-readiness acceptance.

## Next decision gates

### Gate 1 decided, 2026-10-30: hash-bound review with ETag

**Decision:** adopt hash-bound review; the persisted declaration candidate is
superseded and retained in the spike only as comparison evidence.

- Draft GET is side-effect free. It reconstructs the latest draft from
  authoritative history and returns a strong `ETag` encoding the stream version,
  hash-scheme version and SHA-256 content hash, for example
  `"3.h1.<sha256>"`. The format is opaque to clients.
- Submit requires `If-Match` with that ETag. Missing: `428 Precondition Required`.
  Version or hash mismatch: `412 Precondition Failed`, requiring a fresh GET and
  declaration. The server never re-reads a newer draft to satisfy a stale tag.
- Automatic retries reuse the caller's tag unchanged, as the spike demonstrates.
- The hash covers a specified canonical form of the declared content (key
  ordering, map ordering, nulls, numbers, schema version). A hash-scheme change
  invalidates outstanding tags rather than silently matching.
- The stored submitted content is the declared record; no separate candidate.

Open for implementation: canonical-form specification against the real
application schema, ETag on the existing draft GET and consumer coordination for
`If-Match` on the currently bodyless submit, which is an API contract change.

### Gate 2 decided, 2026-10-30: how consumers get application details

**Decided:**

- **Edits after submission store only what they add.** A note stores its
  text; a decision stores its description and serialised request; a full
  application update stores a complete new body, which becomes a checkpoint. No
  more whole-payload copies for small changes. Decision outcomes
  (`overallDecision`, `autoGranted`) are already on `ApplicationDecisionMadeEvent`.
- **Priority is one domain definition of how events build application
  details**, not historical-version browsing. No consumer should re-implement
  event interpretation. Today `ApplicationProjection` duplicates logic
  that `ApplicationEvolve` owns for the aggregate.
- **Principle: read from the aggregate by default.** Events are the source of
  truth. CQRS read models exist to solve performance problems, not as a goal in
  themselves. Build application reads from the aggregate's events. Add a
  stored read model only when that is too slow, or when the view spans more
  than one aggregate (lists, search, cross-aggregate routing). A stored read
  model holds the aggregate's output; it never re-implements event handling.
- **Historical-version browsing is deprioritised.** Building at a fixed stream
  position is retained only so lagging projections stay correct.
  `applicationDataVersion` is internal (absent from API specs), so replacing it
  is not a public contract change.

Consumer needs found in the current code, and the outcome under the principle:

| Consumer | Needs | Outcome |
|----------|-------|---------|
| `ApplicationProjection` (single application details) | Current details of one application. | Single aggregate: **replace** with an application loaded from its events. |
| `ApplicationHistoryAssembler` | Only its own event's note text or decision description; today it loads a whole version for that. | Single aggregate: build from the stream; no projection needed. |
| `WorkListProjection`, `ApplicationListIndexProjection` | Application details as at the handled event. | Lists across applications: **keep**, populated from the aggregate's output. |
| `ApplicationGroupRouteProjection` | Application details as at the handled event. | Spans aggregates: **keep**, populated from the aggregate's output. |
| `IndividualsQueryHandler` | Current client details. | Search across applications: **keep** the read model. |

**Decided 2026-10-30 (revised the same day): one whole aggregate; content is
stored separately only for persistence.** `ApplicationAggregate` holds event
content and is used on both sides. Event content is stored apart from domain
event details so it can be deleted, and reassembled into the event when it is
loaded. There is one event type, one aggregate and one place where events are
applied. This is the "forgettable payload" pattern.

**Guards**, recorded as experimental toolkit principle E1 (ai-toolkit
`feat/data-stewardship-toolkit` commit `257820e`, revising `66571a4`):

- **Erasure-replay test for each aggregate:** replay history with content
  erased, repeat the commands, and assert the same outcomes and events.
  Compaction is partial erasure, so this also covers gate 3.
- **Wrapper type:** content is held in a wrapper with a real erased state.
  Architecture tests stop command handlers, deciders and validators from
  unwrapping it where practical.
- **Registered content readers:** only registered event processors receive
  content; others receive the erased form. Each declares what it stores and how
  erasure reaches it. This is the same list gate 3 uses for compaction.
- **Facts on events:** command-side rules beyond payload schema validation use
  facts on domain events; if a sensitive input were ever needed, record a
  derived fact. ADS's design already expects this.

**Why the earlier choice was revised:** option B was chosen first, keeping
content out of the command aggregate. On reflection, that added a second place
where events are applied, to work around what is really a storage concern.
Loading content on writes costs the same as the read already accepted, because
writes redirect to a read. The real risks are decisions depending on content
and content leaking into read models that erasure cannot reach; the guards
target those directly.

Options considered for what "use the aggregate" means in practice:

- **A. Same class.** `ApplicationAggregate` also holds content and is loaded
  on the read side. **Chosen in revised form:** command loads do fetch content,
  and the guards stop decisions depending on it.
- **B. Same events and evolve logic, separate read entity.**
  `ApplicationDetails` = `ApplicationState` (applied through `ApplicationEvolve`)
  plus content, loaded through Axon from the same event stream. Chosen first,
  then superseded for the reasons above.

Open, for gate 4a:

- **Where content is reassembled.** At the event-store or serialisation layer,
  which is a deeper Axon dependency than today's read side.
- **The erased form.** How events with deleted content are represented and
  loaded.
- **Delivery to registered processors only.** How content reaches registered
  processors but not others.
- **Read cost.** Load time and bytes read at 10, 40 and 100 draft edits,
  compared with today's single-row draft read.
- **Snapshots and caches.** Any snapshots or caches holding content need a
  store that erasure can reach.

Note: the spike currently folds content in `Store.reconstruct`, separately from
`State.apply`. That is the split this gate removes.

**Deferred:** whether a rebuildable full-version table is needed as a migration
safety net is a gate 8 (migration) decision.

### Gate 3 decided, 2026-10-30: compaction trigger and safety

- **Trigger:** asynchronous. A handler reacting to submission dispatches
  `CompactDraftContent` after a configurable delay, outside the submit
  transaction. Compaction failure never blocks or rolls back submission; the
  command is idempotent and retried. The delay doubles as a support window.
- **Safety:** consumers that load the aggregate are safe by construction,
  because the erasure-replay guard from gate 2 proves the aggregate tolerates
  deleted content. Event processors receiving content are on the registered
  content-reader list from gate 2. Any of them genuinely needing per-event
  draft content is marked as such; compaction then waits for that processor's
  token to pass the submission position and for any replay to finish. Today no
  such consumer is known; verify before enabling (including outbound
  integration events).
- **Dependency:** this relies on gate 2's aggregate loading and registered
  content readers. Until every content consumer uses them, compaction must stay
  disabled.
- **Abandoned drafts:** propose a `DraftExpired` domain event plus deletion of the
  draft's event content after a period of inactivity, retaining domain events.
  Data stewardship sets the period; the spike proposes only the mechanism.

### Constraint, 2026-10-30: one-off data reset is available

ADS is not yet in production, so a **full data reset** is acceptable if the
refactor needs it. This is the **last chance**: after go-live, every stored
format becomes a contract that needs upcasting or migration. Consequences:
- Batch every breaking change to stored formats into the same reset:
  - the event mapper (`EventJson`);
  - explicit `@Event` names, which change stored type names;
  - kept-field and content separation of existing events;
  - removal of the draft and PII payload stores and `ApplicationProjection`.
- **Decided 2026-10-30: no report-only mode.** Every event type is enforced
  from the reset: an event type without a kept-field schema is rejected. 4b
  therefore classifies all existing Application event types up front. Report-only
  survives only as scaffolding in the 4a probe, where real events have no
  schemas yet.
- No migration of existing drafts or projections is needed.
- The reset must happen before go-live, so the refactor's breaking changes are
  on the go-live critical path.

### Gate 4 in progress, 2026-10-30: real Application slice

Split into two steps:

- **4a, technical probe (test-only).** Using the real `ApplicationAggregate`,
  store event content apart from domain event details and reassemble it on
  load, wrapped in a type with an erased state. Load the aggregate from a query
  handler as well as a command handler. Prove the erasure-replay guard, and
  deliver content only to a registered processor. Record load time and bytes
  read at 10, 40 and 100 draft edits against today's single-row draft read,
  and where reassembly has to hook into Axon.
- **4a design agreed, 2026-10-30:**
  - **Reassembly:** a generic `EventStorageEngine` decorator, registered through
    Axon's `registerDecorator`.
    - **On append:** it moves content into `event_content` in the same
      transaction.
    - **On load:** it fetches an application's content in one query and
      rejoins it into the event JSON before conversion.
    - **Event processors:** the shared stream gives the erased form; registered
      content readers get their own event source (`eventSource(...)`) that
      reassembles content.
    - The serialiser and a custom repository were rejected: the serialiser
      needs one query per event and can't tell processors apart; the
      repository doesn't cover processors.
  - **Fail-safe extraction:** a schema allowlist of kept fields.
    - **Schema:** each event type has a JSON Schema of exactly the fields kept
      on the event (`additionalProperties: false`). Everything else is content.
    - **Value checks:** the kept part is validated on append, including value
      constraints (formats, enums, patterns), so a kept field can't become
      free text.
    - **Rejections:** an event type without a schema, or failing it, is
      rejected.
    - **Metadata:** Axon event metadata gets the same allowlist.
  - **Build-time tests:**
    - every event type has a schema;
    - every schema property exists on the event class;
    - every field outside the schema is `Erasable`.
  - **Review:** schema changes alter what is kept permanently and need data
    stewardship review (for example through CODEOWNERS). The schemas also
    document the persisted event contracts (toolkit E6). The allowlist rule is
    recorded in toolkit E1 (ai-toolkit commit `3eb94a2`).
  - **To prove in 4a:**
    - the content insert shares Axon's append transaction;
    - the payload form the decorator receives;
    - reading the application id from the load criteria.
- **4a step 1 findings, 2026-10-30**
  (`ContentSeparationProbeIntegrationTest`, 4 tests passing, real
  `ApplicationAggregate` and the auto-configured
  `AggregateBasedJpaEventStorageEngine`):
  - **Decorator registration works:** a `ConfigurationEnhancer` bean calling
    `registerDecorator(EventStorageEngine.class, …)` wraps the engine for
    both appends and loads.
  - **Transaction:** a Spring transaction is active during `appendEvents`, and
    the JPA engine writes the event there. A content row inserted in
    `appendEvents` rolls back with the event when a failure follows both
    writes. `AppendTransaction.commit()` runs after the database commit with
    no transaction active, so content must never be written there. Not yet
    tested: a genuine append conflict, rather than an injected failure.
  - **Payload form:** a Java object (`ApplicationDraftStartedEvent`) with its
    tags on append, but serialised bytes (`byte[]`) on load. Rejoining on load
    therefore works on JSON, as agreed. Splitting on append needs the
    payload converted to JSON first, using Axon's converter.
  - **Application id:** load criteria carry the tag
    `ApplicationAggregate=<id>` (through `criteria().flatten()`), so one content
    query per load is possible. Loading a new aggregate yields only an entry
    with no payload, so the decorator must tolerate entries without one.
- **4a step 2 findings, 2026-10-30**
  (`ContentSeparationSchemaProbeIntegrationTest` plus support in
  `ContentSeparationProbe`, 7 tests passing). Set-up: a test-only entity
  `ProbeDraft` holding `Erasable<ProbeBody>`, kept-field schemas in
  `src/integrationTest/resources/spike/event-schemas/`, and a separating
  decorator. Report-only mode applies to real Application events; probe events
  are enforced.
  - **Kept fields only:** the stored `ProbeDraftStarted` payload has exactly
    `draftId` and `officeCode`. `body` went to `spike.probe_event_content` as
    one JSON object per event, without anyone listing it as content.
  - **Rejoin:** a load after start and update rebuilds the full entity, with
    the latest body and revision 1, using **one content query per load**. Load
    payloads were `byte[]` every time, so the JSON-level merge was enough.
  - **Erasure:** with the content rows deleted, the entity loads with
    `body` erased while `officeCode` and `revision` stay intact, and submit
    still succeeds. `Erasable<T>` works with Jackson 3 in both directions
    through a delegating constructor and `@JsonValue`, including as a command
    result. An absent property gives null, which each event's compact
    constructor normalises to erased; no concrete-wrapper fallback is needed.
  - **Fail-safe rejection:** a probe event type with no schema
    (`ProbeNoteAdded`) and a kept field breaking its schema (`officeCode` =
    `bad`) are both rejected before anything is written. Neither an event nor
    content is stored.
  - **Report-only:** the real `ApplicationDraftStartedEvent` is reported and
    stored unchanged. This is probe scaffolding only; production enforces every
    type (see the data reset constraint).
  - **Type names:** `@Event(namespace = "probe", name = …)` gives
    `probe.ProbeDraftStarted`. Unannotated production events use the fully
    qualified class name. **Decided 2026-10-30:** in 4b, every event gets an
    explicit `@Event` namespace and name, and schema files are keyed by those
    stable names rather than Java packages.
  - **Metadata:** only `causationId` and `correlationId` were seen, so the
    metadata allowlist can start as exactly those two keys.
  - **Erasure-replay check:** this is a pure function. It folds the history,
    then folds it again with each event passed through the same kept-field
    split, and compares the decisions. It passes for the real submit rule. It
    catches a deliberately content-dependent rule (with content the result is
    `ProbeDraftSubmitted`; with content erased it is
    `ContentErasedException`). Because it reuses the schemas, it needs no
    hand-written erased fixtures.
  - **Cost observed:** every load runs the content query, including loads of a
    new entity and of real Application aggregates that have no content.
    **Decided 2026-10-30:** keep it simple and accept one indexed query per
    load. Optimise (for example, skip entity types with no content-bearing
    events) only if the read-cost measurements show a need.
  - **Genuine append conflict, 2026-10-30 (8 tests passing):** two updates were
    routed separately and held at a barrier after loading. Both therefore
    appended at revision 1. Both passed through the decorator and inserted a
    content row. Axon rejected one with `AppendEventsTransactionRejectedException`.
    The loser's content row rolled back with its event: two events, two content
    rows, no content without an event, and the stored content matches the
    winner's body. Content written inside `appendEvents` is therefore safe under
    real optimistic-concurrency conflicts, not just injected failures.
  - **Registered-processor delivery, 2026-10-30 (11 tests passing):**
    - Every event processor reads events through Axon's ordinary event
      stream, and the decorator passes that stream through unchanged. So by
      default every processor receives kept fields only, with content
      arriving as `Erasable` erased.
    - A processor on the registered list (`REGISTERED_CONTENT_READERS`) gets
      its own `StreamableEventSource` through
      `PooledStreamingEventProcessorConfiguration.eventSource(...)`, wrapping
      the default source. That source rejoins content for each event by id.
    - In the test, both processors handled the same `ProbeDraftStarted`: the
      ordinary one saw `body` erased, and the registered one saw the full
      body.
    - `ContentReaders.sourceFor(...)` refuses any processor name not on the
      list.
    - The default event source is available inside `customized(...)`, so
      wrapping it needed no extra wiring.
    - Cost: one content query per streamed event. This is acceptable for now
      under the keep-it-simple decision; batch it if replay cost matters.
    - Gap: code could still bypass the list by constructing the source
      directly or querying the content table. 4b needs architecture tests
      that allow content-table access and `ContentReadingEventSource` only
      from the content module and the list.
  - **Read cost, 2026-10-30 (12 tests passing):** the body carried the real
    `BaseCivilApplication` fixture content (1,235 bytes). Every edit stored a
    full body. Measurements were taken locally against Testcontainers Postgres
    17: 5 warm-up reads, then 30 measured reads.

    | Read | Events | Event bytes | Content bytes | Median | p95 |
    |---|---|---|---|---|---|
    | Today: `ApplicationDraftStore.find` (one row) | – | – | – | 2.68 ms | 4.68 ms |
    | Entity load, 0 edits | 1 | 117 | 1,478 | 7.77 ms | 9.77 ms |
    | Entity load, 10 edits | 11 | 748 | 16,269 | 8.10 ms | 11.25 ms |
    | Entity load, 40 edits | 41 | 2,668 | 60,669 | 8.90 ms | 9.83 ms |
    | Entity load, 100 edits | 101 | 6,509 | 149,470 | 9.10 ms | 11.94 ms |

    - Going from 1 to 101 events adds only about 1.3 ms. History length is
      not the cost.
    - Most of the roughly 5 ms gap to today comes from fixed overhead: the
      entity load runs through the command bus and an Axon load (the
      0-edit row). The baseline is a direct JPA row read, so the comparison
      is not like-for-like.
    - Content grows linearly with full-body edits: about 150 KB read for 100
      edits of a 1.2 KB body. Larger real applications would scale this.
    - Conclusion: under the keep-it-simple decision there is no need for
      snapshots or compaction for read cost at these sizes. Gate 3 compaction
      still matters for storage and erasure, not for latency.
    - Caveats: a local machine, a small fixture body, and no concurrent load.
      4b should repeat the measurement through the real GET draft endpoint
      with realistic application sizes.
  - **Query-handler loading, 2026-10-30 (13 tests passing):**
    - A `@QueryHandler` with `@InjectEntity` loads the whole entity through the
      decorator, exactly as a command handler does. The result equals the
      command-side read, with no append and no stored read model.
    - After content deletion, the query shows `body` erased and revision 10
      intact.
    - Timing at 11 events: query median 3.96 ms (p95 7.49 ms), command read
      median 3.35 ms (p95 4.86 ms).
    - These are lower than the read-cost table's 8–9 ms for similar loads,
      so local timings vary between runs and test orderings (JIT warm-up,
      for example). Treat them as rough; history length is still not the
      cost.
    - Gate 2's single-application read can therefore be a query handler that
      loads the entity.
  - **Snapshots and caches, 2026-10-30:**
    - Axon 5.4 snapshots are opt-in through `@Snapshotting`. The app
      declares none and its migrations have no snapshot table.
    - Axon 5.4 has no entity cache. The only cache is the per-message
      `ConversionCache`, which holds converted payloads for a message's
      lifetime in memory.
    - The app configures no Spring caching.
    - So no content is held beyond the event content table today.
    - Risk: a snapshot would serialise the whole entity, including
      `Erasable` content, into a store that erasure does not reach. The read
      cost shows snapshots are not needed. For 4b, add an architecture test
      that forbids `@Snapshotting` on entities holding `Erasable` fields,
      unless snapshot erasure is designed first.
  - **To evaluate: framework lock-in (raised 2026-10-30).** Before 4b is
    adopted, assess whether this design increases our dependence on Axon. It
    relies on:
    - Axon's `EventStorageEngine` decorator hook (`registerDecorator`);
    - the JPA engine writing inside a Spring transaction during
      `appendEvents`, which is undocumented behaviour that we observed rather
      than a contract;
    - Axon's `EventConverter` and payload forms (an object on append, bytes on
      load);
    - Axon tags and sourcing criteria to find content;
    - `@Event` names as schema keys.

    Compare this with keeping the split behind our own port, so that the
    schemas, the content table, `Erasable<T>` and the erasure-replay test
    stay framework-neutral and only a thin adapter is Axon-specific.
  - **Converter consistency, 2026-10-30 (found a fidelity bug):** the decorator
    now uses Axon's `EventConverter`, taken from the Axon configuration.
    Findings:
    - Axon uses its **own `ObjectMapper`**, not Spring's. The two give
      different JSON for the same event: decimals are `1234.5` under Axon and
      `1234.50` under Spring.
    - Axon alone (object, to bytes, to object) keeps `BigDecimal` scale
      (`1234.50`).
    - Our split goes through a JSON tree, and Axon's mapper reads tree
      decimals as floating point. So separation **loses scale** (`1234.50`
      becomes `1234.5`), and the loaded `ProbeBody` no longer equals the
      original. Two probe tests fail on this.
    - Dates, nanosecond instants and explicit nulls round-trip exactly. The
      split-then-rejoin JSON equals Axon's tree form.
    - Lesson: any conversion through a tree must match Axon's direct
      serialisation byte for byte, so a fidelity test with awkward types
      (decimals, dates, instants, nulls) is needed for each event schema.
    - **Decided 2026-10-30: one event mapper that we own (option 3).**
      `EventJson` is a plain Jackson `JsonMapper` with decimals exact through
      trees (`USE_BIG_DECIMAL_FOR_FLOATS` for deserialisation and tree nodes,
      `STRIP_TRAILING_BIGDECIMAL_ZEROES` off). Axon gets an `EventConverter`
      bean built from it, and Axon's auto-configured converter backs off with
      no conflict. The decorator, kept-field schemas and erase helper all use
      the same mapper. The alternatives were configuring the split's mapper
      only, or splitting raw bytes without a tree.
    - **Result, 9 tests passing:**
      - Axon's direct bytes equal `EventJson` bytes, which proves Axon is using
        our mapper.
      - Split, then rejoin, then load returns an equal `ProbeBody`, with
        `income` keeping scale 2.
      - A tree round-trip keeps `1234.50`.
    - **Same hazard, seen again:** the test's own helper still read content
      with Spring's mapper and lost the scale. Any reader that does not use
      `EventJson` silently degrades content. For 4b, add an architecture test:
      event payloads and content are parsed only through `EventJson`.
    - **Lock-in:** this reduces it. Serialisation rules are ours, and Axon
      only adapts them through `JacksonConverter`.
    - **Stored events (4b):** replacing Axon's mapper changes how stored
      events are read. This system is not yet in production, so a full data
      reset is acceptable (see the constraint below); no replay comparison is
      needed.
  - **4a complete, 2026-10-30.** All agreed probes are done; the framework
    lock-in evaluation remains open for 4b.
- **4b, real slice (production code on the spike branch).** Draft start, then a
  full-body update, then GET draft with the gate 1 ETag, then submit with
  `If-Match`, then GET application details. This replaces `ApplicationProjection`
  for single-application reads. Work list, list index and group route follow
  later. Starts only once 4a holds.
  - **Production mapping, 2026-10-30.** The slice above assumed endpoints that
    do not exist. Production has only POST create draft (idempotent) and POST
    submit, with no draft update and no GET draft. Sensitive content sits in two
    stores: `ApplicationDraftStore` (also used by document upload for
    filenames) and the versioned `ApplicationDataStore`, which
    `ApplicationProjection` reads for details. Prior authority has its own draft
    store. Most of the 21 event types carry no content and point to a data
    version instead. Two fields look like content: `potentialDuplicates` on
    `ApplicationCreatedEvent` and `decisionJustification` on
    `PriorAuthorityDecisionMadeEvent`.
  - **Decided 2026-10-30: split 4b into four steps, stopping after each.**
    1. **4b-1, foundation, no behaviour change:** `EventJson`, the content
       decorator and table (migration, using the data reset), `Erasable`,
       explicit `@Event` names, enforced kept-field schemas for all 21 event
       types (classification reviewed before enforcing), architecture tests.
    2. **4b-2, draft content into events:** draft start and document filenames
       carry content; submit reads from the aggregate; remove
       `ApplicationDraftStore`; erasure-replay test for `ApplicationDecider`.
    3. **4b-3, submitted content into events:** remove `ApplicationDataStore`;
       GET details from a query handler loading the aggregate (gate 2).
    4. **4b-4, new endpoints:** draft update, GET draft with ETag, submit with
       `If-Match` (gate 1).

    Prior authority follows the same pattern afterwards.
  - **Decided 2026-11-02:** the content model (`EventJson`, schemas,
    `Erasable`, content store) lives in a framework-neutral package behind our
    own port, with only a thin Axon adapter. Still open: how large real
    application bodies get.
  - **4b-1 classification, agreed 2026-11-02.** There are 21 stored event
    types. `ApplicationSubmittedEvent` in `integrationevent` is an outbound
    message, not a stored event, so it is out of scope.
    - **Rule: an event keeps only**
      1. identity and linking keys: aggregate, document, group, caseworker and
         work-item IDs, and the LAA reference;
      2. values a decider rule uses: status, versions, decision outcome,
         `autoGranted`, schema version, request fingerprint and `checksum`;
      3. values that decide access or work allocation, now or planned:
         `officeCode`, `priorAuthorityType` and `workItemType`;
      4. the event's own timestamp (`occurredAt`, or `uploadedAt` /
         `deletedAt` on document events);
      5. non-personal outcomes that inform retention schedules (added
         2026-11-06): decision outcomes, including per proceeding.

      Everything else is content. Usefulness after erasure is not a reason to
      keep a field; if data is needed after deletion, that needs its own
      solution.
    - **Invariant:** with all content erased, every aggregate still loads and
      every command still decides correctly. Evolve may read content only
      through `Erasable`, and no decider rule may depend on it.
    - **Why the boundary looked blurry:** aggregate state also acts as a read
      model. Evolve copies document metadata into state, but document rules
      only check `documentId`. That metadata moves to content, and readers
      get it from content.
    - **Keep everything (16 types):** all linked-group events (5), both
      work-item events (2), `ApplicationDraftStartedEvent`,
      `ApplicationUpdatedEvent`, `ApplicationDecisionMadeEvent`,
      `ApplicationReadyForManualAssessmentEvent`, `NoteCreatedEvent`,
      `PriorAuthorityDraftStartedEvent`, `PriorAuthorityDraftUpdatedEvent`,
      `PriorAuthoritySubmittedEvent` and `PriorAuthorityDocumentDeletedEvent`.
    - **Content-bearing (5 types):**
      - `ApplicationCreatedEvent`: content `potentialDuplicates`, which links
        this person to other applications. **Add** the LAA reference as a kept
        field; no event carries it today.
      - `ApplicationDocumentUploadedEvent`: keep `applicationId`,
        `documentId`, `uploadedAt`, `checksum`, `applicationDataVersion`;
        content `documentType`, `size`, `contentType`, `sourceService`,
        `fileSuffix`.
      - `PriorAuthorityDocumentUploadedEvent`: keep `priorAuthorityId`,
        `documentId`, `uploadedAt`, `checksum`, `parentApplicationId`;
        content `size`, `contentType`, `sourceService`, `fileSuffix`.
      - `PriorAuthorityDocumentTypeUpdatedEvent`: content `documentType`.
      - `PriorAuthorityDecisionMadeEvent`: content `decisionJustification`,
        `amountGranted`, `dateGranted`. `overallDecision` stays.
    - **Also noted:**
      - `fileSuffix` is everything after the last `.` in the uploaded filename,
        so it can hold part of a name. Document deletion builds the storage
        filename from it, so that path reads it through `Erasable`.
      - `requestFingerprint` is an unsalted SHA-256 of the whole request. After
        erasure it can only confirm an exact guess of the full payload, which
        is low risk.
      - `PriorAuthorityDraftUpdatedEvent` is the only type without `@Event`;
        4b-1 adds explicit names to all types anyway.
  - **4b-1 implemented, 2026-11-03.** No change to API behaviour.
    - **Where it lives:** `eventcontent` holds the model and port;
      `eventcontent.jdbc` and `eventcontent.axon` hold the adapters. A storage
      engine decorator splits content on append and joins it back on read.
      Content goes in `axon.event_content`, keyed by event identifier
      (migration `V31`, renumbered from `V29` after rebasing onto main).
    - **Event names:** all 21 stored types carry
      `@Event(namespace = "access")`; the payload type is now `access.<Name>`
      rather than the Java class name. Each has a schema
      `event-schemas/access.<Name>.json` listing exactly its kept fields, with
      `additionalProperties: false`. An event type with no schema is
      rejected.
    - **Metadata allowlist (decided 2026-11-03):** only `causationId`,
      `correlationId`, `X-Service-Name` and `authenticatedUserId` are stored.
      The last two identify the actor, not the data subject, so they stay.
    - **Schema qualification:** the content store schema-qualifies its table
      using `axon.db.schema`. Testcontainers service connections replace the
      URL and drop `currentSchema`, so an unqualified table name fails there.
    - **Test type names:** `new MessageType(SomeClass.class)` ignores
      `@Event` and uses the fully qualified class name, so test-only events
      need schemas under that name.
    - **Subscribing processors** (group route, work-item route, submitted
      publisher) receive events with content attached. None reads it. An
      architecture rule enforces this: only `eventcontent`, `*Evolve`,
      `ApplicationProjection` and `PriorAuthorityProjection` may call
      `Erasable` accessors. Further rules keep the content store and Axon
      adapter out of domain code, ban snapshot types, and check every
      `@Event` schema against its record's kept components. A deliberate
      violation of each rule was confirmed to fail.
    - **Erasure invariant proven** by `ErasureReplayTest`. It folds full
      Application and prior authority histories through the real evolve
      functions, with and without content. States match apart from content
      fields, and decider outcomes are identical (ready, note, PA decision with
      right and wrong version, wrong actor, already decided).
    - **`fileSuffix` after erasure (decided 2026-11-03):** once content is
      erased, deleting a PA document cannot build the SDS filename. This is
      accepted: that clean-up is best-effort and logged, and erasure is
      expected to remove the SDS files itself.
    - **Spike probes removed:** the `uk.gov.justice.laa.dstew.spike` tests
      and their SQL were deleted. Their erasure evidence is replaced by
      `ErasureReplayTest` and the production integration tests.
    - **Validation:** all unit tests pass. All `uk.gov.justice.laa.dstew.access`
      integration tests pass except
      `ApplicationSubmittedLocalStackIntegrationTest`, which could not pull
      the LocalStack image (environmental). Main Checkstyle is clean, and test
      Checkstyle is below its baseline.
  - **4b-2 implemented, 2026-11-05.** Application drafts are stored only in
    events. No change to API behaviour, except as noted below.
    - **Draft content in events:** `ApplicationDraftStartedEvent` now carries
      `applicationContent`, `serialisedRequest` and `potentialDuplicates` as
      content, and keeps `status` and `laaReference`. The upload event carries
      `originalFilename` as content. `ApplicationDraftStore`, its payload and
      repository are removed, and `V32` (renumbered from `V30`) drops `axon.application_draft`.
    - **`serialisedRequest` kept (decided 2026-11-05):** stored as content
      next to `applicationContent` for now; revisit in 4b-3, when
      `ApplicationDataStore` goes.
    - **Erased draft returns 404 (decided 2026-11-05):** submit and upload
      against a draft whose content is erased give the same 404 as a missing
      draft. This is the one agreed case where command handling looks at
      content, and it only asks whether content is present. Decider rules
      still compare kept fields only: start-draft retry and conflict use
      `applicationId`, `schemaVersion` and `requestFingerprint`.
    - **Single command-side reader:** `ApplicationCommandHandlerSupport`
      (`findDraft`, `requireDraft`) is the only command-side class allowed to
      unwrap draft content. It is added to the architecture rule's allowlist.
      Submit reads the draft from aggregate state and still writes
      `ApplicationDataStore` version 0 until 4b-3.
    - **First production query handler that loads the aggregate:** the upload
      use case checks the draft exists, before calling SDS, with
      `ApplicationDraftExistsQuery`. `ApplicationDraftQueryHandler` answers it
      using `@InjectEntity`. The document GET does the same to read a draft's
      filenames; submitted filenames still come from `ApplicationDataStore`.
    - **Tests:** `ErasureReplayTest` adds a draft history (draft start plus an
      upload with a filename). States match apart from draft content and
      filenames, and the start-draft retry, conflict and new-draft outcomes are
      identical. Fixture tests cover erased-draft upload and submit (404) and
      an identical retry after erasure (idempotent). Integration tests read
      filenames from `axon.event_content`, and race tests gate on the upload
      command handler instead of the removed store.
    - **Validation:** unit tests 1346/1346 and integration tests 272/272 pass;
      Checkstyle main is clean and test/integration warnings match HEAD (43/49).
    - **Pitfall:** the plain 10-arg `ApplicationDocumentUploadedEvent`
      constructor ends `(dataVersion, fileSuffix)`; a filename passed there
      silently becomes the suffix. Use the 11-arg form.
  - **4b-3 split (decided 2026-11-06):** 4b-3a moves submitted content into
    events and has command handling read it from the aggregate, while still
    writing `ApplicationDataStore` for readers. 4b-3b moves readers off the
    store and drops `application_data`.
  - **4b-3a implemented, 2026-11-06.** No change to API behaviour.
    - **Delta content (decided 2026-11-06):** each event carries only the
      content it introduces, not a snapshot. `ApplicationCreatedEvent` and
      `ApplicationUpdatedEvent` carry the parsed application content and
      `serialisedRequest`; `NoteCreatedEvent` carries `noteText`;
      `ApplicationDecisionMadeEvent` carries `meritsDecisions` (this
      decision's proceedings only), `certificate`, `serialisedRequest` and
      `eventDescription`. Three types move from "keep everything" to
      content-bearing.
    - **Kept `proceedingIds` (decided 2026-11-06):** on Created and Updated.
      Proceedings and their IDs are used for linking and tracing and must
      survive deletion (identity/linking rule 1). Decision validation now
      checks proceeding IDs against kept state, so an unknown proceeding is
      still rejected after erasure.
    - **Kept `decisionRequestFingerprint` (decided 2026-11-06):** on
      DecisionMade, a SHA-256 of the decision request (rule 2). The auto-grant
      retry compares fingerprints instead of the stored request, so retry and
      conflict outcomes are the same after erasure. It is cleared when an
      update re-enters submitted.
    - **`applicationDataVersion` stays** as a kept counter.
    - **Evolve folds content:** `ApplicationState.content` holds an
      `Erasable<ApplicationDataPayload>` built with the same `with*` methods
      the store path uses. If any part is erased (for example one note), the
      whole folded content is erased. Command handlers get it only via
      `ApplicationCommandHandlerSupport.currentContent`, which throws if it
      is erased. That path exists only to keep writing the store, and goes
      away in 4b-3b, when no handler needs content.
    - **Per-proceeding decision kept (decided 2026-11-06):** the decision
      value for each proceeding is not personal data and informs retention
      schedules, so it is not erased. DecisionMade keeps `proceedingDecisions`
      (proceeding ID → decision), and state merges them across decisions,
      clearing them when an update re-enters submitted (as the store does).
      Reason and justification stay content. For now `meritsDecisions` in
      content still repeats the decision value, so the store payload can be
      built unchanged; this can be slimmed in 4b-3b.
    - **Tests:** `ErasureReplayTest` now uses content-bearing Created, Note
      and Decision events. States match apart from `content`. Decisions with
      a known proceeding, an unknown proceeding or a stale version, and
      auto-grant same/different-request matching, give identical outcomes,
      and `proceedingDecisions` survives erasure. The Postgres decision test
      checks that the per-proceeding decision is in the kept payload and the
      justification only in `axon.event_content`.
      `ApplicationEvolveTest` checks that the folded content equals the
      payload the handlers write to the store.
    - **Validation:** unit tests 1347/1347 and integration tests 272/272 pass;
      Checkstyle main is clean, test warnings fall to 40 and integration
      warnings stay at 49.
  - **4b-3b plan (decided 2026-11-06):** three steps: (i) single-application
    reads, (ii) the application summary and the projections that use it,
    (iii) stop writing the store, drop `application_data` and switch Prior
    Authority off `latestVersion`. The individuals endpoint was removed on
    main (#599), so it needs no read model.
    - **Application summary is the one source of application facts
      (decided 2026-11-06).** The list index becomes the application summary
      (to be renamed). It is the only application read model that reads
      event content and joins `RegisteredContentReaders`; no other read
      model copies application content. Erasure plus a rebuild therefore
      only has to reach this table, and a new field touches one handler.
      - **Holds:** descriptive facts (LAA reference, client name and DOB,
        category of law name and code, matter types and codes, delegated
        functions, office) and filterable lifecycle facts (status,
        `submittedAt`, `modifiedAt`, caseworker, `autoGranted`, linked
        group). Not workflow-only state such as assignment versions or
        work-item boundaries. Today it is missing `categoryOfLaw`,
        `usedDelegatedFunctions`, `linkedGroupId` and the matter-type lists.
      - **Interface:** an `ApplicationSummaries` query port with batch
        lookups (`findByIds`). Other read sides call it for display fields.
        SQL joins are used only where a query filters or sorts on summary
        fields, which today is only the list page the summary owns.
      - **Missing rows:** the summary and its callers are separate
        processors, so a summary row can briefly lag. The lookup returns
        nothing for that ID and callers show empty fields; they do not wait.
      - **List page:** built from the summary alone, plus the existing group
        and Prior Authority lookups. Prior Authority appears only as a nested
        list per application, so it does not affect filtering or paging.
        Rejected: loading the aggregate per row (up to 100 stream loads per
        page, against gate 2). After erasure and a rebuild, rows remain with
        empty content fields.
      - **Work list:** drops its six copied application columns and its
        store reads. Its own filters and sort (item type, assignee,
        `autoGranted`, `submittedAt`) stay; application fields come from the
        summary by application ID (`parentApplicationId` for Prior Authority
        rows). The summary keeps rows after a decision, so Prior Authority
        rows can still find their parent. Prior Authority type and expert
        type are Prior Authority data and move with Prior Authority content
        later. Rejected: copying into a per-application work-list table, and
        keeping category and matter types as event fields.
      - **Amended 2026-11-09: the work list keeps its copied columns.**
        Slimming it is not needed to prove the approach, and the work list
        will filter on these fields later, which a read-time lookup can't
        support. The six fields (LAA reference, delegated functions,
        category of law and code, matter types and codes) are not personal
        data, so a disposable projection may copy them; erasure does not
        need to reach them. In (iii) the work list reads them from event
        content instead of `application_data`. Slimming via the summary
        remains an option if the copies prove a maintenance cost.
    - **GET details after erasure returns 404 (decided 2026-11-06).** Details
      load the aggregate; when its content is erased the response is 404, as
      today. Returning the kept fields (200 with an erased marker, or 410)
      would change the API contract, and is left as a later API decision.
    - **`application_current_state` merges into the summary (decided
      2026-11-06).** After 4b-3b it would hold only status, office,
      caseworker, group, timestamps and `autoGranted`, which the summary
      already has. Readers move as follows:

      | Reader | Source after 4b-3b |
      |---|---|
      | GET details, GET by ID, notes, document download | Aggregate |
      | `LinkedGroupAssignmentDispatcher` (caseworker lookup) | Removed on main (#593): group assignment now decides from loaded aggregates |
      | List page | Summary |
      | Stalled assessments (status scan) | Summary |
      | Access policy | Summary |
      | Create/Submit use cases awaiting the row | Wait dropped (see below) |

    - **Create and Submit stop waiting for a projection (decided
      2026-11-06).** They return 201 once the command commits. The 202
      ("not yet readable; poll Location") stays in the spec but is no longer
      used: Location points to GET by ID, which reads the aggregate and so
      is readable at commit. The old wait never guaranteed list visibility
      either, because the list index is a separate processor.
    - **History reads a genesis stream (decided 2026-11-06).** History is a
      presentation of the events in a case, not a store of facts. It no
      longer copies or hydrates content:
      - **Genesis ID:** every event carries the ID of the root application
        it relates to (`genesisId`). Events that span several applications
        (group events) carry each member's ID. A `GenesisScoped` interface
        (`genesisIds()`) is implemented by every `@Event` type, and a unit
        test fails when one does not. Gaps today: `WorkItemAssigned`,
        `WorkItemUnassigned` and `PriorAuthorityDocumentTypeUpdatedEvent`.
        Existing data is deleted, so the field is required and nothing is
        backfilled.
      - **Genesis stream:** one processor handles all events and writes a
        pointer row for each genesis ID (`genesis_id`, event identifier,
        global position, `occurredAt`, event type). It holds no content, so
        erasure needs nothing extra, and it rebuilds by replay.
      - **History:** reads the pointers in order, loads the events and maps
        each to an entry. Descriptions (note text, decision description)
        come from each event's own erasable fields; erased content gives a
        null description. This replaces the history table and its
        `dataVersion` lookup into `application_data`.
      - **Why not Axon tags:** the store is `AggregateBasedJpaEventStorageEngine`
        (one aggregate identifier per event, no multi-tag queries), and adding
        the application tag key to group or work-item events would pull them
        into the application aggregate's consistency boundary.
      - **To check before building:** efficient loading of events by
        identifier or global position from the JPA store. **Checked
        (2026-11-06):** `global_index` is the primary key and
        `event_identifier` is unique, so batch loads are indexed. Axon has
        no load-by-identifier API, so a loader in `eventcontent` decodes
        stored events by `@Event` name and rejoins content in one batch;
        it is a listed content reader.
      - **Refinements (decided 2026-11-06):**
        - `genesisIds()` derives from existing fields; no separate
          `genesisId` field. Only the three gap events gain a field
          (`applicationId` on work-item events, `parentApplicationId` on
          `PriorAuthorityDocumentTypeUpdatedEvent`, matching the other Prior
          Authority document events).
        - History orders by `occurredAt` (then by pointer, for ties), not
          by commit position. History must accept events from other systems
          in future, is not transactional with commands, and is not used
          for command-side decisions (except perhaps deletion), and may be
          extracted into another service.
        - Pointers therefore reference events by source and event
          identifier, not by `global_index`, which exists only in this
          store. One source (this event store) exists today.
        - Built in three commits: genesis IDs, genesis stream, history.
      - **Commit 1, genesis IDs (done 2026-11-06):**
        - `command.GenesisScoped` has `Set<UUID> genesisIds()`. All 21
          `@Event` records implement it. A null ID fails fast.
        - Groups name every affected application:
          - Created: members and lead.
          - Member added or removed: lead and member.
          - Dissolved: members, former lead and removed.
          - Lead changed: previous and new lead.
        - `WorkItemAssigned` and `WorkItemUnassigned` gain `applicationId`.
          For an application work item it equals `workItemId`; for a Prior
          Authority it is the parent application.
        - `PriorAuthorityDocumentTypeUpdatedEvent` gains
          `parentApplicationId`. Schemas are updated.
        - `GenesisScopeArchitectureTest` requires every `@Event` class to
          implement `GenesisScoped`.
        - Unit tests 1425/1425 and integration tests 313/313 pass.
          Checkstyle stays at baseline (0/37/53).
      - **Commit 2, genesis stream (done 2026-11-06):**
        - `GenesisScoped` also has `Instant occurredAt()`. The three
          document events without that field derive it from `uploadedAt` or
          `deletedAt`.
        - `genesis_stream` (V33) has one row per event per genesis ID:
          `genesis_id`, `source`, `event_identifier`, `event_name`
          (`access.*`) and `occurred_at`. The `id BIGSERIAL` breaks ties in
          the order rows were written. The row is unique on genesis ID,
          source and identifier, and has no foreign key. Reads use an index
          on `(genesis_id, occurred_at, id)`.
        - `GenesisStreamProjection` (processor `genesis-stream`, pooled
          streaming) has one handler per event type, each delegating to
          `record`. Axon 5 annotated handlers match by event name, so one
          catch-all handler is not possible. `GenesisScopeArchitectureTest`
          requires a handler for every `@Event` class.
        - The processor reads no content: genesis IDs and `occurredAt` are
          kept fields. It is not a content reader.
        - A redelivered event is skipped when its pointer exists. A reset
          clears the table.
        - The source for this store is `data-access-api`.
        - Unit tests 1429/1429 and integration tests 314/314 pass.
          Checkstyle stays at baseline (0/37/53).
      - **Commit 3, history reads the stream (done 2026-11-06):**
        - `eventcontent.StoredEvents` loads events by identifier. Its
          Axon implementation queries `AggregateEventEntry` in one batch,
          rejoins content in one `event_content` query, and decodes by
          `@Event` name. Callers must be in `RegisteredContentReaders`;
          history is registered as `application-history`.
        - `FindApplicationHistoryQuery` reads the application's pointers in
          stream order, loads their events and maps them. A missing event
          is an integrity failure. The mapper holds no state between
          requests; a Prior Authority's type for assignment entries comes
          from its submitted event in the same stream.
        - Notes and decision descriptions come from event content. Erased
          content gives no description. `ApplicationHistoryMapper` is a
          listed content reader in `EventContentArchitectureTest`.
        - Removed: both history projections, assemblers, read models and
          repositories, and the `application-history-projection`
          processor. V34 drops `application_history` and
          `prior_authority_history` and their tokens.
        - Integration tests now read history through the query, not the
          tables. Two tests that inserted conflicting rows into
          `prior_authority_history` are replaced by
          `ApplicationHistoryMapperTest`. One of them expected
          `PRIOR_AUTHORITY_DOCUMENT_UPLOADED` entries, which no projection
          ever wrote.
        - The API response is unchanged. Only the tables' internal
          `data_version` column, used to look up `application_data`, is
          gone.
        - Fixed an ordering flake in `GenesisStreamProjectionTest` (genesis
          IDs are a set, so either lead may be checked first).
        - Unit tests 1378/1378 and integration tests 312/312 pass.
          Checkstyle stays at baseline (0/37/53).
      - **Scheduling:** a separate step after (i); step (i) does not touch
        history.
  - **4b-3b(i) single-application reads from the aggregate (done
    2026-11-07):**
    - **Reads:** GET details, `FindApplicationByIdQuery`, notes and document
      download take the sourced `ApplicationAggregate` (`@InjectEntity`)
      instead of `application_current_state` and `application_data`.
      `ApplicationReadModelAssembler.fromAggregate` builds the detached read
      model from state and content. It returns nothing for a missing
      aggregate, a draft (no status), erased content, or an office outside
      the caller's scope (`ReadAccessScope.permitsOfficeCode`).
    - **State additions:** `ApplicationState` now keeps `createdAt` (from
      the draft when there is one), `submittedAt`, `modifiedAt` and the
      erasable `potentialDuplicates`. `ErasureReplayTest` treats
      `potentialDuplicates` as content.
    - **Still read from `application_current_state` until (ii):**
      `linkedGroupId` and `leadApplicationId` (the group read model's
      `member_ids` is unindexed JSONB), and `modifiedAt` from group events
      (the later of state and row is used).
    - **Wait dropped:** Create, draft create and draft submit return once the
      command commits (201, or 200 for draft submit). The
      `ApplicationProjectionExistsQuery` handler, the emits and the use-case
      wait tests are removed. `SubscriptionProjectionGateway` remains for
      the Prior Authority draft create and submit.
    - **Content readers:** `EventContentArchitectureTest` registers
      `ApplicationReadModelAssembler` as a content reader.
    - **Known gaps, left as they were:**
      - Document download still has no office access check.
      - `ApplicationEvolve` does not update `state.officeCode` on
        `ApplicationUpdatedEvent`, while the projection re-derives it from
        content. The read side therefore prefers the content's provider office
        code. This is a command-side inconsistency to fix separately.
      - The 202 response stays in the OpenAPI spec but is no longer
        returned.
      - Deleting a row from `application_data` for retention no longer hides
        details, because content comes from the events. Erasure is through
        event content, and the table goes in (iii).
    - **Tests whose expectations changed:**
      - Reads during projection lag (`ProjectionTimeoutInMemoryTest` and the
        paused-upload download) are now current rather than stale or 404.
      - `ProjectionAwaiter` polls of `FindApplicationByIdQuery` now return
        straight away. No races appeared, so it is unchanged.
    - **Validation:**
      - Unit tests 1422/1422 and integration tests 313/313 pass.
      - Checkstyle main is clean; test warnings stay at 37 and integration
        warnings at 53.
      - `pactBootCheck` fails 6/7 with `Schema "AXON" not found` (H2), the
        same as at the previous commit.

  - **4b-3b(ii) commit 1, the application summary reads event content
    (done 2026-11-07):**
    - **Rename:** the list index is now the application summary (package
      `query.application.summary`, `ApplicationSummary*` classes). V35
      renames `application_list_index` to `application_summary`, empties it
      and deletes the old processor's token, so the new `application-summary`
      processor rebuilds it. Index names (`idx_ali_*`) are kept.
    - **Content reader:** the projection reads `ParsedAppContentDetails`
      from `ApplicationCreatedEvent` and `ApplicationUpdatedEvent` instead of
      `ApplicationDataStore`. It is registered as `application-summary` in
      `RegisteredContentReaders`, gets an explicit processor bean, and is a
      listed content reader in `EventContentArchitectureTest`.
    - **New columns:** `category_of_law`, `used_delegated_functions`,
      `matter_types` and `matter_type_codes` (JSONB lists of the distinct
      pairs from the proceedings, in order) and `linked_group_id` (indexed).
      The single lead `matter_type` and `matter_type_code` stay for the list
      page's filter and sort.
    - **Kept versus content fields:** LAA reference, office, status and
      timestamps come from kept event fields. When content is erased the
      descriptive fields are empty and the row stays. The content's provider
      office code overrides the kept one when present, matching (i).
    - **Updates:** `autoGranted` returns to PENDING only when the update
      enters submitted, as in `ApplicationEvolve`; otherwise it is kept.
    - **Groups:** group events set or clear `linkedGroupId` and
      `leadApplicationId`. A lead change updates every row in the group,
      found by `linkedGroupId`.
    - **Port:** `ApplicationSummaries.findByIds` returns detached summary
      rows by ID. `ApplicationReadModelAssembler` uses it for linked-member
      details instead of the repository.
    - **Open question, unchanged behaviour:** linked-member details throw
      (500) when a member's summary row is missing or its LAA reference or
      client name is empty. That happens while the summary lags or after
      one member is erased. `LinkedApplicationSummaryResponse` requires
      those fields, so returning empty values needs an API decision.
    - **Validation:** unit tests 1378/1378 and integration tests 312/312
      pass. Checkstyle main is clean, test warnings stay at 37 and
      integration warnings fall to 52.

  - **4b-3b(ii) commit 2, application_current_state removed
    (done 2026-11-07):**
    - **Removed:** the current-state event handlers, the
      `application-projection` processor and content reader,
      `ApplicationReadRepository` and `ApplicationCurrentStateAccessPolicy`.
      V36 drops `application_current_state` and deletes the old processor's
      token. `ApplicationProjection` is renamed `ApplicationQueryHandler`; it
      now only handles queries.
    - **List page:** pages the summary and returns summary rows directly.
      Groups and prior authorities are batch-loaded by the page's group and
      application IDs. Rows no longer drop out when `application_data` is
      missing, and client names and date of birth come from the summary.
    - **Stalled assessments:** read from the summary (submitted, auto-grant
      PENDING, submitted before the threshold). The summary's
      `streamVersion` is the reported version; it is only logged.
    - **Details:** `ApplicationReadModel` is a plain detached model built
      from the aggregate. Its group ID, lead ID and latest `modifiedAt` come
      from the summary through the `ApplicationSummaries` port; they are
      empty while the summary lags.
    - **Tests:** lag tests and the pact lagging state now stop
      `application-summary`. The sequencing integration test is renamed
      `ApplicationSummarySequencingIntegrationTest` and gates only the
      summary; its current-state replay and race cases are removed, as the
      summary already covers the group races.
    - **Validation:** unit tests 1352/1352 and integration tests 308/308
      pass (the event-handler and current-state tests went with the
      handlers). Checkstyle main is clean; test warnings fall to 36 and
      integration warnings to 50.

  - **Projection waits removed; prior authority reads from its aggregate
    (done 2026-11-09):**
    - **Why:** on `main`, endpoints that wait on a projection return 202
      behind more than one pod. Subscription-query updates are in-memory per
      pod, so the pod handling the request never hears that another pod's
      tracking processor updated the read model; it waits the full timeout.
      Reading the sourced aggregate removes the wait instead of polling for
      it.
    - **State:** `PriorAuthorityState.status()` derives DRAFT, SUBMITTED or
      DECIDED from the `submitted` and `decided` flags.
    - **Reads:** the new `PriorAuthorityQueryHandler` serves
      `FindPriorAuthorityByPriorAuthorityIdQuery` and
      `FindPriorAuthorityDocumentQuery` from the injected aggregate. It
      checks the office scope against the aggregate's office code. Content
      still comes from `PriorAuthorityDraftStore` or `PriorAuthorityDataStore`
      at the aggregate's `dataVersion`; it is not event-scoped yet. A
      submitted prior authority with no stored content now returns 404, not
      500.
    - **Projection:** `PriorAuthorityProjection` keeps only its event handlers
      and reset; it serves the application list and work list.
    - **Removed:** `SubscriptionProjectionGateway`,
      `PriorAuthorityReadQueryGateway`,
      `PriorAuthorityCurrentStateAccessPolicy`,
      `PriorAuthoritySubmittedByPriorAuthorityIdQuery` and the
      `application.projection.timeout` property. Prior authority draft create
      always returns 201 and submit always returns 200. No endpoint returns
      202 now; the OpenAPI spec still lists it.
    - **Tests:** the query tests move to `PriorAuthorityQueryHandlerTest`,
      which builds aggregates with the new test helper
      `PriorAuthorityStateEvents.aggregateOf`. `DevTokenInMemoryTest` now
      imports the in-memory event content store, because the GET sources the
      aggregate.
    - **Validation:** unit tests 1326/1326 and integration tests 308/308
      pass. Checkstyle is unchanged: main clean, test 36, integration 50.

### Remaining gates
1. Choose the declaration design: hash-bound review (recommended by the review)
   or persisted candidate. Agree version/hash transport, stale-response handling,
   canonical-form and hash versioning, and access checks. The prototype exposes
   Java commands/store methods, not an HTTP draft GET or submit contract.
2. Specify content access for pinned-version consumers (work list, list index,
   group route, history, individuals) and post-submission edits, before fixing
   the read-model shape.
3. Specify the compaction gate in terms of every content-reading processor's
   position and replay state, and what triggers compaction. Set retention for
   abandoned drafts.
4. Apply the mechanism to a real Application slice while preserving agreed API
   compatibility. Use Axon sourcing APIs or an incremental projector rather than
   reading Axon tables. Reuse real schema validation and document metadata, prove
   access checks and read-after-write semantics, and measure baseline versus
   proposed bytes, queries and latency using representative synthetic payloads.
5. Compare checkpoint-aware reconstruction with an incremental projection sharing
   domain transitions. Decide read-model schema/indexes and historical-version
   access; one current-state JSON row alone does not replace history, search or
   cross-aggregate work queues.
6. Prove concurrent erasure and replay behaviour, and define recovery for a
   genuinely missing content row. Assess draft-content growth before compaction
   and retention of new sensitive read-model copies, SDS and backups.
7. Validate the same design against PA and its parent-version references,
   immutable-data controls and safe bounded command retries.
8. Specify migration of existing full-body versions, mixed-version deployments,
   comparison/cutover and rollback boundaries. Do not invent historical deltas.
9. Review findings with the team before changing ADR 0002, the workbench's
   read-through guardrail or production storage.

**Working conclusion:** the first experiment tests the mechanism, not the whole
refactor. Production adoption, performance suitability, retention acceptance and
migration remain open.
