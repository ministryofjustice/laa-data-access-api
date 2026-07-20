# ADR-0002: PII Externalization Implementation for Axon Event-Sourced Architecture

**Status:** Proposed  
**Date:** 2026-07-20  
**Deciders:** Architecture Team, Data Protection Officer  
**Supercedes:** Implementation of PII separation previously handled inline with application versioning  
**References:** [ApplicationDataStore](../../data-access-service-axon/src/main/java/uk/gov/justice/laa/dstew/access/command/application/data/ApplicationDataStore.java), [ApplicationAggregate](../../data-access-service-axon/src/main/java/uk/gov/justice/laa/dstew/access/command/application/ApplicationAggregate.java)

---

## Context and Problem Statement

### Regulatory Requirements

The Data Access API persists extensive Personally Identifiable Information (PII):
- Applicant/partner names, dates of birth, national insurance numbers
- Home addresses
- Opponent and children personal details
- Financial means data (income, assets, bank transactions)
- Case narrative (statement of case, urgency descriptions)

**GDPR compliance requirements:**
- **Article 17 (Right to Erasure):** PII must be deletable on request within 30 days
- **Article 5.1(c) (Data Minimization):** Only necessary data retained; PII should be separable
- **Article 5.1(e) (Storage Limitation):** Differential retention periods (e.g., PII 7 years, business data 25 years)
- **Article 32 (Security):** Encryption and access controls for sensitive data

### Current Architecture

The Data Access API uses **immutable event-sourced architecture** (Axon Framework):
- Event stream contains non-PII business events only ✅
- `ApplicationDataPayload` (record) persisted separately in `application_data` table ✅
- Versioning tied to `applicationDataVersion` (counter per application) ✅

**Current Compliance Gaps:**
- ❌ No independent `piiRef` UUID for PII record identity
- ❌ No temporal metadata (`valid_from`, `valid_until`, `status`)
- ❌ No GDPR redaction capability (no cascade delete for all versions)
- ❌ No temporal query support (`findAsOf(timestamp)`)
- ❌ No encryption infrastructure (PII stored as plain JSON)
- ❌ No explicit PII/non-PII separation in data structures
- ❌ Cannot support "show PII as of timestamp T" queries

### Strategy Overview

This ADR implements a **PII Externalization** approach:
1. Extract PII into a separate versioned store with temporal validity tracking
2. Maintain immutable event log (no PII content)
3. Support cascade deletion of all PII versions (GDPR Article 17)
4. Enable temporal queries while respecting redaction state
5. Add optional encryption for production deployments

**Immutability Principle:** Events remain immutable; PII deletions do not alter event stream. Redacted applications return masked data on query; after hard delete, repository-level temporal lookups return empty and query-layer responses remain masked.

---

## Decision Drivers

### Must Have
- **GDPR compliance:** Support hard deletion of all PII versions (Article 17 — Right to Erasure)
- **Event immutability:** Preserve event-sourced architecture benefits (audit, replay, consistency)
- **Backward compatibility:** Existing applications and command handlers continue to work
- **No business logic changes:** Aggregate behavior unchanged
- **Performance:** PII lookup latency < 10ms p99
- **Temporal queries:** "Show application as of timestamp T" must return correct historical PII (or redacted)

### Should Have
- **Encryption:** PII encrypted at rest in production deployments
- **Auditability:** Track all PII access, changes, and redactions
- **Access control:** Separate IAM for PII store (beyond application backend)

### Nice to Have
- **Differential retention:** Delete PII after 7 years; retain business events for 25 years
- **Encryption key rotation:** Zero-downtime key updates
- **Developer experience:** POC mode with plain-text PII for debugging

---

## Considered Options

### Overview

To achieve GDPR compliance while maintaining Axon event sourcing benefits, we considered several approaches for externalizing and versioning PII. The key trade-off: how to balance version-counter semantics (familiar to the codebase) against independent PII identity (required for temporal queries and redaction).

### Option 1: Introduce `piiRef` UUID Alongside Version Counter ✅ **(Selected)**

