# Implementation Details - Phase 7 UUID Embedding

## Architecture Overview

```
ApplicationEntity (Aggregate Root)
    ├── Decision
    │   ├── Cascade: CascadeType.ALL, orphanRemoval = true
    │   ├── Fetch: LAZY
    │   └── MeritsDecisions (Set)
    │       ├── proceedingId (UUID) ← PRIMARY for persistence
    │       ├── proceeding (ProceedingEntity) ← SECONDARY, lazy-loaded
    │       └── sync via @PrePersist/@PreUpdate
    │
    ├── Proceedings (Set)
    │   ├── Cascade: CascadeType.ALL, mappedBy = "application"
    │   └── Fetch: LAZY
    │
    └── Certificates (Set)
        ├── Cascade: CascadeType.ALL
        └── Fetch: LAZY
```

## Code Changes

### 1. MeritsDecisionEntity.java

**Key Addition**: Dual foreign key representation

```java
// NEW: Direct UUID column for persistence
@Setter
@Column(name = "proceeding_id", nullable = false)
private UUID proceedingId;

// CHANGED: From @OneToOne to @ManyToOne, insertable=false
@Setter
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "proceeding_id", nullable = false, insertable = false, updatable = false)
private ProceedingEntity proceeding;

// ADDED: Custom setter to sync proceedingId
public void setProceeding(ProceedingEntity proceeding) {
    this.proceeding = proceeding;
    if (proceeding != null && proceeding.getId() != null) {
        this.proceedingId = proceeding.getId();
    }
}

// ADDED: Lifecycle hooks for cascade scenarios
@PrePersist
protected void ensureProceedingIdBeforePersist() {
    if (this.proceedingId == null && this.proceeding != null) {
        this.proceedingId = this.proceeding.getId();
    }
}

@PreUpdate
protected void ensureProceedingIdBeforeUpdate() {
    if (this.proceedingId == null && this.proceeding != null) {
        this.proceedingId = this.proceeding.getId();
    }
}
```

**Why the Approach**:
- `insertable=false, updatable=false` on relationship: Prevents Hibernate from trying to manage the column twice
- `@PrePersist/@PreUpdate`: Handles cases where proceeding is set but proceedingId not immediately synced
- UUID field directly on entity: Gives Hibernate explicit control over foreign key persistence

### 2. ApplicationService.java - makeDecision() Method

**Enhanced Merits Decision Logic**:

```java
// Line 429-434: Ensure collection is mutable
Set<MeritsDecisionEntity> merits = decision.getMeritsDecisions();
if (merits == null || merits.getClass().getName().contains("Collections$Unmodifiable")) {
    merits = new HashSet<>(merits != null ? merits : Set.of());
    decision.setMeritsDecisions(merits);
}
final Set<MeritsDecisionEntity> finalMerits = merits;

// Line 441-471: Improved matching logic
request.getProceedings().forEach(proceeding -> {
    UUID proceedingId = proceeding.getProceedingId();

    // Check BOTH proceedingId field AND proceeding relationship
    var existingMerit = decision.getMeritsDecisions().stream()
        .filter(m -> {
            UUID mProceedingId = m.getProceedingId();
            if (mProceedingId == null && m.getProceeding() != null) {
                mProceedingId = m.getProceeding().getId();
            }
            return mProceedingId != null && mProceedingId.equals(proceedingId);
        })
        .findFirst();

    if (existingMerit.isPresent()) {
        // UPDATE existing merit
        var merit = existingMerit.get();
        merit.setProceedingId(proceedingId); // Ensure synced
        merit.setModifiedAt(Instant.now());
        merit.setDecision(...);
        merit.setReason(...);
        merit.setJustification(...);
    } else {
        // CREATE new merit
        var newMerit = new MeritsDecisionEntity();
        newMerit.setProceedingId(proceedingId);
        newMerit.setModifiedAt(Instant.now());
        newMerit.setDecision(...);
        newMerit.setReason(...);
        newMerit.setJustification(...);
        finalMerits.add(newMerit);
    }
});
```

**Key Improvements**:
- Handles both persistent and test fixture scenarios
- Separates update vs. create logic (prevents duplicates)
- Always ensures proceedingId is set before adding to collection
- Uses HashSet for mutability instead of immutable Set.of()

### 3. BaseServiceTest.java

**Removed Direct Child Mocks**:
```java
// REMOVED:
@MockitoBean protected DecisionRepository decisionRepository;
@MockitoBean protected MeritsDecisionRepository meritsDecisionRepository;
@MockitoBean protected CertificateRepository certificateRepository;
```

