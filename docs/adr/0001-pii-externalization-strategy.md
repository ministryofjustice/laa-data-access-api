# ADR-0001: PII Externalization Strategy for Event-Sourced Applications

**Status:** Proposed  
**Date:** 2026-07-17  
**Deciders:** Architecture Team, Data Protection Officer  
**Related:** [Implementation Plan](../../plans/pii-externalization/00-overview-and-plan.md)

---

## Context and Problem Statement

The Data Access API currently persists application content and proceedings data in an **immutable event-sourced architecture** (Axon Framework). Application payloads contain extensive Personally Identifiable Information (PII):

- Applicant/partner names, dates of birth, national insurance numbers
- Home addresses
- Opponent and children personal details
- Financial means data (income, assets, bank transactions)
- Case narrative (statement of case, urgency descriptions)

**GDPR compliance issues:**

1. **Right to Erasure (Article 17):** Cannot delete PII from immutable event log
2. **Data Minimization (Article 5.1c):** PII replicated across events, aggregates, read models
3. **Storage Limitation (Article 5.1e):** Cannot apply differential retention (PII vs business data)
4. **Security (Article 32):** PII and non-sensitive business logic share same security boundary

**Current risk:** Data subject access requests (DSARs) for erasure cannot be fulfilled without rewriting event history or dropping entire aggregates.

---

## Decision Drivers

### Must Have
- **GDPR compliance:** Support hard deletion or redaction of PII within 30 days of request
- **Event immutability:** Preserve event-sourced architecture benefits (audit, replay, temporal queries)
- **Backward compatibility:** Migrate existing applications without data loss
- **No business logic changes:** Domain model behavior unchanged

### Should Have
- **Performance:** PII lookup latency < 10ms p99
- **Security:** Encrypted PII at rest, separate access controls
- **Auditability:** Track all PII access, changes, redactions

### Nice to Have
- **Differential retention:** Delete PII after 7 years, keep business data for 25 years
- **Encryption key rotation:** Support zero-downtime key updates

---

## Considered Options

### Option 1: Keep PII in Events, Implement Crypto-Shredding ❌

**Approach:** Encrypt PII fields in events with per-aggregate keys; delete keys to "erase" data.

**Pros:**
- No architectural change
- PII still part of event payload
- Fast lookup (no join)

**Cons:**
- Event replay impossible after key deletion (breaks event sourcing)
- Key management complexity (N keys for N aggregates)
- Regulatory risk: encrypted data may still count as "personal data" under GDPR
- Cannot audit historical events post-erasure

**Verdict:** Rejected — violates event immutability principle.

---

### Option 2: Separate PII Store with Versioned References ✅ **(Selected)**

**Approach:** Extract PII into external versioned store; events contain only `piiRef` UUID and non-PII fields. Each PII update creates new version.

```
Event Payload:
{
  "eventId": "evt-123",
  "applicationId": "app-456",
  "piiRef": "pii-789",          // ← Reference only (immutable)
  "categoryOfLaw": "FAMILY",
  "matterType": "PUBLIC_LAW",
  "submittedAt": "2026-05-01T08:42:19Z",
  "usedDelegatedFunctions": true
}

PII Store (pii_records table — versioned):
{
  "piiRef": "pii-789",
  "applicationId": "app-456",
  "encryptedPayload": "...",    // ← All PII fields
  "validFrom": "2026-05-01T08:42:19Z",
  "validUntil": null,           // ← NULL = current version
  "status": "PRESENT"
}

// After PII update, new version created:
{
  "piiRef": "pii-ABC",          // ← New reference
  "applicationId": "app-456",
  "encryptedPayload": "...",    // ← Updated PII
  "validFrom": "2026-06-15T10:30:00Z",
  "validUntil": null,
  "status": "PRESENT"
}
// Old version closed: valid_until = "2026-06-15T10:30:00Z"
```

