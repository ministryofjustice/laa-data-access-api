# CQRS Building Blocks and Change Impact Guide

Audience: a developer new to the team who needs to estimate **which code must change** for a
requirement. Read [Axon in this module](axon-in-this-module.md) first for the Axon execution model;
this guide maps that model onto the actual classes and gives a decision table for requirements.

Base package: `uk.gov.justice.laa.dstew.access` (Axon Framework 5.2.0, PostgreSQL event store).

## 1. The building blocks in this module

| Building block | What it is here | Where it lives | Example |
|---|---|---|---|
| **Command** | Immutable record carrying intent. Has a `@TargetAggregateIdentifier`-style id (the aggregate id). Built by an HTTP mapper, never the OpenAPI type. | `command/**` (`*Command`) | `MakeApplicationDecisionCommand` |
| **Use case** | Thin Spring bean: security annotation + dispatch via `RetryingCommandDispatcher` (one retry on concurrency/unique-key failure); delegates to a `*Coordinator` when several commands or route locks are involved. | `command/**/*UseCase` | `MakeApplicationDecisionUseCase` |
| **Command handler** | `@CommandHandler` method that handles a command, typically by validating aggregate state and appending events; application-detail handlers may also append to `application_data`. | `command/application/handler/`, `command/application/priorauthority/handler/`, `command/application/linkedgroup/handler/`, `command/worklist/**` | `MakeApplicationDecisionCommandHandler` |
| **Decider** | Pure static functions: `(state, command[, data]) → event(s)` or exception. All business rules live here. | `*Decider` | `ApplicationDecider`, `LinkedApplicationGroupDecider`, `PriorAuthorityDecider` |
| **Aggregate** | Event-sourced consistency boundary (`@EventSourced`). Holds a `*State` object; events are folded by `@EventSourcingHandler`s. | `*Aggregate` | `ApplicationAggregate`, `LinkedApplicationGroupAggregate`, `PriorAuthorityAggregate` |
| **Evolve** | Pure static functions: `(state, event) → state`. The aggregate's `@EventSourcingHandler`s delegate to these. | `*Evolve` | `ApplicationEvolve` |
| **Event** | Immutable, thin record annotated `@Event`, with `@EventTag(key = "<Aggregate>")` on the owning id. Persisted forever. | beside the command/feature | `ApplicationDecisionMadeEvent` |
| **Command-side data** | Append-only versioned JSON (`application_data`, `prior_authority_data`) plus drafts. Holds all sensitive/detail content; events only point at a version. | `command/application/data/` | `ApplicationDataStore`, `ApplicationDataPayload` |
| **Write-side route** | Synchronous lookup tables used only to route/lock commands (not read models). | `*Route*Projection` | `ApplicationGroupRouteProjection`, `WorkItemRouteProjection` |
| **Projection (read model)** | Class with `@EventHandler`s (build tables), `@QueryHandler`s (serve queries) and `@ResetHandler` (clear on replay), bound to an Axon processor by `@Namespace("...")`. | `query/**` | `ApplicationProjection` |
| **Query** | Record + `@QueryHandler` on a projection; controllers call a query use case. | `query/**`, `usecase/**` | `FindApplicationDetailQuery` |
| **Integration event router** | Subscribing handler that publishes to SNS *after commit*. Not replayable. | `integrationevent/` | `ApplicationSubmittedEventRouter` |

### Aggregates (consistency boundaries)

| Aggregate | Stream tag | Owns | State |
|---|---|---|---|
| `ApplicationAggregate` | `ApplicationAggregate` | one Application's lifecycle, `applicationVersion` (public optimistic lock), `applicationDataVersion` (internal pointer), current assignee + `assignmentVersion`, or `assignmentGroupId` while a linked group owns its assignment, autoGrant tri-state, overall decision, uploaded-document metadata | `ApplicationState` |
| `LinkedApplicationGroupAggregate` | `LinkedApplicationGroupAggregate` | group identity, single lead, membership, and the members' shared work-item assignment (`caseworkerId`, `assignmentVersion`) | `LinkedApplicationGroupState` |
| `PriorAuthorityAggregate` | `PriorAuthorityAggregate` | one Prior Authority's lifecycle, data version, assignee, documents | `PriorAuthorityState` |

