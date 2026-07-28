---
status: accepted
date: 2026-07-17
---

# Store PII and supplementary metadata in a separate relational table, not in a second event stream

## Context and Problem Statement

ADR-0001 establishes that events contain only decision-relevant data. As a consequence, PII (applicant names, contact details, provider information) and supplementary metadata (submitting service context, audit fields) that are not needed by aggregates or sagas must be stored somewhere other than the Axon event store.

Two structural options exist: a dedicated relational table written alongside command handling, or a separate Axon event stream that records PII-bearing events independently of the main aggregate stream.

GDPR right-to-erasure must be satisfiable without crypto-shredding, which is excluded as an option due to its operational complexity.

## Decision Drivers

* GDPR right-to-erasure must be implementable with low operational complexity and no risk of leaving recoverable PII behind.
* Crypto-shredding is explicitly out of scope.
* The solution must not introduce disproportionate architectural complexity for what is, in essence, supplementary data storage.
* The metadata store must support indexed lookups, as it will be joined with read models to satisfy filtered queries (see ADR-0003).
* Writes to the metadata store must be coordinated reliably with the main event store to avoid inconsistency.
* The data contains approximately 100 fields in a nested, structured schema that may evolve over time. The storage shape must accommodate this without requiring frequent DDL migrations.

## Considered Options

* **Separate relational table** (`application_metadata` or equivalent), written in the same database transaction as the Axon event store entry
* **Separate Axon event stream**, logically or physically isolated from the main aggregate stream
* **Document store (MongoDB)**, holding PII/metadata as a document keyed by aggregate ID

## Decision Outcome

Chosen option: **Separate relational table**, because GDPR deletion is a single SQL `DELETE` or `UPDATE` with no event stream side-effects, the mental model is simple, and it avoids the dual-stream complexity that a second event stream would introduce. A hybrid storage shape (typed columns for known filter fields, JSONB for the full nested payload) absorbs schema evolution without frequent DDL migrations.

This decision assumes a single application type with a known, relatively stable schema. If multiple application types with substantially different schemas are introduced, or if the system must serve work queues that span application types, a document store (MongoDB) should be reconsidered. The typed-column approach degrades when multiple schemas must coexist: either separate tables per type (making cross-type queries require `UNION`), a single table with nullable columns for each type (the sparse-column problem), or typed columns only for common fields (losing indexed filtering on type-specific fields). A document store absorbs all of this without schema changes.

The table is written in the same database transaction as the Axon event store entry. If the event store and metadata table share the same datasource (the default Axon JPA configuration), this is straightforward. If they are on separate datasources, an outbox table pattern or a compensating `@Saga` must be used to coordinate the writes.

The table uses a hybrid storage shape: typed, indexed columns for fields known to require filtering or sorting (extracted from the nested structure at write time), and a `jsonb` column holding the full nested payload. New filter requirements are met by promoting a field from the JSONB column to a typed column in a targeted migration — no projection replay is needed, as the data is already present in the `jsonb` column.

### Consequences

* Good, because GDPR right-to-erasure is a simple SQL operation on a single table, with no event store mutations required.
* Good, because the metadata table can carry conventional database indexes, supporting efficient filtered queries when joined with read model tables (see ADR-0003).
* Good, because there is no second event stream to replay, operate, or evolve schema for.
* Good, because the approach is accessible to any developer familiar with Spring Data JPA — no Axon-specific expertise required for the PII path.
* Bad, because there is no event-sourced history of PII changes. If a change audit of personal data is needed in future, it must be implemented separately (e.g. a change log table).
* Bad, because the metadata table is not rebuilt by projection replay. A separate restore mechanism (re-seeding from an outbox or command log) is needed if the table is lost.
* Neutral, because the dual-write risk between the event store and metadata table is real but manageable: same-datasource transactions eliminate it; separate datasources require the outbox pattern.

### Confirmation

Integration tests should assert that a GDPR deletion of the metadata row does not cause errors in read model queries (which should display redacted placeholders) and that projection replay does not re-populate deleted metadata.

A database constraint should enforce a foreign-key or unique index relationship between `application_metadata.application_id` and the aggregate ID, preventing orphaned metadata rows.

## Pros and Cons of the Options

### Separate relational table

* Good, because GDPR deletion is a simple, auditable SQL operation.
* Good, because no Axon-specific concepts are required for the PII data path.
* Good, because conventional indexes support filtered joins with read model tables.
* Good, because a single datasource transaction eliminates the dual-write problem in the common case.
* Bad, because there is no history of PII changes; auditing past personal data state is not possible from this store alone.
* Bad, because the table is not rebuilt by projection replay and requires a separate restore strategy.

### Separate Axon event stream

* Good, because PII change history is preserved in the stream before deletion.
* Good, because it is architecturally consistent — both domain and PII data are event-sourced.
* Good, because the PII stream can be replayed independently to rebuild a PII read model.
* Bad, because GDPR deletion requires either a redaction event (leaving tombstone noise) or physical deletion of events, which violates the append-only guarantee and requires custom Axon Server configuration.
* Bad, because maintaining two event streams with separate aggregates, processing groups, and replay strategies significantly increases operational and development complexity.
* Bad, because projections combining domain and PII data must consume two streams and correlate them, complicating event handler logic.
* Bad, because Axon Server does not natively support stream-level access control, requiring additional infrastructure to restrict PII stream access.

### Document store (MongoDB)

* Good, because the document model handles nested, structured JSON natively with no impedance mismatch.
* Good, because multiple application types with different schemas coexist in a single collection without sparse-column problems.
* Good, because schema evolution is absorbed silently — old and new document shapes coexist without DDL migrations.
* Good, because GDPR deletion is a simple document update or delete.
* Bad, because joining the metadata collection with the read model collection for filtered queries requires denormalisation, application-side joins, or `$lookup`, all of which are weaker than a PostgreSQL database-side join (see ADR-0003).
* Bad, because it introduces an additional datastore alongside PostgreSQL (which is already required for the Axon event store), increasing operational footprint.
* Bad, because Spring Data MongoDB and the Axon MongoDB extension are less mature than the Spring Data JPA path.

## More Information

This decision depends on ADR-0001 (lean events) and is a prerequisite for ADR-0003 (read model composition). The choice of a relational table in the same schema as the read model tables is what enables the database-side join strategy in ADR-0003.