**Rationale:** 
- Keep `applicationDataVersion` for business logic versioning (familiar to existing code)
- Add independent `piiRef` UUID for PII record identity (required for temporal queries)
- PII updates create new `piiRef` but version counter increments for all state changes
- Enables GDPR redaction: delete all `piiRef` versions while keeping event log intact
- Minimal disruption to existing aggregate and command handler patterns

```
Timeline:
1. Create application (v=0)
  → piiRef: pii-789
  → ApplicationCreatedEvent(appId, v=0, piiRef=pii-789, ...)
  
2. Update decision (v=1)
  → piiRef: unchanged (pii-789)
  → No PII change, so same piiRef
  → ApplicationDecisionMadeEvent(appId, v=1, piiRef=pii-789, ...)
  
3. Update caseworker assignment (v=2)
  → piiRef: unchanged (pii-789)
  → ApplicationAssignedToCaseworkerEvent(appId, v=2, piiRef=pii-789, ...)
  
4. PII corrected by user (v=3)
  → piiRef: NEW (pii-ABC)
  → Triggered by an existing application command that changes PII-bearing content
  → ApplicationPiiUpdatedEvent(appId, v=3, oldPiiRef=pii-789, newPiiRef=pii-ABC, ...)
  
5. GDPR erasure (v=4)
  → piiRef: NULL
  → ApplicationPiiRedactedEvent(appId, v=4, reason=..., actor=...)
```

**Pros:**
- Version counter continues to sequence all state changes (familiar to developers)
- PII identity tracked independently via `piiRef`
- Minimal disruption to existing `ApplicationDataVersion` semantics
- Events always carry `piiRef` (even if unchanged or null after redaction)
- Backward compatible: new events carry extra field; old aggregates unaware

**Cons:**
- Dual versioning concept: version counter + piiRef UUID (slight cognitive load)
- Must update all events to carry `piiRef` field

**Verdict:** **Selected** — preserves existing version counter semantics while introducing PII identity tracking required for GDPR compliance (temporal queries, cascade deletion, redaction events).

---

### Option 2: Replace Version Counter with `piiRef` UUID ❌

**Approach:** Remove `applicationDataVersion` counter entirely; use `piiRef` as sole version identifier.

**Cons:**
- Breaks existing aggregate state tracking (stores `applicationVersion` and `applicationDataVersion`)
- All event handlers must be rewritten
- Projection logic refactored
- Command versioning assumptions break (e.g., `MakeApplicationDecisionCommand.expectedApplicationVersion`)
- High migration complexity

**Verdict:** Rejected — too disruptive.

---

### Option 3: PII Versioning in Separate Event Stream ❌

**Approach:** Create dedicated `ApplicationPiiEvents` stream for all PII changes; main stream carries non-PII only.

**Cons:**
- Axon doesn't natively support multi-stream aggregates
- Replay becomes complex (must replay both streams)
- Consistency boundaries unclear
- Read model must join two event streams

**Verdict:** Rejected — architectural complexity.

---

## Decision Outcome

**Chosen Option:** **Option 1 — Introduce `piiRef` UUID Alongside Version Counter**

### Implementation Strategy

This decision enables GDPR compliance through:
1. **Independent PII identity:** Each PII version has a unique `piiRef` UUID (separate from `applicationDataVersion`)
2. **Temporal tracking:** PII records carry `validFrom`/`validUntil` for "as of" queries
3. **Cascade deletion:** All PII versions deleted via single `deleteAllForApplication()` call
4. **Immutable audit trail:** Redaction events recorded; event stream unchanged
5. **Encryption-ready:** POC/production modes via Spring profiles

### Implementation Details

#### 1. New `piiRef` Field and Temporal Metadata

**`ApplicationData` Entity Refactoring:**

