# Architecture Refactoring Summary

## Overview

This directory contains documentation for refactoring the LAA Data Access API toward a more maintainable, testable architecture. We've evaluated two approaches and selected the **pragmatic approach** as the recommended path forward.

## Documents in This Collection

### 1. Main Refactoring Proposals

#### [`pragmatic-use-case-refactoring.md`](pragmatic-use-case-refactoring.md) ⭐ **RECOMMENDED**
The primary refactoring proposal that provides 75% of architectural benefits with 35% of the effort.

**Key Features:**
- Split large services into focused use case services
- Separate API DTOs from internal command/query models
- Selective use of persistence interfaces for complex queries
- Maintain Spring framework integration
- 4-5 week migration timeline

**Best for:** Production systems that need practical improvements without major risk.

#### [`use-case-refactoring-proposal.md`](use-case-refactoring-proposal.md)
Full Clean/Hexagonal Architecture approach with ports, adapters, and complete framework independence.

**Key Features:**
- Complete ports/adapters pattern
- Separate domain models from JPA entities
- Full framework independence
- 7 week migration timeline

**Best for:** Greenfield projects or when maximum flexibility is required.

### 2. Detailed Pattern Guides

#### [`api-dtos-vs-internal-models.md`](api-dtos-vs-internal-models.md) ⭐ **QUICK REFERENCE**
Explains why and how to separate API DTOs from internal business models.

**Key Concepts:**
- Controllers map API DTOs to internal commands
- Use case services work with internal models only
- Benefits: API evolution, testability, flexibility
- Code examples for correct and incorrect patterns

**Read this if:** You want to understand the layering strategy.

#### [`persistence-interface-pattern.md`](persistence-interface-pattern.md) ⭐ **TECHNICAL GUIDE**
Comprehensive guide for implementing persistence interfaces with custom repositories and DTO projections.

**Key Concepts:**
- When to use persistence interfaces vs Spring Data directly
- How to create custom repositories with DTO projections
- Implementation examples with EntityManager queries
- Testing strategies

**Read this if:** You need to implement complex queries with joins and want optimal performance.

#### [`persistence-interface-decision-guide.md`](persistence-interface-decision-guide.md) ⭐ **DECISION TOOL**
Quick reference flowchart and decision matrix for choosing persistence strategies.

**Key Features:**
- Decision flowchart
- Scenario-based guidance
- Current operations analysis
- Code smell indicators

**Read this if:** You're implementing a specific operation and need to decide on the approach.

### 3. Supporting Documents

#### [`layering-summary.md`](layering-summary.md)
Visual summary of the three-layer mapping approach (API → Business → Data).

#### [`usecase-approach.md`](usecase-approach.md)
Revised approach document with proper references (may be superseded by pragmatic approach).

## Recommended Reading Order

### For Architecture Overview
1. Start with [`pragmatic-use-case-refactoring.md`](pragmatic-use-case-refactoring.md) - Main proposal
2. Read [`api-dtos-vs-internal-models.md`](api-dtos-vs-internal-models.md) - Understand layering
3. Skim [`use-case-refactoring-proposal.md`](use-case-refactoring-proposal.md) - See the alternative

### For Implementation
1. Review [`pragmatic-use-case-refactoring.md`](pragmatic-use-case-refactoring.md) - Overall approach
2. Consult [`persistence-interface-decision-guide.md`](persistence-interface-decision-guide.md) - For each operation
3. Reference [`persistence-interface-pattern.md`](persistence-interface-pattern.md) - For complex queries
4. Use [`api-dtos-vs-internal-models.md`](api-dtos-vs-internal-models.md) - For mapping guidance

## Architecture Decisions

### Decision 1: Pragmatic Approach Selected ✅

**Rationale:**
- Provides most benefits without extreme complexity
- Manageable risk and timeline (4-5 weeks)
- Team-friendly - familiar Spring patterns
- Can evolve to full Clean Architecture if needed

**Trade-offs Accepted:**
- Not fully framework-independent
- Some coupling to Spring/JPA remains
- Selective abstraction rather than complete isolation

### Decision 2: API DTOs Stay at Controller Layer ✅

**Rationale:**
- Business logic independent of API contracts
- Can support API versioning and multiple interfaces
- Easier testing with simple POJOs
- Clear separation of concerns

**Implementation:**
- Controllers map OpenAPI DTOs to internal commands/queries
- Use case services work exclusively with internal models
- Factories/builders for mapping logic

### Decision 3: Selective Persistence Interfaces ✅

**Rationale:**
- Needed for complex queries with joins and projections
- Performance benefits from DTO projections
- Flexibility without over-engineering
- Keep simple operations direct (Spring Data)

**Implementation:**
- Persistence interfaces for complex queries only
- Custom repositories with EntityManager for optimized queries
- Adapters implement interface, delegate to Spring Data + Custom
- Skip for simple CRUD - use Spring Data directly

## Architecture Layers

### Current State
```
Controller → Service → Repository → Database
```

### Target State
```
┌─────────────────────────────────────────────────────┐
│ Presentation Layer (Controllers)                    │
│ - API DTOs (OpenAPI-generated)                      │
│ - Maps to internal models                           │
└────────────────────┬────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────┐
│ Application Layer (Use Case Services)               │
│ - Internal commands/queries                         │
│ - Business logic                                    │
│ - Depends on persistence interfaces (when needed)   │
└────────────────────┬────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────┐
│ Persistence Layer                                    │
│ - Persistence interfaces (for complex operations)   │
│ - Spring Data repositories (for simple operations)  │
│ - Custom repositories (for complex queries/DTOs)    │
│ - JPA Entities                                      │
└─────────────────────────────────────────────────────┘
```

## Migration Plan

