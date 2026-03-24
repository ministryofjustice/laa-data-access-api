# Plan: Generate Application Entities with `FullJsonGenerator`

## Overview

Update `MassDataGeneratorRunner` so that each generated `ApplicationEntity` looks exactly as if it was
created via `createApplication` and works correctly with `getApplication`. This means:

- The `ApplicationEntity` top-level columns (`applyApplicationId`, `officeCode`, `submittedAt`,
  `laaReference`, `categoryOfLaw`, `matterType`, `usedDelegatedFunctions`) are derived from the
  `FullJsonGenerator` output, mirroring what `ApplicationContentParserService` does at runtime.
- The `applicationContent` column stores the full `FullJsonGenerator` JSON map — exactly what
  `ApplicationMapper.toApplicationEntity` stores.
- One `ProceedingEntity` row is persisted per proceeding in the content — exactly what
  `ProceedingsService.saveProceedings` / `ProceedingMapper.toProceedingEntity` does at runtime.
- Each `ApplicationEntity` is linked to a randomly selected `CaseworkerEntity` and a randomly
  selected `IndividualEntity` from pools that are generated upfront.

---

## Background: how `createApplication` and `getApplication` work

### `createApplication` stores

| Step | Code | What it writes to `ApplicationEntity` / DB |
|------|------|--------------------------------------------|
| 1 | `applicationMapper.toApplicationEntity(req)` | `status`, `laaReference`, `applicationContent` (full JSON map), `individuals` (linked set) |
| 2 | `applicationContentParser.normaliseApplicationContentDetails` | `applyApplicationId` ← `content.id`; `officeCode` ← `content.office.code`; `submittedAt` ← `content.submittedAt`; `categoryOfLaw` / `matterType` ← lead proceeding; `usedDelegatedFunctions` ← any proceeding |
| 3 | `proceedingsService.saveProceedings` | One `ProceedingEntity` per `content.proceedings[]`: `applyProceedingId` ← `proceeding.id`; `description`; `isLead`; `proceedingContent` ← full proceeding JSON map |

### `getApplication` reads back

| Response field | Source |
|---|---|
| `applicationId` | `ApplicationEntity.id` |
| `status`, `laaReference` | entity top-level columns |
| `assignedTo` | `entity.caseworker.id` |
| `submittedAt` | `entity.submittedAt` |
| `usedDelegatedFunctions` | `entity.usedDelegatedFunctions` |
| `provider.officeCode` | `entity.officeCode` |
| `provider.contactEmail` | `applicationContent.submitterEmail` |
| `opponents` | `applicationContent.applicationMerits.opponents[*].opposable` |
| `proceedings` | fetched from `proceedings` table by `applicationId`; each field from `proceedingContent` deserialised as `Proceeding` |
| `version` | `entity.version` |

### ID mapping rule

> IDs in the JSON map to `applyId` columns in the entity tables.

| JSON field | Entity column |
|---|---|
| `applicationContent.id` | `ApplicationEntity.applyApplicationId` |
| `proceeding.id` | `ProceedingEntity.applyProceedingId` |

---

## Field mapping summary

| `ApplicationContent` / `Proceeding` field | `ApplicationEntity` column | `ProceedingEntity` column |
|---|---|---|
| `content.id` | `applyApplicationId` | — |
| `content.office.code` | `officeCode` | — |
| `content.submittedAt` | `submittedAt` (as `Instant`) | — |
| `content.laaReference` | `laaReference` | — |
| `content.submitterEmail` | — (stays in JSON) | — |
| `content` (full serialised map) | `applicationContent` | — |
| lead `proceeding.categoryOfLaw` | `categoryOfLaw` | — |
| lead `proceeding.matterType` | `matterType` | — |
| any `proceeding.usedDelegatedFunctions = true` | `usedDelegatedFunctions` | — |
| `proceeding.id` | — | `applyProceedingId` |
| `proceeding.description` | — | `description` |
| `proceeding.leadProceeding` | — | `isLead` |
| `proceeding` (full serialised map) | — | `proceedingContent` |

---

## Batching strategy

At millions of records, calling `saveAndFlush` once per entity (the current `PersistedDataGenerator.persist`
single-entity path) is far too slow: each call opens a round-trip to PostgreSQL and immediately flushes.
Several complementary approaches are available within this codebase.

### Option A — Hibernate JDBC batch inserts (recommended baseline)

Hibernate can group multiple `INSERT` statements into a single JDBC batch before sending them to the
database. This is controlled entirely through configuration — no code changes to repository or entity
classes are required.

**How it works:**
Hibernate accumulates inserts in memory and flushes them as a single multi-row batch when the batch size
is reached or the session is flushed. The database receives one network round-trip per batch instead of
one per row.