```java
@Entity
@Table(name = "application_data")
public class ApplicationData {
  @EmbeddedId 
  private ApplicationDataId id;  // (applicationId, version) — unchanged
  
  @Column(name = "pii_ref")     // ← NEW: UUID for PII identity
  private UUID piiRef;
  
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false)
  private ApplicationDataPayload payload;
  
  @Column(name = "payload_hash")
  private String payloadHash;
  
  @Column(name = "created_at")
  private Instant createdAt;
  
  // ← NEW: Temporal validity for PII versions
  @Column(name = "pii_valid_from")
  private Instant piiValidFrom;
  
  @Column(name = "pii_valid_until")
  private Instant piiValidUntil;
  
  // ← NEW: PII status tracking
  @Column(name = "pii_status")
  @Enumerated(EnumType.STRING)
  private PiiStatus piiStatus;  // PRESENT, REDACTED
  
  // ← NEW: Redaction audit
  @Column(name = "redacted_at")
  private Instant redactedAt;
  
  @Column(name = "redacted_by")
  private String redactedBy;
  
  @Column(name = "redaction_reason")
  private String redactionReason;
}

public enum PiiStatus {
  PRESENT,
  REDACTED
}
```

**Rationale:** Keep composite key `(applicationId, version)` to preserve existing logic; add `piiRef` as secondary identifier for PII record tracking. Temporal metadata enables GDPR compliance.

---

#### 2. Explicit PII/Non-PII Split in Parser Output

**`ParsedAppContentDetails` Enhancement:**

```java
@Builder
public record ParsedAppContentDetails(
    // ← Existing non-PII fields (unchanged)
    ApplicationContent applicationContent,    // ← Full content (includes PII)
    UUID applyApplicationId,
    CategoryOfLaw categoryOfLaw,
    MatterType matterType,
    Instant submittedAt,
    String officeCode,
    Boolean usedDelegatedFunctions,
    List<Proceeding> proceedings,
    List<LinkedApplication> allLinkedApplications,
    
    // ← NEW: Explicit PII identity for audit
    UUID piiRef) {}  // ← UUID for the PII record being stored
```

**Or (preferred for future):**

```java
@Builder
public record ParsedAppContentDetails(
    // Non-PII fields
    UUID applyApplicationId,
    CategoryOfLaw categoryOfLaw,
    MatterType matterType,
    // ...
    
    // PII fields (grouped for clarity)
    ApplicationContent applicationContent,    // ← Contains all PII
    UUID piiRef) {}                          // ← PII identity
```

**Rationale:** No breaking change to parser; add `piiRef` to output so it propagates through command handler. Long-term: consider splitting into `ParsedNonPii` and `ParsedPii` records for clearer semantics.

---

#### 3. Events Carry `piiRef` Field

**All Events Updated:**

```java
// ApplicationCreatedEvent
public record ApplicationCreatedEvent(
    UUID applicationId,
    long applicationDataVersion,
    String requestFingerprint,
    UUID piiRef,                      // ← NEW
    String status,
    int schemaVersion,
    // ... other non-PII fields
    List<UUID> associatedApplicationIds) {}

// ApplicationDecisionMadeEvent
public record ApplicationDecisionMadeEvent(
    UUID applicationId,
    long applicationVersion,
    long applicationDataVersion,
    UUID piiRef,                      // ← NEW: carries unchanged
    String overallDecision,
    Boolean autoGranted,
    Instant occurredAt) {}

// ApplicationAssignedToCaseworkerEvent
public record ApplicationAssignedToCaseworkerEvent(
    UUID applicationId,
    long applicationVersion,
    long applicationDataVersion,
    UUID piiRef,                      // ← NEW: carries unchanged
    UUID caseworkerId,
    Instant occurredAt) {}

// ApplicationUnassignedFromCaseworkerEvent
public record ApplicationUnassignedFromCaseworkerEvent(
    UUID applicationId,
    long applicationVersion,
    long applicationDataVersion,
    UUID piiRef,                      // ← NEW: carries unchanged
    Instant occurredAt) {}
```

**NEW: PII Change Events**

```java
// When PII is updated as a side effect of an existing application command (e.g., correction)
public record ApplicationPiiUpdatedEvent(
    UUID applicationId,
    long applicationDataVersion,
    UUID oldPiiRef,
    UUID newPiiRef,
    Instant occurredAt) {}

// When PII is redacted (GDPR erasure)
public record ApplicationPiiRedactedEvent(
    UUID applicationId,
    long applicationVersion,
    String reason,
    String actor,
    Instant occurredAt) {}
```

