# Agentic Prompt: Migrate `ApplicationEntity` to Aggregate Root

## Context

You are working in a Spring Boot / JPA project: `laa-data-access-api`.
All entity files are in:
`data-access-service/src/main/java/uk/gov/justice/laa/dstew/access/entity/`

The primary service is:
`data-access-service/src/main/java/uk/gov/justice/laa/dstew/access/service/ApplicationService.java`

The goal is to migrate `ApplicationEntity` to be a proper DDD aggregate root, fix circular reference risks, and set up fetch strategies correctly as an interim measure until a separate read model is implemented.

---

## Constraints

- Do NOT make any changes until each phase is confirmed by the user.
- After proposing changes for each phase, stop and wait for confirmation.
- After making changes, validate with `./gradlew build && ./gradlew test && ./gradlew integrationTest` before proceeding.
- Do NOT change read paths (`getApplication`, `ApplicationSummaryService`) — these still use `ApplicationEntity` and `ApplicationSummaryEntity` directly until a separate read model is in place.
- Do NOT add repositories or inject them into entities.
- Do NOT modify database migrations or schema files.

---

## Pre-Execution Checklist

**Before starting Phase 1, run these searches to understand the codebase scope:**

1. **MapStruct mappers** — Search for all `@Mapping` annotations on `ProceedingEntity` mappers:
   ```bash
   grep -r "ProceedingMapper\|@Mapping.*proceeding\|@Mapping.*Proceeding" data-access-service/src
   ```
   These will need updates in Phase 4 when `applicationId` → `application`.

2. **ProceedingEntity usages** — Find all direct instantiations and builder calls:
   ```bash
   grep -r "\.applicationId\(" data-access-service/src
   grep -r "ProceedingEntity\." data-access-service/src | grep -E "getApplicationId|setApplicationId"
   ```

3. **ProceedingEntity assertions in tests** — Find all test assertions that reference applicationId:
   ```bash
   grep -r "getApplicationId()\|applicationId(" data-access-service/src/test data-access-service/src/integrationTest
   ```

4. **Repository method calls** — Find all usages of `proceedingRepository.findAllByApplicationId()`:
   ```bash
   grep -r "findAllByApplicationId\|findByApplicationId" data-access-service/src
   ```

5. **ProceedingsService usages** — Verify if deleting it in Phase 7 is safe:
   ```bash
   grep -r "ProceedingsService\|proceedingsService" data-access-service/src --include="*.java"
   grep -r "proceedingsService" data-access-service/src/test data-access-service/src/integrationTest
   ```

6. **Test file locations** — Verify test paths exist:
   ```bash
   find data-access-service -name "BaseServiceTest.java" -o -name "CreateApplicationTest.java" -o -name "ApplicationMakeDecisionTest.java" -o -name "ProceedingRepositoryTest.java" -o -name "PersistedDataGenerator.java"
   ```

---

## Phase Dependency Map

```
Phase 1 (Circular Refs)
  ├─ Phase 2 (Cascade Rules)
  │    ├─ Phase 3 (DynamicUpdate) — Can run in parallel with Phase 2
  │    └─ Phase 4 (ProceedingEntity) — **BLOCKS** Phases 5-7
  │         └─ Phase 5 (Fetch Strategies) — Depends on Phase 4
  │              └─ Phase 6 (Transactional) — Independent, can run after Phase 5
  └─ Phase 7 (Save Through Root) — Depends on Phases 4-6
       └─ Phase 8 (Update Tests) — Depends on Phases 4-7
            └─ Phase 9 (Validation)
```

**Note:** Phases 2 and 3 can potentially run in parallel since they modify different aspects of ApplicationEntity. However, they should be validated separately. Phase 4 is the critical bottleneck — all downstream phases depend on it working.

---

## Phase 1 — Fix Circular References

Read the following files before proposing any changes:
- `ApplicationEntity.java`
- `IndividualEntity.java`
- `ApplicationSummaryEntity.java`

