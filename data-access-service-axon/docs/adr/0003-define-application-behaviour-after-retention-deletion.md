# ADR 0003: Define Application Behaviour after Retention Deletion

- Status: Proposed
- Date: 2026-07-20
- Scope: `data-access-service-axon`

## Context

ADR 0002 separates thin domain events from complete sensitive payloads stored in immutable
`application_data` versions. The restricted `delete_application_data_for_retention(UUID)` database
function can delete every payload version for one application while leaving its event stream and
query projections in place.

The current implementation has no explicit application lifecycle state representing that deletion.
After the rows are removed, Axon can still rebuild the aggregate's control state, including its
identifier, versions, assignment, and linking state. However, the aggregate's
`applicationDataVersion` points to a row that no longer exists.

This produces inconsistent behaviour:

| Operation | Current result after payload deletion |
|---|---|
| Aggregate replay | Succeeds from thin events |
| Get one application | Appears not found because hydration returns no application |
| List applications | Application is silently omitted during hydration |
| Get history | Thin history is returned; entries that need detailed data fall back to thin payloads |
| Get notes | Fails when the referenced payload cannot be loaded |
| Make a decision | Fails when the current payload cannot be loaded |
| Assign or unassign an assigned caseworker | Fails when the current payload cannot be loaded |
| Unassign an already-unassigned application | Succeeds without reading the payload |
| Identical creation retry | Can still succeed from the fingerprint retained in the event stream |

Missing payloads currently surface through a mixture of apparent absence, silent filtering,
degraded history, successful no-ops, and internal server errors. Retention deletion therefore works
at the storage level but does not yet define a coherent domain or API lifecycle.

## Decision drivers

- Make retention deletion an intentional domain state rather than an accidental hydration failure.
- Prevent deleted PII from being recreated or inferred through normal commands.
- Return stable, understandable API responses after deletion.
- Preserve the permitted non-sensitive business timeline and group relationships.
- Ensure query results do not silently change because a join happens to fail.
- Make deletion observable and auditable without placing deleted data back into the event stream.
- Avoid claiming that a database function alone completes the retention workflow.

## Proposed decision

Introduce an explicit retention-deleted state for an application. A successful retention workflow
will:

1. record a thin `ApplicationDataDeletedForRetentionEvent` containing only the application ID,
   deletion timestamp, and non-sensitive retention/audit metadata approved for long-term storage;
2. set the application aggregate's `dataDeletedForRetention` flag from that event;
3. update current-state and history projections to represent the retention-deleted state
   explicitly;
4. delete all `application_data` versions for the application through the restricted database
   function;
5. reject commands that require deleted application data with a stable domain exception mapped to
   `410 Gone`;
6. preserve only explicitly approved thin history and linking information.

`410 Gone` is preferred to `404 Not Found` because the system knows that the application existed and
that its detailed representation was deliberately removed. The public response must not expose the
old data version, deletion mechanism, event-store details, or database implementation.

The retention-deleted state must be determined from a domain event, not inferred from a failed
`application_data` lookup. A missing payload without the retention-deletion event is corruption or an
incomplete retention workflow and should remain operationally distinguishable from legitimate
deletion.

## Proposed command and query behaviour

| Behaviour | After explicit retention |
|---|---|
| Aggregate replay | Rebuilds successfully and sets `dataDeletedForRetention = true` |
| Detailed application query | Returns `410 Gone`, subject to confirmation of the public API contract |
| Application list | Excludes retention-deleted applications by an explicit state filter, or returns an approved tombstone when requested |
| History query | Returns only approved thin history; deleted descriptions, notes, individuals, and proceedings remain unavailable |
| Decision, assignment, unassignment, and note commands | Return `410 Gone` before attempting payload access |
| Linking commands | Allowed only if the agreed policy permits a retention-deleted application to remain a group member; otherwise rejected explicitly |
| Creation retry using the same ID | Rejected as gone; retention must not permit the deleted application to be recreated accidentally |

