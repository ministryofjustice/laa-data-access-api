# Layering Strategy Summary

## Three-Layer Mapping Approach

Our pragmatic refactoring uses a **three-layer mapping strategy** to keep API concerns separate from business logic:

```
┌──────────────────────────────────────────────────────┐
│ API Layer (Controller)                               │
│ - Works with: OpenAPI-generated DTOs                 │
│ - Responsibilities: API validation, HTTP concerns    │
└────────────────────┬─────────────────────────────────┘
                     │ Maps API DTO → Internal Model
                     ▼
┌──────────────────────────────────────────────────────┐
│ Business Logic Layer (Use Case Service)              │
│ - Works with: Internal Commands/Queries              │
│ - Responsibilities: Business logic, orchestration    │
└────────────────────┬─────────────────────────────────┘
                     │ Maps Internal Model → Entity
                     ▼
┌──────────────────────────────────────────────────────┐
│ Data Access Layer (Repository)                       │
│ - Works with: JPA Entities                           │
│ - Responsibilities: Persistence, database queries    │
└──────────────────────────────────────────────────────┘
```

## Model Types

### 1. API DTOs (OpenAPI Generated)
- **Location**: `model/` package
- **Examples**: `ApplicationCreateRequest`, `ApplicationUpdateRequest`, `Application`
- **Used By**: Controllers only
- **Purpose**: Define API contract, can change with API versions
- **Annotations**: `@JsonProperty`, validation annotations

### 2. Internal Commands/Queries
- **Location**: `service/{domain}/dto/` package
- **Examples**: `CreateApplicationCommand`, `SearchApplicationsQuery`
- **Used By**: Use case services
- **Purpose**: Represent business operations, stable internal contract
- **Annotations**: `@Value`, `@Builder` (simple POJOs)

### 3. JPA Entities
- **Location**: `entity/` package
- **Examples**: `ApplicationEntity`, `CaseworkerEntity`
- **Used By**: Repositories and mappers
- **Purpose**: Database persistence
- **Annotations**: `@Entity`, `@Table`, JPA annotations

## Example Flow: Create Application

### Step 1: Controller Receives API DTO
```java
@RestController
public class ApplicationController {
    private final CreateApplicationService createApplication;
    
    @PostMapping("/applications")
    public ResponseEntity<Void> createApplication(
            @RequestBody ApplicationCreateRequest apiRequest) {  // ← OpenAPI DTO
        
        // Map API DTO to internal command
        CreateApplicationCommand command = 
            CreateApplicationCommand.from(apiRequest);
        
        UUID id = createApplication.execute(command);
        return ResponseEntity.created(buildUri(id)).build();
    }
}
```

### Step 2: Use Case Service Uses Internal Command
```java
@Service
public class CreateApplicationService {
    private final ApplicationRepository repository;
    private final ApplicationMapper mapper;
    
    public UUID execute(CreateApplicationCommand command) {  // ← Internal model
        // Business validation
        validateCommand(command);
        
        // Map to entity
        ApplicationEntity entity = mapper.toEntity(command);
        
        // Persist
        ApplicationEntity saved = repository.save(entity);
        return saved.getId();
    }
}
```

### Step 3: Mapper Converts to Entity
```java
@Mapper(componentModel = "spring")
public interface ApplicationMapper {
    ApplicationEntity toEntity(CreateApplicationCommand command);  // ← Maps internal → entity
    Application toDto(ApplicationEntity entity);                   // ← Maps entity → API DTO
}
```

## Why This Approach?

### ✅ Benefits

1. **API Evolution Independence**
   - Change OpenAPI spec → only update controllers
   - Business logic remains unchanged
   - Can support multiple API versions simultaneously

2. **Business Logic Clarity**
   - Internal models represent business concepts, not API contracts
   - No API concerns leak into business layer
   - Easier to understand and test

3. **Testability**
   - Unit test services with simple POJOs
   - No need to construct complex API DTOs
   - Mock-friendly internal models

4. **Flexibility**
   - Same service can support REST, GraphQL, gRPC
   - Each interface maps its DTOs to shared internal models
   - Business logic reused across interfaces

### ❌ Trade-offs

1. **More Classes**
   - Need internal command/query classes
   - More mapping code in controllers
   - **Mitigation**: Use factory methods, keep mapping simple

