# Metadata, PII, and Event Streams

This document explores three related architectural decisions for an Axon-based event-sourced application:

1. What data belongs in the event stream?
2. How to handle PII outside the event stream (GDPR compliance)?
3. How to combine PII/metadata with read models for work queues and list pages?

The decisions are tightly coupled — the choice made in point 1 constrains options in point 2, which in turn constrains options in point 3.

---

## 1. What data belongs in the event stream?

### The question

Should events carry _all_ data about a business transaction (rich events), or only the data that command handlers, aggregates, and sagas need to make decisions (lean events)?

### Option A — Lean events (decision-relevant data only)

Events contain only the fields that aggregates or sagas read in `@EventSourcingHandler` / `@SagaEventHandler` methods. Supplementary data (applicant name, provider details, audit metadata, etc.) is stored elsewhere and is not part of the event payload.

**Pros**
- Events model business facts precisely. They express _what happened_ at the domain level, not what was known at the time.
- Aggregate state reconstructed from the event stream is smaller and faster to replay.
- GDPR surface is minimised by design: PII never enters the event store in the first place.
- Events are less brittle. Adding supplementary data fields elsewhere does not require event upcasters.

**Cons**
- Read models and audit trails must join data from multiple sources, increasing query complexity.
- If business rules later turn out to need a field that was not recorded in the event, you cannot replay history to populate it — you have a permanent gap.
- Developers must actively decide what is "decision-relevant", which requires discipline and domain clarity.

### Option B — Rich events (all known data)

Events carry every field known at the time the command was processed, including metadata and contextual data that no aggregate or saga currently reads.

**Pros**
- Complete audit log: a full reconstruction of state at any past point is possible from the event store alone.
- Future read models can be built by replaying events without needing supplementary data sources.
- Simpler initial development — no need to decide upfront what is decision-relevant.

**Cons**
- PII enters the event store. Without crypto-shredding this is a significant GDPR liability. The entire dataset must be treated as high-risk, complicating retention, access control, and right-to-erasure requests.
- Events become coupled to the data shape of external systems (e.g. provider directories, identity systems). Changes to those systems may require upcasters.
- Replay is slower and the event store grows larger.
- The semantic signal of an event is diluted — it becomes a data dump rather than a business fact.

### Recommendation: lean events

Store only what aggregates and sagas need to make decisions. The guiding test is: _"If I removed this field from the event, would any `@EventSourcingHandler` or `@SagaEventHandler` need to change?"_ If no, the field does not belong in the event.