### Required changes

**`IndividualEntity.java`**
- Remove the `@ManyToMany(mappedBy = "individuals") Set<ApplicationEntity> applications` field.
- This is the inverse side of the relationship owned by `ApplicationEntity` and creates a bidirectional loop:
  `Application → Individual → Application → ...`

**`ApplicationEntity.java`**
- Add `@JsonIgnore` to the `linkedApplications` field and the `isLead()` transient method.
- This prevents the self-referential loop: `Application → LinkedApplication → its linkedApplications → ...`
- Import: `com.fasterxml.jackson.annotation.JsonIgnore`

**`ApplicationSummaryEntity.java`**
- Change `Set<ApplicationEntity> linkedApplications` to `Set<ApplicationSummaryEntity> linkedApplications`.
- The read model must not reference the write model entity.
- Update the `@JoinTable` annotation — it can remain identical (same `linked_applications` table, same columns).

---

## Phase 2 — Add Cascade to `ApplicationEntity` for Owned Children

**Before proposing changes:** Search for any existing cascade configurations to understand current state:
```bash
grep -n "cascade.*=" data-access-service/src/main/java/uk/gov/justice/laa/dstew/access/entity/ApplicationEntity.java
grep -n "orphanRemoval" data-access-service/src/main/java/uk/gov/justice/laa/dstew/access/entity/ApplicationEntity.java
```

Read the following files before proposing any changes:
- `ApplicationEntity.java`
- `DecisionEntity.java`
- `ProceedingEntity.java`
- `CertificateEntity.java`

### Required changes

**`ApplicationEntity.java`**

1. Add `@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)` for `ProceedingEntity`:
   ```java
   @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
   @JoinColumn(name = "application_id")
   private Set<ProceedingEntity> proceedings = new HashSet<>();
   ```

2. Change the existing `@OneToOne decision` to cascade:
   ```java
   @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
   @JoinColumn(name = "decision_id", referencedColumnName = "id")
   private DecisionEntity decision;
   ```

3. Add `@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)` for `CertificateEntity`:
   ```java
   @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
   @JoinColumn(name = "application_id")
   private Set<CertificateEntity> certificates = new HashSet<>();
   ```

**`DecisionEntity.java`**
- Change `@ManyToMany(cascade = CascadeType.PERSIST)` to `@ManyToMany(cascade = CascadeType.ALL)` on `meritsDecisions`.
- This allows merits decisions to cascade save/delete through the decision, which in turn cascades from the root.

### Do NOT cascade
- `individuals` (`@ManyToMany`) — shared entity, keep `CascadeType.PERSIST` only, no `orphanRemoval`
- `caseworker` (`@OneToOne`) — shared entity, no cascade

---

## Phase 3 — Add `@DynamicUpdate`

Read `ApplicationEntity.java` before proposing any changes.

### Required changes

**`ApplicationEntity.java`**
- Add `@DynamicUpdate` at the class level alongside the existing `@Entity` annotation.
- Import: `org.hibernate.annotations.DynamicUpdate`

This prevents Hibernate from issuing a full-row UPDATE whenever any field changes. Without it, every `applicationRepository.save()` updates all columns including the large `applicationContent` JSONB blob, even when only a single field like `status` changed. This is already causing unnecessary UPDATE statements on every write operation.

---

## Phase 4 — Fix `ProceedingEntity` Relationship

**CRITICAL:** Before proposing changes, run these searches to identify all impacted code. This phase has wide-ranging impacts:

```bash
# Find all calls to proceedingRepository.findAllByApplicationId()
grep -r "findAllByApplicationId\|findByApplicationId" data-access-service/src --include="*.java"

# Find all ProceedingMapper @Mapping annotations that reference applicationId
grep -r "applicationId" data-access-service/src --include="*Mapper.java" -A 2 -B 2

# Find all places that instantiate ProceedingEntity with applicationId
grep -r "\.applicationId(" data-access-service/src --include="*.java"
grep -r "getApplicationId()" data-access-service/src --include="*.java"

# Check for any Spring Data query methods that need updating
grep -r "@Query.*ProceedingEntity\|findAll.*applicationId" data-access-service/src --include="*Repository.java"
```

