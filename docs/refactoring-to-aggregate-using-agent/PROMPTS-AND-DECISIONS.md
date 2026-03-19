# Prompts & Decisions - Aggregate Root Migration

## Overview
This document captures the key prompts used throughout the 7-phase migration and the decisions they led to. Each section represents a critical decision point where user questions shaped the architecture.

---

## Phase 1: Entity Relationship Analysis

### Prompt 1.1
**User**: "Map out all relationships between ApplicationEntity and its child entities"

**Analysis Process**:
1. Examined ApplicationEntity class structure
2. Identified all @OneToOne, @OneToMany, @ManyToOne relationships
3. Determined which children have independent lifecycle vs. aggregate-managed

**Decision Made**: ApplicationEntity is the aggregate root with four main children:
- DecisionEntity (1:1, required)
- MeritsDecisionEntity (nested under Decision, 1:N, optional)
- ProceedingEntity (1:N, managed through bidirectional relationship)
- CertificateEntity (1:N, optional)

**Outcome**: Aggregate boundary clearly defined

---

## Phase 2: Cascade Configuration

### Prompt 2.1
**User**: "What cascade rules should DecisionEntity have if it's managed by ApplicationEntity?"

**Analysis**:
- DecisionEntity has no independent lifecycle outside ApplicationEntity
- When Application is deleted, Decision should be deleted (orphanRemoval)
- Decision should be persisted when Application is saved

**Decision Made**:
```java
@OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
```

**Rationale**:
- `CascadeType.ALL` covers PERSIST, MERGE, REMOVE
- `orphanRemoval = true` ensures cleanup of unlinked Decisions
- `FetchType.LAZY` prevents unnecessary loading

### Prompt 2.2
**User**: "Should ProceedingEntity use orphanRemoval if it's managed through the application?"

**Analysis**:
- Proceedings are created through application content
- Application owns the relationship (mappedBy)
- Unlinked proceedings should be cleaned up

**Decision Made**: Yes, use `orphanRemoval = true`

```java
@OneToMany(mappedBy = "application", cascade = CascadeType.ALL,
           orphanRemoval = true, fetch = FetchType.LAZY)
```

---

## Phase 3: Query Optimization

### Prompt 3.1
**User**: "We're seeing N+1 query problems when loading application with decisions. How do we optimize?"

**Analysis**:
- When loading Application, Decisions loaded lazily (good)
- When loading DecisionEntity relationships, MeritsDecisions loaded individually (bad)
- Multiple queries when accessing nested relationships

**Decision Made**: Create @EntityGraph methods for common access patterns

```java
@EntityGraph(attributePaths = {"decision", "decision.meritsDecisions", "proceedings"})
Optional<ApplicationEntity> findByIdWithDecisionGraph(UUID id);
```

**Rationale**:
- Selective eager loading for known patterns
- Avoids N+1 for foreseeable access patterns
- Lazy loading for infrequently-accessed data

### Prompt 3.2
**User**: "What's the trade-off between eager loading everything vs. specific @EntityGraph methods?"

**Analysis**:
| Strategy | Pros | Cons |
|----------|------|------|
| Eager Load All | Fewer queries | Wastes memory, slower |
| Selective @EntityGraph | Optimized for patterns | Requires method per pattern |
| Pure Lazy | Minimal loading | N+1 problems |

**Decision Made**: Selective @EntityGraph approach

**Rationale**:
- Three main access patterns identified:
  1. Get application with decision (for decision-making)
  2. Get application with certificates (for certificate ops)
  3. Get application with proceedings (for proceeding details)
- Create specific methods for each pattern

---

## Phase 4: Transactional Boundaries

### Prompt 4.1
**User**: "Should createApplication be @Transactional? What should happen if proceeding save fails?"

**Analysis**:
- Creating application without proceedings is inconsistent state
- If proceeding save fails, entire operation should rollback
- Need atomic guarantees

**Decision Made**: All write operations are `@Transactional`

```java
@Transactional
public UUID createApplication(ApplicationCreateRequest request) {
    // Entire operation is atomic
    // If any child fails, all rolled back
}
```

**Rationale**:
- Ensures ACID properties
- Database rolls back all changes on exception
- No partial saves allowed

### Prompt 4.2
**User**: "Should read-only methods also be @Transactional?"

