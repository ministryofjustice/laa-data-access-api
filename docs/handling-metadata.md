# Handling Metadata and PII: Storage Shape and API Options

This document explores storage strategies for supplementary metadata and PII (established as living outside the event stream in ADR-0001 and ADR-0002) and read models (ADR-0003), given that the data contains approximately 100 fields in a **nested, structured schema** (objects within objects) that may change over time. It also considers whether a NoSQL store is a better fit than PostgreSQL, and whether GraphQL can reduce the long-term maintenance burden on GET endpoints.

---

## Storage shape options for ~100 evolving fields in a nested structure

The data is not a flat list of 100 scalar fields — it contains nested objects (e.g. an applicant object with address, means assessment sub-objects, a list of proceedings each with their own structure). This is an important constraint: it makes fully normalised columns significantly more complex and strengthens the case for JSONB-based storage.

### Option A — Fully normalised columns

Every field gets its own column, and every nested object becomes its own table with a foreign key relationship.

**Good:**
- SQL filtering, ordering, and indexing work exactly as expected.
- Query plans are predictable and inspectable.
- Tooling (Flyway, Spring Data) is mature and well-understood.

**Bad:**
- Nested structure means multiple tables, not just many columns. An `applicant` object with an `address` sub-object and a list of `proceedings` each with scope limitations becomes a table-per-object graph. Joins proliferate.
- 100 fields across a nested structure could easily require 8–15 tables. Every upstream schema change potentially touches multiple tables and their relationships.
- One-to-many nested collections (e.g. a list of proceedings) cannot be flattened into columns at all — they require child tables regardless.
- For the read model, replay handles column additions but missing columns in old projections produce nulls until replay is run.

**Verdict:** Not appropriate here. The nested structure means normalisation produces a complex multi-table schema that mirrors the object graph. Every upstream change requires coordinated migrations across multiple tables. The cost is disproportionate to the benefit.

---

### Option B — JSONB column in PostgreSQL

Store the entire payload as a single `jsonb` column, or use a hybrid: a small number of typed columns for fields known to require filtering or sorting, plus a `jsonb` column for everything else.

#### PostgreSQL JSONB query performance with nested structures

PostgreSQL `jsonb` handles nested objects natively. Path navigation uses `->` for objects and `->>` for text extraction:

```sql
-- filter on a nested field
WHERE metadata->'applicant'->>'surname' = 'Smith'

-- filter on a field two levels deep
WHERE metadata->'applicant'->'address'->>'postcode' = 'SW1A 2AA'

-- containment query on a nested object (uses GIN index)
WHERE metadata @> '{"applicant": {"surname": "Smith"}}'
```

The indexing options for nested paths work the same way as for flat fields:

- **GIN index** on the whole `jsonb` column supports containment queries (`@>`) at any depth efficiently. `metadata @> '{"applicant": {"address": {"postcode": "SW1A"}}}'` will use the GIN index.
- **Expression index** on a nested path — e.g. `CREATE INDEX ON application_metadata ((metadata->'applicant'->>'surname'))` — gives B-tree performance for equality and range queries on that specific nested field.
- **No index** on arbitrary nested paths means a sequential scan, same as for flat fields.
- **Arrays within the structure** (e.g. a list of proceedings): equality/membership queries are well-served by the GIN index. A containment query such as `raw @> '{"proceedings": [{"matterType": "CRIME"}]}'` uses the GIN index efficiently — PostgreSQL decomposes the JSONB array into individual elements at index time, so membership checks at any nesting depth work without scanning. The genuine limitation is **range or inequality queries on values inside array elements** — for example "find applications where any proceeding has an amount greater than 1000" — because `@>` only supports equality matching, and a lateral join with `jsonb_array_elements` cannot use the GIN index for the range predicate. Equality membership queries on array contents are not a problem.

The nested structure makes JSONB _more_ attractive relative to normalisation than it would be for flat data, because the alternative (Option A) now requires a multi-table schema rather than just many columns.

**The practical ceiling:** JSONB works well when there is a known set of 5-15 fields that are filtered or sorted on (index those as expression indexes) and the remaining fields are display-only. The only genuine gap is range/inequality queries on values inside array elements. For those, see the array lifting options in Option C.

