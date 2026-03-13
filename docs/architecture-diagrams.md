# Architecture Diagrams

## Complete Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           CLIENT                                        │
│                    (REST API Consumer)                                  │
└────────────────────────────┬────────────────────────────────────────────┘
                             │ HTTP Request
                             │ (ApplicationCreateRequest - OpenAPI DTO)
                             ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                    PRESENTATION LAYER                                   │
│  ┌─────────────────────────────────────────────────────────────┐       │
│  │ ApplicationController                                        │       │
│  │ - Receives: ApplicationCreateRequest (API DTO)              │       │
│  │ - Maps to: CreateApplicationCommand (Internal)              │       │
│  │ - Calls: CreateApplicationService.execute(command)          │       │
│  └─────────────────────────────────────────────────────────────┘       │
└────────────────────────────┬────────────────────────────────────────────┘
                             │ Internal Command
                             │ (CreateApplicationCommand)
                             ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                    APPLICATION LAYER                                    │
│  ┌─────────────────────────────────────────────────────────────┐       │
│  │ CreateApplicationService                                     │       │
│  │ - Receives: CreateApplicationCommand                         │       │
│  │ - Business validation                                        │       │
│  │ - Business logic                                            │       │
│  │ - Calls: ApplicationRepository.save()                       │       │
│  └─────────────────────────────────────────────────────────────┘       │
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────┐       │
│  │ SearchApplicationsService (Complex Query)                    │       │
│  │ - Receives: SearchApplicationsQuery                         │       │
│  │ - Builds: ApplicationSearchCriteria                         │       │
│  │ - Calls: ApplicationPersistence.searchApplications()        │       │
│  └─────────────────────────────────────────────────────────────┘       │
└────────────────────────────┬────────────────────────────────────────────┘
                             │
                 ┌───────────┴────────────┐
                 │                        │
          Simple │                        │ Complex
         Operation                    Operation
                 │                        │
                 ▼                        ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                    PERSISTENCE LAYER                                    │
│                                                                         │
│  ┌──────────────────────────┐      ┌───────────────────────────────┐   │
│  │ Spring Data Repository   │      │ Persistence Interface          │   │
│  │ (Simple Operations)      │      │ (Complex Operations)           │   │
│  │                          │      │                                │   │
│  │ ApplicationRepository    │      │ ApplicationPersistence         │   │
│  │ - findById()            │      │ - searchApplications()         │   │
│  │ - save()                │      │ - complex queries              │   │
│  │ - delete()              │      │                                │   │
│  └──────────────────────────┘      └───────────┬───────────────────┘   │
│                                                 │                       │
│                                          ┌──────┴────────┐              │
│                                          │               │              │
│                               ┌──────────▼──────┐  ┌────▼─────────────┐│
│                               │ Persistence     │  │ Custom Repository││
│                               │ Adapter         │  │ (DTO Queries)    ││
│                               │ - Delegates to  │  │ - EntityManager  ││
│                               │   Spring Data   │  │ - JPQL/Native    ││
│                               │ - Delegates to  │  │ - DTO Projection ││
│                               │   Custom Repo   │  │                  ││
│                               └─────────────────┘  └──────────────────┘│
└────────────────────────────┬────────────────────────────────────────────┘
                             │ JPA Operations
                             ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                         DATABASE                                        │
│                    (PostgreSQL Tables)                                  │
└─────────────────────────────────────────────────────────────────────────┘
```

## Model Flow Diagram

```
┌──────────────────────┐
│ API DTOs             │
│ (OpenAPI Generated)  │
│                      │
│ - Request objects    │
│ - Response objects   │
│                      │
│ Location:            │
│ model/               │
│                      │
│ Used by:             │
│ Controllers only     │
└──────────┬───────────┘
           │ Maps to
           ▼