Aggregates are rebuilt on every command by replaying that stream's events through
`@EventSourcingHandler` → `*Evolve`.

## 2. Command-handler style

Use one style: dedicated `*CommandHandler` components receive the aggregate through `@InjectEntity`.
Aggregates hold only `@EventSourcingHandler`s; transactional orchestration across commands and
route locks lives in `*Coordinator`s. ArchUnit enforces the handler and coordinator naming rules.

A command handler is a `@Component` with a `@CommandHandler` that receives the aggregate as a
parameter:
```java
@CommandHandler
public void handle(MakeApplicationDecisionCommand command,
    @InjectEntity(idProperty = "applicationId") ApplicationAggregate application,
    ApplicationDataStore applicationDataStore, EventAppender eventAppender)
```
Because it is a Spring bean it may inject stores, factories and validators. Each linked-group
command is handled by a dedicated `@Component` in `command/application/linkedgroup/handler/`; its
`@CommandHandler` receives the group through `@InjectEntity(idProperty = "groupId")` and delegates
to `LinkedApplicationGroupDecider`.

The flow is the same:

```
Controller → mapper → Command → UseCase → RetryingCommandDispatcher → CommandGateway
   → Command handler:
        1. load aggregate (replay events through Evolve)
        2. validate / decide  (Decider: version check, state rules → event or exception)
        3. if detail changes: append next immutable version to application_data   ← BEFORE the event
        4. eventAppender.append(thin event)                                      ← same transaction
   → event store commit
   → async: projections update read tables; sync: route projections + integration router
```

## 3. Three kinds of event consumer (do not confuse them)

| Consumer | Annotation / config | Timing | May do I/O? | Replayed? | Examples |
|---|---|---|---|---|---|
| Aggregate fold | `@EventSourcingHandler` → `*Evolve` | Every aggregate load | **No** | On every load | `ApplicationAggregate.on(...)` |
| Read projection | `@EventHandler` on a `@Namespace` bound to a **pooled streaming** processor (`AxonEventProcessingConfig`) | Async, after commit; may lag | Yes (DB) | Yes — reset + replay | `ApplicationProjection`, `ApplicationHistoryProjection`, `ApplicationListIndexProjection`, `LinkedApplicationGroupProjection`, `WorkListProjection`, `PriorAuthorityProjection`, `PriorAuthorityHistoryProjection` (the application, list-index, work-list and history projections each process in one lane using `@SequencingPolicy`) |
| Synchronous reaction | `@EventHandler` on a **subscribing** processor with `PropagatingErrorHandler` | Inside the command's unit of work; failure rolls the command back | Deliberately | No | `ApplicationGroupRouteProjection` (`application-group-route`), `WorkItemRouteProjection` (`work-item-route`), `ApplicationSubmittedEventRouter` (`application-submitted-publisher`, SNS on `AFTER_COMMIT`) |

Processor wiring is in `config/AxonEventProcessingConfig`. A new `@Namespace` that is not declared
there gets Axon's default processor settings, so declare it explicitly if you need
pooled/subscribing behaviour or `PropagatingErrorHandler`.

## 4. Data flow: why there are two version numbers and `application_data`

* Events are **thin**: ids, versions, status, decision value, timestamps. No names, notes,
  proceedings, certificates, free text (see [ADR 0002](adr/0002-separate-sensitive-data-from-domain-events.md)).
* Detail lives in `application_data` as a **complete, immutable payload per version**
  (`ApplicationDataPayload.withDecision / withNote / withApplicationUpdate / ...`).
