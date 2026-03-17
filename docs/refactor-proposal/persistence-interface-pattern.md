# Persistence Interface Pattern for Custom Repositories

## Overview

This document describes the **lightweight persistence interface pattern** for managing complex queries with custom DTOs and joins. This pattern provides a thin abstraction layer between use case services and Spring Data repositories without the full complexity of ports/adapters architecture.

## Problem Statement

As applications grow, you need:
- ✅ **Complex queries** with joins across multiple tables
- ✅ **DTO projections** that return only required fields (not full entities)
- ✅ **Performance optimization** to avoid N+1 queries and unnecessary data fetching
- ✅ **Read models** optimized separately from write models
- ✅ **Business logic decoupling** from JPA implementation details

### Example: Application Search

```java
// Need to search applications with:
// - Multiple filter criteria (status, reference, client name, date)
// - Join to caseworker table for caseworker name
// - Join to proceedings table for count
// - Pagination and sorting
// - Return only summary fields, not full entities
```

**Problem with direct JPA Repository**:
- Specification pattern becomes complex
- Fetching full entities is inefficient
- Business logic mixed with JPA query construction
- Hard to test without database

## Solution: Persistence Interface Pattern

```
┌─────────────────────────────────────────────────────────────┐
│ Use Case Service (Business Logic)                          │
│ - No JPA dependencies                                       │
│ - Works with persistence interface                          │
└────────────────────┬────────────────────────────────────────┘
                     │ Depends on interface
                     ▼
┌─────────────────────────────────────────────────────────────┐
│ Persistence Interface (Contract)                            │
│ - Defines what operations are available                     │
│ - Returns DTOs for complex queries                          │
│ - Returns entities for write operations                     │
└────────────────────┬────────────────────────────────────────┘
                     │ Implemented by
                     ▼
┌─────────────────────────────────────────────────────────────┐
│ Persistence Adapter                                          │
│ - Delegates to Spring Data + Custom Repository             │
│ - Implements the interface contract                         │
└───────────┬──────────────────────┬──────────────────────────┘
            │                      │
            ▼                      ▼
    ┌──────────────┐      ┌──────────────────┐
    │ Spring Data  │      │ Custom Repository│
    │ Repository   │      │ (Complex Queries)│
    └──────────────┘      └──────────────────┘
```

## Implementation Guide

### Step 1: Define Projection DTOs

Create lightweight DTOs for query results:

```java
// dto/ApplicationSummaryDto.java
package uk.gov.justice.laa.dstew.access.dto;

@Value
@Builder
public class ApplicationSummaryDto {
    UUID id;
    String laaReference;
    ApplicationStatus status;
    String clientFirstName;
    String clientLastName;
    LocalDate clientDateOfBirth;
    String caseworkerFirstName;
    String caseworkerLastName;
    Integer proceedingCount;
    LocalDateTime createdAt;
    Boolean isAutoGranted;
    
    // Constructor for JPQL projection
    public ApplicationSummaryDto(
            UUID id, 
            String laaReference, 
            ApplicationStatus status,
            String clientFirstName,
            String clientLastName,
            LocalDate clientDateOfBirth,
            String caseworkerFirstName,
            String caseworkerLastName,
            Long proceedingCount,
            LocalDateTime createdAt,
            Boolean isAutoGranted) {
        this.id = id;
        this.laaReference = laaReference;
        this.status = status;
        this.clientFirstName = clientFirstName;
        this.clientLastName = clientLastName;
        this.clientDateOfBirth = clientDateOfBirth;
        this.caseworkerFirstName = caseworkerFirstName;
        this.caseworkerLastName = caseworkerLastName;
        this.proceedingCount = proceedingCount != null ? proceedingCount.intValue() : 0;
        this.createdAt = createdAt;
        this.isAutoGranted = isAutoGranted;
    }
}
```

### Step 2: Define Search Criteria

Create criteria objects for complex queries:

```java
// dto/ApplicationSearchCriteria.java
package uk.gov.justice.laa.dstew.access.dto;

@Value
@Builder
public class ApplicationSearchCriteria {
    ApplicationStatus status;
    String laaReference;
    String clientFirstName;
    String clientLastName;
    LocalDate clientDateOfBirth;
    UUID userId;
    Boolean isAutoGranted;
    MatterType matterType;
    ApplicationSortBy sortBy;
    ApplicationOrderBy orderBy;
}
```

