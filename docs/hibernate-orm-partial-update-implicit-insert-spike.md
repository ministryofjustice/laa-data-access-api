# Spike Results: Hibernate ORM Partial Update & Implicit Insert Behaviour

**Ticket:** DSTEW-1424  
**Date:** 9 April 2026  
**Entity under test:** `ApplicationEntity` (table: `applications`)  
**ORM:** Spring Data JPA + Hibernate 6, PostgreSQL  
**Test class:** `ApplicationEntityOrmSpikeTest`  
**Configuration change:** `@OneToOne` fetch type on `decision` and `caseworker` switched to `FetchType.LAZY` for the duration of this spike

---

## Summary

| Scenario | Result | Risk |
|---|---|---|
| Managed entity partial update (within transaction) | ✅ Safe — FK columns preserved | None |
| Detached entity re-attached via `save()` | ✅ Safe — FK columns preserved | None |
| Manually constructed entity with existing ID | ⚠️ Risk confirmed — FKs silently nullified | **High** |
| Save with `decision = null` | ✅ Safe — no implicit INSERT | None |
| Save with `decision = new DecisionEntity()` (no cascade) | ✅ Safe — Hibernate throws, does not insert | None |
| Lombok `@Builder` default construction | ✅ Safe — `decision` and `caseworker` default to `null` | None |

---

## Finding 1 — Managed entity partial update is safe ✅

**Scenario:** Load `ApplicationEntity` within an active transaction, update a scalar field (`status`), flush — without loading or touching `decision` or `caseworker`.

**SQL observed:**
```sql
update applications
set application_content=?, apply_application_id=?, caseworker_id=?,
    category_of_law=?, decision_id=?, is_auto_granted=?, laa_reference=?,
    matter_types=?, modified_at=?, office_code=?, schema_version=?,
    status=?, submitted_at=?, used_delegated_functions=?, version=?
where id=? and version=?
```

**Result:** Hibernate carries the existing `caseworker_id` and `decision_id` values through in the UPDATE because the LAZY proxy objects (even uninitialised) retain their identity on the managed entity. The FK columns are not nullified.

**Conclusion:** The standard load-and-modify pattern is safe under LAZY loading.

---

## Finding 2 — Detached entity re-attach is safe ✅

**Scenario:** Load `ApplicationEntity`, close the transaction (entity becomes detached, LAZY proxies are uninitialised), update a scalar field, call `save()` (which triggers `EntityManager.merge()`).

**Result:** Hibernate merge copies the state of the detached object back into a new managed instance. Because the LAZY proxy references are carried on the detached entity (even uninitialised), their identity is preserved during merge. The FK columns were not nullified.

**Conclusion:** Detaching and re-attaching an entity that was fully loaded before detach is safe. The proxies carry enough state (the FK identity) for merge to produce a correct UPDATE.

> **Note:** This result assumes the entity was loaded via `findById` before detach. If the entity had been *constructed in code with relationships set to `null` before detach*, the result would be different — see Finding 3.

---

## Finding 3 — Manually constructed entity with existing ID silently nullifies FKs ⚠️

**Scenario:** A new `ApplicationEntity` is constructed in code using `ApplicationEntity.builder()` with an existing `id` and `version` set, but `decision` and `caseworker` left as `null`. This entity is passed to `save()`.

**SQL observed:**
```sql
update applications
set application_content=?, apply_application_id=?, caseworker_id=?,
    ...decision_id=?, ...
where id=? and version=?

-- Bind values: caseworker_id=NULL, decision_id=NULL
```

**Result:** Hibernate treats this as a merge of a detached entity. Since `decision` and `caseworker` are `null` on the Java object, Hibernate writes `NULL` to `caseworker_id` and `decision_id` in the UPDATE statement. **No exception is raised.** The FK values are silently lost.

**Confirmed risk:** This is a silent data loss scenario. The `@Version` field provides optimistic locking (preventing concurrent overwrites) but does not prevent an in-process nullification of FK columns.

**When this can happen in production:**
- A mapper or service builds a new `ApplicationEntity` from a command/request without loading the existing row
- A partial update endpoint constructs a fresh entity rather than loading and modifying
- Any code that uses `toBuilder()` on a DTO/POJO (not the JPA entity) and then sets the ID

---

## Finding 4 — Saving with `decision = null` does not trigger an implicit INSERT ✅

**Scenario:** Persist a brand-new `ApplicationEntity` with `decision` left as `null` (the default from `ApplicationEntityGenerator`).

**SQL observed:** Only `INSERT INTO applications` — no `INSERT INTO decisions`.