**Required configuration in `data-access-mass-generator/src/main/resources/application.yml`:**

```yaml
spring:
  jpa:
    properties:
      hibernate:
        jdbc:
          batch_size: 500          # rows per JDBC batch; tune between 250–1000
          batch_versioned_data: true
        order_inserts: true        # group inserts by table so batches are homogeneous
        order_updates: true
```

**Required JDBC URL parameter** — PostgreSQL's JDBC driver only sends batches when
`reWriteBatchedInserts=true` is appended to the connection URL:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/laa_data_access_api?reWriteBatchedInserts=true
```

Without this flag the JDBC driver still sends individual statements despite Hibernate batching them.

**Constraint:** `ApplicationEntity` uses `@GeneratedValue(strategy = GenerationType.IDENTITY)`, which
requires a database round-trip to retrieve the generated `id` after each insert. Hibernate therefore
cannot batch `ApplicationEntity` inserts by default. Two fixes exist:

- **(Preferred)** Switch `ApplicationEntity` to `GenerationType.SEQUENCE` with
  `allocationSize = 500` (matches `batch_size`). Hibernate then pre-allocates 500 IDs in one call and
  can batch all 500 inserts. This requires a new Flyway migration to add the sequence.
- **(No-schema-change)** Keep `IDENTITY` but set `hibernate.jdbc.batch_size` only for
  `ProceedingEntity` (which can use `SEQUENCE`). Application inserts remain one-at-a-time but proceeding
  inserts (3× more numerous) are fully batched.

`ProceedingEntity` also uses `IDENTITY` — the same sequence change should be applied to it for full
benefit.

---

### Option B — Chunk the loop and flush + clear the `EntityManager` each chunk

Even with JDBC batching enabled, the Hibernate first-level cache (persistence context) grows unboundedly
if the `EntityManager` is never cleared. At millions of rows this causes an `OutOfMemoryError`.

Fix: process applications in chunks of `BATCH_SIZE` inside a `@Transactional` boundary, then call
`entityManager.flush()` + `entityManager.clear()` after each chunk. `PersistedDataGenerator` already
has `EntityManager` injected (but currently unused) — expose a `flushAndClear()` method:

```java
// In PersistedDataGenerator:
public void flushAndClear() {
    entityManager.flush();
    entityManager.clear();
}
```

```
// In MassDataGeneratorRunner.run():
final int BATCH_SIZE = 500;

for (int i = 0; i < count; i++) {
    // ... generate and persist application + proceedings (see Change 4) ...

    if ((i + 1) % BATCH_SIZE == 0) {
        persistedDataGenerator.flushAndClear();
        System.out.printf("Committed batch up to %d%n", i + 1);
    }
}
// flush any remaining partial batch
persistedDataGenerator.flushAndClear();
```

This keeps memory flat regardless of `count`.

---

### Option C — Add `saveAllAndFlush` batch methods to `PersistedDataGenerator`

The existing `createAndPersistMultiple` already calls `repository.saveAllAndFlush(entities)` for list
inputs. The single-entity `persist` path calls `saveAndFlush` (one flush per entity). For the
application generation loop the entities are built one at a time (because each proceeding depends on the
saved application's `id`), so `saveAllAndFlush` cannot be used for the applications list directly.

However, proceedings for a single application can be accumulated into a list and persisted in one
`saveAllAndFlush` call rather than three individual ones. Add an explicit list-persist step in the loop:

```
List<ProceedingEntity> proceedingBatch = new ArrayList<>();
for (Proceeding p : content.getProceedings()) {
    proceedingBatch.add(DataGenerator.createDefault(ProceedingsEntityGenerator.class, b -> b
        .applicationId(app.getId())
        ...
    ));
}
// persist all proceedings for this application in one call
persistedDataGenerator.persist(ProceedingsEntityGenerator.class, proceedingBatch);
```

Combined with Option A batching this reduces per-application round-trips from `1 + N_proceedings` to
`1 + 1`.

---

### Option D — `PersistedDataGenerator.createAndPersistMultipleRandom` overload

`createAndPersistMultiple` currently calls `createDefault` for every element. For the caseworker and
individual pre-generation pools this means every entity is identical (same seeded faker). Add a
`createAndPersistMultipleRandom` overload that calls `createRandom` per element, and uses
`saveAllAndFlush` in one shot:

```java
public <TEntity, TBuilder, TGenerator extends BaseGenerator<TEntity, TBuilder>>
List<TEntity> createAndPersistMultipleRandom(Class<TGenerator> generatorType, int count) {
    List<TEntity> entities = DataGenerator.createMultipleRandom(generatorType, count);
    if (entities.isEmpty()) return entities;
    return persist(generatorType, entities);
}
```

This saves the entire caseworker pool (100 rows) and individual pool (1 000 rows) each in a single
`saveAllAndFlush`, which with JDBC batching (Option A) becomes a handful of network round-trips.

---

### Recommended combination

| Concern | Approach |
|---|---|
| Raw insert throughput | Option A — JDBC batch inserts + `reWriteBatchedInserts` + sequence ID strategy |
| Memory stability at millions of rows | Option B — chunk loop with `flushAndClear` every 500 |
| Proceeding insert efficiency per application | Option C — accumulate proceeding list, one `saveAllAndFlush` |
| Pool pre-generation efficiency | Option D — `createAndPersistMultipleRandom` with `saveAllAndFlush` |

---

## Changes required

### Change 1 — `FullJsonGenerator`: guarantee exactly one lead proceeding

`FullProceedingGenerator.createDefault()` currently sets `leadProceeding` to a random boolean.
`ApplicationContentParserService` throws a `ValidationException` if there is not **exactly one** lead
proceeding. Fix the proceedings list in `FullJsonGenerator.createDefault()` so the first proceeding is
always lead and the rest are non-lead:

```java
// Before (all three use random leadProceeding):
.proceedings(List.of(
    proceedingGenerator.createDefault(),
    proceedingGenerator.createDefault(),
    proceedingGenerator.createDefault()))