### Step 3: Define Persistence Interface

Create an interface that defines all persistence operations for a domain:

```java
// service/application/persistence/ApplicationPersistence.java
package uk.gov.justice.laa.dstew.access.service.application.persistence;

public interface ApplicationPersistence {
    
    // === Write Operations (work with entities) ===
    
    ApplicationEntity save(ApplicationEntity entity);
    
    List<ApplicationEntity> saveAll(List<ApplicationEntity> entities);
    
    void delete(UUID id);
    
    // === Simple Read Operations (work with entities) ===
    
    Optional<ApplicationEntity> findById(UUID id);
    
    Optional<ApplicationEntity> findByApplyApplicationId(UUID applyApplicationId);
    
    boolean existsById(UUID id);
    
    boolean existsByApplyApplicationId(UUID applyApplicationId);
    
    List<ApplicationEntity> findAllByApplyApplicationIds(List<UUID> applyApplicationIds);
    
    // === Complex Query Operations (return DTOs for performance) ===
    
    /**
     * Search applications with complex criteria and return summary DTOs.
     * Uses joins and projections for optimal performance.
     */
    Page<ApplicationSummaryDto> searchApplications(
        ApplicationSearchCriteria criteria,
        Pageable pageable
    );
    
    /**
     * Find applications with their caseworker details.
     */
    List<ApplicationWithCaseworkerDto> findApplicationsWithCaseworker(UUID caseworkerId);
    
    /**
     * Find linked applications for a lead application.
     */
    List<LinkedApplicationDto> findLinkedApplications(UUID leadApplicationId);
    
    /**
     * Get application statistics grouped by status.
     */
    List<ApplicationStatusCountDto> getApplicationStatistics();
}
```

### Step 4: Create Custom Repository Interface

Define custom query methods:

```java
// repository/custom/ApplicationCustomRepository.java
package uk.gov.justice.laa.dstew.access.repository.custom;

public interface ApplicationCustomRepository {
    
    Page<ApplicationSummaryDto> searchApplications(
        ApplicationSearchCriteria criteria,
        Pageable pageable
    );
    
    List<ApplicationWithCaseworkerDto> findApplicationsWithCaseworker(UUID caseworkerId);
    
    List<LinkedApplicationDto> findLinkedApplications(UUID leadApplicationId);
    
    List<ApplicationStatusCountDto> getApplicationStatistics();
}
```

### Step 5: Implement Custom Repository

Implement complex queries using EntityManager:

```java
// repository/custom/ApplicationCustomRepositoryImpl.java
package uk.gov.justice.laa.dstew.access.repository.custom;

@Repository
@RequiredArgsConstructor
public class ApplicationCustomRepositoryImpl implements ApplicationCustomRepository {
    
    private final EntityManager entityManager;
    
    @Override
    public Page<ApplicationSummaryDto> searchApplications(
            ApplicationSearchCriteria criteria,
            Pageable pageable) {
        
        // Build dynamic query based on criteria
        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append("""
            SELECT new uk.gov.justice.laa.dstew.access.dto.ApplicationSummaryDto(
                a.id,
                a.laaReference,
                a.status,
                a.clientFirstName,
                a.clientLastName,
                a.clientDateOfBirth,
                c.firstName,
                c.lastName,
                COUNT(p.id),
                a.createdAt,
                a.isAutoGranted
            )
            FROM ApplicationEntity a
            LEFT JOIN a.caseworker c
            LEFT JOIN a.proceedings p
            WHERE 1=1
            """);
        
        // Add dynamic filters
        if (criteria.getStatus() != null) {
            queryBuilder.append("AND a.status = :status ");
        }
        if (criteria.getLaaReference() != null) {
            queryBuilder.append("AND a.laaReference LIKE :laaReference ");
        }
        if (criteria.getClientFirstName() != null) {
            queryBuilder.append("AND LOWER(a.clientFirstName) LIKE LOWER(:clientFirstName) ");
        }
        if (criteria.getClientLastName() != null) {
            queryBuilder.append("AND LOWER(a.clientLastName) LIKE LOWER(:clientLastName) ");
        }
        if (criteria.getClientDateOfBirth() != null) {
            queryBuilder.append("AND a.clientDateOfBirth = :clientDateOfBirth ");
        }
        if (criteria.getUserId() != null) {
            queryBuilder.append("AND c.id = :userId ");
        }
        if (criteria.getIsAutoGranted() != null) {
            queryBuilder.append("AND a.isAutoGranted = :isAutoGranted ");
        }
        if (criteria.getMatterType() != null) {
            queryBuilder.append("AND EXISTS (SELECT 1 FROM ProceedingEntity p2 WHERE p2.application = a AND p2.matterType = :matterType) ");
        }
        
        queryBuilder.append("""
            GROUP BY a.id, a.laaReference, a.status, a.clientFirstName, 
                     a.clientLastName, a.clientDateOfBirth, c.firstName, 
                     c.lastName, a.createdAt, a.isAutoGranted
            """);
        
        // Add sorting
        if (criteria.getSortBy() != null) {
            queryBuilder.append("ORDER BY ");
            queryBuilder.append(mapSortField(criteria.getSortBy()));
            queryBuilder.append(" ");
            queryBuilder.append(criteria.getOrderBy() != null ? criteria.getOrderBy() : "ASC");
        }
        
        // Create query
        TypedQuery<ApplicationSummaryDto> query = entityManager.createQuery(
            queryBuilder.toString(), 
            ApplicationSummaryDto.class
        );
        
        // Set parameters
        if (criteria.getStatus() != null) {
            query.setParameter("status", criteria.getStatus());
        }
        if (criteria.getLaaReference() != null) {
            query.setParameter("laaReference", "%" + criteria.getLaaReference() + "%");
        }
        if (criteria.getClientFirstName() != null) {
            query.setParameter("clientFirstName", "%" + criteria.getClientFirstName() + "%");
        }
        if (criteria.getClientLastName() != null) {
            query.setParameter("clientLastName", "%" + criteria.getClientLastName() + "%");
        }
        if (criteria.getClientDateOfBirth() != null) {
            query.setParameter("clientDateOfBirth", criteria.getClientDateOfBirth());
        }
        if (criteria.getUserId() != null) {
            query.setParameter("userId", criteria.getUserId());
        }
        if (criteria.getIsAutoGranted() != null) {
            query.setParameter("isAutoGranted", criteria.getIsAutoGranted());
        }
        if (criteria.getMatterType() != null) {
            query.setParameter("matterType", criteria.getMatterType());
        }
        
        // Apply pagination
        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());
        
        // Execute query
        List<ApplicationSummaryDto> results = query.getResultList();
        
        // Count query for total
        long total = countApplications(criteria);
        
        return new PageImpl<>(results, pageable, total);
    }
    
    @Override
    public List<ApplicationWithCaseworkerDto> findApplicationsWithCaseworker(UUID caseworkerId) {
        String queryStr = """
            SELECT new uk.gov.justice.laa.dstew.access.dto.ApplicationWithCaseworkerDto(
                a.id,
                a.laaReference,
                a.status,
                c.id,
                c.firstName,
                c.lastName,
                c.email
            )
            FROM ApplicationEntity a
            INNER JOIN a.caseworker c
            WHERE c.id = :caseworkerId
            ORDER BY a.createdAt DESC
            """;
        
        return entityManager.createQuery(queryStr, ApplicationWithCaseworkerDto.class)
            .setParameter("caseworkerId", caseworkerId)
            .getResultList();
    }
    
    @Override
    public List<LinkedApplicationDto> findLinkedApplications(UUID leadApplicationId) {
        // Native query for complex join
        String nativeQuery = """
            SELECT 
                a.id, 
                a.laa_reference, 
                la.is_lead
            FROM applications a
            LEFT JOIN linked_applications la ON a.id = la.associated_application_id
            WHERE la.lead_application_id = :leadId
            """;
        
        Query query = entityManager.createNativeQuery(nativeQuery);
        query.setParameter("leadId", leadApplicationId);
        
        @SuppressWarnings("unchecked")
        List<Object[]> results = query.getResultList();
        
        return results.stream()
            .map(row -> new LinkedApplicationDto(
                UUID.fromString(row[0].toString()),
                (String) row[1],
                (Boolean) row[2]
            ))
            .collect(Collectors.toList());
    }
    
    @Override
    public List<ApplicationStatusCountDto> getApplicationStatistics() {
        String queryStr = """
            SELECT new uk.gov.justice.laa.dstew.access.dto.ApplicationStatusCountDto(
                a.status,
                COUNT(a)
            )
            FROM ApplicationEntity a
            GROUP BY a.status
            """;
        
        return entityManager.createQuery(queryStr, ApplicationStatusCountDto.class)
            .getResultList();
    }
    
    private long countApplications(ApplicationSearchCriteria criteria) {
        // Similar query but with COUNT
        // Implementation omitted for brevity
        return 0L;
    }
    
    private String mapSortField(ApplicationSortBy sortBy) {
        return switch (sortBy) {
            case LAA_REFERENCE -> "a.laaReference";
            case CLIENT_LAST_NAME -> "a.clientLastName";
            case CREATED_AT -> "a.createdAt";
            case STATUS -> "a.status";
        };
    }
}
```