**Good:**
- Schema migrations for new fields are zero — add data to the JSON, read it in application code, no DDL.
- Evolving structure from upstream is absorbed without migrations.
- For the metadata table, PII fields are unlikely to require arbitrary user-driven filtering; the fields that appear in search forms (applicant name, provider name) are known and can be indexed.
- For the read model, the projection writes the JSON blob and remains schema-compatible as the upstream payload adds fields. Replay does not require DDL changes.

**Bad:**
- Spring Data JPA has limited native JSONB support. Non-trivial queries require `@Query` with native SQL, or a library such as `hibernate-types`. Type safety is lost at the JSON boundary.
- Reporting and ad-hoc SQL queries become harder to write correctly.

---

### Option C — Hybrid (typed columns for query fields, JSONB for the rest)

This is the recommended storage shape for both the metadata table and the read model. A small number of typed, indexed columns exist for fields known to require filtering or sorting. The full nested structure lives in a `jsonb` column.

```sql
CREATE TABLE application_metadata (
    application_id    UUID         PRIMARY KEY,
    applicant_surname VARCHAR(100),            -- indexed, filterable
    provider_name     VARCHAR(200),            -- indexed, filterable
    office_code       VARCHAR(20),             -- indexed, filterable
    raw               JSONB        NOT NULL    -- full nested structure
);

CREATE INDEX ON application_metadata (applicant_surname);
CREATE INDEX ON application_metadata (provider_name);
CREATE INDEX ON application_metadata (office_code);
-- GIN index for containment queries across the nested structure
CREATE INDEX ON application_metadata USING GIN (raw);
```

Because the data is nested, the typed columns are extracted from the relevant nested path at write time (e.g. `applicant_surname` is extracted from `raw->'applicant'->>'surname'` and stored redundantly as a flat column for indexing). This is intentional denormalisation for query performance.

When a new filter requirement emerges, the relevant field is promoted from `raw` to a typed column in a targeted migration, backfilling the value from the appropriate nested path in `raw`. No projection replay is needed — the data is already present in the `raw` column.

#### Lifting array contents for filtering

For array fields within the nested structure (e.g. a list of proceedings, each with a matter type), there are two lifting options that avoid the range-query limitation described above, without needing a child table:

**Option 1 — Extract to a native PostgreSQL array column**

Extract the values of a specific field across all array elements into a native `TEXT[]` (or typed array) column at write time. PostgreSQL native array columns with a GIN index support `&&` (overlap), `@>` (contains), and `<@` (contained by) efficiently:

```sql
ALTER TABLE application_metadata ADD COLUMN matter_types TEXT[];
CREATE INDEX ON application_metadata USING GIN (matter_types);

-- query: find applications that have at least one CRIME proceeding
WHERE matter_types @> ARRAY['CRIME']

-- query: find applications with any proceeding matching a user-supplied set
WHERE matter_types && ARRAY['CRIME', 'FAMILY']
```

The column is populated at write time by extracting the relevant field from each element of the proceedings array in `raw`. This is efficient, well-understood, and supports range queries if the column type is numeric rather than text.

**Option 2 — Lift all values of a given key across the structure into a generated `tsvector` column**

If the filtering need is text-search oriented (e.g. "find applications that mention this firm name anywhere in the structure"), a `tsvector` generated column can index all text content across the entire `raw` document:

```sql
ALTER TABLE application_metadata
  ADD COLUMN search_vector tsvector
  GENERATED ALWAYS AS (jsonb_to_tsvector('english', raw, '["string"]')) STORED;

CREATE INDEX ON application_metadata USING GIN (search_vector);
```

This is a broad-brush approach — it indexes every string value in the JSON regardless of path — and is suited to free-text search rather than structured field filtering.

**The preferred approach for known array fields** is Option 1 (native array column). It is targeted, type-safe, and produces a well-understood GIN index. A separate child table is only warranted if you need to query multiple fields of the same array element together (e.g. "proceedings where matterType = CRIME AND amount > 1000"), because a single array column cannot express that cross-field relationship.

**This is the pragmatic sweet spot.** It provides:
- Predictable, indexed query performance for known filter fields.
- Zero-migration absorption of upstream payload changes for display-only fields.
- A clear, low-cost promotion path when a new filter requirement is identified.

---

## Can GraphQL reduce long-term maintenance burden on GET endpoints?

