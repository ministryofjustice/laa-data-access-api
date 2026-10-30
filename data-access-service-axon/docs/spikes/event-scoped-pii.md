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
  - **Open before 4b-1:** whether the content model (`EventJson`, schemas,
    `Erasable`, content store) lives in a framework-neutral package behind our
    own port, with only a thin Axon adapter. Recommended: yes. Also still open:
    how large real application bodies get.

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