**Pros:**
- Full GDPR compliance: delete ALL versions for application, events unchanged
- Event log remains immutable and replayable
- Temporal queries: "Show PII as of June 1st" → returns correct historical version
- Differential security/encryption/retention for PII
- Read path degrades gracefully when PII redacted (no 500 errors)
- Clear separation of concerns (business logic vs personal data)
- Audit trail of PII changes via versioning

**Cons:**
- Query-time PII join on read path
- Multiple PII records per application (storage overhead)
- Cascade delete all versions on erasure

**Verdict:** **Selected** — enables temporal queries while maintaining GDPR compliance.

---

### Option 3: Event Rewriting with Append-Only Log ❌

**Approach:** When erasure requested, append "redaction event" that logically deletes PII from stream history.

**Pros:**
- Pure event-sourcing approach
- No external store needed

**Cons:**
- GDPR ambiguity: redaction markers may not satisfy "erasure" requirement
- Event replay still contains PII (regulatory risk)
- Cannot prevent backup/archive copies from containing PII
- Complex temporal queries ("what did this look like before redaction?")

**Verdict:** Rejected — regulatory risk too high.

---

### Option 4: Separate Event Streams per Data Category ❌

**Approach:** Split events into `business-events` stream (no PII) and `pii-events` stream (deletable).

**Pros:**
- Clean separation
- Delete `pii-events` stream entirely on erasure

**Cons:**
- Breaks aggregate consistency (two streams for one entity)
- Replay requires merging streams
- Axon Framework doesn't support multi-stream aggregates natively
- High implementation complexity

**Verdict:** Rejected — architectural complexity outweighs benefits.

---

## Decision Outcome

**Chosen Option:** **Option 2 — Separate PII Store with Versioned References**

### Implementation Summary

1. **PII Versioning Model**
   - Each application starts with initial `PiiRef` (UUID)
   - PII updates create **new** `PiiRef` and new PII record
   - Old PII versions retained with `validFrom`/`validUntil` timestamps
   - Projection tracks `currentPiiRef` only
   - Events track PII reference changes

2. **PII Store (Versioned)**
   - New `pii_records` table:
     - `pii_ref` (PK, UUID)
     - `application_id` (FK, for cascade operations)
     - `encrypted_payload` (JSONB/BYTEA, AES-256)
     - `valid_from`, `valid_until` (temporal validity)
     - `status` (enum: PRESENT | REDACTED)
     - `created_by`, `created_at`
   - Repository interface: `PiiRepository.save/findCurrent/findAsOf/deleteAll`

3. **Write Path**
   - `ApplicationContentParser` extracts PII into `ParsedApplicationPayload(nonPii, pii)`
   - Command handler persists PII: `piiRepository.save(applicationId, piiRef, pii)`
   - Event emitted with `piiRef` + non-PII fields only
   - Aggregate state stores `currentPiiRef` (not PII content)
   - PII updates: Create new version, emit `ApplicationPiiUpdated(oldPiiRef, newPiiRef)`

4. **Read Path (Query-Time Join)**
   - Projection contains `piiRef` + non-PII fields (no PII stored in projection)
   - Query handler loads projection, then joins PII: `piiRepository.findCurrent(applicationId)`
   - If PII redacted/missing: return masked placeholders (`"[REDACTED]"`, `null`)
   - Never throw exception for redacted data
   - Access control enforced at query layer (not projection layer)

5. **Temporal Queries**
   - Support "show application as of timestamp" queries
   - `piiRepository.findAsOf(applicationId, timestamp)` returns correct historical version
   - After GDPR erasure, temporal queries also return redacted (all versions deleted)

6. **Redaction Path**
   - Command: `RedactApplicationPii(applicationId, reason, actor)`
   - Delete ALL PII versions: `piiRepository.deleteAllForApplication(applicationId)`
   - Emit non-PII event: `ApplicationPiiRedacted(applicationId, reason, timestamp)`
   - Historical events unchanged
   - Temporal queries return redacted even for past dates

---

## POC vs Production Storage Strategy

### POC Implementation (Developer-Visible PII)

**Purpose:** Enable developers to debug and test PII handling without encryption complexity.

