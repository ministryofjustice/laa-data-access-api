# Applied Changes: Aggregate Root Migration

**Date**: April 2026  
**Branch**: Applied to working branch  
**Tests after**:  288 unit tests passing, 273 integration tests passing, full build green

---

## Overview

This document records every code change applied to implement the aggregate root migration described in this folder. The migration refactors `ApplicationEntity` into a proper DDD aggregate root that owns its child entities (`DecisionEntity`, `ProceedingEntity`, `MeritsDecisionEntity`) via JPA cascade rather than through separate service-layer saves.

---

## Phase 1 — Fix Circular Reference Issues

### Problem
`IndividualEntity.applications` and `ApplicationEntity.linkedApplications` caused infinite recursion during Jackson serialisation. `ApplicationSummaryEntity.linkedApplications` held `Set<ApplicationEntity>` creating a cross-type reference.

### Changes

**`IndividualEntity`**
- Added `@JsonIgnore` to the `applications` field.
- Field retained (not removed) because `IndividualSpecification.filterApplicationId()` uses `root.join("applications")`.

**`ApplicationEntity`**
- Added `@JsonIgnore` to `linkedApplications` field.
- Added `@JsonIgnore` to `isLead()` method.

**`ApplicationSummaryEntity`**
- Changed `linkedApplications` from `Set<ApplicationEntity>` to `Set<ApplicationSummaryEntity>`, eliminating the cross-entity circular reference.
- Updated `ApplicationSummaryMapperTest` and `GetApplicationsTest` accordingly (switched generator from `ApplicationEntityGenerator` to `ApplicationSummaryGenerator`).

---

## Phase 2 & 3 — Cascade and Lazy Loading

### Problem
Child entities were persisted independently through separate repositories, preventing aggregate-level cascade. Relations used `FetchType.EAGER` by default.

### Changes

**`ApplicationEntity`**
- Added `@DynamicUpdate` at class level to limit UPDATE statements to only changed columns.
- `decision`: changed to `@OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)`.
- `proceedings`: changed to `@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)` with `@Builder.Default Set<ProceedingEntity> proceedings = new HashSet<>()`.
- `certificates`: initially set to `@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)` — later adjusted (see Phase 7 corrections).

**`DecisionEntity`**
- `meritsDecisions`: changed cascade to `CascadeType.ALL` (later refined — see Phase 7 corrections).

---

## Phase 4 — ProceedingEntity Relationship Inversion

### Problem
`ProceedingEntity` held a bare `@Column UUID applicationId`. This made it impossible to express the `@OneToMany(mappedBy)` aggregate relationship from `ApplicationEntity`.

### Changes

**`ProceedingEntity`**
- Removed `@Column UUID applicationId`.
- Added `@ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "application_id", nullable = false) private ApplicationEntity application`.

**`ApplicationEntity`**
- Changed `proceedings` to `@OneToMany(mappedBy = "application", ...)`.

**`ProceedingRepository`**
- Renamed `findAllByApplicationId(UUID)` → `findByApplication_Id(UUID)` (Spring Data derived query traversal).

**`ProceedingMapper`**
- Changed `toProceedingEntity(Proceeding, UUID applicationId)` signature to `toProceedingEntity(Proceeding, ApplicationEntity application)`.
- Updated body to call `setApplication(application)` instead of `setApplicationId(applicationId)`.

**`ApplicationService`**
- Updated all calls to `proceedingMapper.toProceedingEntity(...)` to pass the entity.
- Updated calls from `findAllByApplicationId` to `findByApplication_Id`.

**Test fixes**
- `ProceedingMapperTest`: updated to pass `ApplicationEntity` and assert `getApplication().getId()`.
- `MakeDecisionForApplicationTest`: replaced `builder.applicationId(applicationId)` with `builder.application(ApplicationEntity.builder().id(applicationId).build())` across ~9 sites using perl regex.
- `CreateApplicationTest.verifyThatProceedingsSaved`: changed `getApplicationId()` → `getApplication().getId()`.
- `ApplicationSummaryMapperTest`, `GetApplicationsTest`, `GetApplicationTest` (unit), `ApplicationMakeDecisionTest` (integration), `ApplicationRepositoryTest` (integration), `GetApplicationTest` (integration): updated builder patterns.

---

## Phase 5 — Entity Graph for makeDecision Read

### Problem
`makeDecision` fetched the application without eagerly loading `decision` and `proceedings`, causing `LazyInitializationException` outside the session.

### Changes

**`ApplicationRepository`**
- Added `findByIdWithDecisionGraph`:

```java
@EntityGraph(attributePaths = {"decision", "decision.meritsDecisions", "proceedings"})
@Query("SELECT a FROM ApplicationEntity a WHERE a.id = :id")
Optional<ApplicationEntity> findByIdWithDecisionGraph(@Param("id") UUID id);
```

---

## Phase 6 — @Transactional Consistency

### Problem
`ApplicationService` used `jakarta.transaction.Transactional` which does not support `readOnly = true`. Read methods lacked transaction annotation.

### Changes

**`ApplicationService`**
- Replaced `import jakarta.transaction.Transactional` with `import org.springframework.transaction.annotation.Transactional`.
- Added `@Transactional(readOnly = true)` to `getApplication()`.
- Added `@Transactional` to `updateApplication()`, `unassignCaseworker()`, `makeDecision()`, `createApplication()`, `assignCaseworker()`, `createApplicationNote()`.

---

## Phase 7 — Aggregate Write Path

### Problem
`makeDecision` performed two saves (one early for `isAutoGranted`, one late for the decision). `createApplication` called `proceedingsService.saveProceedings()` separately. `MeritsDecisionEntity` used `@OneToOne` to its proceeding and had no direct UUID reference, causing issues when matching existing merits decisions.

### Changes

**`MeritsDecisionEntity`**
- Added `@Column(name = "proceeding_id") private UUID proceedingId` as the source-of-truth for the FK.
- Changed proceeding relationship from `@OneToOne @JoinColumn` to `@ManyToOne(fetch = LAZY) @JoinColumn(name = "proceeding_id", insertable = false, updatable = false)`.
- Added custom `setProceeding()` that syncs `proceedingId` when the proceeding has a non-null id.
- Added `@PrePersist` and `@PreUpdate` hooks to sync `proceedingId` from the proceeding object if null.

**`DecisionEntity`**
- Added `addMeritsDecision(MeritsDecisionEntity)` helper that initialises the set if needed and adds to it.

**`ApplicationService.makeDecision()`**
- Replaced `checkIfApplicationExists` + two saves with a single `findByIdWithDecisionGraph` + single `applicationRepository.save(application)` at the end.
- Added private `updateOrCreateMeritsDecision()` that matches on `proceedingId` (with fallback to `getProceeding().getId()` for Lombok builder-created test entities that bypass `setProceeding()`).
- Removed calls to `decisionRepository.save()` and `meritsDecisionRepository.save()`.

**`ApplicationService.createApplication()`**
- Proceedings are now added to `entity.getProceedings()` before the single `applicationRepository.save(entity)` call.
- Removed call to `proceedingsService.saveProceedings()`.

**Removed dependencies from `ApplicationService`**
- `DecisionRepository`, `MeritsDecisionRepository`, `ProceedingsService` removed from injection and all usages.

**`ProceedingsService`**
- File deleted entirely.

---

## Phase 8 — Test Updates

### `MakeDecisionForApplicationTest`
- All `when(applicationRepository.findById(...))` stubs changed to `findByIdWithDecisionGraph(...)`.
- All `verify(applicationRepository, times(2)).save(...)` changed to `times(1)`.
- `verifyDecisionSavedCorrectly` now captures via `applicationRepository.save()` captor (no longer uses `decisionRepository.save()` captor).
- Removed `verify(meritsDecisionRepository, never()).save(any())` assertion.
- Removed `NO_PROCEEDING` condition that previously skipped the `never().save()` assertion.