### Step 6: Implement Persistence Adapter

Create adapter that implements the persistence interface:

```java
// repository/adapter/ApplicationPersistenceAdapter.java
package uk.gov.justice.laa.dstew.access.repository.adapter;

@Component
@RequiredArgsConstructor
public class ApplicationPersistenceAdapter implements ApplicationPersistence {
    
    private final ApplicationRepository jpaRepository;
    private final ApplicationCustomRepository customRepository;
    
    // === Delegate simple operations to Spring Data Repository ===
    
    @Override
    public ApplicationEntity save(ApplicationEntity entity) {
        return jpaRepository.save(entity);
    }
    
    @Override
    public List<ApplicationEntity> saveAll(List<ApplicationEntity> entities) {
        return jpaRepository.saveAll(entities);
    }
    
    @Override
    public void delete(UUID id) {
        jpaRepository.deleteById(id);
    }
    
    @Override
    public Optional<ApplicationEntity> findById(UUID id) {
        return jpaRepository.findById(id);
    }
    
    @Override
    public Optional<ApplicationEntity> findByApplyApplicationId(UUID applyApplicationId) {
        return Optional.ofNullable(jpaRepository.findByApplyApplicationId(applyApplicationId));
    }
    
    @Override
    public boolean existsById(UUID id) {
        return jpaRepository.existsById(id);
    }
    
    @Override
    public boolean existsByApplyApplicationId(UUID applyApplicationId) {
        return jpaRepository.existsByApplyApplicationId(applyApplicationId);
    }
    
    @Override
    public List<ApplicationEntity> findAllByApplyApplicationIds(List<UUID> applyApplicationIds) {
        return jpaRepository.findAllByApplyApplicationIdIn(applyApplicationIds);
    }
    
    // === Delegate complex queries to Custom Repository ===
    
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
    
    @Override
    public List<LinkedApplicationDto> findLinkedApplications(UUID leadApplicationId) {
        return customRepository.findLinkedApplications(leadApplicationId);
    }
    
    @Override
    public List<ApplicationStatusCountDto> getApplicationStatistics() {
        return customRepository.getApplicationStatistics();
    }
}
```

### Step 7: Use in Service Layer

Use case services depend on the persistence interface:

```java
// service/application/SearchApplicationsService.java
package uk.gov.justice.laa.dstew.access.service.application;

@Service
@RequiredArgsConstructor
public class SearchApplicationsService {
    
    private final ApplicationPersistence applicationPersistence;  // Interface, not repository!
    
    public PaginatedResult<ApplicationSummaryDto> execute(SearchApplicationsQuery query) {
        // Build criteria from query
        ApplicationSearchCriteria criteria = ApplicationSearchCriteria.builder()
            .status(query.getStatus())
            .laaReference(query.getLaaReference())
            .clientFirstName(query.getClientFirstName())
            .clientLastName(query.getClientLastName())
            .clientDateOfBirth(query.getClientDateOfBirth())
            .userId(query.getUserId())
            .isAutoGranted(query.getIsAutoGranted())
            .matterType(query.getMatterType())
            .sortBy(query.getSortBy())
            .orderBy(query.getOrderBy())
            .build();
        
        // Create pageable
        Pageable pageable = PageRequest.of(query.getPage(), query.getPageSize());
        
        // Execute query
        Page<ApplicationSummaryDto> page = 
            applicationPersistence.searchApplications(criteria, pageable);
        
        // Return result
        return PaginatedResult.from(page);
    }
}
```