Yes, meaningfully — but it solves a different problem than storage. The two concerns are independent and complementary.

### What GraphQL solves

The maintenance burden on REST GET endpoints for a 100-field structure comes from two sources:

1. **Different consumers need different subsets of fields.** A work queue needs 8 fields. A detail page needs 60. A reporting export needs 30 different ones. With REST you either over-fetch (return all 100 always) or proliferate endpoint variants and projection parameters.

2. **Field additions require API changes.** Adding a field means updating the DTO, the OpenAPI spec, the mapper, and potentially the response shape — even when only one consumer needs it.

GraphQL addresses both directly:

- Clients declare exactly which fields they need in their query. The server returns only those. There is no over-fetching and no versioning required for additive changes.
- Adding a new field to the GraphQL schema is non-breaking by default. Existing queries are unaffected. The new field becomes available when a client asks for it.
- For work queue and list page scenarios, different consumers (caseworker queue, manager dashboard, reporting) can each select the fields relevant to them from a single endpoint without backend changes.

### GraphQL resolvers and the metadata join

GraphQL is a natural fit for the join between the read model and the metadata table (ADR-0003). Rather than a single SQL join query in the `@QueryHandler`, the join is expressed as a resolver graph:

```graphql
type Application {
    applicationId: ID!
    status: String!
    laaReference: String!       # from read model
    applicant: Applicant        # resolved from metadata table
}

type Applicant {
    surname: String             # only fetched if the client requests it
    dateOfBirth: String
}
```

The metadata resolver only runs if the client requests `applicant` fields. A work queue query that does not ask for `applicant.surname` never touches the metadata table. This is elegant but introduces the N+1 problem — a client requesting applicant data for a page of 20 results triggers 20 metadata queries. This is the standard GraphQL N+1 problem and is mitigated with DataLoader (request batching), which works alongside Spring for GraphQL without issue.

### The filtering caveat

GraphQL does not inherently solve filtered queries on metadata fields. Filtering is expressed as query arguments:

```graphql
applications(filter: { applicantSurname: "Smith" }, page: 2) { ... }
```

The resolver still has to translate this to a SQL join or a two-step query. The underlying database strategy from ADR-0003 still applies — GraphQL makes the API surface cleaner and more flexible, but does not change the storage or query execution story.

### Where GraphQL adds friction

- **Infrastructure:** A GraphQL server is required. Spring for GraphQL is mature and Spring Boot 3.x native, but it is more infrastructure than a REST controller.
- **Axon query bus integration:** Axon's `@QueryHandler` maps cleanly to GraphQL `DataFetcher`s for top-level queries. Field-level resolvers (e.g. for `applicant`) bypass the Axon query bus and call services or repositories directly.
- **Client tooling:** GraphQL clients (Apollo, etc.) are more complex than a REST HTTP client. If consumers are internal services rather than browser UI clients, the benefit is smaller.
- **HTTP caching:** HTTP-level caching is harder with GraphQL since queries are typically POST. Application-level caching is required instead.

---

## Recommendations

| Concern | Recommendation |
|---|---|
| Metadata table storage shape | Hybrid: typed indexed columns for known filter fields (extracted from nested paths at write time), `jsonb raw` column for the full nested structure |
| Read model storage shape | Same hybrid — typed columns for status, dates, and references; `jsonb` for the full nested application payload |
| Equality/membership filtering on array fields | Extract to a native `TEXT[]` column with a GIN index (e.g. `matter_types TEXT[]`) |
| Cross-field queries within array elements | Child table is warranted here (e.g. "proceedings where matterType = X AND amount > Y") |
| Range/inequality queries on array element fields | Native typed array column (e.g. `NUMERIC[]`) with GIN, or child table if cross-field |
| Free-text search across the whole structure | Generated `tsvector` column from `jsonb_to_tsvector` |
| Promoting new scalar filter fields | Targeted migration + backfill from nested path in `raw`; no projection replay needed |
| GET endpoint field proliferation | GraphQL is worth evaluating if multiple consumers have divergent field needs; a `fields` projection parameter on REST is the lighter alternative |

The `jsonb raw` column absorbs structural churn from upstream with no migration cost. The typed columns deliver indexed filtering performance that pure JSON cannot guarantee. GraphQL is then a clean presentation layer over that storage strategy — an independent decision that can be adopted incrementally without changing the storage approach.