**Expected findings:**
- ProceedingRepository likely has `findAllByApplicationId(UUID)` method that will need updating
- ApplicationService and/or ProceedingsService likely calls this method
- MapStruct ProceedingMapper likely has `@Mapping(source = "applicationId", target = "applicationId")` lines
- Test files likely instantiate ProceedingEntity using `.applicationId(...)` builder syntax

Read the following files before proposing any changes:
- `ProceedingEntity.java`
- `ApplicationEntity.java`
- `ProceedingRepository.java` (search results may reveal this)
- `ProceedingMapper.java` (if it exists)
- `ApplicationService.java` (for impact analysis)

### Required changes

**`ProceedingEntity.java`**
- Replace the raw UUID `applicationId` column with a proper `@ManyToOne` back-reference to `ApplicationEntity`:

```java
// Remove:
@Column(name = "application_id", nullable = false)
private UUID applicationId;

// Add:
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "application_id", nullable = false)
private ApplicationEntity application;
```

- Add `FetchType` import: `jakarta.persistence.FetchType`
- Add `ManyToOne` import: `jakarta.persistence.ManyToOne`

**`ApplicationEntity.java`**
- Update the `proceedings` relationship added in Phase 2 to use `mappedBy` now that `ProceedingEntity` has the back-reference:

```java
// Replace the @JoinColumn form from Phase 2:
@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
@JoinColumn(name = "application_id")
private Set<ProceedingEntity> proceedings = new HashSet<>();

// With mappedBy form:
@OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
private Set<ProceedingEntity> proceedings = new HashSet<>();
```

### Impact on `ApplicationService`
- `checkIfAllProceedingsExistForApplication()` currently filters by `p.getApplicationId().equals(applicationId)`. After this change it must use `p.getApplication().getId().equals(applicationId)` instead.
- `ProceedingRepository.findAllByApplicationId(UUID)` must be updated to `findAllByApplicationId(UUID)` using the new relationship path, or renamed to `findAllByApplication_Id(UUID)` to match Spring Data naming conventions.
- Any code that calls `proceedingMapper.toProceedingEntity(proceeding, id)` and sets `applicationId` must be updated to set the `application` reference instead.

---

## Phase 5 — Fix Fetch Strategies and Add `@EntityGraph`

Read the following files before proposing any changes:
- `ApplicationEntity.java`
- `ApplicationRepository.java`

### Required changes

**`ApplicationEntity.java`**

1. Add `FetchType` import: `jakarta.persistence.FetchType`

2. Change `caseworker` to `LAZY`:
   ```java
   @OneToOne(fetch = FetchType.LAZY)
   @JoinColumn(name = "caseworker_id", referencedColumnName = "id")
   private CaseworkerEntity caseworker;
   ```

3. Change `decision` to `LAZY`:
   ```java
   @OneToOne(fetch = FetchType.LAZY)
   @JoinColumn(name = "decision_id", referencedColumnName = "id")
   private DecisionEntity decision;
   ```

Note: `individuals` and `linkedApplications` are already `LAZY` by JPA default (`@ManyToMany`, `@OneToMany`) — no change needed.

**`ApplicationRepository.java`**

Add a named `@EntityGraph` query for the `makeDecision` write path, which needs the full decision graph loaded:

```java
@EntityGraph(attributePaths = {
    "decision",
    "decision.meritsDecisions",
    "decision.meritsDecisions.proceeding"
})
@Query("SELECT a FROM ApplicationEntity a WHERE a.id = :id")
Optional<ApplicationEntity> findByIdWithDecisionGraph(@Param("id") UUID id);
```

- Import: `org.springframework.data.jpa.repository.EntityGraph`
- Import: `org.springframework.data.jpa.repository.Query`
- Import: `org.springframework.data.repository.query.Param`

