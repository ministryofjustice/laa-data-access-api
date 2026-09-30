# POST Path Changes for potentialDuplicates Storage

## Overview
This document outlines the changes made to enable `potentialDuplicates` to be stored as part of the application creation (POST) request path. The implementation follows CQRS (Command Query Responsibility Segregation) and Event Sourcing patterns using the Axon framework.

---

## Data Flow

```
POST /applications 
  ↓
ApplicationCreateRequest.potentialDuplicates 
  ↓
CreateApplicationCommandMapper.toCommand() 
  ↓
CreateApplicationCommand.potentialDuplicates 
  ↓
ApplicationCreatedEvent.potentialDuplicates 
  ↓
ApplicationProjection.on(event) 
  ↓
ApplicationReadModel.potentialDuplicates (persisted as JSONB) 
  ↓
Database: application_current_state.potential_duplicates
```

---

## Changed Classes and Methods

### 1. API Contract Definition
**File**: `data-access-api/open-api-applications/components.yml`

**Change**: Added `potentialDuplicates` field to `ApplicationCreateRequest`

The OpenAPI specification now defines:
```yaml
potentialDuplicates:
  type: array
  items:
    $ref: '#/components/schemas/PotentialDuplicate'
  description: List of potential duplicate applications
```

---

### 2. HTTP Request Mapping Layer
**Class**: `CreateApplicationCommandMapper`

**File**: `data-access-service-axon/src/main/java/uk/gov/justice/laa/dstew/access/controller/application/CreateApplicationCommandMapper.java`

**Method**: `toCommand(ApplicationCreateRequest request, int schemaVersion)`

**Change**: Extracts `potentialDuplicates` from the HTTP request and passes it to the domain command.

**Implementation**:
```java
public CreateApplicationCommand toCommand(ApplicationCreateRequest request, int schemaVersion) {
    return new CreateApplicationCommand(
        request.getId(),
        request.getStatus() == null ? null : request.getStatus().name(),
        request.getLaaReference(),
        request.getApplicationContent(),
        serialise(request),
        schemaVersion,
        "BaseCivilApplication.json",
        request.getPotentialDuplicates());  // NEW - line 29
}
```

---

### 2.5. Command to Domain Details Transformation
**Class**: `ApplicationCreationDetailsFactory`

**File**: `data-access-service-axon/src/main/java/uk/gov/justice/laa/dstew/access/command/application/ApplicationCreationDetailsFactory.java`

**Method**: `toCreationDetails(CreateApplicationCommand command, ParsedAppContentDetails parsed)` (called from `prepare()`)

**Change**: Transforms the command into `ApplicationCreationDetails`, extracting `potentialDuplicates` from the command (line 55)

**Implementation**:
```java
private ApplicationCreationDetails toCreationDetails(
    CreateApplicationCommand command, ParsedAppContentDetails parsed) {
  return new ApplicationCreationDetails(
      command.status(),
      command.laaReference(),
      parsed.client(),
      parsed.provider(),
      parsed.opponents(),
      command.schemaVersion(),
      parsed.submittedAt(),
      parsed.usedDelegatedFunctions(),
      parsed.categoryOfLaw(),
      parsed.matterType(),
      parsed.proceedings(),
      command.serialisedRequest(),
      Instant.now(clock),
      command.potentialDuplicates());  // LINE 55 - NEW
}
```

**Data Model**: `ApplicationCreationDetails`
- **File**: `data-access-service-axon/src/main/java/.../ApplicationCreationDetails.java`
- **Field**: `List<PotentialDuplicate> potentialDuplicates`
- **Normalization** (lines 32-33): Converts null to empty list, creates immutable copy

---

### 3. Domain Command Object
**Class**: `CreateApplicationCommand`

**File**: `data-access-service-axon/src/main/java/uk/gov/justice/laa/dstew/access/command/application/CreateApplicationCommand.java`

**Change**: Added field to capture potential duplicates from the HTTP request

**Field Added**:
```java
List<PotentialDuplicate> potentialDuplicates
```

This record now includes:
- `UUID id`
- `String status`
- `String laaReference`
- `Map<String, Object> applicationContent`
- `String serialisedRequest`
- `int schemaVersion`
- `String schemaName`
- `List<PotentialDuplicate> potentialDuplicates` ← **NEW**

---

### 4. Domain Event
**Class**: `ApplicationCreatedEvent`

**File**: `data-access-service-axon/src/main/java/uk/gov/justice/laa/dstew/access/command/application/ApplicationCreatedEvent.java`

**Change**: Added field to propagate potential duplicates through the event stream

**Field Added**:
```java
List<PotentialDuplicate> potentialDuplicates
```