This is not a licence to be parsimonious about genuinely business-relevant data. If a field drives routing, status transitions, or downstream saga steps, it belongs in the event. If it is contextual metadata (who submitted it, what the applicant's name was, provider contact details), it does not.

---

## 2. How to handle PII outside the event stream

### Context

Given the recommendation above, PII and supplementary metadata must live outside the Axon event store. Two viable options exist.

### Option A — Separate relational table (metadata repository)

A dedicated table (e.g. `application_metadata`) is written synchronously or as part of the same transaction as command handling. It is keyed by the same aggregate ID. A Spring Data repository provides access to this table from projections and query handlers.

```
Command Handler
  ├── applies event to Axon event store (lean, no PII)
  └── writes PII/metadata to application_metadata table (same DB transaction or compensating saga)
```

**Pros**
- Simple mental model: it is just a table.
- Deletions and updates for GDPR right-to-erasure are straightforward SQL operations.
- No Axon-specific concepts needed.
- Data is immediately consistent with command handling if written in the same transaction.
- Easy to add indexes for filtering.

**Cons**
- Not event-sourced: there is no history of how the PII changed over time.
- The write must be coordinated with the event store. If they are in different transactions (or the event store uses a different data source), you have a dual-write problem requiring an outbox pattern or saga.
- Replay of projections does not automatically rebuild this table — you need a separate restore mechanism.
- If you ever need to audit PII changes, you must implement change tracking yourself.

### Option B — Separate event stream (PII event store)

PII is recorded as events in a logically or physically separate event stream (separate Axon processing group, separate event store, or a separate aggregate whose stream is marked for restricted access). The stream is append-only but the aggregate itself can emit redaction events, or the stream can be physically deleted per GDPR request.

```
Command Handler
  ├── applies lean event to main aggregate stream
  └── applies PII event to restricted PII stream (separate aggregate or Axon context)
```

**Pros**
- History of PII changes is preserved (useful for auditing prior to deletion).
- Consistent with event-sourced architecture: both data concerns are modelled the same way.
- PII stream can be replayed independently to rebuild a PII read model.
- Access control can be applied at the stream level.

**Cons**
- Significantly more complex: you now maintain two event streams, two sets of aggregates or processing groups, and two replay strategies.
- Dual-write problem still exists between the two streams; requires care to avoid partial failures.
- GDPR deletion requires either a redaction event (leaving tombstone noise in the stream) or physical deletion of events, which conflicts with the append-only guarantee.
- Projections that need to join PII and domain data must consume from two streams and correlate them, increasing complexity.
- Axon Server does not natively support stream-level access control without additional infrastructure.

### Recommendation: separate relational table

The separate metadata table is the pragmatic choice given the constraints:
- PII deletion is a simple `DELETE` or `UPDATE` — no tombstoning, no event upcasting.
- The complexity cost of a second event stream is high and the benefits (PII history, replayability) are largely unnecessary if PII is only supplementary metadata.
- If the metadata table needs to be rebuilt, it can be re-seeded from the originating commands or from an outbox/audit log, not from the event stream.

The dual-write risk is mitigated by writing metadata in the same database transaction as the Axon event store entry (when both use the same datasource), or by using an outbox table pattern and a `@Saga` to coordinate writes if they are on separate datasources.

---

## 3. Combining PII/metadata with read models

### Context

Work queues and list pages need to display data from both the event-sourced read model (status, dates, case reference) and the PII/metadata table (applicant name, provider, contact details). The assumption for this analysis is that **filtering on PII/metadata fields will be required**.

### Option A — Store PII in the read model directly

The projection's `@EventHandler` methods also query the metadata table and denormalise relevant PII fields directly into the read model row.

```
ApplicationCreatedEvent → EventHandler
  ├── reads PII from metadata table
  └── saves ApplicationReadModel { status, laaRef, applicantName, providerName, ... }
```

**Pros**
- Query performance is excellent: a single table, all fields indexed, no joins at query time.
- Filtering on PII fields (e.g. "find all applications for applicant X") is straightforward.
- API query handlers are simple: one repository call returns everything needed.
- Replay rebuilds the complete read model including PII (assuming the metadata table still holds the data at replay time).

**Cons**
- PII is now in the read model table. GDPR deletion requires updating or nulling out rows in the read model table in addition to the metadata table. Read model replay will re-populate deleted PII if the metadata table has not also been cleaned.
- The projection is coupled to the metadata repository, complicating testing and increasing dependencies.
- If PII is updated (e.g. name correction) the read model must be updated independently of any domain event.

### Option B — API query handler combines the data

The read model contains only non-PII data. The `@QueryHandler` fetches the read model, then enriches it with a second query to the metadata repository before returning the response.

```
FindApplicationsQuery → QueryHandler
  ├── queries ApplicationReadRepository (no PII)
  ├── extracts IDs
  ├── queries MetadataRepository for those IDs
  └── returns merged response DTO
```

**Pros**
- Clear separation of concerns: the event-sourced read model is PII-free.
- GDPR deletion is isolated to the metadata table; the read model does not need touching.
- PII is fetched fresh — no staleness between the metadata table and the read model.
- Replay of projections does not require the metadata table to still hold original data.

**Cons**
- **Filtering on PII fields is hard.** If you need to find all applications where `applicantName LIKE 'Smith%'`, you must either: (a) query the metadata table first to get matching IDs, then query the read model by those IDs (two round trips, possible pagination problems with large result sets), or (b) do a database-side join, which requires the read model and metadata to be in the same schema.
- N+1 risk if not implemented carefully (fetch page of results, then fetch N metadata rows).
- API query handlers grow more complex.

### Option C — Leave it to the UI to combine

The API exposes separate endpoints for domain data and metadata. The UI client fetches both and merges them.

**Pros**
- Maximum backend simplicity.
- Each endpoint has a single responsibility.

**Cons**
- **Filtering on PII is impossible at the API level.** The UI cannot efficiently ask "show me page 2 of applications filtered by applicant name" without either receiving all data or the API implementing filtering anyway.
- Chatty: two HTTP calls per page load.
- Business logic leaks into the UI.
- Not viable if filtering on PII/metadata is required (which is our stated assumption).

### Recommendation: API query handler combines the data (Option B), with a join-capable schema

Given the filtering requirement, Option A (PII in read model) or a well-structured Option B are the only viable choices. The recommended approach is **Option B with database-side joins**:

- Keep the read model PII-free.
- Store both the read model table and the metadata table in the **same PostgreSQL schema**.
- The query handler issues a single SQL query joining the two tables (via Spring Data JPA `@Query` or QueryDSL), applying filters across both.
- GDPR deletion targets only the metadata table; the read model row remains and can display redacted placeholders.

This keeps GDPR deletion simple, keeps the event-sourced read model clean, and still allows efficient filtered queries. The trade-off is that the query handler is slightly more complex, but this complexity is localised and testable.

If the two tables cannot share the same schema (e.g. for access control reasons), fall back to Option A (denormalise into the read model) and accept that GDPR deletion must update the read model table as well as the metadata table — this should be driven by a deletion command that emits a `PersonalDataRedactedEvent`, triggering projection updates.

---

## Cross-cutting decision summary

| Decision | Recommendation | Key driver |
|---|---|---|
| What goes in the event stream? | Lean events — decision-relevant data only | GDPR surface, event semantics |
| Where does PII live? | Separate metadata table, same DB | GDPR deletion simplicity, low complexity |
| How do read models access PII? | API query handler joins read model + metadata table | Filtering requirement, GDPR isolation |

### How the decisions interact

- **Lean events → metadata table**: choosing lean events makes a separate metadata store mandatory, not optional. The two decisions are co-dependent.
- **Metadata table in same schema → join-capable queries**: placing the metadata table in the same PostgreSQL database as the read model unlocks database-side joins, which is the key enabler for Option B being workable.
- **GDPR deletion path**: with lean events + metadata table + no PII in read model, the deletion path is a single `DELETE` or `UPDATE` on the metadata table, with no event store changes and no read model changes (or at most a projection-triggered redaction). This is the simplest achievable deletion path.
- **Replay safety**: because PII is never in the event stream and not stored in the read model, replaying projections does not re-expose deleted PII. The metadata table acts as the single authoritative store for PII and is not rebuilt by replay.