**Rationale:** Events become the source of truth for `piiRef` throughout application lifecycle. Projection can track current PII reference. PII corrections are handled inside normal application command flows rather than through a dedicated public PII update command. Backward compatibility: existing events processed by new code (optional `piiRef` handling).

---

#### 4. Aggregate State Tracks `piiRef`

**`ApplicationAggregate` Enhancements:**

```java
@Aggregate
public class ApplicationAggregate {
  @AggregateIdentifier 
  private UUID applicationId;
  
  private long applicationVersion;
  private long applicationDataVersion;
  private UUID currentPiiRef;           // ← NEW: track current PII identity
  private PiiStatus piiStatus;          // ← NEW: PRESENT or REDACTED
  
  // ... existing fields
  
  @EventSourcingHandler
  void on(ApplicationCreatedEvent event) {
    this.applicationId = event.applicationId();
    this.applicationDataVersion = event.applicationDataVersion();
    this.currentPiiRef = event.piiRef();          // ← NEW
    this.piiStatus = PiiStatus.PRESENT;           // ← NEW
    // ... existing handlers
  }
  
  @EventSourcingHandler
  void on(ApplicationPiiUpdatedEvent event) {
    this.applicationDataVersion = event.applicationDataVersion();
    this.currentPiiRef = event.newPiiRef();       // ← NEW
    // piiStatus remains PRESENT
  }
  
  @EventSourcingHandler
  void on(ApplicationPiiRedactedEvent event) {
    this.applicationVersion = event.applicationVersion();
    this.currentPiiRef = null;                    // ← NEW
    this.piiStatus = PiiStatus.REDACTED;          // ← NEW
  }
}
```

**Rationale:** Aggregate remembers which PII version is current. Enables validation (e.g., prevent operations on redacted applications).

---

#### 5. New `ApplicationPiiRepository` for Temporal Queries

**Separate PII-Specific Repository:**

```java
@Repository
public interface ApplicationPiiRepository {
  
  /**
   * Persists a new or updated PII version.
   * If piiRef already exists, performs idempotent upsert.
   * 
   * @param applicationId application identifier
   * @param piiRef unique PII version identifier
   * @param payload the PII-containing payload (may be encrypted in production)
   * @param validFrom when this version became current
   * @return the stored reference
   */
  UUID save(
      UUID applicationId,
      UUID piiRef,
      ApplicationDataPayload payload,
      Instant validFrom);
  
  /**
   * Retrieves the current (most recent) PII version for an application.
   * Returns empty if PII has been redacted.
   * 
   * @param applicationId application identifier
   * @return current payload or empty if redacted
   */
  Optional<ApplicationDataPayload> findCurrent(UUID applicationId);
  
  /**
   * Retrieves the PII version valid at a specific timestamp.
   * Enables temporal queries: "what PII did this have on June 1st?"
   * Returns empty if PII was redacted (all versions deleted).
   * 
   * @param applicationId application identifier
   * @param asOf target timestamp
   * @return payload valid at that time, or empty
   */
  Optional<ApplicationDataPayload> findAsOf(UUID applicationId, Instant asOf);
  
  /**
   * Hard-deletes ALL PII versions for an application.
   * Called by GDPR erasure request; irreversible.
   * 
   * @param applicationId application to redact
   * @param reason erasure reason (for audit)
   * @param actor user requesting erasure
   */
  void deleteAllForApplication(UUID applicationId, String reason, String actor);
  
  /**
   * Counts PII versions for an application (for monitoring/debugging).
   * 
   * @param applicationId application identifier
   * @return number of versions
   */
  long countVersions(UUID applicationId);
}
```

**Rationale:** Separates PII query logic from general application data. Supports temporal queries (show "application as of timestamp T") and GDPR redaction (cascade delete all versions).

---

#### 6. Command Handler: Write `piiRef` to New Repository

**`ApplicationDataStore` Enhancement:**