Then in `ApplicationService.makeDecision()`, replace `checkIfApplicationExists(applicationId)` with `applicationRepository.findByIdWithDecisionGraph(applicationId)` so the decision graph is loaded eagerly only for that operation, while all other operations continue to benefit from lazy loading.

---

## Phase 6 — Fix `@Transactional` Consistency in `ApplicationService`

Read `ApplicationService.java` before proposing any changes.

### Required changes

**`ApplicationService.java`**

Currently `@Transactional` is only declared on `makeDecision()`, `assignCaseworker()`, and `createApplication()`. All other write methods are missing it and all read methods use the default read-write transaction.

1. Add `@Transactional` to all remaining write methods that are missing it:
   - `updateApplication()`
   - `unassignCaseworker()`

2. Add `@Transactional(readOnly = true)` to all read methods:
   - `getApplication()`

Note: use `org.springframework.transaction.annotation.Transactional`, not `jakarta.transaction.Transactional` — the Spring annotation integrates correctly with Spring's transaction management and supports `readOnly`.

---

## Phase 7 — Update `ApplicationService` to Save Through the Root

**Before proposing changes:** Identify all direct repository saves to understand the scope:

```bash
# Find all direct saves to child repositories in ApplicationService
grep -n "decisionRepository.save\|meritsDecisionRepository.save\|certificateRepository.save\|proceedingRepository.save" data-access-service/src/main/java/uk/gov/justice/laa/dstew/access/service/ApplicationService.java

# Check if ProceedingsService is used elsewhere and safe to delete
grep -r "ProceedingsService" data-access-service/src --include="*.java" | grep -v "test"

# Find all proceedingsService method calls
grep -r "proceedingsService\." data-access-service/src --include="*.java"

# Check for direct repository injections in ApplicationService
grep -n "private.*Repository" data-access-service/src/main/java/uk/gov/justice/laa/dstew/access/service/ApplicationService.java | grep -E "decision|meritsDecision|certificate|proceeding"
```

Read the following files before proposing any changes:
- `ApplicationService.java`
- `ProceedingsService.java`
- `ApplicationRepository.java`
- All test files that mock these services (will be identified by Phase-8 search)

### Required changes

**`ApplicationService.java`**

Remove direct saves to child repositories in write operations and replace with mutations on the root followed by a single `applicationRepository.save(application)`:

| Current | Replace with |
|---|---|
| `proceedingsService.saveProceedings(applicationContent, saved.getId())` in `createApplication` | Add proceedings to `saved.getProceedings()` before `applicationRepository.save(entity)` |
| `meritsDecisionRepository.save(meritDecisionEntity)` in `makeDecision` | Remove — cascades via `DecisionEntity` → `ApplicationEntity` |
| `decisionRepository.save(decision)` in `makeDecision` | Remove — cascades via `ApplicationEntity` |
| `certificateRepository.save(certificate)` in `makeDecision` | Add to `application.getCertificates()` before `applicationRepository.save(application)` |

Remove the following injected repositories from the constructor and fields (now unused for writes):
- `decisionRepository`
- `meritsDecisionRepository`
- `certificateRepository`

Note: `proceedingRepository` may still be needed for read queries (`findAllByApplicationId`, `findAllById`) — keep it but remove write usage.

**`ProceedingsService.java`**
- Remove `proceedingRepository.saveAll(proceedingEntities)` — the caller (`ApplicationService`) will instead add proceedings to the root entity before saving.
- If `ProceedingsService` becomes empty after this, delete the class and remove its injection from `ApplicationService`.

---

## Phase 8 — Update Tests

**Before proposing changes:** Locate test files dynamically (paths may vary):

