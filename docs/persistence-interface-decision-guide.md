# Decision Guide: When to Use Persistence Interfaces

## Quick Decision Flowchart

```
┌─────────────────────────────────────────────────────┐
│ Do you need to implement a data access operation?   │
└─────────────────────┬───────────────────────────────┘
                      │
                      ▼
        ┌─────────────────────────────┐
        │ Is it a simple CRUD         │
        │ operation?                  │
        │ (findById, save, delete)    │
        └──────┬──────────────┬───────┘
               │              │
           Yes │              │ No
               │              │
               ▼              ▼
    ┌──────────────┐   ┌────────────────┐
    │ Use Spring   │   │ Does it need   │
    │ Data JPA     │   │ joins across   │
    │ directly     │   │ multiple       │
    │              │   │ tables?        │
    └──────────────┘   └───┬────────┬───┘
                           │        │
                       Yes │        │ No
                           │        │
                           ▼        ▼
                    ┌──────────┐  ┌──────────┐
                    │ Use      │  │ Can      │
                    │ Persist. │  │ Spring   │
                    │ Interface│  │ Data     │
                    │ +        │  │ handle   │
                    │ Custom   │  │ it?      │
                    │ Repo     │  └────┬─────┘
                    └──────────┘       │
                           ▲       Yes │ No
                           │           │  │
                           │           ▼  ▼
                           │      ┌────────────┐
                           └──────┤ Simple?    │
                                  │ Use Spring │
                                  │ Data.      │
                                  │ Complex?   │
                                  │ Use Persist│
                                  │ Interface  │
                                  └────────────┘
```

## Decision Criteria

### Use Spring Data JPA Directly

✅ **When:**
- Simple CRUD operations (findById, save, delete)
- Single entity queries
- Standard Spring Data methods sufficient
- No complex joins needed
- Full entity is acceptable

**Example:**
```java
@Service
@RequiredArgsConstructor
public class GetApplicationByIdService {
    private final ApplicationRepository repository;  // Direct Spring Data
    
    public Application execute(UUID id) {
        ApplicationEntity entity = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(id));
        return mapper.toDto(entity);
    }
}
```

### Use Persistence Interface + Custom Repository

✅ **When:**
- Complex queries with multiple joins
- Need DTO projections (select specific fields only)
- Dynamic query building based on criteria
- Performance-critical read operations
- Might need different persistence strategy in future
- Want to decouple from JPA specifics

**Example:**
```java
@Service
@RequiredArgsConstructor
public class SearchApplicationsService {
    private final ApplicationPersistence persistence;  // Interface
    
    public PaginatedResult<ApplicationSummaryDto> execute(SearchApplicationsQuery query) {
        ApplicationSearchCriteria criteria = buildCriteria(query);
        Page<ApplicationSummaryDto> page = persistence.searchApplications(criteria, pageable);
        return PaginatedResult.from(page);
    }
}
```

## Specific Scenarios

### Scenario 1: Get Single Entity by ID
**Decision:** Use Spring Data directly
**Reason:** Simple operation, no complexity, full entity needed

```java
Optional<ApplicationEntity> findById(UUID id)  // Spring Data method
```

### Scenario 2: Search with Multiple Filters
**Decision:** Use Persistence Interface
**Reason:** Dynamic criteria, might need joins, benefits from abstraction

```java
Page<ApplicationSummaryDto> searchApplications(
    ApplicationSearchCriteria criteria, 
    Pageable pageable
)  // Custom method behind interface
```

### Scenario 3: Get Entity by Unique Field
**Decision:** Use Spring Data directly
**Reason:** Simple query, single entity, no joins

```java
ApplicationEntity findByLaaReference(String laaReference)  // Spring Data derived query
```

### Scenario 4: Get Applications with Caseworker Details
**Decision:** Use Persistence Interface
**Reason:** Joins multiple tables, DTO projection for performance

```java
List<ApplicationWithCaseworkerDto> findApplicationsWithCaseworker(UUID caseworkerId)  // Custom query
```

### Scenario 5: Count by Status
**Decision:** Could go either way
- **Simple count**: Spring Data - `long countByStatus(ApplicationStatus status)`
- **Grouped counts**: Persistence Interface - `List<StatusCountDto> getCountsByStatus()`

### Scenario 6: Update Single Field
**Decision:** Use Spring Data directly
**Reason:** Simple operation, fetch entity → modify → save

```java
ApplicationEntity app = repository.findById(id).orElseThrow();
app.setStatus(newStatus);
repository.save(app);
```

### Scenario 7: Complex Report Query
**Decision:** Use Persistence Interface
**Reason:** Aggregations, joins, grouping - complex query that returns specialized DTO

