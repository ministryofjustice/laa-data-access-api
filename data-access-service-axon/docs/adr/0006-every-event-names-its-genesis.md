# ADR 0006: Every event names its genesis

- Status: Proposed
- Date: 2026-11-12
- Updated: 2026-11-16
- Scope: `data-access-service-axon`

## Related papers

- [Genesis ID introduction][introduction]: the plain-language explanation.
- [Genesis ID][proposal]: the wider data stewardship proposal.

This ADR records the ADS implementation decision. Adoption beyond ADS and the wider retention
policies are not implied by it. The implementation observations below refer to
`feat/genesis-ids` at `52738129`, not a deployed service.

## Context

An application's lifecycle spans several aggregates. Application and Prior Authority events,
including their work-item assignment events, belong to their respective aggregate streams.
Linked-group events belong to a separate group stream. Each history projection currently
reconstructs the link back to the application in its own way.

ADS needs a consistent way to find the events for an application's lifecycle for MVP application
history. The same identity can later help determine the scope of retention and erasure, while
keeping independently governed roots distinct.

Lifecycle grouping must not change aggregate identities or command handling. In the current
aggregate-based event store, using the application's aggregate key for PA or group events is not
a substitute for a separate read-side history.

Prior art in this repository: the event-scoped sensitive data spike
(`spike/event-scoped-pii-payloads`, `docs/spikes/event-scoped-pii.md`,
"History reads a genesis stream"). That spike uses the earlier term "genesis stream";
the papers and this ADR call the read-side view a **lifecycle stream**.

## Definition

A **genesis ID** is the stable identifier for an independent data lifecycle. A **genesis**
groups a root record and its dependents for history, retention and deletion, starting when
the root record is created.

A **lifecycle stream**, identified by the genesis ID, brings together references to domain
events recorded across the system. A pointer retrieves the original event; it is not a copy
of its contents. The stream is read-only: it reports what happened, rather than accepting
commands or enforcing business rules. New entries appear as events are recorded.

| Kind | Test | Genesis | Examples |
|---|---|---|---|
| Root | Has an independently governed lifecycle | Its own | Application in the current ADS mapping; a separately governed user record in another system |
| Dependent | Belongs to one root, fixed at creation; cannot be retained beyond it | Its root's | Prior Authority, redetermination, note, document |
| Relationship | Connects roots without making one dependent on the other | No separate genesis for the link itself | Linked group |

A dependent may be deleted earlier without starting a separate genesis. A record that needs
an independent lifecycle must instead be modelled as a root.

The general concept is not limited to applications or cases. ADS currently uses the root
application's UUID as the genesis ID; this is an implementation mapping, not the definition of
genesis. A wider Legal aid case mapping must be agreed before extending that assumption to
other systems.

## Decision

Every stored Axon domain event identifies the genesis or geneses it concerns, through
`GenesisScoped.genesisIds()`. Readers derive those IDs from the event's own fields rather
than reconstructing them from current ownership or group membership.

### Rules

1. **The creator assigns the genesis ID.** We assign IDs to roots we create; a root started by
   another system retains its genesis ID. IDs never change or get reused. They must be globally
   unique or qualified by their issuing system; ADS currently accepts UUIDs only.
2. **Dependents never move between geneses.** A dependent of another root is not created by
   reassigning an existing dependent. A new independent root has its own genesis ID and can
   record a "created from" relationship. This does not prescribe copying data.
3. **No nesting.** A dependent of a dependent belongs to the root's genesis.
4. **Every stored Axon domain event names at least one genesis.** External event ingestion is
   future work. The wider model permits references to source events or translation into local
   events, with the relevant genesis IDs confirmed first.
5. **Relationship events identify the affected roots.** Ending a relationship does not remove
   already-recorded pointers. Retention of those pointers and underlying events remains subject
   to policy.
6. **Lifecycle streams must be self-contained histories.** Reading one must not require
   following another lifecycle stream. Fetching an original event through its pointer is
   expected. Completeness, delivery, duplicate handling and ordering still require explicit
   implementation rules.
7. **Retention policy determines deletion.** Dependents may start or extend a retention period,
   or be deleted earlier, but cannot be retained beyond their genesis. Deletion must reach the
   relevant content across stores and consumers; an ID does not perform or prove deletion.
   Only explicitly permitted history may remain, as discussed in [ADR 0003][retention-adr].
8. **Shared identity does not imply a shared lifecycle.** A separately governed person or
   caseworker account can have its own genesis. Related cases are not automatically its
   dependents and do not automatically share its retention schedule.

### Linking and unlinking: event-specific scope

Linking retains both roots and genesis IDs. Each lifecycle stream contains pointers only to
events concerning its genesis. A linking event concerns both roots and therefore appears in
both streams. Adding a PA to one of those applications concerns only its parent's genesis,
even while the applications are linked.

Linking does not broaden the scope of subsequent events or copy earlier histories. An unlink
event appears in the streams it names without removing previously recorded pointers. Neither
root closes and existing dependents do not transfer.

These are pointers to the same events, not duplicate business commands or duplicate domain
events. The lifecycle streams do not enforce the link or its retention consequences.

The current `genesisIds()` implementations express this event-specific scope. The pointer
stream itself is follow-on work. Self-contained means that a stream includes the events
concerning its genesis, not that it includes every event concerning every linked member.