```java
@Component
public class ApplicationDataStore {

  private final ApplicationDataRepository repository;
  private final ApplicationPiiRepository piiRepository;  // ← NEW

  public ApplicationDataStore(
      ApplicationDataRepository repository,
      ApplicationPiiRepository piiRepository) {
    this.repository = repository;
    this.piiRepository = piiRepository;
  }

  /**
   * Appends an immutable version of an application's data (PII + non-PII).
   * Writes PII to separate repository; non-PII to application_data.
   * 
   * @param applicationId the application identifier
   * @param version the data version
   * @param piiRef the PII record identifier
   * @param details the application details to persist
   * @return the fingerprint of the serialised request
   */
  public String append(
      UUID applicationId,
      long version,
      UUID piiRef,                              // ← NEW parameter
      ApplicationCreationDetails details) {
    
    String fingerprint = fingerprint(details.serialisedRequest());
    
    // Step 1: Write PII to separate repository (must succeed first)
    piiRepository.save(applicationId, piiRef, 
        ApplicationDataPayload.from(details), details.occurredAt());
    
    // Step 2: Write non-PII application_data record
    repository.saveAndFlush(
        ApplicationData.builder()
            .id(new ApplicationDataId(applicationId, version))
            .piiRef(piiRef)                    // ← NEW: store reference
            .payload(ApplicationDataPayload.from(details))
            .payloadHash(fingerprint)
            .createdAt(details.occurredAt())
            .piiValidFrom(details.occurredAt())   // ← NEW: temporal tracking
            .piiStatus(PiiStatus.PRESENT)         // ← NEW
            .build());
    
    return fingerprint;
  }

  // ... existing overloaded methods also updated with piiRef
}
```

**Rationale:** PII write happens inside command handler before event emission. If PII repository fails, command is rejected and caller retries. This is intentional — partial writes are unacceptable.

---

#### 7. Command Handler: Redaction

**NEW: Redaction Command**

```java
public record RedactApplicationPiiCommand(
    UUID applicationId,
    long expectedApplicationVersion,
    String reason,
    String actor,
    Instant occurredAt) {}
```

**Aggregate Handler:**

```java
@Aggregate
public class ApplicationAggregate {
  
  @CommandHandler
  void handle(RedactApplicationPiiCommand command) {
    if (command.expectedApplicationVersion() != applicationVersion) {
      throw new ApplicationVersionConflictException(
          applicationId, command.expectedApplicationVersion());
    }
    
    if (piiStatus == PiiStatus.REDACTED) {
      // Idempotent: already redacted
      return;
    }
    
    apply(new ApplicationPiiRedactedEvent(
        applicationId,
        applicationVersion + 1,
        command.reason(),
        command.actor(),
        command.occurredAt()));

    // Execute irreversible deletion only after event store commit succeeds.
    CurrentUnitOfWork.get().afterCommit(uow ->
        piiRepository.deleteAllForApplication(
            applicationId,
            command.reason(),
            command.actor()));
  }
}
```

**Rationale:** Enables GDPR Article 17 (Right to Erasure) compliance. All PII versions deleted; event log untouched.

---

#### 8. Query Path: PII Join at Read Time

**Projection: Store Reference Only**

```java
@Entity
@Table(name = "application_projections")
public class ApplicationProjection {
  @Id
  private UUID id;
  
  @Column(name = "current_pii_ref")
  private UUID currentPiiRef;                  // ← Reference only, no PII content
  
  @Column(name = "pii_status")
  @Enumerated(EnumType.STRING)
  private PiiStatus piiStatus;                 // ← PRESENT or REDACTED
  
  @Column(name = "category_of_law")
  private String categoryOfLaw;                // ← Non-PII
  
  // ... other non-PII fields
}
```

**Query Handler: Join at Read Time**