**Result:** `ApplicationEntity.decision` has no `cascade` type defined. Hibernate correctly leaves `decision_id = NULL` and does not attempt to persist the unset relationship.

**Conclusion:** The current mapping is correct for null-decision creates.

---

## Finding 5 — Assigning a transient `DecisionEntity` throws, not inserts ✅

**Scenario:** Reload an existing `ApplicationEntity`, assign `decision = new DecisionEntity(...)` (not yet persisted), call `saveAndFlush()`.

**Result:** Hibernate throws `TransientPropertyValueException` wrapped in `InvalidDataAccessApiUsageException`:

```
org.hibernate.TransientPropertyValueException:
  Persistent instance of 'ApplicationEntity' references an unsaved transient instance of
  'DecisionEntity' (persist the transient instance before flushing)
  [ApplicationEntity.decision -> DecisionEntity]
```

**Conclusion:** The absence of `cascade = PERSIST` on `ApplicationEntity.decision` is correct and acts as a guard. Accidental assignment of an unsaved `DecisionEntity` will fail loudly rather than inserting silently. No configuration change needed here.

---

## Finding 6 — Lombok `@Builder` does not initialise relationships ✅

**Scenario:** Call `ApplicationEntity.builder().status(...).build()` with no other fields set.

**Result:** `getDecision()` and `getCaseworker()` both return `null`. Lombok's `@Builder` does not initialise fields to default instances — no accidental implicit inserts can originate from the builder itself.

---

## Root Cause Analysis — Finding 3

The risk in Finding 3 stems from Hibernate's **merge semantics**:

- `save()` on a detached-or-new entity with an `id` is treated as `EntityManager.merge()`
- Merge copies the state of the provided object into the persistence context
- If a field is `null` on the object, Hibernate writes `NULL` to the corresponding column
- LAZY loading is irrelevant here — the entity was never loaded in the first place; it was constructed in memory

This is not a Hibernate bug or misconfiguration. It is correct JPA merge behaviour. The risk exists whenever code bypasses the load-modify-flush pattern.

### LAZY loading caveat

Hibernate cannot always honour `FetchType.LAZY` on the **owning side** of a `@OneToOne` without bytecode enhancement. In this configuration, Hibernate may silently fall back to EAGER loading on `findById` (it must issue a JOIN to determine whether the FK column is non-null). The SQL logging output confirms whether LAZY is respected. Bytecode enhancement via `org.hibernate.bytecode.enhance.spi` or a `@MapsId` shared-PK strategy would guarantee true LAZY behaviour.

---

## Recommendations

### 1. Team coding guideline — never construct a partial entity with an existing ID

The safe pattern for updates is always:

```java
// ✅ Safe: load-and-modify within a transaction
ApplicationEntity entity = applicationRepository.findById(id).orElseThrow();
entity.setStatus(newStatus);
// no explicit save needed — dirty checking handles the flush
```

Never do:

```java
// ❌ Unsafe: will silently nullify decision_id and caseworker_id
ApplicationEntity partial = ApplicationEntity.builder()
    .id(existingId)
    .version(existingVersion)
    .status(newStatus)
    .build();
applicationRepository.save(partial);
```

#### You never need to call `save()` when using the load-first pattern

Within a `@Transactional` method, Hibernate tracks every managed entity via **dirty checking**. When the method returns and the transaction commits, Hibernate compares the current state of each entity to the snapshot it took when the entity entered the persistence context. Any changed fields are written in a single `UPDATE` statement — no `save()` call is required.

This works for any number of field changes:

```java
@Transactional
public void updateApplication(UUID id, ApplicationStatus newStatus, String newOfficeCode) {
    ApplicationEntity entity = applicationRepository.findById(id).orElseThrow();

    entity.setStatus(newStatus);
    entity.setOfficeCode(newOfficeCode);
    entity.setSchemaVersion(entity.getSchemaVersion() + 1);
    // No save() needed — Hibernate emits ONE UPDATE covering all three changes:
    // UPDATE applications SET status=?, office_code=?, schema_version=?, modified_at=?, version=?
    // WHERE id=? AND version=?
}
```

`@UpdateTimestamp` on `modifiedAt` and `@Version` on `version` are both handled automatically in this UPDATE.

**`save()` / `saveAndFlush()` is only needed in these specific situations:**

| Situation | Reason |
|---|---|
| Inserting a new entity (no existing ID) | Entity is transient — needs to enter the persistence context |
| You need the server-generated ID back immediately | `saveAndFlush()` forces the INSERT and returns the reloaded entity with the DB-assigned ID |
| You need to flush mid-transaction before a subsequent query | Ensures write ordering within a single transaction |
| Re-attaching a truly detached entity | `save()` triggers `EntityManager.merge()` — but see Finding 2 for risks |