### Phase 1: Preparation (Week 1)
- Create package structure
- Implement proof of concept (simplest use case)
- Define patterns and templates
- Team training

### Phase 2: Simple Query Operations (Week 1-2)
- GetApplicationById
- GetAllCaseworkers
- GetApplicationHistory
- SearchIndividuals (simple version)

### Phase 3: Simple Command Operations (Week 2-3)
- CreateApplication
- UpdateApplication

### Phase 4: Complex Operations (Week 3-4)
- SearchApplications (with persistence interface)
- AssignCaseworker
- UnassignCaseworker
- MakeDecision

### Phase 5: Cleanup (Week 4-5)
- Remove old service classes
- Update all tests
- Documentation
- Team training

## Success Metrics

- ✅ Unit test coverage >85%
- ✅ Average service class <150 lines
- ✅ Cyclomatic complexity reduced 30%
- ✅ No increase in build time
- ✅ Code duplication <3%
- ✅ Improved developer satisfaction

## Key Principles

### 1. Pragmatism Over Purity
Don't over-engineer. Use abstractions where they provide clear value.

### 2. Incremental Evolution
Refactor one use case at a time. Keep system working at each step.

### 3. Team-Friendly Patterns
Use familiar Spring patterns. Minimize learning curve.

### 4. Selective Abstraction
Introduce complexity only where it solves real problems:
- API DTOs → Internal models: Always (clear separation)
- Persistence interfaces: Selectively (complex queries only)
- Custom repositories: When needed (performance, complex joins)

### 5. Testability First
Every change should improve testability. Unit tests should be easy to write.

## Comparison: Two Approaches

| Aspect | Pragmatic Approach | Full Clean Architecture |
|--------|-------------------|------------------------|
| **Timeline** | 4-5 weeks | 7 weeks |
| **Complexity** | Low-Medium | High |
| **Risk** | Low | High |
| **Benefits** | 75% | 100% |
| **Effort** | 35% | 100% |
| **Framework Independence** | Partial | Full |
| **Learning Curve** | Gentle | Steep |
| **Reversibility** | Moderate | Difficult |
| **Production Ready** | Yes | Requires more validation |

## When to Evolve Further

Consider moving to full Clean Architecture if:
- ✅ Need to support radically different persistence (e.g., NoSQL + SQL)
- ✅ Need to support multiple frameworks (e.g., Spring + Quarkus)
- ✅ Domain complexity increases significantly
- ✅ Need to isolate domain logic for different deployment models
- ✅ Team has mastered pragmatic approach and wants more

Until then, the pragmatic approach provides excellent ROI.

## Common Patterns

### Pattern 1: Simple Operation
```java
@Service
@RequiredArgsConstructor
public class GetApplicationByIdService {
    private final ApplicationRepository repository;  // Direct Spring Data
    
    public Application execute(UUID id) {
        return repository.findById(id)
            .map(mapper::toDto)
            .orElseThrow(() -> new ResourceNotFoundException(id));
    }
}
```

### Pattern 2: Complex Operation with Internal Model
```java
@Service
@RequiredArgsConstructor
public class CreateApplicationService {
    private final ApplicationRepository repository;
    private final ApplicationMapper mapper;
    
    public UUID execute(CreateApplicationCommand command) {  // Internal model
        validateCommand(command);
        ApplicationEntity entity = mapper.toEntity(command);
        ApplicationEntity saved = repository.save(entity);
        return saved.getId();
    }
}
```

### Pattern 3: Complex Query with Persistence Interface
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

### Pattern 4: Controller Mapping
```java
@RestController
@RequiredArgsConstructor
public class ApplicationController implements ApplicationApi {
    private final CreateApplicationService createApplication;
    
    @Override
    public ResponseEntity<Void> createApplication(
            ServiceName serviceName,
            ApplicationCreateRequest apiRequest) {  // API DTO
        
        // Map API DTO to internal command
        CreateApplicationCommand command = CreateApplicationCommand.from(apiRequest);
        
        // Execute use case
        UUID id = createApplication.execute(command);
        
        return ResponseEntity.created(buildUri(id)).build();
    }
}
```

## FAQs

### Q: Do we need persistence interfaces for every repository?
**A:** No! Only for complex queries with joins and DTO projections. Keep simple CRUD operations using Spring Data directly.

### Q: Should every operation have an internal command/query model?
**A:** For operations with business logic, yes. For trivial pass-through queries (like findById), you can skip it.

### Q: How do we handle transactions?
**A:** Use `@Transactional` on use case service implementations, just like before.

### Q: What about existing integration tests?
**A:** They should mostly work as-is since API contracts aren't changing. Update as needed.

### Q: Can we use this with GraphQL or gRPC later?
**A:** Yes! That's a key benefit. Controllers (or GraphQL resolvers) map to internal models, and use cases remain unchanged.

### Q: How do we handle validation?
**A:** API validation stays in controllers (via Spring validation). Business validation goes in use case services.

## Getting Started

1. **Read** [`pragmatic-use-case-refactoring.md`](pragmatic-use-case-refactoring.md)
2. **Discuss** approach with team
3. **Start** with simplest use case as proof of concept
4. **Iterate** based on learnings
5. **Measure** success metrics
6. **Adjust** approach as needed

## References

### Internal Documents
- All documents in this `docs/` directory
- `conventional_commits.md` - Commit standards
- `pre-commit-hooks.md` - Code quality
- `deployment.md` - Deployment process

### External Resources
- Clean Architecture - Robert C. Martin
- Refactoring - Martin Fowler
- Spring Data JPA documentation
- Domain-Driven Design - Eric Evans

---

**Last Updated**: March 13, 2026  
**Status**: Architecture Decision - Pragmatic Approach Approved  
**Next Review**: After Phase 1 completion (Week 1)