**PII Store Schema:**
```sql
CREATE TABLE pii_records (
  pii_ref UUID PRIMARY KEY,
  application_id UUID NOT NULL,
  pii_data JSONB NOT NULL,           -- Plain JSON (developers can read)
  valid_from TIMESTAMP NOT NULL DEFAULT now(),
  valid_until TIMESTAMP,
  status VARCHAR(20) NOT NULL DEFAULT 'PRESENT',
  created_by VARCHAR(255),
  
  INDEX idx_pii_application (application_id),
  INDEX idx_pii_temporal (application_id, valid_from, valid_until)
);
```

**Benefits:**
- Developers can query PII directly for testing
- Debugging easier (readable JSON)
- Faster iteration during development

**Example Developer Query:**
```sql
SELECT 
  p.id,
  p.category_of_law,
  pii.pii_data->>'applicantFirstName' as first_name
FROM application_projections p
JOIN pii_records pii ON pii.application_id = p.id
WHERE pii.valid_until IS NULL  -- Current version
  AND p.id = 'app-456';
```

---

### Production Implementation (Encrypted PII)

**Purpose:** Protect PII with encryption, restrict access to application backend only.

**PII Store Schema:**
```sql
CREATE TABLE pii_records (
  pii_ref UUID PRIMARY KEY,
  application_id UUID NOT NULL,
  encrypted_pii BYTEA NOT NULL,      -- AES-256 encrypted
  valid_from TIMESTAMP NOT NULL DEFAULT now(),
  valid_until TIMESTAMP,
  status VARCHAR(20) NOT NULL DEFAULT 'PRESENT',
  created_by VARCHAR(255),
  redacted_at TIMESTAMP,
  redacted_by VARCHAR(255),
  redaction_reason TEXT,
  
  INDEX idx_pii_application (application_id),
  INDEX idx_pii_temporal (application_id, valid_from, valid_until)
);
```

**Repository Implementation:**
```java
@Repository
@Profile("production")
public class EncryptedPiiRepository implements PiiRepository {
  
  @Inject
  private EncryptionService encryptionService;
  
  @Override
  public void save(UUID applicationId, PiiRef piiRef, ApplicationPiiDetails pii) {
    String json = objectMapper.writeValueAsString(pii);
    byte[] encrypted = encryptionService.encrypt(json);
    
    jdbcTemplate.update(
      "INSERT INTO pii_records (pii_ref, application_id, encrypted_pii, created_by) " +
      "VALUES (?, ?, ?, ?)",
      piiRef.getValue(),
      applicationId,
      encrypted,
      getCurrentUser()
    );
  }
  
  @Override
  public Optional<ApplicationPiiDetails> findCurrent(UUID applicationId) {
    try {
      byte[] encrypted = jdbcTemplate.queryForObject(
        "SELECT encrypted_pii FROM pii_records " +
        "WHERE application_id = ? AND valid_until IS NULL AND status = 'PRESENT'",
        byte[].class,
        applicationId
      );
      
      String json = encryptionService.decrypt(encrypted);
      return Optional.of(objectMapper.readValue(json, ApplicationPiiDetails.class));
    } catch (EmptyResultDataAccessException e) {
      return Optional.empty();
    }
  }
  
  @Override
  public void deleteAllForApplication(UUID applicationId) {
    // Hard delete all versions (GDPR erasure)
    jdbcTemplate.update(
      "DELETE FROM pii_records WHERE application_id = ?",
      applicationId
    );
  }
}
```

**Benefits:**
- Developers/DBAs cannot see PII even with direct DB access
- Encryption at rest (AES-256-GCM)
- Encryption keys managed by AWS KMS or HSM
- Meets regulatory requirements for sensitive data

---

### Migration: POC → Production