2. **Duplication of Fields**
   - Internal models may look similar to API DTOs
   - **Mitigation**: Only create internal models for complex operations

## When to Use Internal Models

### Always Use Internal Models For:
- ✅ Complex write operations (create, update, decision)
- ✅ Operations with significant business logic
- ✅ Multi-step workflows
- ✅ Operations that might be exposed via multiple interfaces

### Optional for Simple Operations:
- 🤷 Simple read-by-id operations
- 🤷 Direct pass-through to repository
- 🤷 No business validation required

**Rule of thumb**: If the controller does more than basic validation, use an internal model.

## Package Structure

```
uk.gov.justice.laa.dstew.access/
├── controller/
│   └── ApplicationController.java
│       → Uses API DTOs (ApplicationCreateRequest)
│       → Maps to internal models (CreateApplicationCommand)
│
├── service/
│   └── application/
│       ├── CreateApplicationService.java
│       │   → Uses internal models (CreateApplicationCommand)
│       │   → Calls repositories with entities
│       └── dto/
│           ├── CreateApplicationCommand.java (internal)
│           ├── UpdateApplicationCommand.java (internal)
│           └── SearchApplicationsQuery.java (internal)
│
├── mapper/
│   └── ApplicationMapper.java
│       → Maps internal models ↔ entities
│       → Maps entities ↔ API DTOs (for responses)
│
├── model/
│   ├── ApplicationCreateRequest.java (OpenAPI)
│   ├── ApplicationUpdateRequest.java (OpenAPI)
│   └── Application.java (OpenAPI)
│
└── entity/
    └── ApplicationEntity.java (JPA)
```

## Comparison: With vs Without Internal Models

### Without Internal Models (Risky)
```java
// Controller directly passes API DTO to service
@RestController
public class ApplicationController {
    @PostMapping
    public ResponseEntity<Void> create(ApplicationCreateRequest apiDto) {
        UUID id = service.execute(apiDto);  // ❌ Service coupled to API
        return ResponseEntity.created(buildUri(id)).build();
    }
}

// Service uses API DTO
@Service
public class CreateApplicationService {
    public UUID execute(ApplicationCreateRequest apiDto) {  // ❌ Business logic coupled to API contract
        // Business logic using API DTO
        // What if API v2 changes the DTO?
        // Business logic breaks!
    }
}
```

**Problems**:
- Business logic breaks when API changes
- Can't support multiple API versions
- Testing requires API DTOs
- Hard to add GraphQL/gRPC later

### With Internal Models (Recommended)
```java
// Controller maps API DTO to internal model
@RestController
public class ApplicationController {
    @PostMapping
    public ResponseEntity<Void> create(ApplicationCreateRequest apiDto) {
        CreateApplicationCommand cmd = CreateApplicationCommand.from(apiDto);
        UUID id = service.execute(cmd);  // ✅ Service uses internal model
        return ResponseEntity.created(buildUri(id)).build();
    }
}

// Service uses internal model
@Service
public class CreateApplicationService {
    public UUID execute(CreateApplicationCommand cmd) {  // ✅ Business logic independent of API
        // Business logic using internal model
        // API changes? Just update the controller mapping
        // Business logic unaffected!
    }
}
```

**Benefits**:
- Business logic stable when API evolves
- Can support multiple API versions
- Testing uses simple POJOs
- Easy to add new interfaces (GraphQL, gRPC)

## Migration Checklist

When creating a new use case service:

- [ ] Create internal command/query class in `service/{domain}/dto/`
- [ ] Add factory method `from(ApiDto)` for easy mapping
- [ ] Use `@Value` and `@Builder` for immutability
- [ ] Use case service accepts internal model, not API DTO
- [ ] Update ApplicationMapper to handle internal model → entity
- [ ] Controller maps API DTO → internal model before calling service
- [ ] Unit tests use internal models, not API DTOs
- [ ] Integration tests still test full API contract

## Further Reading

- See `pragmatic-use-case-refactoring.md` for full refactoring proposal
- See `use-case-refactoring-proposal.md` for full Clean Architecture approach
- Martin Fowler on [Data Transfer Objects](https://martinfowler.com/eaaCatalog/dataTransferObject.html)

---

**Last Updated**: March 13, 2026  
**Status**: Architectural Decision - Approved for Pragmatic Refactoring

