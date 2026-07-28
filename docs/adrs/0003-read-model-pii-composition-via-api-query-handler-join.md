---
status: accepted
date: 2026-07-17
---

# Compose PII and read model data via a database-side join in the API query handler

## Context and Problem Statement

Work queues and list pages require data from two sources: the event-sourced read model (status, case reference, dates, proceeding details) and the PII/metadata table established in ADR-0002 (applicant name, provider details, contact information). Filtering on PII and metadata fields is a known requirement — users must be able to search and filter by fields such as applicant name or provider.

The question is where the composition of these two data sources should happen: inside the read model itself (denormalised), in the API query handler, or in the UI client.

## Decision Drivers

* Filtering on PII/metadata fields (e.g. applicant name, provider) is a hard requirement. Any option that cannot satisfy this efficiently at an acceptable operational complexity is not viable.
* GDPR right-to-erasure must remain simple. The read model should not need to be updated when PII is deleted from the metadata table.
* Projection replay must not inadvertently re-expose deleted PII.
* The solution must support paginated queries with filters applied across both data sources.
* The data schema is nested and may evolve over time. If multiple application types with different schemas are introduced in future, the composition strategy must remain viable or have a clear migration path.

## Considered Options

* **PII denormalised into the read model** — `@EventHandler` projections copy PII fields from the metadata table into the read model row at write time
* **API query handler joins both tables** — the read model stays PII-free; the `@QueryHandler` issues a SQL join across the read model table and the metadata table
* **UI client combines the data** — the API exposes separate endpoints; the UI fetches both and merges them
* **Elasticsearch as a dedicated search index** — a denormalised search document (combining read model and metadata fields) is maintained in Elasticsearch; filtered list queries go to Elasticsearch rather than PostgreSQL

## Decision Outcome

Chosen option: **API query handler joins both tables**, with the read model table and the metadata table co-located in the same PostgreSQL schema so that a single database-side join can satisfy filtered, paginated queries.

The `@QueryHandler` issues a SQL query (via Spring Data JPA `@Query` or QueryDSL) that joins the two tables and applies filter predicates across both. The read model itself contains no PII. GDPR deletion of a metadata row leaves the read model row intact; the query handler returns redacted placeholders for deleted PII fields.

### Consequences

* Good, because the event-sourced read model contains no PII, so projection replay does not re-expose deleted personal data.
* Good, because GDPR deletion targets only the metadata table — no read model rows need to be updated.
* Good, because a single database-side join supports efficient filtered, paginated queries across both data sources without multiple round trips.
* Good, because PII displayed to users is always fresh from the metadata table — there is no staleness between the metadata table and the read model.
* Bad, because query handler implementations are more complex than a simple single-table repository query. This complexity is localised and testable but must be maintained.
* Bad, because the read model table and metadata table must be in the same PostgreSQL schema for database-side joins to be available. Cross-schema or cross-service queries are not supported by this approach.
* Bad, because if multiple application types with different schemas are introduced, the typed-column approach in the metadata table degrades — separate tables per type require `UNION` queries across types, and a single table with columns for all types produces a sparse-column problem. At that point the Elasticsearch option becomes more appropriate as the filtering tier.
* Neutral, because if the tables cannot share a schema (e.g. due to access control requirements), the fallback is to denormalise PII into the read model (the first considered option) and accept that GDPR deletion must also update read model rows.

### Confirmation

Integration tests for list/work-queue query handlers should verify:
1. Filtered queries on PII fields (e.g. applicant name) return correctly paginated results.
2. After a GDPR deletion (metadata row deleted or nulled), the query handler returns the read model row with redacted PII fields rather than erroring or omitting the row.
3. Projection replay does not re-populate PII fields in the read model.

ArchUnit rules from ADR-0001 (asserting no PII types in event classes) complement this by ensuring PII cannot enter the read model via the event stream.

## Pros and Cons of the Options

### PII denormalised into the read model

* Good, because query performance is optimal — a single table with all fields indexed.
* Good, because filtering on any field is straightforward.
* Good, because `@QueryHandler` implementations are simple.
* Bad, because PII in the read model table complicates GDPR deletion: both the metadata table and any read model row containing the PII must be updated.
* Bad, because projection replay re-populates deleted PII if the metadata table has not also been cleaned, creating a risk of re-exposure.
* Bad, because `@EventHandler` projections become coupled to the metadata repository, complicating testing.
* Bad, because PII updates (e.g. name corrections) must propagate to the read model independently of any domain event.

### API query handler joins both tables

* Good, because the read model is PII-free, keeping GDPR deletion simple and replay safe.
* Good, because a single database-side join supports filtered, paginated queries efficiently.
* Good, because PII is always served fresh from the metadata table.
* Bad, because query handler implementations are more complex.
* Bad, because both tables must be in the same database schema.

### UI client combines the data

* Good, because each backend endpoint has a single responsibility.
* Good, because backend query handlers remain simple.
* Bad, because server-side filtering on PII fields is not possible — the UI cannot issue a filtered paginated request without the backend implementing filtering anyway.
* Bad, because two HTTP round trips are required per page load.
* Bad, because business logic (data composition) leaks into the UI.
* Bad, because this option does not satisfy the filtering requirement and is therefore not viable.

### Elasticsearch as a dedicated search index

* Good, because filtered queries on any combination of fields — including nested objects and arrays — are natively efficient without expression indexes or typed-column promotion.
* Good, because it decouples the filtering requirement from the storage choice: PostgreSQL or MongoDB can serve as the authoritative store regardless of this decision.
* Good, because multiple application types with different schemas coexist naturally in a single index.
* Good, because GDPR deletion is a document update by ID — straightforward.
* Bad, because it introduces a third operational component alongside PostgreSQL (Axon event store) and the read model/metadata database.
* Bad, because writes must be kept in sync between the authoritative store and Elasticsearch. An outbox or event-driven sync pattern is required; failures can leave the index stale.
* Bad, because Elasticsearch is not rebuilt by projection replay. A separate re-indexing process is required if the index is lost.
* Neutral, because the filtering requirement would need to become significantly more complex or numerous before this operational cost is clearly justified over the typed-column promotion approach in PostgreSQL.

## More Information

This decision depends on ADR-0001 (lean events) and ADR-0002 (PII in a separate metadata table). The co-location of the metadata table in the same PostgreSQL schema as the read model tables is a structural requirement that must be established at database provisioning time.

Two conditions should trigger a revisit of this decision:

1. **Multiple application types with divergent schemas are introduced.** The typed-column approach will degrade into a sparse-column problem, making Elasticsearch a more appropriate filtering tier.
2. **Filtering requirements become numerous or unpredictable.** If the typed-column promotion path (adding a column and backfilling from the JSONB payload) becomes a persistent source of migrations, Elasticsearch removes that cost at the price of an additional operational component.