**Analysis**:
- Read operations don't modify state
- But still need consistent view of data
- Spring handles lazy loading within transaction context

**Decision Made**: Read methods are `@Transactional(readOnly = true)`

```java
@Transactional(readOnly = true)
public Application getApplicationById(UUID id) {
    return applicationRepository.findByIdWithDecisionGraph(id);
}
```

**Rationale**:
- Allows lazy loading within transaction
- Tells database read-only, may optimize queries
- Explicit about operation semantics

---

## Phase 5: Service Layer Refactoring - Part 1

### Prompt 5.1
**User**: "Should createApplication directly create proceedings, or use ProceedingsService?"

**Analysis**:
- ProceedingsService is separate service for managing proceedings
- But ApplicationEntity owns the proceedings relationship (mappedBy)
- Using separate service breaks aggregate root pattern

**Decision Made**: Consolidate into ApplicationService

**Before**:
```java
public UUID createApplication(ApplicationCreateRequest request) {
    ApplicationEntity app = mapper.mapToEntity(request);
    applicationRepository.save(app);
    proceedingsService.addProceedings(app.getId(), request.getProceedings());
}
```

**After**:
```java
public UUID createApplication(ApplicationCreateRequest request) {
    ApplicationEntity app = mapper.mapToEntity(request);
    request.getProceedings().forEach(proc -> {
        app.getProceedings().add(mapper.mapToEntity(proc));
    });
    return applicationRepository.save(app).getId();
}
```

**Rationale**:
- ApplicationEntity is aggregate root; all children flow through it
- Single transaction covers all creation
- No two-phase saves

### Prompt 5.2
**User**: "If we remove ProceedingsService calls, what breaks in tests?"

**Analysis**:
- Tests mock ProceedingsRepository
- Tests verify save count on ProceedingsRepository
- Need to update test expectations

**Decision Made**: Update tests to mock only ApplicationRepository

**Changes**:
1. Remove ProceedingsRepository mock
2. Update assertion to verify proceedings within application
3. Use ArgumentCaptor to capture saved entity

```java
ArgumentCaptor<ApplicationEntity> captor = ArgumentCaptor.forClass(ApplicationEntity.class);
verify(applicationRepository).save(captor.capture());
ApplicationEntity saved = captor.getValue();
assertEquals(2, saved.getProceedings().size());
```

---

## Phase 6: Service Layer Refactoring - Part 2

### Prompt 6.1
**User**: "We have DecisionRepository, MeritsDecisionRepository, and CertificateRepository all directly injected. Should these exist if ApplicationEntity owns them?"

**Analysis**:
- These repositories provide direct access to child entities
- Direct access breaks aggregate root pattern
- All modifications should go through ApplicationEntity

**Decision Made**: Remove direct child repository injections

```java
// REMOVE from ApplicationService
@Autowired private DecisionRepository decisionRepository;
@Autowired private MeritsDecisionRepository meritsDecisionRepository;
@Autowired private CertificateRepository certificateRepository;
```

**Rationale**:
- Prevents accidental direct child modifications
- Forces all changes through aggregate root
- Clearer code showing relationships

### Prompt 6.2
**User**: "In makeDecision(), how should we handle creating decisions if not using DecisionRepository.save()?"

**Analysis**:
- Decision creation used to call `decisionRepository.save()`
- But ApplicationService already does `applicationRepository.save()`
- Need to create Decision entity and let cascade handle it

**Decision Made**: Create decision in-memory, add to application, let cascade persist

```java
// Before
DecisionEntity decision = new DecisionEntity(...);
decisionRepository.save(decision);
application.setDecision(decision);
applicationRepository.save(application);

// After
DecisionEntity decision = new DecisionEntity(...);
application.setDecision(decision);
applicationRepository.save(application); // Cascade persists decision
```

**Rationale**:
- Single save point means single transaction
- Cascade handles the persistence
- Atomic operation without explicit child saves

### Prompt 6.3
**User**: "Should we remove DecisionRepository entirely, or keep it for direct querying?"

**Analysis**:
- Direct child entity queries violate aggregate root pattern
- But DecisionRepository might be used elsewhere
- Need to check usage across codebase

**Decision Made**: Keep repository for now, but don't inject into ApplicationService

**Rationale**:
- May have legitimate query-only use cases
- Removing would require checking all usages
- Focus on service layer first, repositories as separate concern
- Can be removed in future if no longer needed