```java
@Service
public class ApplicationQueryService {
  
  private final ApplicationProjectionRepository projectionRepository;
  private final ApplicationPiiRepository piiRepository;
  
  public ApplicationReadModel getApplication(UUID applicationId) {
    // Load projection (fast, no PII)
    ApplicationProjection projection = projectionRepository.findById(applicationId)
        .orElseThrow(() -> new ResourceNotFoundException(...));
    
    // Join PII if available (optional, may be redacted)
    Optional<ApplicationDataPayload> pii = 
        piiRepository.findCurrent(applicationId);
    
    // Build DTO with graceful degradation for redacted PII
    return ApplicationReadModel.builder()
        .id(applicationId)
        .categoryOfLaw(projection.getCategoryOfLaw())      // Non-PII
        .applicantName(pii
            .map(p -> p.applicationContent().getApplicant().getFirstName())
            .orElse("[REDACTED]"))                         // Graceful fallback
        .piiStatus(projection.getPiiStatus())
        .build();
  }
  
  public ApplicationReadModel getApplicationAsOf(UUID applicationId, Instant asOf) {
    // Temporal query: show state at specific time
    ApplicationProjection projection = projectionRepository.findById(applicationId)...
    
    Optional<ApplicationDataPayload> piiAtTime = 
        piiRepository.findAsOf(applicationId, asOf);
    
    return buildModel(projection, piiAtTime);
  }
}
```

**Rationale:**
- Projection contains only non-PII; no sensitive data replicated
- Query-time join adds ~5-10ms latency (acceptable)
- Redacted applications still queryable (non-PII intact)
- Temporal queries supported
- Access control enforced at query layer

---

#### 9. Encryption: POC vs Production

**POC Mode (Development):**

```sql
-- application_data and pii_records tables store plain JSON
-- Developers can query PII directly for testing
CREATE TABLE application_data (
  application_id UUID NOT NULL,
  version BIGINT NOT NULL,
  pii_ref UUID NOT NULL,
  payload JSONB NOT NULL,            -- Plain JSON
  payload_hash VARCHAR(64) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  pii_valid_from TIMESTAMP,
  pii_valid_until TIMESTAMP,
  pii_status VARCHAR(20) NOT NULL DEFAULT 'PRESENT',
  redacted_at TIMESTAMP,
  redacted_by VARCHAR(255),
  redaction_reason TEXT,
  PRIMARY KEY (application_id, version),
  INDEX idx_pii_ref (pii_ref),
  INDEX idx_pii_temporal (application_id, pii_valid_from, pii_valid_until)
);
```

**Production Mode:**

```java
@Component
@Profile("production")
public class EncryptedPiiRepository implements ApplicationPiiRepository {
  
  @Autowired
  private EncryptionService encryptionService;
  
  @Override
  public UUID save(UUID applicationId, UUID piiRef, 
                   ApplicationDataPayload payload, Instant validFrom) {
    String json = objectMapper.writeValueAsString(payload);
    byte[] encrypted = encryptionService.encrypt(json);
    
    jdbcTemplate.update(
      "INSERT INTO pii_records (pii_ref, application_id, encrypted_payload, valid_from, pii_status) " +
      "VALUES (?, ?, ?, ?, 'PRESENT')",
      piiRef, applicationId, encrypted, validFrom);
    
    return piiRef;
  }
  
  @Override
  public Optional<ApplicationDataPayload> findCurrent(UUID applicationId) {
    try {
      byte[] encrypted = jdbcTemplate.queryForObject(
        "SELECT encrypted_payload FROM pii_records " +
        "WHERE application_id = ? AND pii_valid_until IS NULL AND pii_status = 'PRESENT'",
        byte[].class, applicationId);
      
      String json = encryptionService.decrypt(encrypted);
      return Optional.of(objectMapper.readValue(json, ApplicationDataPayload.class));
    } catch (EmptyResultDataAccessException e) {
      return Optional.empty();  // Redacted or missing
    }
  }
  
  @Override
  public void deleteAllForApplication(UUID applicationId, String reason, String actor) {
    Instant now = Instant.now();
    
    // Hard delete all versions
    jdbcTemplate.update(
      "DELETE FROM pii_records WHERE application_id = ?",
      applicationId);
    
    // Mark in audit table (for compliance)
    jdbcTemplate.update(
      "INSERT INTO pii_redaction_audit (application_id, reason, actor, redacted_at) " +
      "VALUES (?, ?, ?, ?)",
      applicationId, reason, actor, now);
  }
}
```

**Rationale:** Encryption infrastructure conditional on Spring profile. POC mode transparent to developers; production mode encrypts at rest.

---

## Schema Migration

### New Tables