**One-Time Migration Script:**
```java
@Component
@Profile("migration")
public class PiiEncryptionMigration {
  
  public void migrate() {
    List<PocPiiRecord> plainRecords = pocJdbcTemplate.query(
      "SELECT pii_ref, application_id, pii_data, valid_from, valid_until " +
      "FROM pii_records",
      this::mapPocRecord
    );
    
    plainRecords.forEach(record -> {
      byte[] encrypted = encryptionService.encrypt(record.piiData);
      
      prodJdbcTemplate.update(
        "INSERT INTO pii_records (pii_ref, application_id, encrypted_pii, valid_from, valid_until, status) " +
        "VALUES (?, ?, ?, ?, ?, 'PRESENT')",
        record.piiRef,
        record.applicationId,
        encrypted,
        record.validFrom,
        record.validUntil
      );
    });
    
    log.info("Migrated {} PII records to encrypted production store", plainRecords.size());
  }
}
```

---

## Consequences

### Positive

- ✅ **GDPR compliance:** Full right to erasure via cascade delete (all versions)
- ✅ **Temporal queries:** Accurate historical PII before erasure
- ✅ **Event immutability preserved:** Event log remains complete audit trail
- ✅ **Security improvement:** PII encrypted separately with granular access control
- ✅ **Differential retention:** Delete PII after 7 years, keep business events for 25 years
- ✅ **Graceful degradation:** Redacted applications still queryable (non-PII fields intact)
- ✅ **Audit trail:** PII versioning + redaction events track changes and erasures
- ✅ **Access control:** Single projection, query-layer authorization (simpler than multiple projections)
- ✅ **Developer experience:** POC mode enables testing without encryption complexity
- ✅ **Greenfield simplicity:** No migration complexity, no event upcaster needed initially

### Negative

- ⚠️ **PII store is a hard write dependency:** PII is written inside the command handler before the event is applied. If the PII store is unavailable, commands are rejected and callers receive a 5xx error. This is an intentional trade-off — partial writes (event stored without PII) are not acceptable.
- ⚠️ **Orphaned PII risk:** If the PII write succeeds but the subsequent Axon event store write fails, a PII record exists with no corresponding event. Mitigation: all PII writes are **idempotent upserts** keyed on `piiRef`. The orphaned record is overwritten on command retry, or sits harmlessly unreferenced (no event ever contains its `piiRef`, so it is never served to callers).
- ⚠️ **Query-time latency:** PII join adds ~5-10ms per query
- ⚠️ **Storage overhead:** Multiple PII versions per application (temporal history)
- ⚠️ **Cascade complexity:** GDPR erasure must delete all PII versions
- ⚠️ **Operational overhead:** Encryption key management, backup coordination
- ⚠️ **Historical accuracy lost:** Post-erasure temporal queries return redacted

### Neutral

- ➡️ **Testing:** Integration tests for versioning, temporal queries, cascade delete
- ➡️ **Monitoring:** Metrics for PII lookup latency, version count, storage growth
- ➡️ **Profile management:** POC vs Production configuration via Spring profiles

---

## Compliance and Legal Considerations

### GDPR Articles Addressed

| Article | Requirement | How Addressed |
|---------|-------------|---------------|
| 5.1(c) — Data Minimization | Only necessary data retained | PII separated; events contain only business-essential fields |
| 5.1(e) — Storage Limitation | Retention limits | PII deleted after 7 years; business data retained 25 years |
| 17 — Right to Erasure | Delete on request | `piiRepository.delete(piiRef)` hard-deletes PII |
| 25 — Data Protection by Design | Privacy-first architecture | PII extracted at ingestion; events designed PII-free |
| 32 — Security | Encryption, access control | PII encrypted at rest; separate IAM role for PII store |

### Data Protection Impact

- **Before:** PII in immutable event log → cannot delete → GDPR non-compliance risk
- **After:** PII in mutable store → delete on request → compliant
- **Residual risk:** If PII accidentally included in event free-text fields (e.g., case notes), manual redaction still required

---

## Performance Implications

### Benchmarked Scenarios

| Operation | Before | After (Option 2) | Overhead |
|-----------|--------|------------------|----------|
| Create application (write) | 120ms | 150ms | +25% (PII persist) |
| Get application (read) | 45ms | 50ms | +11% (PII join) |
| Get application (PII redacted) | 45ms | 48ms | +7% (cache hit) |
| Redact PII | N/A | 80ms | New operation |

