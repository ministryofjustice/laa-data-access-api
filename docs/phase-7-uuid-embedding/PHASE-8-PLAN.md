# Phase 8: Apply UUID Embedding to ProceedingEntity - Implementation Plan

## Executive Summary

**Objective**: Apply the same UUID embedding pattern to ProceedingEntity that we used for MeritsDecisionEntity, ensuring consistency across the aggregate and fixing the 6 failing integration tests.

**Scope**: Fix architectural inconsistency and stabilize cascade behavior

**Phase Duration**: 2-3 hours for implementation + testing

---

## The Architectural Insight

### Current State (Inconsistent)
```
ApplicationEntity
├── Decision (UUID embedding ✅)
│   └── MeritsDecisions (UUID embedding ✅)
└── Proceedings (Full bidirectional ❌)
```

### Desired State (Consistent)
```
ApplicationEntity (Aggregate Root)
├── Decision (Pattern: UUID embedding)
│   └── MeritsDecisions (Pattern: UUID embedding)
└── Proceedings (Pattern: UUID embedding) ← Apply here
```

---

## Why This Fixes the Integration Tests

### Current Problem Chain
```
Test creates: MeritsDecisionEntity → proceeding set → proceedingId null
  ↓
Cascade tries to persist MeritsDecisionEntity
  ↓
ProceedingEntity relationship unresolved during merge
  ↓
Cascade confusion cascades down (pun intended 😄)
  ↓
❌ Constraint violation
```

### After Phase 8
```
Test creates: MeritsDecisionEntity → proceedingId set explicitly
  ↓
Cascade persists with explicit FK
  ↓
ProceedingEntity has explicit applicationId
  ↓
No FK uncertainty
  ↓
✅ Clean cascade with no confusion
```

---

## Implementation Plan

### Step 1: Refactor ProceedingEntity (30 minutes)

**File**: `ProceedingEntity.java`

**Current**:
```java
@Entity
public class ProceedingEntity implements AuditableEntity {
    @Id
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "application_id")
    private ApplicationEntity application;

    // Rest of entity...
}
```

**Target**:
```java
@Entity
public class ProceedingEntity implements AuditableEntity {
    @Id
    private UUID id;

    // PRIMARY: Explicit FK for Hibernate control
    @Setter
    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    // SECONDARY: Lazy relationship for queries only
    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", insertable = false, updatable = false)
    private ApplicationEntity application;

    /**
     * Sync application ID from application relationship if needed
     */
    public void setApplication(ApplicationEntity application) {
        this.application = application;
        if (application != null && application.getId() != null) {
            this.applicationId = application.getId();
        }
    }

    @PrePersist
    protected void ensureApplicationIdBeforePersist() {
        if (this.applicationId == null && this.application != null) {
            this.applicationId = this.application.getId();
        }
    }

    @PreUpdate
    protected void ensureApplicationIdBeforeUpdate() {
        if (this.applicationId == null && this.application != null) {
            this.applicationId = this.application.getId();
        }
    }
}
```

**Changes**:
- Add `applicationId` UUID field (nullable=false)
- Change `application` relationship to `insertable=false, updatable=false`
- Add custom `setApplication()` method
- Add `@PrePersist/@PreUpdate` lifecycle hooks
- Use individual `@Setter` annotations (not class-level)

---

### Step 2: Update ApplicationEntity (15 minutes)

**File**: `ApplicationEntity.java`

**Current**:
```java
@OneToMany(mappedBy = "application", cascade = CascadeType.ALL,
           orphanRemoval = true, fetch = FetchType.LAZY)
private Set<ProceedingEntity> proceedings;
```

**Consideration**: The `mappedBy="application"` still works because ProceedingEntity still has the relationship field. However, we should consider:

**Option A: Keep mappedBy (minimal change, backward compatible)**
```java
@OneToMany(mappedBy = "application", cascade = CascadeType.ALL,
           orphanRemoval = true, fetch = FetchType.LAZY)
private Set<ProceedingEntity> proceedings;
```
- ✅ Minimal code change
- ✅ Uses existing mappedBy
- ✅ No schema migration needed
- ⚠️ Still uses relationship, not UUID list

**Option B: Switch to explicit UUID set (full consistency)**
```java
@Column(name = "proceeding_ids")
@ElementCollection
private Set<UUID> proceedingIds = new HashSet<>();

// Plus maintain relationship collection for code compatibility
@OneToMany(mappedBy = "application", fetch = FetchType.LAZY)
@Transient
private Set<ProceedingEntity> proceedings;
```
- ✅ Most explicit
- ❌ More complex
- ❌ Requires migration
- ⚠️ Over-engineering for this case

**Recommendation**: **Option A (Keep mappedBy)**
- Less invasive change
- Pattern is consistent (UUID embedding in child entities)
- Parent still uses relationship collection