┌──────────────────────┐
│ Internal Models      │
│ (Commands/Queries)   │
│                      │
│ - Commands (write)   │
│ - Queries (read)     │
│                      │
│ Location:            │
│ service/*/dto/       │
│                      │
│ Used by:             │
│ Use case services    │
└──────────┬───────────┘
           │ Maps to
           ▼
┌──────────────────────┐
│ JPA Entities         │
│                      │
│ - Database mappings  │
│ - Relationships      │
│                      │
│ Location:            │
│ entity/              │
│                      │
│ Used by:             │
│ Repositories         │
└──────────────────────┘

PLUS (for complex queries):

┌──────────────────────┐
│ Projection DTOs      │
│                      │
│ - Query results      │
│ - Selected fields    │
│ - Optimized          │
│                      │
│ Location:            │
│ dto/                 │
│                      │
│ Returned by:         │
│ Custom repositories  │
└──────────────────────┘
```

## Package Structure Diagram

```
uk.gov.justice.laa.dstew.access/
│
├── controller/                      [PRESENTATION LAYER]
│   ├── ApplicationController.java       → Maps API DTO to Internal Model
│   ├── CaseworkerController.java
│   └── IndividualsController.java
│
├── service/                         [APPLICATION LAYER]
│   ├── application/
│   │   ├── CreateApplicationService.java    → Business Logic
│   │   ├── SearchApplicationsService.java   → Business Logic
│   │   ├── dto/                             → Internal Models
│   │   │   ├── CreateApplicationCommand.java
│   │   │   └── SearchApplicationsQuery.java
│   │   └── persistence/                     → Interface Contracts
│   │       └── ApplicationPersistence.java
│   ├── caseworker/
│   └── individual/
│
├── repository/                      [PERSISTENCE LAYER]
│   ├── ApplicationRepository.java       → Spring Data (simple operations)
│   ├── adapter/
│   │   └── ApplicationPersistenceAdapter.java → Implements interface
│   └── custom/
│       ├── ApplicationCustomRepository.java    → Interface for custom queries
│       └── ApplicationCustomRepositoryImpl.java → EntityManager queries
│
├── entity/                          [DATA MODEL]
│   ├── ApplicationEntity.java       → JPA entities
│   └── CaseworkerEntity.java
│
├── dto/                             [QUERY RESULTS]
│   ├── ApplicationSummaryDto.java   → Projection DTOs
│   └── ApplicationWithCaseworkerDto.java
│
├── mapper/                          [MAPPING]
│   └── ApplicationMapper.java       → MapStruct mappers
│
└── model/                           [API CONTRACTS]
    ├── ApplicationCreateRequest.java → OpenAPI DTOs
    └── Application.java
```

## Decision Flow Diagram

```
┌─────────────────────────────────────┐
│ Implement Data Access Operation     │
└───────────────┬─────────────────────┘
                │
                ▼
        ┌───────────────┐
        │ Simple CRUD?  │
        │ (single entity)│
        └───┬───────┬───┘
            │       │
        Yes │       │ No
            │       │
            ▼       ▼
    ┌──────────┐  ┌─────────────┐
    │ Use      │  │ Complex     │
    │ Spring   │  │ query with  │
    │ Data JPA │  │ joins?      │
    │          │  └──┬──────┬───┘
    │ Direct   │     │      │
    │ in       │  Yes│      │ No
    │ Service  │     │      │
    └──────────┘     │      ▼
                     │  ┌────────────┐
                     │  │ Need DTO   │
                     │  │ projection?│
                     │  └──┬──────┬──┘
                     │     │      │
                     │  Yes│      │ No
                     │     │      │
                     ▼     ▼      ▼
            ┌────────────────────────┐
            │ Use Persistence        │
            │ Interface +            │
            │ Custom Repository      │
            └────────────────────────┘
```

## Use Case Service Patterns

### Pattern A: Simple CRUD
```
┌──────────────────────────────────────┐
│ GetApplicationByIdService            │
├──────────────────────────────────────┤
│ Dependencies:                        │
│ - ApplicationRepository              │
│ - ApplicationMapper                  │
├──────────────────────────────────────┤
│ execute(UUID id)                     │
│   ↓                                  │
│ repository.findById(id)              │
│   ↓                                  │
│ mapper.toDto(entity)                 │
│   ↓                                  │
│ return dto                           │
└──────────────────────────────────────┘
```

### Pattern B: Command with Business Logic
```
┌──────────────────────────────────────┐
│ CreateApplicationService             │
├──────────────────────────────────────┤
│ Dependencies:                        │
│ - ApplicationRepository              │
│ - ApplicationMapper                  │
│ - ValidationService                  │
├──────────────────────────────────────┤
│ execute(CreateApplicationCommand cmd)│
│   ↓                                  │
│ validationService.validate(cmd)      │
│   ↓                                  │
│ entity = mapper.toEntity(cmd)        │
│   ↓                                  │
│ entity.setBusinessData()             │
│   ↓                                  │
│ saved = repository.save(entity)      │
│   ↓                                  │
│ return saved.getId()                 │
└──────────────────────────────────────┘
```

### Pattern C: Complex Query
```
┌──────────────────────────────────────┐
│ SearchApplicationsService            │
├──────────────────────────────────────┤
│ Dependencies:                        │
│ - ApplicationPersistence (interface) │
├──────────────────────────────────────┤
│ execute(SearchApplicationsQuery q)   │
│   ↓                                  │
│ criteria = buildCriteria(q)          │
│   ↓                                  │
│ pageable = buildPageable(q)          │
│   ↓                                  │
│ page = persistence.searchApplications│
│        (criteria, pageable)          │
│   ↓                                  │
│ return PaginatedResult.from(page)    │
└──────────────────────────────────────┘
```

## Persistence Layer Patterns

### Direct Spring Data (Simple)
```
┌────────────────────────────────┐
│ Use Case Service               │
└────────┬───────────────────────┘
         │
         │ Uses directly
         ▼
┌────────────────────────────────┐
│ ApplicationRepository          │
│ extends JpaRepository          │
│                                │
│ - findById()                   │
│ - save()                       │
│ - delete()                     │
│ - findByLaaReference()         │
└────────┬───────────────────────┘
         │
         ▼
┌────────────────────────────────┐
│ Database                       │
└────────────────────────────────┘
```

### With Persistence Interface (Complex)
```
┌────────────────────────────────┐
│ Use Case Service               │
└────────┬───────────────────────┘
         │
         │ Depends on interface
         ▼
┌────────────────────────────────┐
│ ApplicationPersistence         │
│ (interface)                    │
│                                │
│ - save()                       │
│ - findById()                   │
│ - searchApplications()         │
└────────┬───────────────────────┘
         │
         │ Implemented by
         ▼
┌────────────────────────────────┐
│ ApplicationPersistenceAdapter  │
│                                │
│ Delegates to:                  │
│ ├─→ ApplicationRepository      │
│ │   (simple operations)        │
│ └─→ ApplicationCustomRepository│
│     (complex queries)          │
└───┬────────────────────┬───────┘
    │                    │
    ▼                    ▼
┌─────────────┐  ┌──────────────────┐
│ Spring Data │  │ Custom Repo      │
│ JPA         │  │ with             │
│ Repository  │  │ EntityManager    │
│             │  │                  │
│ - findById()│  │ - complex JPQL   │
│ - save()    │  │ - native SQL     │
│             │  │ - DTO projection │
└──────┬──────┘  └────────┬─────────┘
       │                  │
       └────────┬─────────┘
                │
                ▼
┌────────────────────────────────┐
│ Database                       │
└────────────────────────────────┘
```

## Testing Architecture

```
┌──────────────────────────────────────────────────────────┐
│ INTEGRATION TESTS (Full Stack)                          │
│                                                          │
│  Controller → Service → Repository → Database           │
│                                                          │
│  @SpringBootTest                                        │
│  Tests API contracts and full flow                      │
└──────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────┐
│ UNIT TESTS (Use Case Services)                          │
│                                                          │
│  Service + Mocked Dependencies                          │
│                                                          │
│  @ExtendWith(MockitoExtension.class)                    │
│  @Mock ApplicationPersistence persistence               │
│  @InjectMocks SearchApplicationsService service         │
│                                                          │
│  Fast, isolated business logic tests                    │
└──────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────┐
│ INTEGRATION TESTS (Custom Repositories)                 │
│                                                          │
│  Custom Repository → EntityManager → Database           │
│                                                          │
│  @DataJpaTest                                           │
│  @Import(ApplicationCustomRepositoryImpl.class)         │
│                                                          │
│  Tests complex queries with real database               │
└──────────────────────────────────────────────────────────┘
```

## Dependency Direction

```
        Controllers
             ↓ (depends on)
        Use Case Services
             ↓ (depends on)
        Persistence Interfaces
             ↑ (implemented by)
        Persistence Adapters
             ↓ (uses)
    ┌────────┴─────────┐
    ↓                  ↓
Spring Data       Custom
Repositories     Repositories
    ↓                  ↓
    └────────┬─────────┘
             ↓
        JPA Entities
             ↓
         Database
```

**Key Principle**: Dependencies point inward. Business logic never depends on infrastructure details.

## Evolution Path

```
Current State
─────────────
Controller → Service → Repository

↓ (Refactor Step 1)

Split Services
──────────────
Controller → Use Case Service → Repository

↓ (Refactor Step 2)

Add Internal Models
───────────────────
Controller (API DTO) → Use Case Service (Internal Model) → Repository

↓ (Refactor Step 3 - Selective)

Add Persistence Interfaces
───────────────────────────
Controller (API DTO) → Use Case Service (Internal Model) → Persistence Interface → Custom Repository

↓ (Future - If Needed)

Full Clean Architecture
───────────────────────
Controller (API DTO) → Use Case (Command) → Domain Model ← Port → Adapter → Repository
```

## Summary

- **3 Main Layers**: Presentation, Application, Persistence
- **3 Model Types**: API DTOs, Internal Commands/Queries, JPA Entities
- **2 Persistence Strategies**: Direct Spring Data (simple), Persistence Interface (complex)
- **1 Direction**: Dependencies always point inward toward business logic

---

**Last Updated**: March 13, 2026  
**Visual Reference for**: Pragmatic Use Case Refactoring Architecture