## Benefits

### 1. Performance
- **DTO Projections**: Fetch only required fields, not full entities
- **Optimized Queries**: Custom queries with explicit joins, no N+1 problems
- **Reduced Memory**: Smaller DTOs consume less memory than full entity graphs

### 2. Testability
```java
@ExtendWith(MockitoExtension.class)
class SearchApplicationsServiceTest {
    @Mock
    private ApplicationPersistence applicationPersistence;  // Easy to mock interface
    
    @InjectMocks
    private SearchApplicationsService service;
    
    @Test
    void shouldSearchApplications() {
        // Arrange
        SearchApplicationsQuery query = SearchApplicationsQuery.builder()
            .status(ApplicationStatus.SUBMITTED)
            .page(0)
            .pageSize(10)
            .build();
        
        Page<ApplicationSummaryDto> expectedPage = new PageImpl<>(
            List.of(ApplicationSummaryDto.builder().id(UUID.randomUUID()).build())
        );
        
        when(applicationPersistence.searchApplications(any(), any()))
            .thenReturn(expectedPage);
        
        // Act
        PaginatedResult<ApplicationSummaryDto> result = service.execute(query);
        
        // Assert
        assertThat(result.getResults()).hasSize(1);
        verify(applicationPersistence).searchApplications(any(), any());
    }
}
```

### 3. Decoupling
- Business logic doesn't depend on JPA specifics
- Can swap persistence implementation without changing services
- Clear contract between layers

### 4. Evolution Path
- Easy to add new query methods
- Can migrate to different persistence technology if needed
- Foundation for eventual ports/adapters if required

## When to Use This Pattern

### ✅ Use Persistence Interface For:

1. **Complex Search Operations**
   - Multiple filter criteria
   - Dynamic query building
   - Joins across multiple tables

2. **Performance-Critical Queries**
   - Need DTO projections
   - Avoid fetching full entity graphs
   - Optimize for read operations

3. **Operations That Might Evolve**
   - Might need different persistence strategy
   - Might need caching layer
   - Might need to support multiple data sources

### ❌ Skip Persistence Interface For:

1. **Simple CRUD Operations**
   - `findById()`, `save()`, `delete()`
   - Single entity, no joins
   - Full entity is needed anyway

2. **Low-Traffic Operations**
   - Performance isn't critical
   - Simple queries work fine

## Comparison: With vs Without Persistence Interface

### Without Persistence Interface
```java
@Service
@RequiredArgsConstructor
public class SearchApplicationsService {
    private final ApplicationSummaryRepository repository;  // Spring Data directly
    
    public PaginatedResult<ApplicationSummary> execute(SearchQuery query) {
        // Build JPA Specification (complex!)
        Specification<ApplicationSummaryEntity> spec = buildSpecification(query);
        
        // Fetch full entities (inefficient!)
        Page<ApplicationSummaryEntity> page = repository.findAll(spec, pageable);
        
        // Map to DTOs (extra step!)
        return page.map(mapper::toDto);
    }
}
```

**Problems**:
- Service couples to JPA Specification
- Fetches full entities even if only need few fields
- Hard to test without database
- Cannot easily add caching or alternative storage

### With Persistence Interface
```java
@Service
@RequiredArgsConstructor
public class SearchApplicationsService {
    private final ApplicationPersistence persistence;  // Interface
    
    public PaginatedResult<ApplicationSummaryDto> execute(SearchQuery query) {
        ApplicationSearchCriteria criteria = buildCriteria(query);
        Page<ApplicationSummaryDto> page = persistence.searchApplications(criteria, pageable);
        return PaginatedResult.from(page);
    }
}
```