```bash
# Find actual test file locations
find data-access-service -name "BaseServiceTest.java" -o -name "*CreateApplicationTest.java" -o -name "*ApplicationMakeDecisionTest.java" -o -name "*ProceedingRepositoryTest.java" -o -name "PersistedDataGenerator.java"

# Find all tests that reference ProceedingEntity.applicationId
grep -r "\.applicationId(" data-access-service/src/test data-access-service/src/integrationTest --include="*.java"

# Find all test mock setups for removed repositories
grep -r "@MockitoBean\|when.*decisionRepository\|when.*meritsDecisionRepository\|when.*certificateRepository" data-access-service/src/test --include="*.java"

# Find all proceedingRepository.saveAll assertions
grep -r "verify.*proceedingRepository.*saveAll" data-access-service/src/test --include="*.java"

# Find all certificateRepository.findAll assertions
grep -r "certificateRepository\.findAll\|verify.*certificateRepository" data-access-service/src/integrationTest --include="*.java"
```

**Test files to be updated** (exact locations will be revealed by above searches):
- `BaseServiceTest.java` or similar test base class
- `*CreateApplicationTest.java` or other application creation tests
- `PersistedDataGenerator.java` (typically in integrationTest utils)
- `*ApplicationMakeDecisionTest.java` or decision-related tests
- `*ProceedingRepositoryTest.java` if repository tests exist

### Unit tests — `BaseServiceTest.java`

Remove the following `@MockitoBean` declarations — these repositories are no longer injected into `ApplicationService` after Phase 4:
- `DecisionRepository decisionRepository`
- `MeritsDecisionRepository meritsDecisionRepository`
- `CertificateRepository certificateRepository`

Remove the corresponding imports.

### Unit tests — `CreateApplicationTest.java`

The `verifyThatProceedingsSaved` helper currently asserts `verify(proceedingRepository).saveAll(...)`. After Phase 4, proceedings are added to the root entity and saved via `applicationRepository.save()`. Replace this assertion with a check that the `ApplicationEntity` captured by `applicationRepository.save()` contains the expected proceedings in its `proceedings` collection:

```java
// Before (remove):
private void verifyThatProceedingsSaved(ApplicationContent applicationContent, UUID expectedId) {
    ArgumentCaptor<List<ProceedingEntity>> captor = ArgumentCaptor.forClass((Class) List.class);
    verify(proceedingRepository).saveAll(captor.capture());
    ...
}

// After (replace with):
private void verifyThatProceedingsSaved(ApplicationContent applicationContent, UUID expectedId) {
    ArgumentCaptor<ApplicationEntity> captor = ArgumentCaptor.forClass(ApplicationEntity.class);
    verify(applicationRepository, atLeastOnce()).save(captor.capture());
    ApplicationEntity savedEntity = captor.getAllValues().getFirst();

    List<Proceeding> expectedProceedings = applicationContent.getProceedings();
    Set<ProceedingEntity> actualProceedings = savedEntity.getProceedings();

    assertEquals(expectedProceedings.size(), actualProceedings.size());
    // existing per-proceeding field assertions remain unchanged
}
```

### Unit and integration tests — `ProceedingEntity` relationship change (Phase 4 impact)

Any test that constructs a `ProceedingEntity` using `.applicationId(uuid)` must be updated to set `.application(applicationEntity)` instead. Search all test files for `.applicationId(` on `ProceedingEntity` builders and update them.

Any assertion that calls `proceedingEntity.getApplicationId()` must be replaced with `proceedingEntity.getApplication().getId()`.

`ProceedingRepositoryTest` — if it tests `findAllByApplicationId(UUID)`, update the method call to `findAllByApplication_Id(UUID)` to match the renamed Spring Data query method.

---

### Integration tests — `PersistedDataGenerator.java`

After Phase 7, `ProceedingEntity`, `DecisionEntity`, `MeritsDecisionEntity`, and `CertificateEntity` are no longer saved independently — they cascade through `ApplicationEntity`. The `PersistedDataGenerator` currently registers individual repositories for each of these generators:

```java
registerRepository(DecisionEntityGenerator.class, DecisionRepository.class);
registerRepository(ProceedingsEntityGenerator.class, ProceedingRepository.class);
registerRepository(MeritsDecisionsEntityGenerator.class, MeritsDecisionRepository.class);
registerRepository(CertificateEntityGenerator.class, CertificateRepository.class);
```

These registrations must remain for integration test data setup (tests still use `createAndPersist` to seed data directly into the DB before making HTTP calls). No change is needed here — the repositories are not removed from the project, only from `ApplicationService` write paths.

### Integration tests — `ApplicationMakeDecisionTest.java`

**`verifyCertificateSavedCorrectly`** — currently asserts `certificateRepository.findAll()`. After Phase 4, certificates cascade from `ApplicationEntity`. Update to verify via the application:

```java
// Before:
private void verifyCertificateSavedCorrectly(UUID applicationId) {
    List<CertificateEntity> certificates = certificateRepository.findAll();
    assertThat(certificates.size()).isEqualTo(1);
    CertificateEntity certificate = certificates.get(0);
    assertThat(certificate.getApplicationId()).isEqualTo(applicationId);
    ...
}

// After:
private void verifyCertificateSavedCorrectly(UUID applicationId) {
    ApplicationEntity updatedApplication = applicationRepository.findById(applicationId).orElseThrow();
    Set<CertificateEntity> certificates = updatedApplication.getCertificates();
    assertThat(certificates.size()).isEqualTo(1);
    CertificateEntity certificate = certificates.iterator().next();
    // existing field assertions remain unchanged
}
```

**`givenGrantedDecisionWithNoCertificate` and `givenRefusedDecisionWithNoCertificate` and `givenPartiallyGrantedDecisionWithNoCertificate`** — currently assert `certificateRepository.findAll().size() == 0`. Update to assert via the application entity's `certificates` collection:

```java
// Before:
List<CertificateEntity> certificates = certificateRepository.findAll();
assertThat(certificates.size()).isEqualTo(0);

// After:
ApplicationEntity updatedApplication = applicationRepository.findById(applicationEntity.getId()).orElseThrow();
assertThat(updatedApplication.getCertificates()).isEmpty();
```

**`givenMakeDecisionRequestWithExistingContentAndNewContent` setup** — this test currently manually saves a `DecisionEntity` and calls `applicationRepository.save(applicationEntity)` to link it:

```java
DecisionEntity decision = persistedDataGenerator.createAndPersist(DecisionEntityGenerator.class, ...);
applicationEntity.setDecision(decision);
applicationRepository.save(applicationEntity);
```

After Phase 4 with `CascadeType.ALL` on `decision`, the `DecisionEntity` does not need to be persisted separately first — it will cascade. Update to:

```java
DecisionEntity decision = DataGenerator.createDefault(DecisionEntityGenerator.class, ...);
applicationEntity.setDecision(decision);
applicationRepository.save(applicationEntity); // cascades decision and meritsDecisions
```

Remove the `persistedDataGenerator.createAndPersist(DecisionEntityGenerator.class, ...)` call for this test.

---

## Phase 9 — Validation

After all phases are complete, run these checks in order:

### Build and Test Validation

```bash
# Full clean rebuild
./gradlew clean build

# Run all test suites
./gradlew test
./gradlew integrationTest
```

If tests fail, address errors before proceeding. Common issues:
- `LazyInitializationException` — a lazy-loaded collection was accessed outside @Transactional context
- `Compilation errors` — mapper or import issues from Phase 4 changes
- `AssertionError in tests` — test assertions still reference old field names (e.g., `getApplicationId()`)

### Code Quality Checks