This event now carries:
- `UUID id`
- `String requestFingerprint`
- `String status`
- `int schemaVersion`
- `Instant occurredAt`
- `List<PotentialDuplicate> potentialDuplicates` ← **NEW**

---

### 5. Event Construction from Details
**Class**: `ApplicationDecider`

**File**: `data-access-service-axon/src/main/java/uk/gov/justice/laa/dstew/access/command/application/ApplicationDecider.java`

**Method**: `decideCreate()` → calls → `buildApplicationCreatedEvent()`

**Transformation** (lines 220-228):
```java
private static ApplicationCreatedEvent buildApplicationCreatedEvent(
    UUID applicationId,
    long applicationDataVersion,
    String fingerprint,
    ApplicationCreationDetails details) {
  return new ApplicationCreatedEvent(
      applicationId,
      applicationDataVersion,
      fingerprint,
      details.status(),
      details.schemaVersion(),
      details.occurredAt(),
      details.potentialDuplicates());  // LINE 227 - Event gets potentialDuplicates from details
}
```

**Summary**:
- `ApplicationCreationDetails.potentialDuplicates()` is passed directly to `ApplicationCreatedEvent`
- This completes the transformation chain from command → details → event

---

### 6. Query-side Read Model (JPA Entity)
**Class**: `ApplicationReadModel` (Query side)

**File**: `data-access-service-axon/src/main/java/uk/gov/justice/laa/dstew/access/query/application/ApplicationReadModel.java`

**Changes**: 
- Added database column mapping
- Added necessary Hibernate annotations for JSON persistence

**Imports Added**:
```java
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uk.gov.justice.laa.dstew.access.model.PotentialDuplicate;
```

**Field Added**:
```java
@Column(name = "potential_duplicates")
@JdbcTypeCode(SqlTypes.JSON)
private List<PotentialDuplicate> potentialDuplicates;
```

This maps the `potential_duplicates` JSONB column in the database to a Java list of `PotentialDuplicate` objects.

---

### 7. Event Handler / Projector
**Class**: `ApplicationProjection`

**File**: `data-access-service-axon/src/main/java/uk/gov/justice/laa/dstew/access/query/application/ApplicationProjection.java`

**Method**: `on(ApplicationCreatedEvent event)` (annotated with `@EventHandler`)

**Change**: Populates the `potentialDuplicates` field in the read model from the event

**Implementation**:
```java
@EventHandler
public void on(ApplicationCreatedEvent event) {
    repository.save(ApplicationReadModel.builder()
        // ... other fields ...
        .leadApplicationId(null)
        .potentialDuplicates(event.potentialDuplicates())  // NEW
        .build());
}
```

This projector is responsible for persisting data from the event to the read model (database).

---

### 8. Database Schema Migration
**File**: `data-access-service-axon/src/main/resources/db/migration/V17__add_potential_duplicates_column.sql`

**Change**: Adds a new JSONB column to store potential duplicates

**SQL Migration**:
```sql
ALTER TABLE application_current_state 
ADD COLUMN potential_duplicates JSONB DEFAULT NULL;
```

The `potential_duplicates` column:
- Type: JSONB (JSON Binary - allows indexing and query operations)
- Default: NULL
- Stores an array of `PotentialDuplicate` objects

---

## Key Design Decisions

1. **JSONB Storage**: Potential duplicates are stored as JSONB in PostgreSQL, allowing for flexible querying and full-text search capabilities without needing a separate junction table.

2. **Event Sourcing**: The data flows through domain events (`ApplicationCreatedEvent`), ensuring an audit trail of when and what duplicate applications were identified.

3. **Read Model Projection**: The Axon projector (`ApplicationProjection`) handles the one-way sync from events to the query-side read model, keeping read and write models separate.

4. **Type Safety**: Uses the `PotentialDuplicate` model object throughout the pipeline, ensuring type safety and validation at compile time.

---

## Verification Checklist

- [x] API contract updated in `components.yml`
- [x] HTTP request mapper passes `potentialDuplicates` to command
- [x] Domain command includes `potentialDuplicates` field
- [x] Domain event includes `potentialDuplicates` field
- [x] Query-side read model entity includes column mapping
- [x] Event handler/projector populates field from event
- [x] Database schema migration adds JSONB column
- [x] All tests updated to include potentialDuplicates

---

## Testing

Tests have been updated to verify the complete flow:
- `CreateApplicationUseCaseTest` - Verifies command creation
- `ApplicationAggregateTest` - Verifies event generation
- `ApplicationProjectionTest` - Verifies read model persistence
- `CreateApplicationCommandControllerTest` - Verifies HTTP mapping