```java
List<ApplicationReportDto> generateApplicationReport(ReportCriteria criteria)  // Custom query
```

## Implementation Decision Matrix

| Operation Type | Joins | Projections | Performance Critical | Decision |
|---------------|-------|-------------|---------------------|----------|
| findById | No | No | No | Spring Data |
| findByField | No | No | No | Spring Data |
| save/update | No | No | No | Spring Data |
| delete | No | No | No | Spring Data |
| Search with filters | Yes | Yes | Yes | Persistence Interface |
| List with related entities | Yes | No | No | Could go either way* |
| Aggregations/Stats | Yes | Yes | Maybe | Persistence Interface |
| Bulk operations | No | No | No | Spring Data |
| Complex reports | Yes | Yes | Yes | Persistence Interface |

*If simple join with full entity fetch, Spring Data is fine. If need DTO projection, use persistence interface.

## Current Operations Analysis

### ApplicationController Operations

1. **createApplication** → Spring Data (simple save)
2. **updateApplication** → Spring Data (findById + save)
3. **getApplicationById** → Spring Data (findById)
4. **getApplications** → **Persistence Interface** (complex search with filters, joins, pagination)
5. **assignCaseworker** → Spring Data (findById + update)
6. **unassignCaseworker** → Spring Data (findById + update)
7. **getApplicationHistory** → Spring Data or Persistence Interface (depends on complexity)
8. **makeDecision** → Spring Data (findById + create decision)

### CaseworkerController Operations

1. **getCaseworkers** → Spring Data (findAll)

### IndividualsController Operations

1. **getIndividuals** → **Persistence Interface** (filters, pagination, potentially joins)

## Migration Priority

### Phase 1: High-Value Operations
1. ✅ `SearchApplicationsService` - Complex search with filters and joins
2. ✅ `SearchIndividualsService` - Filtered search with pagination

### Phase 2: Performance-Critical Operations
3. Application reports/statistics (if exists)
4. Dashboard queries (if exists)

### Phase 3: Operations That Might Evolve
5. Any operation that might need caching
6. Any operation that might need alternative storage

### Phase 4: Simple Operations
7. Keep using Spring Data directly - don't over-engineer

## Code Smells Indicating Need for Persistence Interface

🚩 **Red Flags:**
- JPA Specification becoming complex and hard to maintain
- N+1 query problems
- Fetching full entities when only need few fields
- Slow query performance on high-traffic endpoints
- Multiple joins in service layer
- Business logic mixed with query construction

✅ **Green Lights:**
- Simple findById, save, delete operations
- Single-entity queries
- Low-traffic operations
- Full entity needed anyway

## Testing Strategy

### With Spring Data (Direct)
```java
@ExtendWith(MockitoExtension.class)
class GetApplicationByIdServiceTest {
    @Mock
    private ApplicationRepository repository;  // Mock Spring Data
    
    @Test
    void shouldGetApplication() {
        when(repository.findById(id)).thenReturn(Optional.of(entity));
        // Test logic
    }
}
```

### With Persistence Interface
```java
@ExtendWith(MockitoExtension.class)
class SearchApplicationsServiceTest {
    @Mock
    private ApplicationPersistence persistence;  // Mock interface
    
    @Test
    void shouldSearchApplications() {
        when(persistence.searchApplications(any(), any())).thenReturn(page);
        // Test logic
    }
}
```

Both are easy to test, but persistence interface gives you more control over what to mock.

## Best Practices

### ✅ DO:
- Use persistence interfaces for complex queries
- Return DTOs from complex queries
- Keep interface methods focused on business operations
- Write integration tests for custom queries
- Document performance characteristics

### ❌ DON'T:
- Use persistence interfaces for everything (over-engineering)
- Return entities from complex multi-join queries (performance)
- Create one generic persistence interface (keep them domain-specific)
- Skip testing custom queries (they need integration tests)

## Summary

**Simple Rule:**
- **Simple operation?** → Use Spring Data directly
- **Complex query with joins/projections?** → Use Persistence Interface + Custom Repository
- **Not sure?** → Start with Spring Data, refactor to persistence interface when complexity increases

**Remember:** This is pragmatic refactoring. You don't need to introduce persistence interfaces everywhere. Use them where they provide clear value:
1. Performance optimization (DTO projections)
2. Complex query logic
3. Decoupling from JPA specifics
4. Operations that might evolve

For everything else, Spring Data JPA is perfectly fine and keeps things simple.

---

**Last Updated**: March 13, 2026
**Quick Reference for**: Deciding when to use persistence interfaces

