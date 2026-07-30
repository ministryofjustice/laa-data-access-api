# Thin List Index with Query-Time Rehydration

## Background

The `GET /applications` endpoint supports paginated, filterable queries across all applications. Each application's rich content — client PII, proceedings, certificate, application content — is stored as a versioned JSONB payload in the `application_data` table, deliberately separated from the event log for GDPR reasons.

This separation created a query problem: filters such as client name or date of birth could not be pushed to the database because the values lived inside opaque JSONB blobs. PostgreSQL can query inside JSONB using operators such as `->>` or `jsonb_path_query`, but doing so requires either a full table scan (no index support for arbitrary nested paths) or maintaining GIN indexes on the entire document — neither of which is practical for filtered, sorted, paginated queries across a large dataset.

The only alternatives were:
- **Load all application payloads into memory** before filtering — not scalable as the dataset grows, and forces the application to do work the database should do
- **Duplicate the entire payload into the read model table** — expensive to maintain during event replay and bloats the projection with data that is only needed for the response body, not for filtering

Neither option is acceptable at scale. The thin list index solves this by extracting only the filterable fields into discrete, properly typed and indexed columns, while leaving the full payload in `application_data` where it is only loaded for the final result page.

## What Was Introduced

### New database table: `application_list_index` (V15 migration)

A thin, purpose-built projection table containing only the columns needed for filtering, sorting, counting, and paging the list endpoint. Rich response-only fields are explicitly excluded.

**Filterable/sortable columns stored:**
- `status`, `laa_reference`, `matter_type`, `caseworker_id`
- `client_first_name`, `client_last_name`, `client_date_of_birth` — minimum PII required to support client name and DOB filter pushdown
- `lead_application_id`, `submitted_at`, `is_auto_granted`

**Bookkeeping columns:**
- `stream_version` — the application domain version at the time the row was last written; used as an optimistic concurrency guard
- `projection_position` — tracks the event position at which the row was last written

**Indexes created:**

Without indexes, every filtered query against the list endpoint would require a full sequential scan of the `application_list_index` table — reading every row regardless of how many match the filter. As the table grows this becomes progressively slower and more expensive.

- **Equality indexes** on `status`, `matter_type`, `laa_reference`, `caseworker_id` — allow PostgreSQL to seek directly to matching rows rather than scanning the full table when these filters are applied
- **Functional indexes** on `lower(client_first_name)` and `lower(client_last_name)` — client name filters use a case-insensitive `LIKE 'value%'` predicate (e.g. searching "jane" should match "Jane" or "JANE"). A standard B-tree index on the raw column would not be used by a `lower(column)` expression, so functional indexes on the pre-lowercased value are required. The `ApplicationListIndexSpecification` applies `lower()` consistently to match these indexes, ensuring PostgreSQL uses them rather than falling back to a sequential scan

### `ApplicationListIndexProjection`

An Axon `@EventHandler` component that maintains the list index in response to domain events:

| Event | Action |
|---|---|
| `ApplicationCreatedEvent` | Inserts a new index row; fetches client PII from `application_data` at this point |
| `ApplicationLinkedEvent` | Updates `lead_application_id` |
| `ApplicationDecisionMadeEvent` | Updates `status` and `is_auto_granted` |
| `ApplicationAssignedToCaseworkerEvent` | Updates `caseworker_id` |
| `ApplicationUnassignedFromCaseworkerEvent` | Clears `caseworker_id` |
| `NoteCreatedEvent` | Updates `projection_position` only (no filter fields change) |

Supports `@ResetHandler` — the table is wiped and fully rebuilt from the event log on replay.

### `ApplicationListIndexSpecification`

A Spring Data JPA `Specification` factory that converts `FindAllApplicationsQuery` filter parameters into database predicates. All non-null filters produce a PostgreSQL predicate. Client name filters use `lower()` to match the functional indexes in the V15 migration.

### `ApplicationListIndexReadRepository`

A Spring Data JPA repository extending both `JpaRepository` and `JpaSpecificationExecutor`, providing filtered pagination support against the list index table.

### Refactored `FindAllApplicationsQuery` handler

The `ApplicationProjection.handle(FindAllApplicationsQuery)` method was refactored to a two-phase approach:

**Phase 1 — database:** Query `application_list_index` with all filters, sort, and pagination applied entirely in PostgreSQL. Returns a page of `applicationId`s.

**Phase 2 — batch rehydration:** For the result page only:
1. Batch-load `application_current_state` rows for those IDs (`findAllById`)
2. Batch-load `application_data` payloads for those IDs (`getAll`)
3. Merge into full `ApplicationReadModel` objects in memory

## Key Benefits

**Filter pushdown to the database.** Filtering by client name, DOB, status, matter type, and LAA reference now executes entirely in PostgreSQL against indexed columns. No application payloads are loaded until after paging has been applied.

**Elimination of N+1 queries.** The list endpoint now makes exactly two batch reads regardless of page size — one to `application_current_state`, one to `application_data` — rather than one query per application.

**Scalability.** COUNT and pagination happen on the thin index table, not against the full JSONB payload store. As the number of applications grows, query performance degrades gradually rather than sharply.

**Full replayability.** The list index is a standard Axon event-sourced projection. It can be wiped and rebuilt from the event log at any time without data loss or manual intervention.

**GDPR positioning preserved.** PII remains confined to `application_data` (JSONB payload) and the three denormalised columns in `application_list_index`. It is not duplicated into the event log. A right-to-erasure implementation would target only these two known locations.

## Considerations

- **`projectionPosition` semantics:** The field is populated using `message.getIdentifier().hashCode()` — a hash of a string UUID into an `int`. This will not be monotonically increasing and cannot reliably measure projection lag against `domain_event_entry.global_index` as the Javadoc describes. The actual Axon token position would be needed for that purpose.
- **Dual projection maintenance:** `ApplicationListIndexProjection` handles the same events as `ApplicationProjection`. New domain events will need to be assessed and wired into both projections.
- **`caseworker_id` filter not yet exposed:** The column and its index exist in V15, but `caseworker_id` is not yet a filter parameter in `FindAllApplicationsQuery` or `ApplicationListIndexSpecification`. It is in place for a future filter.
