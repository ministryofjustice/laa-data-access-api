# Pragmatic Use Case Refactoring Proposal

## Executive Summary

This document proposes a **pragmatic, incremental refactoring** of the LAA Data Access API from a traditional service-layer architecture to a use-case-driven approach. Unlike a full Clean/Hexagonal Architecture transformation, this approach maintains the existing Spring framework integration while achieving better separation of concerns, improved testability, and clearer business logic organization.

**Key Principle**: Evolve the existing service layer into focused use case services without introducing ports, adapters, or separate domain models.

**Important Architectural Decision**: While this refactoring keeps the overall architecture simple, we maintain a clear separation between API DTOs (OpenAPI-generated models) and internal command/query models. Controllers map API DTOs to internal models, and use case services work exclusively with internal models. This provides flexibility for API evolution without affecting business logic. See [API DTOs vs Internal Models](#api-dtos-vs-internal-models) section for details.

## Philosophy: Evolution Over Revolution

This refactoring approach prioritizes:
- **Incremental changes** - Refactor one operation at a time
- **Low risk** - Maintain existing infrastructure and patterns
- **Quick wins** - Deliver value early with minimal disruption
- **Team familiarity** - Keep Spring Data, JPA entities, and existing patterns
- **Practical benefits** - Focus on testability and maintainability improvements

## Current Architecture

### Existing Structure

```
controller/ → service/ → repository/ → entity/
```

### Current Problems

1. **Large, Multi-Purpose Services**: `ApplicationService` handles 8+ different operations
2. **Scattered Business Logic**: Related logic spread across multiple service classes
3. **Testing Complexity**: Testing one operation requires mocking unrelated dependencies
4. **Unclear Ownership**: Hard to know which service method handles which business operation
5. **Parallel Development Conflicts**: Multiple developers modifying the same service class

## Proposed Architecture

### Refactored Structure

```
controller/ → use-case-service/ → repository/ → entity/
```

### Key Changes

1. **One Service Per Use Case**: Break large services into focused, single-purpose services
2. **Explicit Naming**: Service names clearly indicate the business operation
3. **Standardized Interface**: All use case services implement a common pattern
4. **Keep Existing Infrastructure**: No changes to repositories, entities, or Spring configuration

## Identified Use Cases

### Application Domain (8 Use Cases)

1. **CreateApplicationService**
   - Operation: Create new application
   - Input: `CreateApplicationRequest`
   - Output: `UUID`
   - Dependencies: `ApplicationRepository`, validation logic

2. **UpdateApplicationService**
   - Operation: Update application details
   - Input: `UUID`, `UpdateApplicationRequest`
   - Output: `void`
   - Dependencies: `ApplicationRepository`, `DomainEventRepository`

3. **GetApplicationByIdService**
   - Operation: Retrieve single application
   - Input: `UUID`
   - Output: `Application`
   - Dependencies: `ApplicationRepository`

4. **SearchApplicationsService**
   - Operation: Search/list applications with filtering
   - Input: Multiple filter parameters, pagination
   - Output: `PaginatedResult<ApplicationSummary>`
   - Dependencies: `ApplicationSummaryRepository`

5. **AssignCaseworkerService**
   - Operation: Assign caseworker to applications
   - Input: `AssignCaseworkerRequest`
   - Output: `void`
   - Dependencies: `ApplicationRepository`, `CaseworkerRepository`, `DomainEventRepository`

6. **UnassignCaseworkerService**
   - Operation: Remove caseworker assignment
   - Input: `UUID`, `UnassignCaseworkerRequest`
   - Output: `void`
   - Dependencies: `ApplicationRepository`, `DomainEventRepository`

7. **GetApplicationHistoryService**
   - Operation: Retrieve application event history
   - Input: `UUID`, `List<DomainEventType>`
   - Output: `ApplicationHistoryResponse`
   - Dependencies: `DomainEventRepository`

8. **MakeDecisionService**
   - Operation: Make decision on application
   - Input: `UUID`, `MakeDecisionRequest`
   - Output: `void`
   - Dependencies: `ApplicationRepository`, `DecisionRepository`, `DomainEventRepository`

### Caseworker Domain (1 Use Case)

9. **GetAllCaseworkersService**
   - Operation: List all caseworkers
   - Input: None (or service name)
   - Output: `List<Caseworker>`
   - Dependencies: `CaseworkerRepository`

### Individual Domain (1 Use Case)

10. **SearchIndividualsService**
    - Operation: Search/list individuals with filtering
    - Input: Pagination, filters
    - Output: `PaginatedResult<Individual>`
    - Dependencies: `IndividualRepository`

## Persistence Interfaces for Custom Repositories

### The Problem: Complex Queries with Projection DTOs

As your application evolves, you'll need:
- **Custom queries** with complex joins across multiple tables
- **Projection DTOs** that select only required fields (not full entities)
- **Performance optimization** to avoid fetching unnecessary data
- **Read models** separate from write models (CQRS-lite)

### The Solution: Lightweight Persistence Interface

Introduce a thin persistence interface layer between use case services and Spring Data repositories:

```
Use Case Service → Persistence Interface → Custom Repository + Spring Data Repository
```

This gives you:
- ✅ Flexibility to add custom query methods
- ✅ Return DTOs directly from complex queries
- ✅ Keep business logic decoupled from JPA specifics
- ✅ Easy to test (mock the interface)
- ✅ No need for full ports/adapters complexity

### Implementation Pattern

#### 1. Define Persistence Interface

```java
// service/application/persistence/ApplicationPersistence.java
public interface ApplicationPersistence {
    // Write operations - work with entities
    ApplicationEntity save(ApplicationEntity entity);
    Optional<ApplicationEntity> findById(UUID id);
    
    // Read operations - can return DTOs for performance
    Page<ApplicationSummaryDto> searchApplications(
        ApplicationSearchCriteria criteria, 
        Pageable pageable
    );
    
    List<ApplicationWithCaseworkerDto> findApplicationsWithCaseworker(UUID caseworkerId);
}
```

#### 2. Create Custom Repository with DTO Projections

```java
// repository/custom/ApplicationCustomRepository.java
public interface ApplicationCustomRepository {
    Page<ApplicationSummaryDto> searchApplications(
        ApplicationSearchCriteria criteria,
        Pageable pageable
    );
    
    List<ApplicationWithCaseworkerDto> findApplicationsWithCaseworker(UUID caseworkerId);
}

// repository/custom/ApplicationCustomRepositoryImpl.java
@Repository
@RequiredArgsConstructor
public class ApplicationCustomRepositoryImpl implements ApplicationCustomRepository {
    private final EntityManager entityManager;
    
    @Override
    public Page<ApplicationSummaryDto> searchApplications(
            ApplicationSearchCriteria criteria,
            Pageable pageable) {
        
        // Complex query with joins and projections
        String query = """
            SELECT new uk.gov.justice.laa.dstew.access.dto.ApplicationSummaryDto(
                a.id, a.laaReference, a.status, 
                c.firstName, c.lastName,
                COUNT(p.id)
            )
            FROM ApplicationEntity a
            LEFT JOIN a.caseworker c
            LEFT JOIN a.proceedings p
            WHERE (:status IS NULL OR a.status = :status)
            AND (:laaReference IS NULL OR a.laaReference LIKE :laaReference)
            GROUP BY a.id, a.laaReference, a.status, c.firstName, c.lastName
            """;
        
        TypedQuery<ApplicationSummaryDto> typedQuery = entityManager
            .createQuery(query, ApplicationSummaryDto.class);
        
        // Set parameters, apply pagination, return results
        // ...
    }
}
```

#### 3. Implement Persistence Interface (Adapter)

```java
// repository/adapter/ApplicationPersistenceAdapter.java
@Component
@RequiredArgsConstructor
public class ApplicationPersistenceAdapter implements ApplicationPersistence {
    private final ApplicationRepository jpaRepository;
    private final ApplicationCustomRepository customRepository;
    
    @Override
    public ApplicationEntity save(ApplicationEntity entity) {
        return jpaRepository.save(entity);
    }
    
    @Override
    public Optional<ApplicationEntity> findById(UUID id) {
        return jpaRepository.findById(id);
    }
    
    @Override
    public Page<ApplicationSummaryDto> searchApplications(
            ApplicationSearchCriteria criteria,
            Pageable pageable) {
        return customRepository.searchApplications(criteria, pageable);
    }
    
    @Override
    public List<ApplicationWithCaseworkerDto> findApplicationsWithCaseworker(UUID caseworkerId) {
        return customRepository.findApplicationsWithCaseworker(caseworkerId);
    }
}
```

#### 4. Use Case Service Uses Persistence Interface

```java
@Service
@RequiredArgsConstructor
public class SearchApplicationsService {
    private final ApplicationPersistence applicationPersistence;  // Interface, not repository
    
    public PaginatedResult<ApplicationSummaryDto> execute(SearchApplicationsQuery query) {
        ApplicationSearchCriteria criteria = buildCriteria(query);
        Pageable pageable = PageRequest.of(query.getPage(), query.getPageSize());
        
        Page<ApplicationSummaryDto> page = 
            applicationPersistence.searchApplications(criteria, pageable);
        
        return PaginatedResult.from(page);
    }
}
```

### Benefits of This Approach

1. **Performance**: Return only fields you need via DTO projections
2. **Flexibility**: Custom queries without compromising use case services
3. **Testability**: Mock the persistence interface, not Spring Data repositories
4. **Decoupling**: Business logic doesn't depend on JPA specifics
5. **Evolution Path**: Easy to evolve toward full ports/adapters later if needed

### Package Structure with Persistence Interfaces

```
uk.gov.justice.laa.dstew.access/
  ├── service/
  │   └── application/
  │       ├── CreateApplicationService.java
  │       ├── SearchApplicationsService.java
  │       ├── dto/
  │       │   └── SearchApplicationsQuery.java
  │       └── persistence/
  │           └── ApplicationPersistence.java (interface)
  ├── repository/
  │   ├── ApplicationRepository.java (Spring Data)
  │   ├── adapter/
  │   │   └── ApplicationPersistenceAdapter.java (implements persistence interface)
  │   └── custom/
  │       ├── ApplicationCustomRepository.java
  │       └── ApplicationCustomRepositoryImpl.java (custom queries + DTOs)
  └── dto/
      ├── ApplicationSummaryDto.java (projection DTO)
      └── ApplicationWithCaseworkerDto.java
```

### When to Use Persistence Interfaces

**Use for**:
- ✅ Complex search operations with multiple filters
- ✅ Queries with joins across multiple tables
- ✅ DTO projections for performance
- ✅ Read-heavy operations that need optimization
- ✅ Operations that might need different persistence strategies

**Skip for**:
- ❌ Simple CRUD operations (findById, save)
- ❌ Single-table queries with no joins
- ❌ Operations where full entity is needed anyway

### Pragmatic Rule

For this refactoring:
1. **Simple operations**: Use case service → Spring Data Repository directly
2. **Complex queries**: Use case service → Persistence Interface → Custom Repository

This gives you flexibility where you need it without over-engineering simple cases.

## API DTOs vs Internal Models

### Layering Principle

**API DTOs should remain at the controller layer.** Your use case services should work with internal models, not API DTOs.

```
Controller (API DTOs) 
    ↓ maps to
Use Case Service (Internal Models)
    ↓ maps to
Repository (JPA Entities)
```

### Why Separate API DTOs from Internal Models?

1. **API Stability**: OpenAPI-generated DTOs change when the API contract changes
2. **Business Logic Protection**: Internal models can evolve independently of API
3. **Validation Separation**: API validation (format, required fields) vs business validation (rules, constraints)
4. **Flexibility**: Same internal model can support multiple API versions or interfaces (REST, GraphQL, etc.)
5. **Testing**: Unit test business logic without API concerns

### Mapping Layers

#### Layer 1: Controller Maps API DTO → Internal Model
```java
@RestController
public class ApplicationController {
    private final CreateApplicationService createApplication;
    
    @Override
    public ResponseEntity<Void> createApplication(
            ServiceName serviceName,
            ApplicationCreateRequest apiRequest) {  // OpenAPI-generated DTO
        
        // Map API DTO to internal command/model
        CreateApplicationCommand command = CreateApplicationCommand.from(apiRequest);
        
        UUID id = createApplication.execute(command);
        return ResponseEntity.created(buildUri(id)).build();
    }
}
```

#### Layer 2: Use Case Service Works With Internal Models
```java
@Service
public class CreateApplicationService {
    private final ApplicationRepository applicationRepository;
    private final ApplicationMapper applicationMapper;
    
    public UUID execute(CreateApplicationCommand command) {
        // Business logic uses internal models
        ApplicationEntity entity = applicationMapper.toEntity(command);
        ApplicationEntity saved = applicationRepository.save(entity);
        return saved.getId();
    }
}
```

#### Layer 3: Mapper Converts Internal Model → JPA Entity
```java
@Mapper(componentModel = "spring")
public interface ApplicationMapper {
    ApplicationEntity toEntity(CreateApplicationCommand command);
    Application toModel(ApplicationEntity entity);
}
```

### Practical Implementation

For your codebase:

1. **Keep OpenAPI DTOs**: `ApplicationCreateRequest`, `ApplicationUpdateRequest` stay in `model/` package
2. **Create Internal Commands**: New classes like `CreateApplicationCommand`, `UpdateApplicationCommand` in `service/application/dto/`
3. **Map in Controllers**: Controllers handle API→Internal mapping
4. **Use Existing Mappers**: Your existing `ApplicationMapper` already handles Internal→Entity mapping

### Package Structure for DTOs

```
uk.gov.justice.laa.dstew.access/
  ├── controller/
  │   └── ApplicationController.java (maps API DTOs to commands)
  ├── service/
  │   └── application/
  │       ├── CreateApplicationService.java (uses commands)
  │       └── dto/
  │           ├── CreateApplicationCommand.java (internal)
  │           ├── UpdateApplicationCommand.java (internal)
  │           └── ApplicationResult.java (internal)
  ├── mapper/
  │   └── ApplicationMapper.java (maps internal models ↔ entities)
  ├── model/
  │   └── ApplicationCreateRequest.java (OpenAPI-generated, API layer only)
  └── entity/
      └── ApplicationEntity.java (JPA)
```

### When to Skip Internal Models

For **very simple read operations**, you can skip internal models:

```java
// Simple pass-through - OK to use repository directly
@Service
public class GetApplicationByIdService {
    private final ApplicationRepository repository;
    private final ApplicationMapper mapper;
    
    public Application execute(UUID id) {
        ApplicationEntity entity = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(id));
        return mapper.toModel(entity);  // Entity → API DTO
    }
}
```

But for **complex operations with business logic**, use internal models:

```java
// Complex logic - use internal command
@Service
public class AssignCaseworkerService {
    // ...
    
    public void execute(AssignCaseworkerCommand command) {  // Internal model
        // Business validation
        validateAssignment(command);
        
        // Business logic
        List<ApplicationEntity> apps = loadApplications(command.getApplicationIds());
        CaseworkerEntity caseworker = loadCaseworker(command.getCaseworkerId());
        
        apps.forEach(app -> app.assignCaseworker(caseworker));
        applicationRepository.saveAll(apps);
        
        // Domain event
        recordAssignmentEvent(command);
    }
}
```

### Visual Flow: Request to Response

```
┌─────────────────────────────────────────────────────────────┐
│ 1. CLIENT REQUEST                                           │
│    POST /applications                                       │
│    Body: { "apply_application_id": "...", ... }           │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│ 2. CONTROLLER (API Layer)                                   │
│    ApplicationController.createApplication()                │
│    - Receives: ApplicationCreateRequest (OpenAPI DTO)      │
│    - Maps to: CreateApplicationCommand (Internal)          │
│    - Calls: CreateApplicationService.execute(command)      │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│ 3. USE CASE SERVICE (Business Logic)                       │
│    CreateApplicationService.execute()                       │
│    - Receives: CreateApplicationCommand (Internal)         │
│    - Validates: Business rules                             │
│    - Maps to: ApplicationEntity (JPA)                      │
│    - Calls: Repository.save()                              │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│ 4. REPOSITORY (Data Access)                                 │
│    ApplicationRepository.save()                             │
│    - Receives: ApplicationEntity (JPA)                     │
│    - Persists to database                                   │
│    - Returns: ApplicationEntity with ID                    │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│ 5. RESPONSE FLOW                                            │
│    - Service returns: UUID (or internal result)            │
│    - Controller builds: ResponseEntity with URI            │
│    - Client receives: 201 Created with Location header     │
└─────────────────────────────────────────────────────────────┘
```

### Key Benefits of This Layering

1. **API Evolution**: Change OpenAPI spec without touching business logic
2. **API Versioning**: Support v1 and v2 APIs with same business logic
3. **Multiple Interfaces**: Add GraphQL alongside REST using same services
4. **Testability**: Test business logic with simple POJOs, not API DTOs
5. **Clarity**: Clear separation of concerns at each layer

## Implementation Pattern

### Standard Use Case Service Structure

Each use case service follows this pattern:

```java
@Service
@RequiredArgsConstructor
@Transactional
public class CreateApplicationService {
    
    private final ApplicationRepository applicationRepository;
    private final ApplicationMapper applicationMapper;
    // ... other dependencies
    
    public UUID execute(CreateApplicationCommand command) {  // Internal model, not API DTO
        // 1. Validate business rules
        validateApplicationData(command);
        
        // 2. Execute business logic
        ApplicationEntity entity = applicationMapper.toEntity(command);
        
        // 3. Persist changes
        ApplicationEntity saved = applicationRepository.save(entity);
        
        // 4. Return result (internal model or primitive)
        return saved.getId();
    }
    
    private void validateApplicationData(CreateApplicationCommand command) {
        // Business validation logic
    }
}
```

### Internal Command/Query Models

Create simple internal DTOs for your use cases:

```java
// service/application/dto/CreateApplicationCommand.java
@Value
@Builder
public class CreateApplicationCommand {
    UUID applyApplicationId;
    String laaReference;
    String applicationType;
    Map<String, Object> applicationContent;
    
    // Factory method to map from API DTO
    public static CreateApplicationCommand from(ApplicationCreateRequest apiRequest) {
        return CreateApplicationCommand.builder()
            .applyApplicationId(apiRequest.getApplyApplicationId())
            .laaReference(apiRequest.getLaaReference())
            .applicationType(apiRequest.getApplicationType())
            .applicationContent(apiRequest.getApplicationContent())
            .build();
    }
}
```

### Naming Convention

- **Service Class**: `<Verb><Noun>Service` (e.g., `CreateApplicationService`)
- **Method**: `execute(...)` - consistent across all use case services
- **API Request DTO**: `<Noun><Verb>Request` (e.g., `ApplicationCreateRequest`) - OpenAPI generated
- **API Response DTO**: `<Noun>` or `<Noun>Response` (e.g., `Application`, `ApplicationHistoryResponse`) - OpenAPI generated
- **Internal Command**: `<Verb><Noun>Command` (e.g., `CreateApplicationCommand`) - for write operations
- **Internal Query**: `<Verb><Noun>Query` (e.g., `SearchApplicationsQuery`) - for read operations with complex criteria
- **Internal Result**: `<Noun>Result` (e.g., `ApplicationResult`) - optional internal result model

### Controller Integration

Controllers become thin coordinators that delegate to use case services:

```java
@RestController
@RequiredArgsConstructor
public class ApplicationController implements ApplicationApi {
    
    private final CreateApplicationService createApplication;
    private final UpdateApplicationService updateApplication;
    private final GetApplicationByIdService getApplicationById;
    // ... other use case services
    
    @Override
    public ResponseEntity<Void> createApplication(
            ServiceName serviceName,
            ApplicationCreateRequest request) {
        UUID id = createApplication.execute(request);
        URI uri = ServletUriComponentsBuilder
            .fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(id)
            .toUri();
        return ResponseEntity.created(uri).build();
    }
}
```

## Package Structure

### Proposed Organization

```
uk.gov.justice.laa.dstew.access/
  ├── controller/
  │   ├── ApplicationController.java
  │   ├── CaseworkerController.java
  │   └── IndividualsController.java
  ├── service/
  │   ├── application/
  │   │   ├── CreateApplicationService.java
  │   │   ├── UpdateApplicationService.java
  │   │   ├── GetApplicationByIdService.java
  │   │   ├── SearchApplicationsService.java
  │   │   ├── AssignCaseworkerService.java
  │   │   ├── UnassignCaseworkerService.java
  │   │   ├── GetApplicationHistoryService.java
  │   │   ├── MakeDecisionService.java
  │   │   ├── dto/
  │   │   │   ├── CreateApplicationCommand.java
  │   │   │   ├── UpdateApplicationCommand.java
  │   │   │   ├── SearchApplicationsQuery.java
  │   │   │   ├── AssignCaseworkerCommand.java
  │   │   │   └── MakeDecisionCommand.java
  │   │   └── persistence/
  │   │       └── ApplicationPersistence.java (interface)
  │   ├── caseworker/
  │   │   ├── GetAllCaseworkersService.java
  │   │   └── persistence/
  │   │       └── CaseworkerPersistence.java (interface)
  │   ├── individual/
  │   │   ├── SearchIndividualsService.java
  │   │   ├── dto/
  │   │   │   └── SearchIndividualsQuery.java
  │   │   └── persistence/
  │   │       └── IndividualPersistence.java (interface)
  │   └── shared/
  │       ├── ApplicationContentParserService.java
  │       └── ProceedingsService.java (if still needed)
  ├── repository/
  │   ├── ApplicationRepository.java (Spring Data JPA)
  │   ├── ApplicationSummaryRepository.java (Spring Data JPA)
  │   ├── CaseworkerRepository.java
  │   ├── IndividualRepository.java
  │   ├── DomainEventRepository.java
  │   ├── DecisionRepository.java
  │   ├── adapter/
  │   │   ├── ApplicationPersistenceAdapter.java (implements ApplicationPersistence)
  │   │   ├── CaseworkerPersistenceAdapter.java
  │   │   └── IndividualPersistenceAdapter.java
  │   └── custom/
  │       ├── ApplicationCustomRepository.java (interface)
  │       ├── ApplicationCustomRepositoryImpl.java (custom queries, DTOs)
  │       ├── IndividualCustomRepository.java
  │       └── IndividualCustomRepositoryImpl.java
  ├── entity/
  │   ├── ApplicationEntity.java
  │   ├── CaseworkerEntity.java
  │   ├── IndividualEntity.java
  │   └── ... (existing entities)
  ├── dto/
  │   ├── ApplicationSummaryDto.java (projection DTO for queries)
  │   ├── ApplicationWithCaseworkerDto.java
  │   └── IndividualSummaryDto.java
  ├── mapper/
  │   ├── ApplicationMapper.java (maps internal commands/queries ↔ entities)
  │   ├── CaseworkerMapper.java
  │   └── IndividualMapper.java
  ├── model/
  │   └── ... (existing DTOs from OpenAPI - API layer only)
  ├── config/
  ├── exception/
  ├── security/
  ├── utils/
  └── validation/
```

### What Stays The Same

- ✅ Repository interfaces remain in `repository/` package
- ✅ JPA entities remain in `entity/` package
- ✅ OpenAPI-generated models remain in `model/` package
- ✅ All existing configuration, exceptions, security, and utils
- ✅ Spring Data JPA, Spring Boot, and all existing dependencies
- ✅ Database schema and migrations
- ✅ Integration tests can remain largely unchanged

## Migration Strategy

### Phase 1: Create New Structure (Week 1)

**Goal**: Set up new package structure and create one example use case service

**Tasks**:
1. Create `service/application/`, `service/caseworker/`, `service/individual/` packages
2. Move shared services to `service/shared/`
3. Implement `GetAllCaseworkersService` as proof of concept (simplest use case)
4. Update `CaseworkerController` to use new service
5. Run tests to verify no regression

**Deliverable**: One fully migrated use case with passing tests

### Phase 2: Migrate Simple Query Operations (Week 1-2)

**Goal**: Migrate read-only operations with minimal dependencies

**Use Cases**:
- `GetApplicationByIdService`
- `SearchIndividualsService`
- `GetApplicationHistoryService`

**Tasks**:
1. Create service classes
2. Extract logic from existing services
3. Update controllers
4. Write unit tests for each service
5. Keep old services temporarily for backward compatibility

**Deliverable**: 4 use case services migrated

### Phase 3: Migrate Simple Command Operations (Week 2-3)

**Goal**: Migrate operations that modify data but have few dependencies

**Use Cases**:
- `CreateApplicationService`
- `UpdateApplicationService`

**Tasks**:
1. Create service classes with transaction management
2. Extract and refactor business logic
3. Update controllers
4. Write comprehensive unit tests
5. Verify integration tests pass

**Deliverable**: 6 use case services migrated

### Phase 4: Migrate Complex Operations (Week 3-4)

**Goal**: Migrate operations with multiple dependencies and business logic

**Use Cases**:
- `AssignCaseworkerService`
- `UnassignCaseworkerService`
- `MakeDecisionService`
- `SearchApplicationsService`

**Tasks**:
1. Identify and extract all dependencies
2. Create service classes
3. Refactor complex business logic for clarity
4. Update controllers
5. Write unit and integration tests
6. Performance testing for search operations

**Deliverable**: All 10 use case services migrated

### Phase 5: Cleanup and Deprecation (Week 4)

**Goal**: Remove old service classes and finalize migration

**Tasks**:
1. Verify no code references old service classes
2. Delete `ApplicationService`, `ApplicationSummaryService`, etc.
3. Update all tests to use new services
4. Code review and refactoring
5. Update documentation

**Deliverable**: Clean codebase with only use case services

### Phase 6: Documentation and Training (Week 5)

**Goal**: Document new patterns and train team

**Tasks**:
1. Create architecture decision record (ADR)
2. Update developer documentation
3. Create examples and best practices guide
4. Team presentation and Q&A
5. Update onboarding materials

**Deliverable**: Team fully trained on new approach

## Testing Strategy

### Unit Testing Benefits

With focused use case services, unit tests become simpler:

**Before** (testing one method in large service):
```java
@Test
void testCreateApplication() {
    // Mock 8 different dependencies
    // Only use 2 of them
    // Test one method
}
```

**After** (testing focused service):
```java
@Test
void testExecute() {
    // Mock only 2 required dependencies
    // Test the entire service
    // Clear, focused test
}
```

### Testing Approach Per Use Case

- **Query Operations**: Mock repositories, verify correct data retrieval
- **Command Operations**: Mock repositories, verify state changes and persistence calls
- **Complex Operations**: Mock all dependencies, verify orchestration logic

### Integration Testing

Integration tests remain largely unchanged - they test the full stack through controllers.

## Benefits of This Approach

### Immediate Benefits

1. **Better Testability**
   - Smaller services with fewer dependencies
   - Easier to write focused unit tests
   - Reduced test setup complexity

2. **Clearer Code Organization**
   - One service = one business operation
   - Easy to locate code for specific features
   - Package structure reflects business domains

3. **Improved Maintainability**
   - Changes to one operation don't risk breaking others
   - Smaller files are easier to understand
   - Clear naming makes intent obvious

4. **Parallel Development**
   - Multiple developers can work on different use cases
   - Reduced merge conflicts
   - Easier code reviews (smaller changes)

### Long-term Benefits

5. **Easier Refactoring**
   - Can refactor individual use cases without affecting others
   - Foundation for future architectural improvements
   - Can evolve to full Clean Architecture if needed

6. **Better Documentation**
   - Service names document the business operations
   - Easy to generate operation inventory
   - Clear for new team members

7. **Flexibility**
   - Can add cross-cutting concerns per use case
   - Can optimize individual operations independently
   - Can add telemetry and monitoring per operation

## What We're NOT Doing

To keep this refactoring pragmatic and low-risk:

❌ **No Full Ports/Adapters Pattern** - Not introducing domain/infrastructure boundaries everywhere
❌ **No Separate Domain Models** - Continue using JPA entities for domain logic
❌ **No Heavy Entity Mappers** - Simple API DTO → Internal Command mapping only
❌ **No Framework Independence** - Embrace Spring throughout (but with some abstraction)
❌ **No Major Architectural Changes** - Evolutionary improvement only

### What We ARE Doing (Pragmatic Abstractions)

✅ **API DTO → Internal Command Mapping** - Thin layer at controller to separate API from business logic
✅ **Internal Command → Entity Mapping** - Using existing MapStruct mappers
✅ **Entity → API DTO Mapping** - For responses, using existing mappers
✅ **Persistence Interfaces (Selective)** - For complex queries with custom DTOs, introduce lightweight persistence interfaces
✅ **Custom Repository Pattern** - For complex joins and projections, use custom repositories behind interfaces

This is much lighter than full Clean Architecture which requires Domain Models completely separate from Entities, but gives us flexibility where we need it for performance and complex queries.

## Comparison: Full vs Pragmatic Refactoring

| Aspect | Full Clean Architecture | Pragmatic Approach |
|--------|------------------------|-------------------|
| **Complexity** | High - ports, adapters, mappers | Low-Medium - focused services + selective interfaces |
| **Timeline** | 7 weeks | 4-5 weeks |
| **Risk** | High - major architectural change | Low - incremental evolution |
| **Learning Curve** | Steep - new patterns | Gentle - familiar Spring patterns |
| **Code Volume** | Significant increase | Moderate increase |
| **Benefits** | Maximum flexibility | 75% of benefits, 35% of effort |
| **Framework Independence** | Yes - full | Partial - selective abstractions |
| **Testability** | Excellent | Very Good (excellent with persistence interfaces) |
| **Maintainability** | Excellent | Very Good |
| **Migration Effort** | High | Medium |
| **Reversibility** | Difficult | Moderate |
| **Custom Queries** | Via ports/adapters | Via persistence interfaces (when needed) |
| **DTO Projections** | Via domain models | Via projection DTOs directly |

## Success Metrics

### Quantitative Metrics

1. **Unit Test Coverage**: Increase from current baseline to >85%
2. **Service Class Size**: Average class lines of code <150
3. **Cyclomatic Complexity**: Reduce average complexity by 30%
4. **Build Time**: No increase in build/test time
5. **Code Duplication**: Maintain <3% duplication

### Qualitative Metrics

6. **Developer Satisfaction**: Survey team after 3 months
7. **Code Review Time**: Measure time to review PRs
8. **Bug Rate**: Track defect rate for 3 months post-migration
9. **Feature Velocity**: Time from story start to production
10. **Onboarding Time**: Time for new developers to become productive

## Risks and Mitigations

### Risk 1: Incomplete Migration
**Impact**: Code exists in both old and new patterns
**Mitigation**: 
- Clear timeline and commitment to complete migration
- Regular progress tracking
- Mark old services as `@Deprecated` immediately

### Risk 2: Inconsistent Patterns
**Impact**: Different developers implement use cases differently
**Mitigation**:
- Create template/example service early
- Code review checklist
- Pair programming for first few migrations

### Risk 3: Increased Class Count
**Impact**: More files to navigate, perceived complexity
**Mitigation**:
- Clear naming conventions make navigation easy
- Package-by-feature helps organization
- IDE support (e.g., IntelliJ's search) mitigates this

### Risk 4: Service Method Duplication
**Impact**: Similar code across multiple use case services
**Mitigation**:
- Create shared utilities in `service/shared/`
- Extract common logic to helper classes
- Accept some duplication for independence

### Risk 5: Team Resistance
**Impact**: Developers prefer familiar large services
**Mitigation**:
- Demonstrate benefits with proof of concept
- Show improved testability early
- Gather feedback and adjust approach

## Recommendations

### Getting Started

1. **Start with Caseworkers**: Simplest domain, quickest win
2. **Create Template**: Document the pattern with first example
3. **Team Review**: Get feedback after first 2-3 migrations
4. **Iterate**: Adjust approach based on learnings
5. **Measure**: Track metrics to demonstrate value

### Best Practices

1. **Keep Services Small**: If `execute()` is >50 lines, consider splitting logic
2. **Inject Only What's Needed**: Each service should have minimal dependencies
3. **Use `@Transactional` Wisely**: Apply at service level for command operations
4. **Consistent Error Handling**: Use existing exception hierarchy
5. **Logging**: Use existing logging aspects consistently

### When to Stop

This approach is complete when:
- ✅ All controller operations delegate to use case services
- ✅ Old multi-purpose services are deleted
- ✅ Tests pass at previous or higher coverage
- ✅ Team is comfortable with the pattern
- ✅ Documentation is updated

### Future Evolution

This pragmatic refactoring creates a foundation for future improvements:

**If business complexity grows**, you can:
- Introduce ports for specific use cases that need flexibility
- Extract domain models for complex business logic
- Add adapters for external integrations

**But only when needed** - don't over-engineer early.

## Related Resources

### Books
- **Refactoring: Improving the Design of Existing Code** by Martin Fowler
- **Working Effectively with Legacy Code** by Michael Feathers
- **Clean Code** by Robert C. Martin

### Articles
- **[Refactoring to Use Case Services](https://martinfowler.com/bliki/ServiceLayer.html)** - Martin Fowler
- **[Organizing Application Logic](https://docs.spring.io/spring-framework/reference/core/beans/classpath-scanning.html)** - Spring Documentation
- **[Test-Driven Refactoring](https://www.jamesshore.com/v2/books/aoad1/test_driven_development)** - James Shore

### Internal Resources
- See existing `conventional_commits.md` for commit message standards
- Follow existing `pre-commit-hooks.md` for code quality checks
- Reference `deployment.md` for deployment considerations

## Appendix

### A. Example: Before and After

#### Before: Large ApplicationService

```
ApplicationService (500+ lines)
├── createApplication()
├── updateApplication()
├── getApplication()
├── getAllApplications()
├── assignCaseworker()
├── unassignCaseworker()
├── getApplicationHistory()
├── makeDecision()
└── [15+ dependencies injected]
```

#### After: Focused Use Case Services

```
application/
├── CreateApplicationService (50 lines, 2 dependencies)
├── UpdateApplicationService (60 lines, 3 dependencies)
├── GetApplicationByIdService (30 lines, 1 dependency)
├── SearchApplicationsService (80 lines, 1 dependency)
├── AssignCaseworkerService (70 lines, 3 dependencies)
├── UnassignCaseworkerService (50 lines, 2 dependencies)
├── GetApplicationHistoryService (40 lines, 1 dependency)
└── MakeDecisionService (90 lines, 3 dependencies)
```

### B. Decision Record Template

When implementing this refactoring, create an ADR:

**Title**: Refactor Services to Use Case Pattern

**Status**: Accepted

**Context**: Large service classes make testing difficult and obscure business operations

**Decision**: Split services into focused use case services, one per business operation

**Consequences**: 
- Positive: Better testability, clearer code organization
- Negative: More class files, need to update existing code

**Alternatives Considered**: Full Clean Architecture (rejected as too complex)

### C. Code Review Checklist

When reviewing use case service PRs:

- [ ] Service name clearly indicates business operation
- [ ] Single `execute()` method with clear parameters
- [ ] Minimal dependencies (only what's needed)
- [ ] `@Transactional` applied if modifying data
- [ ] Unit tests cover happy path and error cases
- [ ] Controller updated to use new service
- [ ] Old service method removed (or deprecated)
- [ ] Logging aspects applied correctly
- [ ] Follows existing code style and conventions

### D. Rollback Plan

If the refactoring needs to be rolled back:

1. Old services are kept temporarily with `@Deprecated`
2. Git branches allow reverting changes
3. Feature flags can control which services are used
4. Each phase is independently deployable
5. Integration tests verify API contracts unchanged

---

**Document Version**: 1.0  
**Date**: March 13, 2026  
**Author**: Technical Architecture Team  
**Status**: Proposal - Recommended Approach  
**Alternative To**: Full Clean Architecture Refactoring Proposal