* `applicationVersion` = public optimistic lock (client-supplied `expected` version).
  `applicationDataVersion` = internal pointer chosen by the command side (`state + 1`).
* Projections store thin state + the pinned `applicationDataVersion`, and **hydrate on read**
  (`ApplicationReadModelAssembler`) from the exact version the event pinned. Never "latest".
* Because events carry the pointer, replay of aggregate and projection never needs external
  systems, but it **does** need `application_data` rows to still exist (retention, ADR 0003).

## 5. Change-impact decision table

Legend: **A** = aggregate state/Evolve (+ `@EventSourcingHandler`), **D** = Decider rules,
**H** = command handler, **C**/**UC** = command / use case / controller + mapper,
**E** = event class, **P** = projection(s), **R** = write-side route / sync reaction,
**S** = `application_data` payload, **M** = Flyway migration, **Replay** = projection reset/replay needed.

| Requirement type | Touches | Does **not** touch | Notes |
|---|---|---|---|
| New validation / business rule on an existing command (e.g. "decision needs ≥1 proceeding") | **D** (and its unit tests) | A, E, P | Cheapest change. Only affects new commands; history is unaffected. If the rule needs state not in `*State`, see "new state" below. |
| Rule depends on data that is not aggregate state (e.g. content in `application_data`) | **H** (load via the store, pass to decider) + **D** | A, E, P | Aggregates must not query projections or do I/O. The handler loads command-side data and passes it in, as `decideDecision` receives `ApplicationDataPayload`. |
| Rule needs new aggregate state (e.g. "can't decide if a review flag is set") | **A**: add field to `*State`, update `*Evolve`; existing events must still fold; **D**; **H** if input is new | P usually | Old events won't set the new field → define a default for history. Tests: replay an old event stream. |
| New command that produces an **existing** event (new entry point, same fact) | **C/UC**, **H**, **D**; controller/OpenAPI | A, E, P | Precedent: `decideSubmitDraft` emits the same `ApplicationCreatedEvent` as direct create, so "the projection requires no changes". |
| New command / business fact → **new event type** | **C/UC/controller**, **H**, **D**, **E**, **A** (new `@EventSourcingHandler` + `Evolve.apply`), **P** (each projection that needs it), maybe **R** | — | Mandatory for the aggregate: an unhandled event is silently not folded. Decide per projection (current-state, list-index, history, work-list) whether it reacts. History projection needs a mapped `eventType` string. |
| Change what an existing **event carries** (add field) | **E**, **D/H** (populate), **A** if used for state, **P** if used for read | — | Must be backward compatible with persisted JSON (absent ⇒ default). Do not reuse a field with a new meaning. See [Event evolution](event-evolution.md); add an old-JSON fixture test. |
| Rename/move/retype/remove an event or field | **E** + compat strategy (alias/upcaster/new event) | — | Treat as a breaking, migration-style change; no upcasters exist yet. Needs an ADR. |
| Change **meaning** of an existing event | New event type (+ keep old handlers in A and P) | — | Old events remain in the store forever and are re-folded. |
| New piece of sensitive/detail content on an application | **S** (`ApplicationDataPayload` + a `with...` method), **H** (write new version), thin event pointing at version, **P** hydration/assembler, response mapper/OpenAPI | A (only if state/pointer logic changes) | Never put in event. Complete payload per version, not a patch. |
| New **read** field or filter/sort on an existing list/detail endpoint | **P** (`@QueryHandler`, read model, repository/specification), **M** (read table), response mapper/OpenAPI, **Replay** if populated from historical events | A, D, H, E | Pure query-side change if the data is already in an event or `application_data`. If a historical event lacks the value, replay cannot recover it. |
| New read model / report / endpoint over existing events | New projection class + `@Namespace` + processor bean + `@ResetHandler`, **M**, query record + use case + controller | Command side | Needs a first-time replay to backfill. Must be idempotent and I/O-free apart from its own DB and data store. |
| Change how an existing projection interprets an event (bug fix / new derived column) | **P**, **M** if schema, **Replay** | Command side | Reset only clears its own table; verify counts/spot values afterwards. See [Projections and replay](projections-and-replay.md). |
| Add event to the audit history | `ApplicationHistoryProjection` `@EventHandler` + assembler (+ hydrate description from the data version) | A, D | Needs history replay to be complete for old events. |
| Change who may perform an action / access scope | **UC** security annotation, `query/utils/security/*`, `SecurityConfig` | A, D, E, P (unless read filtering) | Authorisation sits outside the aggregate. Actor identity arrives as the authenticated Entra `oid`, mapped in the controller. |
| New optimistic-locking requirement | **C** (expected version), **D** (compare), **E**/**A** if a new version counter | — | Follow `applicationVersion` / `assignmentVersion` / group version-token precedent. Map conflict exception in `ApplicationExceptionHandler`. |
| Change HTTP error/status mapping | `ApplicationExceptionHandler`, exception class | Everything else | Aggregate/Axon wording must not leak to clients. |
| Cross-aggregate rule (e.g. group assignment, "PA requires GRANTED application") | **H** (coordinator) + routes **R**, or validation/follow-up commands | — | No aggregate may read another's state. Options in use: route tables + locks (`LinkApplicationCoordinator`, `AssignWorkItemCoordinator`), validation commands (`ValidateApplicationGrantedCommand`, `ValidateLinkedGroupAssigneeCommand`), and coordinator-dispatched follow-up commands (`DelegateAssignmentToGroupCommand`, `ReclaimAssignmentFromGroupCommand`). See [ADR 0005](adr/0005-linked-group-owns-assignment.md). |
| New aggregate (new lifecycle with its own invariant) | Aggregate + State + Evolve + Decider, events with `@EventTag`, handlers, projections, route if it is a work item, `AxonArchitectureTest` rules apply | — | Large change. Prefer extending an existing aggregate only if the invariant genuinely belongs in the same consistency boundary. |
| Publish a new integration event (SNS) | New subscribing router on `AFTER_COMMIT` + processor bean + publisher | A, P | Never from aggregate folds or tracking projections (replay would re-publish). |
| Retention / deletion of `application_data` | `ApplicationDataStore`, projection hydrators, history assembler; ADR 0003 | Events (they are not rewritten) | Hydration must treat a missing version deliberately, not as an accident. |
| Data/schema migration of read tables | **M** + **Replay** | A, D, H, E | Flyway migrations are not backfilled from events; replay is. |
| Data/schema migration of `application_data` payload JSON | **S** + tolerant deserialisation | Events | Old versions are immutable and never rewritten; code must read all historical payload shapes. |

