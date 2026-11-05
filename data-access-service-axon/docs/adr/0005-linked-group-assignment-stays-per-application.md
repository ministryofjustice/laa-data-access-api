# ADR 0005: Linked-group assignment stays per application

- Status: Accepted
- Date: 2026-10-28
- Scope: `data-access-service-axon`

## Context

Ticket DSTEW-2284 defines these link scenarios:

| Scenario | Group before link | Joining application | Result |
|---|---|---|---|
| AC1 | Assigned to a caseworker | Unassigned | The application takes the group's caseworker |
| AC2 | Assigned to a caseworker | Assigned to another caseworker | The application takes the group's caseworker |
| AC3 | Unassigned | Assigned to a caseworker | The group takes the application's caseworker |
| AC4 | Unassigned | Unassigned | No assignment changes |

DSTEW-2283 also requires assigning related applications together. The earlier read-model-driven
dispatcher could make decisions from projections that had not caught up. It treated the lead's
assignment as the group's assignment, which could miss create-time and AC3 changes. It also offered
no group-wide unassign.

## Decision

Assignment stays on each `ApplicationAggregate`. A linked group has no assignment state of its own.
The existing per-application `caseworkerId`, `assignmentVersion`, `WorkItemAssigned` and
`WorkItemUnassigned` events remain authoritative. The lead has no special role in assignment.

An **active work item** is an application whose `autoGranted` state is `MANUAL` and whose
`overallDecision` is null. The **group assignment** is derived from active members only:

- `Assigned(C)` when there is at least one active member and every active member is assigned to C;
- `Unassigned` when there is at least one active member and every active member is unassigned;
- `None` when there are no active members.

Disagreement among active members breaks the assignment rule and returns HTTP 409. Inactive
members do not give or receive the group's caseworker.

The command side applies these rules:

1. Assigning a grouped application checks that the addressed item is active, its assignment version
   is current, and it is unassigned. It assigns every active member to the caller in the same
   transaction. If another active member is held by a different caseworker, the request returns 409
   without changes. Assigning an already-assigned item returns 409; unassign the group before a
   different caseworker takes it.
2. Unassigning a grouped application checks that the addressed item is active, its assignment
   version is current, and it is assigned. It unassigns every assigned active member in the same
   transaction, including members held by another caseworker.
3. Standalone applications and Prior Authorities keep their existing assignment behaviour.
4. On link, the group's assignment is derived from its members before the new member joins. For a
   newly created group, the target is the existing group. An active joining application takes an
   assigned group's caseworker (AC1/AC2). If the group is unassigned or has no active members, an
   active assigned joining application assigns every active member (AC3). Otherwise assignments
   do not change (AC4).
5. When a grouped application becomes ready, it takes the assignment of the other active members.
   It is assigned or cleared to match them; if there are no other active members, its assignment is
   unchanged. A readiness result already recorded changes nothing.
6. Link, unlink, lead change and dissolution do not otherwise change assignment. Decisions remain
   per application.
7. Assignment versions remain per application and increase by one for each assignment change.

All active members therefore remain unassigned or share one caseworker when every command path
follows these rules. No aggregate enforces this group-wide invariant.

### Command pipeline and route locks

A coordinator applies endpoint security where required, locks and routes one command. A handler
loads its primary aggregate with `@InjectEntity` and uses Axon's `StateManager` and
`LinkedGroupMembers` to load any other required aggregates. The pure
`LinkedGroupAssignmentDecider` makes the business decision, and the handler appends the group and
application events together. The decider is the seam where a future rules engine could be used,
with the same consistent state as input.

Writers follow this lock order:

1. Lock the relevant `application_group_route` rows with `PESSIMISTIC_WRITE` in ascending
   application ID order, using one statement.
2. Re-read membership after acquiring the locks. If membership changed while waiting, return 409
   (`Linked group membership of application <id> changed; retry the request`) rather than taking
   more locks in a second pass.
3. Dispatch one command. Its handler loads the group and member aggregates and appends all resulting
   events.
4. Do not lock `work_item_route` before appending to an application. Decisions append to the
   application stream before the route projection removes its work-item route. Locking that route
   first could deadlock against the event append.

Two ArchUnit rules in `AxonArchitectureTest` enforce this at build time. Only
`ApplicationGroupRouteResolver` and the route projection may run route-lock queries. Only
`*Coordinator` classes may send the commands that depend on those locks: establish, add, remove
and change lead; linked-group assign and unassign; direct application assign and unassign; and
readiness. Code review checks that each coordinator calls the resolver before sending in the same
transaction.

For readiness, the retry boundary includes the complete lock-and-dispatch attempt. A retry runs in a
fresh transaction and reacquires its route locks.

## Alternatives considered

- **Group-owned assignment:** rejected. The explored DSTEW-2284 design needed delegation markers,
  tracking tables, single-lane projections and a breaking assignment-token change.
- **Read-only commands followed by per-member commands:** rejected. The handlers can load member
  aggregates and append all changes atomically in one command.
- **A batch endpoint:** rejected. It would expose group membership and member versions to clients.
- **Client-side looping:** rejected. Separate requests can leave a partially assigned group.
- **Unassign, then assign:** rejected. It writes extra events and exposes a transient unassigned
  state.
- **Locking `work_item_route`:** rejected because its lock can conflict with the application event
  append and deadlock.
- **A generic lock check in `RetryingCommandDispatcher`:** rejected. A generic dispatcher cannot
  know which routes each command needs.
- **A runtime dispatch guard:** removed. A transaction-bound lock registry and a command dispatch
  interceptor rejected these commands when their locks had not been recorded. It needed its own
  per-command list, a test opt-out property and about 600 lines of code and tests. The ArchUnit
  coordinator rule catches the realistic mistake, sending a command from outside a coordinator, at
  build time instead.

## Consequences

- The group-wide assignment invariant is maintained by the command rules and route locks, not by an
  aggregate. A future command that changes membership or assignment must be sent from a coordinator
  that uses the resolver, and must be added to the ArchUnit coordinator rule.
- A group change can append one event for each affected application.
- Membership changes found while locking, disagreement among active members, and an attempted
  takeover return 409.
- Read projections may lag after a successful command.

## Reconsider when

Reconsider this decision if assignment becomes a regulatory invariant or linked groups grow large
enough that loading and appending across all members is no longer practical.