> **Important:** `@Transactional` must be on the service/use-case method for dirty checking to fire. Without it, the entity is detached the moment `findById` returns and Hibernate never generates the UPDATE.

### 2. Consider `@Modifying @Query` for narrow column updates

For use cases that genuinely need to update a single column without loading the full entity:

```java
@Modifying
@Query("UPDATE ApplicationEntity a SET a.status = :status WHERE a.id = :id")
void updateStatus(@Param("id") UUID id, @Param("status") ApplicationStatus status);
```

This emits a targeted `UPDATE applications SET status=? WHERE id=?` and never touches FK columns.

### 3. Validate bytecode enhancement for true LAZY `@OneToOne`

Confirm via SQL log output whether `findById` issues a JOIN on `decisions` and `caseworkers`. If it does, `FetchType.LAZY` is being silently ignored. Consider either:
- Enabling Hibernate bytecode enhancement in `build.gradle`
- Switching to a `@MapsId` strategy (shared PK) if the relationship is always 1:1

### 4. No changes needed for cascade configuration

`ApplicationEntity.decision` correctly has no `cascade` defined. Finding 5 confirms this produces a loud failure rather than silent data corruption. No configuration change is required.

---

## Test Evidence

All findings are reproducible via:

```bash
./gradlew :data-access-service:integrationTest --tests "*.ApplicationEntityOrmSpikeTest"
```

Full SQL output for each test is available in the test run logs with:

```properties
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
logging.level.org.hibernate.SQL=DEBUG
logging.level.org.hibernate.orm.jdbc.bind=TRACE
```

(Configured in `data-access-service/src/integrationTest/resources/application-test.properties`)

---

## Design Discussion: Would an Aggregate Root Redesign Mitigate Finding 3?

Two redesign options were explored in discussion. Neither eliminates the root cause but they affect the failure mode significantly.

### Option A — Move to JSONB / embedded fields (no FK)

Storing `DecisionEntity` data as embedded columns or inside `applicationContent` JSONB changes where the null lands but not the fact that it lands there. A partial entity with `decision = null` would:

- **JSONB:** overwrite the JSON field with whatever the partial entity carries (likely `null` or an empty map)
- **Embedded columns:** null out the decision columns directly in the `applications` row

The risk is not mitigated — the failure mode shifts from "FK nullified" to "data column nullified or overwritten."

### Option B — Aggregate root: `DecisionEntity` as a child field of `ApplicationEntity`, `DecisionRepository` removed

This is the more meaningful design change. It has two distinct effects:

**Effect 1 — Structural prevention (positive):**  
Removing `DecisionRepository` means code cannot save a `DecisionEntity` independently. All decision mutations must flow through `ApplicationEntity`. This structurally eliminates Finding 5 (transient insert risk) and reduces the *likelihood* of Finding 3 occurring, because the only way to interact with a decision is to load the `ApplicationEntity` first.

**Effect 2 — Silent deletion instead of silent nullification (negative):**  
With `cascade = ALL, orphanRemoval = true` on `ApplicationEntity.decision`, Hibernate treats a `null` on the Java field as "this child should be removed." A partial save where `decision = null` would emit:

```sql
DELETE FROM decisions WHERE id = ?
```

instead of:

```sql
UPDATE applications SET decision_id = NULL WHERE id = ?
```

The deletion is harder to recover from than a nullified FK, and it remains completely silent — no exception is raised.

| Design | Failure mode for `save(partialEntity)` where `decision = null` |
|---|---|
| Current (FK, no cascade) | `decision_id` set to `NULL` — `decisions` row survives |
| Aggregate root (`cascade = ALL, orphanRemoval = true`) | `decisions` row is **silently deleted** |

### Conclusion

The aggregate root redesign is architecturally sound for consistency boundary reasons and its main protective value comes from **removing `DecisionRepository`** — this makes the anti-pattern harder to reach in the first place. However, it does not eliminate Finding 3's root cause (Hibernate merge semantics) and it changes the failure mode from nullification to deletion.

The mitigations that directly address the root cause remain:

| Mitigation | Addresses Finding 3 | Notes |
|---|---|---|
| Remove `DecisionRepository` (aggregate root) | Indirectly — removes escape hatch | Recommended, but not sufficient alone |
| Load-first coding convention | Yes — prevents the anti-pattern | Required regardless of design |
| `@DynamicUpdate` on `ApplicationEntity` | Partially — only for managed entities | Does not protect against new-instance-with-ID |
| `@Modifying @Query` for narrow updates | Yes — guarantees targeted SQL | Most reliable for specific column updates |