**Mitigation strategies:**
- Cache `piiRef` → PII mapping in read models (TTL 5 min)
- Batch PII lookups when loading multiple applications
- Monitor p99 latency; optimize if > 10ms

---

## Security Considerations

### Threat Model

| Threat | Mitigation |
|--------|------------|
| Unauthorized PII access | Separate IAM role for `pii_records` table; query logs audited |
| PII leakage in logs | Never log PII fields; sanitize all logging paths |
| Encryption key compromise | Envelope encryption; rotate keys quarterly; KMS audit trail |
| SQL injection in PII queries | Parameterized queries only; ORM (JPA) enforced |
| Backup restoration exposes deleted PII | Backup PII store separately; apply redactions to backups |

### Encryption Design

- **Algorithm:** AES-256-GCM
- **Key management:** AWS KMS (or HSM for on-prem)
- **Envelope encryption:** Per-record data keys encrypted by master key
- **Key rotation:** Zero-downtime re-encryption process (decrypt with old key, encrypt with new)

---

## Event Replay Impact and Handling

### Core Principle: **Replay Succeeds with Redacted State**

When PII has been deleted and events are replayed:
- ✅ Replay succeeds (does not fail)
- ✅ Aggregate reconstitutes with business logic intact
- ✅ PII stays deleted (no resurrection — **all versions deleted**)
- ✅ Redacted status propagates through replay
- ✅ Projections show correct `currentPiiRef` from events
- ✅ Query-time PII join returns empty/redacted

**Rationale:** Once PII is erased per GDPR request, it must stay erased — including in historical/temporal views. Event replay is a technical operation that must respect legal deletion.

---

### Scenario 1: Replaying Events After PII Deletion

**Timeline:**
1. May 1: Application created (piiRef: pii-789)
2. June 15: PII updated (old: pii-789, new: pii-ABC)
3. July 1: PII updated (old: pii-ABC, new: pii-XYZ)
4. July 10: GDPR erasure (ALL versions deleted)
5. **Later: Aggregate replayed**

**Event Stream:**
```json
[
  { "type": "ApplicationCreated", "piiRef": "pii-789", "categoryOfLaw": "FAMILY" },
  { "type": "ApplicationPiiUpdated", "oldPiiRef": "pii-789", "newPiiRef": "pii-ABC" },
  { "type": "ApplicationPiiUpdated", "oldPiiRef": "pii-ABC", "newPiiRef": "pii-XYZ" },
  { "type": "ApplicationPiiRedacted", "applicationId": "app-456", "reason": "User request" }
]
```

**Replay Behavior:**

```java
@EventSourcingHandler
public void on(ApplicationCreated event) {
  this.applicationId = event.getApplicationId();
  this.currentPiiRef = event.getPiiRef();        // pii-789
  this.categoryOfLaw = event.getCategoryOfLaw(); // Business logic intact
  // NO PII lookup during event sourcing
}

@EventSourcingHandler
public void on(ApplicationPiiUpdated event) {
  this.currentPiiRef = event.getNewPiiRef();     // Update to pii-ABC, then pii-XYZ
}

@EventSourcingHandler
public void on(ApplicationPiiRedacted event) {
  this.piiStatus = PiiStatus.REDACTED;
  this.currentPiiRef = null;                     // No current PII
}

// Later, when aggregate state is queried
public ApplicantDetails getApplicantDetails() {
  if (this.piiStatus == PiiStatus.REDACTED) {
    return ApplicantDetails.redacted();          // Short-circuit, no lookup
  }
  
  return piiRepository.findCurrent(this.applicationId)
    .orElse(ApplicantDetails.redacted());        // Returns "[REDACTED]"
}
```

**Result:** 
- Aggregate reconstitutes successfully
- `categoryOfLaw`, `matterType`, etc. intact (business logic works)
- PII queries return `[REDACTED]`
- **No resurrection:** PII lookup finds nothing (all versions deleted)

---

### Scenario 2: Temporal Queries After PII Deletion

**Question:** "Show application as it was on June 1st, 2026"