// After (exactly one lead):
.proceedings(List.of(
    proceedingGenerator.createDefault(b -> b.leadProceeding(true)),
    proceedingGenerator.createDefault(b -> b.leadProceeding(false)),
    proceedingGenerator.createDefault(b -> b.leadProceeding(false))))
```

---

### Change 2 — `MassDataGeneratorRunner`: inject `ObjectMapper`

Add an `@Autowired ObjectMapper` field. This is the Spring-managed Jackson mapper (already configured
in the service layer) and is used to serialise `ApplicationContent` and `Proceeding` objects to
`Map<String, Object>` for the JSON columns, mirroring what the production mappers do.

```java
@Autowired
private ObjectMapper objectMapper;
```

---

### Change 3 — `PersistedDataGenerator`: add `flushAndClear` and `createAndPersistMultipleRandom`

Two small additions to support memory-safe chunked processing (Option B) and random pool generation
(Option D). `EntityManager` is already injected but unused.

```java
// expose flush+clear for chunked loop
public void flushAndClear() {
    entityManager.flush();
    entityManager.clear();
}

// random-variant pool generation with single saveAllAndFlush
public <TEntity, TBuilder, TGenerator extends BaseGenerator<TEntity, TBuilder>>
List<TEntity> createAndPersistMultipleRandom(Class<TGenerator> generatorType, int count) {
    List<TEntity> entities = DataGenerator.createMultipleRandom(generatorType, count);
    if (entities.isEmpty()) return entities;
    return persist(generatorType, entities);
}
```

---

### Change 4 — `MassDataGeneratorRunner`: rewrite the application generation loop

Replace the empty loop section with the logic below. Uses the chunked flush+clear pattern (Option B)
and accumulates proceedings per application into a single list persist (Option C).

#### Full loop logic (pseudo-code)

```
Faker faker = new Faker();
final int BATCH_SIZE = 500;

// Pre-generate pools using random variants in one saveAllAndFlush each (Option D)
List<CaseworkerEntity> caseworkers =
    persistedDataGenerator.createAndPersistMultipleRandom(CaseworkerGenerator.class, 100);
List<IndividualEntity> individuals =
    persistedDataGenerator.createAndPersistMultipleRandom(IndividualEntityGenerator.class, 1000);