---

## PostgreSQL vs NoSQL: the schema reusability tradeoff

The hybrid PostgreSQL approach above was designed around a single application type with a known, relatively stable schema. If the system needs to support multiple application types with genuinely different schemas — or if significant schema divergence is expected over time — the JOIN-first assumption deserves scrutiny.

### The JOIN advantage of PostgreSQL

With PostgreSQL, a filtered paginated query across the read model and metadata is a single SQL statement with predicates on both tables, fully planned by the query optimiser. This is the key capability that justified co-locating the read model and metadata tables in ADR-0003.

With a document store (MongoDB), the equivalent requires either:

- **Denormalisation** — copy filterable fields from both sources into one collection at write time. The JOIN disappears but GDPR deletion must now propagate to every collection that holds the denormalised field.
- **Application-side join** — query one collection, extract IDs, query the second. Pagination breaks: you cannot reliably return page 2 of applications filtered by applicant surname and status without fetching and merging unbounded result sets.
- **`$lookup`** — MongoDB's aggregation join. Works, but cannot push predicates from the second collection into the optimiser for the first. Acceptable at small-medium scale; degrades under load.

The severity of the JOIN gap depends on how many cross-source filtered queries are needed. If filtering is primarily on domain fields (status, dates, office code) with metadata as display-only enrichment, the gap is small. If users routinely filter on applicant name *and* status *and* proceeding type simultaneously, it matters more.

### The schema reusability advantage of a document store

The hybrid PostgreSQL approach degrades when multiple application types with different schemas must coexist, or when schema evolution is frequent. The typed columns are the problem: they are defined per table and optimised for one known schema. With multiple schemas you face:

- **Separate tables per application type** — clean in isolation, but every query spanning types requires a `UNION` or separate queries. Work queues mixing types become painful.
- **Single table with nullable columns per type** — the sparse column problem. A table with 40 typed columns of which only 12 apply to any given row. Schema grows with every new application type.
- **Typed columns for common fields only** — workable, but you lose indexed filtering on type-specific fields, which is the point of the typed columns.

A document store has none of this problem. A single collection holds documents of any shape. Indexes coexist without conflict — `{"applicant.surname": 1}` and `{"defendant.surname": 1}` both exist on the same collection. Work queue queries spanning types are a single query with a type discriminator.

Schema evolution within a single type is also easier: old documents retain the old shape, new documents carry the new shape, and the application handles both. No DDL migration, no coordination with replay.

### Decision hinge

| Situation | Preferred approach |
|---|---|
| Single application type, stable or slowly evolving schema | PostgreSQL hybrid — JOIN advantage is the dominant factor |
| Single application type, significant schema churn expected | PostgreSQL hybrid still — JSONB `raw` absorbs most structural change without migrations |
| Multiple application types, work queues spanning types | MongoDB becomes compelling — sparse column / UNION cost in PostgreSQL is a recurring tax |
| Complex multi-field filtering across all scenarios | Elasticsearch as a search index alongside either store — removes the JOIN dependency entirely |

If there is genuine uncertainty about future schema diversity, that uncertainty is itself an argument for MongoDB — not because it is better at any individual capability, but because it does not require you to be right about the schema upfront. The JOIN gap is the cost of that insurance.

### Elasticsearch as a search tier

A third path exists that decouples the filtering requirement from the storage choice: use PostgreSQL (or MongoDB) as the authoritative store and Elasticsearch as a dedicated search index for work queue and list page queries. Writes go to both; filtered reads go to Elasticsearch.

- Elasticsearch handles nested objects, arrays, full-text, range queries, and arbitrary field combinations natively and efficiently.
- GDPR deletion is an update by document ID in Elasticsearch — straightforward.
- The index is rebuilt from the authoritative store (not from the event stream), so replay safety is preserved.
- The cost is operational: two stores to operate and keep in sync. An outbox or event-driven sync pattern is required to keep Elasticsearch consistent.

This is the industry-standard pattern for work queues and list pages with complex filtering requirements. It becomes worthwhile when the filtering requirements are numerous or unpredictable enough that the typed-column promotion path in the hybrid PostgreSQL approach would become a persistent source of migrations.
