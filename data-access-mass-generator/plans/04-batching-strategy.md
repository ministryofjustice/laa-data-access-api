# Plan: Batching Strategy for the Mass Data Generator

## Constraint

**No changes may be made to `data-access-service`** — not to entity classes, not to Flyway migrations,
not to repositories. All changes are confined to `data-access-mass-generator`.

---

## The IDENTITY + UUID problem

Every entity in `data-access-service` is annotated:

```java
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
@Column(columnDefinition = "UUID")
private UUID id;
```

The DB schema defines these columns as `UUID PRIMARY KEY DEFAULT uuid_generate_v4()`. There is a
fundamental mismatch here:

- `GenerationType.IDENTITY` tells Hibernate to delegate ID generation to the DB via an auto-increment
  serial column. After each `INSERT`, Hibernate fires a separate `SELECT lastval()` (or equivalent) to
  retrieve the generated key.
- The actual DB columns are `UUID`, not `BIGSERIAL`. PostgreSQL generates the UUID via
  `uuid_generate_v4()`, not a sequence, so `lastval()` returns nothing useful.
- Hibernate therefore **cannot batch `IDENTITY` inserts at all** — it must flush and round-trip to the
  DB after every single row to attempt to read back the key.

The correct fix is to change the ID generation strategy, but that requires modifying `data-access-service`
which is out of scope. The implication is:

> **Every `saveAndFlush` call in the mass generator is one network round-trip to the DB, regardless of
> JDBC batch settings.** The `batch_size`, `order_inserts`, and `reWriteBatchedInserts` Hibernate
> properties have no effect on `IDENTITY` inserts.

---

## What is still achievable without touching the application

Even though per-row insert batching is blocked, significant throughput and memory improvements are
available within the mass generator alone:

### 1. Proceedings — accumulated list per application ✅ Already done

Each application's proceedings are accumulated into a `List<ProceedingEntity>` and persisted with
`saveAllAndFlush(list)`. Because `ProceedingEntity` also uses `IDENTITY`, Hibernate still inserts each
row individually, but `saveAllAndFlush` at least processes them in a single JPA call and keeps the
number of explicit round-trips bounded (Option C from plan 03).

### 2. Pool pre-generation — `createAndPersistMultipleRandom` ✅ Already done

Caseworker (100) and individual (1,000) pools are each saved with one `saveAllAndFlush(list)`. Same
caveat as above — IDENTITY prevents true batching — but the pool sizes are small enough that this is
not the bottleneck.

### 3. Hibernate session memory — `flushAndClear` every 500 rows ✅ Already done

Without clearing the Hibernate first-level cache the persistence context grows unboundedly. The existing
`flushAndClear()` call every 500 applications keeps memory flat at any count.

### 4. JDBC URL and Hibernate batch properties ⚠️ Configured but ineffective for IDENTITY

`reWriteBatchedInserts=true`, `batch_size: 500`, `order_inserts: true` are all set in `application.yml`.
These settings are correct and harmless, but they do not improve throughput for `IDENTITY`-mapped
entities. They would take effect if the ID strategy were ever changed.

---

## What needs to be cleaned up

### Delete V19__add_id_sequences.sql

`data-access-service/src/main/resources/db/migration/V19__add_id_sequences.sql` was created as part of
a previous (now abandoned) attempt to switch to `SEQUENCE`-based ID generation. Since no changes are
being made to `data-access-service`, this migration must be deleted. If it runs it will create unused
sequences (`applications_id_seq`, `proceedings_id_seq`) that serve no purpose and could mislead future
developers.

**Action:** delete `data-access-service/src/main/resources/db/migration/V19__add_id_sequences.sql`.

> If the migration has already been applied to any environment's DB, it must also be rolled back there
> (`DROP SEQUENCE IF EXISTS applications_id_seq; DROP SEQUENCE IF EXISTS proceedings_id_seq;`) and
> removed from the `flyway_schema_history` table before the file is deleted.

---

## Summary of current state

| Concern | Approach | Effective? |
|---|---|---|
| Per-row insert throughput | Blocked by `IDENTITY` on UUID columns — one round-trip per row | ❌ Cannot improve without changing `data-access-service` |
| Hibernate session memory | `flushAndClear()` every 500 applications | ✅ Effective |
| Proceeding insert grouping | `saveAllAndFlush(list)` per application | ✅ Reduces code complexity; same DB round-trips |
| Pool pre-generation | `createAndPersistMultipleRandom` + `saveAllAndFlush` | ✅ Small pools, not a bottleneck |
| JDBC batch config | Set in `application.yml` — no effect until ID strategy changes | ⚠️ Harmless but currently a no-op |
| Stale Flyway migration | `V19__add_id_sequences.sql` — must be deleted | ❌ Needs action |

---

## Files to change

| File | Action |
|---|---|
| `data-access-service/src/main/resources/db/migration/V19__add_id_sequences.sql` | **Delete** |

## Files unchanged

Everything else — no changes to `data-access-service` entity classes, no changes to the
mass-generator Java source beyond what is already in place from plan 03.