**Benefits**:
- Service works with simple interface
- DTOs returned directly from query
- Easy to mock in tests
- Can change implementation without touching service

## Migration Strategy

### Phase 1: Identify Complex Queries
- Review existing services
- Identify operations with complex queries, joins, or performance issues
- Prioritize by impact (high-traffic, slow queries first)

### Phase 2: Create Persistence Interface
- Define interface for the domain (e.g., `ApplicationPersistence`)
- Include both simple operations (delegate to Spring Data) and complex operations (custom)

### Phase 3: Implement Custom Repository
- Create custom repository for complex queries
- Use DTO projections for performance
- Test queries with integration tests

### Phase 4: Create Persistence Adapter
- Implement persistence interface
- Delegate to Spring Data for simple operations
- Delegate to custom repository for complex operations

### Phase 5: Update Services
- Update use case services to use persistence interface
- Remove direct Spring Data repository dependencies
- Update tests to mock persistence interface

### Phase 6: Measure & Optimize
- Monitor query performance
- Optimize queries as needed
- Add caching if beneficial

## Best Practices

### 1. Keep Interfaces Focused
```java
// ✅ GOOD: Focused interface per domain
public interface ApplicationPersistence {
    // Only application-related operations
}

public interface CaseworkerPersistence {
    // Only caseworker-related operations
}

// ❌ BAD: Generic interface
public interface GenericPersistence<T> {
    // Too generic, not domain-specific
}
```

### 2. Return DTOs for Reads, Entities for Writes
```java
public interface ApplicationPersistence {
    // Write operations return entities
    ApplicationEntity save(ApplicationEntity entity);
    
    // Complex reads return DTOs
    Page<ApplicationSummaryDto> searchApplications(criteria, pageable);
    
    // Simple reads can return entities
    Optional<ApplicationEntity> findById(UUID id);
}
```

### 3. Use Criteria Objects for Complex Queries
```java
// ✅ GOOD: Criteria object
ApplicationSearchCriteria criteria = ApplicationSearchCriteria.builder()
    .status(status)
    .laaReference(reference)
    .build();

Page<ApplicationSummaryDto> results = persistence.searchApplications(criteria, pageable);

// ❌ BAD: Many parameters
Page<ApplicationSummaryDto> results = persistence.searchApplications(
    status, reference, firstName, lastName, dob, userId, isAutoGranted, matterType, sort, order, pageable
);  // Too many parameters!
```

### 4. Write Integration Tests for Custom Queries
```java
@DataJpaTest
@Import(ApplicationCustomRepositoryImpl.class)
class ApplicationCustomRepositoryIntegrationTest {
    @Autowired
    private ApplicationCustomRepository customRepository;
    
    @Autowired
    private TestEntityManager entityManager;
    
    @Test
    void shouldSearchApplicationsWithCriteria() {
        // Given: test data in database
        ApplicationEntity app = createTestApplication();
        entityManager.persist(app);
        entityManager.flush();
        
        // When: search with criteria
        ApplicationSearchCriteria criteria = ApplicationSearchCriteria.builder()
            .status(ApplicationStatus.SUBMITTED)
            .build();
        
        Page<ApplicationSummaryDto> results = customRepository.searchApplications(
            criteria, 
            PageRequest.of(0, 10)
        );
        
        // Then: verify results
        assertThat(results).hasSize(1);
        assertThat(results.getContent().get(0).getId()).isEqualTo(app.getId());
    }
}
```

## Summary

The persistence interface pattern provides:

✅ **Flexibility** for complex queries and DTO projections
✅ **Performance** through optimized queries
✅ **Testability** via mockable interfaces
✅ **Decoupling** business logic from JPA specifics
✅ **Pragmatic** middle-ground between direct repositories and full ports/adapters

Use this pattern selectively for operations that benefit from the abstraction, while keeping simple CRUD operations direct for simplicity.

---

**Last Updated**: March 13, 2026
**Related Documents**:
- `pragmatic-use-case-refactoring.md` - Overall refactoring approach
- `api-dtos-vs-internal-models.md` - API layer separation

