# Options for converting the `GetAllApplications` query

## Purpose

This note describes options for moving `GET /api/v0/applications` from its current relational query to event-sourced, projection-backed reads.

The endpoint must preserve its current filters, ordering, paging, and linked-application enrichment:

- status, LAA reference, caseworker, matter type, and auto-grant filters;
- client first name, surname, and date-of-birth search;
- page total and stable ordering;
- linked-application summaries for returned rows.

The source implementation is `GetAllApplicationsUseCase` and `GetAllApplicationsApplicationJpaGateway`.

## Common requirements

Every option should:

- page applications before enriching linked applications or detailed PII;
- use deterministic ordering, including `application_id` as a tie-breaker;
- avoid replaying aggregates or querying raw events on the request path;
- use idempotent projectors, stream versions, and projection checkpoints; and
- retain compatible response and error behaviour.

## Option 1: one restricted search projection with plaintext search PII

Create a query-optimised `application_search_view` with one row per application. It contains all non-PII filter/sort fields plus the minimum PII required by the existing list search.

```text
application_search_view
-----------------------
application_id
stream_version
status
laa_reference
caseworker_id
matter_type
category_of_law
is_auto_granted
submitted_at
created_at
modified_at
lead_application_id

client_first_name          -- plaintext restricted PII
client_last_name           -- plaintext restricted PII
client_date_of_birth       -- plaintext restricted PII
projection_position
```

A separate `application_link_search_view` holds group membership:

```text
application_link_search_view
----------------------------
lead_application_id
application_id
stream_version
projection_position

PRIMARY KEY (lead_application_id, application_id)
```

### Query flow

1. Filter, sort, and page `application_search_view`.
2. Load link rows for the page's application IDs.
3. Attach linked-application summaries.
4. Map the page to the existing response.

Plaintext name and date-of-birth fields permit the current partial-match behaviour using normal database query and index facilities. The table is therefore a restricted PII read model, not a general operational table.

### PII boundary

Keep richer PII—such as addresses, contact details, financial information, identifiers, and arbitrary client content—in a separate protected PII store. Only rehydrate that data after paging, and only when a list or detail response requires it.

### GDPR erasure

A GDPR request requires an explicit, tested PII-erasure workflow; clearing live columns alone is insufficient. The workflow must:

1. remove or redact name and date of birth from `application_search_view`;
2. delete or cryptographically erase detailed PII in its protected store;
3. clear PII from other projections, search indexes, caches, exports, notes/certificate views where applicable, and dead-letter records;
4. ensure logs, tracing, metrics, and support diagnostics do not retain the data;
5. apply the agreed backup retention and restore controls; and
6. record a non-PII, replay-safe `ApplicationPiiErased` tombstone so a projection rebuild cannot reintroduce erased data.

The application event stream must not contain the plaintext PII fields. Events should contain a PII reference/revision or encrypted payload under an approved retention policy.

### Benefits

- Simplest conversion of the existing combined filter, sort, and page query.
- Supports partial name matching efficiently.
- Avoids querying a PII store before pagination.
- Keeps rich PII off the normal list query path.

### Costs and risks

- The search projection is a sensitive asset and needs least-privilege access, encryption at rest/backups, audit controls, and safe logging.
- The erasure process and rebuild behaviour must be first-class operational capabilities.
- Plaintext PII must never be stored in immutable events if later erasure is required.

## Option 2: split operational projection and restricted PII search index

Keep non-PII columns in `application_search_view` and place client search fields in a separate `application_search_pii_index` joined by `application_id`.

```text
application_search_pii_index
----------------------------
application_id
client_first_name
client_last_name
client_date_of_birth
stream_version
projection_position
```

The query joins the two tables to apply all filters and paging. It may still use plaintext PII to support partial matches, but narrows database roles and accidental-access risk.

### Benefits

- Stronger schema and database-role separation between operational data and PII.
- The PII index can have targeted retention, auditing, and index controls.

### Costs and risks

- Adds a join to the core list query.
- Requires atomic consistency between the two projection rows.
- Does not eliminate the GDPR erasure requirements from Option 1.

## Option 3: search PII in a separate store, then query application projection

Search a PII store first to find matching application IDs, then use those IDs with `application_search_view` for operational filters, ordering, and paging.

### Benefits

- No PII duplication in the application search projection.
- Strong separation of PII storage and application query storage.

### Costs and risks

- Cross-store filtering, total counts, sorting, and paging are difficult to make correct.
- It can require very large ID sets for broad name searches.
- The PII store becomes a synchronous dependency for client-filtered requests.

This is not recommended for the existing endpoint unless its query semantics are simplified.

## Option 4: encrypted PII in events and a restricted projection

Store the PII needed for replay as encrypted event payloads; project plaintext name and date of birth into the restricted search view.

### Benefits

- Projection rebuild is self-contained in the event store.

### Costs and risks

- Immutable events retain PII and make correction, erasure, backup handling, and operational diagnostics more complex.
- It requires a formal encryption/key-destruction and retention strategy.

Use this only if the organisation accepts encrypted PII in immutable event history.

## Option 5: thin list index with query-time rehydration