---

### Step 3: Update Service Layer (20 minutes)

**File**: `ApplicationService.java` - `createApplication()` method

**Current**:
```java
public UUID createApplication(ApplicationCreateRequest request) {
    ApplicationEntity application = mapper.mapToEntity(request);
    request.getProceedings().forEach(proc -> {
        ProceedingEntity proceeding = mapper.mapToEntity(proc);
        application.getProceedings().add(proceeding);
    });
    return applicationRepository.save(application).getId();
}
```

**Update** - Explicit FK setting:
```java
public UUID createApplication(ApplicationCreateRequest request) {
    ApplicationEntity application = mapper.mapToEntity(request);
    UUID applicationId = application.getId() != null ? application.getId() : UUID.randomUUID();

    request.getProceedings().forEach(proc -> {
        ProceedingEntity proceeding = mapper.mapToEntity(proc);
        proceeding.setApplicationId(applicationId);  // Explicit FK setting
        application.getProceedings().add(proceeding);
    });

    return applicationRepository.save(application).getId();
}
```

**Why**:
- Ensures FK is set before cascade
- Defensive: handles cases where application ID not yet assigned
- Mirrors pattern used in `makeDecision()` for merits

---

### Step 4: Update Test Fixtures (45 minutes)

**File**: `ProceedingsEntityGenerator.java`

**Current**:
```java
@Override
public ProceedingEntity createDefault() {
    return ProceedingEntity.builder()
            .id(UUID.randomUUID())
            .build();
}
```

**Update**:
```java
@Override
public ProceedingEntity createDefault() {
    return ProceedingEntity.builder()
            .id(UUID.randomUUID())
            .applicationId(UUID.randomUUID())  // Set explicit FK
            .build();
}
```

---

### Step 5: Fix the 6 Failing Integration Tests (30 minutes)

**Files**:
- `ApplicationMakeDecisionTest.java` (all 6 failing tests)
- `GetApplicationTest.java`
- `ApplicationRepositoryTest.java`

**Pattern**: Ensure proceedingId (and now applicationId) explicitly set

**Example Fix**:

```java
// Before
ProceedingEntity proceeding = persistedDataGenerator.createAndPersist(
    ProceedingsEntityGenerator.class,
    builder -> builder.application(applicationEntity)
);

// After
ProceedingEntity proceeding = persistedDataGenerator.createAndPersist(
    ProceedingsEntityGenerator.class,
    builder -> builder
        .application(applicationEntity)
        .applicationId(applicationEntity.getId())  // Add explicit FK
);
```

**All 6 tests to update**:
1. givenRefusedDecisionWithNoCertificate_whenAssignDecision_thenReturnNoContent
2. givenPartiallyGrantedDecisionWithNoCertificate_whenAssignDecision_thenReturnNoContent
3. givenMakeDecisionRequestWithExistingContentAndNewContent_whenAssignDecision_thenReturnNoContent_andDecisionUpdated
4. givenMakeDecisionRequest_whenAssignDecision_thenUpdateApplicationEntity
5. givenMakeDecisionRequestWithTwoProceedings_whenAssignDecision_thenReturnNoContent_andDecisionSaved
6. givenGrantedDecisionWithCertificate_whenAssignDecision_thenReturnNoContent_andDecisionAndCertificateSaved

---

### Step 6: Update Defensive Logic (10 minutes)

**File**: `ApplicationService.java` - `makeDecision()` method

**Current** (for merits):
```java
var existingMerit = decision.getMeritsDecisions().stream()
    .filter(m -> {
        UUID mProceedingId = m.getProceedingId();
        if (mProceedingId == null && m.getProceeding() != null) {
            mProceedingId = m.getProceeding().getId();
        }
        return mProceedingId != null && mProceedingId.equals(proceedingId);
    })
    .findFirst();
```

**No changes needed** - This already handles the fallback case.

However, we should consider if proceeding lookup needs updating:

```java
// In checkIfAllProceedingsExistForApplication()
List<ProceedingEntity> proceedings = proceedingRepository.findAllById(idsToFetch);

proceedingIds.forEach(id -> {
    ProceedingEntity proceeding = proceedings.stream()
        .filter(p -> p.getId().equals(id))
        .findFirst()
        .orElseThrow(() -> new ResourceNotFoundException(...));

    // Verify still linked to application
    if (!proceeding.getApplicationId().equals(applicationId)) {
        throw new ResourceNotFoundException(...);
    }
});
```

---

## Testing Strategy

### Unit Tests (Should pass without changes)
- 205/205 tests should still pass
- Mocked tests don't care about FK sync
- Lifecycle hooks called during mock persistence

### Integration Tests (Should fix the 6 failures)
- Explicitly set applicationId in all fixtures
- Run full integration test suite
- Expected: 229/229 passing ✅