```bash
# Verify @DynamicUpdate was added
grep -n "@DynamicUpdate" data-access-service/src/main/java/uk/gov/justice/laa/dstew/access/entity/ApplicationEntity.java

# Verify no raw applicationId fields remain in ProceedingEntity
grep -n "private UUID applicationId" data-access-service/src/main/java/uk/gov/justice/laa/dstew/access/entity/ProceedingEntity.java

# Verify all ApplicationEntity relationships have explicit FetchType.LAZY
grep -n "@OneToOne\|@OneToMany\|@ManyToOne" data-access-service/src/main/java/uk/gov/justice/laa/dstew/access/entity/ApplicationEntity.java | grep -v "LAZY" | grep -v "EAGER"
# ^^ Should return no results or only @ManyToMany (which default to LAZY)

# Verify findByIdWithDecisionGraph exists and is used
grep -n "findByIdWithDecisionGraph" data-access-service/src/main/java/uk/gov/justice/laa/dstew/access/repository/ApplicationRepository.java
grep -n "findByIdWithDecisionGraph" data-access-service/src/main/java/uk/gov/justice/laa/dstew/access/service/ApplicationService.java

# Verify @Transactional annotations on all write methods
grep -n "@Transactional" data-access-service/src/main/java/uk/gov/justice/laa/dstew/access/service/ApplicationService.java | grep -E "updateApplication|unassignCaseworker|createApplication|makeDecision"

# Verify @Transactional(readOnly = true) on read methods
grep -n "@Transactional(readOnly" data-access-service/src/main/java/uk/gov/justice/laa/dstew/access/service/ApplicationService.java

# Verify no direct child repository saves in ApplicationService
grep -n "decisionRepository.save\|meritsDecisionRepository.save\|certificateRepository.save" data-access-service/src/main/java/uk/gov/justice/laa/dstew/access/service/ApplicationService.java
# ^^ Should return no results (0 matches)

# Verify removed repositories are not injected (should be 1-2 matches: decisionRepository, certificateRepository)
grep -c "decisionRepository\|meritsDecisionRepository\|certificateRepository" data-access-service/src/main/java/uk/gov/justice/laa/dstew/access/service/ApplicationService.java

# Verify removed @MockitoBean declarations
grep -n "DecisionRepository\|MeritsDecisionRepository\|CertificateRepository" data-access-service/src/test/java/.../utils/BaseServiceTest.java
# ^^ Should return no results (0 matches) or only repository invocations in tests that still need them
```

### Explicit Validation Checklist

1. ✅ `./gradlew clean build` completes without compilation errors
2. ✅ `./gradlew test` passes all unit tests (0 failures)
3. ✅ `./gradlew integrationTest` passes all integration tests (0 failures)
4. ✅ `@DynamicUpdate` is present on `ApplicationEntity` class declaration
5. ✅ `ProceedingEntity.applicationId` (raw UUID field) no longer exists — replaced by `@ManyToOne ApplicationEntity application`
6. ✅ All relationships in `ApplicationEntity` have explicit `fetch = FetchType.LAZY` (check: `caseworker`, `decision`, `proceedings`, `certificates`, `individuals`, `linkedApplications`)
7. ✅ `ApplicationRepository.findByIdWithDecisionGraph()` exists with `@EntityGraph` paths for `decision`, `decision.meritsDecisions`, `decision.meritsDecisions.proceeding`
8. ✅ `ApplicationService.makeDecision()` uses `findByIdWithDecisionGraph()` instead of `checkIfApplicationExists()`
9. ✅ `ApplicationService.updateApplication()` has `@Transactional` (non-readonly)
10. ✅ `ApplicationService.unassignCaseworker()` has `@Transactional` (non-readonly)
11. ✅ `ApplicationService.getApplication()` has `@Transactional(readOnly = true)`
12. ✅ `DecisionRepository`, `MeritsDecisionRepository`, `CertificateRepository` are no longer injected into `ApplicationService` constructor
13. ✅ No `@MockitoBean` declarations for removed repositories in test base classes
14. ✅ `applicationRepository.save()` is the only repository save call in all write methods of `ApplicationService`
15. ✅ All test assertions on `ProceedingEntity` use `getApplication().getId()` instead of `getApplicationId()`
16. ✅ All test builders use `.application(appEntity)` instead of `.applicationId(uuid)` for ProceedingEntity