for (int i = 0; i < count; i++):

  // 1. Generate full rich application content (mirrors what Apply POSTs to createApplication)
  ApplicationContent content = new FullJsonGenerator().createDefault();

  // 2. Pick a random caseworker and individual from the pre-generated pools
  CaseworkerEntity cw    = caseworkers.get(faker.number().numberBetween(0, caseworkers.size()));
  IndividualEntity indiv = individuals.get(faker.number().numberBetween(0, individuals.size()));

  // 3. Derive entity-level columns from the content (mirrors ApplicationContentParserService)
  Proceeding lead = content.getProceedings().stream()
      .filter(p -> Boolean.TRUE.equals(p.getLeadProceeding()))
      .findFirst()
      .orElseThrow();

  CategoryOfLaw col = new CategoryOfLawTypeConvertor()
      .lenientEnumConversion(lead.getCategoryOfLaw());   // nullable-safe
  MatterType    mt  = new MatterTypeConvertor()
      .lenientEnumConversion(lead.getMatterType());       // nullable-safe
  boolean       udf = content.getProceedings().stream()
      .anyMatch(p -> Boolean.TRUE.equals(p.getUsedDelegatedFunctions()));

  // 4. Persist ApplicationEntity with all required columns populated
  ApplicationEntity app = persistedDataGenerator.createAndPersist(
      ApplicationEntityGenerator.class,
      b -> b
          .applyApplicationId(content.getId())
          .laaReference(content.getLaaReference())
          .submittedAt(Instant.parse(content.getSubmittedAt()))
          .officeCode(content.getOffice().getCode())
          .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
          .categoryOfLaw(col)
          .matterType(mt)
          .usedDelegatedFunctions(udf)
          .applicationContent(objectMapper.convertValue(content, Map.class))
          .caseworker(cw)
          .individuals(Set.of(indiv))
  );

  // 5. Accumulate proceedings then persist as a single saveAllAndFlush (Option C)
  List<ProceedingEntity> proceedingBatch = new ArrayList<>();
  for (Proceeding p : content.getProceedings()):
      proceedingBatch.add(DataGenerator.createDefault(
          ProceedingsEntityGenerator.class,
          b -> b
              .applicationId(app.getId())
              .applyProceedingId(p.getId())
              .description(p.getDescription())
              .isLead(Boolean.TRUE.equals(p.getLeadProceeding()))
              .createdBy("mass-generator")
              .updatedBy("mass-generator")
              .proceedingContent(objectMapper.convertValue(p, Map.class))
      ));
  persistedDataGenerator.persist(ProceedingsEntityGenerator.class, proceedingBatch);

  // 6. Flush + clear Hibernate session every BATCH_SIZE applications (Option B)
  if ((i + 1) % BATCH_SIZE == 0):
      persistedDataGenerator.flushAndClear();
      System.out.printf("Persisted %d / %d applications%n", i + 1, count);

// flush any remaining partial batch
persistedDataGenerator.flushAndClear();
```

---

### Change 5 — `application.yml` (mass-generator): enable JDBC batching

Add Hibernate batch properties and the `reWriteBatchedInserts` JDBC flag (Option A):

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/laa_data_access_api?reWriteBatchedInserts=true
  jpa:
    properties:
      hibernate:
        jdbc:
          batch_size: 500
          batch_versioned_data: true
        order_inserts: true
        order_updates: true
```

---

### Change 6 — Flyway migration: switch `ApplicationEntity` and `ProceedingEntity` to sequences (optional but recommended)

`ApplicationEntity` and `ProceedingEntity` both use `GenerationType.IDENTITY`. Hibernate cannot batch
`IDENTITY` inserts because it needs the generated key back immediately. Switching to a `SEQUENCE` with
`allocationSize = 500` (matching `batch_size`) lets Hibernate pre-allocate IDs and batch all inserts.

New migration file `V{next}__add_id_sequences.sql`:

```sql
CREATE SEQUENCE applications_id_seq START WITH 1 INCREMENT BY 500;
CREATE SEQUENCE proceedings_id_seq  START WITH 1 INCREMENT BY 500;
```

Update the `@Id` / `@GeneratedValue` annotations on `ApplicationEntity` and `ProceedingEntity`:

```java
// Before:
@GeneratedValue(strategy = GenerationType.IDENTITY)

// After:
@SequenceGenerator(name = "applications_seq", sequenceName = "applications_id_seq", allocationSize = 500)
@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "applications_seq")
```

> This change affects the shared entity classes in `data-access-service`. The Flyway migration must be
> added to `data-access-service/src/main/resources/db/migration` so it runs in all environments.
> The `IDENTITY` columns remain in the table — only the default-value source changes. Existing rows and
> constraints are unaffected.

---

## Files to change

| File | Change |
|------|--------|
| `data-access-mass-generator/.../generator/application/FullJsonGenerator.java` | Fix proceedings list: first proceeding `leadProceeding(true)`, others `leadProceeding(false)` |
| `data-access-mass-generator/.../generator/PersistedDataGenerator.java` | Add `flushAndClear()` and `createAndPersistMultipleRandom()` |
| `data-access-mass-generator/.../MassDataGeneratorRunner.java` | Inject `ObjectMapper`; use random pool generation; chunked application + proceeding loop |
| `data-access-mass-generator/src/main/resources/application.yml` | Add JDBC batch properties and `reWriteBatchedInserts` |
| `data-access-service/src/main/java/.../entity/ApplicationEntity.java` *(optional)* | Switch to `SEQUENCE` generator |
| `data-access-service/src/main/java/.../entity/ProceedingEntity.java` *(optional)* | Switch to `SEQUENCE` generator |
| `data-access-service/src/main/resources/db/migration/V{next}__add_id_sequences.sql` *(optional)* | Create sequences |

## Files unchanged

All existing `Full*Generator` classes, model classes, `ApplicationEntityGenerator`,
`ProceedingsEntityGenerator`, `CaseworkerGenerator`, `IndividualEntityGenerator`,
all repositories — no modifications needed.