### Current implementation

- `command.GenesisScoped` declares `Set<UUID> genesisIds()`, implemented by every `@Event`.
  Its helpers de-duplicate IDs and reject nulls. Non-empty is a contract requirement; the
  helpers do not themselves reject empty input.
- Prior Authority events name the parent application (`parentApplicationId`, or
  `applicationId`). Application notes, documents and decisions name the application.
  `WorkItemAssigned` and `WorkItemUnassigned` name the owning application's genesis,
  not the caseworker's identity.
- Linked-group events currently name these roots:

  | Event | Geneses named |
  |---|---|
  | Group created | Members and lead |
  | Member added or removed | Lead and member |
  | Group dissolved | Members, former lead and removed members |
  | Lead changed | Previous and new lead |

- New fields where no genesis could be derived:
  - `WorkItemAssigned`, `WorkItemUnassigned`: `applicationId` (equals `workItemId` for
    application work items; the parent for Prior Authority work items).
  - `PriorAuthorityDocumentTypeUpdatedEvent`: `parentApplicationId`.
- `GenesisScopeArchitectureTest` (ArchUnit) fails the build if an `@Event` does not implement
  `GenesisScoped`. It also checks a dissolved group's IDs and one missing-ID failure. It
  does not test delivery into the planned lifecycle streams.

### Planned use

- **Next:** a lifecycle stream with one pointer row per domain event per genesis, replacing
  the existing history projections. "Application history" is the ADS view built from events
  referenced by that lifecycle stream, including PA and relationship events; it is not a
  replay of only the Application aggregate.
- **Later:** genesis-scoped retention and erasure. Lifecycle events supply evidence for
  applying policy as facts and retention triggers become known. Drafts need a rule or review
  arrangement from creation; uncertainty about a final disposal date is not permission for
  unbounded retention.

## Alternatives considered

- **Using the application's aggregate key for lifecycle grouping:** rejected. Aggregate
  addressing and read-side lifecycle history are different concerns.
- **A dedicated `genesisId` field on every event:** rejected. All but three events already carry
  the necessary fields for the current ADS mapping; a duplicate field can disagree with them.
- **Resolving the genesis in a projection:** rejected. Every consumer would need lookup state,
  and events would no longer be self-describing. In particular, current membership cannot
  reliably reconstruct which roots a historical event concerned.
- **One genesis per aggregate:** rejected. Several independently updated aggregates can
  belong to one governed lifecycle. Aggregate identity alone does not express that boundary.

## Consequences

- New `@Event` types must implement `GenesisScoped`; the build enforces this.
- No upcasters: stored events load the new fields as `null`. Dev and staging require a data
  reset before `genesisIds()` is used.
- Genesis ID set order is not defined and is not event-history order.
- Event pointers and identifiers are not automatically anonymous. Retention must account for
  content versions, documents, downstream copies and references held by other geneses.
- Linked-root schedules and relationship content remain policy questions. Keeping group
  content until the last member is deleted is not an approved default and must not preserve
  information about earlier-deleted members that should have been erased.
- This ADR changes event identification, not business rules for linking, assignment or
  retention. In particular, [ADR 0005][assignment-adr] remains the command-side assignment
  decision; its per-application assignment events are not duplicate lifecycle pointers.

## Implementation status and follow-on work

The event scopes above are intentional, not incomplete sharing between linked roots. Member
add/remove events name the lead and member; lead-change events name the old and new lead.
Other members do not receive a pointer merely because they belong to the group.

The remaining work concerns the history reader and wider adoption, not a change to these scopes:

| Topic | Evidence in this branch | Follow-on work |
|---|---|---|
| Application history delivery | `ApplicationHistoryProjection`, `PriorAuthorityHistoryProjection` and their assemblers still provide history. This branch adds `GenesisScoped`, not the lifecycle-stream replacement. | The papers describe the intended ADS view. Treat the pointer stream and replacement history reader as follow-on work, not delivered by this ADR's branch. |
| Wider identity model | `GenesisScoped` uses `Set<UUID>` and derives IDs from root application fields. | Person/account geneses, non-UUID external identities and a broader case-file mapping are not implemented here. Agree their issuing and mapping contracts before adoption across systems. |

Retaining both IDs on link does not establish shared retention, and a lifecycle stream does
not replace the domain's transaction or concurrency rules. Failure recovery for pointer
processing is a read-side concern, separate from whether a business command succeeds.

## Reconsider when

- A dependent needs to change genesis or be retained independently.
- Another system starts geneses that we ingest, for example during migration.
- A wider case or user-record lifecycle cannot be represented by the current application UUID.
- Retention policy requires deletion of identifiers or shared event references.
- The event store offers a different read-side indexing mechanism without changing aggregate
  addressing or the lifecycle contract.

[introduction]: https://dsdmoj.atlassian.net/wiki/spaces/WHCZA/pages/6371999854/Genesis+ID+introduction
[proposal]: https://dsdmoj.atlassian.net/wiki/spaces/WHCZA/pages/6367543645/Genesis+ID
[retention-adr]: 0003-define-application-behaviour-after-retention-deletion.md
[assignment-adr]: 0005-linked-group-assignment-stays-per-application.md
