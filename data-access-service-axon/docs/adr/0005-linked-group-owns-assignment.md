# ADR 0005: The Linked-Group Aggregate Owns Its Members' Shared Assignment

- Status: Accepted
- Date: 2026-10-22
- Scope: `data-access-service-axon`

## Context

Applications in a linked group share one caseworker assignment. Previously, assigning a group
fanned out commands to the member application aggregates, while a read-model-driven event
dispatcher decided which members to update. That approach could no-op while a group was being
created, make decisions from stale projections, and leave a split assignment when only some
members were updated. It also conflicted with the command/query and aggregate-boundary rules in
`AGENTS.md`: decisions must not use eventually consistent projections, and application
coordinators must not substitute for aggregate authority.

The Axon JPA event store writes an event to one aggregate stream. A group event therefore cannot
also be an event in every member application's stream.

## Decision drivers

- Keep each business invariant within one consistency boundary.
- Do not fan out commands when assigning or unassigning a group.
- Never decide assignment from a read projection.
- Keep query projections independently replayable.
- Use one lock order for commands that cross group, route, and application boundaries.
- Ensure clients cannot act on assignment state from a different owner or an older version.

## Decision

1. `LinkedApplicationGroupAggregate` owns the shared caseworker assignment and its assignment
   version, as well as group identity and membership.
2. `ApplicationAggregate` records delegation through `assignmentGroupId`; direct assignment or
   unassignment commands are rejected while a linked group owns that application's assignment.
3. Only active manual work items (ready and undecided) follow the group's assignment. Not-ready,
   auto-granted, and decided members are not changed by group assignment events.
4. Joining resolves the group's caseworker using the joining rules: an existing group assignee
   remains; if the group is unassigned, an active joining member's assignee becomes the group
   assignee; when creating a group, the target lead's assignee takes precedence, otherwise an
   active joining member's assignee is inherited. Equal assignments cause no additional assignment
   event. The group event is the sole command-side assignment change.
5. Assignment versions exposed to clients are opaque tokens naming the owner (the item or its
   linked group) and the internal version. Joining or leaving changes the owner and invalidates
   tokens from the previous owner. Clients pass tokens back unchanged.
6. Readiness is coordinated by `MarkApplicationReadyCoordinator`, which records readiness and, for
   a grouped application, dispatches `InheritLinkedGroupAssignmentCommand`. Decision coordination
   uses `ValidateLinkedGroupAssigneeCommand` before the application decision, and records the
   validated group owner in `groupAssigneeValidatedFor`. The readiness coordinator preserves the
   existing API authorization: a named API caseworker or the `LAA_DECIDE_AUTO_GRANT` service role
   may call the endpoint. Decision authorization remains for API caseworkers.
7. `work_item_route` is the durable command-routing authority for work items. It records
   `STANDALONE` or `LINKED_GROUP` and, for the latter, the group ID.
8. Each of the work-list, application, list-index, and history projections owns its linked-group
   assignment tracking tables. These projections process their own events in one lane and are
   rebuilt by replay.
9. Requests that change a linked group, change the assignment of an active manual-assessment
   application in a group, record an application's readiness for manual assessment in a group, or
   decide an active manual-assessment application in a group lock that group's
   `application_group_route` rows. Terminal auto-grant decisions are exempt: an auto-granted
   application is never active as a manual-assessment work item, has no work-item route, and
   `RecordAutoGrantOutcomeUseCase.record(Object)` dispatches its decision directly. Coordinators
   lock routes in this order: application-group routes by ascending application ID, work-item
   routes by ascending item ID, application aggregate commands by ascending application ID, then
   the group aggregate command. Unlink performs reclaim commands after the group command because
   it needs that command's result; decision validates the group assignee before dispatching the
   application decision. The lock rule is documented and reviewed, not enforced at runtime.
10. Dedicated `*CommandHandler` components own Axon command-handler methods; transactional
    orchestration belongs in `*Coordinator` components. ArchUnit checks that classes named
    `*CommandHandler` declare an Axon `@CommandHandler`, and that annotated methods are declared
    only in classes with that name.

## Why link and unlink write to applications

Assignment itself never fans out: one group assignment request appends one command and one event
to the group stream regardless of group size. Linking and unlinking write to at most two
application aggregates in the same transaction to record that assignment ownership moved. This is
necessary because an event in the group stream cannot also belong to an application's stream.
Joining dispatches delegation before the group membership command; leaving dispatches reclaim
commands after it returns the group's assignment state.

## Alternatives considered and rejected

### Multi-tag domain-change events

Rejected because the event store and current Axon aggregate model write each event to one aggregate
stream. A group assignment fact cannot be recorded atomically in both the group and every member
stream using the current design.

### Asynchronous handover with `PENDING_LINKED_GROUP` and `TRANSITIONING` route states

Rejected because a delayed handover introduces states in which route authority and assignment
ownership disagree, leaves a window for commands to be rejected or routed inconsistently, and
requires recovery logic for partially completed transitions.

### Apply the inherited assignment later by reading the route table

Rejected because the route table is for synchronous command routing, not a source of domain
assignment state. Delayed application could also leave an active work item with a stale or absent
assignment.

### Revert to the pre-link caseworker

Rejected because the former standalone assignee may differ from the caseworker that owns the
group assignment. Reverting on unlink would not preserve the assignment the member actually used
while in the group.

### Record the decider's caseworker on the decision event

Rejected because the actor who submits the decision is not necessarily the work item's assigned
caseworker. Decision validation proves the assignee against group state; event metadata continues
to identify the request actor.

### Numeric assignment versions with "max + 1" jumps on joining

Rejected for the public token contract. Opaque tokens identify the owner as well as the version,
which invalidates previous-owner tokens without exposing arithmetic and matches the linked-group
version token model. The internal application assignment version on reclaim still advances to
`max(application assignment version, group assignment version) + 1`.

### Runtime lock guard in `RetryingCommandDispatcher`

Rejected during review in favour of documenting the lock rule and checking coordinators in review.
A generic dispatcher cannot reliably infer all group rows that a command sequence must lock.

## Consequences

### Positive

- Group assignment is one command and one event, independent of membership size.
- Aggregate state and locked write-side routes, not read projections, determine command outcomes.
- Query models can be independently reset and replayed from events.
- Owner-encoded tokens reject updates based on stale assignment boundaries.

### Negative

- The assignment token is a breaking API change from an integer to an opaque string.
- Four query projections must process in one lane and maintain their own tracking tables.
- The route-lock rule depends on reviewers; it is not enforced at runtime.
- An active manual-assessment member decided while grouped has no current assignee in its
  application aggregate; its query model retains the caseworker shown at decision time.
- Tracking tables are rebuilt by replay of their owning projections.

## Reconsider when

- The event store supports atomic multi-tag events; or
- linked-group rules grow beyond membership and shared assignment.

## Related documentation

- [Linked applications](../linked-applications.md)
- [Endpoint errors](../endpoint-errors.md)
- [Guardrails](../guardrails.md)
- [Projections and replay](../projections-and-replay.md)
- [Linked-group assignment sequence](../sequence-diagrams/05-linked-group-assignment.md)
