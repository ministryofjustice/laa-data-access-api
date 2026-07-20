# ADR 0002: Separate Sensitive Application Data from Domain Events

- Status: Accepted for the proof of concept
- Date: 2026-07-20
- Scope: `data-access-service-axon`

## Context

Application requests contain personal and sensitive data, including individuals, proceedings,
certificates, notes, and free-text audit descriptions. A conventional event-sourced model could
place all information needed to rebuild the aggregate in its event stream.

Axon events are durable and normally retained indefinitely. They are also consumed by projections,
diagnostics, replays, and future integrations. Putting complete request payloads in events would
copy sensitive data into a store that is deliberately difficult to alter and would make selective
retention deletion complicated.

This proof of concept does not require migration of existing events or data. It can therefore
evaluate a design in which events are not the sole source of complete application content.

## Decision drivers

- Minimise sensitive fields in the durable event store.
- Support deletion of application data for retention purposes without rewriting event streams.
- Retain a durable, replayable record of business transitions and aggregate control state.
- Preserve historical payload versions rather than overwriting a mutable snapshot.
- Keep callers unaware of internal data-version management.
- Allow current-state and history responses to reconstruct detailed data when it remains available.

## Decision

Store complete sensitive application payloads in the append-only `application_data` table. Each row
is identified by `(application_id, version)` and contains the complete payload for that point in
time.

Store thin events in Axon's event store. An event contains the identifiers, timestamps, business
outcome, concurrency state, and `applicationDataVersion` required to locate the corresponding
payload, but excludes detailed application content and free text where practical.

The aggregate rebuilds its control state from events without reading `application_data`. Command
handlers load a referenced payload only when they need detailed current data to make the next
change. Query projections retain version pointers and hydrate detailed responses from
`application_data`.

The aggregate chooses each new `applicationDataVersion`; it is an internal storage concern and is
not supplied by API callers.

PostgreSQL triggers prohibit updates, direct deletes, and truncation of `application_data`.
Retention deletion is available only through the restricted
`delete_application_data_for_retention(UUID)` function.

## Why this is not pure event sourcing

The event stream alone cannot reconstruct the complete historical application payload. It can
reconstruct the aggregate's identifiers, control state, version pointers, assignments, and business
transitions. Fully hydrating application content also requires the referenced `application_data`
rows.

This is an accepted trade-off. The design prioritises data minimisation and retention over the rule
that an event-sourced aggregate's event stream must contain every historical input required to
rebuild all domain data.

Deleting retained payloads has deliberate consequences:

- thin events remain available;
- aggregate control state can still replay;
- detailed queries and history cannot reconstruct deleted fields;
- commands that require the current detailed payload cannot continue normally.

The product's retention policy must define the expected behaviour of an application after deletion.

## Consequences

### Positive

- Detailed sensitive content is not duplicated into every durable event.
- Retention deletion does not require event-stream rewriting.
- Every sensitive-data change creates an immutable historical version.
- Projections remain disposable and contain only thin state plus version pointers.
- Events still provide a compact business timeline and enough state to route later commands.

### Negative

- The event store is no longer a self-contained source for the complete application.
- Commands and queries can fail or degrade if referenced data has been deleted or is inconsistent.
- Replay can rebuild projection pointers, but not deleted detailed content.
- Transactional correctness depends on committing the data row and its referencing event together.
- Consumers must understand both `applicationVersion` and `applicationDataVersion`.
- “Thin” does not mean “not personal data”; stable application and caseworker identifiers may still
  be personal data when linked with other records.

## Alternatives considered

### Store complete payloads in events

Rejected because it maximises replay independence at the cost of copying sensitive data into an
indefinitely retained, append-only log. Selective deletion would require rewriting streams or
accepting retained personal data.

### Encrypt sensitive fields in events and destroy keys for retention

Not selected for the proof of concept. Crypto-shredding could preserve self-contained encrypted
events, but introduces key ownership, rotation, backup, audit, and partial-decryption complexity.
Event metadata and identifiers would still require separate assessment.

### Keep only one mutable application snapshot

Rejected because overwriting a snapshot loses the exact payload associated with each historical
business transition and weakens audit reconstruction.

### Store deltas in `application_data`

Rejected because reading a version would require replaying an additional change log. Complete
immutable payloads are simpler to retrieve and delete as a unit during this proof of concept.

### Put sensitive fields in read-model projections only

Rejected because projections are disposable and replayable. They cannot be the only durable source
of data needed by later commands.

## When to revisit

Reconsider this decision when:

- production retention and deletion requirements are agreed;
- encryption or crypto-shredding becomes a mandated control;
- applications must remain writable after sensitive payload deletion;
- the storage cost of complete immutable payload versions becomes material;
- event consumers require detailed historical state without database access;
- the data row and event cannot be guaranteed to share a transaction;
- identifiers in thin events are classified as requiring erasure.

Before production adoption, complete a privacy and security review covering event payloads,
metadata, backups, database roles, retention execution, and failure recovery.

## Related documentation

- [Events and sensitive data](../events-and-sensitive-data.md)
- [Storage model](../storage-model.md)
- [Projections and replay](../projections-and-replay.md)
- [Sensitive-data change sequence](../sequence-diagrams/04-sensitive-data-change.md)