### Quick questions to place a requirement

1. *Does it change whether a request is accepted or rejected?* → **Decider** (and handler if it
   needs extra data). Aggregate only if the rule needs new remembered state.
2. *Does it need to remember something new to decide later?* → **State + Evolve + event**.
3. *Does it only change what is shown or searchable?* → **Projection** (+ migration + replay), not
   the command side.
4. *Is there a new business fact worth recording?* → **New event**; then list every consumer
   (aggregate fold, each projection, route, history, integration router).
5. *Is it detailed/free-text/personal data?* → **`application_data`**, not the event.
6. *Must two aggregates agree atomically?* → Not possible with one aggregate transaction; use the
   route-table/lock pattern or rethink the boundary.

## 6. Worked walk-throughs

### A. "Reject a decision unless every proceeding has a reason" (rule only)
Edit `ApplicationDecider.validateDecision`. Add decider unit tests. No event, aggregate, projection,
migration or replay changes.

### B. "Record the reason a decision was overridden" (new sensitive detail)
`MakeApplicationDecisionCommand` gains a field → mapper/OpenAPI; `ApplicationDataPayload` stores it
in the next version (`withDecision`); thin event unchanged (it already points at the version);
read model/assembler exposes it; history description hydrates it. Aggregate and routes untouched.