```sql
-- Separate PII records table (POC or encrypted)
CREATE TABLE pii_records (
  pii_ref UUID PRIMARY KEY,
  application_id UUID NOT NULL,
  encrypted_payload BYTEA NOT NULL,        -- Encrypted in production, JSON in POC
  pii_valid_from TIMESTAMP NOT NULL DEFAULT now(),
  pii_valid_until TIMESTAMP,
  pii_status VARCHAR(20) NOT NULL DEFAULT 'PRESENT',
  created_by VARCHAR(255),
  
  INDEX idx_pii_application (application_id),
  INDEX idx_pii_temporal (application_id, pii_valid_from, pii_valid_until)
);

-- Audit table for redactions
CREATE TABLE pii_redaction_audit (
  id BIGSERIAL PRIMARY KEY,
  application_id UUID NOT NULL,
  reason TEXT,
  actor VARCHAR(255),
  redacted_at TIMESTAMP NOT NULL DEFAULT now(),
  
  INDEX idx_audit_application (application_id)
);
```

### Existing Table Changes

```sql
-- Extend application_data with new columns
ALTER TABLE application_data
ADD COLUMN pii_ref UUID,                         -- NEW: PII record identity
ADD COLUMN pii_valid_from TIMESTAMP,             -- NEW: temporal validity start
ADD COLUMN pii_valid_until TIMESTAMP,            -- NEW: temporal validity end
ADD COLUMN pii_status VARCHAR(20) DEFAULT 'PRESENT',  -- NEW: PRESENT or REDACTED
ADD COLUMN redacted_at TIMESTAMP,                -- NEW: audit
ADD COLUMN redacted_by VARCHAR(255),             -- NEW: audit
ADD COLUMN redaction_reason TEXT,                -- NEW: audit
ADD INDEX idx_pii_ref (pii_ref),
ADD INDEX idx_pii_temporal (application_id, pii_valid_from, pii_valid_until);
```

---

## Consequences

### Positive

- ✅ Maintains existing `applicationDataVersion` semantics (minimal code disruption)
- ✅ Introduces `piiRef` UUID for independent PII identity tracking
- ✅ Enables temporal queries (`findAsOf`)
- ✅ Supports GDPR erasure via cascade delete
- ✅ Query-time PII joins degrade gracefully when redacted
- ✅ Event log remains immutable
- ✅ POC/production encryption modes support developer experience
- ✅ Backward compatible: existing events processed by new aggregate handlers
- ✅ Audit trail for all redactions

### Negative

- ⚠️ PII repository is a hard write dependency (commands fail if unavailable)
- ⚠️ Dual versioning concept (counter + UUID) adds cognitive load
- ⚠️ All events must carry `piiRef` field (increases event size slightly)
- ⚠️ Projection rebuild requires PII join at replay time (slight performance overhead)
- ⚠️ Hard delete is irreversible: historical PII cannot be recovered post-redaction

### Neutral

- ➡️ Query-time PII lookup adds ~5-10ms per query (acceptable, cacheable)
- ➡️ Storage overhead: additional PII versions per application
- ➡️ Encryption key management operational complexity (production only)

---

## Implementation Phases

### Phase 1: Schema and Repository (Weeks 1-2)
- [ ] Create `pii_records` table
- [ ] Add temporal columns to `application_data`
- [ ] Implement `ApplicationPiiRepository` interface (in-memory mock first)
- [ ] Add `piiRef` field to `ApplicationData` entity

### Phase 2: Event Changes (Weeks 2-3)
- [ ] Add `piiRef` field to all application events
- [ ] Create `ApplicationPiiUpdatedEvent` and `ApplicationPiiRedactedEvent`
- [ ] Update event handlers in `ApplicationAggregate`
- [ ] Implement redaction command handler

### Phase 3: Parser and Command Handler (Week 3)
- [ ] Enhance `ParsedAppContentDetails` with `piiRef`
- [ ] Update `ApplicationDataStore` to write both repositories
- [ ] Inject `ApplicationPiiRepository` into aggregate
- [ ] Update `CreateApplicationCommand` handler
- [ ] Add `piiRef` rotation as a side effect of existing application content update handlers