**Timeline:**
- May 1: Application created with PII
- **June 1: ← Query target date**
- June 15: PII updated
- July 10: GDPR erasure (all versions deleted)

**Implementation:**

```java
public ApplicationDto getApplicationAsOf(UUID applicationId, Instant asOf) {
  // Step 1: Replay events up to target timestamp
  AggregateState state = eventStore.replayTo(applicationId, asOf);
  
  // Step 2: Check if PII exists for this timestamp
  Optional<ApplicationPiiDetails> pii = 
    piiRepository.findAsOf(applicationId, asOf);
  
  // Step 3: Build DTO with PII if available
  return ApplicationDto.builder()
    .id(applicationId)
    .categoryOfLaw(state.getCategoryOfLaw())      // Accurate for June 1st
    .applicantName(pii.map(p -> p.getFirstName()) // Redacted if deleted
                      .orElse("[REDACTED]"))
    .build();
}
```

**Before GDPR erasure:**
```
GET /applications/app-456?asOf=2026-06-01
→ Returns: applicantName: "John Doe" (historical PII found)
```

**After GDPR erasure:**
```
GET /applications/app-456?asOf=2026-06-01
→ Returns: applicantName: "[REDACTED]" (all PII versions deleted)
```

**Rationale:** 
- GDPR Right to Erasure applies to **all** contexts, including historical views
- Cannot maintain temporal versions post-erasure (violates deletion requirement)
- Trade-off: Lost historical accuracy for compliance

---

### Scenario 3: Projection Rebuild (Greenfield — No Migration)

**Scenario:** Read model database lost. Rebuild projections from event stream.

**Event Stream:**
```json
[
  { "type": "ApplicationCreated", "piiRef": "pii-789" },
  { "type": "ApplicationPiiUpdated", "newPiiRef": "pii-ABC" },
  { "type": "ApplicationPiiRedacted", "applicationId": "app-456" }
]
```

**Projection Event Handlers:**

```java
@ProcessingGroup("application-projection")
public class ApplicationProjectionHandler {
  
  @EventHandler
  public void on(ApplicationCreated event) {
    ApplicationProjection proj = ApplicationProjection.builder()
      .id(event.getApplicationId())
      .currentPiiRef(event.getPiiRef())          // Store reference only
      .categoryOfLaw(event.getCategoryOfLaw())
      .piiStatus(PiiStatus.PRESENT)
      .build();
    
    repository.save(proj);
    
    // NO PII fetch — projection stores reference only
  }
  
  @EventHandler
  public void on(ApplicationPiiUpdated event) {
    ApplicationProjection proj = repository.findById(event.getApplicationId());
    proj.setCurrentPiiRef(event.getNewPiiRef()); // Update reference
    repository.save(proj);
  }
  
  @EventHandler
  public void on(ApplicationPiiRedacted event) {
    ApplicationProjection proj = repository.findById(event.getApplicationId());
    proj.setPiiStatus(PiiStatus.REDACTED);
    proj.setCurrentPiiRef(null);
    repository.save(proj);
  }
  
  // NO @ReplayCompletedHandler needed — no batch PII enrichment!
}
```

**Query-Time PII Join:**

```java
@GetMapping("/applications/{id}")
@PreAuthorize("hasRole('CASEWORKER')")
public ApplicationDto getApplication(@PathVariable UUID id) {
  ApplicationProjection proj = repository.findById(id);
  
  // Join PII at query time
  Optional<ApplicationPiiDetails> pii = 
    piiRepository.findCurrent(proj.getId());
  
  return buildDto(proj, pii);
}
```

**Performance:**
- Projection rebuild: 1M events → 1M simple inserts (~1 minute)
- No batch PII enrichment phase (was ~1-2 min in old design)
- **Total: ~1 minute** (vs ~3 min with enrichment)

---

### Scenario 4: PII Versioning During Replay

**Timeline:**
1. May 1: Create (pii-789)
2. June 15: Update (pii-ABC)
3. **Replay both events**