### `CreateApplicationTest`
- `verifyThatProceedingsSaved`: replaced `verify(proceedingRepository).saveAll()` captor with `verify(applicationRepository, atLeastOnce()).save()` captor; extracts `getFirst().getProceedings()` from the captured entity.
- Changed per-proceeding assertion from index-based loop to unordered `anySatisfy` to handle `Set` iteration order.
- Changed `assertThat(actualProceedingEntity.getApplication().getId()).isEqualTo(expectedId)` to `assertThat(actual.getApplication()).isNotNull()` (entity id is null in unit test context — mock doesn't assign ids).
- Added `import static org.mockito.Mockito.atLeastOnce`.

### `BaseServiceTest`
- Removed `@MockitoBean protected DecisionRepository decisionRepository`.
- Removed `@MockitoBean protected MeritsDecisionRepository meritsDecisionRepository`.
- Both removed because `ApplicationService` no longer injects them.

---

## Integration Test Cascade Corrections

During integration testing, several Hibernate / DB cascade conflicts were found and fixed.

### Problem 1 — `DecisionEntity.meritsDecisions` cascade conflict
`ApplicationEntity` → `proceedings ON DELETE CASCADE` → `merits_decisions ON DELETE CASCADE` (DB).  
Hibernate's `CascadeType.ALL` (including REMOVE) on `DecisionEntity.meritsDecisions` tried to `DELETE FROM merits_decisions WHERE id=?` after the DB had already deleted them, producing `StaleStateException`.

**Fix**: Changed `DecisionEntity.meritsDecisions` cascade from `CascadeType.ALL` to `{CascadeType.PERSIST, CascadeType.MERGE}` (no REMOVE).

### Problem 2 — `ApplicationEntity.certificates` cascade conflict
`ApplicationEntity.certificates` had `@OneToMany(cascade = ALL, orphanRemoval = true)`.  
On application delete, Hibernate tried to null the FK (`UPDATE certificates SET application_id = null`) before deletion, which violated the `NOT NULL` constraint on `application_id`. The DB `ON DELETE CASCADE` already handles certificate cleanup.

**Fix**: Removed the `certificates` field from `ApplicationEntity` entirely. The field was not used in any production code; `CertificateService` uses `certificateRepository` directly.

### Problem 3 — `PersistedDataGenerator.deleteTrackedData()` cascade conflict  
`decRepo.findById(id).ifPresent(decRepo::delete)` caused Hibernate to cascade-remove `meritsDecisions`, conflicting with rows already deleted by the DB cascade chain from the preceding `appRepo.delete()`.

**Fix**: Replaced Hibernate repository delete with a direct JDBC statement:
```java
trackedDecisionIds.forEach(id ->
    jdbcTemplate.update("DELETE FROM decisions WHERE id = ?", id));
```

### Problem 4 — `ApplicationRepositoryTest` circular comparison failure
`assertThat(expected).usingRecursiveComparison()` traversed `proceedings.application` back to the application entity, causing objects to not match due to proxy state.

**Fix**: Added `"proceedings.application"` and `"decision.meritsDecisions.proceeding.application"` to the `ignoringFields` list.

---

## Phase Extra — V23 Migration: Replace ManyToMany Join Table with FK

### Why
The `linked_merits_decisions` join table was an artefact of the original schema. A `MeritsDecisionEntity` always belongs to exactly one `DecisionEntity`, making it a OneToMany relationship. The join table structure prevented clean cascade management and complicated queries.

This is documented as the intended target state in `README.md` ("Database Schema Migration" section).

### Migration (`V23__convert_merits_decisions_to_one_to_many.sql`)

```sql
ALTER TABLE merits_decisions ADD COLUMN decisions_id UUID;

UPDATE merits_decisions md
  SET decisions_id = lmd.decisions_id
  FROM linked_merits_decisions lmd
  WHERE md.id = lmd.merits_decisions_id;

ALTER TABLE merits_decisions ALTER COLUMN decisions_id SET NOT NULL;

ALTER TABLE merits_decisions
  ADD CONSTRAINT fk_merits_decisions_decisions_id
    FOREIGN KEY (decisions_id) REFERENCES decisions(id) ON DELETE CASCADE;

DROP TABLE linked_merits_decisions;
```

### `MeritsDecisionEntity`
- Added `@ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "decisions_id", nullable = false) private DecisionEntity decisionEntity` as the FK owner.
- Lombok's `@Setter` generates `setDecisionEntity()` normally.

### `DecisionEntity`
- Changed `@ManyToMany ... @JoinTable(name = "linked_merits_decisions")` to `@OneToMany(mappedBy = "decisionEntity", cascade = {PERSIST, MERGE}, fetch = LAZY)`.
- Added `@lombok.Setter(lombok.AccessLevel.NONE)` on the field to suppress Lombok's auto-generated setter.
- Added custom `setMeritsDecisions(Set)` that sets the `decisionEntity` back-reference on all elements.
- Added `@PrePersist @PreUpdate syncMeritsDecisionBackRefs()` — syncs `decisionEntity` on all merits before insert/update, compensating for Lombok builder bypassing the custom setter.
- Updated `addMeritsDecision()` to call `merit.setDecisionEntity(this)` before adding.
- `orphanRemoval = false` — the DB `ON DELETE CASCADE` from `decisions → merits_decisions` handles cleanup; adding `orphanRemoval = true` would conflict with the `proceedings → merits_decisions ON DELETE CASCADE` chain on application delete.

### `ApplicationDomainTables`
- Removed `"linked_merits_decisions"` from the `TABLES` list used by `DatabaseCleanlinessAssertion`.

### `ApplicationMakeDecisionTest` (integration)
- Refactored the "existing decision with merits" test setup to create `MeritsDecisionEntity` inline within the `DecisionEntity` at persist time, rather than persisting merits standalone via `MeritsDecisionRepository`. This is required because `decisions_id` is now `NOT NULL` — a merit cannot exist without a parent decision.
- Removed `createAndPersistWithPersistedMeritsDecisions` call in that test in favour of `createAndPersist(DecisionEntityGenerator.class, ...)`.
- Added `DecisionEntityGenerator` import.

---

## Summary of Files Changed

| File | Change |
|------|--------|
| `entity/IndividualEntity.java` | `@JsonIgnore` on `applications` |
| `entity/ApplicationSummaryEntity.java` | `linkedApplications` type change |
| `entity/ApplicationEntity.java` | `@DynamicUpdate`; cascade on decision/proceedings; `@JsonIgnore`; removed `certificates` field |
| `entity/ProceedingEntity.java` | `applicationId` → `@ManyToOne application` |
| `entity/DecisionEntity.java` | `@ManyToMany` → `@OneToMany(mappedBy)`; cascade change; `addMeritsDecision`/`setMeritsDecisions`; `@PrePersist/@PreUpdate` |
| `entity/MeritsDecisionEntity.java` | Added `proceedingId` UUID; `@OneToOne` → `@ManyToOne`; added `@ManyToOne decisionEntity`; custom `setProceeding`; `@PrePersist/@PreUpdate` |
| `repository/ApplicationRepository.java` | Added `findByIdWithDecisionGraph` |
| `repository/ProceedingRepository.java` | Renamed `findAllByApplicationId` → `findByApplication_Id` |
| `mapper/ProceedingMapper.java` | Signature change to accept `ApplicationEntity` |
| `service/ApplicationService.java` | Single-save paths; removed `DecisionRepository`, `MeritsDecisionRepository`, `ProceedingsService`; Spring `@Transactional` |
| `service/ProceedingsService.java` | **Deleted** |
| `db/migration/V23__convert_merits_decisions_to_one_to_many.sql` | **New** — migrates join table to FK column |
| `test/.../BaseServiceTest.java` | Removed unused `@MockitoBean`s |
| `test/.../MakeDecisionForApplicationTest.java` | `findById` → `findByIdWithDecisionGraph`; save times; decision captor |
| `test/.../CreateApplicationTest.java` | `verifyThatProceedingsSaved` rewritten; `atLeastOnce` import |
| `test/.../ProceedingMapperTest.java` | Signature + assertion updates |
| `test/.../ApplicationSummaryMapperTest.java` | Generator type update |
| `test/.../GetApplicationsTest.java` | Generator type update |
| `test/.../GetApplicationTest.java` (unit) | `findByApplication_Id` rename |
| `integrationTest/.../ApplicationMakeDecisionTest.java` | Builder pattern updates; refactored "existing decision" setup |
| `integrationTest/.../ApplicationRepositoryTest.java` | `ignoringFields` for back-refs; `proceedings.add` before save |
| `integrationTest/.../GetApplicationTest.java` | `application` builder pattern |
| `integrationTest/.../generator/PersistedDataGenerator.java` | JDBC delete for decisions; JDBC delete for applications |
| `integrationTest/.../harness/ApplicationDomainTables.java` | Removed `linked_merits_decisions` |

---

## Phase Extra — Certificate into Aggregate Boundary

### Why
`CertificateEntity` held a bare `@Column UUID applicationId` and was managed exclusively via `CertificateRepository` in `ApplicationService`. Moving it inside the aggregate boundary allows the application to own its certificate the same way it owns its decision and proceedings: the root entity is the single point of write, and cascade handles persistence.

### Migration (`V24__remove_cascade_delete_from_certificates.sql`)

The DB had `ON DELETE CASCADE` on `certificates.application_id → applications`. That constraint prevented `orphanRemoval = true` on the JPA side: when an application is deleted, the DB cascade fires before Hibernate can issue its own `DELETE`, causing `StaleStateException`. The migration drops the cascade from the DB so JPA can own deletion entirely.

```sql
ALTER TABLE certificates DROP CONSTRAINT fk_certificates_applications_id;

ALTER TABLE certificates
    ADD CONSTRAINT fk_certificates_applications_id
        FOREIGN KEY (application_id) REFERENCES applications (id);
```

### `CertificateEntity`
- Removed `@Column UUID applicationId`.
- Added `@OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "application_id", nullable = false, unique = true) private ApplicationEntity application` as the FK owner.

### `ApplicationEntity`
- Added `@OneToOne(mappedBy = "application", cascade = {PERSIST, MERGE, REMOVE}, orphanRemoval = true, fetch = LAZY) private CertificateEntity certificate`.
- With `orphanRemoval = true`, setting `certificate = null` and saving the application causes Hibernate to delete the orphaned row cleanly.

### `ApplicationRepository`
- Entity graph updated to include `"certificate"`:
  ```java
  @EntityGraph(attributePaths = {"decision", "decision.meritsDecisions", "proceedings", "certificate"})
  ```

### `CertificateRepository`
- Renamed all three derived-query methods to use Spring Data FK traversal syntax:
  - `findByApplicationId` → `findByApplication_Id`
  - `existsByApplicationId` → `existsByApplication_Id`
  - `deleteByApplicationId` → `deleteByApplication_Id`

### `CertificateService`
- Updated `findByApplicationId` call → `findByApplication_Id`.

### `ApplicationService.makeDecision()`
- **GRANTED path**: reads `application.getCertificate()` (loaded via entity graph). Creates a new `CertificateEntity` if null. Sets `certificate.setApplication(application)` and `application.setCertificate(certificate)`. The trailing `applicationRepository.save(application)` cascades PERSIST/MERGE to the certificate.
- **REFUSED path**: `application.setCertificate(null)`. `orphanRemoval = true` causes Hibernate to delete the row on flush.
- `CertificateRepository` dependency removed from `ApplicationService` entirely — no longer needed.

### `CertificateEntityGenerator`
- Removed `.applicationId(UUID.randomUUID())` from `createDefault()`.

### `PersistedDataGenerator`
- Application teardown switched from JPA `appRepo.delete()` to JDBC:
  ```java
  trackedApplicationIds.forEach(id ->
      jdbcTemplate.update("DELETE FROM applications WHERE id = ?", id));
  ```
  This avoids Hibernate attempting to cascade-MERGE the `certificate.application` back-reference (which is a detached proxy after the session closes) when loading the application entity for deletion.

### Test fixes

**Unit tests**
- `GetCertificateTest`: `builder.applicationId(...)` → `.application(ApplicationEntity.builder().id(applicationId).build())`; `findByApplicationId` → `findByApplication_Id` throughout.
- `MakeDecisionForApplicationTest`:
  - REFUSED test: removed `when(certificateRepository.existsByApplicationId(...))` stub; pre-set `applicationEntity.setCertificate(cert)` instead. Removed `verify(certificateRepository, times(...)).delete(...)`.
  - GRANTED (new cert) test: removed `when(certificateRepository.findByApplicationId(...))` stub and `verify(certificateRepository.save(...))`. Now captures via `ApplicationEntity` captor on `applicationRepository.save()` and asserts `appCaptor.getValue().getCertificate()`.
  - GRANTED (existing cert) test: same pattern; pre-sets `applicationEntity.setCertificate(existingCertificate)` and asserts updated cert via app captor.

**Integration tests**
- `CertificateRepositoryTest`: `builder.applicationId(applicationEntity.getId())` → `builder.application(applicationEntity)`. Added `"application"` to `ignoringFields` in `assertCertificateEqual`.
- `GetCertificateTest` (integration): same builder fix.
- `ApplicationMakeDecisionTest`: `existsByApplicationId` → `existsByApplication_Id`; replaced `certificate.getApplicationId()` assertions with `existsByApplication_Id(applicationId)`.

### Summary of files changed

| File | Change |
|------|--------|
| `db/migration/V24__remove_cascade_delete_from_certificates.sql` | **New** — removes `ON DELETE CASCADE` from certificates FK |
| `entity/CertificateEntity.java` | `UUID applicationId` → `@OneToOne ApplicationEntity application` |
| `entity/ApplicationEntity.java` | Added `@OneToOne(orphanRemoval=true) CertificateEntity certificate` |
| `repository/CertificateRepository.java` | Methods renamed to `findByApplication_Id` etc. |
| `repository/ApplicationRepository.java` | Entity graph includes `"certificate"` |
| `service/CertificateService.java` | `findByApplicationId` → `findByApplication_Id` |
| `service/ApplicationService.java` | Certificate written via aggregate; `CertificateRepository` dependency removed |
| `testUtilities/.../CertificateEntityGenerator.java` | Removed `applicationId` from builder |
| `test/.../GetCertificateTest.java` | Builder + method rename fixes |
| `test/.../MakeDecisionForApplicationTest.java` | Stubs/verifies rewritten to use app captor |
| `integrationTest/.../CertificateRepositoryTest.java` | Builder fix; `ignoringFields` |
| `integrationTest/.../GetCertificateTest.java` | Builder fix |
| `integrationTest/.../ApplicationMakeDecisionTest.java` | Method renames; assertion style updates |
| `integrationTest/.../generator/PersistedDataGenerator.java` | Application teardown via JDBC |