### Performance Verification (Optional but Recommended)

After validation passes, you may want to verify performance improvements:

```bash
# Run a profile to check SQL generated by a key operation
# Compare the SQL before and after @DynamicUpdate — look for SELECT-only vs UPDATE-all-columns

# Before: UPDATE applications SET id = ?, application_content = ?, ... (all columns)
# After:  UPDATE applications SET status = ? (only changed fields)
```

### Rollback / Diagnostic

If any phase fails catastrophically:

1. Review the error output carefully — it usually identifies the file and line causing the issue
2. Common causes:
   - **Mapper compilation errors** — check Phase 4 changes to ProceedingEntity didn't break mapper logic
   - **Test assertion failures** — a test still references `getApplicationId()` that was removed
   - **LazyInitializationException** — a relationship is being accessed outside a @Transactional context
3. If unsure, revert the latest phase and re-read the entity/service files to understand the issue
4. Each phase is independent enough to be retried after fixing the root cause

---

## Git Workflow Recommendation

For clean history and easy review:

1. **Create a feature branch:**
   ```bash
   git checkout -b dstew/1216-aggregate-root-migration
   ```

2. **One commit per phase:** After each phase is validated passing tests, create a commit:
   ```bash
   git add data-access-service/src/main/java/uk/gov/justice/laa/dstew/access/entity/ApplicationEntity.java
   git add data-access-service/src/main/java/uk/gov/justice/laa/dstew/access/service/ApplicationService.java
   git commit -m "Phase 1: Fix circular references in ApplicationEntity and IndividualEntity"
   ```

3. **Commit message pattern:**
   ```
   Phase N: <Short description>

   - Specific change 1
   - Specific change 2
   - Tests: [pass/fail and count]
   ```

4. **After Phase 9 validation passes,** squash or keep commits and open a PR with full context.

---

## Summary of What This Migration Achieves

| Aspect | Before | After |
|--------|--------|-------|
| **Consistency boundary** | Implicit; services independently update child entities | Explicit; all writes go through `ApplicationEntity.save()` |
| **Circular references** | IndividualEntity ↔ ApplicationEntity bidirectional; ApplicationEntity self-referential | No circular refs; unidirectional ownership |
| **Fetch strategies** | Accidental EAGER defaults on @OneToOne relations | Explicit LAZY on all relationships; @EntityGraph for specific writes |
| **Database updates** | Every `save()` does UPDATE on all columns (including 1MB+ JSONB blob) | Only changed columns updated via @DynamicUpdate |
| **Transaction boundaries** | Inconsistent; only makeDecision() has @Transactional | Consistent; all writes have @Transactional, all reads have @Transactional(readOnly=true) |
| **Child entity saves** | 5 separate repository.save() calls per operation | 1 applicationRepository.save() cascades to children |
| **Test maintainability** | Mocks for 6 repositories; hard to follow complex object graphs | Simpler mocks; assertions verify through the root aggregate |

---

## Quick Reference: Files Modified by Phase

- **Phase 1:** ApplicationEntity.java, IndividualEntity.java, ApplicationSummaryEntity.java
- **Phase 2:** ApplicationEntity.java, DecisionEntity.java
- **Phase 3:** ApplicationEntity.java
- **Phase 4:** ProceedingEntity.java, ApplicationEntity.java, ApplicationService.java, ProceedingRepository.java, ProceedingMapper.java
- **Phase 5:** ApplicationEntity.java, ApplicationRepository.java, ApplicationService.java (makeDecision method)
- **Phase 6:** ApplicationService.java
- **Phase 7:** ApplicationService.java, ProceedingsService.java (possibly delete)
- **Phase 8:** BaseServiceTest.java, CreateApplicationTest.java, PersistedDataGenerator.java, ApplicationMakeDecisionTest.java, ProceedingRepositoryTest.java, and all other test files using ProceedingEntity
- **Phase 9:** None (validation only)