**Rationale**: These repositories are no longer directly injected into ApplicationService. All persistence goes through ApplicationRepository via cascade.

### 4. MeritsDecisionsEntityFactory.java

**Simplified to Let Tests Set Values**:
```java
@Override
public MeritsDecisionEntity createDefault() {
    return MeritsDecisionEntity.builder()
            .id(UUID.randomUUID())
            // Removed: .proceedingId(UUID.randomUUID())
            .build();
}
```

**Why**: Random proceedingId would conflict with test fixtures that set real proceeding entities.

## Cascade Flow

### Creating Application with Proceedings
```
1. ApplicationService.createApplication(request)
2. Create ApplicationEntity, add ProceedingEntities to proceedings set
3. applicationRepository.save(application)
4. Hibernate cascade: all proceedings cascade-persisted
```

### Making Decision with Merits
```
1. ApplicationService.makeDecision(applicationId, request)
2. Load application with decision graph
3. Create MeritsDecisionEntity instances, set proceedingId explicitly
4. Add to decision.meritsDecisions set
5. applicationRepository.save(application)
6. Hibernate cascade:
   a. @PrePersist called on MeritsDecisionEntity
   b. Syncs proceedingId from proceeding if needed
   c. Persists decision with all merits
   d. Persists certificates if granted
```

## Data Flow Diagram

```
HTTP Request (Make Decision)
    ↓
ApplicationService.makeDecision()
    ↓
Fetch ApplicationEntity with Decision Graph
    ↓
Create/Update MeritsDecisionEntity instances
    ↓ setProceedingId() → explicit UUID assignment
    ↓
Add to decision.meritsDecisions (HashSet)
    ↓
applicationRepository.save(application)
    ↓
Hibernate Session
    ├─ @PrePersist hooks on MeritsDecisionEntity
    ├─ Sync proceedingId from proceeding if null
    ├─ INSERT/UPDATE merits_decisions table
    ├─ INSERT/UPDATE decisions table
    └─ Cascade certificates if present
```

## Test Fixture Patterns

### Unit Tests - With Mocks
```java
ApplicationEntity entity = applicationEntityFactory.createDefault(builder ->
    builder.id(expectedId)
);
when(applicationRepository.save(any())).then(i -> {
    ApplicationEntity savedEntity = (ApplicationEntity) i.getArguments()[0];
    if (savedEntity.getId() == null) {
        savedEntity.setId(expectedId);
    }
    return savedEntity;
});
```

### Integration Tests - With Real Database
```java
ApplicationEntity app = persistedDataGenerator.createAndPersist(
    ApplicationEntityGenerator.class,
    builder -> builder.caseworker(caseworker)
);

ProceedingEntity proceeding = persistedDataGenerator.createAndPersist(
    ProceedingsEntityGenerator.class,
    builder -> builder.application(app)
);

MeritsDecisionEntity merit = persistedDataGenerator.createAndPersist(
    MeritsDecisionsEntityGenerator.class,
    builder -> builder.proceeding(proceeding)  // Must set proceeding
);
// @PrePersist will sync proceedingId from proceeding
```

## Performance Considerations

### Database Queries
- **Single Save**: One INSERT/UPDATE on applicationRepository.save()
- **Cascade**: Hibernate handles all related entity persistence
- **Lazy Loading**: proceeding relationship only loaded if accessed

### Memory Usage
- **UUID Field**: 16 bytes (UUID value)
- **Relationship**: Lazy proxy only created if accessed
- **Collection**: HashSet for O(1) lookup on updates

### Optimization Opportunities
1. Use `@EntityGraph(attributePaths = {"decision.meritsDecisions"})` for eager loading when needed
2. Batch operations for bulk merit updates
3. Index on merits_decisions(proceeding_id) for query performance

## Comparison: Before vs. After

### Before (Approach Attempts)
```
Issue: MeritsDecisionEntity with @ManyToOne to ProceedingEntity
Problem: Cascade couldn't persist foreign key relationship properly
Result: "null value in column 'proceeding_id' violates not-null constraint"
```

### After (Approach 2)
```
proceedingId UUID field: Hibernate controls persistence directly
proceeding relationship: Optional for queries, not required for persistence
Result: Clean cascade, @PrePersist syncs values, no constraint violations
```

## Related Patterns

This implementation establishes patterns for:
- **Dual Representation**: UUID + relationship in other aggregate entities
- **Cascade Optimization**: Explicit FK fields + lazy relationships
- **Test Fixture Compatibility**: lifecycle hooks to handle both prod and test scenarios