Maintain a minimal index for fields that determine candidate membership, ordering, total count, and paging. It includes the minimum plaintext PII needed for the client-name and date-of-birth filters, but omits non-filtered summary and detailed data.

```text
application_list_index
----------------------
application_id
status
laa_reference
caseworker_id
matter_type
is_auto_granted
submitted_at
lead_application_id
client_first_name
client_last_name
client_date_of_birth
stream_version
projection_position
```

After the index query selects a page, the query API bulk-loads linked-application data, non-filtered summary data from a current-state projection, and richer PII from the protected PII store where required. It assembles the existing response in memory.

### Benefits

- Keeps the filtering and paging projection small.
- Avoids duplicating response-only fields into the list index.
- Fetches rich PII only for the page returned to the caller.

### Costs and risks

- The query API orchestrates multiple bulk reads and must avoid N+1 lookups.
- Independently lagging projections can make a single response internally inconsistent.
- Latency and availability depend on every read source used to enrich the result.

This is suitable when response data substantially exceeds filter/sort data, and the endpoint can tolerate bounded eventual consistency between its read sources.

## Option 6: query-time decryption and JSON extraction

Maintain a non-PII operational index, store detailed PII as encrypted JSON, and decrypt/extract client fields during `GET /api/v0/applications`.

The extraction can happen in the query API after a coarse non-PII filter, or inside the database by decrypting JSON and querying its paths.

### Benefits

- Does not maintain plaintext client search fields in a long-lived projection.
- Avoids eagerly materialising a PII search index.

### Costs and risks

- Correct total counts and paging require filtering and sorting every candidate before selecting a page; a broad query may therefore decrypt a very large set.
- Application-side extraction has high latency and memory cost; strict candidate limits and timeouts are required.
- Database-side decryption exposes keys to the database execution environment, prevents normal index use on decrypted values, and risks plaintext exposure through database diagnostics.
- It is unsuitable for a high-volume partial-name search endpoint unless candidate sets are reliably small.

Database-side decryption is not recommended for the normal endpoint path. This option is best limited to administrative/recovery use cases, a temporary migration bridge, or a tightly constrained query with mandatory selective non-PII filters.

## Decision table

| Option | Partial name matching | Correct server-side paging and count | Read performance at scale | PII placement | Replay / rebuild | Main concern | Suitability for `GetAllApplications` |
|---|---|---|---|---|---|---|---|
| 1. One restricted search projection | Yes | Yes | Good with appropriate indexes | Plaintext minimum PII in restricted search view | Requires PII reference/revision or approved encrypted source | GDPR erasure and restricted access | Recommended default |
| 2. Split operational projection and PII index | Yes | Yes | Good; requires indexed join | Plaintext minimum PII in separate restricted table | Requires PII reference/revision or approved encrypted source | Consistency across projection tables | Recommended where stronger separation is required |
| 3. PII-store-first cross-query | Yes | Difficult across stores | Variable; broad queries can create large ID sets | PII remains in PII store | PII store must support query/rebuild semantics | Cross-store paging, sorting, and total counts | Usually not suitable |
| 4. Encrypted PII events plus restricted projection | Yes | Yes | Good for reads | Encrypted PII retained in immutable events; plaintext minimum PII in restricted view | Self-contained event replay | Erasure, correction, keys, backups, and diagnostics | Use only with explicit retention approval |
| 5. Thin list index plus rehydration | Yes | Yes, because filter fields remain indexed | Good if all enrichment is bulk-loaded | Minimum search PII in index; richer PII fetched per page | Multiple projections/stores must have defined lag semantics | Query orchestration and cross-view consistency | Good when summaries have many response-only fields |
| 6. Query-time decryption and JSON extraction | Yes | Only after processing all candidates | Poor for broad queries | Encrypted JSON outside the index | Depends on retained encrypted PII source | Full scans, decryption cost, key exposure in database variant | Not recommended as the normal endpoint path |

## Recommended direction

Choose **Option 1** as the straightforward initial conversion, **Option 2** where stronger database-level separation is valuable, or **Option 5** where the response has substantial non-filtered data that is best bulk-rehydrated after the page is selected. All three preserve partial name matching by allowing plaintext name and date-of-birth values in a tightly controlled index.

For either option:

- write the minimum search PII to the restricted projection on the create/update write path or a synchronous projector;
- retain detailed PII in a protected store and rehydrate it only after pagination when required;
- do not include plaintext PII in application events; and
- deliver a replay-safe GDPR erasure tombstone, clear-out procedure, and operational tests before storing production PII.

## Delivery and test checklist

1. Confirm current name-match semantics: case-insensitive exact, prefix, or substring.
2. Map every existing filter, sort field, response field, and linked-application lookup to projection columns.
3. Define the selected projection schema, indexes, database roles, and safe logging rules.
4. Implement create/update projection handling, including client PII changes.
5. Replace `GetAllApplicationsApplicationGateway` with the projection-backed gateway.
6. Add contract tests for each filter combination, sort, page boundary, stable tie-breaking, and linked-application enrichment.
7. Add tests for write/read consistency, replay/rebuild, retries, and projection failures.
8. Add end-to-end GDPR erasure tests proving partial-name search, detail reads, caches, and rebuilds cannot restore erased PII.