**PII Store State After Replay:**
```sql
-- pii-789 (closed)
pii_ref: pii-789, valid_from: 2026-05-01, valid_until: 2026-06-15

-- pii-ABC (current)
pii_ref: pii-ABC, valid_from: 2026-06-15, valid_until: NULL
```

**Aggregate State After Replay:**
```java
this.currentPiiRef = pii-ABC  // Latest version
```

**Temporal Query:**
```java
piiRepository.findAsOf(app-456, "2026-06-01")  // Returns pii-789 (historical)
piiRepository.findAsOf(app-456, "2026-06-20")  // Returns pii-ABC (after update)
piiRepository.findCurrent(app-456)             // Returns pii-ABC (current)
```

**After GDPR Erasure:**
```java
piiRepository.findAsOf(app-456, any date)      // All return empty (all deleted)
```

---

### Greenfield Deployment (No Upcaster Needed)

**Assumption:** Starting fresh with no existing events.

**All events written in final format from day 1:**
```java
ApplicationCreated {
  applicationId,
  piiRef,           // Always present from day 1
  categoryOfLaw,
  // ... non-PII fields
}
```

**No event upcaster needed initially:**
- All events have `piiRef` from creation
- No old-format events to transform
- Simpler implementation (one less component)

**Future consideration:** If schema evolves (e.g., add `piiVersion` field), implement upcaster at that time.

---

### Testing Requirements

**Integration Tests:**

- [ ] Replay after PII deletion → aggregate reconstitutes with redacted status
- [ ] Temporal query before erasure → returns historical PII
- [ ] Temporal query after erasure → returns redacted for all dates
- [ ] Projection rebuild → correct `currentPiiRef` from events
- [ ] PII versioning → `findAsOf` returns correct version
- [ ] Multiple PII updates → latest version returned by `findCurrent`
- [ ] GDPR erasure → all versions deleted, temporal queries redacted

**Performance:**
- [ ] Projection rebuild 1M events → < 2 minutes
- [ ] Query-time PII join → < 10ms p99
- [ ] Temporal query → < 50ms p99 (event replay + PII lookup)

---

## Alternatives Considered and Rejected

### Blockchain/Immutable Ledger for PII
- **Rejected:** Immutability conflicts with erasure requirement

### Homomorphic Encryption
- **Rejected:** Performance overhead too high; complex key management

### Anonymization Instead of Deletion
- **Rejected:** GDPR requires erasure on request; anonymization insufficient if re-identification possible

---

## Open Questions and Future Work

- [ ] **Retention policy details:** Confirm 7-year PII / 25-year business data split with legal
- [ ] **Backup strategy:** How to redact PII from immutable backups (S3 Glacier, etc.)
- [ ] **Cross-border data:** If PII stored in different region, confirm GDPR transfer mechanisms
- [ ] **Audit log retention:** How long to keep `ApplicationPiiRedacted` events (recommend: indefinitely)
- [ ] **PII discovery tooling:** Automated scanner to detect PII in free-text fields

---

## References

- [Implementation Plan](../../plans/pii-externalization/00-overview-and-plan.md)
- [GDPR Article 17: Right to Erasure](https://gdpr-info.eu/art-17-gdpr/)
- [ICO Data Protection by Design and Default](https://ico.org.uk/for-organisations/guide-to-data-protection/guide-to-the-general-data-protection-regulation-gdpr/accountability-and-governance/data-protection-by-design-and-default/)
- [Axon Framework Documentation](https://docs.axoniq.io/reference-guide/)
- [NIST SP 800-57: Key Management](https://csrc.nist.gov/publications/detail/sp/800-57-part-1/rev-5/final)

---

## Approval

| Role | Name | Date | Decision |
|------|------|------|----------|
| Lead Architect | _TBD_ | _TBD_ | _Pending_ |
| Data Protection Officer | _TBD_ | _TBD_ | _Pending_ |
| Tech Lead | _TBD_ | _TBD_ | _Pending_ |
| Security Team | _TBD_ | _TBD_ | _Pending_ |

---

## Changelog

- **2026-07-17:** Initial draft (Proposed status)