---

## Phase 7: UUID Embedding - The Critical Decision

### Prompt 7.1
**User**: "Why are we getting 'null value in column proceeding_id' errors in integration tests?"

**Root Cause Analysis**:
```
Error: null value in column 'proceeding_id' violates not-null constraint
Test: persistedDataGenerator.createAndPersist(MeritsDecisionEntity with proceeding set)
Problem: Hibernate cascade merge can't handle @ManyToOne to external entity
```

**Initial Hypothesis**: Cascade operations don't properly sync foreign keys

**Investigation Steps**:
1. Examined MeritsDecisionEntity structure
2. Found @OneToOne relationship to ProceedingEntity
3. Realized ProceedingEntity managed separately from cascade
4. Cascade merge couldn't properly establish FK relationship

**Decision Made**: Need architectural approach change

---

### Prompt 7.2
**User**: "What are the possible approaches to fix cascade persistence with @ManyToOne relationships?"

**Brainstorming Session - 6 Approaches Evaluated**:

#### Approach 1: Separate Batch Saves ❌
```java
proceedingRepository.saveAll(proceedings);
application.setProceedings(loadedProceedings);
applicationRepository.save(application);
```
**Pros**: Simple to implement
**Cons**: Two transactions, race conditions, not atomic

#### Approach 2: Detached Proceeding Loading
```java
detachedProceedingIds.forEach(id -> {
    ProceedingEntity p = proceedingRepository.findById(id).orElseThrow();
    entityManager.detach(p);
    decision.setProceeding(p);
});
```
**Pros**: Avoids cascade issues
**Cons**: Complex, brittle, managing entity lifecycle manually

#### Approach 3: Raw SQL Inserts
```java
jdbcTemplate.batchUpdate("INSERT INTO merits_decisions...", ...)
```
**Pros**: Bypasses ORM complexity
**Cons**: Loses type safety, difficult to maintain, mixing paradigms

#### Approach 4: Temporary Join Table
```
merits_decisions → temp_merit_proceeding_mapping
```
**Pros**: Decouples relationships
**Cons**: Schema complexity, unnecessary overhead

#### Approach 5: Lazy Initialization Pattern
```java
@PostLoad
void initializeProceeding() {
    if (proceedingId != null && proceeding == null) {
        proceeding = proceedingRepository.findById(proceedingId).orElse(null);
    }
}
```
**Pros**: Explicit control
**Cons**: Timing issues, may not work in cascade scenarios

#### Approach 6: UUID Embedding ✅ **SELECTED**
```java
@Column(name = "proceeding_id", nullable = false) UUID proceedingId;
@ManyToOne(fetch = FetchType.LAZY, insertable = false, updatable = false)
ProceedingEntity proceeding;
```
**Pros**:
- Simple, clean design
- Hibernate controls FK directly
- Maintains relationship for queries
- No complex lifecycle management

**Cons**:
- Need to keep UUID and relationship in sync
- Test fixtures need updates

---

### Prompt 7.3
**User**: "For UUID embedding approach, how do we keep proceedingId and proceeding in sync?"

**Design Options**:

**Option A: Always Update Both**
```java
merit.setProceedingId(uuid);
merit.setProceeding(proceedingRepository.findById(uuid).orElseThrow());
```
**Pros**: Explicit
**Cons**: Extra query, tight coupling to repository

**Option B: Lifecycle Hooks (@PrePersist)**
```java
@PrePersist
void syncProceedingId() {
    if (proceedingId == null && proceeding != null) {
        proceedingId = proceeding.getId();
    }
}
```
**Pros**: Automatic, only called at persistence time
**Cons**: Requires understanding of JPA lifecycle

**Option C: Custom Setter**
```java
public void setProceeding(ProceedingEntity p) {
    this.proceeding = p;
    if (p != null) this.proceedingId = p.getId();
}
```
**Pros**: Explicit sync whenever relationship changes
**Cons**: Lombok @Builder doesn't call custom setters

**Decision Made**: Combine B + C - Custom setter + @PrePersist

```java
// Custom setter (called explicitly in production)
public void setProceeding(ProceedingEntity proceeding) {
    this.proceeding = proceeding;
    if (proceeding != null) {
        this.proceedingId = proceeding.getId();
    }
}

// Lifecycle hook (catches test fixture scenarios)
@PrePersist
protected void ensureProceedingIdBeforePersist() {
    if (this.proceedingId == null && this.proceeding != null) {
        this.proceedingId = this.proceeding.getId();
    }
}
```