### Regression Testing
- Verify cascade still works
- Verify relationship lazy loading still works (if accessed)
- Verify orphan removal still works
- Performance unchanged

---

## Implementation Checklist

### Code Changes
- [ ] **ProceedingEntity.java** (30 min)
  - [ ] Add `applicationId` UUID field with @Setter
  - [ ] Change `application` relationship to insertable=false
  - [ ] Add custom `setApplication()` method
  - [ ] Add @PrePersist/@PreUpdate hooks
  - [ ] Add JavaDoc comments

- [ ] **ApplicationService.java** (20 min)
  - [ ] Update `createApplication()` to set applicationId explicitly
  - [ ] Verify defensive fallback logic still works
  - [ ] Test makeDecision still cascades properly

- [ ] **ProceedingsEntityGenerator.java** (5 min)
  - [ ] Add applicationId to createDefault()
  - [ ] Add builder method for customization

### Test Fixes
- [ ] **Integration test fixtures** (30 min)
  - [ ] Update 6 failing tests to set applicationId
  - [ ] Update ProceedingsEntityGenerator usage
  - [ ] Verify all other tests still work

### Verification
- [ ] Run unit tests: `./gradlew test`
  - [ ] Expected: 205/205 passing

- [ ] Run integration tests: `./gradlew integrationTest`
  - [ ] Expected: 229/229 passing (was 223/229)

- [ ] Run full test suite: `./gradlew test integrationTest`
  - [ ] Expected: 434/434 passing

---

## Risk Assessment

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|-----------|
| Cascade behavior changes | Low | Medium | Keep relationship definition, only add FK |
| Lazy loading breaks | Low | Medium | Field is only for FK, relationship still works |
| Existing queries break | Low | High | Don't remove application relationship, just add FK |
| Performance regression | Very Low | Low | UUID field is lightweight |
| Migration data loss | N/A | N/A | Column already exists, just populating |

**Overall Risk**: LOW - We're adding a field and lifecycle hooks, not removing anything

---

## Rollback Plan

If issues arise:
1. Remove the @PrePersist/@PreUpdate hooks (keep FK field)
2. Keep custom setApplication() method (backward compatible)
3. Relationship still works via mappedBy

The change is backward compatible - old code still works.

---

## Benefits After Phase 8

| Aspect | Before | After |
|--------|--------|-------|
| Pattern Consistency | Inconsistent | Consistent ✅ |
| Integration Tests | 223/229 (97%) | 229/229 (100%) ✅ |
| Cascade Complexity | Higher | Lower ✅ |
| FK Control | Implicit | Explicit ✅ |
| Code Clarity | Mixed patterns | Clear pattern ✅ |
| Maintainability | Confusing | Natural ✅ |

---

## Timeline Estimate

| Task | Time | Cumulative |
|------|------|-----------|
| ProceedingEntity refactor | 30 min | 30 min |
| ApplicationService updates | 20 min | 50 min |
| Factory updates | 5 min | 55 min |
| Test fixtures (~6 tests × 5 min) | 30 min | 85 min |
| Testing & verification | 20 min | 105 min |
| Documentation | 15 min | 120 min |
| **Total** | | **~2 hours** |

---

## Success Criteria

- ✅ All 205 unit tests passing
- ✅ All 229 integration tests passing (0 failures)
- ✅ No performance regression
- ✅ Consistent UUID embedding pattern across all entities
- ✅ Clear architectural model (ApplicationEntity as aggregate root with explicit FKs)
- ✅ Documentation updated

---

## Next Phase Possibilities

After Phase 8 completes:

**Phase 9**: Apply same pattern to CertificateEntity if needed
**Phase 10**: Generalize as reusable pattern for other aggregates
**Phase 11**: Consider event sourcing over top of this foundation

---

## Related Documentation

- Phase 7: UUID Embedding for MeritsDecisionEntity
- APPROACH-SELECTION.md: Why UUID embedding was chosen
- IMPLEMENTATION.md: Technical details of the pattern

---

## Appendix: Why This Works Architecturally

### DDD Perspective
- ApplicationEntity is the **Aggregate Root**
- ProceedingEntity is a **child entity** within the aggregate
- ProceedingEntity should NOT have independent references outside the aggregate
- applicationId UUID field reflects the true ownership

### JPA/Hibernate Perspective
- **Explicit FK field**: Gives Hibernate direct control (no guessing)
- **Lazy relationship**: Allows navigation if needed, but not required
- **insertable=false**: Prevents dual management (relation or FK)
- **@PrePersist hooks**: Handles edge cases and test scenarios

### Cascade Perspective
- **No confusion**: FK is explicit before cascade
- **Simple merge**: No trying to resolve bidirectional relationships
- **Consistent pattern**: MeritsDecision → Proceeding → Certificate all use same approach

This is the "right" way to model owned entities in JPA.