### Phase 4: Query Path (Week 4)
- [ ] Enhance `ApplicationProjection` with `currentPiiRef`
- [ ] Implement `ApplicationPiiRepository.findCurrent()` and `findAsOf()`
- [ ] Add query handler for PII join
- [ ] Implement graceful degradation for redacted PII

### Phase 5: Encryption (Week 5)
- [ ] Implement `EncryptionService` (AES-256-GCM)
- [ ] Create production `ApplicationPiiRepository` implementation
- [ ] Add Spring profile management (POC vs production)
- [ ] Integration tests for encrypted storage

### Phase 6: Testing and Rollout (Weeks 6-7)
- [ ] Integration tests for all new components
- [ ] Performance benchmarks (latency, throughput)
- [ ] Canary deployment and monitoring

---

## Testing Requirements

### Unit Tests

- [ ] `ApplicationPiiRepository.findAsOf()` returns correct version for timestamp
- [ ] `ApplicationPiiRepository.deleteAllForApplication()` cascades all versions
- [ ] `ApplicationAggregate.handle(RedactApplicationPiiCommand)` is idempotent
- [ ] Fingerprinting unchanged (still works on non-PII)

### Integration Tests

- [ ] Create application → PII stored with `piiRef` → Event has `piiRef`
- [ ] Update PII → New `piiRef` created → Old version closed with `validUntil`
- [ ] Redact application → All versions deleted → Projection shows `REDACTED`
- [ ] Temporal query pre-redaction → Returns historical PII
- [ ] Temporal query post-redaction → Repository lookup empty for all dates; query response returns masked fields
- [ ] Projection rebuild → Correct `currentPiiRef` from events

### Performance Tests

- [ ] Query-time PII join < 10ms p99
- [ ] Temporal query < 50ms p99
- [ ] Projection rebuild 1M events → < 2 minutes

### Security Tests

- [ ] PII encrypted at rest (production mode)
- [ ] Encryption key rotation zero-downtime
- [ ] Backup contains no plaintext PII

---

## Open Questions

- [ ] **Redaction reasons:** Standardized taxonomy for `ApplicationPiiRedactedEvent.reason`
- [ ] **PII discovery:** Automated scanner to detect PII accidentally in free-text fields (case notes)
- [ ] **Temporal query performance:** Cache strategy for frequently accessed historical versions
- [ ] **Encryption key rotation:** Frequency and zero-downtime procedure

---

## References

**Codebase:**
- [ApplicationDataStore](../../data-access-service-axon/src/main/java/uk/gov/justice/laa/dstew/access/command/application/data/ApplicationDataStore.java) — Current PII persistence logic
- [ApplicationAggregate](../../data-access-service-axon/src/main/java/uk/gov/justice/laa/dstew/access/command/application/ApplicationAggregate.java) — Event-sourced aggregate root
- [ApplicationDataPayload](../../data-access-service-axon/src/main/java/uk/gov/justice/laa/dstew/access/command/application/data/ApplicationDataPayload.java) — Sensitive data record
- [ApplicationContentParser](../../data-access-service-axon/src/main/java/uk/gov/justice/laa/dstew/access/applicationcontent/ApplicationContentParser.java) — Parser extracting application details

**External:**
- [GDPR Article 17: Right to Erasure](https://gdpr-info.eu/art-17-gdpr/)
- [ICO Data Protection by Design and Default](https://ico.org.uk/for-organisations/guide-to-data-protection/guide-to-the-general-data-protection-regulation-gdpr/accountability-and-governance/data-protection-by-design-and-default/)
- [Axon Framework Event Sourcing Guide](https://docs.axoniq.io/reference-guide/axon-framework/events/event-sourcing)
- [NIST SP 800-57: Key Management](https://csrc.nist.gov/publications/detail/sp/800-57-part-1/rev-5/final)

---

## Approval

| Role | Name | Date | Decision |
|------|------|------|----------|
| Lead Architect | _TBD_ | _TBD_ | _Pending_ |
| Data Protection Officer | _TBD_ | _TBD_ | _Pending_ |
| Tech Lead | _TBD_ | _TBD_ | _Pending_ |

---

## Changelog

- **2026-07-20:** Initial draft (Proposed status) — Complete self-contained ADR for PII externalization implementation in Axon event-sourced codebase