### C. "Add a 'returned to provider' status change that locks the application"
New command + use case + endpoint, new event, `ApplicationState`/`ApplicationEvolve` (and aggregate
`@EventSourcingHandler`), decider rules on other commands that must reject when returned,
projections (`ApplicationProjection`, list index, work list if it leaves the queue,
history), possibly a `WorkItemRouteProjection` handler to drop command authority, and a migration
if new read columns are added. No replay is needed for old data because the event is new.

### D. "Show assignment date in the work list"
`WorkItemAssigned` already carries `occurredAt`. Add the column + `WorkListProjection` handler +
query/mapper; replay `work-list-projection` to backfill. No command-side change.

### E. "Notify another system when a decision is made"
New subscribing router modelled on `ApplicationSubmittedEventRouter` (AFTER_COMMIT) and a processor
bean in `AxonEventProcessingConfig`. No change to aggregate or existing projections.

## 7. Rules of thumb that decide *where* code goes

* Decide in the **decider**, remember in **state/evolve**, record in an **event**, store detail in
  **`application_data`**, show in a **projection**.
* The aggregate fold must be deterministic and I/O-free; projections may do DB I/O but must be
  replay-safe; only subscribing routers/after-commit hooks may cause external effects.
* Aggregates never query projections; queries never dispatch commands (enforced by
  `AxonArchitectureTest`, together with "commands/events are records" and "no event nested in an
  event").
* Write the `application_data` row first, then append the event (same transaction).
* `sendAndWait` returning means the event is committed, **not** that tracking projections are
  current. Tests and endpoints that need read-your-writes use a subscription query
  (`SubscriptionProjectionGateway`, as in `CreateApplicationUseCase`) or return `202`.
* Concurrency: `RetryingCommandDispatcher` retries once on `ConcurrencyException` /
  `AppendEventsTransactionRejectedException` / unique-key violation. Handlers must therefore be safe
  to re-run.

## 8. Things to be aware of in the current code

* **Multiple projections consume the same events independently** (`application-projection`,
  `application-list-index-projection`, `application-history-projection`, `work-list-projection`,
  group, prior authority). A new/changed event usually needs to be considered for all of them;
  they can lag one another.
* **`@SequencingPolicy`** on the application, list-index, work-list and history projections
  serialises their handlers for ordering (creation vs. membership and assignment events). New
  handlers there inherit that ordering.
* **Draft lifecycle**: an Application in draft has `state.status == null`; documents upload before
  `ApplicationCreatedEvent`; this is the guard used by `requireApplicationDraft`.
* **Documentation drift risk.** Several docs describe processors/flows in prose (for example the
  linking router/initializer). When in doubt, check `AxonEventProcessingConfig`, the `@Namespace`
  value and the class's `@EventHandler` methods.

* **Linked-group assignment.** `LinkedApplicationGroupAggregate` owns the shared assignment and
  `ApplicationState.assignmentGroupId` marks delegation. Assignment never fans out on the command
  side; each query projection applies group events through its own tracking table, where every
  member row carries the group's caseworker and assignment version.
  **Any request that changes a group, changes the assignment of an active manual-assessment
  application in a group, records an application's readiness for manual assessment in a group, or
  decides an active manual-assessment application in a group, must lock the group's
  `application_group_route` rows before its first command** — the one-lane projections depend on
  it and nothing checks it at runtime (see [Guardrails](guardrails.md)). Terminal auto-grant
  decisions are exempt: an auto-granted application is never active as a manual-assessment work
  item, has no work-item route, and `RecordAutoGrantOutcomeUseCase.record(Object)` dispatches its
  decision directly.

## 9. Test layers to update with a change

| Change | Tests to add/update |
|---|---|
| Decider rule | Plain unit tests of the decider (`src/test/.../command/application/...`) |
| Evolve/state | Aggregate replay tests (fold an event list, then apply a command that depends on it) |
| Command handler | Handler tests with mocked stores / Axon test fixtures |
| Event shape | Old-JSON fixture deserialisation test; sensitive-field absence assertion |
| Projection | Projection unit tests incl. reset/replay; `src/integrationTest` against PostgreSQL (`PostgresAxonIntegrationTest`, `ApplicationProjectionSequencingIntegrationTest`) |
| API behaviour | Controller tests, integration tests, Pact provider states (`pactTest`) |

See [Testing Axon code](testing-axon-code.md), [Guardrails](guardrails.md),
[Event evolution](event-evolution.md) and [Projections and replay](projections-and-replay.md) for
detail.

## 10. Architecture conformance review and suggested changes

Overall the code applies the architecture well: events are thin and detail lives in versioned
`application_data`; aggregate event-sourcing handlers are I/O-free and delegate to `*Evolve`;
`application_data` is written before the event is appended; every replayable projection has a
`@ResetHandler`; SNS publication happens only in an after-commit hook; no aggregate uses
`QueryGateway` and `CommandGateway` is used only on the command side. The items below are
deviations or inconsistencies, ordered by risk.

| # | Observation | Why it matters | Suggested change |
|---|---|---|---|
| 1 | Resolved by ADR 0005: the read-model-driven dispatcher was removed; assignment decisions use aggregate state and locked write-side routes. | — | — |
| 2 | The decider is used inconsistently. `ApplicationDecider` covers create, decision, note, ready and update, but assignment/unassignment rules sit in `ApplicationCommandHandlerSupport` and the handlers; `MarkApplicationReadyCommandHandler` computes versions and builds its event inline; draft document-upload rules are in `ApplicationDocumentUploadCommandHandler`. | Rules are harder to find and cannot be unit-tested without Axon. New contributors cannot rely on "rules live in the decider". | Move these decisions and event construction into `ApplicationDecider` so handlers only load data, call the decider and append. |
| 3 | `RecordAutoGrantedOutcomeCommandHandler` calls the static `MakeApplicationDecisionCommandHandler.recordDecision`. | Couples one handler to another's internals. | Move the shared logic to the decider or a shared support class. |
| 4 | Resolved by ADR 0005: assignment coordinators use `RetryingCommandDispatcher`. | — | — |
| 5 | Resolved by ADR 0005: assigning a linked group is one command and one event on the group. | — | — |
| 6 | About 17 classes under `query` import `command.*` types (`ApplicationDataStore`, `ApplicationDataPayload`, event classes). `AxonArchitectureTest` only forbids `CommandGateway` in query. | Read and write sides are coupled through shared persistence classes, so a command-side refactor can break projections. | Move the data store and payload to a neutral shared package. Add an ArchUnit rule that `query` must not use aggregates, deciders or command handlers. |
| 7 | `ApplicationGroupRouteProjection` and `WorkItemRouteProjection` are write-side routing tables maintained by subscribing handlers. `WorkItemRouteProjection` has no reset handler. | A missing or corrupt route cannot be rebuilt by replay. | Document this clearly and consider a repair or rebuild procedure. |
| 8 | `PriorAuthorityDraftUpdatedEvent` has an empty `@EventSourcingHandler`. | Intentional (stream concurrency only) but easy to mistake for a bug. | Keep it, and keep the explanatory comment. |
| 9 | Guardrails are partly manual. | A new event without an aggregate fold handler is silently ignored on replay. | Add ArchUnit rules: every `@Event` has an `@EventTag`; every event has a matching `@EventSourcingHandler` on its aggregate; aggregates and evolve classes import no stores or repositories. Declare a processor bean in `AxonEventProcessingConfig` for every `@Namespace` (for example `application-list-index-projection`). |

Suggested order: 2/3 (a refactor that keeps behaviour), then 6 and 9 (low risk, and they prevent
regressions).