Whether retention-deleted applications appear as tombstones in administrative searches and whether
they may remain in linked groups require product and information-governance decisions before this
ADR can be accepted.

## Transaction boundary

The retention-deletion event and payload deletion must not leave contradictory states such as:

- event says retention-deleted but payload rows still exist;
- payload rows are gone but no retention-deletion event exists.

For the current single-service, single-PostgreSQL design, the preferred implementation is one
controlled command transaction using the same Spring transaction manager as the Axon JPA event
store and `application_data`. The aggregate handles a retention command, applies the thin event,
and an authorised retention component invokes the deletion function within that transaction.

The exact ordering must be tested against commit failure:

- the command must verify the application and authorisation before deletion;
- a deletion failure must prevent the retention-deletion event from committing;
- an event-store commit failure must roll back the deletion;
- retrying the retention command must be idempotent.

If retention later crosses database or service boundaries, a single transaction will no longer be
available. This ADR must then be revisited in favour of a recoverable workflow with explicit pending
and failed states.

## Consequences

### Positive

- All data-dependent operations receive one deliberate post-retention outcome.
- Legitimate deletion is distinguishable from missing or corrupt payload data.
- Aggregate replay remains possible without reintroducing PII.
- Thin history can be retained according to an explicit policy.
- Search exclusion and tombstone behaviour become intentional and testable.
- Retention becomes an auditable application operation rather than an isolated DBA action.

### Negative

- Retention becomes a domain workflow requiring command, event, projection, API, and database work.
- The event stream permanently records that the application existed and was retained/deleted.
- Identifiers and retention metadata in the thin deletion event still require privacy review.
- Linked-group behaviour needs a defined policy when one member is retention-deleted.
- A `410 Gone` contract may require OpenAPI and client changes.
- Operational deletion cannot safely remain an uncoordinated direct function call.

## Alternatives considered

### Treat missing payloads as `404 Not Found`

Rejected because it hides the distinction between an unknown ID, corruption, and intentional
retention. It also does not solve inconsistent list, history, and command behaviour.

### Keep the current best-effort hydration behaviour

Rejected because applications silently disappear from lists while some operations return server
errors and others still succeed. The result depends on implementation details rather than policy.

### Delete the event stream and all projections as well

Not selected because deleting or rewriting event streams can break aggregate sequence integrity,
group history, audit requirements, and replay. It may still be required if identifiers themselves
must be erased; that would require a different architecture and privacy decision.

### Retain a redacted final payload row

Not selected because it complicates the meaning of immutable versions and risks retaining fields
that should have been deleted. An explicit thin retention-deleted state expresses the same control
fact more clearly.

### Continue allowing commands that do not currently need payload data

Rejected as a default because behaviour would depend on handler implementation. A future code
change could accidentally make a previously allowed command read or recreate deleted content.
Allowed post-retention operations should be explicitly listed instead.

## Acceptance criteria

Before changing this ADR to Accepted, agree:

- whether the public response is `410 Gone` and its exact Problem Detail;
- whether retention-deleted applications are excluded or represented as tombstones in list
  responses;
- which thin history fields may remain available;
- whether retention-deleted applications remain linked-group members;
- who is authorised to request and execute retention;
- which metadata may be placed in the retention event;
- how backups, replicas, logs, and downstream consumers satisfy the same retention policy.

The implementation must then demonstrate:

- atomic event-and-deletion commit and rollback;
- idempotent retry;
- aggregate replay after deletion;
- stable behaviour for every command and query endpoint;
- projection reset/replay after deletion;
- no deleted content in events, projections, logs, or API responses.

## Related documentation

- [ADR 0002: Separate sensitive application data from events](0002-separate-sensitive-data-from-domain-events.md)
- [Events and sensitive data](../events-and-sensitive-data.md)
- [Failure behaviour](../failure-behaviour.md)
- [Running and operating](../running-and-operating.md)
- [Storage model](../storage-model.md)