**Rationale**:
- Custom setter handles normal production cases
- @PrePersist catches edge cases (test fixtures)
- Defensive programming for robustness

---

### Prompt 7.4
**User**: "In ApplicationService.makeDecision(), how do we find existing merits decisions when proceedingId might be null in test fixtures?"

**Scenario**:
- Test creates MeritsDecisionEntity with proceedingId = null
- Test sets proceeding relationship only
- Production code needs to find it by proceedingId

**Decision Made**: Check both proceedingId AND proceeding relationship

```java
var existingMerit = decision.getMeritsDecisions().stream()
    .filter(m -> {
        UUID mProceedingId = m.getProceedingId();
        // Fallback: extract from relationship if UUID field is null
        if (mProceedingId == null && m.getProceeding() != null) {
            mProceedingId = m.getProceeding().getId();
        }
        return mProceedingId != null && mProceedingId.equals(proceedingId);
    })
    .findFirst();
```

**Rationale**:
- Handles both test and production scenarios
- Backward compatible with existing test fixtures
- Defensive: doesn't assume field is always populated

---

### Prompt 7.5
**User**: "Should we update existing test fixtures or make code more defensive?"

**Analysis**:
| Option | Pros | Cons |
|--------|------|------|
| Update Fixtures | Clear intent, explicit | Large test changes, fragile |
| Defensive Code | Handles both cases, backward compat | More complex logic |

**Decision Made**: Make code defensive, update fixtures progressively

**Rationale**:
- Prevents breaking existing tests
- Allows gradual migration
- Safer approach for production
- Tests can stay as-is while we fix the 6 integration issues

---

## Key Principles Applied Across All Phases

### 1. Single Responsibility Principle
**Applied**: Each repository has one responsibility
- ApplicationRepository: Application aggregate root persistence
- ProceedingRepository: Proceeding query/direct operations only
- NOT mixed in service layer

### 2. Aggregate Root Pattern
**Applied**: ApplicationEntity controls all child persistence
- No direct child repository saves from service
- All children cascade from root
- Clear boundary enforcement

### 3. Lazy vs. Eager Loading
**Applied**: Default LAZY, specific @EntityGraph for known patterns
- Prevents wasting memory
- Optimizes for identified access patterns
- Prevents N+1 queries

### 4. Defensive Programming
**Applied**: Code handles multiple scenarios
- UUID embedding checks both fields
- Lifecycle hooks catch edge cases
- Fallback logic for backward compatibility

### 5. Transaction Atomicity
**Applied**: @Transactional on all write operations
- All-or-nothing persistence
- Clear rollback semantics
- Consistent aggregate state

---

## Decision Impact Analysis

### High-Impact Decisions
1. **Cascade Configuration** → Eliminated 5 repository save paths
2. **UUID Embedding** → Resolved cascade persistence issues
3. **Single Save Point** → Enforced aggregate root pattern
4. **@EntityGraph** → Eliminated N+1 query problems

### Test-Impacting Decisions
1. **Removed Repository Mocks** → Updated 12+ test classes
2. **Defensive merits matching** → Maintained test compatibility
3. **Lifecycle hooks** → Handled test fixture edge cases

### Performance-Impacting Decisions
1. **Lazy loading** → Reduced memory usage
2. **Selective eager loading** → Optimized query performance
3. **Single transaction** → Faster aggregate persistence

---

## Lessons for Future Decisions

### When to Use Cascade
✅ Use when:
- Child has no independent lifecycle
- Child is always owned by parent
- Deleting parent should delete children

❌ Don't use when:
- Child is independently managed
- Multiple parents can own same child
- Child lifetime extends beyond parent

### When to Embed Foreign Keys
✅ Use when:
- Child has @ManyToOne to external entity
- External entity not in aggregate
- Need explicit FK control in cascade

❌ Don't use when:
- Pure composition (child fully managed)
- Single relationship (one-to-one)
- Performance of extra UUID negligible

### Entity Graph Strategy
✅ Define when:
- Access pattern is predictable
- Query cost is significant
- Multiple levels of relationships

❌ Don't define when:
- Relationships rarely accessed together
- Relationships change frequently with new features
- Pure lazy loading sufficient

